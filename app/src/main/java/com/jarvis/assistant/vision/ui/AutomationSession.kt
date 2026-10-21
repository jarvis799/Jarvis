package com.jarvis.assistant.vision.ui

import android.util.Log
import com.jarvis.assistant.automation.agent.AgentTaskEngine
import com.jarvis.assistant.automation.agent.ScreenSignature
import com.jarvis.assistant.automation.agent.ScreenSignatureCalculator
import com.jarvis.assistant.vision.UiElementInfo
import com.jarvis.assistant.vision.UiGroundingEngine
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs one spoken UI command as a closed-loop session: ground the target, act once,
 * re-inspect, verify, and only then report success. Every step is bounded by a
 * timeout, retries are limited and never repeat an action that may already have
 * landed, and Android limits are reported as limits instead of being hidden.
 */
object AutomationSession {

    private const val TAG = "AutomationSession"
    private const val STEP_SETTLE_MS = 450L
    private const val SESSION_TIMEOUT_MS = 25_000L
    private const val MAX_STEPS = 6

    private val engine = UiGroundingEngine()
    private val resolver = UiTargetResolver(engine)
    private val sessionMutex = Mutex()

    private data class PendingChoice(val plan: UiCommandPlan, val candidates: List<UiCandidate>)
    private data class PendingConfirm(val plan: UiCommandPlan, val question: String)

    @Volatile private var pendingChoice: PendingChoice? = null
    @Volatile private var pendingConfirm: PendingConfirm? = null
    @Volatile private var cancelRequested = false

    /** True when the utterance is a UI command or answers a pending clarification. */
    fun canHandle(utterance: String): Boolean {
        val clean = utterance.trim()
        if (clean.isBlank()) return false
        if (pendingChoice != null || pendingConfirm != null) return true
        return UiCommandPlanner.plan(clean) != null
    }

    fun hasPendingPrompt(): Boolean = pendingChoice != null || pendingConfirm != null

    fun requestCancellation() {
        cancelRequested = true
    }

    /** Voice/text entry point: speaks exactly one reply. */
    fun tryHandle(utterance: String, speak: (String) -> Unit) {
        val clean = utterance.trim()
        CoroutineScope(Dispatchers.Main).launch {
            val reply = handle(clean)
            speak(reply)
        }
    }

    private suspend fun handle(utterance: String): String {
        val confirm = pendingConfirm
        if (confirm != null) {
            pendingConfirm = null
            return if (isYes(utterance)) {
                run(confirm.plan.copy(needsConfirmation = false, confirmationQuestion = null)).spoken
            } else {
                "Okay, I did not do it, Sir."
            }
        }
        val choice = pendingChoice
        if (choice != null) {
            val picked = pickCandidate(choice, utterance)
            if (picked == null) {
                pendingChoice = choice
                val labels = choice.candidates.take(3).map { it.label }.filter { it.isNotBlank() }
                return "Which one, Sir - " + labels.joinToString(", ") + "?"
            }
            pendingChoice = null
            return actOnChosen(choice.plan, picked).spoken
        }
        val plan = UiCommandPlanner.plan(utterance) ?: return "I did not catch a screen command, Sir."
        return run(plan).spoken
    }

    suspend fun run(plan: UiCommandPlan): UiCommandResult {
        if (!UiActionExecutor.isServiceReady()) {
            return UiCommandResult(
                UiOutcome.BLOCKED,
                "I need the Jarvis accessibility service switched on before I can work the screen, Sir - Settings, Accessibility, Jarvis, turn it on.",
                limitation = "accessibility_service_disabled",
                plan = plan
            )
        }
        if (plan.needsConfirmation) {
            val question = plan.confirmationQuestion ?: "That may change something important, Sir. Should I continue?"
            pendingConfirm = PendingConfirm(plan, question)
            return UiCommandResult(UiOutcome.BLOCKED, question, limitation = "awaiting_confirmation", plan = plan)
        }
        cancelRequested = false
        return sessionMutex.withLock {
            val steps = ArrayList<UiStepLog>()
            val result = withTimeoutOrNull(SESSION_TIMEOUT_MS) {
                when (plan.action) {
                    UiActionKind.DESCRIBE -> describe(plan, steps)
                    UiActionKind.BACK, UiActionKind.HOME, UiActionKind.RECENTS -> navigate(plan, steps)
                    UiActionKind.SCROLL_FORWARD, UiActionKind.SCROLL_BACKWARD -> scrollOnce(plan, steps)
                    UiActionKind.FIND_AND_ACT -> findAndAct(plan, steps)
                    UiActionKind.CLICK, UiActionKind.LONG_CLICK -> clickTarget(plan, steps)
                    UiActionKind.SET_TEXT -> typeText(plan, steps)
                }
            } ?: UiCommandResult(UiOutcome.FAILED, "That took too long, Sir, so I stopped.", steps, "session_timeout", plan)
            logSteps(result)
            result.copy(steps = steps)
        }
    }

