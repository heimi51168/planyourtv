package app.orchard.tv.policy

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import app.orchard.tv.R
import app.orchard.tv.system.BootOpen
import com.planyourtv.policy.AccessMode
import com.planyourtv.policy.Activation
import com.planyourtv.policy.BootEvidence
import com.planyourtv.policy.GuardSnapshot
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.LocalPolicyStore
import com.planyourtv.policy.LocalRecovery
import com.planyourtv.policy.LocalRestPolicy
import com.planyourtv.policy.LocalRuleSets
import com.planyourtv.policy.LocalRuleSetsLoad
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalSessionEnd
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.LockReason
import com.planyourtv.policy.OverrideLease
import com.planyourtv.policy.PendingGrantSnapshot
import com.planyourtv.policy.PendingGrantSource
import com.planyourtv.policy.PinResult
import com.planyourtv.policy.PolicyActivation
import com.planyourtv.policy.PolicyConfig
import com.planyourtv.policy.PolicyEndReason
import com.planyourtv.policy.PolicyExecutionPlan
import com.planyourtv.policy.PolicyFlowTrace
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicyPlanSource
import com.planyourtv.policy.PolicyRecoveryPolicy
import com.planyourtv.policy.PolicySessionOrigin
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.PolicyStateRepository
import com.planyourtv.policy.PolicyUiState
import com.planyourtv.policy.PolicyVoiceBus
import com.planyourtv.policy.PolicyVoiceCue
import com.planyourtv.policy.RecoveryNow
import com.planyourtv.policy.RejectReason
import com.planyourtv.policy.RulePlanDecision
import com.planyourtv.policy.RulePlanScheduler
import com.planyourtv.policy.RuleUsageEventFactory
import com.planyourtv.policy.RuleUsageEventType
import com.planyourtv.policy.RuleUsageSink
import com.planyourtv.policy.RuleUsageStore
import com.planyourtv.policy.RuntimeLedger
import com.planyourtv.policy.RuntimeSnapshot
import com.planyourtv.policy.SavedLocalSession
import com.planyourtv.policy.ScreenOffEvidence
import com.planyourtv.policy.SessionPhase
import com.planyourtv.policy.SessionSnapshot
import com.planyourtv.policy.SessionStartResult
import com.planyourtv.policy.SessionState
import com.planyourtv.policy.SessionTiming
import com.planyourtv.policy.SettledSessionSnapshot
import com.planyourtv.policy.StudyLease
import com.planyourtv.policy.TvStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

/**
 * The TV's single local executor. Automatic and manual entertainment sessions share this timer,
 * ledger, rest and app scope; study mode only replaces the visible app scope and has no timer,
 * ledger or plan. The executor never reads the network: hosted clients write the config mirror
 * and send commands, and this object executes from the local cache.
 */
object LocalPolicyRuntime {
    private const val TAG = "LocalPolicy"
    private const val SAVE_EVERY_TICKS = 5L
    private const val RUNTIME_SYNC_EVERY_CHECKPOINTS = 6
    private const val USAGE_PROGRESS_SECONDS = 30L
    private const val SETTLED_GRANT_LIMIT = 32
    private const val SETTLED_SESSION_LIMIT = 64
    private const val KEPT_USAGE_DAYS = 3
    private val USAGE_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var context: Context? = null
    private var ticker: Job? = null
    private var initialized = false
    private var active = false
    private var rules: LocalRules? = null
    /** Both stored time-rule sets; [rules] is always their materialized active snapshot. */
    private var ruleSets: LocalRuleSets? = null
    private var session: LocalActiveSession? = null
    /**
     * The exchange record that started the running session. Only a record-backed remote session
     * is stopped when the REST snapshot says the record is gone; LOCAL, automatic and the
     * record-less PTV dispatch keep running even if the grant poll is empty.
     */
    private var activeRemoteRecordId: Int = 0

    /**
     * Durable origin of the running session. A LOCAL start during hosted rule mode has no
     * exchange record, so a grant seen while it runs is a new authorization and must stay
     * unconsumed for the poll to arm after the session ends.
     */
    private var sessionOrigin: PolicySessionOrigin? = null

    /** Whether this session belongs to the hosted usage ledger; persisted across a restart. */
    private var sessionReportUsage = false

    private var sessionBoot = BootEvidence()
    private var checkpointWallMs = 0L
    private var checkpointElapsedMs = 0L
    private var checkpointAsleepMs = 0L
    private var checkpointFlushCounter = 0
    /** True once this screen-off episode has its durable marker; reset when the screen lights. */
    private var screenOffEvidenceForEpisode = false

    /**
     * A remote manual grant that has been admitted but not started. The phone may send it over
     * MQTT (`start_session`) or deliver it through the REST grant poll; both paths arm this one
     * object. Nothing is consumed until the child confirms on the TV.
     */
    private var pendingRemoteStart: PendingRemoteStart? = null

    /** Grant records already confirmed, stopped or canceled here; a poll must not re-arm them. */
    private val settledGrantRecords = LinkedHashSet<Int>()
    private var dayKey = ""
    private var usedSeconds = 0L
    /** Recent daily totals, including a session settled into the date it actually ran. */
    private val dayUsage = LinkedHashMap<String, Long>()
    private var breakUntil = 0L
    private var lastSessionEndedAt = 0L
    /** Sessions already settled, keyed by id, so a crash cannot add their usage twice. */
    private val settledSessions = LinkedHashMap<String, SettledSessionSnapshot>()
    private var parentOverrideUntil = 0L
    private var guardStatus = GuardStatus.ENFORCING
    private var guardSnapshot = GuardSnapshot()
    /** False until initialize() has finished restoring runtime and guard state. */
    private var recoveryComplete = false
    private var activation = Activation.MANUAL
    private var source = PolicySource.LOCAL
    private var policyEpoch = 0L
    private var studyMode = false
    private var autoReady = false
    /** Launchable packages, cached so an all-apps policy can publish a concrete APPS scope. */
    private var installedPackages: Set<String> = emptySet()
    private var blockedWindowKey = ""
    private var lockReason = LockReason.NONE
    private var nextWindowLabel = ""
    private var restRemainingSeconds = 0L
    private var lastSavedTick = 0L
    private var templateVersion = 0L
    private var usageSink: RuleUsageSink? = null
    private var lastUsageReportedSeconds = 0L
    private val spokenThresholds = mutableSetOf<Long>()

    private val _state = MutableStateFlow(PolicyUiState(mode = PolicyMode.LOCAL, loaded = false))
    val state: StateFlow<PolicyUiState> = _state.asStateFlow()

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private data class LocalActiveSession(
        val plan: PolicyExecutionPlan,
        val remainingSeconds: Long,
        val startedAtEpochMs: Long,
        val usageDate: String,
        /** Fixed at the first start: budget + screen-off grace, capped by the day. */
        val wallDeadlineEpochMs: Long,
        /** Non-zero while the screen is off. Pausing never consumes [remainingSeconds]. */
        val pausedAtEpochMs: Long = 0L,
    ) {
        val totalSeconds: Long get() = plan.budgetSeconds

        val isPaused: Boolean get() = pausedAtEpochMs > 0L

        val endReason: LocalSessionEnd
            get() = when (plan.endReason) {
                PolicyEndReason.DAILY_LIMIT -> LocalSessionEnd.DAILY_LIMIT
                PolicyEndReason.WINDOW_END -> LocalSessionEnd.WINDOW_END
                else -> LocalSessionEnd.SESSION_LIMIT
            }
    }

    private enum class PendingSource { MQTT, GRANT }

    /**
     * An armed remote manual grant. [expectedEpoch] and [requestedScope] are rechecked when the
     * child confirms, and [deadlineEpochMs] is the earlier of the grant window and the current
     * rules window so a closed window cannot leave a dead orange button on screen.
     */
    private data class PendingRemoteStart(
        val durationSeconds: Long,
        val expectedEpoch: Long,
        val requestedScope: Set<String>?,
        val accessMode: AccessMode,
        val allowedPackages: Set<String>,
        val restAfterSeconds: Long,
        val recordId: Int,
        val source: PendingSource,
        val deadlineEpochMs: Long,
        val validUntilLabel: String = "",
        /** Recipient identity from the REST grant; empty for a record-less PTV dispatch. */
        val userName: String = "",
        val userIconUrl: String = "",
    )

    /** Evidence for the boot this process belongs to; shared by session and guard checkpoints. */
    private fun bootEvidence(): BootEvidence = BootEvidence(
        bootId = BootOpen.BOOT_ID.orEmpty(),
        bootStampMs = System.currentTimeMillis() - SystemClock.elapsedRealtime(),
    )

    /** Suspended time since boot, the same measure BootOpen uses to detect standby. */
    private fun asleepMs(): Long = SystemClock.elapsedRealtime() - SystemClock.uptimeMillis()

    private fun recoveryNow(wallMs: Long): RecoveryNow = RecoveryNow(
        wallMs = wallMs,
        elapsedMs = SystemClock.elapsedRealtime(),
        asleepMs = asleepMs(),
        boot = bootEvidence(),
    )

    /**
     * Anchors the next dead-gap calculation at "now". A process death after this point loses at
     * most one checkpoint interval; everything before it has already been charged to the budget.
     */
    private fun markCheckpoint(nowWallMs: Long) {
        checkpointWallMs = nowWallMs
        checkpointElapsedMs = SystemClock.elapsedRealtime()
        checkpointAsleepMs = asleepMs()
        sessionBoot = bootEvidence()
    }

    /**
     * Rebuilds the immutable plan for a session restored from disk. A session saved by an older
     * build has no id/start time, so synthesize values that keep the usage report valid instead
     * of dropping the remaining time.
     */
    private fun restoreSession(
        saved: SessionSnapshot,
        remainingSeconds: Long,
        paused: Boolean,
        nowMs: Long,
    ): LocalActiveSession {
        val consumed = (saved.totalSeconds - remainingSeconds).coerceAtLeast(0L)
        val startedAt = saved.startedAtEpochMs.takeIf { it > 0 }
            ?: (nowMs - consumed * 1_000L).coerceAtLeast(1L)
        val usageDate = saved.usageDate.takeIf { USAGE_DATE.matches(it) } ?: dayKey
        val endReason = saved.endReason.toPolicyEndReason()
        val restAfter = if (endReason == PolicyEndReason.SESSION_LIMIT) {
            saved.restAfterSeconds.takeIf { it > 0 }
                ?: ((rules?.restMinutes ?: 0) * 60L)
        } else {
            0L
        }
        val plan = PolicyExecutionPlan(
            id = saved.sessionId,
            source = PolicyPlanSource.RULE,
            activation = saved.activation,
            endReason = endReason,
            budgetSeconds = saved.totalSeconds,
            deadlineEpochMs = saved.planDeadlineEpochMs.takeIf { it > 0 },
            accessMode = saved.accessMode,
            allowedPackages = saved.allowedPackages,
            restAfterSeconds = restAfter,
            templateVersion = saved.templateVersion.takeIf { it > 0 } ?: templateVersion,
            sessionId = saved.sessionId,
        )
        // Sessions written before V11 have no fixed wall deadline. Reconstruct one from the
        // same start snapshot; the buffer is a grace, never an extension of usable time.
        val wallDeadline = saved.wallDeadlineEpochMs.takeIf { it > 0L }
            ?: SessionTiming.wallDeadlineEpochMs(
                startedAtEpochMs = startedAt,
                budgetSeconds = saved.totalSeconds,
                bufferMinutes = rules?.sessionScreenOffBufferMinutes
                    ?: LocalRules.DEFAULT_SESSION_BUFFER_MINUTES,
                planDeadlineEpochMs = saved.planDeadlineEpochMs.takeIf { it > 0L },
                nextMidnightEpochMs = nextMidnightEpochMs(LocalDateTime.now()),
            )
        return LocalActiveSession(
            plan = plan,
            remainingSeconds = remainingSeconds.coerceAtLeast(0L),
            startedAtEpochMs = startedAt,
            usageDate = usageDate,
            wallDeadlineEpochMs = wallDeadline,
            pausedAtEpochMs = if (paused) nowMs.coerceAtLeast(1L) else 0L,
        )
    }

    /**
     * Why a restored session cannot continue: its plan/window wall deadline passed while the
     * process was off, or midnight separated it from today's quota. Null means it still runs.
     */
    private fun expiredReason(saved: SessionSnapshot, nowEpochMs: Long): PolicyEndReason? {
        val wallExpired = saved.wallDeadlineEpochMs > 0L && nowEpochMs >= saved.wallDeadlineEpochMs
        val planExpired = saved.planDeadlineEpochMs > 0L &&
            nowEpochMs >= saved.planDeadlineEpochMs
        val crossDay = saved.usageDate.isNotBlank() &&
            USAGE_DATE.matches(saved.usageDate) &&
            saved.usageDate != dayKey
        return when {
            crossDay -> PolicyEndReason.WINDOW_END
            planExpired -> PolicyEndReason.WINDOW_END
            wallExpired -> if (saved.endReason.toPolicyEndReason() == PolicyEndReason.DAILY_LIMIT) {
                PolicyEndReason.DAILY_LIMIT
            } else {
                PolicyEndReason.SCREEN_OFF_TIMEOUT
            }
            else -> null
        }
    }

    /** The moment a restored expired session actually stopped, never later than now. */
    private fun expiredEndedAt(saved: SessionSnapshot, nowEpochMs: Long): Long {
        val deadlines = listOfNotNull(
            saved.planDeadlineEpochMs.takeIf { it > 0L && nowEpochMs >= it },
            saved.wallDeadlineEpochMs.takeIf { it > 0L && nowEpochMs >= it },
        )
        return deadlines.minOrNull() ?: nowEpochMs
    }

    /**
     * Adds a session's usage to the date it actually ran, exactly once. Both the session id and
     * the daily total live in the same snapshot, so a crash can neither lose nor double-count it.
     * Returns the date's total after the settlement.
     */
    private fun recordSettlement(
        sessionId: String,
        usageDate: String,
        consumedSeconds: Long,
        endedAtEpochMs: Long,
        reason: PolicyEndReason,
        remoteRecordId: Int,
    ): Long {
        val date = usageDate.takeIf { USAGE_DATE.matches(it) } ?: dayKey
        settledSessions[sessionId]?.let { return dayUsage[date] ?: it.consumedSeconds }
        val consumed = consumedSeconds.coerceAtLeast(0L)
        val total = ((dayUsage[date] ?: 0L) + consumed).coerceAtLeast(0L)
        dayUsage[date] = total
        if (date == dayKey) usedSeconds = total
        settledSessions[sessionId] = SettledSessionSnapshot(
            consumedSeconds = consumed,
            endedAtEpochMs = endedAtEpochMs,
            reason = reason,
            usageDate = date,
            remoteRecordId = remoteRecordId,
        )
        while (settledSessions.size > SETTLED_SESSION_LIMIT) {
            settledSessions.remove(settledSessions.keys.first())
        }
        pruneDayUsage(keep = date)
        return total
    }

    /** Keeps the ledger small on a 1 GB TV without dropping the date being settled. */
    private fun pruneDayUsage(keep: String) {
        if (dayUsage.size <= KEPT_USAGE_DAYS) return
        val cutoff = LocalDate.now().minusDays((KEPT_USAGE_DAYS - 1).toLong()).toString()
        val iterator = dayUsage.keys.iterator()
        while (iterator.hasNext()) {
            val date = iterator.next()
            if (date != keep && date < cutoff) iterator.remove()
        }
    }

    /** Rest end produced by a settlement, or zero when no rest applies. */
    private fun restUntilFor(
        endedAtEpochMs: Long,
        endReason: PolicyEndReason,
        planRestSeconds: Long = 0L,
        skipRest: Boolean = false,
    ): Long {
        val configured = rules?.normalized() ?: return 0L
        val restSeconds = LocalRestPolicy.restSeconds(
            activation = activation,
            configuredRestMinutes = configured.restMinutes,
            endReason = endReason,
            planRestSeconds = planRestSeconds,
            skipRest = skipRest,
        )
        val until = LocalRestPolicy.restUntilEpochMs(endedAtEpochMs, restSeconds)
        return until.takeIf { it > System.currentTimeMillis() } ?: 0L
    }

