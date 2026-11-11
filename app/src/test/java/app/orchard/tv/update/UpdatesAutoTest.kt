package app.orchard.tv.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowPackageInstaller
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [UpdatesAutoTest.UnansweredSession::class])
@LooperMode(LooperMode.Mode.PAUSED)
class UpdatesAutoTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val installer get() = app.packageManager.packageInstaller
    private val apk = File(app.cacheDir, "updates/aerial.apk")

    @Before
    fun prepare() {
        Updates.testApk = null
        app.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().clear().commit()
        ReflectionHelpers.setField(Updates, "started", false)
        ReflectionHelpers.setField(Updates, "awaiting", null)
        ReflectionHelpers.setField(Updates, "autoDownloading", false)
        ReflectionHelpers.setField(Updates, "autoArmedFor", null)
        ReflectionHelpers.getField<MutableSet<Int>>(Updates, "abandoned").clear()
        Updates.resetInstall()
        Updates.onHomeResumed()
        // Aerial installed itself last, so from Android 12 the update may go through silently.
        shadowOf(app.packageManager).setInstallSourceInfo(app.packageName, app.packageName, app.packageName)
        apk.parentFile!!.mkdirs()
        apk.writeBytes(byteArrayOf(1, 2, 3))
    }

    @After
    fun cleanup() {
        Updates.onHomeResumed()
        Updates.resetInstall()
        ReflectionHelpers.setField(Updates, "started", false)
        ReflectionHelpers.setField(Updates, "autoDownloading", false)
        ReflectionHelpers.setField(Updates, "autoArmedFor", null)
        Updates.testApk = null
    }

    @Test
    fun autoUpdateIsOffAndTheFirstChoiceIsRemembered() {
        Updates.init(app)
        assertFalse(Updates.auto)
        assertFalse(Updates.autoChoiceMade())

        Updates.setAuto(app, true)
        assertTrue(Updates.auto)
        assertTrue(Updates.autoChoiceMade())

        // A restart keeps the switch where the parent left it.
        ReflectionHelpers.setField(Updates, "started", false)
        Updates.init(app)
        assertTrue(Updates.auto)
        assertTrue(Updates.autoChoiceMade())
    }

    @Test
    fun turningAutoUpdateOffIsAlsoRemembered() {
        Updates.init(app)
        Updates.setAuto(app, true)
        Updates.setAuto(app, false)

        ReflectionHelpers.setField(Updates, "started", false)
        Updates.init(app)
        assertFalse(Updates.auto)
        assertTrue(Updates.autoChoiceMade())
    }

    @Test
    fun autoSessionThatAsksForConfirmationIsAbandonedSilently() {
        Updates.init(app)
        val id = commitAuto()
        assertNull(shadowOf(app).nextStartedActivity)

        answer(id, PackageInstaller.STATUS_PENDING_USER_ACTION, Intent("test.confirm"), auto = true)

        assertNull(installer.getSessionInfo(id))
        assertEquals(Updates.Install.Idle, Updates.install)
        assertFalse(Updates.autoUpdateBusy())
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun autoSessionFailureNeverOpensTheInstaller() {
        Updates.init(app)
        val id = commitAuto()

        answer(id, PackageInstaller.STATUS_FAILURE, auto = true)

        assertEquals(Updates.Install.Idle, Updates.install)
        assertFalse(Updates.autoUpdateBusy())
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun autoSessionTimeoutIsAbandonedWithoutOpeningTheInstaller() {
        Updates.init(app)
        val id = commitAuto()
        advance(119_999)
        assertNotNull(installer.getSessionInfo(id))
        assertNull(shadowOf(app).nextStartedActivity)

        advance(1)

        assertNull(installer.getSessionInfo(id))
        assertEquals(Updates.Install.Idle, Updates.install)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    private fun commitAuto(): Int {
        assertTrue(
            ReflectionHelpers.callInstanceMethod(
                Updates,
                "installBySession",
                ClassParameter.from(Context::class.java, app),
                ClassParameter.from(File::class.java, apk),
                ClassParameter.from(Boolean::class.javaPrimitiveType!!, false),
                ClassParameter.from(Boolean::class.javaPrimitiveType!!, true),
            ),
        )
        assertEquals(Updates.Install.Waiting, Updates.install)
        return installer.mySessions.single().sessionId
    }

    private fun answer(id: Int, status: Int, confirm: Intent? = null, auto: Boolean) {
        UpdateResult().onReceive(app, Intent().apply {
            putExtra(PackageInstaller.EXTRA_SESSION_ID, id)
            putExtra(PackageInstaller.EXTRA_STATUS, status)
            if (auto) putExtra("app.orchard.tv.update.AUTO", true)
            if (confirm != null) putExtra(Intent.EXTRA_INTENT, confirm)
        })
    }

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    // Robolectric normally reports success at commit; model the firmware's missing response.
    @Implements(PackageInstaller.Session::class)
    class UnansweredSession : ShadowPackageInstaller.ShadowSession() {
        @Implementation
        override fun commit(statusReceiver: IntentSender) = Unit
    }
}