    // ------------------------------------------------------------------ clicks

    private suspend fun clickTarget(plan: UiCommandPlan, steps: ArrayList<UiStepLog>): UiCommandResult {
        val spec = plan.target ?: return notFound(plan, steps, "no target was named", null)
        val (before, elements) = snapshot()
        var resolution = resolver.resolve(spec, elements)
        var note: String? = null

        if (resolution.chosen == null && !resolution.ambiguous) {
            val grounded = groundWithOcr(spec, resolution, plan)
            resolution = grounded.first
            note = grounded.second
        }
        if (resolution.chosen == null && spec.hints.contains("seek")) {
            val scroll = UiActionExecutor.scroll(true, null)
            steps += UiStepLog(steps.size + 1, "SCROLL_FORWARD", spec.query, scroll.detail ?: "looked for the target lower down", scroll.performed)
            if (scroll.performed) {
                settle()
                val fresh = snapshot()
                resolution = resolver.resolve(spec, fresh.second)
            }
        }
        if (resolution.chosen == null) {
            if (resolution.ambiguous) return ask(plan, resolution, steps)
            return notFound(plan, steps, note, spec.query)
        }

        val target = resolution.chosen!!
        val action = if (plan.action == UiActionKind.LONG_CLICK) UiActionExecutor.longClick(target) else UiActionExecutor.click(target)
        steps += UiStepLog(steps.size + 1, plan.action.name, target.primaryLabel, action.detail ?: "sent", false, "method=" + action.method.name)
        if (!action.performed) {
            return UiCommandResult(
                UiOutcome.NOT_SUPPORTED,
                "I could not work " + target.primaryLabel + ", Sir - " + (action.detail ?: "Android refused the action") + ".",
                steps, action.detail, plan
            )
        }
        settle()
        val (after, _) = snapshot()
        val verification = UiVerifier.verifyScreenChanged(before, after, "tapping " + target.primaryLabel)
        steps[steps.size - 1] = steps.last().copy(result = verification.detail, verified = verification.verified)
        return if (verification.verified) {
            val source = if (resolution.usedOcr) " (found from the screen text)" else ""
            UiCommandResult(UiOutcome.VERIFIED, "Done, Sir - I opened " + target.primaryLabel + source + ".", steps, null, plan)
        } else {
            UiCommandResult(
                UiOutcome.UNVERIFIED,
                "I tapped " + target.primaryLabel + ", Sir, but the screen did not change, so I am not calling it done.",
                steps, "no_visible_change", plan
            )
        }
    }

    private suspend fun findAndAct(plan: UiCommandPlan, steps: ArrayList<UiStepLog>): UiCommandResult {
        val spec = plan.target ?: return notFound(plan, steps, "no target was named", null)
        val forward = !spec.hints.contains("scroll_backward")
        var attempt = 0
        while (attempt < plan.maxAttempts && steps.size < MAX_STEPS) {
            attempt++
            if (isCancelled()) return UiCommandResult(UiOutcome.CANCELLED, "Stopped, Sir.", steps, "cancelled", plan)
            val (before, elements) = snapshot()
            val resolution = resolver.resolve(spec, elements)
            if (resolution.ambiguous) return ask(plan, resolution, steps)
            val found = resolution.chosen
            if (found != null) {
                val action = UiActionExecutor.click(found)
                steps += UiStepLog(steps.size + 1, "FIND_AND_ACT", found.primaryLabel, action.detail ?: "sent", false, "method=" + action.method.name)
                if (!action.performed) {
                    return UiCommandResult(UiOutcome.NOT_SUPPORTED, "I found " + found.primaryLabel + " but could not open it, Sir - " + (action.detail ?: "Android refused the action") + ".", steps, action.detail, plan)
                }
                settle()
                val (after, _) = snapshot()
                val verification = UiVerifier.verifyScreenChanged(before, after, "opening " + found.primaryLabel)
                steps[steps.size - 1] = steps.last().copy(result = verification.detail, verified = verification.verified)
                return if (verification.verified) {
                    UiCommandResult(UiOutcome.VERIFIED, "Found it, Sir - " + found.primaryLabel + " is open.", steps, null, plan)
                } else {
                    UiCommandResult(UiOutcome.UNVERIFIED, "I found and tapped " + found.primaryLabel + ", Sir, but nothing changed on screen.", steps, "no_visible_change", plan)
                }
            }
            val scroll = UiActionExecutor.scroll(forward, null)
            if (!scroll.performed) {
                return notFound(plan, steps, scroll.detail, spec.query)
            }
            settle()
            val (after, _) = snapshot()
            val moved = UiVerifier.verifyScrolled(before, after)
            steps += UiStepLog(steps.size + 1, if (forward) "SCROLL_FORWARD" else "SCROLL_BACKWARD", spec.query, moved.detail, moved.verified)
            if (!moved.verified) {
                return UiCommandResult(UiOutcome.TARGET_NOT_FOUND, "I reached the end of the list, Sir, and could not find " + spec.query + ".", steps, "end_of_list_reached", plan)
            }
        }
        return notFound(plan, steps, null, spec.query)
    }