    /**
     * Settles a session that could not continue after a restart and queues its END before the
     * usage sink is attached. The outbox is loaded by [RuleUsageReporter] later, so a crash
     * during app start does not lose the report. Local-only sessions never enqueue.
     */
    private fun settleRecovered(
        saved: SessionSnapshot,
        reason: PolicyEndReason,
        endedAtEpochMs: Long,
    ) {
        if (settledSessions.containsKey(saved.sessionId)) return
        val consumed = (saved.totalSeconds - saved.remainingSeconds).coerceAtLeast(0L)
        if (consumed <= 0L) return
        val startedAt = saved.startedAtEpochMs.takeIf { it > 0 }
            ?: (endedAtEpochMs - consumed * 1_000L).coerceAtLeast(1L)
        val usageDate = saved.usageDate.takeIf { USAGE_DATE.matches(it) } ?: dayKey
        val usedToday = recordSettlement(
            sessionId = saved.sessionId,
            usageDate = usageDate,
            consumedSeconds = consumed,
            endedAtEpochMs = endedAtEpochMs,
            reason = reason,
            remoteRecordId = saved.remoteRecordId,
        )
        // The process may have been off for a while: start the rest from the real end and only
        // publish the remaining part. An already-elapsed rest stays over.
        val restUntil = restUntilFor(endedAtEpochMs, reason)
        lastSessionEndedAt = if (restUntil > 0L) endedAtEpochMs else 0L
        breakUntil = restUntil
        if (restUntil > 0L) {
            lockReason = LockReason.RESTING
            restRemainingSeconds = LocalRestPolicy.remainingSeconds(restUntil, System.currentTimeMillis())
        }
        if (saved.reportUsage) {
            val plan = PolicyExecutionPlan(
                id = saved.sessionId,
                source = PolicyPlanSource.RULE,
                activation = saved.activation,
                endReason = saved.endReason.toPolicyEndReason(),
                budgetSeconds = saved.totalSeconds,
                deadlineEpochMs = saved.planDeadlineEpochMs.takeIf { it > 0 },
                accessMode = saved.accessMode,
                allowedPackages = saved.allowedPackages,
                restAfterSeconds = 0L,
                templateVersion = saved.templateVersion,
                sessionId = saved.sessionId,
            )
            runCatching {
                val event = RuleUsageEventFactory.end(
                    plan = plan,
                    usageDate = usageDate,
                    startedAtEpochMs = startedAt,
                    endedAtEpochMs = endedAtEpochMs,
                    sessionUsedSeconds = consumed,
                    usedSecondsToday = usedToday,
                    endReason = reason,
                )
                val queue = RuleUsageStore.load().also { it.enqueue(event) }
                RuleUsageStore.save(queue)
            }.onFailure { Log.w(TAG, "recovered END enqueue failed", it) }
        }
        Log.i(
            TAG,
            "recovered session settled id=${saved.sessionId} reason=$reason " +
                "end=$endedAtEpochMs consumed=$consumed",
        )
    }

    private fun newRuleSessionId(): String =
        "rule-" + UUID.randomUUID().toString().replace("-", "").take(24)

    private fun LocalSessionEnd.toPolicyEndReason(): PolicyEndReason = when (this) {
        LocalSessionEnd.SESSION_LIMIT -> PolicyEndReason.SESSION_LIMIT
        LocalSessionEnd.DAILY_LIMIT -> PolicyEndReason.DAILY_LIMIT
        LocalSessionEnd.WINDOW_END -> PolicyEndReason.WINDOW_END
    }

    @Synchronized
    fun initialize(ctx: Context) {
        context = ctx.applicationContext
        if (initialized) return
        if (!TvStore.isReady && !TvStore.init(ctx)) return
        recoveryComplete = false
        refreshInstalledPackages()
        // Read the activation first: the legacy v1 reader fills missing fields from the defaults
        // of the saved activation.
        activation = LocalPolicyStore.loadActivation()
        loadRuleSets()
        policyEpoch = LocalPolicyStore.policyEpoch()
        settledGrantRecords.clear()
        settledGrantRecords.addAll(LocalPolicyStore.settledGrantRecordIds())
        blockedWindowKey = LocalPolicyStore.autoBlockKey()

        // Load the two atomic partitions before deriving anything. The repository performs the
        // one-time migration from the pre-V37 scattered keys and reports the source it used.
        val nowWall = System.currentTimeMillis()
        val runtimeLoad = PolicyStateRepository.loadRuntimeOrMigrate()
        val savedRuntime = runtimeLoad.snapshot
        dayKey = LocalDate.now().toString()
        dayUsage.clear()
        savedRuntime.ledger.days.forEach { (date, seconds) ->
            if (USAGE_DATE.matches(date)) dayUsage[date] = seconds
        }
        usedSeconds = dayUsage[dayKey] ?: 0L
        breakUntil = savedRuntime.ledger.breakUntilEpochMs.takeIf { it > nowWall } ?: 0L
        lastSessionEndedAt = savedRuntime.ledger.lastEndedAtEpochMs
        settledSessions.clear()
        settledSessions.putAll(savedRuntime.ledger.settled)
        while (settledSessions.size > SETTLED_SESSION_LIMIT) {
            settledSessions.remove(settledSessions.keys.first())
        }
        restorePendingGrant(savedRuntime.ledger.pendingGrant, nowWall)

        guardSnapshot = PolicyStateRepository.loadGuard().snapshot
        val now = recoveryNow(nowWall)
        val screenInteractive = isScreenInteractive()
        val savedSession = savedRuntime.session
        val restored = restoreSavedSession(
            saved = savedSession,
            now = now,
            screenOff = guardSnapshot.screenOff,
            screenInteractive = screenInteractive,
        )
        session = restored
        if (restored == null || savedSession == null) {
            sessionOrigin = null
            activeRemoteRecordId = 0
            sessionReportUsage = false
            lastUsageReportedSeconds = 0L
            if (savedSession != null && rules?.isUsable != true) {
                // The plan cannot be rebuilt without its rules. Drop it rather than charge time
                // that cannot be reported; the legacy path behaved the same way.
                LocalPolicyStore.clearSession()
            }
        } else {
            sessionOrigin = savedSession.origin
            activeRemoteRecordId = savedSession.remoteRecordId
            sessionReportUsage = savedSession.reportUsage
            lastUsageReportedSeconds =
                (restored.totalSeconds - restored.remainingSeconds).coerceAtLeast(0L)
            // The dead gap up to this moment is now reflected in the budget and this checkpoint.
            markCheckpoint(nowWall)
        }

        // Older builds closed the whole window after a parent stop. Convert that block to the
        // rest interval derived from the last recorded session, so the child waits only until
        // the configured rest is over instead of until the next window.
        if (blockedWindowKey.isNotEmpty()) {
            val availableAt = legacyBlockAvailableAt(nowWall)
            blockedWindowKey = ""
            LocalPolicyStore.saveAutoBlockKey("")
            if (availableAt != null && availableAt > nowWall && breakUntil <= nowWall) {
                breakUntil = availableAt
            } else if (activation == Activation.AUTO && session == null && breakUntil == 0L) {
                // The rest is already over (or there is no record to derive one from): wait for
                // the child to press the start button instead of auto-starting behind the lock.
                LocalPolicyStore.saveAutoReady(true)
            }
        }

        val guardRestored = restoreGuard(
            saved = guardSnapshot,
            now = now,
            screenInteractive = screenInteractive,
            sessionIdle = session == null,
        )
        if (!guardRestored) {
            guardStatus = GuardStatus.ENFORCING
            parentOverrideUntil = 0L
            studyMode = false
        }

        // Recover the rest cursor if the process died after recording the session end but
        // before writing BREAK_UNTIL. V37 writes both in one snapshot; this only fills the gap
        // left behind by an older build's separate keys.
        if (session == null && breakUntil == 0L && lastSessionEndedAt > 0L) {
            recoverRestFromLastEnd(nowWall)
        }
        // AUTO_READY is a lock-screen state, not a transient tick value: after a reboot the
        // child must still see "开始计时" instead of the scheduler starting a segment by itself.
        val persistedAutoReady = LocalPolicyStore.autoReady()
        autoReady = activation == Activation.AUTO &&
            session == null &&
            rules?.isUsable == true &&
            persistedAutoReady
        if (persistedAutoReady && !autoReady) LocalPolicyStore.saveAutoReady(false)

        // The v2 phase is authoritative; the old boolean is kept in sync only for rollback.
        LocalPolicyStore.saveParentOverrideActive(guardStatus == GuardStatus.PARENT_OVERRIDE)
        saveGuardState(
            phase = guardStatus,
            override = guardSnapshot.override.takeIf {
                guardStatus == GuardStatus.PARENT_OVERRIDE
            },
            study = guardSnapshot.study.takeIf { guardStatus == GuardStatus.STUDY_MODE },
        )
        recoveryComplete = true
        initialized = true
        checkpointRuntime(durable = true, forceSync = true)
        publish()
    }

    /**
     * Applies the durable session to memory or settles it when it cannot continue. A dead gap is
     * charged only with complete evidence (same boot, screen on, no standby, no screen-off after
     * the checkpoint); otherwise the saved budget is kept, which is the safe undercount.
     */
    private fun restoreSavedSession(
        saved: SessionSnapshot?,
        now: RecoveryNow,
        screenOff: ScreenOffEvidence,
        screenInteractive: Boolean,
    ): LocalActiveSession? {
        if (saved == null) return null
        if (rules?.isUsable != true) return null
        expiredReason(saved, now.wallMs)?.let { reason ->
            settleRecovered(saved, reason, expiredEndedAt(saved, now.wallMs))
            return null
        }
        // MainActivity/HomeGuard write the shared BootOpen marker even when this runtime was not
        // the screen-off receiver. A screen-off while the process was dead must also disable the
        // dead-gap charge, which is the safe undercount.
        val externalScreenOff = context?.let {
            BootOpen.screenOffSince(it, saved.checkpointElapsedMs)
        } == true
        val gap = if (saved.phase == SessionPhase.PAUSED || externalScreenOff) {
            0L
        } else {
            PolicyRecoveryPolicy.deadGapSeconds(
                remainingSeconds = saved.remainingSeconds,
                checkpointElapsedMs = saved.checkpointElapsedMs,
                checkpointWallMs = saved.checkpointWallMs,
                checkpointAsleepMs = saved.checkpointAsleepMs,
                boot = saved.boot,
                now = now,
                screenInteractive = screenInteractive,
                screenOff = screenOff,
            )
        }
        val remaining = (saved.remainingSeconds - gap).coerceAtLeast(0L)
        if (remaining <= 0L) {
            settleRecovered(saved, saved.endReason.toPolicyEndReason(), now.wallMs)
            return null
        }
        // A process restart is not a screen-on action: resume a paused session silently when the
        // screen is lit, otherwise keep it paused so the budget stays untouched.
        val paused = saved.phase == SessionPhase.PAUSED || !screenInteractive
        return restoreSession(saved, remaining, paused, now.wallMs)
    }

    /**
     * Restores an armed grant only while it can still be confirmed. A stale epoch (rules were
     * changed while the process was off) or a passed window drops it; an expired record is
     * remembered so the grant poll cannot re-arm it.
     */
    private fun restorePendingGrant(saved: PendingGrantSnapshot?, nowMs: Long) {
        pendingRemoteStart = null
        if (saved == null) return
        val expired = saved.deadlineEpochMs > 0L && nowMs >= saved.deadlineEpochMs
        if (expired || saved.expectedEpoch != policyEpoch || activation != Activation.MANUAL) {
            if (expired && saved.recordId > 0) rememberSettledGrantRecord(saved.recordId)
            return
        }
        pendingRemoteStart = PendingRemoteStart(
            durationSeconds = saved.durationSeconds,
            expectedEpoch = saved.expectedEpoch,
            requestedScope = saved.requestedScope,
            accessMode = saved.accessMode,
            allowedPackages = saved.allowedPackages,
            restAfterSeconds = saved.restAfterSeconds,
            recordId = saved.recordId,
            source = when (saved.source) {
                PendingGrantSource.MQTT -> PendingSource.MQTT
                PendingGrantSource.GRANT -> PendingSource.GRANT
            },
            deadlineEpochMs = saved.deadlineEpochMs,
            validUntilLabel = saved.validUntilLabel,
            userName = saved.userName,
            userIconUrl = saved.userIconUrl,
        )
        lockReason = LockReason.NONE
    }

    /**
     * A screen-off recorded by another component (MainActivity/HomeGuard) while this runtime was
     * dead. BootOpen validates the stored marker against this boot itself.
     */
    private fun externalScreenOffSince(elapsedMs: Long): Boolean {
        val ctx = context ?: return false
        return BootOpen.screenOffSince(ctx, elapsedMs)
    }

    /**
     * Restores the guard phase only with evidence that the process did not outlive it: same boot,
     * no standby, no screen-off after the lease began, current screen on, and inside the caps.
     * Anything else locks to ENFORCING instead of silently keeping a parent unlock.
     */
    private fun restoreGuard(
        saved: GuardSnapshot,
        now: RecoveryNow,
        screenInteractive: Boolean,
        sessionIdle: Boolean,
    ): Boolean {
        if (!sessionIdle) return false
        return when (saved.phase) {
            GuardStatus.PARENT_OVERRIDE -> {
                val lease = saved.override ?: return false
                if (externalScreenOffSince(lease.startedElapsedMs)) return false
                if (!PolicyRecoveryPolicy.overrideRestorable(
                        lease,
                        now,
                        screenInteractive,
                        saved.screenOff,
                    )
                ) {
                    return false
                }
                guardStatus = GuardStatus.PARENT_OVERRIDE
                parentOverrideUntil = lease.untilWallMs
                true
            }
            GuardStatus.STUDY_MODE -> {
                val lease = saved.study ?: return false
                if (externalScreenOffSince(lease.startedElapsedMs)) return false
                if (!PolicyRecoveryPolicy.studyRestorable(
                        lease,
                        now,
                        screenInteractive,
                        saved.screenOff,
                    )
                ) {
                    return false
                }
                guardStatus = GuardStatus.STUDY_MODE
                studyMode = true
                true
            }
            GuardStatus.ENFORCING -> false
        }
    }

    /**
     * Loads both stored time-rule sets. A pre-v2 install is migrated once from its single v1
     * set (rest preserved); a corrupt v2 document fails closed to setup-required and is never
     * silently replaced by the v1 snapshot, which would discard the inactive set unnoticed.
     */
    private fun loadRuleSets() {
        when (val loaded = LocalPolicyStore.loadRuleSets()) {
            is LocalRuleSetsLoad.Loaded -> {
                ruleSets = loaded.ruleSets
                rules = loaded.ruleSets.rulesFor(activation)
                // Rewrite the v1 write-through with the activation actually in effect.
                LocalPolicyStore.saveRuleSets(loaded.ruleSets, activation)
            }
            LocalRuleSetsLoad.Missing -> {
                val legacy = LocalPolicyStore.loadRules()
                if (legacy == null) {
                    ruleSets = null
                    rules = null
                } else {
                    // Fresh install / pre-v2 upgrade: the inactive set gets its own first-use
                    // rest default instead of inheriting the active mode's value.
                    val migrated = LocalRuleSets.from(legacy, activation)
                    ruleSets = migrated
                    rules = migrated.rulesFor(activation)
                    LocalPolicyStore.saveRuleSets(migrated, activation)
                    PolicyFlowTrace.event(
                        "local.ruleSets.migrate",
                        "activation" to activation,
                        "rest" to legacy.restMinutes,
                    )
                }
            }
            is LocalRuleSetsLoad.Corrupt -> {
                ruleSets = null
                rules = null
                PolicyFlowTrace.event(
                    "local.ruleSets.corrupt",
                    "reason" to loaded.reason,
                )
            }
        }
    }

