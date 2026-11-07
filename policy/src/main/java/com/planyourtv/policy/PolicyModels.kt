package com.planyourtv.policy

/**
 * The part of a TV policy that Aerial needs for list and foreground decisions.
 *
 * This is intentionally independent of app.orchard.tv.* so the module can be
 * developed and tested without changing Aerial's model classes. The server
 * contract and the remaining session/voice/power fields belong in this module
 * as the product grows.
 */
data class PolicySnapshot(
    val version: Long,
    val enabled: Boolean,
    val baseAllowlist: Set<String> = emptySet(),
    val redeemedAllowlist: Set<String> = emptySet(),
    val allowInputs: Boolean = false,
    val allowSettings: Boolean = false,
)

/** State the TV lock screen and HomeGuard read. */
/** Why the lock screen is showing. NONE is the idle "parent lock". */
enum class LockReason {
    NONE,
    RESTING,
    /** Automatic mode, screen on, quota left: the child starts the next segment. */
    AUTO_READY,
    /** Manual mode: only a parent action can start entertainment. */
    MANUAL_WAITING,
    TIME_UP,
    OUTSIDE_WINDOW,
    PARENT_STOP,
    /** A parent ended the segment; automatic mode stays locked for this window. */
    AUTO_BLOCKED,
    /** Explicit empty entertainment scope, or study removed every timed app. */
    NO_TIMED_APPS,
    SETUP_REQUIRED,
    /** Legacy cloud redemption states. */
    CANCELED,
}

data class PolicyUiState(
    val loaded: Boolean = false,
    val mode: PolicyMode = PolicyMode.CLOUD,
    /** The cloud guard's RULE/REDEEM choice; null before the policy mirror is loaded. */
    val remoteMode: RemotePolicyMode? = null,
    val sessionState: SessionState = SessionState.IDLE,
    val guardState: GuardStatus = GuardStatus.ENFORCING,
    /** Manual is the product default; the parent explicitly starts each segment. */
    val activation: Activation = Activation.MANUAL,
    val source: PolicySource = PolicySource.LOCAL,
    val policyEpoch: Long = 0L,
    /**
     * The study list is non-empty. The V10 entry stays visible with an empty list so the child
     * learns why it is unavailable, but the lock screen keeps it as a no-op and names the
     * missing setup in the focused footer instead of opening an empty launcher.
     */
    val studyModeAvailable: Boolean = false,
    val studyModeActive: Boolean = false,
    /** The explicit study scope shown by the launcher while study mode is active. */
    val studyPackages: Set<String> = emptySet(),
    /** Automatic quota left and no session: the child may start the next segment. */
    val autoReady: Boolean = false,
    /** Effective timed scope is empty; no entertainment session may start. */
    val noTimedApps: Boolean = false,
    val totalSeconds: Long = 0,
    val remainingSeconds: Long = 0,
    /**
     * What the TV can actually use: min(points remaining, physical window remaining). Cloud
     * exposes the buffered validEndTime as the window; local has no separate window.
     */
    val effectiveRemainingSeconds: Long = 0,
    val userName: String = "",
    val pinConfigured: Boolean = false,
    val bound: Boolean = false,
    /** Cloud is bound, but no usable grant/session exists; show the mini-program guide. */
    val cloudNeedsGrant: Boolean = false,
    /** Stable device id (Hashed Android ID, or a legacy UUID) reported as deviceType TV_APP. */
    val deviceNo: String = "",
    /** The parent chose "later" for the binding screen. Memory only. */
    val bindingDeferred: Boolean = false,
    /** SCREEN now; APPS is carried through so the future allowlist mode needs no state change. */
    val accessMode: AccessMode = AccessMode.SCREEN,
    val allowedPackages: Set<String> = emptySet(),
    /** Localized "最晚今天22:45结束" line for the PENDING gate; empty when there is no window. */
    val validUntil: String = "",
    /** The grant recipient's icon URL; the app caches it and falls back to the glyph. */
    val userIconUrl: String = "",
    val lockReason: LockReason = LockReason.NONE,

    // Local mode. Cloud states leave these at their defaults.
    val localRulesConfigured: Boolean = false,
    val setupRequired: Boolean = false,
    val dailyUsedSeconds: Long = 0,
    val dailyLimitSeconds: Long = 0,
    val sessionLimitSeconds: Long = 0,
    /** Configured rest between sessions, not the remaining rest on the lock screen. */
    val configuredRestMinutes: Int = 0,
    /** Configured screen-off grace; a deadline grace, never extra usable time. */
    val configuredBufferMinutes: Int = 0,
    /** Localized next allowed time, for example "明天 18:00". */
    val nextWindowLabel: String = "",
    /** Today's allowed windows, for example "18:00–20:00"; empty when today has none. */
    val todayWindowsLabel: String = "",
    /** Seconds left in the current allowed window; zero outside a window. */
    val windowRemainingSeconds: Long = 0,
    /** Seconds until the next allowed window starts; zero when no window is left. */
    val nextWindowInSeconds: Long = 0,
    val restRemainingSeconds: Long = 0,
    /** Remaining parent temporary unlock; zero outside that state. */
    val parentUnlockRemainingSeconds: Long = 0,
)
