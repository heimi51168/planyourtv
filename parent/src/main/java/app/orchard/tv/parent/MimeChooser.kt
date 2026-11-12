package app.orchard.tv.parent

/**
 * Converts a page's `accept` attribute into MIME types for the system picker.
 *
 * SAF cannot filter by file extension, and a provider may report a `.json`
 * file as `application/octet-stream`, so an extension-only or mixed accept
 * list falls back to every file type. The page that asked for the file
 * validates it.
 */
object MimeChooser {

    fun types(acceptTypes: List<String>?): Array<String> {
        val accept = acceptTypes.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        if (accept.any { it.startsWith(".") }) return EVERYTHING
        return accept
            .filter { it.contains('/') && it.length <= MAX_TYPE_CHARS }
            .toTypedArray()
            .takeIf { it.isNotEmpty() }
            ?: EVERYTHING
    }

    private val EVERYTHING = arrayOf("*/*")
    private const val MAX_TYPE_CHARS = 100
}