    /**
     * Clears guard state for a real mode switch; a cold start restore must not call this or it
     * would erase a parent unlock that [initialize] just recovered with evidence.
     */
    @Synchronized
    fun resetGuardForModeSwitch() {
        guardStatus = GuardStatus.ENFORCING
        parentOverrideUntil = 0L
        studyMode = false
        saveGuardState(phase = GuardStatus.ENFORCING, override = null, study = null)
        LocalPolicyStore.saveParentOverrideActive(false)
        // A pending remote grant belongs to the previous guard; the new mode must not inherit
        // an orange button or a half-armed reservation.
        val hadPending = pendingRemoteStart != null
        pendingRemoteStart = null
        if (initialized && hadPending) checkpointRuntime(durable = true, forceSync = true)
        // Keep a waiting state restored by initialize (a converted window block or a parent
        // override restart). Clearing it would let the first tick auto-start a session behind
        // the lock screen instead of leaving the start button for the child.
        publish()
    }

    /** Starts evaluating and auto-starting sessions; called only while the local guard is active. */
    @Synchronized
    fun activate() {
        if (!initialized) return
        active = true
        if (ticker == null) {
            ticker = scope.launch {
                while (isActive) {
                    delay(1_000)
                    onTick()
                }
            }
        }
        onTick()
    }

    @Synchronized
    fun deactivate() {
        active = false
        ticker?.cancel()
        ticker = null
        val hadPending = pendingRemoteStart != null
        pendingRemoteStart = null
        if (initialized && hadPending) checkpointRuntime(durable = true, forceSync = true)
        publish()
    }

    /** True when the hosted control plane owns configuration writes. */
    @Synchronized
    fun setSource(newSource: PolicySource) {
        if (source == newSource) return
        source = newSource
        val hadPending = pendingRemoteStart != null
        if (newSource != PolicySource.HOSTED) pendingRemoteStart = null
        if (initialized && hadPending && pendingRemoteStart == null) {
            checkpointRuntime(durable = true, forceSync = true)
        }
        publish()
    }

    /**
     * V29 removes the automatic/manual choice from every local surface. An upgraded TV may still
     * have AUTO persisted, so entering LOCAL forces MANUAL without touching either stored rule
     * set: the old automatic values remain available if this model ever returns.
     */
    @Synchronized
    fun ensureLocalManualActivation(): Boolean {
        if (!initialized) return false
        return if (activation == Activation.MANUAL) true else setActivation(Activation.MANUAL)
    }

    /**
     * The only activation switch. AUTO follows the scheduler and may start immediately; MANUAL
     * only changes the lock state because every manual segment needs a parent action. A real
     * switch also ends the rest cursor earned by the previous activation.
     */
    @Synchronized
    fun setActivation(newActivation: Activation): Boolean {
        if (!initialized) return false
        if (activation == newActivation) return true
        // End with the old activation still in effect, so rest/lock decisions are made under the
        // mode the session actually ran in. The switch itself must never auto-start a new plan.
        if (session != null) {
            finishSession(
                parentStop = false,
                emitCue = false,
                forSwitch = true,
                reasonOverride = PolicyEndReason.ACTIVATION_SWITCH,
            )
        }
        // A rest cursor belongs to the activation that earned it. End it before persisting the
        // new activation, so the target mode never waits out a break it did not configure and a
        // process restart cannot inherit the old mode's break.
        endRestForActivationSwitch()
        activation = newActivation
        LocalPolicyStore.saveActivation(newActivation)
        // Each activation owns its own time-rule set: the switch only swaps which stored set is
        // materialized for the executor. Neither set is rewritten (rest included).
        ruleSets?.let { sets ->
            rules = sets.rulesFor(newActivation)
            LocalPolicyStore.saveRuleSets(sets, newActivation)
        }
        policyEpoch = LocalPolicyStore.policyEpoch()
        // A switch invalidates any armed remote grant; only MANUAL accepts one, and its old
        // epoch can no longer be confirmed.
        pendingRemoteStart = null
        setAutoReady(false)
        lockReason = LockReason.NONE
        checkpointRuntime(durable = true, forceSync = true)
        if (guardStatus == GuardStatus.ENFORCING && session == null && !studyMode) {
            evaluateNow(allowAutoStart = false)
        }
        publish()
        return true
    }

    /**
     * A mode switch ends the timing state that belongs to the old activation. The caller settles
     * any running session; this clears its rest cursor so the new mode starts without the old
     * mode's break. The recovery timestamp is cleared first: if the process dies between the two
     * writes, initialize() must not rebuild the old rest from it. Daily usage and the configured
     * windows belong to the day, not the activation, and are deliberately left untouched.
     */
    private fun endRestForActivationSwitch() {
        lastSessionEndedAt = 0L
        breakUntil = 0L
        restRemainingSeconds = 0L
        lockReason = LockReason.NONE
        nextWindowLabel = ""
        if (initialized) checkpointRuntime(durable = true, forceSync = true)
    }

    /**
     * Screen off pauses active counting. Wall-clock time still runs toward the fixed deadline;
     * only the active budget is frozen. Repeating the broadcast is idempotent.
     */
    @Synchronized
    fun onScreenOff() {
        if (!initialized) return
        // Write the policy's own screen-off evidence before changing any phase. A ROM that
        // stops the process right after the broadcast must not erase the proof that a temporary
        // unlock ended, otherwise the next process would restore it.
        notePolicyScreenOff()
        when (guardStatus) {
            GuardStatus.PARENT_OVERRIDE -> endParentOverride()
            GuardStatus.STUDY_MODE -> exitStudyMode()
            GuardStatus.ENFORCING -> pauseSession(System.currentTimeMillis(), emitCue = true)
        }
    }

    /** Durable screen-off marker, committed before the guard/session transition it causes. */
    private fun notePolicyScreenOff() {
        screenOffEvidenceForEpisode = true
        val evidence = ScreenOffEvidence(
            elapsedMs = SystemClock.elapsedRealtime(),
            wallMs = System.currentTimeMillis(),
            boot = bootEvidence(),
        )
        guardSnapshot = guardSnapshot.copy(screenOff = evidence)
        PolicyStateRepository.saveGuard(guardSnapshot)
        context?.let { BootOpen.noteScreenOff(it) }
    }

    /**
     * Screen on resumes a paused session if the fixed deadline has not passed. If it has, the
     * session is settled at the deadline and no “resumed” cue is played. No new session starts.
     */
    @Synchronized
    fun onScreenOn() {
        if (!initialized) return
        screenOffEvidenceForEpisode = false
        val current = session ?: return
        val now = System.currentTimeMillis()
        if (SessionTiming.hasReachedDeadline(current.wallDeadlineEpochMs, now)) {
            finishSession(
                parentStop = false,
                emitCue = false,
                reasonOverride = wallEndReason(current),
                endedAtEpochMs = current.wallDeadlineEpochMs,
            )
            return
        }
        if (current.isPaused) {
            resumeSession(now, emitCue = true)
        } else {
            onTick()
        }
    }

    private fun pauseSession(nowMs: Long, emitCue: Boolean) {
        val current = session ?: return
        if (current.isPaused) return
        session = current.copy(pausedAtEpochMs = nowMs)
        markCheckpoint(nowMs)
        checkpointRuntime(durable = true, forceSync = true)
        if (emitCue) PolicyVoiceBus.emit(PolicyVoiceCue.PAUSED)
        publish()
    }

    private fun resumeSession(nowMs: Long, emitCue: Boolean) {
        val current = session ?: return
        if (!current.isPaused) return
        if (SessionTiming.hasReachedDeadline(current.wallDeadlineEpochMs, nowMs)) {
            finishSession(
                parentStop = false,
                emitCue = false,
                reasonOverride = wallEndReason(current),
                endedAtEpochMs = current.wallDeadlineEpochMs,
            )
            return
        }
        // Never write the effective remaining back into the budget: pauses must not change
        // settlement, only what the child is shown and when voice thresholds fire.
        session = current.copy(pausedAtEpochMs = 0L)
        markCheckpoint(nowMs)
        checkpointRuntime(durable = true, forceSync = true)
        if (emitCue) PolicyVoiceBus.emit(PolicyVoiceCue.RESUMED)
        publish()
    }

    /** Parent PIN from the lock screen: child mode pauses until the override ends. */
    @Synchronized
    fun submitPin(pin: String): PinResult {
        val result = LocalPolicyStore.verifyPin(pin)
        if (result == PinResult.OK) {
            // No timed auto-lock: a parent may watch a full movie. Turning the screen off
            // (or rebooting) ends the override and returns to the child's guard.
            beginParentOverride()
        }
        return result
    }

    /** Non-mutating check used before opening the parent center or starting manual time. */
    fun verifyPin(pin: String): PinResult = LocalPolicyStore.verifyPin(pin)

    @Synchronized
    fun setPin(pin: String): Boolean {
        if (!com.planyourtv.policy.LocalPin.isValid(pin)) return false
        LocalPolicyStore.setPin(pin)
        publish()
        return true
    }

    @Synchronized
    fun changePin(oldPin: String, newPin: String): Boolean {
        if (LocalPolicyStore.verifyPin(oldPin) != PinResult.OK) return false
        if (!com.planyourtv.policy.LocalPin.isValid(newPin)) return false
        LocalPolicyStore.setPin(newPin)
        publish()
        return true
    }

    /**
     * First-run setup: save PIN and rules together, then create the one-time recovery code.
     * Returns the clear recovery code for the setup screen; only its hash is stored.
     */
    @Synchronized
    fun completeSetup(pin: String, newRules: LocalRules, recoveryCode: String? = null): String? {
        if (!com.planyourtv.policy.LocalPin.isValid(pin)) return null
        val normalized = newRules.normalized()
        if (!normalized.isUsable) return null
        refreshInstalledPackages()
        LocalPolicyStore.setPin(pin)
        // First-run setup has one editor; both sets start from its values, but the inactive
        // activation keeps its own first-use rest default (manual 0 / automatic 10).
        val sets = LocalRuleSets.from(normalized, activation)
        ruleSets = sets
        rules = normalized
        LocalPolicyStore.saveRuleSets(sets, activation)
        policyEpoch = LocalPolicyStore.bumpEpoch()
        val code = recoveryCode?.takeIf { LocalRecovery.normalize(it) != null }
            ?: LocalRecovery.newCode()
        LocalPolicyStore.setRecovery(code)
        lockReason = LockReason.NONE
        publish()
        evaluateNow(allowAutoStart = activation == Activation.AUTO)
        return code
    }

    /**
     * Recovery reset: verifies the saved code and sets a new PIN. The code is shown only once,
     * at setup, and is intentionally not rotated or shown again; losing it means reinstalling.
     */
    @Synchronized
    fun resetWithRecovery(code: String, newPin: String): Boolean {
        if (!com.planyourtv.policy.LocalPin.isValid(newPin)) return false
        if (LocalPolicyStore.verifyRecovery(code) != PinResult.OK) return false
        LocalPolicyStore.setPin(newPin)
        guardStatus = GuardStatus.ENFORCING
        parentOverrideUntil = 0L
        studyMode = false
        saveGuardState(phase = GuardStatus.ENFORCING, override = null, study = null)
        LocalPolicyStore.saveParentOverrideActive(false)
        pendingRemoteStart = null
        setAutoReady(false)
        checkpointRuntime(durable = true, forceSync = true)
        publish()
        return true
    }

    @Synchronized
    fun verifyRecovery(code: String): PinResult = LocalPolicyStore.verifyRecovery(code)

    /** Seconds left on the recovery-code lockout; zero once another attempt may run. */
    @Synchronized
    fun recoveryLockSeconds(): Long = LocalPolicyStore.recoveryLockSeconds()

    @Synchronized
    fun setRules(newRules: LocalRules): Boolean =
        setRulesWithVersion(newRules, templateVersion)

    /**
     * Applies a rule template together with its version. Time limits are a next-session
     * snapshot, but the app allowlist is an access decision and takes effect immediately for
     * the running session too.
     */
    @Synchronized
    fun setRulesWithVersion(newRules: LocalRules, version: Long): Boolean {
        val normalized = newRules.normalized()
        if (!normalized.isUsable) return false
        refreshInstalledPackages()
        val updatedSets = (ruleSets ?: LocalRuleSets.from(normalized, activation))
            .withActiveRules(normalized, activation)
        ruleSets = updatedSets
        rules = normalized
        templateVersion = version.coerceAtLeast(0L)
        LocalPolicyStore.saveRuleSets(updatedSets, activation)
        policyEpoch = LocalPolicyStore.bumpEpoch()
        // A pending grant was admitted against the old window/scope and epoch. Dropping it here
        // avoids leaving an orange button that the confirm check can only reject as stale.
        if (pendingRemoteStart != null) {
            pendingRemoteStart = null
            lockReason = LockReason.NONE
        }
        // An empty study list is still a valid study mode (the launcher opens with no tiles),
        // so a rule refresh must not kick the child out of it.
        if (guardStatus == GuardStatus.ENFORCING && session == null && !studyMode &&
            pendingRemoteStart == null
        ) {
            // A configuration edit must not surprise the child by starting a session.
            evaluateNow(allowAutoStart = false)
        }
        publish()
        return true
    }

    /**
     * Applies the cloud's two rule sets. Only the materialized rules of the current activation
     * (including the shared app scope and buffer) may interrupt a session; a change confined to
     * the inactive set is persisted silently and takes effect when that activation is selected.
     */
    @Synchronized
    fun setRuleSetsWithVersion(newSets: LocalRuleSets, version: Long): Boolean {
        if (!initialized) return false
        val materialized = newSets.rulesFor(activation)
        if (!materialized.isUsable) return false
        refreshInstalledPackages()
        val activeChanged = rules != materialized
        ruleSets = newSets
        rules = materialized
        templateVersion = version.coerceAtLeast(0L)
        // The v1 write-through always carries the activation the executor is in right now; a
        // later setActivation rewrites it again for the new activation.
        LocalPolicyStore.saveRuleSets(newSets, activation)
        if (!activeChanged) {
            // Inactive-set-only change: stored for the next switch, no epoch bump and no
            // interruption of the running session.
            publish()
            return true
        }
        policyEpoch = LocalPolicyStore.bumpEpoch()
        // A pending grant was admitted against the old window/scope and epoch. Dropping it here
        // avoids leaving an orange button that the confirm check can only reject as stale.
        if (pendingRemoteStart != null) {
            pendingRemoteStart = null
            lockReason = LockReason.NONE
        }
        // An empty study list is still a valid study mode (the launcher opens with no tiles),
        // so a rule refresh must not kick the child out of it.
        if (guardStatus == GuardStatus.ENFORCING && session == null && !studyMode &&
            pendingRemoteStart == null
        ) {
            // A configuration edit must not surprise the child by starting a session.
            evaluateNow(allowAutoStart = false)
        }
        publish()
        return true
    }

    /** Reporting is optional. Without a sink the local engine still runs entirely offline. */
    @Synchronized
    fun setUsageSink(sink: RuleUsageSink?) {
        usageSink = sink
        // A sink attached after the session started still needs the END event. Upgrade the
        // durable flag so a later process restart keeps reporting this session.
        if (sink != null && session != null && !sessionReportUsage) {
            sessionReportUsage = true
            checkpointRuntime(durable = true)
        }
    }

    /** AUTO_READY crosses ticks and process restarts; persist only when it actually changes. */
    private fun setAutoReady(value: Boolean) {
        if (autoReady == value) return
        autoReady = value
        LocalPolicyStore.saveAutoReady(value)
    }

    val currentRules: LocalRules?
        get() = rules

