package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteActivationAnnouncerTest {
    @Test
    fun firstObservedActivationOnlyPrimesTheBaseline() {
        val announcer = RemoteActivationAnnouncer()
        assertNull(announcer.observe(Activation.MANUAL, active = true))
        assertNull(announcer.observe(Activation.MANUAL, active = true))
    }

    @Test
    fun transitionsEmitTheMatchingCue() {
        val announcer = RemoteActivationAnnouncer()
        announcer.observe(Activation.MANUAL, active = true)

        assertEquals(
            PolicyVoiceCue.REMOTE_ACTIVATION_AUTO,
            announcer.observe(Activation.AUTO, active = true),
        )
        assertEquals(
            PolicyVoiceCue.REMOTE_ACTIVATION_MANUAL,
            announcer.observe(Activation.MANUAL, active = true),
        )
    }

    @Test
    fun inactiveObservationsDoNotPrimeOrSpeak() {
        val announcer = RemoteActivationAnnouncer()
        assertNull(announcer.observe(Activation.MANUAL, active = false))
        assertNull(announcer.observe(Activation.MANUAL, active = true))
        assertNull(announcer.observe(null, active = true))
        assertEquals(
            PolicyVoiceCue.REMOTE_ACTIVATION_AUTO,
            announcer.observe(Activation.AUTO, active = true),
        )
    }
}
