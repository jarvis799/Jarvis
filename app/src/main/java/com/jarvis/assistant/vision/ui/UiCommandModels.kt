package com.jarvis.assistant.vision.ui

import com.jarvis.assistant.vision.UiElementInfo

/** The single UI action a voice command asks for. */
enum class UiActionKind {
    CLICK,
    LONG_CLICK,
    SET_TEXT,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    FIND_AND_ACT,
    BACK,
    HOME,
    RECENTS,
    DESCRIBE
}

/** How the spoken target should be matched against grounded screen evidence. */
enum class UiTargetSource { TEXT, DESCRIPTION, RESOURCE_ID, LABEL_HINT, SELECTED_FIELD, SCROLL_CONTAINER, NONE }

data class UiTargetSpec(
    val query: String,
    val source: UiTargetSource = UiTargetSource.TEXT,
    val ordinal: Int? = null,
    val isToggle: Boolean = false,
    val prefersEditable: Boolean = false,
    val hints: List<String> = emptyList()
)

data class UiCommandPlan(
    val action: UiActionKind,
    val target: UiTargetSpec? = null,
    val text: String? = null,
    val expected: String,
    val maxAttempts: Int = 2,
    val needsConfirmation: Boolean = false,
    val confirmationQuestion: String? = null,
    val originalUtterance: String = ""
)

enum class UiOutcome {
    VERIFIED,
    UNVERIFIED,
    AMBIGUOUS,
    TARGET_NOT_FOUND,
    NOT_SUPPORTED,
    BLOCKED,
    FAILED,
    CANCELLED
}

data class UiCandidate(val element: UiElementInfo, val score: Int, val reason: String) {
    val label: String get() = element.primaryLabel
}

data class UiResolution(
    val spec: UiTargetSpec,
    val chosen: UiElementInfo?,
    val candidates: List<UiCandidate> = emptyList(),
    val ambiguous: Boolean = false,
    val usedOcr: Boolean = false,
    val note: String? = null
) {
    val topLabels: List<String> get() = candidates.take(3).map { it.label }.filter { it.isNotBlank() }
}

data class UiStepLog(
    val step: Int,
    val action: String,
    val target: String,
    val result: String,
    val verified: Boolean,
    val detail: String? = null
)

data class UiCommandResult(
    val outcome: UiOutcome,
    val spoken: String,
    val steps: List<UiStepLog> = emptyList(),
    val limitation: String? = null,
    val plan: UiCommandPlan? = null
) {
    val verified: Boolean get() = outcome == UiOutcome.VERIFIED
}
