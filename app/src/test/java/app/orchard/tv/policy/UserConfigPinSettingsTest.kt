package app.orchard.tv.policy

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * V47 device switch for the web user-configuration PIN prompts. Missing storage must read as
 * on, matching both fresh installs and upgrades where the key did not exist.
 */
@RunWith(RobolectricTestRunner::class)
class UserConfigPinSettingsTest {
    private lateinit var app: android.app.Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        UserConfigPinSettings.debugResetForTest()
        UserConfigPinSettings.install(app)
    }

    @After
    fun tearDown() {
        UserConfigPinSettings.debugResetForTest()
    }

    @Test
    fun missingSettingDefaultsToRequired() {
        assertTrue(UserConfigPinSettings.isRequired())
        assertTrue(UserConfigPinSettings.required.value)
    }

    @Test
    fun disablingPersistsAcrossAReinstall() {
        assertTrue(UserConfigPinSettings.setRequired(false))
        assertFalse(UserConfigPinSettings.isRequired())

        // Drop only the in-process handle; install must read the stored value again.
        UserConfigPinSettings.debugResetForTest(clearStorage = false)
        UserConfigPinSettings.install(app)
        assertFalse(UserConfigPinSettings.isRequired())
    }

    @Test
    fun enablingAgainRestoresTheDefaultBehavior() {
        assertTrue(UserConfigPinSettings.setRequired(false))
        assertTrue(UserConfigPinSettings.setRequired(true))
        assertTrue(UserConfigPinSettings.isRequired())
    }
}