    private suspend fun actOnChosen(plan: UiCommandPlan, element: UiElementInfo): UiCommandResult {
        return sessionMutex.withLock {
            val steps = ArrayList<UiStepLog>()
            if (!UiActionExecutor.isServiceReady()) {
                return@withLock UiCommandResult(UiOutcome.BLOCKED, "The accessibility service is off now, Sir.", steps, "accessibility_service_disabled", plan)
            }
            val (before, _) = snapshot()
            val action = UiActionExecutor.click(element)
            steps += UiStepLog(1, "CLICK", element.primaryLabel, action.detail ?: "sent", false, "method=" + action.method.name)
            if (!action.performed) {
                return@withLock UiCommandResult(UiOutcome.NOT_SUPPORTED, "I could not open " + element.primaryLabel + ", Sir - " + (action.detail ?: "Android refused the action") + ".", steps, action.detail, plan)
            }
            settle()
            val (after, _) = snapshot()
            val verification = UiVerifier.verifyScreenChanged(before, after, "tapping " + element.primaryLabel)
            steps[0] = steps.last().copy(result = verification.detail, verified = verification.verified)
            logSteps(UiCommandResult(if (verification.verified) UiOutcome.VERIFIED else UiOutcome.UNVERIFIED, "", steps, null, plan))
            if (verification.verified) {
                UiCommandResult(UiOutcome.VERIFIED, "Done, Sir - " + element.primaryLabel + " is open.", steps, null, plan)
            } else {
                UiCommandResult(UiOutcome.UNVERIFIED, "I tapped " + element.primaryLabel + ", Sir, but the screen did not change.", steps, "no_visible_change", plan)
            }
        }
    }

    // ------------------------------------------------------------------ typing

    private suspend fun typeText(plan: UiCommandPlan, steps: ArrayList<UiStepLog>): UiCommandResult {
        val text = plan.text.orEmpty()
        if (text.isBlank()) return notSupported(plan, steps, "I need the exact text to type, Sir.")
        val spec = plan.target ?: UiTargetSpec(query = "", source = UiTargetSource.SELECTED_FIELD, prefersEditable = true)
        val (_, elements) = snapshot()
        val resolution = resolver.resolve(spec, elements)
        if (resolution.ambiguous) return ask(plan, resolution, steps)
        val field = resolution.chosen
            ?: return notSupported(plan, steps, "I cannot see an editable field on this screen, Sir.")
        val outcome = UiActionExecutor.setText(field, text)
        if (outcome.sensitive) {
            pendingConfirm = PendingConfirm(plan, "That looks like a password or payment field, Sir. Should I really type into it?")
            return UiCommandResult(UiOutcome.BLOCKED, "That looks like a password or payment field, Sir. Should I really type into it?", steps, "sensitive_field", plan)
        }
        steps += UiStepLog(steps.size + 1, "SET_TEXT", field.primaryLabel, outcome.detail ?: "sent", false, "method=" + outcome.method.name)
        if (!outcome.performed) {
            return UiCommandResult(UiOutcome.NOT_SUPPORTED, "I could not type into " + field.primaryLabel + ", Sir - " + (outcome.detail ?: "the field refused it") + ".", steps, outcome.detail, plan)
        }
        settle()
        val (_, after) = snapshot()
        val verification = UiVerifier.verifyText(text, after)
        steps[steps.size - 1] = steps.last().copy(result = verification.detail, verified = verification.verified)
        return if (verification.verified) {
            UiCommandResult(UiOutcome.VERIFIED, "Typed into " + field.primaryLabel + ", Sir.", steps, null, plan)
        } else {
            UiCommandResult(UiOutcome.UNVERIFIED, "I sent the text, Sir, but I could not confirm the field kept it.", steps, verification.detail, plan)
        }
    }

