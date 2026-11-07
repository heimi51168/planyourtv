package app.orchard.tv.quiz

import org.json.JSONObject
import java.net.URI

/**
 * Provider endpoint metadata. The API key is stored separately and encrypted;
 * this record only holds non-secret routing choices chosen by the parent.
 */
data class QuizProviderMeta(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
) {
    fun toJson(): String = JSONObject()
        .put("baseUrl", baseUrl)
        .put("model", model)
        .toString()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-flash"
        private val MODEL_RE = Regex("[A-Za-z0-9._:-]{1,80}")

        fun fromJson(raw: String?): QuizProviderMeta {
            if (raw.isNullOrBlank()) return QuizProviderMeta()
            return runCatching {
                val root = JSONObject(raw)
                val base = normalizeBaseUrl(root.optString("baseUrl", DEFAULT_BASE_URL))
                    ?: DEFAULT_BASE_URL
                val model = normalizeModel(root.optString("model", DEFAULT_MODEL)) ?: DEFAULT_MODEL
                QuizProviderMeta(base, model)
            }.getOrElse { QuizProviderMeta() }
        }

        /** Returns a normalized HTTPS origin/path, or null when the value is unusable. */
        fun normalizeBaseUrl(value: String): String? {
            val trimmed = value.trim().trimEnd('/')
            if (trimmed.isEmpty() || trimmed.length > 200) return null
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
            if (uri.query != null || uri.fragment != null) return null
            return trimmed
        }

        fun normalizeModel(value: String): String? =
            value.trim().takeIf { MODEL_RE.matches(it) }
    }
}
