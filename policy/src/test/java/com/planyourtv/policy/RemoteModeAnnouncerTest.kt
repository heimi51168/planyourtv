package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteModeAnnouncerTest {
    @Test
    fun firstObservedModeOnlyPrimesTheBaseline() {
        val announcer = RemoteModeAnnouncer()
        assertNull(announcer.observe(RemotePolicyMode.RULE, active = true))
        assertNull(announcer.observe(RemotePolicyMode.RULE, active = true))
    }

    @Test
    fun transitionsEmitTheMatchingCue() {
        val announcer = RemoteModeAnnouncer()
        announcer.observe(RemotePolicyMode.RULE, active = true)

        assertEquals(
            PolicyVoiceCue.CLOUD_MODE_REDEEM,
            announcer.observe(RemotePolicyMode.REDEEM, active = true),
        )
        assertEquals(
            PolicyVoiceCue.CLOUD_MODE_RULE,
            announcer.observe(RemotePolicyMode.RULE, active = true),
        )
    }

    @Test
    fun inactiveObservationsDoNotPrimeOrSpeak() {
        val announcer = RemoteModeAnnouncer()
        assertNull(announcer.observe(RemotePolicyMode.RULE, active = false))
        assertNull(announcer.observe(RemotePolicyMode.RULE, active = true))
        assertNull(announcer.observe(null, active = true))
        assertEquals(
            PolicyVoiceCue.CLOUD_MODE_REDEEM,
            announcer.observe(RemotePolicyMode.REDEEM, active = true),
        )
    }
}
