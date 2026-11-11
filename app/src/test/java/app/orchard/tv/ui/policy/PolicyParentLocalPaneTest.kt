package app.orchard.tv.ui.policy

import com.planyourtv.policy.Activation
import com.planyourtv.policy.LocalRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyParentLocalPaneTest {
    @Test
    fun limitRowsExistOnlyInAutomaticMode() {
        val auto = v6LocalFocusKeys(LocalRules.DEFAULTS, Activation.AUTO, sessionActive = false)
        val manual = v6LocalFocusKeys(LocalRules.DEFAULTS, Activation.MANUAL, sessionActive = false)

        assertTrue("daily" in auto)
        assertTrue("session" in auto)
        // Manual has no daily quota and takes its length from the lock-screen entry, so neither
        // row may advertise a cap the executor does not apply.
        assertFalse("daily" in manual)
        assertFalse("session" in manual)
        assertTrue("rest" in manual)
    }

    @Test
    fun sharedAppsComeBeforeActivationTabs() {
        val keys = v6LocalFocusKeys(LocalRules.DEFAULTS, Activation.MANUAL, sessionActive = false)
        // The app scope and source switch are shared by both activations, so they sit above the
        // timing tabs.
        assertEquals("apps", keys.first())
        assertEquals("study", keys.getOrNull(1))
        assertEquals("sources", keys.getOrNull(2))
        assertEquals("activation-auto", keys.getOrNull(3))
        assertEquals("activation-manual", keys.getOrNull(4))
        // Manual timing has the quiz switch before the first time row.
        assertEquals("quiz", keys.getOrNull(5))
        assertTrue(keys.getOrNull(6)?.startsWith("wd-") == true)
    }

    @Test
    fun quizSwitchExistsOnlyInManualMode() {
        val auto = v6LocalFocusKeys(LocalRules.DEFAULTS, Activation.AUTO, sessionActive = false)
        val manual = v6LocalFocusKeys(LocalRules.DEFAULTS, Activation.MANUAL, sessionActive = false)

        assertFalse("quiz" in auto)
        assertEquals(manual.indexOf("activation-manual") + 1, manual.indexOf("quiz"))
    }

    @Test
    fun windowRowsHaveNoAddAction() {
        val keys = v6LocalFocusKeys(LocalRules.DEFAULTS, Activation.AUTO, sessionActive = false)
        // The TV keeps the existing weekday/weekend windows editable but no longer offers a
        // control to add more.
        assertFalse(keys.any { it.endsWith("-add") })
        assertTrue("wd-0-start" in keys)
        assertTrue("we-0-start" in keys)
    }

    @Test
    fun readOnlyKeepsRuleRowsButRemovesMutatingOnes() {
        val keys = v6LocalFocusKeys(
            LocalRules.DEFAULTS,
            Activation.AUTO,
            sessionActive = true,
            readOnly = true,
        )
        assertEquals("apps", keys.first())
        assertTrue("activation-auto" in keys)
        assertTrue("activation-manual" in keys)
        assertTrue("wd-0-start" in keys)
        assertTrue("daily" in keys)
        assertTrue("rest" in keys)
        assertTrue("apps" in keys)
        assertFalse("wd-add" in keys)
        assertFalse("we-add" in keys)
        assertFalse("end-session" in keys)
    }
}
