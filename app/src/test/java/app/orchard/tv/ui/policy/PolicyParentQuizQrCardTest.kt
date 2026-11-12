package app.orchard.tv.ui.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyParentQuizQrCardTest {
    private fun visible(
        childOn: Boolean = true,
        localMode: Boolean = true,
        bindPaneOpen: Boolean = false,
    ): Boolean = quizWebQrVisible(
        childOn = childOn,
        localMode = localMode,
        bindPaneOpen = bindPaneOpen,
    )

    @Test
    fun localChildModeShowsTheEntry() {
        assertTrue(visible())
    }

    @Test
    fun childModeOffHidesTheEntry() {
        assertFalse(visible(childOn = false))
    }

    @Test
    fun cloudModeHidesTheEntry() {
        assertFalse(visible(localMode = false))
    }

    @Test
    fun bindPaneHidesTheEntry() {
        assertFalse(visible(bindPaneOpen = true))
    }

    @Test
    fun parentAppQrUsesTheStableDownloadRedirect() {
        assertEquals("https://planyourtv.me/download/parent", PARENT_APP_DOWNLOAD_URL)
    }
}
