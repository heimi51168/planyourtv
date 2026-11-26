package app.orchard.tv.quiz

import org.json.JSONObject

/**
 * Parent choices for quiz voice reading.
 *
 * [initialized] is false until the parent saves the AI settings on a build that
 * understands local speech. An absent record therefore keeps the switch looking
 * "on" in the form without starting to upload quiz text to DeepSeek after an
 * update. Cloud quiz speech comes from QB's TTS and does not use the local key,
 * so it uses [cloudActive] instead.
 */
data class QuizSpeechSettings(
    val initialized: Boolean = false,
    val enabled: Boolean = true,
    val readPrompt: Boolean = true,
    val readOptions: Boolean = true,
    val readAnswer: Boolean = true,
) {
    val hasContent: Boolean get() = readPrompt || readOptions || readAnswer

    /** Local DeepSeek gate: the parent enabled a part and saved the setting. */
    val active: Boolean get() = initialized && enabled && hasContent

    /**
     * Cloud quiz gate. QB already owns the question text and generates/caches
     * the audio, so the DeepSeek consent/key gate does not apply; the parent's
     * enabled and per-part choices still do.
     */
    val cloudActive: Boolean get() = enabled && hasContent

    fun toJson(): JSONObject = JSONObject()
        .put("initialized", initialized)
        .put("enabled", enabled)
        .put("readPrompt", readPrompt)
        .put("readOptions", readOptions)
        .put("readAnswer", readAnswer)

    companion object {
        fun fromJson(raw: JSONObject?): QuizSpeechSettings {
            if (raw == null) return QuizSpeechSettings()
            return QuizSpeechSettings(
                initialized = raw.optBoolean("initialized", false),
                enabled = raw.optBoolean("enabled", true),
                readPrompt = raw.optBoolean("readPrompt", true),
                readOptions = raw.optBoolean("readOptions", true),
                readAnswer = raw.optBoolean("readAnswer", true),
            )
        }
    }
}