    /**
     * Monotonic revision of the materialized rule configuration. Every legitimate write
     * (setup, rules, activation switch, remote mirror) bumps the stored epoch; the web parent
     * center only reads it to reject a stale write, never to make its own counter.
     */
    @get:Synchronized
    val rulesEpoch: Long
        get() = policyEpoch

    /** Both stored sets; null before setup or when the v2 document failed to parse. */
    val currentRuleSets: LocalRuleSets?
        get() = ruleSets

    /** Snapshot of the running plan; null while idle, resting, in study mode or overridden. */
    val currentPlan: PolicyExecutionPlan?
        @Synchronized get() = session?.plan

    val isSetupRequired: Boolean
        get() = recoveryComplete &&
            (!LocalPolicyStore.pinConfigured || rules?.isUsable != true)

    val isActive: Boolean
        get() = session != null

    /**
     * Start time of the running segment, zero when idle. The web control API uses it in the
     * confirmation fingerprint so a token issued for one timer cannot end a later one.
     */
    @Synchronized
    fun activeSessionStartedAtEpochMs(): Long = session?.startedAtEpochMs ?: 0L

    fun isLockScreen(): Boolean =
        recoveryComplete &&
            initialized &&
            guardStatus == GuardStatus.ENFORCING &&
            session == null &&
            !studyMode

    fun isTemporaryUnlocked(): Boolean = guardStatus == GuardStatus.PARENT_OVERRIDE

    fun isStudyMode(): Boolean = studyMode

    /** Parent center action: end the current session. Auto mode blocks the rest of the window. */
    @Synchronized
    fun endSessionByParent() {
        if (session == null) return
        finishSession(parentStop = true, emitCue = true)
    }

    /**
     * Child action in the TV control center. The used time is settled and the session ends
     * immediately. Automatic mode still takes one normal rest so “exit” cannot be used to skip
     * the rest protection; manual mode returns to the parent waiting state.
     */
    @Synchronized
    fun endSessionByUser(): Boolean {
        if (session == null) return false
        finishSession(
            parentStop = false,
            emitCue = false,
            reasonOverride = PolicyEndReason.USER_EXIT,
        )
        return true
    }

    /** Mode switch: commit the current session, without entering a rest interval. */
    @Synchronized
    fun endSessionForSwitch(): Boolean {
        if (session == null) return true
        finishSession(parentStop = false, emitCue = false, forSwitch = true)
        return true
    }

    /**
     * Lock-screen "start timing": only automatic mode and only after a completed rest. Manual
     * time is a separate parent action and never starts from this path.
     */
    @Synchronized
    fun startAutoSession(): SessionStartResult {
        if (!initialized || !active) {
            return SessionStartResult.Rejected(RejectReason.GUARD_DISABLED)
        }
        if (activation != Activation.AUTO) {
            return SessionStartResult.Rejected(RejectReason.ACTIVATION_MISMATCH)
        }
        if (guardStatus != GuardStatus.ENFORCING) {
            return SessionStartResult.Rejected(RejectReason.PARENT_OVERRIDE)
        }
        if (session != null) return SessionStartResult.Rejected(RejectReason.BUSY)
        if (studyMode) return SessionStartResult.Rejected(RejectReason.BUSY)
        if (!isScreenInteractive()) return SessionStartResult.Rejected(RejectReason.SCREEN_OFF)
        setAutoReady(false)
        evaluateNow(allowAutoStart = true)
        return if (session != null) {
            SessionStartResult.Started
        } else {
            SessionStartResult.Rejected(rejectForLock(lockReason))
        }
    }

    /**
     * Parent-confirmed local manual start. The caller has already verified the parent PIN.
     * Local mode and the V10 hosted rule guard both accept it; legacy controlled modes keep
     * rejecting it in [PolicyModeCoordinator.startLocalManualSession].
     */
    @Synchronized
    fun startManualSession(
        durationSeconds: Long,
        expiresAtEpochMs: Long? = null,
    ): SessionStartResult {
        // Leaving study mode is a separate parent action. A manual start (including the parent
        // web page) must not clear it as a side effect of commitStart.
        if (studyMode) return SessionStartResult.Rejected(RejectReason.BUSY)
        return startEntertainment(
            origin = SessionOrigin.LOCAL,
            durationSeconds = durationSeconds,
            expectedEpoch = null,
            requestedScope = null,
            expiresAtEpochMs = expiresAtEpochMs,
        )
    }

    /**
     * The quiz reward's own grant. Same admission as a manual start except the daily quota:
     * the child earned these minutes by answering, so refusing them with DAILY_LIMIT once the
     * day's quota is spent breaks the product's one sanctioned escape hatch (and the settle
     * screen then strands the child on the empty-bank QR page). The reward runs on its own
     * budget — maxEarnedMinutes caps the round and the optional earned-minutes ledger caps the
     * day — while its usage still counts into the day's totals like any other session.
     */
    @Synchronized
    fun startQuizRewardSession(durationSeconds: Long): SessionStartResult {
        if (studyMode) return SessionStartResult.Rejected(RejectReason.BUSY)
        return startEntertainment(
            origin = SessionOrigin.LOCAL,
            durationSeconds = durationSeconds,
            expectedEpoch = null,
            requestedScope = null,
            expiresAtEpochMs = null,
            exemptDailyQuota = true,
        )
    }

    /**
     * Hosted control plane: arm one manual entertainment segment. This deliberately does not
     * start the timer: both the MQTT command and the REST grant feed the same pending object,
     * and only the child's lock-screen confirmation (or an equivalent local action) starts it.
     * The phone must not report the session as running until the TV acknowledges a start.
     */
    @Synchronized
    fun startRemoteManualSession(
        durationSeconds: Long,
        expectedEpoch: Long,
        allowedPackages: Set<String>?,
        expiresAtEpochMs: Long? = null,
        recordId: Int = 0,
    ): SessionStartResult {
        PolicyFlowTrace.event(
            "grant.mqtt.seen",
            "recordId" to recordId,
            "duration" to durationSeconds,
            "pkgs" to allowedPackages?.size,
            "epoch" to expectedEpoch,
            "localEpoch" to policyEpoch,
        )
        val configured = rules?.normalized()?.takeIf { it.isUsable }
            ?: return SessionStartResult.Rejected(RejectReason.INVALID_COMMAND)
        val existing = pendingRemoteStart
        if (existing != null) {
            if (existing.expectedEpoch == policyEpoch &&
                existing.recordId > 0 && recordId > 0 && existing.recordId != recordId
            ) {
                // Two exchange records for one TV: the phone has to cancel the armed one first.
                return SessionStartResult.Rejected(RejectReason.BUSY)
            }
            // A retry of the same dispatch (or the REST grant of an MQTT-armed exchange) must
            // stay accepted without speaking again. A different duration/scope is a real
            // conflict: the phone has to cancel this one first.
            if (pendingMatches(existing, configured, durationSeconds, allowedPackages) &&
                existing.expectedEpoch == policyEpoch
            ) {
                if (existing.recordId == 0 && recordId > 0) {
                    // The MQTT command also carries the exchange record, so a later stop/cancel
                    // and the REST poll can match the same grant after confirmation.
                    pendingRemoteStart = existing.copy(
                        recordId = recordId,
                        source = PendingSource.MQTT,
                    )
                    publish()
                }
                return SessionStartResult.Pending
            }
            if (existing.expectedEpoch != policyEpoch) {
                // The epoch moved without the pending being cleared (for example a restored
                // process); it can never be confirmed, so replace it instead of reporting BUSY.
                pendingRemoteStart = null
                lockReason = LockReason.NONE
            } else {
                return SessionStartResult.Rejected(RejectReason.BUSY)
            }
        }
        return when (
            val admission = admitStart(
                origin = SessionOrigin.REMOTE,
                durationSeconds = durationSeconds,
                expectedEpoch = expectedEpoch,
                requestedScope = allowedPackages,
                expiresAtEpochMs = expiresAtEpochMs,
                confirmingPending = false,
            )
        ) {
            is StartAdmission.Refused -> {
                PolicyFlowTrace.event(
                    "grant.mqtt.reject",
                    "reason" to admission.reason,
                    "recordId" to recordId,
                )
                SessionStartResult.Rejected(admission.reason)
            }
            is StartAdmission.Ready -> {
                armPending(
                    admission = admission,
                    durationSeconds = durationSeconds,
                    expectedEpoch = expectedEpoch,
                    requestedScope = allowedPackages,
                    recordId = recordId,
                    source = PendingSource.MQTT,
                    deadlineEpochMs = admission.windowEndEpochMs,
                    validUntilLabel = "",
                )
                SessionStartResult.Pending
            }
        }
    }

    /**
     * The REST grant poll is the second hosted delivery path. A usable, not-yet-started grant
     * arms the same pending gate as the MQTT command. A grant already confirmed or canceled on
     * this TV is ignored so a polling loop cannot resurrect it.
     */
    @Synchronized
    fun armRemoteManualSessionFromGrant(config: PolicyConfig): SessionStartResult {
        PolicyFlowTrace.event(
            "grant.rest.seen",
            "recordId" to config.recordId,
            "cost" to config.costSeconds,
            "remaining" to config.remainingSeconds,
            "usable" to config.usable,
            "window" to config.validWindowSeconds,
            "access" to config.accessMode,
            "pkgs" to config.allowedPackages.size,
            "user" to config.userName.isNotEmpty(),
            "icon" to config.userIconUrl.isNotEmpty(),
        )
        if (config.recordId <= 0 || config.costSeconds > 0 || !config.usable) {
            PolicyFlowTrace.event("grant.rest.skip", "reason" to "INVALID")
            return SessionStartResult.Rejected(RejectReason.INVALID_COMMAND)
        }
        if (config.recordId in settledGrantRecords) {
            PolicyFlowTrace.event("grant.rest.skip", "reason" to "SETTLED")
            return SessionStartResult.Rejected(RejectReason.EXPIRED)
        }
        if (session != null) {
            val localStart = sessionOrigin == PolicySessionOrigin.LOCAL
            PolicyFlowTrace.event(
                "grant.rest.skip",
                "reason" to "SESSION_RUNNING",
                "localStart" to localStart,
            )
            // A local parent-confirmed start has no exchange record. A grant that arrives now
            // is a new authorization, not this session's record; leave it unconsumed so the
            // poll can arm it after the local session ends. Record-backed remote starts keep
            // consuming a raced grant as before.
            if (!localStart) rememberSettledGrantRecord(config.recordId)
            return SessionStartResult.Rejected(RejectReason.BUSY)
        }
        val configured = rules?.normalized()?.takeIf { it.isUsable }
            ?: return SessionStartResult.Rejected(RejectReason.INVALID_COMMAND)
        val requestedScope = if (config.accessMode == AccessMode.APPS) {
            config.allowedPackages
        } else {
            null
        }
        val nowMs = System.currentTimeMillis()
        val grantDeadline = config.validWindowSeconds
            ?.takeIf { it > 0L }
            ?.let { nowMs + it * 1_000L }
            ?: 0L

        val existing = pendingRemoteStart
        if (existing != null) {
            val sameStart = pendingMatches(
                existing,
                configured,
                config.remainingSeconds,
                requestedScope,
            )
            if (sameStart && existing.expectedEpoch == policyEpoch) {
                PolicyFlowTrace.event(
                    "grant.rest.reuse",
                    "recordId" to config.recordId,
                    "pendingRecordId" to existing.recordId,
                    "source" to existing.source,
                )
                // An MQTT-armed gate is completed by the exchange record (and becomes tied to
                // the poll); a repeated grant only refreshes the display fields it owns.
                val merged = existing.copy(
                    recordId = if (existing.recordId == 0) config.recordId else existing.recordId,
                    source = if (existing.recordId == 0) PendingSource.GRANT else existing.source,
                    deadlineEpochMs = earliest(existing.deadlineEpochMs, grantDeadline),
                    validUntilLabel = config.validUntilLabel.ifEmpty { existing.validUntilLabel },
                    userName = config.userName.ifEmpty { existing.userName },
                    userIconUrl = config.userIconUrl.ifEmpty { existing.userIconUrl },
                )
                if (merged != existing) {
                    pendingRemoteStart = merged
                    publish()
                }
                return SessionStartResult.Pending
            }
            if (existing.expectedEpoch == policyEpoch) {
                PolicyFlowTrace.event(
                    "grant.rest.skip",
                    "reason" to "PENDING_CONFLICT",
                    "recordId" to config.recordId,
                    "pendingRecordId" to existing.recordId,
                )
                return SessionStartResult.Rejected(RejectReason.BUSY)
            }
            pendingRemoteStart = null
            lockReason = LockReason.NONE
        }

        return when (
            val admission = admitStart(
                origin = SessionOrigin.REMOTE,
                durationSeconds = config.remainingSeconds,
                expectedEpoch = policyEpoch,
                requestedScope = requestedScope,
                expiresAtEpochMs = null,
                confirmingPending = false,
            )
        ) {
            is StartAdmission.Refused -> {
                PolicyFlowTrace.event(
                    "grant.rest.skip",
                    "reason" to admission.reason,
                    "recordId" to config.recordId,
                )
                SessionStartResult.Rejected(admission.reason)
            }
            is StartAdmission.Ready -> {
                armPending(
                    admission = admission,
                    durationSeconds = config.remainingSeconds,
                    expectedEpoch = policyEpoch,
                    requestedScope = requestedScope,
                    recordId = config.recordId,
                    source = PendingSource.GRANT,
                    deadlineEpochMs = earliest(admission.windowEndEpochMs, grantDeadline),
                    validUntilLabel = config.validUntilLabel,
                    userName = config.userName,
                    userIconUrl = config.userIconUrl,
                )
                SessionStartResult.Pending
            }
        }
    }

    /** The child pressed "开始计时" on the armed grant. Revalidates before consuming any budget. */
    @Synchronized
    fun confirmRemoteManualSession(): SessionStartResult {
        val pending = pendingRemoteStart
            ?: return SessionStartResult.Rejected(RejectReason.INVALID_COMMAND)
        PolicyFlowTrace.event(
            "grant.confirm",
            "recordId" to pending.recordId,
            "source" to pending.source,
            "duration" to pending.durationSeconds,
            "epoch" to policyEpoch,
        )
        val nowMs = System.currentTimeMillis()
        if (pending.deadlineEpochMs > 0L && nowMs >= pending.deadlineEpochMs) {
            PolicyFlowTrace.event("grant.confirm.reject", "reason" to "EXPIRED")
            dropPending(pending, rememberRecord = true)
            return SessionStartResult.Rejected(RejectReason.EXPIRED)
        }
        val admission = admitStart(
            origin = SessionOrigin.REMOTE,
            durationSeconds = pending.durationSeconds,
            expectedEpoch = pending.expectedEpoch,
            requestedScope = pending.requestedScope,
            expiresAtEpochMs = null,
            confirmingPending = true,
        )
        if (admission is StartAdmission.Refused) {
            PolicyFlowTrace.event(
                "grant.confirm.reject",
                "recordId" to pending.recordId,
                "reason" to admission.reason,
            )
            // Rest and screen-off are transient: keep the orange gate for when they pass.
            // Every other refusal describes an armed grant that can no longer start.
            if (admission.reason != RejectReason.RESTING &&
                admission.reason != RejectReason.SCREEN_OFF
            ) {
                dropPending(
                    pending,
                    rememberRecord = admission.reason in setOf(
                        RejectReason.EXPIRED,
                        RejectReason.OUTSIDE_WINDOW,
                        RejectReason.NO_TIMED_APPS,
                        RejectReason.INVALID_SCOPE,
                        RejectReason.INVALID_COMMAND,
                    ),
                )
            }
            return SessionStartResult.Rejected(admission.reason)
        }
        val ready = admission as StartAdmission.Ready
        PolicyFlowTrace.event(
            "grant.confirm.started",
            "recordId" to pending.recordId,
            "duration" to pending.durationSeconds,
            "access" to ready.scope.accessMode,
            "pkgs" to ready.scope.allowedPackages.size,
        )
        rememberSettledGrantRecord(pending.recordId)
        pendingRemoteStart = null
        commitStart(
            ready,
            origin = SessionOrigin.REMOTE,
            remoteRecordId = pending.recordId,
        )
        return SessionStartResult.Started
    }

