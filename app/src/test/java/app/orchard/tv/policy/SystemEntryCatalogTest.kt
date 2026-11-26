package app.orchard.tv.policy

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SystemEntryCatalogTest {
    @After
    fun tearDown() {
        SystemEntryCatalog.debugSetForTest(emptySet())
    }

    @Test
    fun knownBrandEntriesAreCataloguedAndTclGuardIsHideOnly() {
        val app = RuntimeEnvironment.getApplication()
        SystemEntryCatalog.install(app)

        assertTrue(SystemEntryCatalog.isSystemEntry("com.xiaomi.mitv.settings"))
        assertTrue(SystemEntryCatalog.isSystemEntry("com.xiaomi.mitv.tvmanager"))
        assertTrue(SystemEntryCatalog.isSystemEntry("com.xiaomi.mitv.appstore"))
        assertTrue(SystemEntryCatalog.isSystemEntry("com.tcl.settings"))
        assertTrue(SystemEntryCatalog.isSystemEntry("com.tcl.guard"))
        assertTrue(SystemEntryCatalog.isSystemEntry("com.tcl.appmarket2"))
        assertTrue(SystemEntryCatalog.isSystemEntry("com.mipt.store"))
        assertFalse(SystemEntryCatalog.isSystemEntry(app.packageName))
        assertFalse(SystemEntryCatalog.isSystemEntry("com.example.video"))

        assertTrue(SystemEntryCatalog.blocksLaunch("com.xiaomi.mitv.settings"))
        assertFalse(SystemEntryCatalog.blocksLaunch("com.tcl.guard"))
    }
}