    // ---------------------------------------------------------------- scrolling

    private suspend fun scrollOnce(plan: UiCommandPlan, steps: ArrayList<UiStepLog>): UiCommandResult {
        val (before, elements) = snapshot()
        val container = resolver.resolve(UiTargetSpec(query = "", source = UiTargetSource.SCROLL_CONTAINER), elements).chosen
        val forward = plan.action == UiActionKind.SCROLL_FORWARD
        val outcome = UiActionExecutor.scroll(forward, container)
        steps += UiStepLog(1, plan.action.name, container?.primaryLabel ?: "screen", outcome.detail ?: "sent", false, "method=" + outcome.method.name)
        if (!outcome.performed) {
            return notSupported(plan, steps, outcome.detail ?: "there is nothing to scroll here")
        }
        settle()
        val (after, _) = snapshot()
        val verification = UiVerifier.verifyScrolled(before, after)
        steps[0] = steps.last().copy(result = verification.detail, verified = verification.verified)
        return if (verification.verified) {
            UiCommandResult(UiOutcome.VERIFIED, "Scrolled, Sir.", steps, null, plan)
        } else {
            UiCommandResult(UiOutcome.UNVERIFIED, "Nothing new appeared, Sir - this list is probably at its end.", steps, "end_of_list_reached", plan)
        }
    }

    // --------------------------------------------------------------- navigation

    private suspend fun navigate(plan: UiCommandPlan, steps: ArrayList<UiStepLog>): UiCommandResult {
        val (before, _) = snapshot()
        val outcome = UiActionExecutor.global(plan.action)
        steps += UiStepLog(1, plan.action.name, "system navigation", outcome.detail ?: "sent", false, "method=" + outcome.method.name)
        if (!outcome.performed) {
            return notSupported(plan, steps, outcome.detail ?: "Android refused that navigation action")
        }
        settle()
        val (after, _) = snapshot()
        val verification = UiVerifier.verifyScreenChanged(before, after, "that navigation")
        steps[0] = steps.last().copy(result = verification.detail, verified = verification.verified)
        return if (verification.verified) {
            UiCommandResult(UiOutcome.VERIFIED, "Done, Sir.", steps, null, plan)
        } else {
            UiCommandResult(UiOutcome.UNVERIFIED, "I sent it, Sir, but the screen looks unchanged - there may be nothing to go back to.", steps, "no_visible_change", plan)
        }
    }

    // ----------------------------------------------------------------- describe

