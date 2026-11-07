package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentAuthSessionTest {
    @Test
    fun `successful lock screen pin is shared`() {
        val auth = ParentAuthSession()
        auth.onLockScreenPin("456258", PinResult.OK)
        assertTrue(auth.authenticated.value)
        assertEquals("456258", auth.verifiedPin)
    }

    @Test
    fun `failed lock screen pin does not authenticate`() {
        val auth = ParentAuthSession()
        auth.onLockScreenPin("000000", PinResult.WRONG)
        auth.onLockScreenPin("000000", PinResult.NOT_SET)
        auth.onLockScreenPin("000000", PinResult.LOCKED_OUT)
        assertFalse(auth.authenticated.value)
        assertNull(auth.verifiedPin)
    }

    @Test
    fun `temporary unlock keeps the proof`() {
        val auth = ParentAuthSession()
        auth.onLockScreenPin("456258", PinResult.OK)
        auth.onGuardStatus(GuardStatus.PARENT_OVERRIDE)
        assertTrue(auth.authenticated.value)
        assertEquals("456258", auth.verifiedPin)
    }

    @Test
    fun `guard resume expires the proof`() {
        val auth = ParentAuthSession()
        auth.onLockScreenPin("456258", PinResult.OK)
        auth.onGuardStatus(GuardStatus.ENFORCING)
        assertFalse(auth.authenticated.value)
        assertNull(auth.verifiedPin)
    }

    @Test
    fun `screen off and mode switch clear the proof`() {
        val auth = ParentAuthSession()
        auth.onLockScreenPin("456258", PinResult.OK)
        auth.clear()
        assertFalse(auth.authenticated.value)
        assertNull(auth.verifiedPin)
    }

    @Test
    fun `parent center verification is shared without a lock screen unlock`() {
        val auth = ParentAuthSession()
        auth.onParentVerified("456258")
        assertTrue(auth.authenticated.value)
        assertEquals("456258", auth.verifiedPin)
    }

    @Test
    fun `restored proof is authenticated but has no plaintext pin`() {
        var clock = 1_000L
        var stored = 2_000L
        val auth = ParentAuthSession(
            now = { clock },
            loadExpiresAt = { stored },
            saveExpiresAt = { stored = it },
            clearExpiresAt = { stored = 0L },
        )
        assertTrue(auth.authenticated.value)
        assertNull(auth.verifiedPin)

        clock = stored
        auth.onGuardStatus(GuardStatus.PARENT_OVERRIDE)
        assertFalse(auth.authenticated.value)
        assertEquals(0L, stored)
    }

    @Test
    fun `reload restores a proof after the store becomes ready`() {
        var storeReady = false
        var stored = 0L
        val auth = ParentAuthSession(
            now = { 1_000L },
            loadExpiresAt = { if (storeReady) stored else 0L },
            saveExpiresAt = { stored = it },
            clearExpiresAt = { stored = 0L },
        )
        // The object is built before MMKV is initialized; the first read must not look like
        // "no proof" forever.
        assertFalse(auth.authenticated.value)

        storeReady = true
        stored = 2_000L
        auth.reloadFromStore()

        assertTrue(auth.authenticated.value)
        assertNull(auth.verifiedPin)
    }

    @Test
    fun `reload never replaces a pin verified in this process`() {
        var stored = 0L
        val auth = ParentAuthSession(
            now = { 1_000L },
            loadExpiresAt = { stored },
            saveExpiresAt = { stored = it },
            clearExpiresAt = { stored = 0L },
        )
        auth.onParentVerified("456258")
        stored = 9_000L
        auth.reloadFromStore()

        assertTrue(auth.authenticated.value)
        assertEquals("456258", auth.verifiedPin)
    }

    @Test
    fun `expired stored proof is discarded on creation`() {
        var cleared = false
        val auth = ParentAuthSession(
            now = { 5_000L },
            loadExpiresAt = { 4_000L },
            saveExpiresAt = {},
            clearExpiresAt = { cleared = true },
        )
        assertFalse(auth.authenticated.value)
        assertNull(auth.verifiedPin)
        assertTrue(cleared)
    }
}
