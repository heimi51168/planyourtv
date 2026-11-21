package com.planyourtv.policy

/**
 * Decides whether the product watchdog must bring Aerial's lock surface forward when no
 * accessibility guard is running. The guard owns its own edge watcher; this is the fallback
 * for TVs where the service cannot be enabled.
 */
fun shouldBringHomeWithoutGuard(
    lockScreen: Boolean,
    guardRunning: Boolean,
    aerialInFront: Boolean,
    lockOwnedAppInFront: Boolean,
    screenInteractive: Boolean,
    userUnlocked: Boolean,
    nowMs: Long,
    lastAttemptMs: Long,
    cooldownMs: Long,
): Boolean {
    if (!lockScreen || guardRunning || aerialInFront) return false
    // The lock desktop's own study/unlimited app is not an escape: the guard leaves the same
    // app in front, and reclaiming it would pause the focus round one tick after launch.
    if (lockOwnedAppInFront) return false
    if (!screenInteractive || !userUnlocked) return false
    if (cooldownMs <= 0L || lastAttemptMs <= 0L) return true
    return nowMs - lastAttemptMs >= cooldownMs
}