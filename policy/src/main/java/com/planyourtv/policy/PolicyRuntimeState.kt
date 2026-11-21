package com.planyourtv.policy

import java.util.UUID
import kotlin.math.abs

/**
 * The durable execution state of the local rule engine. One runtime snapshot is committed at a
 * time, so a session, its daily ledger and an armed remote grant can never be read from
 * different points in time after a process death.
 *
 * This model is deliberately separate from the rule/PIN configuration in [LocalPolicyStore]:
 * configuration changes are rare, while [RuntimeSnapshot] is checkpointed every few seconds.
 */
data class RuntimeSnapshot(
    val session: SessionSnapshot? = null,
    val ledger: RuntimeLedger = RuntimeLedger(),
)

/** A running or screen-off-paused rule session. */
data class SessionSnapshot(
    val sessionId: String,
    val phase: SessionPhase,
    val totalSeconds: Long,
    val remainingSeconds: Long,
    val endReason: LocalSessionEnd,
    val usageDate: String,
    val startedAtEpochMs: Long,
    val planDeadlineEpochMs: Long = 0L,
    val wallDeadlineEpochMs: Long = 0L,
    val pausedAtEpochMs: Long = 0L,
    /** Why the session is paused; screen-off wins over an unlimited app in front. */
    val pauseReason: SessionPauseReason? = null,
    val checkpointWallMs: Long = 0L,
    val checkpointElapsedMs: Long = 0L,
    val checkpointAsleepMs: Long = 0L,
    val boot: BootEvidence = BootEvidence(),
    val activation: PolicyActivation = PolicyActivation.MANUAL,
    val accessMode: AccessMode = AccessMode.SCREEN,
    val allowedPackages: Set<String> = emptySet(),
    val origin: PolicySessionOrigin? = null,
    val remoteRecordId: Int = 0,
    val reportUsage: Boolean = false,
    val templateVersion: Long = 0L,
    val restAfterSeconds: Long = 0L,
    /** V45 parent-started session outside the child plan; never writes the daily ledger. */
    val outsidePlan: Boolean = false,
)

enum class SessionPhase { ACTIVE, PAUSED }

/** Why an active session stopped consuming budget. Recovery/restore leaves this null. */
enum class SessionPauseReason { SCREEN_OFF, UNLIMITED_APP }

/**
 * LOCAL is a parent-confirmed start on the TV itself, GRANT is a REST exchange record and REMOTE
 * is a record-less MQTT dispatch. The distinction survives a restart so an END event can still
 * be matched to the record that started it.
 */
enum class PolicySessionOrigin { LOCAL, GRANT, REMOTE }

/** Result of one settled session, kept for crash idempotency and END replay. */
data class SettledSessionSnapshot(
    val consumedSeconds: Long,
    val endedAtEpochMs: Long,
    val reason: PolicyEndReason,
    val usageDate: String,
    val remoteRecordId: Int = 0,
)

/** An admitted remote grant that is waiting for the child to confirm it on the TV. */
data class PendingGrantSnapshot(
    val recordId: Int,
    val source: PendingGrantSource,
    val durationSeconds: Long,
    val expectedEpoch: Long,
    val requestedScope: Set<String>? = null,
    val accessMode: AccessMode = AccessMode.SCREEN,
    val allowedPackages: Set<String> = emptySet(),
    val restAfterSeconds: Long = 0L,
    val armedAtEpochMs: Long = 0L,
    val deadlineEpochMs: Long = 0L,
    val validUntilLabel: String = "",
    val userName: String = "",
    val userIconUrl: String = "",
)

enum class PendingGrantSource { MQTT, GRANT }

/**
 * Daily usage is an additive ledger, not a single "today" key: a session that crossed midnight
 * is settled into the date it actually ran, so a crash cannot move yesterday's seconds into the
 * new day. [breakUntilEpochMs] and [lastEndedAtEpochMs] ride in the same snapshot as the
 * settlement that produced them.
 */
data class RuntimeLedger(
    val days: Map<String, Long> = emptyMap(),
    /** Study seconds per local date. Kept apart from the entertainment [days] ledger. */
    val studyDays: Map<String, Long> = emptyMap(),
    val breakUntilEpochMs: Long = 0L,
    val lastEndedAtEpochMs: Long = 0L,
    val settled: Map<String, SettledSessionSnapshot> = emptyMap(),
    val pendingGrant: PendingGrantSnapshot? = null,
    /** V44 successful-session counts for the child unlock routes, scoped to one local date. */
    val routeUsage: RouteUsage? = null,
)

data class GuardSnapshot(
    val phase: GuardStatus = GuardStatus.ENFORCING,
    val override: OverrideLease? = null,
    val study: StudyLease? = null,
    /**
     * Policy-owned evidence that the screen really went off. It is written before the normal
     * screen-off handling, so a ROM that kills the process immediately afterwards cannot erase
     * the evidence that a temporary unlock must not be restored.
     */
    val screenOff: ScreenOffEvidence = ScreenOffEvidence(),
)

data class OverrideLease(
    val leaseId: String,
    val startedWallMs: Long,
    val startedElapsedMs: Long,
    val startedAsleepMs: Long,
    /** Zero means "until the screen goes off"; [hardCapWallMs] still applies. */
    val untilWallMs: Long,
    val hardCapWallMs: Long,
    val hardCapElapsedMs: Long,
    val boot: BootEvidence = BootEvidence(),
    val screenOffMarkerAtStartMs: Long = 0L,
)

data class StudyLease(
    val startedWallMs: Long,
    val startedElapsedMs: Long,
    val startedAsleepMs: Long,
    val hardCapWallMs: Long,
    val hardCapElapsedMs: Long,
    val boot: BootEvidence = BootEvidence(),
)

