package app.orchard.tv.parent

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class MimeChooserTest {

    @Test
    fun `mime types are kept when there are no extensions`() {
        assertArrayEquals(
            arrayOf("application/json", "text/plain"),
            MimeChooser.types(listOf("application/json", "text/plain")),
        )
    }

    @Test
    fun `extension in the accept list opens the picker to all files`() {
        assertArrayEquals(
            arrayOf("*/*"),
            MimeChooser.types(listOf(".json", ".txt", "application/json", "text/plain")),
        )
    }

    @Test
    fun `only extensions opens the picker to all files`() {
        assertArrayEquals(arrayOf("*/*"), MimeChooser.types(listOf(".json")))
    }

    @Test
    fun `missing accept list opens the picker to all files`() {
        assertArrayEquals(arrayOf("*/*"), MimeChooser.types(null))
        assertArrayEquals(arrayOf("*/*"), MimeChooser.types(emptyList()))
    }

    @Test
    fun `blank and overlong tokens are ignored`() {
        assertArrayEquals(
            arrayOf("image/*"),
            MimeChooser.types(listOf("", " ", "image/*", "a".repeat(120))),
        )
    }
}