    private suspend fun describe(plan: UiCommandPlan, steps: ArrayList<UiStepLog>): UiCommandResult {
        val (_, elements) = snapshot()
        val root = UiActionExecutor.activeRoot()
        var summary = runCatching { engine.formatElementsSummary(elements, root) }.getOrNull().orEmpty()
        var usedOcr = false

        if (summary.isBlank()) {
            val ocr = runCatching { UiScreenOcr.readScreen() }.getOrNull()
            if (ocr == null || ocr.blocked) {
                val reason = ocr?.reason ?: "the screen could not be read"
                steps += UiStepLog(1, "DESCRIBE", "screen", "blocked", false, reason)
                return UiCommandResult(UiOutcome.BLOCKED, "I cannot read this screen, Sir - " + reason, steps, "screen_read_blocked", plan)
            }
            if (ocr.lines.isNotEmpty()) {
                usedOcr = true
                summary = ocr.lines.take(14).joinToString(", ") { it.text }
            }
        }
        if (summary.isBlank()) {
            steps += UiStepLog(1, "DESCRIBE", "screen", "no readable content", true)
            return UiCommandResult(UiOutcome.VERIFIED, "This screen looks empty to me, Sir.", steps, null, plan)
        }
        val spoken = "On screen: " + summary.take(320)
        steps += UiStepLog(1, "DESCRIBE", "screen", if (usedOcr) "read from the screenshot text" else "read from the accessibility tree", true)
        return UiCommandResult(UiOutcome.VERIFIED, spoken, steps, if (usedOcr) "read_from_screenshot_text" else null, plan)
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun groundWithOcr(spec: UiTargetSpec, resolution: UiResolution, plan: UiCommandPlan): Pair<UiResolution, String?> {
        val ocr = runCatching { UiScreenOcr.readScreen() }.getOrNull()
            ?: return resolution to "the screenshot could not be taken"
        if (ocr.blocked) return resolution to ocr.reason
        if (ocr.lines.isEmpty()) return resolution to "no readable text was found on this screen"
        val metrics = com.jarvis.assistant.JarvisApp.instance.resources.displayMetrics
        val ocrResolution = resolver.resolveFromOcr(
            spec,
            ocr.lines,
            metrics.widthPixels,
            metrics.heightPixels,
            ocr.bitmapWidth,
            ocr.bitmapHeight
        )
        if (ocrResolution.chosen != null || ocrResolution.ambiguous) return ocrResolution to null
        return resolution to "not matched in the screen text either"
    }

    private suspend fun snapshot(): Pair<ScreenSignature?, List<UiElementInfo>> {
        val root = UiActionExecutor.activeRoot()
        val signature = runCatching { ScreenSignatureCalculator.computeSignature(root) }.getOrNull()
        val elements = runCatching { engine.scanScreen(root) }.getOrDefault(emptyList())
        return signature to elements
    }

    private suspend fun settle() {
        delay(STEP_SETTLE_MS)
    }

    private fun ask(plan: UiCommandPlan, resolution: UiResolution, steps: ArrayList<UiStepLog>): UiCommandResult {
        val labels = resolution.topLabels
        val question = if (labels.size >= 2) {
            "I can see more than one match, Sir - " + labels[0] + ", or " + labels[1] + "?"
        } else {
            "I found more than one match, Sir. Which one do you mean?"
        }
        pendingChoice = PendingChoice(plan, resolution.candidates)
        steps += UiStepLog(steps.size + 1, "RESOLVE", resolution.spec.query, "ambiguous - asked the user", false, labels.joinToString(" | "))
        return UiCommandResult(UiOutcome.AMBIGUOUS, question, steps, "ambiguous_target", plan)
    }

    private fun notFound(plan: UiCommandPlan, steps: ArrayList<UiStepLog>, note: String?, query: String?): UiCommandResult {
        val name = query ?: "it"
        val hint = note?.let { " (" + it + ")" } ?: ""
        steps += UiStepLog(steps.size + 1, "RESOLVE", name, "not found on screen", false, note)
        return UiCommandResult(UiOutcome.TARGET_NOT_FOUND, "I cannot see " + name + " on this screen, Sir" + hint + ".", steps, "target_not_found", plan)
    }

    private fun notSupported(plan: UiCommandPlan, steps: ArrayList<UiStepLog>, reason: String): UiCommandResult {
        steps += UiStepLog(steps.size + 1, plan.action.name, plan.target?.query ?: "screen", "not possible", false, reason)
        return UiCommandResult(UiOutcome.NOT_SUPPORTED, "I cannot do that here, Sir - " + reason + ".", steps, reason, plan)
    }

    private fun isCancelled(): Boolean = cancelRequested || AgentTaskEngine.isCancelled()

    private fun isYes(utterance: String): Boolean {
        val clean = utterance.lowercase(Locale.ROOT).trim().trimEnd('.', '!', '?', ',')
        return clean in setOf(
            "yes", "yes please", "yeah", "yep", "ok", "okay", "do it", "go ahead", "confirm",
            "hae", "ha", "haan", "hyam", "koro", "koroo", "kore fel", "h\u00e4n", "h\u00e3n"
        )
    }

    private fun pickCandidate(choice: PendingChoice, utterance: String): UiElementInfo? {
        val clean = utterance.lowercase(Locale.ROOT).trim()
        val ordinals = listOf(
            "first" to 1, "one" to 1, "ek" to 1, "1" to 1, "prothom" to 1,
            "second" to 2, "two" to 2, "dui" to 2, "2" to 2, "ditio" to 2,
            "third" to 3, "three" to 3, "tin" to 3, "3" to 3, "tritio" to 3,
            "last" to -1, "shesh" to -1
        )
        for ((word, index) in ordinals) {
            if (Regex("\\b" + Regex.escape(word) + "\\b").containsMatchIn(clean)) {
                val position = if (index < 0) choice.candidates.size - 1 else (index - 1).coerceIn(0, choice.candidates.size - 1)
                return choice.candidates.getOrNull(position)?.element
            }
        }
        val tokens = clean.split(" ").filter { it.length > 1 }
        return choice.candidates.firstOrNull { candidate ->
            val label = candidate.label.lowercase(Locale.ROOT)
            label.isNotBlank() && (clean.contains(label) || tokens.any { token -> label.contains(token) })
        }?.element
    }

    private fun logSteps(result: UiCommandResult) {
        for (step in result.steps) {
            Log.i(TAG, "step " + step.step + " " + step.action + " -> " + step.target + " : " + step.result + " verified=" + step.verified)
        }
        Log.i(TAG, "session outcome " + result.outcome.name + (result.limitation?.let { " limitation=" + it } ?: ""))
    }
}
