package com.jarvis.assistant.agent

import android.content.Context
import com.jarvis.assistant.agent.emotion.EmotionalIntelligence
import com.jarvis.assistant.agent.memory.UserMemory
import com.jarvis.assistant.agent.personality.DynamicPersonality
import com.jarvis.assistant.agent.personas.SpecialistPersonas
import com.jarvis.assistant.agent.planning.TaskPlanner
import com.jarvis.assistant.agent.permissions.PermissionGate
import com.jarvis.assistant.agent.recovery.ErrorRecovery
import com.jarvis.assistant.agent.tools.ToolOrchestrator
import com.jarvis.assistant.agent.verification.ResultVerifier
import com.jarvis.assistant.bugs.BugDetectionService
import com.jarvis.assistant.network.HttpChatClient
import com.jarvis.assistant.tools.GoogleSearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Main orchestrator. Enhances prompts with emotional intelligence, memory, personality, planning, personas, and tool awareness. Wraps the existing HttpChatClient behind a feature flag. */
object AgentEngine {

    suspend fun process(
        userInput: String, basePrompt: String, endpoint: String,
        apiKey: String, model: String, context: Context,
        onDelta: ((String) -> Unit)? = null
    ): String {
        com.jarvis.assistant.automation.command.NativeCommandGateway.execute(context, userInput)?.let { return it }
        val router = com.jarvis.assistant.model.LlmRouter
        if (!router.isOnline(context) || endpoint.isBlank()) {
            com.jarvis.assistant.util.OfflineEngine.localAnswer(context, userInput)?.let { return it }
            // Stream the on-device answer: the first sentence is spoken while the rest is still
            // being generated, so a local reply never sits silent for a minute before speaking.
            return if (onDelta != null) router.localAnswerStream(context, userInput, onDelta)
            else router.localAnswer(context, userInput)
        }
        return router.answer(context, userInput, com.jarvis.assistant.model.LocalJarvisPrompt.system(context)) {
            processConnected(userInput, basePrompt, endpoint, apiKey, model, context, onDelta)
        }
    }

