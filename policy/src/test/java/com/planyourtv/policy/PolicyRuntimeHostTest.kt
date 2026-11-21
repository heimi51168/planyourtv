package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The product fallback host survives a guard that went away without detaching. */
class PolicyRuntimeHostTest {
    private class RecordingHost : PolicyHost {
        var broughtHome = 0

        override fun bringHome() {
            broughtHome++
        }

        override fun lockScreen(): Boolean = false
    }

    private companion object {
        val guardHost = RecordingHost()
        val fallbackHost = RecordingHost()
    }

    @Before
    fun clearHosts() {
        PolicyRuntime.detachHost(guardHost)
        PolicyRuntime.detachFallbackHost(fallbackHost)
        guardHost.broughtHome = 0
        fallbackHost.broughtHome = 0
    }

    @Test
    fun fallbackHostIsPreferredWhileAttached() {
        PolicyRuntime.attachHost(guardHost)
        PolicyRuntime.attachFallbackHost(fallbackHost)

        PolicyRuntime.bringHome()

        assertEquals(0, guardHost.broughtHome)
        assertEquals(1, fallbackHost.broughtHome)
    }

    @Test
    fun guardHostIsUsedWhenNoFallbackIsInstalled() {
        PolicyRuntime.attachHost(guardHost)

        PolicyRuntime.bringHome()

        assertEquals(1, guardHost.broughtHome)
    }

    @Test
    fun detachingTheFallbackReturnsToTheGuardHost() {
        PolicyRuntime.attachHost(guardHost)
        PolicyRuntime.attachFallbackHost(fallbackHost)
        PolicyRuntime.detachFallbackHost(fallbackHost)

        PolicyRuntime.bringHome()

        assertEquals(1, guardHost.broughtHome)
    }
}