    /** MQTT cancel_session while the grant is still waiting for the child. */
    @Synchronized
    fun cancelRemotePending(commandRecordId: Int): Boolean {
        val pending = pendingRemoteStart ?: return false
        if (commandRecordId > 0 && pending.recordId > 0 &&
            pending.recordId != commandRecordId
        ) {
            return false
        }
        dropPending(pending, rememberRecord = true)
        PolicyVoiceBus.emit(PolicyVoiceCue.CANCELED)
        return true
    }

    /** MQTT stop_session: an active segment or an armed grant both belong to the parent. */
    @Synchronized
    fun stopRemoteSessionByParent(commandRecordId: Int = 0): Boolean {
        if (session != null) {
            endSessionByParent()
            return true
        }
        val pending = pendingRemoteStart ?: return false
        if (commandRecordId > 0 && pending.recordId > 0 &&
            pending.recordId != commandRecordId
        ) {
            return false
        }
        dropPending(pending, rememberRecord = true)
        PolicyVoiceBus.emit(PolicyVoiceCue.PARENT_STOP)
        return true
    }

    /** The REST grant disappeared (settled, canceled or expired in uniapp). */
    @Synchronized
    fun onHostedGrantRemoved() {
        PolicyFlowTrace.event(
            "grant.removed",
            "session" to (if (session != null) "ACTIVE" else "IDLE"),
            "remaining" to session?.remainingSeconds,
            "pendingRecordId" to pendingRemoteStart?.recordId,
            "pendingSource" to pendingRemoteStart?.source,
            "activeRecordId" to activeRemoteRecordId,
        )
        if (session != null) {
            // A record-backed remote segment is tied to the grant poll. If the snapshot no
            // longer carries the record, the parent/backend ended it; MQTT may be down, so the
            // snapshot is the stop fallback. Never touch LOCAL, automatic or PTV (record-less)
            // sessions here.
            if (activeRemoteRecordId > 0) endSessionByParent()
            return
        }
        val pending = pendingRemoteStart ?: return
        // Only a REST-armed gate is tied to the grant poll. A direct MQTT dispatch may not
        // create an exchange record at all, so an empty poll must not cancel it silently.
        if (pending.source != PendingSource.GRANT || pending.recordId <= 0) return
        dropPending(pending, rememberRecord = true)
        PolicyVoiceBus.emit(PolicyVoiceCue.CANCELED)
    }

    private enum class SessionOrigin { LOCAL, REMOTE }

    private data class ResolvedScope(
        val accessMode: AccessMode,
        val allowedPackages: Set<String>,
    )

    private sealed interface StartAdmission {
        data class Ready(
            val configured: LocalRules,
            val scope: ResolvedScope,
            /** Already capped to the daily quota and the per-session limit for local starts. */
            val budgetSeconds: Long,
            val restAfterSeconds: Long,
            val nowMs: Long,
            val windowEndEpochMs: Long,
        ) : StartAdmission

        data class Refused(val reason: RejectReason) : StartAdmission
    }

    private fun startEntertainment(
        origin: SessionOrigin,
        durationSeconds: Long,
        expectedEpoch: Long?,
        requestedScope: Set<String>?,
        expiresAtEpochMs: Long?,
        exemptDailyQuota: Boolean = false,
    ): SessionStartResult = when (
        val admission = admitStart(
            origin = origin,
            durationSeconds = durationSeconds,
            expectedEpoch = expectedEpoch,
            requestedScope = requestedScope,
            expiresAtEpochMs = expiresAtEpochMs,
            confirmingPending = false,
            exemptDailyQuota = exemptDailyQuota,
        )
    ) {
        is StartAdmission.Refused -> SessionStartResult.Rejected(admission.reason)
        is StartAdmission.Ready -> {
            commitStart(admission, origin)
            SessionStartResult.Started
        }
    }

    /**
     * All policy checks shared by a direct local start, a remote arm and the child's confirm.
     * It never mutates state, so a rejected command leaves study mode and any pending grant
     * exactly as they were.
     */
    private fun admitStart(
        origin: SessionOrigin,
        durationSeconds: Long,
        expectedEpoch: Long?,
        requestedScope: Set<String>?,
        expiresAtEpochMs: Long?,
        confirmingPending: Boolean,
        exemptDailyQuota: Boolean = false,
    ): StartAdmission {
        if (!initialized || !active) return StartAdmission.Refused(RejectReason.GUARD_DISABLED)
        if (durationSeconds <= 0) {
            return StartAdmission.Refused(RejectReason.INVALID_COMMAND)
        }
        val configured = rules?.normalized()?.takeIf { it.isUsable }
            ?: return StartAdmission.Refused(RejectReason.INVALID_COMMAND)
        if (origin == SessionOrigin.LOCAL &&
            source != PolicySource.LOCAL && source != PolicySource.HOSTED
        ) {
            return StartAdmission.Refused(RejectReason.INVALID_COMMAND)
        }
        if (origin == SessionOrigin.REMOTE && source != PolicySource.HOSTED) {
            return StartAdmission.Refused(RejectReason.INVALID_COMMAND)
        }
        if (activation != Activation.MANUAL) {
            return StartAdmission.Refused(RejectReason.ACTIVATION_MISMATCH)
        }
        if (expectedEpoch != null && expectedEpoch != policyEpoch) {
            return StartAdmission.Refused(RejectReason.STALE_EPOCH)
        }
        if (guardStatus == GuardStatus.PARENT_OVERRIDE) {
            return StartAdmission.Refused(RejectReason.PARENT_OVERRIDE)
        }
        if (session != null) return StartAdmission.Refused(RejectReason.BUSY)
        if (!confirmingPending && pendingRemoteStart != null) {
            return StartAdmission.Refused(RejectReason.BUSY)
        }
        val nowMs = System.currentTimeMillis()
        if (breakUntil > nowMs) return StartAdmission.Refused(RejectReason.RESTING)
        if (!isScreenInteractive()) return StartAdmission.Refused(RejectReason.SCREEN_OFF)
        val now = LocalDateTime.now()
        val window = currentWindow(configured, now)
            ?: return StartAdmission.Refused(RejectReason.OUTSIDE_WINDOW)
        if (expiresAtEpochMs != null && nowMs > expiresAtEpochMs) {
            return StartAdmission.Refused(RejectReason.EXPIRED)
        }
        if (!configured.hasTimedApps()) {
            return StartAdmission.Refused(RejectReason.NO_TIMED_APPS)
        }
        val scope = resolveScope(configured, requestedScope)
            ?: return StartAdmission.Refused(RejectReason.INVALID_SCOPE)
        val secondsLeft = (window.endSecond - secondOfDay(now)).coerceAtLeast(1)
        // V29 has no automatic/manual switch on the page: the local rules are the manual rules.
        // The web page no longer exposes a per-session cap; a quiz reward is already capped by
        // its own maxEarnedMinutes, and a parent-chosen duration still obeys the daily quota.
        // Remote hosted grants keep their own server-side budget.
        // Quiz rewards are the exception the comment already promised: rejecting them with
        // DAILY_LIMIT once the day's quota is spent leaves a child who just earned time with
        // nothing (reproduced on device, 2026-11-16). The reward starts on its own budget and
        // its usage still counts into the day's ledger like any other session.
        val budgetSeconds = if (origin == SessionOrigin.LOCAL && !exemptDailyQuota) {
            val dailyRemaining = (configured.autoDailyLimitMinutes * 60L - usedSeconds)
                .coerceAtLeast(0L)
            if (dailyRemaining <= 0L) return StartAdmission.Refused(RejectReason.DAILY_LIMIT)
            minOf(durationSeconds, dailyRemaining)
        } else {
            durationSeconds
        }
        return StartAdmission.Ready(
            configured = configured,
            scope = scope,
            budgetSeconds = budgetSeconds,
            restAfterSeconds = configured.restMinutes * 60L,
            nowMs = nowMs,
            windowEndEpochMs = nowMs + secondsLeft * 1_000L,
        )
    }

    private fun commitStart(
        admission: StartAdmission.Ready,
        origin: SessionOrigin,
        remoteRecordId: Int = 0,
    ) {
        // Commit only after every check passes and the scope is resolved. A rejected command
        // must leave study mode intact.
        if (studyMode) {
            studyMode = false
            guardStatus = GuardStatus.ENFORCING
            saveGuardState(phase = GuardStatus.ENFORCING, override = null, study = null)
        }
        startPlan(
            activation = PolicyActivation.MANUAL,
            budgetSeconds = admission.budgetSeconds,
            endReason = PolicyEndReason.SESSION_LIMIT,
            restAfterSeconds = admission.restAfterSeconds,
            accessMode = admission.scope.accessMode,
            allowedPackages = admission.scope.allowedPackages,
            deadlineEpochMs = null,
            nowMs = admission.nowMs,
            origin = origin,
            remoteRecordId = remoteRecordId,
        )
    }

    private fun armPending(
        admission: StartAdmission.Ready,
        durationSeconds: Long,
        expectedEpoch: Long,
        requestedScope: Set<String>?,
        recordId: Int,
        source: PendingSource,
        deadlineEpochMs: Long,
        validUntilLabel: String,
        userName: String = "",
        userIconUrl: String = "",
    ) {
        if (studyMode) {
            studyMode = false
            guardStatus = GuardStatus.ENFORCING
            saveGuardState(phase = GuardStatus.ENFORCING, override = null, study = null)
        }
        pendingRemoteStart = PendingRemoteStart(
            durationSeconds = durationSeconds,
            expectedEpoch = expectedEpoch,
            requestedScope = requestedScope,
            accessMode = admission.scope.accessMode,
            allowedPackages = admission.scope.allowedPackages,
            restAfterSeconds = admission.restAfterSeconds,
            recordId = recordId,
            source = source,
            deadlineEpochMs = deadlineEpochMs,
            validUntilLabel = validUntilLabel,
            userName = userName,
            userIconUrl = userIconUrl,
        )
        setAutoReady(false)
        lockReason = LockReason.NONE
        restRemainingSeconds = 0L
        PolicyFlowTrace.event(
            "grant.arm",
            "recordId" to recordId,
            "source" to source,
            "duration" to durationSeconds,
            "deadlineIn" to (deadlineEpochMs.takeIf { it > 0L }
                ?.let { (it - System.currentTimeMillis()).coerceAtLeast(0L) / 1_000L }),
            "access" to admission.scope.accessMode,
            "pkgs" to admission.scope.allowedPackages.size,
            "user" to userName.isNotEmpty(),
            "icon" to userIconUrl.isNotEmpty(),
            "epoch" to policyEpoch,
        )
        checkpointRuntime(durable = true, forceSync = true)
        publish()
        // The arm itself is what the child hears; START is spoken only after confirmation.
        PolicyVoiceBus.emit(PolicyVoiceCue.GRANT_READY)
    }

    /** A rejecting caller drops the gate and recomputes the normal lock-screen state. */
    private fun dropPending(pending: PendingRemoteStart, rememberRecord: Boolean) {
        if (pendingRemoteStart !== pending) return
        PolicyFlowTrace.event(
            "grant.drop",
            "recordId" to pending.recordId,
            "source" to pending.source,
            "remember" to rememberRecord,
            "session" to (if (session != null) "ACTIVE" else "IDLE"),
        )
        pendingRemoteStart = null
        if (rememberRecord) rememberSettledGrantRecord(pending.recordId)
        lockReason = LockReason.NONE
        checkpointRuntime(durable = true, forceSync = true)
        if (guardStatus == GuardStatus.ENFORCING && !studyMode) {
            evaluateNow(allowAutoStart = false)
        } else {
            publish()
        }
    }

    private fun rememberSettledGrantRecord(recordId: Int) {
        if (recordId <= 0) return
        settledGrantRecords.add(recordId)
        while (settledGrantRecords.size > SETTLED_GRANT_LIMIT) {
            settledGrantRecords.remove(settledGrantRecords.first())
        }
        LocalPolicyStore.saveSettledGrantRecordIds(settledGrantRecords)
    }

    private fun pendingMatches(
        pending: PendingRemoteStart,
        configured: LocalRules,
        durationSeconds: Long,
        requestedScope: Set<String>?,
    ): Boolean {
        val scope = resolveScope(configured, requestedScope) ?: return false
        return pending.durationSeconds == durationSeconds &&
            pending.accessMode == scope.accessMode &&
            pending.allowedPackages == scope.allowedPackages
    }

    /** Zero means no deadline; the earlier real deadline wins. */
    private fun earliest(first: Long, second: Long): Long = when {
        first > 0L && second > 0L -> minOf(first, second)
        first > 0L -> first
        else -> second
    }

    /**
     * Resolves the requested entertainment scope against the effective timed packages. A null
     * requested scope means every timed app; study apps are always removed. All-apps is expanded
     * to the installed launchers so the executor can publish a concrete APPS scope; empty is
     * invalid for a start and is never widened to all apps.
     */
    private fun resolveScope(configured: LocalRules, requested: Set<String>?): ResolvedScope? {
        val available = configured.timedPackages()
        val timed = requested?.let { it intersect available } ?: available
        return if (timed.isEmpty()) null else ResolvedScope(AccessMode.APPS, timed)
    }

