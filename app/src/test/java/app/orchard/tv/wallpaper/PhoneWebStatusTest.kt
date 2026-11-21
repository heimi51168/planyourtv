package app.orchard.tv.wallpaper

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhoneWebStatusTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun loopbackAndUnknownAddressesAreNotPhoneAccesses() {
        assertTrue(PhoneServer.isLoopbackHost(null))
        assertTrue(PhoneServer.isLoopbackHost("127.0.0.1"))
        assertTrue(PhoneServer.isLoopbackHost("127.0.0.2"))
        assertTrue(PhoneServer.isLoopbackHost("::1"))
        assertTrue(PhoneServer.isLoopbackHost("[::1]"))
        assertTrue(PhoneServer.isLoopbackHost("::ffff:127.0.0.1"))
        // TEST-NET-2 documentation address: not loopback, and not a real LAN address.
        assertFalse(PhoneServer.isLoopbackHost("198.51.100.20"))
        assertFalse(PhoneServer.isLoopbackHost("fe80::1%wlan0"))
    }

    @Test
    fun onlyTheParentPagesCountAsPhoneAccess() {
        assertTrue(PhoneServer.isTrackedParentPath("/quiz"))
        assertTrue(PhoneServer.isTrackedParentPath("/quiz/"))
        assertTrue(PhoneServer.isTrackedParentPath("/api/parent/gate"))
        assertTrue(PhoneServer.isTrackedParentPath("/parent.js"))
        assertTrue(PhoneServer.isTrackedParentPath("/parent.css"))
        assertTrue(PhoneServer.isTrackedParentPath("/parent-kids-default.jpg"))
        assertTrue(PhoneServer.isTrackedParentPath("/parent-avatar"))
        assertFalse(PhoneServer.isTrackedParentPath("/"))
        assertFalse(PhoneServer.isTrackedParentPath("/api/state"))
        assertFalse(PhoneServer.isTrackedParentPath("/quizfoo"))
    }

    @Test
    fun loopbackProbeReachesTheParentGate() {
        val server = requireNotNull(PhoneServer.listen(context)) { "phone page did not start" }
        try {
            assertTrue("parent gate did not answer on loopback", server.probeParentWeb())
        } finally {
            server.stop()
        }
    }
}