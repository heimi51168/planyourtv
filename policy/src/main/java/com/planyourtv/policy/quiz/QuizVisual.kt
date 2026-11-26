package com.planyourtv.policy.quiz

import org.json.JSONObject

/**
 * Optional visual layer attached to a [QuizItem].
 *
 * The prompt, options and answer stay canonical: a visual is an enhancement and
 * may always be dropped without making the question unanswerable. This round
 * only accepts a cached PNG reference (`rich`). The QVD `simple` block from
 * `doc/QUIZ-VISUAL-ARCHITECTURE.md` is intentionally not parsed yet.
 */
data class QuizVisual(
    val schema: Int = SCHEMA_VERSION,
    val mode: String = MODE_AUTO,
    val required: Boolean = true,
    val rich: QuizRichVisual? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 2
        const val MODE_AUTO = "auto"
        const val MODE_RICH = "rich"
        val MODES = setOf(MODE_AUTO, MODE_RICH)
    }
}

/** A content-addressed image reference. It never contains a URL or a file path. */
data class QuizRichVisual(
    val assetId: String,
    val sha256: String,
    val kind: String = QuizVisualCodec.KIND_IMAGE,
    val mime: String = QuizVisualCodec.MIME_PNG,
    val width: Int,
    val height: Int,
    val altText: String = "",
    val essential: Boolean = true,
)

/**
 * Lenient codec for cache/session read-back and strict validation for writers.
 *
 * Cache read-back must never fail a whole question bank because one visual is
 * malformed: [parseOrNull] returns null and leaves the text question intact.
 * Writers can call [issues] before persisting.
 */
object QuizVisualCodec {
    const val KIND_IMAGE = "image"
    const val MIME_PNG = "image/png"

    private const val MAX_ASSET_SEGMENTS = 4
    private const val MAX_ASSET_SEGMENT_CHARS = 64
    private const val MAX_ALT_TEXT_CHARS = 200
    private const val MAX_DIMENSION = 4096
    private const val MAX_PIXELS = 4_000_000

    private val assetSegment = Regex("[A-Za-z0-9_-]{1,$MAX_ASSET_SEGMENT_CHARS}")
    private val sha256 = Regex("sha256:[0-9a-f]{64}")

    fun parseOrNull(raw: JSONObject?): QuizVisual? {
        if (raw == null) return null
        return runCatching { parse(raw) }.getOrNull()
    }

    private fun parse(raw: JSONObject): QuizVisual? {
        if (raw.optInt("schema", -1) != QuizVisual.SCHEMA_VERSION) return null
        if (raw.has("simple") && !raw.isNull("simple")) return null
        val mode = raw.optString("mode", QuizVisual.MODE_AUTO).trim()
        if (mode !in QuizVisual.MODES) return null
        if (!raw.optBoolean("required", false)) return null
        val richJson = raw.optJSONObject("rich") ?: return null
        val assetId = richJson.optString("assetId", "").trim()
        val digest = richJson.optString("sha256", "").trim()
        val kind = richJson.optString("kind", KIND_IMAGE).trim()
        val mime = richJson.optString("mime", MIME_PNG).trim()
        val width = richJson.optInt("width", 0)
        val height = richJson.optInt("height", 0)
        val altText = richJson.optString("altText", "")
        val essential = richJson.optBoolean("essential", true)
        val rich = QuizRichVisual(
            assetId = assetId,
            sha256 = digest,
            kind = kind,
            mime = mime,
            width = width,
            height = height,
            altText = altText,
            essential = essential,
        )
        return QuizVisual(schema = QuizVisual.SCHEMA_VERSION, mode = mode, required = true, rich = rich)
            .takeIf { issues(it).isEmpty() }
    }

    fun encode(visual: QuizVisual): JSONObject = JSONObject()
        .put("schema", visual.schema)
        .put("mode", visual.mode)
        .put("required", visual.required)
        .apply {
            visual.rich?.let { rich ->
                put(
                    "rich",
                    JSONObject()
                        .put("assetId", rich.assetId)
                        .put("sha256", rich.sha256)
                        .put("kind", rich.kind)
                        .put("mime", rich.mime)
                        .put("width", rich.width)
                        .put("height", rich.height)
                        .put("altText", rich.altText)
                        .put("essential", rich.essential),
                )
            }
        }

    /** Stable identity used by the question-cache fingerprint. */
    fun canonicalKey(visual: QuizVisual): String? {
        val rich = visual.rich ?: return null
        if (issues(visual).isNotEmpty()) return null
        return "${rich.assetId}:${rich.sha256}"
    }

    fun issues(visual: QuizVisual, prefix: String = "visual"): List<String> {
        val errors = mutableListOf<String>()
        if (visual.schema != QuizVisual.SCHEMA_VERSION) errors += "$prefix.schema: unsupported"
        if (visual.mode !in QuizVisual.MODES) errors += "$prefix.mode: unsupported"
        if (!visual.required) errors += "$prefix.required: must be true"
        val rich = visual.rich ?: run {
            errors += "$prefix.rich: required"
            return errors
        }
        if (!isValidAssetId(rich.assetId)) errors += "$prefix.rich.assetId: invalid"
        if (!sha256.matches(rich.sha256)) errors += "$prefix.rich.sha256: invalid"
        if (rich.kind != KIND_IMAGE) errors += "$prefix.rich.kind: unsupported"
        if (rich.mime != MIME_PNG) errors += "$prefix.rich.mime: unsupported"
        if (rich.width !in 1..MAX_DIMENSION || rich.height !in 1..MAX_DIMENSION) {
            errors += "$prefix.rich.size: out of range"
        } else if (rich.width.toLong() * rich.height > MAX_PIXELS) {
            errors += "$prefix.rich.size: too many pixels"
        }
        if (rich.altText.length > MAX_ALT_TEXT_CHARS) errors += "$prefix.rich.altText: too long"
        return errors
    }

    /**
     * True when [value] is a safe relative asset namespace. The path is built
     * from this value, so `.`/`..`, empty segments and absolute paths are banned.
     */
    fun isValidAssetId(value: String): Boolean {
        val parts = value.split('/')
        return parts.size in 1..MAX_ASSET_SEGMENTS && parts.all { assetSegment.matches(it) }
    }

    /**
     * Returns the 64 lowercase hex characters after `sha256:`; null means the
     * digest is not a valid content address.
     */
    fun shaHex(value: String): String? =
        value.takeIf { sha256.matches(it) }?.removePrefix("sha256:")
}