    @Synchronized
    fun onTick() {
        if (!initialized || !active) return
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        if (today.toString() != dayKey) {
            // A local day boundary ends any session that crossed midnight; the new date starts
            // with a fresh quota, as the V5 rules specify. Report the old date's usage first.
            if (session != null) {
                finishSession(parentStop = false, emitCue = false, crossDay = true)
            }
            dayKey = today.toString()
            usedSeconds = dayUsage[dayKey] ?: 0L
            setAutoReady(false)
            // The armed grant's window/deadline belongs to the old day; never carry an orange
            // confirm button into a new quota day.
            pendingRemoteStart?.let { rememberSettledGrantRecord(it.recordId) }
            pendingRemoteStart = null
            blockedWindowKey = ""
            LocalPolicyStore.saveAutoBlockKey("")
            checkpointRuntime(durable = true, forceSync = true)
        }
        if (breakUntil > 0 && now >= breakUntil) {
            breakUntil = 0L
            lastSessionEndedAt = 0L
            checkpointRuntime(durable = true)
            if (guardStatus == GuardStatus.ENFORCING && session == null && !studyMode &&
                isScreenInteractive()
            ) {
                // Rest is over. AUTO_READY is only entered with the screen on; while the screen
                // is off the lock keeps its rest state and transitions after the screen lights.
                enterRestCompletedState()
                publish()
            }
        }

        // An armed remote grant is a wall-clock reservation: its grant/window deadline can pass
        // while the screen is off. Drop it silently rather than leaving a confirm button that
        // could only reject. Rest and screen-off at confirm time are handled separately.
        pendingRemoteStart?.let { armed ->
            val configured = rules?.normalized()
            val windowOpen = configured != null && currentWindow(configured, LocalDateTime.now()) != null
            val deadlinePassed = armed.deadlineEpochMs > 0L && now >= armed.deadlineEpochMs
            if (activation != Activation.MANUAL || !windowOpen || deadlinePassed) {
                PolicyFlowTrace.event(
                    "grant.expire",
                    "recordId" to armed.recordId,
                    "activation" to activation,
                    "windowOpen" to windowOpen,
                    "deadlinePassed" to deadlinePassed,
                )
                rememberSettledGrantRecord(armed.recordId)
                pendingRemoteStart = null
                lockReason = LockReason.NONE
                checkpointRuntime(durable = true, forceSync = true)
                if (guardStatus == GuardStatus.ENFORCING && !studyMode) {
                    evaluateNow(allowAutoStart = false)
                } else {
                    publish()
                }
                return
            }
        }

        // The fixed wall deadline runs even while the screen is off and the ticker survives
        // sleep. Settle at the deadline itself, not at the moment the TV wakes up.
        session?.takeIf {
            SessionTiming.hasReachedDeadline(it.wallDeadlineEpochMs, now)
        }?.let { expired ->
            finishSession(
                parentStop = false,
                emitCue = false,
                reasonOverride = wallEndReason(expired),
                endedAtEpochMs = expired.wallDeadlineEpochMs,
            )
            return
        }

        if (!isScreenInteractive()) {
            // The broadcast may have been missed or lost with a killed process. Record the
            // evidence here before pausing/ending anything; one marker per episode is enough.
            if (!screenOffEvidenceForEpisode) notePolicyScreenOff()
            if (guardStatus == GuardStatus.ENFORCING && session != null) {
                pauseSession(now, emitCue = true)
            }
            when (guardStatus) {
                GuardStatus.PARENT_OVERRIDE -> endParentOverride()
                GuardStatus.STUDY_MODE -> exitStudyMode()
                GuardStatus.ENFORCING -> Unit
            }
            return
        }

        // The wall-clock rest ended while the screen was off. Enter the same waiting state now,
        // without letting the scheduler start a segment.
        if (guardStatus == GuardStatus.ENFORCING && session == null && !studyMode &&
            lockReason == LockReason.RESTING && breakUntil == 0L
        ) {
            enterRestCompletedState()
            publish()
            return
        }

        if (guardStatus == GuardStatus.PARENT_OVERRIDE) {
            val lease = guardSnapshot.override
            val capped = lease != null &&
                ((lease.hardCapWallMs > 0L && now >= lease.hardCapWallMs) ||
                    (lease.hardCapElapsedMs > 0L &&
                        SystemClock.elapsedRealtime() >= lease.hardCapElapsedMs))
            if (capped || (parentOverrideUntil > 0 && now >= parentOverrideUntil)) {
                endParentOverride()
            } else {
                publish()
            }
            return
        }
        if (guardStatus == GuardStatus.STUDY_MODE) {
            val lease = guardSnapshot.study
            val capped = lease != null &&
                ((lease.hardCapWallMs > 0L && now >= lease.hardCapWallMs) ||
                    (lease.hardCapElapsedMs > 0L &&
                        SystemClock.elapsedRealtime() >= lease.hardCapElapsedMs))
            if (capped) exitStudyMode() else publish()
            return
        }

        val current = session
        if (current != null) {
            if (current.isPaused) {
                // Covers a missed screen-on broadcast. Both this path and the broadcast path
                // clear pausedAt under the same lock, so the resume cue plays at most once.
                resumeSession(now, emitCue = true)
                return
            }
            val remaining = current.remainingSeconds - 1
            session = current.copy(remainingSeconds = remaining.coerceAtLeast(0L))
            if (remaining <= 0) {
                finishSession(parentStop = false, emitCue = true)
                return
            }
            lastSavedTick += 1
            if (lastSavedTick >= SAVE_EVERY_TICKS) {
                lastSavedTick = 0
                markCheckpoint(now)
                checkpointRuntime()
            }
            val consumed = (current.totalSeconds - remaining).coerceAtLeast(0L)
            if (consumed - lastUsageReportedSeconds >= USAGE_PROGRESS_SECONDS) {
                lastUsageReportedSeconds = consumed
                emitUsage(
                    current = current,
                    type = RuleUsageEventType.PROGRESS,
                    sessionUsedSeconds = consumed,
                    usedSecondsToday = usedTodayFor(current, consumed),
                )
            }
            maybeVoiceThreshold(
                SessionTiming.effectiveRemainingSeconds(
                    budgetRemainingSeconds = remaining,
                    wallDeadlineEpochMs = current.wallDeadlineEpochMs,
                    nowMs = now,
                ),
            )
            publish()
            return
        }

        // An armed manual grant owns the lock screen until the child confirms or it expires.
        // Do not let the generic waiting state replace its title/body or auto-start behind it.
        if (pendingRemoteStart != null) {
            lockReason = LockReason.NONE
            publish()
            return
        }

        // AUTO_READY waits for the child. Passing false here keeps a tick from starting a
        // session behind the lock screen; the explicit start button passes true.
        evaluateNow(allowAutoStart = activation == Activation.AUTO && !autoReady)
    }

    @Synchronized
    private fun evaluateNow(allowAutoStart: Boolean) {
        if (guardStatus != GuardStatus.ENFORCING || studyMode) return
        val configured = rules?.normalized()
        if (configured == null || !configured.isUsable || !LocalPolicyStore.pinConfigured) {
            setAutoReady(false)
            lockReason = LockReason.SETUP_REQUIRED
            nextWindowLabel = ""
            restRemainingSeconds = 0
            publish()
            return
        }
        if (!configured.hasTimedApps()) {
            setAutoReady(false)
            lockReason = LockReason.NO_TIMED_APPS
            nextWindowLabel = ""
            restRemainingSeconds = 0
            publish()
            return
        }
        if (activation == Activation.MANUAL) {
            lockManualWaiting(configured)
            publish()
            return
        }

        val now = LocalDateTime.now()
        val nowMs = System.currentTimeMillis()
        val breakSeconds = LocalRestPolicy.remainingSeconds(breakUntil, nowMs)
        if (breakSeconds > 0) {
            setAutoReady(false)
            lockReason = LockReason.RESTING
            restRemainingSeconds = breakSeconds
            publish()
            return
        }
        val windowKey = windowKey(configured, now)
        if (blockedWindowKey.isNotEmpty() && blockedWindowKey != windowKey) {
            blockedWindowKey = ""
            LocalPolicyStore.saveAutoBlockKey("")
        }
        if (windowKey.isNotEmpty() && blockedWindowKey == windowKey) {
            setAutoReady(false)
            lockReason = LockReason.AUTO_BLOCKED
            nextWindowLabel = nextWindowLabelFor(configured, now)
            restRemainingSeconds = 0
            publish()
            return
        }

        val decision = RulePlanScheduler.decide(
            dayOfWeek = now.dayOfWeek.value,
            secondOfDay = secondOfDay(now),
            rules = configured,
            templateVersion = templateVersion,
            sessionId = newRuleSessionId(),
            usedSecondsToday = usedSeconds,
            breakRemainingSeconds = breakSeconds,
            accessMode = AccessMode.APPS,
            allowedPackages = configured.timedPackages(),
        )
        when (decision) {
            is RulePlanDecision.SetupRequired -> {
                setAutoReady(false)
                lockReason = LockReason.SETUP_REQUIRED
                nextWindowLabel = ""
                restRemainingSeconds = 0
            }
            is RulePlanDecision.Locked -> {
                setAutoReady(false)
                lockReason = decision.reason
                nextWindowLabel = labelFor(
                    decision.nextWindowDayOffset,
                    decision.nextWindowMinuteOfDay,
                )
                restRemainingSeconds = decision.breakRemainingSeconds
            }
            is RulePlanDecision.Start -> {
                lockReason = LockReason.NONE
                nextWindowLabel = ""
                restRemainingSeconds = 0
                if (allowAutoStart && isScreenInteractive()) {
                    startPlanFromScheduler(decision.plan, nowMs)
                } else {
                    setAutoReady(true)
                    lockReason = LockReason.AUTO_READY
                }
            }
        }
        publish()
    }

    /** Rest completed with the screen on: re-decide instead of blindly enabling a start. */
    private fun enterRestCompletedState() {
        // The window may have closed or the daily quota may have run out while the child was
        // resting, so let the scheduler choose between the start button and the next lock.
        evaluateNow(allowAutoStart = false)
    }

    private fun lockManualWaiting(configured: LocalRules) {
        val now = LocalDateTime.now()
        val nowMs = System.currentTimeMillis()
        restRemainingSeconds = LocalRestPolicy.remainingSeconds(breakUntil, nowMs)
        lockReason = if (restRemainingSeconds > 0) LockReason.RESTING else LockReason.MANUAL_WAITING
        nextWindowLabel = nextWindowLabelFor(configured, now)
    }

    private fun startPlanFromScheduler(plan: PolicyExecutionPlan, nowMs: Long) {
        sessionOrigin = null
        activeRemoteRecordId = 0
        sessionReportUsage = usageSink != null
        session = LocalActiveSession(
            plan = plan,
            remainingSeconds = plan.budgetSeconds,
            startedAtEpochMs = nowMs,
            usageDate = dayKey,
            wallDeadlineEpochMs = wallDeadlineFor(plan, nowMs),
        )
        setAutoReady(false)
        spokenThresholds.clear()
        lastUsageReportedSeconds = 0L
        markCheckpoint(nowMs)
        checkpointRuntime(durable = true, forceSync = true)
        emitUsage(session!!, RuleUsageEventType.START, 0L, usedTodayFor(session!!, 0L))
        PolicyVoiceBus.emit(PolicyVoiceCue.START)
    }

    private fun startPlan(
        activation: PolicyActivation,
        budgetSeconds: Long,
        endReason: PolicyEndReason,
        restAfterSeconds: Long,
        accessMode: AccessMode,
        allowedPackages: Set<String>,
        deadlineEpochMs: Long?,
        nowMs: Long,
        origin: SessionOrigin? = null,
        remoteRecordId: Int = 0,
    ) {
        // A started plan is the only state that owns the timer; never leave an armed grant
        // beside it (a confirm already cleared it, this is the defensive path).
        pendingRemoteStart = null
        activeRemoteRecordId = remoteRecordId
        // Set before the durable write so a process death cannot lose a parent-confirmed local
        // start or the grant record that owns the session.
        sessionOrigin = origin?.toSessionOrigin(remoteRecordId)
        sessionReportUsage = usageSink != null
        val sessionId = newRuleSessionId()
        val plan = PolicyExecutionPlan(
            id = sessionId,
            source = PolicyPlanSource.RULE,
            activation = activation,
            endReason = endReason,
            budgetSeconds = budgetSeconds,
            deadlineEpochMs = deadlineEpochMs,
            accessMode = accessMode,
            allowedPackages = allowedPackages,
            restAfterSeconds = restAfterSeconds,
            templateVersion = templateVersion,
            sessionId = sessionId,
        )
        session = LocalActiveSession(
            plan = plan,
            remainingSeconds = budgetSeconds,
            startedAtEpochMs = nowMs,
            usageDate = dayKey,
            wallDeadlineEpochMs = wallDeadlineFor(plan, nowMs),
        )
        setAutoReady(false)
        lockReason = LockReason.NONE
        restRemainingSeconds = 0L
        nextWindowLabel = ""
        spokenThresholds.clear()
        lastUsageReportedSeconds = 0L
        markCheckpoint(nowMs)
        checkpointRuntime(durable = true, forceSync = true)
        emitUsage(session!!, RuleUsageEventType.START, 0L, usedTodayFor(session!!, 0L))
        PolicyVoiceBus.emit(PolicyVoiceCue.START)
        publish()
    }

    /** Enter the study launcher from the locked screen. No timer, ledger, plan or PIN. */
    @Synchronized
    fun enterStudyMode(): Boolean {
        if (!initialized || studyMode) return false
        if (guardStatus != GuardStatus.ENFORCING || session != null) return false
        // The runtime accepts an empty scope, but the lock screen must not open it: the entry
        // stays visible as a no-op and the focused footer explains the missing setup.
        val configured = rules?.normalized() ?: return false
        if (!configured.isUsable) return false
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val lease = StudyLease(
            startedWallMs = nowWall,
            startedElapsedMs = nowElapsed,
            startedAsleepMs = asleepMs(),
            hardCapWallMs = nowWall + PolicyRecoveryPolicy.STUDY_HARD_CAP_MS,
            hardCapElapsedMs = nowElapsed + PolicyRecoveryPolicy.STUDY_HARD_CAP_MS,
            boot = bootEvidence(),
        )
        studyMode = true
        guardStatus = GuardStatus.STUDY_MODE
        saveGuardState(phase = GuardStatus.STUDY_MODE, override = null, study = lease)
        lockReason = LockReason.NONE
        publish()
        return true
    }

    /** Leave the study launcher; return to the same lock-screen overlay, never auto-start. */
    @Synchronized
    fun exitStudyMode() {
        if (!studyMode) return
        studyMode = false
        guardStatus = GuardStatus.ENFORCING
        saveGuardState(phase = GuardStatus.ENFORCING, override = null, study = null)
        // Leaving study with an armed grant returns to the orange confirm gate, not to the
        // generic manual-waiting copy (which would replace the pending body under the title).
        if (pendingRemoteStart != null) {
            lockReason = LockReason.NONE
            publish()
            return
        }
        evaluateNow(allowAutoStart = false)
        publish()
    }

    /**
     * Parent override: end the child's segment (if any) and unlock for parent use. Time spent
     * here is never child usage; on exit the lock screen is recomputed, never a new session.
     */
    @Synchronized
    fun beginParentOverride(durationMs: Long = 0L) {
        if (session != null) {
            // The parent is taking over, so this stop must not start the child's rest interval.
            finishSession(parentStop = true, emitCue = false, suppressRest = true)
        } else if (studyMode) {
            studyMode = false
        }
        applyParentOverrideLocked(durationMs)
    }

    /**
     * Non-mutating check for the remote parent page's temporary unlock. That unlock path must
     * only mirror a PIN typed on the lock screen, so every condition here is also enforced by
     * [tryBeginParentOverrideFromLockScreen].
     */
    @Synchronized
    fun canBeginParentOverrideFromLockScreen(): Boolean =
        initialized &&
            guardStatus == GuardStatus.ENFORCING &&
            session == null &&
            !studyMode &&
            !isSetupRequired &&
            isScreenInteractive()

    /**
     * Remote form of the lock-screen PIN. Unlike [beginParentOverride] this never ends a
     * running session or study mode, never starts a timer, and refuses a dark screen: this
     * unlock path only reproduces the lock-screen temporary unlock.
     */
    @Synchronized
    fun tryBeginParentOverrideFromLockScreen(): Boolean {
        if (!canBeginParentOverrideFromLockScreen()) return false
        applyParentOverrideLocked()
        return true
    }

    private fun applyParentOverrideLocked(durationMs: Long = 0L) {
        // A parent takeover is not the child's window ending. finishSession() marks the window
        // blocked on a parent stop; leaving that block would strand the child on "this session
        // has ended" after the unlock, even with time and quota left.
        clearAutoBlockForOpenWindow()
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val untilWall = if (durationMs > 0) nowWall + durationMs else 0L
        val lease = OverrideLease(
            leaseId = PolicyRecoveryPolicy.newLeaseId(),
            startedWallMs = nowWall,
            startedElapsedMs = nowElapsed,
            startedAsleepMs = asleepMs(),
            untilWallMs = untilWall,
            // The wall cap is meaningless while the TV clock is unset; the monotonic cap still
            // ends the unlock even when the ROM never delivered a screen-off.
            hardCapWallMs = if (nowWall > 0L) {
                nowWall + PolicyRecoveryPolicy.OVERRIDE_HARD_CAP_MS
            } else {
                0L
            },
            hardCapElapsedMs = nowElapsed + PolicyRecoveryPolicy.OVERRIDE_HARD_CAP_MS,
            boot = bootEvidence(),
            screenOffMarkerAtStartMs = guardSnapshot.screenOff.elapsedMs,
        )
        guardStatus = GuardStatus.PARENT_OVERRIDE
        parentOverrideUntil = untilWall
        saveGuardState(phase = GuardStatus.PARENT_OVERRIDE, override = lease, study = null)
        LocalPolicyStore.saveParentOverrideActive(true)
        setAutoReady(false)
        lockReason = LockReason.NONE
        publish()
    }

