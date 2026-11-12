package app.orchard.tv.ui.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyParentQuizQrCardTest {
    @Test
    fun parentAppQrUsesTheStableDownloadRedirect() {
        assertEquals("https://planyourtv.me/download/parent", PARENT_APP_DOWNLOAD_URL)
    }
}
