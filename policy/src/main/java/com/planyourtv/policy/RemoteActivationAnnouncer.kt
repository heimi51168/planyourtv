package com.planyourtv.policy

/**
 * Turns a remote AUTO ↔ MANUAL activation change into a voice cue.
 *
 * The switch can arrive either as a SetActivation command or through the config pull that follows
 * a phone-side edit, so both paths observe here. The first active value primes the baseline:
 * starting up already in AUTO, or reconnecting after the phone changed the value while the TV was
 * offline, must not speak. Every later transition does.
 */
class RemoteActivationAnnouncer {
    private var last: Activation? = null

    /**
     * @param active false when the hosted rule guard does not own the TV; such observations are
     *   ignored completely, so the next active observation still only primes the baseline.
     * @return the cue for a transition, or null for the baseline/same value/inactive state.
     */
    fun observe(activation: Activation?, active: Boolean): PolicyVoiceCue? {
        if (!active) return null
        val current = activation ?: return null
        val previous = last
        last = current
        if (previous == null || previous == current) return null
        return when (current) {
            Activation.AUTO -> PolicyVoiceCue.REMOTE_ACTIVATION_AUTO
            Activation.MANUAL -> PolicyVoiceCue.REMOTE_ACTIVATION_MANUAL
        }
    }
}
