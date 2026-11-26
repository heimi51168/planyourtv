package com.planyourtv.policy

/**
 * The only execution mode: every session starts from an explicit parent action. Configuration
 * provenance is [PolicySource], not a second activation.
 */
enum class Activation {
    MANUAL,
}

/** Where the editable configuration comes from. It never changes how the TV times a session. */
enum class PolicySource {
    NONE,
    LOCAL,
    HOSTED,
}

/**
 * Binding is configuration ownership only. SYNCING locks the editor without pretending a
 * session is pending; a failed handshake rolls back to UNBOUND.
 */
enum class BindingState {
    UNBOUND,
    SYNCING,
    BOUND,
}

/**
 * The app scope of one entertainment session. It is always intersected with the effective timed
 * packages, so study apps can never leak into a timed session.
 */
data class SessionScope(
    val allowedPackages: Set<String>,
) {
    /**
     * Returns the scope narrowed to [timedPackages], or null when the intersection is empty.
     * A null requested scope means "all timed apps"; it still excludes study packages.
     */
    fun resolveWithin(timedPackages: Set<String>, requested: Set<String>? = null): SessionScope? {
        val candidate = requested ?: timedPackages
        val resolved = candidate intersect timedPackages
        return if (resolved.isEmpty()) null else SessionScope(resolved)
    }
}

/**
 * Why a start or configuration command was refused. The runtime applies a command only after all
 * checks pass, so a rejection never leaves study mode or a session half-closed.
 */
enum class RejectReason {
    GUARD_DISABLED,
    /** Legacy controlled modes only; the V10 hosted rule guard accepts a PIN-confirmed local start. */
    HOSTED_MANUAL_REMOTE_ONLY,
    STALE_EPOCH,
    PARENT_OVERRIDE,
    BUSY,
    /** A quiz round is on screen or settling; a remote start must not interrupt it. */
    QUIZ_ACTIVE,
    /** Focused study mode or a study-to-earn round is in progress. */
    STUDY_ACTIVE,
    /** A remote grant is already waiting for the child; the phone must resolve it first. */
    PENDING_GRANT,
    /** An app is in front, so someone is using the TV (allowed or not). */
    FOREGROUND_IN_USE,
    /** The TV's active member is not the member this command is for. */
    MEMBER_MISMATCH,
    /** Member/foreground state is not fresh enough; a remote start fails closed. */
    STATE_UNKNOWN,
    RESTING,
    SCREEN_OFF,
    OUTSIDE_WINDOW,
    /** The local day's quota is already used up; a manual start would otherwise be a free session. */
    DAILY_LIMIT,
    /** V44: this child route already used its own daily count (or its count is zero). */
    ROUTE_LIMIT,
    EXPIRED,
    NO_TIMED_APPS,
    INVALID_SCOPE,
    STALE_VERSION,
    INVALID_COMMAND,
}

/** Result of a local or remote entertainment start. Rejections are returned, never thrown. */
sealed interface SessionStartResult {
    data object Started : SessionStartResult

    /**
     * The hosted control plane delivered a manual grant and the TV armed it. No budget is
     * consumed yet: the child must press "开始计时" on the lock screen, and only that local
     * action starts the timer. Re-delivering the same command returns this, not an error.
     */
    data object Pending : SessionStartResult

    data class Rejected(val reason: RejectReason) : SessionStartResult
}

/** The parent override enters from the lock screen and clears when its countdown ends. */
data class ParentOverrideRequest(
    val durationSeconds: Long,
    val origin: CommandOrigin = CommandOrigin.LOCAL,
)

enum class CommandOrigin {
    LOCAL,
    REMOTE,
}
