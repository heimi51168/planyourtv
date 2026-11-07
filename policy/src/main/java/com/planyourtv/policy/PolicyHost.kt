package com.planyourtv.policy

/**
 * Actions the policy module needs from the Aerial host process.
 *
 * Implemented by HomeGuardService, which already owns the accessibility
 * connection and the "bring Aerial back" path. Keeping the interface here
 * avoids a dependency from :policy back to :app.
 */
interface PolicyHost {
    /** Bring Aerial's Home to the front. */
    fun bringHome()

    /** Try the accessibility lock action. Returns false when unavailable. */
    fun lockScreen(): Boolean
}