/**
 * A process cannot outlive its boot. The kernel boot id is authoritative; the wall-minus-elapsed
 * stamp is the fallback for old TVs where /proc is unavailable.
 */
data class BootEvidence(
    val bootId: String = "",
    val bootStampMs: Long = 0L,
) {
    val valid: Boolean get() = bootId.isNotBlank() || bootStampMs > 0L

    fun sameBoot(other: BootEvidence): Boolean {
        if (bootId.isNotBlank() && other.bootId.isNotBlank()) return bootId == other.bootId
        if (bootStampMs <= 0L || other.bootStampMs <= 0L) return false
        return abs(bootStampMs - other.bootStampMs) < PolicyRecoveryPolicy.SAME_BOOT_SLACK_MS
    }
}

data class ScreenOffEvidence(
    val elapsedMs: Long = 0L,
    val wallMs: Long = 0L,
    val boot: BootEvidence = BootEvidence(),
)

/** The clock and boot identity read once at a decision point. */
data class RecoveryNow(
    val wallMs: Long,
    val elapsedMs: Long,
    val asleepMs: Long,
    val boot: BootEvidence,
)

/**
 * Pure recovery decisions shared by the runtime and unit tests. Keeping them here makes the
 * safety matrix (same boot, screen state, standby, deadlines) testable without Android.
 */
object PolicyRecoveryPolicy {
    const val SAME_BOOT_SLACK_MS = 2 * 60 * 1000L
    const val ASLEEP_SLACK_MS = 30 * 1000L
    const val CLOCK_SLACK_MS = 5 * 60 * 1000L
    /** An indefinite override ends after this long even if the ROM missed every screen-off. */
    const val OVERRIDE_HARD_CAP_MS = 12 * 60 * 60 * 1000L
    /** Study mode is not a security bypass, but it must not survive forever either. */
    const val STUDY_HARD_CAP_MS = 24 * 60 * 60 * 1000L

    fun overrideRestorable(
        lease: OverrideLease,
        now: RecoveryNow,
        screenInteractive: Boolean,
        screenOff: ScreenOffEvidence,
    ): Boolean {
        if (!screenInteractive) return false
        if (lease.untilWallMs > 0L && now.wallMs >= lease.untilWallMs) return false
        if (lease.hardCapWallMs > 0L && now.wallMs >= lease.hardCapWallMs) return false
        if (lease.hardCapElapsedMs > 0L && now.elapsedMs >= lease.hardCapElapsedMs) return false
        if (!lease.boot.sameBoot(now.boot)) return false
        if (now.elapsedMs < lease.startedElapsedMs) return false
        if (now.asleepMs - lease.startedAsleepMs > ASLEEP_SLACK_MS) return false
        val markerAfterStart = screenOff.elapsedMs > lease.startedElapsedMs &&
            (screenOff.boot.sameBoot(lease.boot) || !screenOff.boot.valid)
        return !markerAfterStart
    }

    fun studyRestorable(
        lease: StudyLease,
        now: RecoveryNow,
        screenInteractive: Boolean,
        screenOff: ScreenOffEvidence,
    ): Boolean {
        if (!screenInteractive) return false
        if (lease.hardCapWallMs > 0L && now.wallMs >= lease.hardCapWallMs) return false
        if (lease.hardCapElapsedMs > 0L && now.elapsedMs >= lease.hardCapElapsedMs) return false
        if (!lease.boot.sameBoot(now.boot)) return false
        if (now.elapsedMs < lease.startedElapsedMs) return false
        if (now.asleepMs - lease.startedAsleepMs > ASLEEP_SLACK_MS) return false
        val markerAfterStart = screenOff.elapsedMs > lease.startedElapsedMs &&
            (screenOff.boot.sameBoot(lease.boot) || !screenOff.boot.valid)
        return !markerAfterStart
    }

    /**
     * Seconds the active budget should lose for the gap between the last checkpoint and now.
     * Zero is the safe answer whenever the evidence is incomplete: undercounting is safer for
     * the child than charging time they did not spend, and the fixed wall deadline still caps.
     */
    fun deadGapSeconds(
        remainingSeconds: Long,
        checkpointElapsedMs: Long,
        checkpointWallMs: Long,
        checkpointAsleepMs: Long,
        boot: BootEvidence,
        now: RecoveryNow,
        screenInteractive: Boolean,
        screenOff: ScreenOffEvidence,
    ): Long {
        if (remainingSeconds <= 0L) return 0L
        if (!screenInteractive) return 0L
        if (checkpointElapsedMs <= 0L || now.elapsedMs <= checkpointElapsedMs) return 0L
        if (!boot.sameBoot(now.boot)) return 0L
        if (now.asleepMs - checkpointAsleepMs > ASLEEP_SLACK_MS) return 0L
        if (checkpointWallMs > 0L && now.wallMs > 0L) {
            val wallDelta = now.wallMs - checkpointWallMs
            val elapsedDelta = now.elapsedMs - checkpointElapsedMs
            if (abs(wallDelta - elapsedDelta) > CLOCK_SLACK_MS) return 0L
        }
        val markerAfterCheckpoint = screenOff.elapsedMs > checkpointElapsedMs &&
            (screenOff.boot.sameBoot(boot) || !screenOff.boot.valid)
        if (markerAfterCheckpoint) return 0L
        return ((now.elapsedMs - checkpointElapsedMs) / 1000L)
            .coerceAtLeast(0L)
            .coerceAtMost(remainingSeconds)
    }

    fun newLeaseId(): String = "ovr-" + UUID.randomUUID().toString().replace("-", "").take(20)
}