    @Synchronized
    fun endParentOverride() {
        if (guardStatus != GuardStatus.PARENT_OVERRIDE) return
        guardStatus = GuardStatus.ENFORCING
        parentOverrideUntil = 0L
        saveGuardState(phase = GuardStatus.ENFORCING, override = null, study = null)
        LocalPolicyStore.saveParentOverrideActive(false)
        setAutoReady(false)
        // A pending grant survives the parent takeover; returning must show its confirm gate
        // again instead of the waiting copy.
        if (pendingRemoteStart != null) {
            lockReason = LockReason.NONE
            publish()
            return
        }
        // Recompute the child's normal lock: rest if it is still running, otherwise the start
        // button when the window and quota allow a session. A parent's unlock must not close
        // the rest of the child's window.
        evaluateNow(allowAutoStart = false)
    }

    /**
     * Latest rest end for a window block written by an older build. Those blocks carried no
     * timestamp, so recover the last session end from the usage ledger and add the configured
     * rest. Null means there is no usable end; the caller treats the rest as already over.
     */
    private fun legacyBlockAvailableAt(nowMs: Long): Long? {
        val configured = rules?.normalized() ?: return null
        val today = LocalDate.now().toString()
        val lastEnd = RuleUsageStore.load().snapshot()
            .asSequence()
            .filter { it.type == RuleUsageEventType.END && it.usageDate == today }
            .mapNotNull { it.endedAtEpochMs }
            .filter { it > 0L && it <= nowMs }
            .maxOrNull()
            ?: return null
        return lastEnd + configured.restMinutes.toLong() * 60_000L
    }

    /** Drop a window block that belongs to the open window; parent actions do not close it. */
    private fun clearAutoBlockForOpenWindow() {
        if (activation != Activation.AUTO || blockedWindowKey.isEmpty()) return
        val configured = rules?.normalized() ?: return
        val key = windowKey(configured, LocalDateTime.now())
        if (key.isNotEmpty() && blockedWindowKey == key) {
            blockedWindowKey = ""
            LocalPolicyStore.saveAutoBlockKey("")
        }
    }

    /** Remote temporary unlock is the same override path as the lock-screen PIN. */
    @Synchronized
    fun setTemporaryUnlock(enabled: Boolean, durationMs: Long = 0L) {
        if (enabled) beginParentOverride(durationMs) else endParentOverride()
    }

    /** Rebuilds an interrupted rest write from the last recorded session end. */
    private fun recoverRestFromLastEnd(nowMs: Long) {
        if (lastSessionEndedAt <= 0L) return
        val configured = rules?.normalized() ?: return
        val restSeconds = LocalRestPolicy.restSeconds(
            activation = activation,
            configuredRestMinutes = configured.restMinutes,
            // This path only needs the configured duration, not a particular end reason.
            endReason = PolicyEndReason.SESSION_LIMIT,
        )
        val until = LocalRestPolicy.restUntilEpochMs(
            endedAtEpochMs = lastSessionEndedAt,
            restSeconds = restSeconds,
        )
        if (until <= nowMs) return
        breakUntil = until
        lockReason = LockReason.RESTING
        restRemainingSeconds = LocalRestPolicy.remainingSeconds(until, nowMs)
    }

    /** The normal lock state after a segment with no active rest. */
    private fun enterPostSessionWaiting() {
        if (activation == Activation.AUTO) {
            setAutoReady(true)
            lockReason = LockReason.AUTO_READY
        } else {
            lockReason = LockReason.MANUAL_WAITING
        }
    }

    @Synchronized
    private fun finishSession(
        parentStop: Boolean,
        emitCue: Boolean,
        forSwitch: Boolean = false,
        crossDay: Boolean = false,
        reasonOverride: PolicyEndReason? = null,
        endedAtEpochMs: Long? = null,
        suppressRest: Boolean = false,
    ) {
        val current = session ?: return
        val consumed = (current.totalSeconds - current.remainingSeconds).coerceAtLeast(0L)
        val endedAt = endedAtEpochMs ?: System.currentTimeMillis()
        val reportedEnd = reasonOverride ?: when {
            parentStop -> PolicyEndReason.PARENT_STOP
            forSwitch -> PolicyEndReason.MODE_SWITCH
            crossDay -> PolicyEndReason.WINDOW_END
            else -> current.plan.endReason
        }
        val endReason = current.endReason
        val endBySwitch = forSwitch || crossDay ||
            reportedEnd == PolicyEndReason.ACTIVATION_SWITCH
        // A rest belongs to the mode that earned it. computeRest decides from the same rules
        // the old post-settlement branch used, but the cursor now rides in the settlement
        // snapshot, so a process death cannot separate the end from its rest.
        val restApplies = !endBySwitch && when {
            parentStop -> !suppressRest
            reportedEnd == PolicyEndReason.USER_EXIT -> true
            reportedEnd == PolicyEndReason.SCREEN_OFF_TIMEOUT -> true
            reportedEnd == PolicyEndReason.WINDOW_END -> false
            endReason == LocalSessionEnd.SESSION_LIMIT -> true
            else -> false
        }
        val restUntil = if (restApplies) {
            restUntilFor(
                endedAtEpochMs = endedAt,
                endReason = reportedEnd,
                planRestSeconds = current.plan.restAfterSeconds,
                skipRest = suppressRest,
            )
        } else {
            0L
        }
        val usedToday = recordSettlement(
            sessionId = current.plan.sessionId ?: current.plan.id,
            usageDate = current.usageDate,
            consumedSeconds = consumed,
            endedAtEpochMs = endedAt,
            reason = reportedEnd,
            remoteRecordId = activeRemoteRecordId,
        )
        lastSessionEndedAt = if (restUntil > 0L) endedAt else 0L
        breakUntil = restUntil
        session = null
        sessionOrigin = null
        activeRemoteRecordId = 0
        sessionReportUsage = false
        spokenThresholds.clear()
        lastUsageReportedSeconds = 0L
        setAutoReady(false)
        // One durable commit carries session=null, the daily total, the settlement record and
        // the rest cursor. A crash after this point can no longer resurrect the session.
        checkpointRuntime(durable = true, forceSync = true)
        emitUsage(current, RuleUsageEventType.END, consumed, usedToday, reportedEnd, endedAt)
        if (endBySwitch) {
            lockReason = LockReason.NONE
            restRemainingSeconds = 0
            guardStatus = GuardStatus.ENFORCING
            publish()
            return
        }
        Log.i(
            TAG,
            "session end mode=$activation reason=$reportedEnd end=$endedAt consumed=$consumed",
        )
        when {
            parentStop -> {
                restRemainingSeconds = 0
                when {
                    // Both modes rest after a parent ends the segment. Only a parent takeover
                    // (which is about to unlock the TV for the parent) skips the child's break.
                    restUntil > 0L -> lockReason = LockReason.RESTING
                    activation == Activation.AUTO && suppressRest ->
                        lockReason = LockReason.NONE
                    else -> enterPostSessionWaiting()
                }
            }
            reportedEnd == PolicyEndReason.USER_EXIT -> {
                restRemainingSeconds = 0
                // Leaving early must not be a way to skip the break in either mode.
                if (restUntil > 0L) lockReason = LockReason.RESTING
                else enterPostSessionWaiting()
            }
            reportedEnd == PolicyEndReason.SCREEN_OFF_TIMEOUT -> {
                restRemainingSeconds = 0
                if (restUntil > 0L) lockReason = LockReason.RESTING
                else enterPostSessionWaiting()
            }
            reportedEnd == PolicyEndReason.WINDOW_END -> {
                restRemainingSeconds = 0
                if (activation == Activation.AUTO) {
                    lockReason = LockReason.OUTSIDE_WINDOW
                } else {
                    lockReason = LockReason.MANUAL_WAITING
                }
            }
            endReason == LocalSessionEnd.SESSION_LIMIT -> {
                restRemainingSeconds = 0
                // The plan's snapshot covers a natural end; both modes take the same rest.
                if (restUntil > 0L) lockReason = LockReason.RESTING
                else enterPostSessionWaiting()
            }
            else -> {
                restRemainingSeconds = 0
                if (activation == Activation.AUTO) {
                    if (endReason == LocalSessionEnd.DAILY_LIMIT) {
                        lockReason = LockReason.TIME_UP
                    } else if (endReason == LocalSessionEnd.WINDOW_END) {
                        lockReason = LockReason.OUTSIDE_WINDOW
                    } else {
                        setAutoReady(true)
                        lockReason = LockReason.AUTO_READY
                    }
                } else {
                    lockReason = LockReason.MANUAL_WAITING
                }
            }
        }
        if (restUntil > 0L) {
            restRemainingSeconds =
                LocalRestPolicy.remainingSeconds(restUntil, System.currentTimeMillis())
        }
        guardStatus = GuardStatus.ENFORCING
        publish()
        if (emitCue && reasonOverride == null) {
            PolicyVoiceBus.emit(
                if (parentStop) PolicyVoiceCue.PARENT_STOP else PolicyVoiceCue.TIME_UP,
            )
        }
    }

    private fun usedTodayFor(current: LocalActiveSession, sessionUsedSeconds: Long): Long =
        if (current.usageDate == dayKey) {
            (usedSeconds + sessionUsedSeconds).coerceAtLeast(0L)
        } else {
            sessionUsedSeconds.coerceAtLeast(0L)
        }

    private fun emitUsage(
        current: LocalActiveSession,
        type: RuleUsageEventType,
        sessionUsedSeconds: Long,
        usedSecondsToday: Long,
        endReason: PolicyEndReason? = null,
        endedAtEpochMs: Long? = null,
    ) {
        val sink = usageSink ?: return
        val used = sessionUsedSeconds.coerceAtLeast(0L)
        val today = usedSecondsToday.coerceAtLeast(0L)
        val event = runCatching {
            when (type) {
                RuleUsageEventType.START -> RuleUsageEventFactory.start(
                    plan = current.plan,
                    usageDate = current.usageDate,
                    startedAtEpochMs = current.startedAtEpochMs,
                    usedSecondsToday = today,
                )
                RuleUsageEventType.PROGRESS -> RuleUsageEventFactory.progress(
                    plan = current.plan,
                    usageDate = current.usageDate,
                    startedAtEpochMs = current.startedAtEpochMs,
                    sessionUsedSeconds = used,
                    usedSecondsToday = today,
                )
                RuleUsageEventType.END -> RuleUsageEventFactory.end(
                    plan = current.plan,
                    usageDate = current.usageDate,
                    startedAtEpochMs = current.startedAtEpochMs,
                    endedAtEpochMs = requireNotNull(endedAtEpochMs) { "END needs a time" },
                    sessionUsedSeconds = used,
                    usedSecondsToday = today,
                    endReason = requireNotNull(endReason) { "END needs a reason" },
                )
            }
        }.getOrNull() ?: return
        runCatching { sink.onRuleUsage(event) }
            .onFailure { Log.w(TAG, "usage sink failed", it) }
    }

    private fun maybeVoiceThreshold(remaining: Long) {
        // 30/20/10 matches the floating ball's 10-second display and voice cadence.
        // A separate 5-second cue would break that rhythm.
        val cue = when (remaining) {
            60L -> PolicyVoiceCue.ONE_MINUTE
            30L -> PolicyVoiceCue.THIRTY_SECONDS
            20L -> PolicyVoiceCue.TWENTY_SECONDS
            10L -> PolicyVoiceCue.TEN_SECONDS
            else -> return
        }
        if (spokenThresholds.add(remaining)) PolicyVoiceBus.emit(cue)
    }

    private fun SessionOrigin.toSessionOrigin(remoteRecordId: Int): PolicySessionOrigin =
        when (this) {
            SessionOrigin.LOCAL -> PolicySessionOrigin.LOCAL
            SessionOrigin.REMOTE -> if (remoteRecordId > 0) {
                PolicySessionOrigin.GRANT
            } else {
                PolicySessionOrigin.REMOTE
            }
        }

    private fun PendingRemoteStart.toPendingSnapshot(): PendingGrantSnapshot =
        PendingGrantSnapshot(
            recordId = recordId,
            source = when (source) {
                PendingSource.MQTT -> PendingGrantSource.MQTT
                PendingSource.GRANT -> PendingGrantSource.GRANT
            },
            durationSeconds = durationSeconds,
            expectedEpoch = expectedEpoch,
            requestedScope = requestedScope,
            accessMode = accessMode,
            allowedPackages = allowedPackages,
            restAfterSeconds = restAfterSeconds,
            armedAtEpochMs = System.currentTimeMillis(),
            deadlineEpochMs = deadlineEpochMs,
            validUntilLabel = validUntilLabel,
            userName = userName,
            userIconUrl = userIconUrl,
        )

    /** The single durable runtime document: session, ledger and armed grant together. */
    private fun runtimeSnapshot(): RuntimeSnapshot = RuntimeSnapshot(
        session = sessionSnapshot(),
        ledger = RuntimeLedger(
            days = LinkedHashMap(dayUsage),
            breakUntilEpochMs = breakUntil,
            lastEndedAtEpochMs = lastSessionEndedAt,
            settled = LinkedHashMap(settledSessions),
            pendingGrant = pendingRemoteStart?.toPendingSnapshot(),
        ),
    )

    private fun sessionSnapshot(): SessionSnapshot? {
        val current = session ?: return null
        return SessionSnapshot(
            sessionId = current.plan.sessionId ?: current.plan.id,
            phase = if (current.isPaused) SessionPhase.PAUSED else SessionPhase.ACTIVE,
            totalSeconds = current.totalSeconds,
            remainingSeconds = current.remainingSeconds,
            endReason = current.endReason,
            usageDate = current.usageDate,
            startedAtEpochMs = current.startedAtEpochMs,
            planDeadlineEpochMs = current.plan.deadlineEpochMs ?: 0L,
            wallDeadlineEpochMs = current.wallDeadlineEpochMs,
            pausedAtEpochMs = current.pausedAtEpochMs,
            checkpointWallMs = checkpointWallMs,
            checkpointElapsedMs = checkpointElapsedMs,
            checkpointAsleepMs = checkpointAsleepMs,
            boot = sessionBoot,
            activation = current.plan.activation,
            accessMode = current.plan.accessMode,
            allowedPackages = current.plan.allowedPackages,
            origin = sessionOrigin,
            remoteRecordId = activeRemoteRecordId,
            reportUsage = sessionReportUsage,
            templateVersion = current.plan.templateVersion,
            restAfterSeconds = current.plan.restAfterSeconds,
        )
    }

    /**
     * Commits the whole runtime state as one MMKV value. High-frequency checkpoints use an
     * ordinary encode; transitions settle durably and are mirrored to the legacy keys so an
     * older APK can still roll back without losing the session.
     */
    private fun checkpointRuntime(durable: Boolean = false, forceSync: Boolean = false) {
        if (!TvStore.isReady) return
        val written = PolicyStateRepository.saveRuntime(
            snapshot = runtimeSnapshot(),
            durable = durable,
            forceSync = forceSync,
        )
        if (!written) {
            Log.w(TAG, "runtime checkpoint failed")
            return
        }
        if (durable) {
            checkpointFlushCounter = 0
            mirrorRuntimeLegacy()
        } else {
            checkpointFlushCounter += 1
            if (checkpointFlushCounter >= RUNTIME_SYNC_EVERY_CHECKPOINTS) {
                checkpointFlushCounter = 0
                TvStore.sync()
            }
        }
    }