    private suspend fun processConnected(
        userInput: String, basePrompt: String, endpoint: String,
        apiKey: String, model: String, context: Context,
        onDelta: ((String) -> Unit)?
    ): String {
        val adultEducationEnabled = com.jarvis.assistant.JarvisApp.instance.preferences.adultEducationEnabled
        if (endpoint.isBlank()) {
            com.jarvis.assistant.util.OfflineEngine.localAnswer(context, userInput)?.let { return it }
            return com.jarvis.assistant.model.LlmRouter.localAnswer(context, userInput)
        }
        if (!FeatureFlags.engineEnabled) {
            CapabilityRouter.route(context, userInput)?.let { return it }
            return HttpChatClient().ask(
                endpoint, com.jarvis.assistant.personality.AdultEducationPolicy.applyTo(basePrompt, adultEducationEnabled), apiKey, model
            )
        }

        val sb = StringBuilder(basePrompt)
        // DEV STYLE: Hindi-first language, no-nonsense replies and full app knowledge - always on.
        sb.append(coreSystemBlock())

        // DEV STYLE 2.0.2: the creator key typed in chat is recognized at once and remembered,
        // so Jarvis keeps treating the owner as its creator from then on.
        if (com.jarvis.assistant.security.CreatorVerifier.isCreatorPhrase(userInput)) {
            com.jarvis.assistant.security.CreatorAuthenticationService.markCreatorVerified(context)
            return com.jarvis.assistant.security.CreatorVerifier.greeting()
        }

        // DEV STYLE: high-risk actions always ask the owner for permission first.
        val risk = PermissionGate.riskCheck(userInput)
        if (risk.level != PermissionGate.Level.READ_ONLY) return risk.message
        // If the owner just confirmed a pending high-risk action, resume it.
        val request = if (risk.message.isNotBlank() && risk.message != userInput) risk.message else userInput

        // DEV STYLE: Plugin framework - creator-installed commands answer before anything else.
        com.jarvis.assistant.plugins.PluginManager.match(context, request)?.let { return it }

        // DEV STYLE: in-app media viewer - YouTube/Instagram links play right inside Jarvis.
        com.jarvis.assistant.media.web.MediaResolver.launchIfMedia(context, request)?.let { return it }

        // DEV STYLE: open the Plugin Lab or the Terminal screen on request.
        if (Regex("\\bplugins?\\b.*\\b(store|lab|install|manage)\\b|\\b(store|install|manage)\\b.*\\bplugins?\\b", RegexOption.IGNORE_CASE).containsMatchIn(request)) {
            runCatching {
                context.startActivity(android.content.Intent(context, com.jarvis.assistant.plugins.PluginStoreActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return "Plugin Lab opened, Sir. Install new skills any time - they load instantly."
        }
        if (Regex("\\b(terminal|console)\\b", RegexOption.IGNORE_CASE).containsMatchIn(request) &&
            Regex("\\b(open|kholo|khulun|launch|start|show|dekhao|dekhun)\\b", RegexOption.IGNORE_CASE).containsMatchIn(request)) {
            runCatching {
                context.startActivity(android.content.Intent(context, com.jarvis.assistant.terminal.TerminalActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return "Terminal opened, Sir."
        }

        // DEV STYLE 2.0.1 - Sub Agent routing: WhatsApp / Email auto reply agents and the showcase.
        com.jarvis.assistant.subagent.SubAgentRouter.route(context, request)?.let { return it }

        // DEV STYLE 2.0.5 - local capability router: sub agent creation, plugin lab, local
        // models, backup and every in-app screen Jarvis can act on itself.
        CapabilityRouter.route(context, request)?.let { return it }

        // DEV STYLE: APK Agent - import an APK, truthful analysis, step-by-step changes.
        if (Regex("\\bapk\\b.*(analy|agent|import|modify)|\\b(analy[sz]e|analy[sz]is)\\b.*\\bapk\\b", RegexOption.IGNORE_CASE).containsMatchIn(request)) {
            runCatching {
                context.startActivity(android.content.Intent(context, com.jarvis.assistant.apkagent.ApkAgentActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return "APK Agent opened, Sir. Select an APK - ami truthful analysis debo, tarpor ekta ekta question kore change guide korbo."
        }

        // DEV STYLE: Skill Evolution Lab - fully offline self-improvement report.
        if (Regex("\\bskill\\s*(?:evolution\\s*)?lab\\b", RegexOption.IGNORE_CASE).containsMatchIn(request)) {
            return com.jarvis.assistant.agent.skills.SkillEvolutionLab.labReport()
        }

        // DEV STYLE: "What's new" - Jarvis announces newly added features after every update.
        if (isNewFeatureQuestion(request)) return whatsNewAnswer()
        // DEV STYLE: "What can you do" - Jarvis knows its full feature list.
        if (isCapabilityQuestion(request)) return com.jarvis.assistant.security.FeatureKnowledge.capabilitiesText()

        // DEV STYLE: Jarvis never goes dark - offline answers come first.
        if (!com.jarvis.assistant.util.OfflineEngine.isOnline(context)) {
            com.jarvis.assistant.util.OfflineEngine.localAnswer(context, request)?.let { return it }
            val universePlannerOffline = com.jarvis.assistant.agent.planning.ParallelUniversePlanner
            if (universePlannerOffline.handles(request)) {
                return universePlannerOffline.report(universePlannerOffline.extractGoal(request))
            }
            return com.jarvis.assistant.util.OfflineEngine.offlineFallback()
        }

        // DEV STYLE: Multi-Agent System - Main JARVIS delegates to specialists.
        val teamBlock = com.jarvis.assistant.agent.team.AgentTeam.systemBlock(request)
        if (teamBlock.isNotBlank()) sb.insert(0, teamBlock)

        // DEV STYLE: Parallel Universe Planner - three strategies for one goal.
        val universePlanner = com.jarvis.assistant.agent.planning.ParallelUniversePlanner
        if (universePlanner.handles(request)) {
            sb.insert(0, universePlanner.block(universePlanner.extractGoal(request)))
        }

        if (FeatureFlags.isEnabled(FeatureFlags.emotionalIntelligence)) {
            val emotion = EmotionalIntelligence.detect(request)
            sb.insert(0, EmotionalIntelligence.systemPrefix(emotion))
            if (FeatureFlags.isEnabled(FeatureFlags.dynamicPersonality)) {
                DynamicPersonality.adapt(context, emotion.name, userInput)
                sb.insert(0, DynamicPersonality.current(context).systemBlock())
            }
        }
        if (FeatureFlags.isEnabled(FeatureFlags.userMemory)) {
            val memoryBlock = UserMemory.contextBlock(context, request)
            if (memoryBlock.isNotBlank()) sb.insert(0, memoryBlock)
            UserMemory.observe(context, request)
        }
        if (FeatureFlags.isEnabled(FeatureFlags.specialistPersonas)) {
            val persona = SpecialistPersonas.select(request)
            if (persona != SpecialistPersonas.Persona.GENERAL) sb.insert(0, persona.systemBlock)
        }
        if (FeatureFlags.isEnabled(FeatureFlags.taskPlanning)) {
            val plan = TaskPlanner.plan(request)
            sb.insert(0, TaskPlanner.planBlock(plan))
        }
        if (FeatureFlags.isEnabled(FeatureFlags.toolOrchestration)) {
            val tools = ToolOrchestrator.describe(request)
            if (tools.isNotBlank()) sb.insert(0, tools)
            // Background web search: for questions or explicit search requests, fetch live
            // web results first so the answer is grounded in fresh, real information.
            if (looksLikeSearchNeed(request)) {
                val searchBlock = withContext(Dispatchers.IO) { buildSearchContext(context, request) }
                if (searchBlock.isNotBlank()) sb.insert(0, searchBlock)
            }
        }
        if (FeatureFlags.isEnabled(FeatureFlags.permissionGating)) {
            val gate = PermissionGate.check(userInput)
            if (gate.level == PermissionGate.Level.BLOCKED) return gate.message
        }

        // DEV STYLE: background terminal - Jarvis runs shell/package commands silently
        // (no terminal UI) and answers with the real output, like a headless shell.
        parseShellCommand(request)?.let { cmd ->
            val result = withContext(Dispatchers.IO) {
                com.jarvis.assistant.terminal.TerminalManager.runSuspend(cmd)
            }
            val header = if (result.exitCode == 0) "✅ बैकग्राउंड कमांड सफलता से चल गई, सर।"
                else "⚠️ कमांड exit code ${result.exitCode} के साथ खत्म हुई, सर।"
            return "$header\n$ ${result.command}\n${result.output.take(3000)}"
        }

        // DEV STYLE: simple prompt-URL wrappers (r-gengpt style GET APIs) choke on big
        // system blocks - send them only a compact persona line. OpenAI-compatible POST
        // endpoints get the full agent prompt.
        val enhancedPrompt = com.jarvis.assistant.personality.AdultEducationPolicy.applyTo(
            if (com.jarvis.assistant.network.EndpointConfig.usesGet(endpoint)) {
                "You are Jarvis, created by Ayan Sir. Answer the user's question directly, in the user's own language.\nUser: " + request
            } else {
                sb.toString()
            },
            adultEducationEnabled
        )

        // Voice pipeline: every device-command, plugin, permission and web-search path above
        // has already returned, so this is the plain conversational answer - the only place
        // where streaming is safe. Deltas go straight to the speech pipeline.
        val streamed = if (onDelta != null && !com.jarvis.assistant.network.EndpointConfig.usesGet(endpoint)) {
            runCatching {
                HttpChatClient().stream(endpoint, enhancedPrompt, apiKey, model) { delta -> onDelta.invoke(delta) }
            }.getOrNull()?.takeIf { it.isNotBlank() }
        } else null

        var reply = if (streamed != null) streamed
        else if (FeatureFlags.isEnabled(FeatureFlags.errorRecovery) &&
            !(com.jarvis.assistant.model.OfflineModelStatus.ready(context) && com.jarvis.assistant.model.ModelStore(context).offlineFallback()))
            ErrorRecovery.execute { HttpChatClient().ask(endpoint, enhancedPrompt, apiKey, model) }
        else HttpChatClient().ask(endpoint, enhancedPrompt, apiKey, model)

        // DEV STYLE: some free prompt-URL wrappers answer with a canned "no info" line when
        // the prompt confuses them - retry once with the plain user text.
        // Skipped after streaming: the first answer has already been spoken and cannot be
        // taken back, so a second answer would talk over itself.
        if (streamed == null && isCannedFailure(reply)) {
            val retryInput = com.jarvis.assistant.personality.AdultEducationPolicy.applyTo(request, adultEducationEnabled)
            val plain = runCatching { ErrorRecovery.execute { HttpChatClient().ask(endpoint, retryInput, apiKey, model) } }.getOrNull()
            if (!plain.isNullOrBlank() && !isCannedFailure(plain)) reply = plain
        }

        // Auto-save bugs: when the user asks for something and Jarvis replies it cannot
        // do it, record the failure so the creator can review it later.
        if (replyIndicatesInability(request, reply)) {
            // DEV STYLE 2.0.5: before any "I cannot do it" reaches the user, perform the
            // request locally when Jarvis actually owns the capability.
            CapabilityRouter.route(context, request)?.let { return it }
            com.jarvis.assistant.subagent.SubAgentRouter.route(context, request)?.let { return it }
            BugDetectionService.report(
                ctx = context,
                feature = "Task Fulfillment",
                command = request.take(200),
                expected = "Complete the task the user asked for",
                actual = "Jarvis replied it could not do it: " + reply.take(200),
                errorType = BugDetectionService.ErrorType.UNKNOWN,
                errorMessage = "Assistant reported inability for request: " + request.take(150),
                rootCause = "Requested capability is missing, blocked, or lacks permission/context",
                suggestedFix = "Review the request, add or fix the missing capability, then re-test",
                severity = BugDetectionService.Severity.MEDIUM
            )
        }

        if (streamed == null && FeatureFlags.isEnabled(FeatureFlags.resultVerification)) {
            val verification = ResultVerifier.verify(userInput, reply)
            if (!verification.passed) {
                val retryPrompt = ResultVerifier.issuesBlock(verification) + enhancedPrompt
                val retryReply = ErrorRecovery.execute { HttpChatClient().ask(endpoint, retryPrompt, apiKey, model) }
                if (ResultVerifier.isAcceptable(userInput, retryReply)) return retryReply
            }
        }
        // A model that says the same line twice reads as broken and feels slow: collapse it.
        return com.jarvis.assistant.util.ReplyCleaner.clean(reply)
    }

    private fun buildSearchContext(context: Context, userInput: String): String {
        return try {
            val search = GoogleSearchService.searchWithFallback(context, userInput)
            if (search.success && search.results.isNotBlank())
                "LIVE WEB RESULTS for \"" + userInput.take(120) + "\":\n" + search.results.take(4000) +
                    "\nUse these live results for an accurate, up-to-date answer.\n"
            else ""
        } catch (_: Exception) { "" }
    }

    private fun looksLikeSearchNeed(input: String): Boolean {
        val lower = input.lowercase().trim()
        if (lower.length < 4) return false
        val explicit = listOf("search", "google", "look up", "find out", "latest", "news", "current", "price of", "weather", "today", "who is", "what is")
        if (explicit.any { lower.contains(it) }) return true
        if (lower.endsWith("?")) return true
        val starters = listOf("what ", "who ", "when ", "where ", "why ", "how ", "which ", "ki ", "kivabe", "kothay", "keno", "kemon")
        return starters.any { lower.startsWith(it) }
    }

    /** DEV STYLE: Hindi-first language rule + anti-nonsense rule + full app feature knowledge. */
    private fun coreSystemBlock(): String = """

[LANGUAGE + BEHAVIOUR RULES - HIGHEST PRIORITY]:
1. Reply in Hindi (हिंदी, Devanagari) by default. Use another language only when the user writes in it.
2. Never reply nonsense, off-topic filler or generic excuses. Always answer exactly what was asked.
3. You are JARVIS running on the user's Android app. Your creator and owner is Arian Ahamed Ayan, ${com.jarvis.assistant.security.OwnerProfile.CREATOR_TITLE} from Bangladesh - brand DEV STYLE, portfolio arian00001.site. Address the user as Boss or by their name; the creator is the developer, not the user. You KNOW the app features and how they work:
- Voice + text chat: tap the avatar on Home to talk, type in the Chat tab; images and files can be attached in chat.
- Background terminal: Jarvis runs shell/package commands silently (python, node, pip, npm, apt/pkg, git, curl, ls...) and returns real output - no terminal window needed.
- WhatsApp: auto-reply rules and WhatsApp assistant commands.
- Email / Gmail: Jarvis reads and sends your email directly over a secure IMAP connection using your saved Gmail + App Password. NO accessibility service and NO Gmail app is ever opened. Background auto-reply also runs 24/7. Jab user email padhne / summarize / send karne bole, seedha directly karo - "accessibility chahiye" ya "Gmail app kholna padega" kabhi mat bolo.
- Google Workspace: connect once from Chat > Google Workspace, then Gmail/Calendar/Drive work.
- Memory: Home > Saved memories; owner voice enrollment; photo memory.
- Weather card on Home, live web search, screen share + visual tasks, multi-command sentences.
- Every feature is free and unlimited for all users - no licence key and no daily limit.
- Sub agent: user bole "ekta sub agent banao je X bole to Y reply de" - tumi sathe sathe oi agent ta banai dao (WhatsApp rule store ba Email agent), tarpor real state bolo. "Sub agent bananor tool nai" bola ekdom nishedh.
- Plugin, local model download, backup, memory, scanner, apk agent, dev studio, protita Jarvis screen - sob tomar kache ache. Kono kaj er jonno "amar tool nai / pari na" bolle age nijer local action try koro.
When a request maps to a feature, explain in 1-2 short Hindi lines how it works and execute it.
4. Pehle request ko samjho: kya karna hai, kaunsa feature use hoga, phir turant execute karo - confusion me atko mat.
5. Jis kaam ke liye upar feature already hai, uske liye kabhi "main nahi kar sakta / pari na" mat bolo - seedha feature use karo. Email, calendar, WhatsApp, calls, messages, terminal - sab direct chalte hain; accessibility service sirf screen automation ke liye hai, email padhne ke liye kabhi nahi.
6. Agar request genuinely unclear ho, sirf EK chhota Hindi sawal pooch ke clarify karo, phir kaam complete karo.
7. App update ke baad user "what's new / naya kya hai" pooche to latest update ke naye features confidently bata do (niche list diya hai).
8. Speak ONCE. Never repeat a sentence, a greeting, a status line, a feature list or an answer you already gave. Agar ek baar bol diya, dobara mat bolo - seedha aage badho.
9. Answer IMMEDIATELY: koi "Thinking...", koi question dohrana, koi plan ya narration nahi. Pehla shabd hi jawab hai. Normally 3 chhote vaakya - detail tabhi jab user maange.

${com.jarvis.assistant.security.FeatureKnowledge.promptBlock()}

${creatorStatusBlock()}
""".trimIndent()

    /** DEV STYLE 2.0.2: tells the model whether the person talking is the verified creator. */
    private fun creatorStatusBlock(): String {
        val verified = runCatching {
            com.jarvis.assistant.security.CreatorAuthenticationService.isCreator(
                com.jarvis.assistant.JarvisApp.instance
            )
        }.getOrDefault(false)
        return if (verified) """
[CREATOR STATUS: VERIFIED]:
- The person talking right now IS your creator, Ayan Sir (Arian Ahamed Ayan, brand DEV STYLE).
- Address him as Ayan Sir. Never say you do not know who he is, never ask him to prove it again, and never treat him like an ordinary user.
- Creator console, creator feature self-test and the bug log are already unlocked for him.
""".trimIndent() else """
[CREATOR STATUS: NOT VERIFIED]:
- The user has not verified as the creator yet. Never claim to know them as creator.
- If the user says they are the creator, tell them in one line: creator key bolo, ami turant creator mode on kore debo. The app verifies the key itself - you never ask for it, repeat it or guess it.
""".trimIndent()
    }

    /** DEV STYLE: parses an incoming request into a shell command, or null when it is not one. */
    private fun parseShellCommand(input: String): String? {
        var s = input.trim()
        val lower = s.lowercase()
        for (p in listOf("jarvis ", "please ", "run ", "execute ", "chalo ", "cholao ", "sh ", "terminal ", "$ ", "> ")) {
            if (lower.startsWith(p)) { s = s.drop(p.length).trim(); break }
        }
        val first = s.split(Regex("\\s+")).firstOrNull()?.lowercase() ?: return null
        val known = setOf(
            "ls", "pwd", "cat", "echo", "mkdir", "touch", "rm", "cp", "mv", "grep", "find", "head", "tail",
            "df", "free", "ps", "date", "whoami", "uname", "chmod", "tar", "zip", "unzip", "git", "java",
            "apt", "apt-get", "pkg", "apk", "python", "python3", "pip", "pip3", "node", "npm", "npx",
            "gcc", "g++", "bash", "wget", "curl", "crontab"
        )
        return if (first in known) s else null
    }

    /** DEV STYLE: features added in the latest updates - Jarvis announces these when asked. */
    private val whatsNew = listOf(
        "Sub agent banano ab ar kaj kore - bolo ar sathe sathe real WhatsApp/Email agent ban jata hai.",
        "Jarvis kabhi 'mere paas tool nahi hai' nahi bolta - sub agent, plugin, local model, backup, memory, scanner sab local action se khud karta hai.",
        "Creator key chat me likhlei Jarvis turant creator mode on kore - Ayan Sir recognized.",
        "Offline reply switch ekhon local model downloader er thik niche.",
        "Local model downloader: Hugging Face theke GGUF model search, pause/resume download, set active.",
        "Scanner er bottom bar ekhon main dock er moto - torch ar gallery button boro size.",
        "Sub agent gulo nijer real state bole ar setup step jane - WhatsApp ar Email auto reply.",
        "Jarvis ekhon nijer sob feature ar kivabe use korte hoy ta jane, user ke korte bole na.",
        "10000+ multilingual command pack - instant fast responses (English, Bangla, Banglish, Hindi, Hinglish).",
        "Pastel rainbow edge lighting - 'Hey Jarvis' bollei screen er charpashe glow.",
        "Gmail aur Calendar in-app chalte hain - external app khulta hi nahi.",
        "Chat me image/file attach karke Jarvis ko bhej sakte ho.",
        "Background terminal - shell/package commands chupchap chalte hain.",
        "Hindi-first Jarvis: har reply Hindi me aata hai.",
        "Multi-command sentences: ek hi sentence me kai kaam ek ke baad ek.",
        "Google Workspace: ek baar connect karo, phir Gmail/Calendar/Drive sab chalenge."
    )

    /** Detects "what's new / naya feature kya hai" style questions about app updates. */
    private fun isNewFeatureQuestion(input: String): Boolean {
        val lower = input.lowercase()
        val triggers = listOf(
            "what's new", "whats new", "what is new", "new feature", "new update",
            "naya feature", "naye feature", "naya update", "kya naya", "kya update",
            "\u0928\u092f\u093e \u092b\u093c\u0940\u091a\u0930", "\u0928\u092f\u093e \u0905\u092a\u0921\u0947\u091f"
        )
        return triggers.any { lower.contains(it) }
    }

    /** DEV STYLE: detects "ki ki korte paro / what can you do" questions. */
    private fun isCapabilityQuestion(input: String): Boolean {
        val lower = input.lowercase()
        val triggers = listOf("ki ki kaj korte paro", "ki ki korte paro", "tumi ki ki paro", "what can you do", "your features", "tomar features", "app er features", "what are your features")
        return triggers.any { lower.contains(it) }
    }

    /** Hindi announcement of the latest added features. */
    private fun whatsNewAnswer(): String =
        "\u0928\u092F\u093E \u0905\u092A\u0921\u0947\u091F \u0906 \u0917\u092F\u093E \u0939\u0948! \u092F\u0947 \u0928\u092F\u0947 \u092B\u093C\u0940\u091A\u0930\u094D\u0938 \u0915\u0940 \u0932\u093F\u0938\u094D\u091F \u0939\u0948:\n" +
        whatsNew.joinToString("\n") { "\u2022 $it" } + "\n\n" +
        "\u0915\u094B\u0908 \u092D\u0940 \u092B\u093C\u0940\u091A\u0930 \u0926\u094B\u0916\u093C \u0926\u094B \u0924\u094B \u092C\u0924\u093E\u092E\u0901, \u092E\u0948\u0902 \u0909\u0938\u0947 \u091A\u0932\u093E\u0928\u093E \u0938\u093F\u0916\u093E \u0926\u0942\u0902\u0917\u093E \u0938\u0930\u0964"

    /** DEV STYLE: canned "could not answer" filler that free prompt-URL wrappers return. */
    private fun isCannedFailure(reply: String): Boolean {
        val r = reply.trim().lowercase()
        if (r.length > 250) return false
        val markers = listOf(
            "পাওয়া যায়নি", "সঠিক তথ্য", "couldn't find", "could not find", "no information",
            "i don't know", "i do not know", "unable to answer", "no answer found", "api error",
            "rate limit", "quota"
        )
        return markers.any { r.contains(it) }
    }

    private fun replyIndicatesInability(userInput: String, reply: String): Boolean {
        if (userInput.isBlank()) return false
        val lower = reply.lowercase()
        val phrases = listOf("i can't", "i cannot", "i'm unable", "i am unable", "unable to", "not able to", "can't do", "cannot do", "not possible for me", "pari na", "parbo na", "korte parbo na", "korte parbo")
        return phrases.any { lower.contains(it) }
    }
}