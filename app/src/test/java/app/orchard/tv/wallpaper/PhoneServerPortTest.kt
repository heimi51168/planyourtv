package app.orchard.tv.wallpaper

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhoneServerPortTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun fallsBackWhenAerialAlreadyOwnsThePreferredPort() {
        val blocker = ServerSocket()
        try {
            blocker.bind(InetSocketAddress(8790))
        } catch (e: IOException) {
            blocker.close()
            assumeNoException("port 8790 is already in use", e)
            return
        }
        try {
            val prefs = context.getSharedPreferences("phone_page", Context.MODE_PRIVATE)
            prefs.edit().clear().commit()

            val server = requireNotNull(PhoneServer.listen(context)) { "phone page did not start" }
            try {
                assertNotEquals(8790, server.listeningPort)
                assertTrue(server.listeningPort > 0)
                assertEquals(server.listeningPort, prefs.getInt("port", -1))
            } finally {
                server.stop()
            }
        } finally {
            blocker.close()
        }
    }
}