    /** Best-effort write-through of the same state for an older APK after a downgrade. */
    private fun mirrorRuntimeLegacy() {
        if (!TvStore.isReady) return
        val current = session
        if (current == null) {
            LocalPolicyStore.clearSession()
        } else {
            LocalPolicyStore.saveSession(
                SavedLocalSession(
                    totalSeconds = current.totalSeconds,
                    remainingSeconds = current.remainingSeconds,
                    endReason = current.endReason,
                    sessionId = current.plan.sessionId.orEmpty(),
                    templateVersion = current.plan.templateVersion,
                    restAfterSeconds = current.plan.restAfterSeconds,
                    startedAtEpochMs = current.startedAtEpochMs,
                    usageDate = current.usageDate,
                    deadlineEpochMs = current.plan.deadlineEpochMs ?: 0L,
                    wallDeadlineEpochMs = current.wallDeadlineEpochMs,
                    pausedAtEpochMs = current.pausedAtEpochMs,
                    reportUsage = sessionReportUsage,
                    startedLocally = sessionOrigin == PolicySessionOrigin.LOCAL,
                    activation = current.plan.activation,
                    accessMode = current.plan.accessMode,
                    allowedPackages = current.plan.allowedPackages,
                ),
            )
        }
        LocalPolicyStore.saveUsed(dayKey, usedSeconds)
        LocalPolicyStore.saveBreakUntil(breakUntil)
        LocalPolicyStore.saveLastSessionEndedAt(lastSessionEndedAt)
    }

    /** The guard partition: phase, lease and the last policy-owned screen-off evidence. */
    private fun saveGuardState(
        phase: GuardStatus,
        override: OverrideLease?,
        study: StudyLease?,
    ) {
        guardSnapshot = GuardSnapshot(
            phase = phase,
            override = override,
            study = study,
            screenOff = guardSnapshot.screenOff,
        )
        PolicyStateRepository.saveGuard(guardSnapshot)
    }

    private fun publish() {
        val configured = rules?.normalized()
        val current = session
        val pending = pendingRemoteStart
        val now = System.currentTimeMillis()
        val nowDateTime = LocalDateTime.now()
        val breakRemaining = LocalRestPolicy.remainingSeconds(breakUntil, now)
        val todayWindows = configured?.windowsFor(nowDateTime.dayOfWeek.value).orEmpty()
        val windowRemaining = configured
            ?.let { currentWindow(it, nowDateTime) }
            ?.let { (it.endSecond - secondOfDay(nowDateTime)).toLong().coerceAtLeast(0L) }
            ?: 0L
        val nextWindowIn = configured?.let { secondsUntilNextWindow(it, nowDateTime) } ?: 0L
        val effectiveRemaining = current?.let {
            SessionTiming.effectiveRemainingSeconds(
                budgetRemainingSeconds = it.remainingSeconds,
                wallDeadlineEpochMs = it.wallDeadlineEpochMs,
                nowMs = now,
            )
        } ?: pending?.durationSeconds ?: 0L
        _state.value = PolicyUiState(
            loaded = initialized,
            mode = PolicyMode.LOCAL,
            sessionState = when {
                current != null -> SessionState.ACTIVE
                pending != null -> SessionState.PENDING
                else -> SessionState.IDLE
            },
            guardState = guardStatus,
            activation = activation,
            source = source,
            policyEpoch = policyEpoch,
            studyModeAvailable = configured?.hasStudyMode == true,
            studyModeActive = studyMode,
            studyPackages = configured?.effectiveStudyPackages.orEmpty(),
            autoReady = autoReady && pending == null,
            noTimedApps = configured != null && configured.isUsable && !configured.hasTimedApps(),
            // Study mode and entertainment both publish a concrete APPS scope. A SCREEN scope
            // would let a study app launch during entertainment (and anything launch in study).
            accessMode = if (configured != null) AccessMode.APPS else AccessMode.SCREEN,
            allowedPackages = when {
                studyMode -> configured?.effectiveStudyPackages.orEmpty()
                pending != null -> pending.allowedPackages
                // A hosted manual segment is one concrete grant. The base rule may allow more
                // apps, so falling through would widen the grant after the child confirms.
                // Automatic and unbound local sessions keep the live base scope below.
                current != null &&
                    source == PolicySource.HOSTED &&
                    current.plan.activation == PolicyActivation.MANUAL &&
                    current.plan.accessMode == AccessMode.APPS &&
                    current.plan.allowedPackages.isNotEmpty() -> current.plan.allowedPackages
                else -> configured?.timedPackages().orEmpty()
            },
            totalSeconds = current?.totalSeconds ?: pending?.durationSeconds ?: 0L,
            remainingSeconds = current?.remainingSeconds ?: pending?.durationSeconds ?: 0L,
            effectiveRemainingSeconds = effectiveRemaining,
            userName = pending?.userName.orEmpty(),
            userIconUrl = pending?.userIconUrl.orEmpty(),
            pinConfigured = LocalPolicyStore.pinConfigured,
            bound = false,
            deviceNo = "",
            bindingDeferred = false,
            localRulesConfigured = configured?.isUsable == true,
            setupRequired = isSetupRequired,
            dailyUsedSeconds = usedSeconds,
            dailyLimitSeconds = (configured?.autoDailyLimitMinutes ?: 0).toLong() * 60L,
            sessionLimitSeconds = (configured?.sessionLimitMinutes ?: 0).toLong() * 60L,
            configuredRestMinutes = configured?.restMinutes ?: 0,
            configuredBufferMinutes = configured?.sessionScreenOffBufferMinutes ?: 0,
            nextWindowLabel = nextWindowLabel,
            todayWindowsLabel = windowsLabel(todayWindows),
            windowRemainingSeconds = windowRemaining,
            nextWindowInSeconds = nextWindowIn,
            restRemainingSeconds = if (lockReason == LockReason.RESTING) breakRemaining else 0L,
            parentUnlockRemainingSeconds =
                if (guardStatus == GuardStatus.PARENT_OVERRIDE && parentOverrideUntil > 0) {
                    ((parentOverrideUntil - now) / 1000L).coerceAtLeast(0L)
                } else {
                    0L
                },
            validUntil = pending?.validUntilLabel.orEmpty(),
            lockReason = lockReason,
        )
        _revision.value = _revision.value + 1L
    }

    private fun labelFor(dayOffset: Int, minuteOfDay: Int): String {
        if (dayOffset < 0 || minuteOfDay < 0) return ""
        val ctx = context ?: return ""
        val day = when (dayOffset) {
            0 -> ctx.getString(R.string.policy_today)
            1 -> ctx.getString(R.string.policy_tomorrow)
            else -> LocalDate.now().plusDays(dayOffset.toLong()).format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()),
            )
        }
        val time = String.format(Locale.getDefault(), "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)
        return ctx.getString(R.string.policy_next_window, day, time)
    }

    private fun nextWindowLabelFor(configured: LocalRules, now: LocalDateTime): String {
        val next = com.planyourtv.policy.LocalScheduleEngine.nextWindow(
            dayOfWeek = now.dayOfWeek.value,
            secondOfDay = secondOfDay(now),
            rules = configured,
        ) ?: return ""
        return labelFor(next.first, next.second)
    }

    /** Whole minutes are enough here; the lock screen renders the value as "18:00–20:00". */
    private fun windowsLabel(windows: List<LocalTimeWindow>): String =
        windows.sortedBy { it.startMinute }.joinToString(", ") { window ->
            String.format(
                Locale.getDefault(),
                "%02d:%02d–%02d:%02d",
                window.startMinute / 60,
                window.startMinute % 60,
                window.endMinute / 60,
                window.endMinute % 60,
            )
        }

    private fun secondsUntilNextWindow(configured: LocalRules, now: LocalDateTime): Long {
        val next = com.planyourtv.policy.LocalScheduleEngine.nextWindow(
            dayOfWeek = now.dayOfWeek.value,
            secondOfDay = secondOfDay(now),
            rules = configured,
        ) ?: return 0L
        val target = now.toLocalDate()
            .plusDays(next.first.toLong())
            .atStartOfDay()
            .plusMinutes(next.second.toLong())
        return Duration.between(now, target).seconds.coerceAtLeast(1L)
    }

    /** Snapshot the deadline once; later rule edits only affect the next plan. */
    private fun wallDeadlineFor(plan: PolicyExecutionPlan, startedAtEpochMs: Long): Long =
        SessionTiming.wallDeadlineEpochMs(
            startedAtEpochMs = startedAtEpochMs,
            budgetSeconds = plan.budgetSeconds,
            bufferMinutes = rules?.sessionScreenOffBufferMinutes
                ?: LocalRules.DEFAULT_SESSION_BUFFER_MINUTES,
            planDeadlineEpochMs = plan.deadlineEpochMs,
            nextMidnightEpochMs = nextMidnightEpochMs(LocalDateTime.now()),
        )

    private fun wallEndReason(current: LocalActiveSession): PolicyEndReason {
        val planDeadline = current.plan.deadlineEpochMs
        return if (planDeadline != null && current.wallDeadlineEpochMs >= planDeadline) {
            PolicyEndReason.WINDOW_END
        } else {
            PolicyEndReason.SCREEN_OFF_TIMEOUT
        }
    }

    private fun nextMidnightEpochMs(now: LocalDateTime): Long =
        now.toLocalDate().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun isScreenInteractive(): Boolean =
        (context?.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true

    private fun currentWindow(configured: LocalRules, now: LocalDateTime): LocalTimeWindow? =
        configured.windowsFor(now.dayOfWeek.value)
            .firstOrNull { it.contains(secondOfDay(now)) }

    private fun windowKey(configured: LocalRules, now: LocalDateTime): String {
        val window = currentWindow(configured, now) ?: return ""
        return "${now.toLocalDate()}:${window.startMinute}"
    }

    private fun secondOfDay(now: LocalDateTime): Int =
        now.hour * 3600 + now.minute * 60 + now.second

    private fun rejectForLock(reason: LockReason): RejectReason = when (reason) {
        LockReason.RESTING -> RejectReason.RESTING
        LockReason.OUTSIDE_WINDOW, LockReason.TIME_UP, LockReason.AUTO_BLOCKED ->
            RejectReason.OUTSIDE_WINDOW
        LockReason.NO_TIMED_APPS -> RejectReason.NO_TIMED_APPS
        else -> RejectReason.INVALID_COMMAND
    }

    /** Test-only: drop all in-memory engine state; durable storage is untouched. */
    @Synchronized
    private fun resetInMemoryForTest() {
        active = false
        ticker?.cancel()
        ticker = null
        initialized = false
        rules = null
        ruleSets = null
        session = null
        activeRemoteRecordId = 0
        sessionOrigin = null
        sessionReportUsage = false
        pendingRemoteStart = null
        settledGrantRecords.clear()
        dayKey = ""
        usedSeconds = 0L
        dayUsage.clear()
        breakUntil = 0L
        lastSessionEndedAt = 0L
        settledSessions.clear()
        parentOverrideUntil = 0L
        guardStatus = GuardStatus.ENFORCING
        guardSnapshot = GuardSnapshot()
        recoveryComplete = false
        activation = Activation.MANUAL
        source = PolicySource.LOCAL
        policyEpoch = 0L
        studyMode = false
        autoReady = false
        installedPackages = emptySet()
        blockedWindowKey = ""
        lockReason = LockReason.NONE
        nextWindowLabel = ""
        restRemainingSeconds = 0L
        lastSavedTick = 0L
        checkpointWallMs = 0L
        checkpointElapsedMs = 0L
        checkpointAsleepMs = 0L
        checkpointFlushCounter = 0
        sessionBoot = BootEvidence()
        screenOffEvidenceForEpisode = false
        templateVersion = 0L
        usageSink = null
        lastUsageReportedSeconds = 0L
        spokenThresholds.clear()
    }

    /** Test-only: return the singleton to a clean, uninitialized state. */
    @Synchronized
    internal fun debugResetForTest() {
        resetInMemoryForTest()
        LocalPolicyStore.resetLocalSetup()
        LocalPolicyStore.clearSettledGrantRecordIds()
        PolicyStateRepository.clearAll()
        val today = LocalDate.now().toString()
        LocalPolicyStore.saveUsed(today, 0L)
        LocalPolicyStore.saveBreakUntil(0L)
        LocalPolicyStore.saveLastSessionEndedAt(0L)
        publish()
    }

    /**
     * Test-only: simulate the OS killing the process. The singleton drops its memory exactly like
     * a cold start, but the MMKV/SharedPreferences files stay, so the next initialize() must
     * rebuild the state from disk.
     */
    @Synchronized
    internal fun debugSimulateProcessRestart() {
        resetInMemoryForTest()
    }

    /** Debug-only: start a local session without touching the parent's rules. */
    @Synchronized
    internal fun debugStartSession(seconds: Long) {
        if (!initialized) return
        guardStatus = GuardStatus.ENFORCING
        parentOverrideUntil = 0L
        studyMode = false
        saveGuardState(phase = GuardStatus.ENFORCING, override = null, study = null)
        startPlan(
            activation = if (activation == Activation.AUTO) {
                PolicyActivation.AUTO
            } else {
                PolicyActivation.MANUAL
            },
            budgetSeconds = seconds.coerceAtLeast(1L),
            endReason = PolicyEndReason.SESSION_LIMIT,
            restAfterSeconds = 0L,
            accessMode = AccessMode.APPS,
            allowedPackages = rules?.timedPackages().orEmpty(),
            deadlineEpochMs = null,
            nowMs = System.currentTimeMillis(),
        )
    }

    /** Debug-only: clear the synthetic session without adding usage to the day ledger. */
    @Synchronized
    internal fun debugClearSession() {
        session = null
        sessionOrigin = null
        activeRemoteRecordId = 0
        sessionReportUsage = false
        pendingRemoteStart = null
        guardStatus = GuardStatus.ENFORCING
        studyMode = false
        lockReason = LockReason.NONE
        checkpointRuntime(durable = true, forceSync = true)
        publish()
    }

    /** Debug-only: move an active rest deadline close to now to test the expiry transition. */
    @Synchronized
    internal fun debugExpireRest() {
        if (breakUntil <= System.currentTimeMillis()) return
        breakUntil = System.currentTimeMillis() + 2_000L
        checkpointRuntime(durable = true)
        publish()
    }

    /** Debug-only: restore a test snapshot's day usage after synthetic sessions. */
    @Synchronized
    internal fun debugRestoreUsage(seconds: Long) {
        dayKey = LocalDate.now().toString()
        usedSeconds = seconds.coerceAtLeast(0L)
        dayUsage.clear()
        dayUsage[dayKey] = usedSeconds
        settledSessions.clear()
        breakUntil = 0L
        lastSessionEndedAt = 0L
        session = null
        sessionOrigin = null
        activeRemoteRecordId = 0
        sessionReportUsage = false
        guardStatus = GuardStatus.ENFORCING
        parentOverrideUntil = 0L
        studyMode = false
        saveGuardState(phase = GuardStatus.ENFORCING, override = null, study = null)
        LocalPolicyStore.saveParentOverrideActive(false)
        pendingRemoteStart = null
        setAutoReady(false)
        checkpointRuntime(durable = true, forceSync = true)
        publish()
        evaluateNow(allowAutoStart = activation == Activation.AUTO)
    }

    /**
     * Effective entertainment scope. All-apps expands to the installed launchers minus the study
     * list, so study apps stay blocked during entertainment; explicit empty stays empty.
     */
    private fun LocalRules.timedPackages(): Set<String> =
        if (allowsAllEntertainmentApps) {
            installedPackages - studyPackages
        } else {
            allowedPackages - studyPackages
        }

    /** True when at least one entertainment app can start; study-only does not count. */
    private fun LocalRules.hasTimedApps(): Boolean =
        if (allowsAllEntertainmentApps) {
            installedPackages.any { it !in studyPackages }
        } else {
            (allowedPackages - studyPackages).isNotEmpty()
        }

    /**
     * The launchable scan excludes stock homes and our own launcher. It is refreshed at start and
     * whenever the parent saves a configuration, not on every tick.
     */
    private fun refreshInstalledPackages() {
        val app = context ?: return
        installedPackages = runCatching {
            PolicyInstalledApps.scan(app).mapTo(LinkedHashSet()) { it.packageName }
        }.getOrDefault(emptySet())
    }
}
