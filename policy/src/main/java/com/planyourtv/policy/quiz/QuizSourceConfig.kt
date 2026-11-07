package com.planyourtv.policy.quiz

import org.json.JSONObject

enum class QuizSource { LOCAL, CLOUD }

/**
 * Source selection and its sync versions. The engine never receives this
 * object; only [QuizSourceCoordinator] and the config sync path read it.
 */
data class QuizSourceConfig(
    val enabled: Boolean = true,
    val source: QuizSource = QuizSource.LOCAL,
    val configVersion: Long = 0,
    val localVersion: Long = 0,
    val updatedAt: Long = 0,
) {
    fun encode(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("source", source.name.lowercase())
        .put("configVersion", configVersion)
        .put("localVersion", localVersion)
        .put("updatedAt", updatedAt)

    companion object {
        fun decode(raw: String?): QuizSourceConfig? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val root = JSONObject(raw)
                val source = when (root.optString("source", "local").lowercase()) {
                    "local" -> QuizSource.LOCAL
                    "cloud" -> QuizSource.CLOUD
                    else -> return null
                }
                QuizSourceConfig(
                    enabled = root.optBoolean("enabled", true),
                    source = source,
                    configVersion = root.optLong("configVersion", 0L).coerceAtLeast(0L),
                    localVersion = root.optLong("localVersion", 0L).coerceAtLeast(0L),
                    updatedAt = root.optLong("updatedAt", 0L).coerceAtLeast(0L),
                )
            }.getOrNull()
        }
    }
}
