package app.orchard.tv.parent

import java.net.URI

/**
 * The only addresses this shell accepts are the Parent Center pages served by
 * the TV: `http://<tv>:<port>/<8-char-token>/quiz`.
 *
 * The page derives its API root from `location.pathname`, so the URL must be
 * loaded exactly as the TV printed it. Normalization only repairs what a person
 * may type or paste (missing scheme, trailing slash, the token-only address
 * under the QR); it never rewrites the token path.
 */
internal object QuizUrl {
    private const val MIN_TOKEN_LENGTH = 4
    private const val MAX_TOKEN_LENGTH = 64

    /** Returns the canonical URL to load, or null when [raw] is not a Parent Center address. */
    fun normalize(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null

        // QR payloads are the bare address, but a label wrapped around it is a
        // common scanning failure; start at the first http(s) marker.
        val start = listOf("http://", "https://")
            .map { text.indexOf(it, ignoreCase = true) }
            .filter { it >= 0 }
            .minOrNull()
        if (start != null && start > 0) text = text.substring(start)
        text = text.substringBefore(' ')
            .substringBefore('\n')
            .substringBefore('\r')
            .substringBefore('\t')
        if (!text.contains("://")) text = "http://$text"

        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (!uri.userInfo.isNullOrEmpty()) return null
        if (uri.host.isNullOrBlank()) return null
        val authority = uri.rawAuthority ?: return null
        if (authority.isBlank()) return null

        val path = (uri.rawPath ?: return null).trimEnd('/')
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val token = if (segments.last() == "quiz") {
            segments.dropLast(1).lastOrNull()
        } else {
            // Accept the address printed under the QR as well; the page itself
            // is one level deeper.
            segments.singleOrNull()
        }
        if (token == null || token.length !in MIN_TOKEN_LENGTH..MAX_TOKEN_LENGTH) return null
        if (!token.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return null

        val quizPath = if (segments.last() == "quiz") path else "$path/quiz"
        val query = uri.rawQuery?.takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty()
        return "$scheme://$authority$quizPath$query"
    }

    /** True when both addresses belong to the same scheme, host and port. */
    fun sameOrigin(a: String, b: String): Boolean {
        val first = runCatching { URI(a) }.getOrNull() ?: return false
        val second = runCatching { URI(b) }.getOrNull() ?: return false
        return first.scheme.equals(second.scheme, ignoreCase = true) &&
            first.host.equals(second.host, ignoreCase = true) &&
            effectivePort(first) == effectivePort(second)
    }

    private fun effectivePort(uri: URI): Int = if (uri.port >= 0) {
        uri.port
    } else {
        when (uri.scheme?.lowercase()) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
    }
}
