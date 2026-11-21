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
    /** Manual mode: only a parent action can start entertainment. */
    MANUAL_WAITING,
    TIME_UP,
    OUTSIDE_WINDOW,
    PARENT_STOP,
    /** Explicit empty entertainment scope, or study removed every timed app. */
    NO_TIMED_APPS,
    SETUP_REQUIRED,
    /** Legacy cloud redemption states. */
    CANCELED,
}

/**
 * One in-progress study-to-earn round. The runtime keeps the durable copy; UI only needs the
 * labels and the two numbers for the side countdown and progress bar.
 */
data class StudyRoundUi(
    val studyPackage: String,
    /** App opened on completion, or null for the direct-study entry (go to entertainment Home). */
    val targetPackage: String? = null,
    val totalSeconds: Long = 0L,
    val remainingSeconds: Long = 0L,
    /** Accrued focus time; the X-minute reward trigger compares against this. */
    val studiedSeconds: Long = 0L,
    /** This round's study allowance; 0 with [studyLimitUnlimited] means no fixed countdown. */
    val studyLimitSeconds: Long = 0L,
    val studyLimitUnlimited: Boolean = false,
    val rewardSeconds: Long = 0L,
    /** The study app is in the reward list; false means this app only studies. */
    val rewardEnabled: Boolean = false,
    /** Focus seconds needed for the one reward attempt this round. */
    val rewardAtSeconds: Long = 0L,
    /** The round already used its one reward attempt, even if the reward was unavailable. */
    val rewardAttempted: Boolean = false,
    /** The round already earned its entertainment reward. */
    val rewardGranted: Boolean = false,
    /**
     * Leaving the study app, screen off or a process restore freezes the countdown. Only the
     * focus desktop's 继续学习 resumes it, so a pause can never be mistaken for dead time.
     */
    val paused: Boolean = false,
) {
    /**
     * This round's study allowance can reach [rewardAtSeconds]. A reward-enabled app started
     * near the end of its study window still studies, but cannot promise a reward.
     */
    val rewardReachable: Boolean
        get() = rewardEnabled && totalSeconds >= rewardAtSeconds
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
    /** Study apps that trade focused minutes for entertainment time; empty means disabled. */
    val studyEarnPackages: Set<String> = emptySet(),
    val studyEarnStudyMinutes: Int = LocalRules.DEFAULT_STUDY_EARN_STUDY_MINUTES,
    val studyEarnRewardMinutes: Int = LocalRules.DEFAULT_STUDY_EARN_REWARD_MINUTES,
    /**
     * Device-level entry visibility from the cloud policy (2026-11-21). True when the backend
     * does not project the field yet. In local mode these stay true: the TV's own switches are
     * authoritative there, and this state must not fake a cloud decision.
     */
    val sourceEntryVisible: Boolean = true,
    val quizEntryVisible: Boolean = true,
    /** The parent allows the quiz route. V44 routes are independent and never pause each other. */
    val quizGateEnabled: Boolean = true,
    /** The parent allows study mode/study-to-earn. Independent from the other two routes. */
    val studyEarnEnabled: Boolean = false,
    /** Tapping an entertainment app starts immediately for [sessionLimitSeconds]. */
    val directStartEnabled: Boolean = false,
    /**
     * The single V44 projection of the three child routes, counts, cap and leftover quota.
     * TV widgets must render this instead of recomputing route availability.
     */
    val unlockRoutePlan: UnlockRoutePlan? = null,
    /** Non-null while the focus desktop should show; the round is not an entertainment session. */
    val studyRound: StudyRoundUi? = null,
    /** Effective timed scope is empty; no entertainment session may start. */
    val noTimedApps: Boolean = false,
    val totalSeconds: Long = 0,
    val remainingSeconds: Long = 0,
    /**
     * What the TV can actually use: min(points remaining, physical window remaining). Cloud
     * exposes the buffered validEndTime as the window; local has no separate window.
     */
    val effectiveRemainingSeconds: Long = 0,
    /** The ACTIVE session was started by the parent's out-of-plan manual timer. */
    val activeSessionOutsidePlan: Boolean = false,
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
    /** Apps that never consume entertainment or study time. */
    val unlimitedPackages: Set<String> = emptySet(),
    /** Localized "最晚今天22:45结束" line for the PENDING gate; empty when there is no window. */
    val validUntil: String = "",
    /** The grant recipient's icon URL; the app caches it and falls back to the glyph. */
    val userIconUrl: String = "",
    /**
     * Garden family member shown on the desktop (2026-11-21). Null while the backend does not
     * send one (older backend, or device not bound to a garden family). The desktop identity
     * chip and the switch sheet render from these instead of the local FamilyHub roster when
     * cloud mode is active.
     */
    val cloudActiveMemberId: Long? = null,
    val cloudActiveMemberName: String = "",
    val cloudActiveMemberIcon: String = "",
    /** Switchable garden members; already filtered by the device's allowed-learner list. */
    val cloudMembers: List<CloudMember> = emptyList(),
    val lockReason: LockReason = LockReason.NONE,

    // Local mode. Cloud states leave these at their defaults.
    val localRulesConfigured: Boolean = false,
    val setupRequired: Boolean = false,
    val dailyUsedSeconds: Long = 0,
    val dailyLimitSeconds: Long = 0,
    /** Study ledger for today, independent of entertainment usage. */
    val studyDailyUsedSeconds: Long = 0,
    /** Today's study quota in seconds; zero when [studyLimitUnlimited] is true. */
    val studyDailyLimitSeconds: Long = 0,
    val studyRemainingSeconds: Long = 0,
    val studyLimitUnlimited: Boolean = false,
    /** Localized label of the day group that owns today. */
    val currentDayGroupLabel: String = "",
    /** Direct-use "per viewing" duration, already clamped by the public cap. */
    val sessionLimitSeconds: Long = 0,
    /** V44 public cap for one child entertainment session, in seconds. */
    val sessionCapSeconds: Long = 0,
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
