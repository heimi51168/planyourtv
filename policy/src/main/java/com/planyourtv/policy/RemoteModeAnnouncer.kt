package com.planyourtv.policy

/**
 * Turns cloud RULE/REDEEM transitions into a voice cue.
 *
 * The first observed mode is only a baseline: the TV may start up already in RULE, or the phone
 * may have changed the mode while the TV was offline. Speaking only on a later transition keeps
 * startup and reconnect quiet while still announcing every user-visible switch after that.
 */
class RemoteModeAnnouncer {
    private var last: RemotePolicyMode? = null

    /**
     * @param active false when the cloud guard does not own the TV; such observations are
     *   ignored completely, so the next active observation still only primes the baseline.
     * @return the cue for a transition, or null for the baseline/same mode/inactive state.
     */
    fun observe(mode: RemotePolicyMode?, active: Boolean): PolicyVoiceCue? {
        if (!active) return null
        val current = mode ?: return null
        val previous = last
        last = current
        if (previous == null || previous == current) return null
        return when (current) {
            RemotePolicyMode.RULE -> PolicyVoiceCue.CLOUD_MODE_RULE
            RemotePolicyMode.REDEEM -> PolicyVoiceCue.CLOUD_MODE_REDEEM
        }
    }
}
