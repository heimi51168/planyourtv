package app.orchard.tv.quiz

import org.json.JSONObject

/**
 * The public status DTO. It contains no question text, answer, explanation,
 * session id, PIN, or provider key. Answers stay inside [QuizRuntime.UiState];
 * only the authenticated admin API may ever see them.
 */
object QuizWeb {
    fun statusJson(): JSONObject {
        val state = QuizRuntime.refresh()
        val session = state.session
        val config = state.challengeConfig
        return JSONObject()
            .put("enabled", state.enabled)
            // A second phone can show the "parent already using" notice before
            // asking for the PIN. The flag reveals only that the settings page
            // is occupied, never the token or question content.
            .put("parentInUse", QuizAccess.hasActiveSession())
            // An expired READY record is still stored, but it is not a usable setup.
            .put("configured", state.sessionState == "ready" || state.sessionState == "running")
            .put("sessionState", state.sessionState)
            .put("entry", state.entry)
            .put("sessionQuestionCount", state.questionCount)
            .put("sessionExpiresAt", session?.expiresAt ?: 0L)
            .put("providerConfigured", state.providerConfigured)
            .put("consentAccepted", state.consentAccepted)
            .put("challenge", JSONObject()
                .put("defaultTimeLimitSec", config.defaultTimeLimitSec)
                .put("minutesPerPoint", config.minutesPerPoint)
                .put("pointsPerQuestion", config.pointsPerQuestion)
                .put("minutesPerQuestion", config.minutesPerQuestion)
                .put("pullCount", config.pullCount)
                .put("minCorrectCount", config.minCorrectCount)
                .put("maxEarnedMinutes", config.maxEarnedMinutes)
                .put("shuffleOptions", config.shuffleOptions)
                .put("answerDisplaySec", config.answerDisplaySec))
            .put("questionPool", state.readyQuestionCount)
            .put("questionSource", state.source.name)
            .put("source", state.source.name)
            .put("localBankActive", state.localBankActive)
            .put("bankState", state.bankState)
            .put("bank", JSONObject()
                .put("total", state.bankCounts.total)
                .put("unused", state.bankCounts.unused)
                .put("inRound", state.bankCounts.inRound)
                .put("answered", state.bankCounts.answered)
                .put("correct", state.bankCounts.correct)
                .put("wrong", state.bankCounts.wrong)
                .put("legacy", state.bankCounts.legacy)
                .put("expired", state.bankCounts.expired))
            .put("sourceConfig", state.sourceConfig.encode())
            .put("cache", JSONObject()
                .put("available", state.bankCounts.unused)
                .put("reserved", state.bankCounts.inRound)
                .put("consumed", state.bankCounts.answered)
                .put("expired", state.bankCounts.expired))
    }
}
