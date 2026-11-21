package app.orchard.tv.ui.policy

import com.planyourtv.policy.LocalRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyParentLocalPaneTest {
    @Test
    fun sharedAppRowsComeBeforeTheManualTimingRows() {
        val keys = v6LocalFocusKeys(LocalRules.DEFAULTS, sessionActive = false)

        // The app scope and source switch are shared by every member, so they stay above the
        // manual time rows.
        assertEquals("apps", keys.first())
        assertEquals("study", keys.getOrNull(1))
        assertEquals("sources", keys.getOrNull(2))
        assertEquals("quiz", keys.getOrNull(3))
        assertTrue(keys.getOrNull(4)?.startsWith("wd-") == true)
    }

    @Test
    fun noActivationTabOrQuotaEditorsRemain() {
        val keys = v6LocalFocusKeys(LocalRules.DEFAULTS, sessionActive = false)

        // Manual time takes its segment length from the lock-screen entry, so neither an
        // activation tab nor a daily/session cap may advertise a rule the executor ignores.
        assertFalse(keys.any { it.startsWith("activation-") })
        assertFalse("daily" in keys)
        assertFalse("session" in keys)
        assertTrue("rest" in keys)
        assertTrue("buffer" in keys)
    }

    @Test
    fun windowRowsHaveNoAddAction() {
        val keys = v6LocalFocusKeys(LocalRules.DEFAULTS, sessionActive = false)
        // The TV keeps the existing weekday/weekend windows editable but no longer offers a
        // control to add more.
        assertFalse(keys.any { it.endsWith("-add") })
        assertTrue("wd-0-start" in keys)
        assertTrue("we-0-start" in keys)
    }

    @Test
    fun endingASessionIsOfferedOnlyWhileOneIsRunning() {
        assertFalse("end-session" in v6LocalFocusKeys(LocalRules.DEFAULTS, sessionActive = false))
        assertTrue("end-session" in v6LocalFocusKeys(LocalRules.DEFAULTS, sessionActive = true))
    }

    @Test
    fun readOnlyKeepsRuleRowsButRemovesMutatingOnes() {
        val keys = v6LocalFocusKeys(
            LocalRules.DEFAULTS,
            sessionActive = true,
            readOnly = true,
        )
        assertEquals("apps", keys.first())
        assertTrue("wd-0-start" in keys)
        assertTrue("we-0-start" in keys)
        assertTrue("rest" in keys)
        assertTrue("buffer" in keys)
        assertFalse(keys.any { it.endsWith("-add") })
        assertFalse(keys.any { it.endsWith("-remove") })
        assertFalse("end-session" in keys)
    }
}