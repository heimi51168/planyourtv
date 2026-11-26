package app.orchard.tv.policy

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import app.orchard.tv.R
import app.orchard.tv.guard.HomeGuardService
import app.orchard.tv.quiz.QuizLaunchTarget
import app.orchard.tv.quiz.QuizRuntime
import app.orchard.tv.system.BootOpen
import com.planyourtv.policy.AccessMode
import com.planyourtv.policy.Activation
import com.planyourtv.policy.BootEvidence
import com.planyourtv.policy.FruitAccountSnapshot
import com.planyourtv.policy.FruitAccountRules
import com.planyourtv.policy.FruitFacts
import com.planyourtv.policy.FruitReserveResult
import com.planyourtv.policy.GuardSnapshot
import com.planyourtv.policy.GuardStatus
import com.planyourtv.policy.HostedFruitFacts
import com.planyourtv.policy.LocalFruitAccountStore
import com.planyourtv.policy.LocalPolicyStore
import com.planyourtv.policy.LocalRecovery
import com.planyourtv.policy.LocalRestPolicy
import com.planyourtv.policy.LocalRuleSets
import com.planyourtv.policy.LocalRuleSetsLoad
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalSessionEnd
import com.planyourtv.policy.ManualTimerScope
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.LockReason
import com.planyourtv.policy.MemberStatus
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
import com.planyourtv.policy.PolicyRuntime
import com.planyourtv.policy.PolicySessionOrigin
import com.planyourtv.policy.PolicySource
import com.planyourtv.policy.PolicyStateRepository
import com.planyourtv.policy.PolicyUiState
import com.planyourtv.policy.PolicyVoiceBus
import com.planyourtv.policy.PolicyVoiceCue
import com.planyourtv.policy.RecoveryNow
import com.planyourtv.policy.RejectReason
import com.planyourtv.policy.RuleUsageEventFactory
import com.planyourtv.policy.RouteUsage
import com.planyourtv.policy.RuleUsageEventType
import com.planyourtv.policy.RuleUsageSink
import com.planyourtv.policy.RuleUsageStore
import com.planyourtv.policy.RuntimeLedger
import com.planyourtv.policy.RuntimeSnapshot
import com.planyourtv.policy.SavedLocalSession
import com.planyourtv.policy.SavedStudyRound
import com.planyourtv.policy.ScreenOffEvidence
import com.planyourtv.policy.SessionPauseReason
import com.planyourtv.policy.SessionPhase
import com.planyourtv.policy.SessionSnapshot
import com.planyourtv.policy.SessionStartResult
import com.planyourtv.policy.SessionState
import com.planyourtv.policy.SessionTiming
import com.planyourtv.policy.SettledSessionSnapshot
import com.planyourtv.policy.StudyLease
import com.planyourtv.policy.StudyRoundUi
import com.planyourtv.policy.TvStore
import com.planyourtv.policy.UnlockRoute
import com.planyourtv.policy.UnlockRouteDecision
import com.planyourtv.policy.UnlockRouteFacts
import com.planyourtv.policy.UnlockRoutePlan
import com.planyourtv.policy.UnlockRoutePlanner
import com.planyourtv.policy.UnlockRouteReason
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
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/**
 * The TV's single local executor. Manual entertainment sessions own this timer, ledger, rest and
 * app scope; study mode only replaces the visible app scope and has no timer,
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
    private const val LEDGER_RETENTION_DAYS = 31
    /**
     * A remote fruit start is refused when the last successful foreground query is older than
     * this. The guard polls once a second; 3 s tolerates one slow binder call (cold boot can
     * take seconds) without letting a stale snapshot authorize a start.
     */
    private const val FOREGROUND_STALE_MS = 3_000L
    private val USAGE_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var context: Context? = null
    private var ticker: Job? = null
    private var initialized = false
    private var active = false
    private var rules: LocalRules? = null
    /** The stored manual time-rule set; [rules] is its materialized snapshot. */
    private var ruleSets: LocalRuleSets? = null
    private var session: LocalActiveSession? = null
    /**
     * The exchange record that started the running session. Only a record-backed remote session
     * is stopped when the REST snapshot says the record is gone; a LOCAL start and the
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

    /**
     * A focused study-to-earn round. It is a timer only: no entertainment session, ledger entry or
     * plan exists while it runs. The round is stored under its own key so the runtime snapshot
     * codec (and its strict version contract) does not change.
     */
    private var studyRound: LocalStudyRound? = null
    private var studyRoundSavedTick = 0L

    /** Grant records already confirmed, stopped or canceled here; a poll must not re-arm them. */
    private val settledGrantRecords = LinkedHashSet<Int>()
    private var dayKey = ""
    private var usedSeconds = 0L
    /**
     * V44 successful-session counts for the three child unlock routes. The date is part of the
     * value; anything left over from another local day is treated as zero.
     */
    private var routeUsage: RouteUsage? = null
    /** Recent daily totals, including a session settled into the date it actually ran. */
    private val dayUsage = LinkedHashMap<String, Long>()
    /** Focused study seconds per local date; never charged to [dayUsage]. */
    private val studyDays = LinkedHashMap<String, Long>()
    /** Last foreground package reported by the guard; memory-only, used to resume on return. */
    private var foregroundPackage: String? = null
    /**
     * Last time the guard got a fresh answer from the foreground source. The poll only calls
     * [onForegroundChanged] when the front app changes, so a child who stays in one app would
     * otherwise look stale; [noteForegroundObserved] is called after every successful query.
     */
    @Volatile
    private var foregroundObservedAtMs = 0L
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
    /**
     * True once the hosted (cloud) execution partition has been read into memory. Kept separate
     * from [source] because setSource is also called while no partition is active.
     */
    private var hostedExecutionLoaded = false
    private val hostedStorage: Boolean get() = source == PolicySource.HOSTED
    /**
     * Cloud member that owns the hosted partition currently in memory (2026-11-21). The hosted
     * snapshot is keyed by this id, so switching the TV desktop user restores that child's own
     * ledger and rest cursor instead of leaking the previous child's state. 0 = the pre-scope
     * device-global partition, used only before the roster is known or when migrating it.
     */
    private var hostedSubjectId = 0L
    private val hostedScope: String? get() = LocalPolicyStore.hostedScopeOf(hostedSubjectId)
    /**
     * The cloud fruit projection for the current hosted member. It is a HOSTED cache only and
     * is refreshed by the coordinator from the orchard device context; the LOCAL ledger is
     * never read or written for it. Cached per scope so a member switch cannot leak a balance.
     */
    private var hostedFruitFacts: HostedFruitFacts? = null
    private var hostedFruitFactsScope: String? = null
    private var policyEpoch = 0L
    private var studyMode = false
    /** Launchable packages, cached so an all-apps policy can publish a concrete APPS scope. */
    private var installedPackages: Set<String> = emptySet()
    private var lockReason = LockReason.NONE
    private var nextWindowLabel = ""
    private var restRemainingSeconds = 0L
    private var lastSavedTick = 0L
    private var templateVersion = 0L
    private var usageSink: RuleUsageSink? = null
    private var lastUsageReportedSeconds = 0L
    private val spokenThresholds = mutableSetOf<Long>()
    /**
     * A Web credit or rate edit must refresh the child lock screen without waiting for a tick.
     * Only the active member's account is visible there; edits to another child's ledger stay
     * silent until that child becomes active.
     */
    private val fruitAccountListener: (String, FruitAccountSnapshot) -> Unit = { memberId, _ ->
        if (initialized) {
            scope.launch {
                synchronized(this@LocalPolicyRuntime) {
                    if (memberId == FamilyHub.state.value.activeUserId) publish()
                }
            }
        }
    }

    private val _state = MutableStateFlow(PolicyUiState(mode = PolicyMode.LOCAL, loaded = false))
    val state: StateFlow<PolicyUiState> = _state.asStateFlow()

    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private data class LocalStudyRound(
        val studyPackage: String,
        val targetPackage: String?,
        /** This round's own allowance; the study window and quota are checked on every tick. */
        val totalSeconds: Long,
        val remainingSeconds: Long,
        /** Accrued focus time, used once for the X-minute reward attempt. */
        val studiedSeconds: Long = 0L,
        val rewardSeconds: Long,
        /** Only a study app in studyEarnPackages may trade this round for entertainment. */
        val rewardEnabled: Boolean = false,
        /** The one attempt happened; an unavailable reward is never retried this round. */
        val rewardAttempted: Boolean = false,
        val rewardGranted: Boolean = false,
        /** The date this round's study seconds are charged to. */
        val usageDate: String = "",
        val startedAtEpochMs: Long,
        val boot: BootEvidence = BootEvidence(),
        /**
         * Non-zero while the countdown is frozen. It is memory-only: after a process restart the
         * round always restores paused, so a persisted flag could only resurrect a running round
         * the child never explicitly resumed.
         */
        val pausedAtEpochMs: Long = 0L,
    ) {
        val isPaused: Boolean get() = pausedAtEpochMs > 0L
    }

    private data class LocalActiveSession(
        val plan: PolicyExecutionPlan,
        val remainingSeconds: Long,
        val startedAtEpochMs: Long,
        val usageDate: String,
        /** Fixed at the first start: budget + screen-off grace, capped by the day. */
        val wallDeadlineEpochMs: Long,
        /** Non-zero while the screen is off or an unlimited app is in front. */
        val pausedAtEpochMs: Long = 0L,
        val pauseReason: SessionPauseReason? = null,
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
        pauseReason: SessionPauseReason? = null,
    ): LocalActiveSession {
        val consumed = (saved.totalSeconds - remainingSeconds).coerceAtLeast(0L)
        val startedAt = saved.startedAtEpochMs.takeIf { it > 0 }
            ?: (nowMs - consumed * 1_000L).coerceAtLeast(1L)
        val usageDate = saved.usageDate.takeIf { USAGE_DATE.matches(it) } ?: dayKey
        val endReason = saved.endReason.toPolicyEndReason()
        val today = LocalDate.now().dayOfWeek.value
        val restAfter = if (!saved.outsidePlan && endReason == PolicyEndReason.SESSION_LIMIT) {
            saved.restAfterSeconds.takeIf { it > 0 }
                ?: ((rules?.restFor(today) ?: 0) * 60L)
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
            outsidePlan = saved.outsidePlan,
        )
        // Sessions written before V11 have no fixed wall deadline. Reconstruct one from the
        // same start snapshot; the buffer is a grace, never an extension of usable time.
        val wallDeadline = saved.wallDeadlineEpochMs.takeIf { it > 0L }
            ?: SessionTiming.wallDeadlineEpochMs(
                startedAtEpochMs = startedAt,
                budgetSeconds = saved.totalSeconds,
                bufferMinutes = rules?.screenOffBufferFor(today)
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
            pauseReason = pauseReason.takeIf { paused },
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
            configuredRestMinutes = configured.restFor(LocalDate.now().dayOfWeek.value),
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
        if (saved.outsidePlan) {
            // The parent timer never touches the ledger or rest, including when it ended while
            // the process was dead. There is nothing to enqueue either.
            Log.i(TAG, "recovered outside-plan session dropped id=${saved.sessionId} consumed=$consumed")
            return
        }
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
        // Runs the one-way legacy migration before any policy state is read. When it cannot
        // complete, the runtime keeps the legacy single-user state instead of bricking the TV.
        FamilyHub.initialize(ctx)
        // The legacy mirror is the migration source for the default member only. A later child
        // without a subject document must boot unconfigured instead of inheriting the first
        // child's rules, activation or usage.
        val family = FamilyHub.state.value
        val isDefaultSubject = !family.ready || family.activeUserId == family.defaultUserId
        val subjectConfig = FamilyHub.activeConfig()
        // Execution is manual-only; a stored AUTO cursor is never restored.
        activation = Activation.MANUAL
        loadRuleSets(subjectConfig, allowLegacyFallback = isDefaultSubject)
        LocalFruitAccountStore.addListener(fruitAccountListener)
        restoreRuntimePartition(allowLegacyFallback = isDefaultSubject)
        recoveryComplete = true
        initialized = true
        checkpointRuntime(durable = true, forceSync = true)
        publish()
    }

    /**
     * Reads one execution partition into memory. LOCAL is the TV's own child mode (subject cache
     * plus legacy mirror); HOSTED is the cloud rule guard's private partition. They share no key:
     * binding, unbinding or switching modes must never convert one into the other.
     */
    private fun restoreRuntimePartition(allowLegacyFallback: Boolean) {
        val hosted = hostedStorage
        policyEpoch = LocalPolicyStore.policyEpoch(hosted = hosted, hostedScope = hostedScope)
        settledGrantRecords.clear()
        settledGrantRecords.addAll(
            LocalPolicyStore.settledGrantRecordIds(hosted = hosted, hostedScope = hostedScope),
        )

        val nowWall = System.currentTimeMillis()
        val savedRuntime = if (hosted) {
            PolicyStateRepository.loadRuntimeOrMigrate(
                hosted = true,
                hostedScope = hostedScope,
            ).snapshot
        } else {
            // The subject cache is authoritative after migration; the repository is the fallback
            // only for the default member (including a family that could not be created or
            // repaired), and still performs the pre-V37 key migration.
            val legacyRuntime = if (allowLegacyFallback) {
                PolicyStateRepository.loadRuntimeOrMigrate().snapshot
            } else {
                null
            }
            freshestRuntime(FamilyHub.activeRuntime()?.runtime, legacyRuntime)
                ?: RuntimeSnapshot()
        }
        dayKey = LocalDate.now().toString()
        loadLedgers(savedRuntime.ledger)
        routeUsage = savedRuntime.ledger.routeUsage?.normalizedFor(dayKey)
        breakUntil = savedRuntime.ledger.breakUntilEpochMs.takeIf { it > nowWall } ?: 0L
        lastSessionEndedAt = savedRuntime.ledger.lastEndedAtEpochMs
        settledSessions.clear()
        settledSessions.putAll(savedRuntime.ledger.settled)
        while (settledSessions.size > SETTLED_SESSION_LIMIT) {
            settledSessions.remove(settledSessions.keys.first())
        }
        restorePendingGrant(savedRuntime.ledger.pendingGrant, nowWall)

        guardSnapshot = PolicyStateRepository.loadGuard(
            hosted = hosted,
            hostedScope = hostedScope,
        ).snapshot
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
            if (savedSession != null && rules?.isUsable != true && !hosted) {
                // The plan cannot be rebuilt without its rules. Drop it rather than charge time
                // that cannot be reported; the legacy path behaved the same way. The legacy
                // session keys belong to LOCAL and are never touched by a hosted restore.
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
        restoreStudyRound()

        // Recover the rest cursor if the process died after recording the session end but
        // before writing BREAK_UNTIL. V37 writes both in one snapshot; this only fills the gap
        // left behind by an older build's separate keys.
        if (session == null && breakUntil == 0L && lastSessionEndedAt > 0L) {
            recoverRestFromLastEnd(nowWall)
        }
        if (!hosted) {
            // The v2 phase is authoritative; the old boolean is kept in sync only for rollback.
            LocalPolicyStore.saveParentOverrideActive(
                guardStatus == GuardStatus.PARENT_OVERRIDE,
            )
        }
        saveGuardState(
            phase = guardStatus,
            override = guardSnapshot.override.takeIf {
                guardStatus == GuardStatus.PARENT_OVERRIDE
            },
            study = guardSnapshot.study.takeIf { guardStatus == GuardStatus.STUDY_MODE },
        )
        // V58: a fruit debit is written before the session starts. After a process death the
        // route can only tell which reservations really started once the session/settled state
        // above is loaded. This never runs for the hosted partition: its account is separate.
        if (!hosted) reconcileFruitLedgers()
    }

    /** Loads the entertainment and study ledgers; the study ledger never feeds [usedSeconds]. */
    private fun loadLedgers(ledger: RuntimeLedger) {
        dayUsage.clear()
        ledger.days.entries
            .filter { (date, seconds) -> USAGE_DATE.matches(date) && seconds >= 0L }
            .takeLast(LEDGER_RETENTION_DAYS)
            .forEach { (date, seconds) -> dayUsage[date] = seconds }
        usedSeconds = dayUsage[dayKey] ?: 0L
        studyDays.clear()
        ledger.studyDays.entries
            .filter { (date, seconds) -> USAGE_DATE.matches(date) && seconds >= 0L }
            .takeLast(LEDGER_RETENTION_DAYS)
            .forEach { (date, seconds) -> studyDays[date] = seconds }
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
        val pauseReason = if (!paused) {
            null
        } else {
            if (!screenInteractive) SessionPauseReason.SCREEN_OFF else saved.pauseReason
        }
        return restoreSession(saved, remaining, paused, now.wallMs, pauseReason)
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
     * Restores a focus round only on the same boot and only while its study app is still enabled
     * for redemption. A restart drops it (the child must study again); dead time is never charged,
     * so a restored round can only be shorter, never a free reward.
     *
     * A restored round is always paused. The process cannot prove the child was still in the study
     * app while it was dead, and Aerial is in front at restore time, so counting would charge the
     * child for sitting on the focus desktop. 继续学习 resumes it explicitly.
     */
    private fun restoreStudyRound() {
        studyRound = null
        studyRoundSavedTick = 0L
        val hosted = hostedStorage
        // Once the subject cache exists it is authoritative even when it holds no round: falling
        // back to the legacy key would resurrect a round this subject already canceled. Only
        // the default member (or a family that failed to load) may read that legacy key.
        // The hosted partition has no subject cache: its round lives only under HOSTED_*.
        val family = FamilyHub.state.value
        val isDefaultSubject = !family.ready || family.activeUserId == family.defaultUserId
        val subjectRuntime = if (hosted) null else FamilyHub.activeRuntime()
        val saved: SavedStudyRound? = when {
            hosted -> LocalPolicyStore.loadStudyRound(hosted = true, hostedScope = hostedScope)
            subjectRuntime != null -> subjectRuntime.studyRound
            isDefaultSubject -> LocalPolicyStore.loadStudyRound()
            else -> null
        }
        if (saved == null) {
            // The subject document is authoritative even when it holds no round. Drop a stale
            // default-member mirror so a later downgrade cannot resurrect a canceled round.
            if (!hosted && isDefaultSubject && subjectRuntime != null) {
                LocalPolicyStore.clearStudyRound()
            }
            return
        }
        val configured = rules?.normalized()
        val sameDay = saved.usageDate == LocalDate.now().toString()
        val studyLimit = configured?.studyDailyLimitFor(LocalDate.now().dayOfWeek.value)
        val usedStudy = studyDays[saved.usageDate] ?: 0L
        val valid = configured != null &&
            configured.isUsable &&
            saved.boot.sameBoot(bootEvidence()) &&
            sameDay &&
            saved.remainingSeconds > 0L &&
            saved.studiedSeconds <= saved.totalSeconds &&
            configured.studyRouteActive &&
            saved.studyPackage in configured.effectiveStudyPackages &&
            (studyLimit == null || usedStudy < studyLimit * 60L) &&
            session == null &&
            pendingRemoteStart == null &&
            guardStatus == GuardStatus.ENFORCING &&
            activation == Activation.MANUAL &&
            !studyMode
        if (!valid) {
            LocalPolicyStore.clearStudyRound(hosted = hosted, hostedScope = hostedScope)
            return
        }
        val timedPackages = configured?.timedPackages().orEmpty()
        val rewardEnabled = saved.rewardEnabled &&
            saved.studyPackage in configured.effectiveStudyEarnPackages
        val target = saved.targetPackage
            ?.takeIf { rewardEnabled && it in timedPackages }
        // An old saved round may carry a reward above the current cap or above the current
        // effective Y; clamp it on restore so the attempt and the study UI agree.
        val savedRewardSeconds = if (rewardEnabled) {
            saved.rewardSeconds.coerceIn(
                0L,
                configured.effectiveStudyRewardMinutes.toLong() * 60L,
            )
        } else {
            0L
        }
        studyRound = LocalStudyRound(
            studyPackage = saved.studyPackage,
            targetPackage = target,
            totalSeconds = saved.totalSeconds,
            remainingSeconds = saved.remainingSeconds,
            studiedSeconds = saved.studiedSeconds,
            rewardSeconds = savedRewardSeconds,
            rewardEnabled = rewardEnabled,
            rewardAttempted = saved.rewardAttempted,
            rewardGranted = saved.rewardGranted,
            usageDate = saved.usageDate,
            startedAtEpochMs = saved.startedAtEpochMs,
            boot = saved.boot,
            pausedAtEpochMs = System.currentTimeMillis(),
        )
        LockLaunchPermit.setRoundPackage(saved.studyPackage)
        PolicyFlowTrace.event(
            "study.round.restore",
            "study" to saved.studyPackage,
            "remaining" to saved.remainingSeconds,
            "reward" to rewardEnabled,
            "target" to target,
            "paused" to true,
        )
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
     * Loads the stored manual time-rule set. A pre-v2 install is migrated once from its single
     * v1 set (rest preserved); a corrupt v2 document fails closed to setup-required and is never
     * silently replaced by the v1 snapshot.
     */
    private fun loadRuleSets(
        subjectConfig: com.planyourtv.policy.SubjectConfigDocument? = null,
        /**
         * Only the default member may fall back to the legacy mirror. A later child without a
         * subject document must start unconfigured instead of inheriting the first child's rules.
         */
        allowLegacyFallback: Boolean = true,
    ) {
        if (subjectConfig != null && subjectConfig.ruleSets.isUsable) {
            ruleSets = subjectConfig.ruleSets
            rules = subjectConfig.ruleSets.rules()
            return
        }
        if (!allowLegacyFallback) {
            ruleSets = null
            rules = null
            return
        }
        when (val loaded = LocalPolicyStore.loadRuleSets()) {
            is LocalRuleSetsLoad.Loaded -> {
                ruleSets = loaded.ruleSets
                rules = loaded.ruleSets.rules()
                // Rewrite the v1 write-through with the manual set.
                persistRuleSets(loaded.ruleSets)
            }
            LocalRuleSetsLoad.Missing -> {
                // Schema 3 has no migration path: a fresh install or an older document is
                // unconfigured until the parent saves the new contract.
                ruleSets = null
                rules = null
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
     * Writes the local rule set to the legacy mirror and to the active subject. A hosted rule
     * template belongs to the device mirror, not to the local family cache, so only LOCAL mode
     * owns the subject document.
     */
    private fun persistRuleSets(sets: LocalRuleSets) {
        // Only LOCAL owns the local rule template. A hosted template is cached by the cloud
        // policy store (REMOTE_POLICY), and NONE has no configuration owner at all.
        if (source != PolicySource.LOCAL) return
        LocalPolicyStore.saveRuleSets(sets)
        FamilyHub.saveActiveConfig(Activation.MANUAL, sets)
    }

    /**
     * Clears guard state for a real mode switch; a cold start restore must not call this or it
     * would erase a parent unlock that [initialize] just recovered with evidence.
     */
    @Synchronized
    fun resetGuardForModeSwitch() {
        cancelStudyRound("MODE_CHANGED", bringHome = true)
        guardStatus = GuardStatus.ENFORCING
        parentOverrideUntil = 0L
        studyMode = false
        foregroundPackage = null
        foregroundObservedAtMs = 0L
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

    /** Starts evaluating the lock state; called only while the local guard is active. */
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
        cancelStudyRound("GUARD_INACTIVE", bringHome = false)
        active = false
        foregroundPackage = null
        foregroundObservedAtMs = 0L
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
        // Leaving the hosted partition invalidates its in-memory cursor; the next cloud rule
        // pass must read the partition again instead of reusing LOCAL state.
        if (newSource != PolicySource.HOSTED) hostedExecutionLoaded = false
        val hadPending = pendingRemoteStart != null
        if (newSource != PolicySource.HOSTED) pendingRemoteStart = null
        if (initialized && hadPending && pendingRemoteStart == null) {
            checkpointRuntime(durable = true, forceSync = true)
        }
        publish()
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
            GuardStatus.ENFORCING -> {
                pauseSession(
                    System.currentTimeMillis(),
                    emitCue = true,
                    reason = SessionPauseReason.SCREEN_OFF,
                )
                // A study round is not a session, but it must freeze with the screen too. It
                // stays paused after the screen lights until the child presses 继续学习.
                pauseStudyRound("SCREEN_OFF")
            }
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
        PolicyStateRepository.saveGuard(
            guardSnapshot,
            hosted = hostedStorage,
            hostedScope = hostedScope,
        )
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
            // An unlimited app still in front is not a screen-on action; the guard's foreground
            // report resumes the session when the child returns to a timed app. Screen-off
            // outranked that pause while the screen was dark, so restore the unlimited reason
            // here instead of resuming behind an app that must not charge the session.
            val unlimitedForeground = foregroundPackage?.let {
                it in rules?.normalized()?.effectiveUnlimitedPackages.orEmpty()
            } == true
            if (current.pauseReason == SessionPauseReason.SCREEN_OFF && unlimitedForeground) {
                session = current.copy(pauseReason = SessionPauseReason.UNLIMITED_APP)
                checkpointRuntime(durable = true, forceSync = true)
                publish()
                return
            }
            if (current.pauseReason != SessionPauseReason.UNLIMITED_APP) {
                resumeSession(now, emitCue = true)
            }
        } else {
            onTick()
        }
    }

    private fun pauseSession(
        nowMs: Long,
        emitCue: Boolean,
        reason: SessionPauseReason = SessionPauseReason.SCREEN_OFF,
    ) {
        val current = session ?: return
        // Screen-off outranks an unlimited app in front. Upgrade the reason without moving the
        // pause instant, so turning the screen back on cannot resume behind the unlimited app.
        if (current.isPaused) {
            if (reason == SessionPauseReason.SCREEN_OFF &&
                current.pauseReason != SessionPauseReason.SCREEN_OFF
            ) {
                session = current.copy(pauseReason = SessionPauseReason.SCREEN_OFF)
                markCheckpoint(nowMs)
                checkpointRuntime(durable = true, forceSync = true)
                publish()
            }
            return
        }
        session = current.copy(pausedAtEpochMs = nowMs, pauseReason = reason)
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
        session = current.copy(pausedAtEpochMs = 0L, pauseReason = null)
        markCheckpoint(nowMs)
        checkpointRuntime(durable = true, forceSync = true)
        if (emitCue) PolicyVoiceBus.emit(PolicyVoiceCue.RESUMED)
        publish()
    }

    /**
     * The guard saw a new foreground activity. An unlimited app freezes the entertainment
     * session without consuming its budget; returning to any app in the session scope (or
     * Aerial itself) resumes it. Screen-off pause is handled separately and always wins.
     */
    @Synchronized
    fun onForegroundChanged(pkg: String) {
        if (!initialized || pkg.isBlank()) return
        foregroundPackage = pkg
        foregroundObservedAtMs = SystemClock.elapsedRealtime()
        val configured = rules?.normalized()
        val unlimited = pkg in configured?.effectiveUnlimitedPackages.orEmpty()
        if (unlimited) {
            pauseStudyRound("UNLIMITED_APP")
            if (guardStatus == GuardStatus.ENFORCING) {
                pauseSession(
                    System.currentTimeMillis(),
                    emitCue = true,
                    reason = SessionPauseReason.UNLIMITED_APP,
                )
            }
            return
        }
        val current = session ?: return
        if (!current.isPaused ||
            current.pauseReason != SessionPauseReason.UNLIMITED_APP ||
            !isScreenInteractive()
        ) {
            return
        }
        if (pkg == context?.packageName || pkg in current.plan.allowedPackages) {
            resumeSession(System.currentTimeMillis(), emitCue = true)
        }
    }

    /**
     * The guard's foreground source answered successfully, even when the front app did not
     * change. Cheap enough for the usage-stats worker; [startRemoteFruitSession] uses it to
     * tell "idle for a while" from "the poll has not run since boot".
     */
    fun noteForegroundObserved() {
        foregroundObservedAtMs = SystemClock.elapsedRealtime()
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
        // First-run setup has one manual editor.
        val sets = LocalRuleSets.from(normalized)
        ruleSets = sets
        rules = normalized
        persistRuleSets(sets)
        policyEpoch = LocalPolicyStore.bumpEpoch()
        val code = recoveryCode?.takeIf { LocalRecovery.normalize(it) != null }
            ?: LocalRecovery.newCode()
        LocalPolicyStore.setRecovery(code)
        lockReason = LockReason.NONE
        publish()
        evaluateNow()
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
        val updatedSets = (ruleSets ?: LocalRuleSets.from(normalized))
            .withRules(normalized)
        ruleSets = updatedSets
        rules = normalized
        templateVersion = version.coerceAtLeast(0L)
        persistRuleSets(updatedSets)
        cancelStudyRound("RULES_CHANGED", bringHome = true)
        policyEpoch = LocalPolicyStore.bumpEpoch(
            hosted = hostedStorage,
            hostedScope = hostedScope,
        )
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
            evaluateNow()
        }
        publish()
        return true
    }

    /**
     * Applies the cloud's manual rule set. The materialized rules (including the shared app
     * scope and buffer) may interrupt a session when they actually changed.
     */
    @Synchronized
    fun setRuleSetsWithVersion(newSets: LocalRuleSets, version: Long): Boolean {
        if (!initialized) return false
        val materialized = newSets.rules()
        if (!materialized.isUsable) return false
        refreshInstalledPackages()
        val activeChanged = rules != materialized
        ruleSets = newSets
        rules = materialized
        templateVersion = version.coerceAtLeast(0L)
        persistRuleSets(newSets)
        if (!activeChanged) {
            publish()
            return true
        }
        cancelStudyRound("RULES_CHANGED", bringHome = true)
        policyEpoch = LocalPolicyStore.bumpEpoch(
            hosted = hostedStorage,
            hostedScope = hostedScope,
        )
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
            evaluateNow()
        }
        publish()
        return true
    }

    /** True once the cloud execution partition has been read into memory. */
    @Synchronized
    fun isHostedExecutionLoaded(): Boolean = hostedExecutionLoaded

    /**
     * Switches the executor to the cloud (hosted) partition and loads its own runtime. The
     * local child-mode rules, ledger, guard and study round are never read or written here: a
     * later unbind or manual LOCAL start must restore exactly what the parent configured on
     * the TV.
     */
    @Synchronized
    fun enterHostedMode(
        newSets: LocalRuleSets,
        version: Long,
        subjectId: Long = 0L,
    ): Boolean {
        if (!initialized) return false
        val materialized = newSets.rules()
        if (!materialized.isUsable) return false
        if (hostedExecutionLoaded) {
            if (subjectId > 0L) ensureHostedSubject(subjectId)
            return setRuleSetsWithVersion(newSets, version)
        }
        // Direct assignment, not setSource(): setSource may checkpoint a dropped pending grant,
        // and that checkpoint must never write LOCAL state into the hosted keys.
        source = PolicySource.HOSTED
        hostedSubjectId = subjectId.coerceAtLeast(0L)
        if (hostedSubjectId > 0L) {
            LocalPolicyStore.rememberHostedSubject(hostedSubjectId)
            val adopted = LocalPolicyStore.adoptUnscopedHostedPartition(hostedSubjectId)
            PolicyFlowTrace.event(
                "hosted.subject.scope",
                "member" to hostedSubjectId,
                "adopted" to adopted,
            )
            if (adopted) {
                Log.i(TAG, "adopted pre-scope hosted partition into member $hostedSubjectId")
            }
        }
        refreshInstalledPackages()
        // Drop every in-memory cursor left by the previous partition before reading the hosted
        // snapshot; otherwise the first hosted checkpoint would persist local data.
        session = null
        sessionOrigin = null
        activeRemoteRecordId = 0
        sessionReportUsage = false
        lastUsageReportedSeconds = 0L
        sessionBoot = BootEvidence()
        checkpointWallMs = 0L
        checkpointElapsedMs = 0L
        checkpointAsleepMs = 0L
        checkpointFlushCounter = 0
        screenOffEvidenceForEpisode = false
        lastSavedTick = 0L
        spokenThresholds.clear()
        studyRound = null
        studyRoundSavedTick = 0L
        LockLaunchPermit.clearRoundPackage()
        studyMode = false
        lockReason = LockReason.NONE
        nextWindowLabel = ""
        restRemainingSeconds = 0L
        pendingRemoteStart = null
        ruleSets = newSets
        rules = materialized
        templateVersion = version.coerceAtLeast(0L)
        policyEpoch = LocalPolicyStore.bumpEpoch(hosted = true, hostedScope = hostedScope)
        // A hosted round belongs to the previous hosted template; never resume it after a rule
        // change, and never touch the local round in the same step.
        LocalPolicyStore.clearStudyRound(hosted = true, hostedScope = hostedScope)
        restoreRuntimePartition(allowLegacyFallback = false)
        hostedExecutionLoaded = true
        recoveryComplete = true
        checkpointRuntime(durable = true, forceSync = true)
        if (guardStatus == GuardStatus.ENFORCING && session == null && !studyMode &&
            pendingRemoteStart == null
        ) {
            evaluateNow()
        }
        publish()
        return true
    }

    /**
     * Points the hosted executor at the cloud member that currently owns the TV desktop.
     *
     * The backend re-projects the member's apps when the desktop user changes, but usage,
     * rest and study state live only on the TV. Keeping them in one device-global partition is
     * what let 佳奇's rest leak into another child; each member now has their own snapshot.
     * The outgoing member is settled and checkpointed before the pointer moves, and the target
     * partition is read back exactly like a cold start.
     */
    @Synchronized
    fun ensureHostedSubject(subjectId: Long): Boolean {
        if (!initialized || !hostedExecutionLoaded || subjectId <= 0L) return false
        if (hostedSubjectId == subjectId) return false

        if (hostedSubjectId <= 0L) {
            // The hosted engine was entered without a member id (cold boot restores the cached
            // cloud policy before the roster is fetched), so it read the bare pre-scope keys.
            // That bootstrap view may belong to whoever was active before the restart — it must
            // never be written back as this member's state. Adopt the bare partition once (the
            // upgrade path), then re-read this member's own scoped snapshot so a cold boot also
            // lands on their real ledger/rest cursor instead of the un-scoped leftover.
            hostedSubjectId = subjectId
            LocalPolicyStore.rememberHostedSubject(subjectId)
            val adopted = LocalPolicyStore.adoptUnscopedHostedPartition(subjectId)
            PolicyFlowTrace.event(
                "hosted.subject.scope",
                "member" to subjectId,
                "adopted" to adopted,
            )
            resetTransientForHostedSwitch()
            restoreRuntimePartition(allowLegacyFallback = false)
            // A session recovered here is this member's own crash/boot artifact, so it is kept
            // exactly like enterHostedMode does; only a live switch settles the outgoing one.
            checkpointRuntime(durable = true, forceSync = true)
            PolicyFlowTrace.event(
                "hosted.subject.restore",
                "member" to subjectId,
                "session" to (session != null),
                "rest" to LocalRestPolicy.remainingSeconds(breakUntil, System.currentTimeMillis()),
            )
            if (guardStatus == GuardStatus.ENFORCING && session == null && !studyMode &&
                pendingRemoteStart == null
            ) {
                evaluateNow()
            } else {
                publish()
            }
            return true
        }

        val previous = hostedSubjectId
        endSessionForSwitch()
        checkpointRuntime(durable = true, forceSync = true)
        // Drop every cursor that belonged to [previous] before the scope changes; storage is
        // untouched here so the outgoing member's own document stays intact.
        resetTransientForHostedSwitch()
        hostedSubjectId = subjectId
        LocalPolicyStore.rememberHostedSubject(subjectId)
        restoreRuntimePartition(allowLegacyFallback = false)

        // A switch is not a cold boot: a session or round recovered from a crash artifact
        // belongs to a moment when this child was not the desktop user. Settle the session
        // honestly into their ledger, and drop the round with a log instead of resurrecting it.
        val restoredSession = session
        if (restoredSession != null) {
            Log.w(
                TAG,
                "settling stale hosted session on member switch member=$subjectId " +
                    "id=${restoredSession.plan.sessionId}",
            )
            finishSession(parentStop = false, emitCue = false, forSwitch = true)
        }
        if (studyRound != null) {
            Log.w(TAG, "dropping stale hosted study round on member switch member=$subjectId")
            clearStudyRoundState()
        }
        checkpointRuntime(durable = true, forceSync = true)
        PolicyFlowTrace.event(
            "hosted.subject.switch",
            "from" to previous,
            "member" to subjectId,
            "rest" to LocalRestPolicy.remainingSeconds(breakUntil, System.currentTimeMillis()),
        )
        // Republish the target member's lock state in the same transition. Without this the
        // desktop keeps the previous frame (no rest) until the next tick, so a child switching
        // back briefly sees themselves as usable while their rest cursor is still running.
        if (guardStatus == GuardStatus.ENFORCING && session == null && !studyMode &&
            pendingRemoteStart == null
        ) {
            evaluateNow()
        } else {
            publish()
        }
        return true
    }

    /** Memory-only reset used before the hosted scope moves to another member. */
    private fun resetTransientForHostedSwitch() {
        session = null
        sessionOrigin = null
        activeRemoteRecordId = 0
        sessionReportUsage = false
        lastUsageReportedSeconds = 0L
        sessionBoot = BootEvidence()
        checkpointWallMs = 0L
        checkpointElapsedMs = 0L
        checkpointAsleepMs = 0L
        checkpointFlushCounter = 0
        screenOffEvidenceForEpisode = false
        lastSavedTick = 0L
        spokenThresholds.clear()
        studyRound = null
        studyRoundSavedTick = 0L
        LockLaunchPermit.clearRoundPackage()
        studyMode = false
        lockReason = LockReason.NONE
        nextWindowLabel = ""
        restRemainingSeconds = 0L
        pendingRemoteStart = null
    }

    /**
     * Drops the cloud partition after an unbind. The TV keeps its local child mode intact; the
     * cloud execution state (ledger/guard/round) must not resurrect on a later rebind.
     */
    @Synchronized
    fun clearHostedExecution() {
        LocalPolicyStore.clearHostedExecution()
        hostedSubjectId = 0L
        hostedExecutionLoaded = false
        hostedFruitFacts = null
        hostedFruitFactsScope = null
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

    val currentRules: LocalRules?
        get() = rules

    /**
     * V45 manual timer summary for the parent Web page. Counts are computed against the current
     * launchable scan so an all-apps scope reports the same number the TV can actually launch.
     */
    @Synchronized
    fun manualTimerScope(): ManualTimerScope? =
        rules?.normalized()?.takeIf { it.isUsable }?.manualTimerScope(installedPackages)

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
        get() {
            if (!recoveryComplete) return false
            if (!LocalPolicyStore.pinConfigured) return true
            // The first-run wizard also sets the family PIN. A secondary member with missing or
            // corrupt rules must not reach it, or switching to that child could reset the PIN.
            // Such a member stays on the lock screen until the parent repairs its rules.
            val family = FamilyHub.state.value
            val isDefaultMember = !family.ready ||
                family.activeUserId == family.defaultUserId
            return isDefaultMember && rules?.isUsable != true
        }

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

    /**
     * True while the active child owns a running session, focus round, pending grant or
     * temporary unlock. A member switch must wait: moving the pointer under any of them would
     * charge or unlock the wrong child.
     */
    @Synchronized
    fun canSwitchSubject(): Boolean =
        initialized &&
            session == null &&
            studyRound == null &&
            pendingRemoteStart == null &&
            guardStatus == GuardStatus.ENFORCING &&
            !studyMode

    /** Commits the outgoing child's runtime before the active pointer moves. */
    @Synchronized
    fun prepareSubjectSwitch(): Boolean {
        if (!canSwitchSubject()) return false
        checkpointRuntime(durable = true, forceSync = true)
        return true
    }

    /**
     * Replaces the in-memory subject after the active pointer moved. The switch is only allowed
     * while idle, so this reloads the ledger, rules and activation from the newly active subject
     * and never resumes a leftover session. A non-default member without a subject document
     * starts unconfigured rather than inheriting the first child's rules or usage.
     */
    @Synchronized
    fun reloadActiveSubject() {
        if (!initialized) return
        val family = FamilyHub.state.value
        val isDefault = !family.ready || family.activeUserId == family.defaultUserId
        val subjectConfig = FamilyHub.activeConfig()
        // A switch only runs in LOCAL mode, where the model executes MANUAL. Reading the target
        // document's stored activation would let a copied or migrated AUTO cursor resurrect;
        // every transient timer is reset below, so MANUAL is the only safe activation here.
        activation = Activation.MANUAL
        loadRuleSets(subjectConfig, allowLegacyFallback = isDefault)
        policyEpoch = LocalPolicyStore.policyEpoch()
        settledGrantRecords.clear()
        settledGrantRecords.addAll(LocalPolicyStore.settledGrantRecordIds())

        val nowWall = System.currentTimeMillis()
        val savedRuntime = FamilyHub.activeRuntime()?.runtime
            ?: if (isDefault) {
                PolicyStateRepository.loadRuntimeOrMigrate().snapshot
            } else {
                RuntimeSnapshot()
            }
        dayKey = LocalDate.now().toString()
        loadLedgers(savedRuntime.ledger)
        routeUsage = savedRuntime.ledger.routeUsage?.normalizedFor(dayKey)
        breakUntil = savedRuntime.ledger.breakUntilEpochMs.takeIf { it > nowWall } ?: 0L
        lastSessionEndedAt = savedRuntime.ledger.lastEndedAtEpochMs
        settledSessions.clear()
        settledSessions.putAll(savedRuntime.ledger.settled)
        while (settledSessions.size > SETTLED_SESSION_LIMIT) {
            settledSessions.remove(settledSessions.keys.first())
        }
        restorePendingGrant(savedRuntime.ledger.pendingGrant, nowWall)

        // Subject switches happen between rounds. Keep every transient timer idle even when the
        // stored runtime of the target is missing or carries a crash artifact.
        session = null
        sessionOrigin = null
        activeRemoteRecordId = 0
        sessionReportUsage = false
        lastUsageReportedSeconds = 0L
        sessionBoot = BootEvidence()
        checkpointWallMs = 0L
        checkpointElapsedMs = 0L
        checkpointAsleepMs = 0L
        checkpointFlushCounter = 0
        screenOffEvidenceForEpisode = false
        lastSavedTick = 0L
        spokenThresholds.clear()
        studyRound = null
        studyRoundSavedTick = 0L
        LocalPolicyStore.clearStudyRound()
        LockLaunchPermit.clearRoundPackage()
        guardStatus = GuardStatus.ENFORCING
        parentOverrideUntil = 0L
        studyMode = false
        saveGuardState(GuardStatus.ENFORCING, override = null, study = null)
        lockReason = LockReason.NONE
        nextWindowLabel = ""
        restRemainingSeconds = 0L
        recoveryComplete = true
        checkpointRuntime(durable = true, forceSync = true)
        reconcileFruitLedgers()
        publish()
    }

    /** Parent center action: end the current session. */
    @Synchronized
    fun endSessionByParent() {
        if (session == null) return
        finishSession(parentStop = true, emitCue = true)
    }

    /**
     * Child action in the TV control center. The used time is settled and the session ends
     * immediately. A rest still applies so “exit” cannot be used to skip the rest protection;
     * afterwards the lock returns to the parent waiting state.
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
     * Starts the study timer for one study app. Every package in the study scope is timed by the
     * group's study windows and study quota; only membership in [LocalRules.studyEarnPackages]
     * controls the optional entertainment reward.
     *
     * The round allowance is the rest of the study window, capped by the remaining study quota.
     * Entertainment availability is deliberately not part of admission: a reward that cannot be
     * used is skipped when its one attempt fires, while studying continues.
     */
    @Synchronized
    fun startStudyRound(
        studyPackage: String,
        targetPackage: String?,
        /** A tile on the study launcher: a valid round leaves the launcher for the app. */
        fromStudyMode: Boolean = false,
    ): SessionStartResult {
        if (!initialized || !active) {
            return rejectStudyRound(RejectReason.GUARD_DISABLED)
        }
        // 2026-11-21: the hosted cloud rule guard runs the same focus-study route as LOCAL
        // mode. The cloud projection carries the member's study apps, X/Y and windows.
        if (source != PolicySource.LOCAL && source != PolicySource.HOSTED) {
            return rejectStudyRound(RejectReason.INVALID_COMMAND)
        }
        val leavingStudyMode = fromStudyMode &&
            studyMode &&
            guardStatus == GuardStatus.STUDY_MODE
        if ((guardStatus != GuardStatus.ENFORCING && !leavingStudyMode) ||
            activation != Activation.MANUAL ||
            (studyMode && !leavingStudyMode)
        ) {
            return rejectStudyRound(RejectReason.BUSY)
        }
        if (session != null || pendingRemoteStart != null || studyRound != null) {
            return rejectStudyRound(RejectReason.BUSY)
        }
        if (!isScreenInteractive()) return rejectStudyRound(RejectReason.SCREEN_OFF)
        val configured = rules?.normalized()?.takeIf { it.isUsable }
            ?: return rejectStudyRound(RejectReason.INVALID_COMMAND)
        // The master switch pauses study mode and study-to-earn without clearing their lists.
        if (!configured.studyRouteActive) {
            return rejectStudyRound(RejectReason.INVALID_COMMAND, "studyRouteOff")
        }
        if (studyPackage !in configured.effectiveStudyPackages) {
            return rejectStudyRound(RejectReason.INVALID_COMMAND, "studyAppNotAllowed")
        }
        val now = LocalDateTime.now()
        val allowance = studyAllowance(configured, now)
            ?: return rejectStudyRound(RejectReason.OUTSIDE_WINDOW, "windowClosed")
        if (allowance <= 0L) {
            return rejectStudyRound(
                if (configured.studyDailyLimitFor(now.dayOfWeek.value) != null) {
                    RejectReason.DAILY_LIMIT
                } else {
                    RejectReason.OUTSIDE_WINDOW
                },
                "noAllowance",
            )
        }
        val rewardEnabled = studyPackage in configured.effectiveStudyEarnPackages
        val rewardSeconds = if (rewardEnabled) {
            configured.effectiveStudyRewardMinutes.toLong() * 60L
        } else {
            0L
        }
        val target = targetPackage
            ?.takeIf { rewardEnabled && it in configured.timedPackages() }
        val nowMs = System.currentTimeMillis()
        val round = LocalStudyRound(
            studyPackage = studyPackage,
            targetPackage = target,
            totalSeconds = allowance,
            remainingSeconds = allowance,
            rewardSeconds = rewardSeconds,
            rewardEnabled = rewardEnabled,
            usageDate = dayKey,
            startedAtEpochMs = nowMs,
            boot = bootEvidence(),
        )
        studyRound = round
        studyRoundSavedTick = 0L
        if (leavingStudyMode) {
            studyMode = false
            guardStatus = GuardStatus.ENFORCING
            saveGuardState(phase = GuardStatus.ENFORCING, override = null, study = null)
        }
        persistStudyRound(round)
        LockLaunchPermit.setRoundPackage(studyPackage)
        PolicyFlowTrace.event(
            "study.round.start",
            "study" to studyPackage,
            "target" to target,
            "seconds" to allowance,
            "reward" to rewardEnabled,
            "rewardMinutes" to configured.effectiveStudyRewardMinutes,
            "source" to source,
        )
        publish()
        return SessionStartResult.Started
    }

    /** One log line per refused study round; the lock screen shows the same reason. */
    private fun rejectStudyRound(
        reason: RejectReason,
        detail: String = "-",
    ): SessionStartResult {
        PolicyFlowTrace.event(
            "study.round.reject",
            "reason" to reason,
            "detail" to detail,
            "source" to source,
            "guard" to guardStatus,
            "activation" to activation,
            "session" to (session != null),
            "round" to (studyRound != null),
            "screen" to isScreenInteractive(),
        )
        return SessionStartResult.Rejected(reason)
    }

    /**
     * Freezes a running round. Aerial coming back to the front, screen off and a launch that never
     * covered the focus desktop all land here. Idempotent, and the pause is written at once: a
     * process killed in standby must not restore a round with more time than the child saw.
     */
    @Synchronized
    fun pauseStudyRound(reason: String): Boolean {
        val round = studyRound ?: return false
        if (round.isPaused) return false
        val paused = round.copy(pausedAtEpochMs = System.currentTimeMillis())
        studyRound = paused
        studyRoundSavedTick = 0L
        persistStudyRound(paused)
        PolicyFlowTrace.event(
            "study.round.pause",
            "reason" to reason,
            "remaining" to paused.remainingSeconds,
        )
        publish()
        return true
    }

    /**
     * Child-confirmed resume from the focus desktop. Time spent paused must not let a round cross
     * a closing study window or a quota that was already used up; entertainment availability is
     * checked only when the one reward attempt fires. A running round is already started.
     */
    @Synchronized
    fun resumeStudyRound(): SessionStartResult {
        val round = studyRound ?: return SessionStartResult.Rejected(RejectReason.INVALID_COMMAND)
        if (!round.isPaused) return SessionStartResult.Started
        if (!initialized || !active) {
            return SessionStartResult.Rejected(RejectReason.GUARD_DISABLED)
        }
        // Hosted rounds resume on the same path; the cloud projection supplied the round.
        if (source != PolicySource.LOCAL && source != PolicySource.HOSTED) {
            return SessionStartResult.Rejected(RejectReason.INVALID_COMMAND)
        }
        if (guardStatus != GuardStatus.ENFORCING || activation != Activation.MANUAL || studyMode) {
            return SessionStartResult.Rejected(RejectReason.BUSY)
        }
        if (session != null || pendingRemoteStart != null) {
            return SessionStartResult.Rejected(RejectReason.BUSY)
        }
        if (!isScreenInteractive()) return SessionStartResult.Rejected(RejectReason.SCREEN_OFF)
        val configured = rules?.normalized()?.takeIf { it.isUsable }
            ?: return SessionStartResult.Rejected(RejectReason.INVALID_COMMAND)
        if (!configured.studyRouteActive ||
            round.studyPackage !in configured.effectiveStudyPackages
        ) {
            return SessionStartResult.Rejected(RejectReason.INVALID_COMMAND)
        }
        if (round.usageDate != dayKey) {
            return SessionStartResult.Rejected(RejectReason.OUTSIDE_WINDOW)
        }
        val now = LocalDateTime.now()
        currentStudyWindow(configured, now)
            ?: return SessionStartResult.Rejected(RejectReason.OUTSIDE_WINDOW)
        val limit = configured.studyDailyLimitFor(now.dayOfWeek.value)
        if (limit != null && studyUsedSeconds(round.usageDate) >= limit * 60L) {
            return SessionStartResult.Rejected(RejectReason.DAILY_LIMIT)
        }
        val resumed = round.copy(pausedAtEpochMs = 0L)
        studyRound = resumed
        studyRoundSavedTick = 0L
        persistStudyRound(resumed)
        PolicyFlowTrace.event(
            "study.round.resume",
            "study" to resumed.studyPackage,
            "remaining" to resumed.remainingSeconds,
        )
        publish()
        return SessionStartResult.Started
    }

    /**
     * Ends a focus round without a reward. Idempotent; repeated calls only clear once. The caller
     * decides whether the guard must bring Aerial back (mode/rule changes) or the child is
     * already on the lock screen behind the focus desktop.
     */
    @Synchronized
    fun cancelStudyRound(reason: String, bringHome: Boolean = false): Boolean {
        val round = studyRound ?: return false
        clearStudyRoundState()
        PolicyFlowTrace.event(
            "study.round.cancel",
            "reason" to reason,
            "study" to round.studyPackage,
            "remaining" to round.remainingSeconds,
        )
        publish()
        if (bringHome) PolicyRuntime.bringHome()
        return true
    }

    private fun clearStudyRoundState() {
        studyRound = null
        studyRoundSavedTick = 0L
        LocalPolicyStore.clearStudyRound(
            hosted = hostedStorage,
            hostedScope = hostedScope,
        )
        if (!hostedStorage) {
            // The subject cache carries its own copy of the round; clear it in the same
            // transition so a restart cannot restore a round that was just completed or
            // canceled. The hosted partition never writes the subject cache.
            FamilyHub.saveActiveRuntime(runtimeSnapshot(), null)
        }
        LockLaunchPermit.clearRoundPackage()
    }

    private fun currentSavedStudyRound(): SavedStudyRound? = studyRound?.let { it.toSavedRound() }

    private fun LocalStudyRound.toSavedRound(): SavedStudyRound = SavedStudyRound(
        studyPackage = studyPackage,
        targetPackage = targetPackage,
        totalSeconds = totalSeconds,
        remainingSeconds = remainingSeconds,
        studiedSeconds = studiedSeconds,
        rewardSeconds = rewardSeconds,
        rewardEnabled = rewardEnabled,
        rewardAttempted = rewardAttempted,
        rewardGranted = rewardGranted,
        usageDate = usageDate,
        startedAtEpochMs = startedAtEpochMs,
        boot = boot,
    )

    private fun persistStudyRound(round: LocalStudyRound) {
        val saved = round.toSavedRound()
        LocalPolicyStore.saveStudyRound(
            saved,
            hosted = hostedStorage,
            hostedScope = hostedScope,
        )
        if (!hostedStorage) FamilyHub.saveActiveRuntime(runtimeSnapshot(), saved)
    }

    /**
     * The round's study allowance or quota ran out without a reward. Clear it and return to the
     * lock screen; the study app must not stay usable after its time is gone.
     */
    private fun completeStudyRound(round: LocalStudyRound) {
        clearStudyRoundState()
        PolicyFlowTrace.event(
            "study.round.complete",
            "study" to round.studyPackage,
            "studied" to round.studiedSeconds,
            "reward" to (round.rewardGranted || round.rewardAttempted),
        )
        checkpointRuntime(durable = true, forceSync = true)
        publish()
        PolicyRuntime.bringHome()
    }

    /**
     * One tick of the study timer. The study ledger is credited every tick so a crash cannot
     * refund already-studied time. A paused round still verifies that its window and app are
     * valid; screen-off pauses before this runs, and this check covers a missed broadcast.
     */
    private fun tickStudyRound(round: LocalStudyRound) {
        val configured = rules?.normalized()
        val now = LocalDateTime.now()
        val windowOpen = configured != null &&
            configured.isUsable &&
            currentStudyWindow(configured, now) != null &&
            round.studyPackage in configured.effectiveStudyPackages
        if (!windowOpen || round.usageDate != dayKey) {
            cancelStudyRound(
                if (round.usageDate != dayKey) "DAY_ROLLOVER" else "WINDOW_CLOSED",
                bringHome = true,
            )
            return
        }
        if (round.isPaused) return
        val limit = configured.studyDailyLimitFor(now.dayOfWeek.value)
        val used = studyUsedSeconds(round.usageDate)
        if (limit != null && used >= limit * 60L) {
            completeStudyRound(round)
            return
        }
        val remaining = (round.remainingSeconds - 1L).coerceAtLeast(0L)
        val studied = round.studiedSeconds + 1L
        creditStudy(round.usageDate, 1L)
        var updated = round.copy(remainingSeconds = remaining, studiedSeconds = studied)
        if (updated.rewardEnabled && !updated.rewardAttempted) {
            val rewardAt = configured.studyEarnStudyMinutes * 60L
            if (updated.studiedSeconds >= rewardAt) {
                if (attemptStudyReward(updated)) return
                updated = updated.copy(rewardAttempted = true)
            }
        }
        val limitReached = limit != null && studyUsedSeconds(round.usageDate) >= limit * 60L
        if (updated.remainingSeconds <= 0L || limitReached) {
            completeStudyRound(updated)
            return
        }
        studyRound = updated
        studyRoundSavedTick += 1L
        if (studyRoundSavedTick >= SAVE_EVERY_TICKS) {
            studyRoundSavedTick = 0L
            persistStudyRound(updated)
        }
        publish()
    }

    /**
     * The one reward attempt for a round. It is deliberately pre-checked before the round is
     * cleared: an unavailable entertainment window/quota/target skips this reward and study
     * continues, while a successful attempt starts the normal, fully admitted session.
     */
    private fun attemptStudyReward(round: LocalStudyRound): Boolean {
        val configured = rules?.normalized() ?: return false
        val now = LocalDateTime.now()
        val nowMs = System.currentTimeMillis()
        if (breakUntil > nowMs || !isScreenInteractive()) return false
        if (round.rewardSeconds <= 0L || round.usageDate != dayKey) return false
        // A saved round from an older build may carry Y above the current cap. Clamp at
        // attempt time; the rules keep their original stored values. X never caps Y.
        val rewardSeconds = minOf(
            round.rewardSeconds,
            configured.effectiveStudyRewardMinutes.toLong() * 60L,
        ).coerceAtLeast(1L)
        // The entertainment window must be open, but a short remaining window is not a refusal:
        // V44 lets the grant deploy and the session ends with the window, like the other routes.
        currentWindow(configured, now) ?: return false
        // The target only chooses which app opens after the reward. The session itself grants
        // every authorized app, not just that target. Without any entertainment app there is
        // nothing to reward, so the round keeps waiting instead of starting an empty session.
        val target = round.targetPackage?.takeIf { it in configured.timedPackages() }
        if (configured.timedPackages().isEmpty()) return false
        clearStudyRoundState()
        when (
            val result = startEntertainment(
                origin = SessionOrigin.LOCAL,
                durationSeconds = rewardSeconds,
                expectedEpoch = null,
                expiresAtEpochMs = null,
                route = UnlockRoute.STUDY_REWARD,
            )
        ) {
            SessionStartResult.Started -> {
                if (target != null) QuizLaunchTarget.confirm(target)
                PolicyFlowTrace.event(
                    "study.round.reward",
                    "study" to round.studyPackage,
                    "target" to target,
                    "requested" to rewardSeconds,
                    "granted" to (session?.totalSeconds ?: rewardSeconds),
                )
                // The child may still be inside the study app; the reward session and its
                // target live on the lock desktop.
                PolicyRuntime.bringHome()
                publish()
                return true
            }
            is SessionStartResult.Rejected -> {
                PolicyFlowTrace.event(
                    "study.round.reward.skip",
                    "reason" to result.reason,
                    "reward" to rewardSeconds,
                )
                Log.w(TAG, "study reward rejected: ${result.reason}")
            }
            SessionStartResult.Pending -> Unit
        }
        // The admission lost a race (for example the screen turned off); put the round back as
        // running so the refused attempt cannot cost the child its focus time.
        val restored = round.copy(rewardAttempted = true)
        studyRound = restored
        LockLaunchPermit.setRoundPackage(restored.studyPackage)
        persistStudyRound(restored)
        publish()
        return false
    }

    /** The remaining study allowance for [now], or null when the study window is closed. */
    private fun studyAllowance(configured: LocalRules, now: LocalDateTime): Long? {
        val window = currentStudyWindow(configured, now) ?: return null
        val windowRemaining = (window.endSecond - secondOfDay(now)).coerceAtLeast(0).toLong()
        if (windowRemaining <= 0L) return null
        val limit = configured.studyDailyLimitFor(now.dayOfWeek.value)
        return if (limit == null) {
            windowRemaining
        } else {
            minOf(windowRemaining, (limit * 60L - studyUsedSeconds(dayKey)).coerceAtLeast(0L))
        }
    }

    private fun currentStudyWindow(configured: LocalRules, now: LocalDateTime): LocalTimeWindow? =
        configured.studyWindowsFor(now.dayOfWeek.value)
            .firstOrNull { it.contains(secondOfDay(now)) }

    private fun studyUsedSeconds(date: String): Long = studyDays[date] ?: 0L

    private fun creditStudy(date: String, seconds: Long) {
        if (seconds <= 0L) return
        studyDays[date] = (studyDays[date] ?: 0L) + seconds
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
        /**
         * V45 parent Web timer: a deliberate out-of-plan authorization. It uses the manual
         * visible scope and never touches the daily ledger, route counts or rest.
         */
        outsidePlan: Boolean = false,
    ): SessionStartResult {
        // Leaving study mode is a separate parent action. A manual start (including the parent
        // web page) must not clear it as a side effect of commitStart.
        if (studyMode) return SessionStartResult.Rejected(RejectReason.BUSY)
        return startEntertainment(
            origin = SessionOrigin.LOCAL,
            durationSeconds = durationSeconds,
            expectedEpoch = null,
            expiresAtEpochMs = expiresAtEpochMs,
            outsidePlan = outsidePlan,
        )
    }

    /**
     * Child-started session for the direct-start route: tapping an entertainment app starts one
     * session without a quiz or study round. Only [LocalRules.directStartEnabled] authorizes it;
     * the duration is the parent's single-session value, then [admitStart] caps it to the day's
     * remaining quota and the window deadline.
     */
    @Synchronized
    fun startDirectSession(): SessionStartResult {
        if (!initialized || !active) {
            return rejectDirectSession(RejectReason.GUARD_DISABLED)
        }
        // Hosted rule guard (2026-11-21): the cloud projects directStartEnabled, the watch
        // duration (sessionLimitMinutes) and the daily count, so the same route runs here.
        if ((source != PolicySource.LOCAL && source != PolicySource.HOSTED) ||
            activation != Activation.MANUAL
        ) {
            return rejectDirectSession(RejectReason.INVALID_COMMAND)
        }
        // Study mode is a separate child surface. A direct start must not clear it implicitly.
        if (studyMode) return rejectDirectSession(RejectReason.BUSY)
        val configured = rules?.normalized()?.takeIf { it.isUsable }
            ?: return rejectDirectSession(RejectReason.INVALID_COMMAND)
        if (!configured.directRouteActive) {
            return rejectDirectSession(RejectReason.INVALID_COMMAND)
        }
        // The direct input is its own "per viewing" value; the public cap is a ceiling only.
        // Legacy values above the cap are truncated on read, not rewritten here.
        val seconds = configured.effectiveDirectMinutes.toLong() * 60L
        PolicyFlowTrace.event(
            "session.direct.start",
            "seconds" to seconds,
            "session" to (session != null),
            "source" to source,
        )
        return startEntertainment(
            origin = SessionOrigin.LOCAL,
            durationSeconds = seconds,
            expectedEpoch = null,
            expiresAtEpochMs = null,
            route = UnlockRoute.DIRECT,
        )
    }

    /**
     * Child quiz route. The report has already shown the capped value; admission repeats the
     * same planner calculation so the session cannot exceed the public cap, the leftover
     * quota, or the route's daily count.
     */
    @Synchronized
    fun startQuizRewardSession(earnedSeconds: Long): SessionStartResult {
        if (!initialized || !active) {
            return rejectQuizSession(RejectReason.GUARD_DISABLED, earnedSeconds)
        }
        // Hosted rule guard (2026-11-21): the cloud projects quizGateEnabled, the daily count and
        // the shared cap, so the quiz route is admitted here exactly like in LOCAL mode.
        if ((source != PolicySource.LOCAL && source != PolicySource.HOSTED) ||
            activation != Activation.MANUAL
        ) {
            return rejectQuizSession(RejectReason.INVALID_COMMAND, earnedSeconds)
        }
        if (studyMode) return rejectQuizSession(RejectReason.BUSY, earnedSeconds)
        val configured = rules?.normalized()?.takeIf { it.isUsable }
            ?: return rejectQuizSession(RejectReason.INVALID_COMMAND, earnedSeconds)
        if (!configured.quizGateEnabled) {
            return rejectQuizSession(RejectReason.INVALID_COMMAND, earnedSeconds, "quizGateOff")
        }
        if (earnedSeconds <= 0L) {
            return rejectQuizSession(RejectReason.INVALID_COMMAND, earnedSeconds, "nothingEarned")
        }
        PolicyFlowTrace.event(
            "session.quiz.start",
            "earned" to earnedSeconds,
            "cap" to configured.effectiveSessionCapMinutes * 60L,
            "source" to source,
        )
        return startEntertainment(
            origin = SessionOrigin.LOCAL,
            durationSeconds = earnedSeconds,
            expectedEpoch = null,
            expiresAtEpochMs = null,
            route = UnlockRoute.QUIZ,
        )
    }

    /**
     * V58 child fruit exchange. The account debit and the session are one logical transaction:
     * the session id is generated first, the balance is reserved as `PENDING`, only then is the
     * normal admitted session started, and the row becomes `COMMITTED` afterwards. A failure
     * before the session exists restores the fruits; once the session is running there is no
     * refund, even if the child leaves early. The hosted account is a separate system in V58,
     * so only LOCAL mode may enter here.
     */
    @Synchronized
    fun startFruitSession(fruits: Int, title: String): SessionStartResult {
        if (!initialized || !active) {
            return rejectFruitSession(RejectReason.GUARD_DISABLED, fruits)
        }
        if (source != PolicySource.LOCAL || activation != Activation.MANUAL) {
            return rejectFruitSession(RejectReason.INVALID_COMMAND, fruits)
        }
        if (studyMode) return rejectFruitSession(RejectReason.BUSY, fruits)
        val configured = rules?.normalized()?.takeIf { it.isUsable }
            ?: return rejectFruitSession(RejectReason.INVALID_COMMAND, fruits)
        if (!configured.fruitRouteActive) {
            return rejectFruitSession(RejectReason.INVALID_COMMAND, fruits)
        }
        if (fruits !in 1..FruitAccountRules.MAX_EXCHANGE_FRUITS) {
            return rejectFruitSession(RejectReason.INVALID_COMMAND, fruits)
        }
        val memberId = FamilyHub.state.value.activeUserId
        val account = memberId.takeIf { it.isNotBlank() }
            ?.let { LocalFruitAccountStore.load(it) }
            ?: return rejectFruitSession(
                RejectReason.INVALID_COMMAND,
                fruits,
                "accountMissing",
            )
        val rate = account.exchangeRateMinutesPerFruit
        if (!account.enabled || !account.rateValid) {
            return rejectFruitSession(RejectReason.INVALID_COMMAND, fruits, "accountUnavailable")
        }
        val expectedSeconds = fruits.toLong() * rate.toLong() * 60L
        if (expectedSeconds <= 0L) {
            return rejectFruitSession(RejectReason.INVALID_COMMAND, fruits, "durationZero")
        }
        val facts = FruitFacts(
            accountPresent = true,
            accountEnabled = account.enabled,
            exchangeRateMinutesPerFruit = rate,
            availableFruitHundredths = account.availableFruitHundredths,
            selectedFruits = fruits,
            supported = true,
        )
        val admission = admitStart(
            origin = SessionOrigin.LOCAL,
            durationSeconds = expectedSeconds,
            expectedEpoch = null,
            requestedScope = null,
            expiresAtEpochMs = null,
            confirmingPending = false,
            route = UnlockRoute.FRUIT,
            fruit = facts,
        )
        if (admission is StartAdmission.Refused) {
            return rejectFruitSession(admission.reason, fruits)
        }
        val ready = admission as StartAdmission.Ready
        // The planner is the only authority for "how many whole fruits fit"; a mismatch means
        // the exchange would promise minutes the session cannot grant.
        if (ready.budgetSeconds != expectedSeconds) {
            return rejectFruitSession(RejectReason.INVALID_COMMAND, fruits, "budgetMismatch")
        }
        val sessionId = newRuleSessionId()
        val now = ready.nowMs
        val reservation = LocalFruitAccountStore.reserveExchange(
            memberId = memberId,
            fruits = fruits,
            sessionId = sessionId,
            title = title.trim().ifEmpty { "FRUIT" },
            now = now,
        )
        if (reservation is FruitReserveResult.Rejected) {
            return rejectFruitSession(
                RejectReason.INVALID_COMMAND,
                fruits,
                "reserve:${reservation.error.name}",
            )
        }
        val entry = (reservation as FruitReserveResult.Ok).reservation.entry
        // The account rate used for the debit must be the same rate the session was planned
        // with. A parent editing the rate from the Web between those two steps would otherwise
        // create a ledger row that disagrees with the granted minutes; void and ask again.
        if (entry.minutesPerFruit != rate || entry.minutes != fruits * rate) {
            LocalFruitAccountStore.voidExchange(memberId, entry.id, System.currentTimeMillis())
            return rejectFruitSession(RejectReason.INVALID_COMMAND, fruits, "rateChanged")
        }
        return try {
            commitStart(
                ready,
                origin = SessionOrigin.LOCAL,
                route = UnlockRoute.FRUIT,
                sessionId = sessionId,
            )
            // The session is now established; from here on the exchange is final.
            LocalFruitAccountStore.commitExchange(
                memberId,
                entry.id,
                System.currentTimeMillis(),
            )
            PolicyFlowTrace.event(
                "session.fruit.start",
                "fruits" to fruits,
                "rate" to rate,
                "seconds" to expectedSeconds,
                "balance" to entry.fruitHundredths,
                "member" to memberId,
            )
            SessionStartResult.Started
        } catch (error: Throwable) {
            // If startPlan already put the session in memory, it is running and must stay
            // charged; only a failure before that point refunds the reservation.
            if (session?.plan?.sessionId == sessionId) {
                runCatching {
                    LocalFruitAccountStore.commitExchange(
                        memberId,
                        entry.id,
                        System.currentTimeMillis(),
                    )
                }
                Log.w(TAG, "fruit session started but commit was retried", error)
                SessionStartResult.Started
            } else {
                LocalFruitAccountStore.voidExchange(
                    memberId,
                    entry.id,
                    System.currentTimeMillis(),
                )
                Log.e(TAG, "fruit session failed after reserve; restored", error)
                SessionStartResult.Rejected(RejectReason.INVALID_COMMAND)
            }
        }
    }

    /** One log line per refused fruit exchange; the wheel shows the mapped message. */
    private fun rejectFruitSession(
        reason: RejectReason,
        fruits: Int,
        detail: String = "-",
    ): SessionStartResult {
        PolicyFlowTrace.event(
            "session.fruit.reject",
            "reason" to reason,
            "detail" to detail,
            "fruits" to fruits,
            "source" to source,
            "guard" to guardStatus,
            "studyMode" to studyMode,
            "session" to (session != null),
        )
        return SessionStartResult.Rejected(reason)
    }

    /** One log line per refused direct start; the lock screen shows the same reason. */
    private fun rejectDirectSession(reason: RejectReason, detail: String = "-"): SessionStartResult {
        PolicyFlowTrace.event(
            "session.direct.reject",
            "reason" to reason,
            "detail" to detail,
            "source" to source,
            "activation" to activation,
            "guard" to guardStatus,
            "studyMode" to studyMode,
            "session" to (session != null),
        )
        return SessionStartResult.Rejected(reason)
    }

    /** One log line per refused quiz reward; the lock screen shows the same reason. */
    private fun rejectQuizSession(
        reason: RejectReason,
        earnedSeconds: Long,
        detail: String = "-",
    ): SessionStartResult {
        PolicyFlowTrace.event(
            "session.quiz.reject",
            "reason" to reason,
            "detail" to detail,
            "earned" to earnedSeconds,
            "source" to source,
            "activation" to activation,
            "guard" to guardStatus,
            "studyMode" to studyMode,
            "session" to (session != null),
        )
        return SessionStartResult.Rejected(reason)
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
        /**
         * TV-initiated hosted fruit exchange: the child already confirmed the exchange on the
         * lock screen, so the dispatch that follows the HTTP request arms and starts in one
         * step. The pending gate is never shown or spoken; a failed start is returned as a
         * rejection so the caller ACKs false and the orchard rolls the fruit back.
         */
        autoStart: Boolean = false,
    ): SessionStartResult {
        PolicyFlowTrace.event(
            "grant.mqtt.seen",
            "recordId" to recordId,
            "duration" to durationSeconds,
            "pkgs" to allowedPackages?.size,
            "epoch" to expectedEpoch,
            "localEpoch" to policyEpoch,
            "autoStart" to autoStart,
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
                if (autoStart) return confirmRemoteManualSession()
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
                    announce = !autoStart,
                )
                if (!autoStart) return SessionStartResult.Pending
                val started = confirmRemoteManualSession()
                if (started is SessionStartResult.Rejected) {
                    // The exchange must fail as one unit: leaving the armed gate behind would
                    // show a start button for a grant the orchard is about to roll back.
                    pendingRemoteStart?.let { dropPending(it, rememberRecord = false) }
                }
                started
            }
        }
    }

    /**
     * Uniapp fruit exchange delivered as `start_fruit_session` (2026-11-25).
     *
     * <p>Unlike [startRemoteManualSession], this never arms the pending gate: the parent already
     * confirmed the exchange in the orchard page, and the TV is idle (checked below), so the
     * session starts in this same callback. It keeps `outsidePlan=false` so the TV's START /
     * PROGRESS / END usage stream settles the fruit `points_usage_records` row, exactly like
     * the record-less auto-start path used by a TV-initiated exchange.
     *
     * <p>Every check is in-memory: no HTTP, no member switch, no package scan, no quiz refresh.
     * The fruit transaction holds the device row lock while waiting for this ACK, so a backend
     * call here would deadlock.
     */
    @Synchronized
    fun startRemoteFruitSession(
        durationSeconds: Long,
        expectedEpoch: Long,
        allowedPackages: Set<String>?,
        expiresAtEpochMs: Long? = null,
        memberId: Long,
    ): SessionStartResult {
        val startedAt = SystemClock.elapsedRealtime()
        fun reject(reason: RejectReason): SessionStartResult {
            PolicyFlowTrace.event(
                "fruit.busy",
                "reason" to reason,
                "memberId" to memberId,
                "elapsedMs" to (SystemClock.elapsedRealtime() - startedAt),
                "session" to (session != null),
                "studyMode" to studyMode,
                "studyRound" to (studyRound != null),
                "pending" to (pendingRemoteStart != null),
            )
            return SessionStartResult.Rejected(reason)
        }

        if (!initialized || !active) return reject(RejectReason.GUARD_DISABLED)
        // This path is hosted-manual only; a local TV keeps its own fruit flow.
        if (source != PolicySource.HOSTED || activation != Activation.MANUAL) {
            return reject(RejectReason.INVALID_COMMAND)
        }
        if (guardStatus == GuardStatus.PARENT_OVERRIDE) {
            return reject(RejectReason.PARENT_OVERRIDE)
        }
        if (session != null) return reject(RejectReason.BUSY)
        if (studyMode || studyRound != null) return reject(RejectReason.STUDY_ACTIVE)
        if (pendingRemoteStart != null) return reject(RejectReason.PENDING_GRANT)
        // The fruit target must be the child this TV is currently showing. Switching members
        // here would need HTTP, which is forbidden while the backend holds the device lock.
        // Hosted manual mode follows the cloud active member, not the local FamilyHub roster:
        // FamilyHub only backs quiz/avatar state and is never synced from the cloud.
        val hostedMemberId = PolicyRuntime.state.value.cloudActiveMemberId
            ?: return reject(RejectReason.STATE_UNKNOWN)
        if (memberId <= 0L || memberId != hostedMemberId) {
            return reject(RejectReason.MEMBER_MISMATCH)
        }
        // Cached snapshot only: isRoundActive() re-reads the store and the cloud plan.
        if (!QuizRuntime.isStateLoaded()) return reject(RejectReason.STATE_UNKNOWN)
        if (QuizRuntime.isRoundActiveCached()) return reject(RejectReason.QUIZ_ACTIVE)
        if (!isScreenInteractive()) return reject(RejectReason.SCREEN_OFF)
        val observedAt = foregroundObservedAtMs
        if (observedAt <= 0L ||
            SystemClock.elapsedRealtime() - observedAt > FOREGROUND_STALE_MS
        ) {
            return reject(RejectReason.STATE_UNKNOWN)
        }
        val foreground = foregroundPackage
        if (foreground.isNullOrBlank()) return reject(RejectReason.STATE_UNKNOWN)
        // Idle means the guard's own surface or a stock launcher. Any app in front is "in use",
        // including an allowed one: the parent asked for a new timer, not to share the screen.
        if (!isIdleForeground(foreground)) return reject(RejectReason.FOREGROUND_IN_USE)

        val admission = admitStart(
            origin = SessionOrigin.REMOTE,
            durationSeconds = durationSeconds,
            expectedEpoch = expectedEpoch,
            requestedScope = allowedPackages,
            expiresAtEpochMs = expiresAtEpochMs,
            confirmingPending = false,
            remoteVisibleScope = true,
        )
        if (admission is StartAdmission.Refused) return reject(admission.reason)
        val ready = admission as StartAdmission.Ready
        commitStart(ready, SessionOrigin.REMOTE)
        PolicyFlowTrace.event(
            "fruit.commit",
            "duration" to durationSeconds,
            "pkgs" to ready.scope.allowedPackages.size,
            "unlimited" to ready.scope.allowedPackages.count {
                it in ready.configured.effectiveUnlimitedPackages
            },
            "elapsedMs" to (SystemClock.elapsedRealtime() - startedAt),
        )
        return SessionStartResult.Started
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
        expiresAtEpochMs: Long?,
        route: UnlockRoute? = null,
        outsidePlan: Boolean = false,
        fruit: FruitFacts = FruitFacts.UNAVAILABLE,
    ): SessionStartResult = when (
        val admission = admitStart(
            origin = origin,
            durationSeconds = durationSeconds,
            expectedEpoch = expectedEpoch,
            // Every local route uses the session-wide app scope, so it has no requested subset.
            // Remote grants call admitStart directly with their explicit scope.
            requestedScope = null,
            expiresAtEpochMs = expiresAtEpochMs,
            confirmingPending = false,
            route = route,
            outsidePlan = outsidePlan,
            fruit = fruit,
        )
    ) {
        is StartAdmission.Refused -> SessionStartResult.Rejected(admission.reason)
        is StartAdmission.Ready -> {
            commitStart(admission, origin, route = route, outsidePlan = outsidePlan)
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
        route: UnlockRoute? = null,
        outsidePlan: Boolean = false,
        fruit: FruitFacts = FruitFacts.UNAVAILABLE,
        /**
         * Fruit remote start: the requested scope names entertainment packages, but the
         * running plan must expose the same visible scope as the web / TV single timer
         * (entertainment + unlimited + study-reward apps).
         */
        remoteVisibleScope: Boolean = false,
    ): StartAdmission {
        if (!initialized || !active) return StartAdmission.Refused(RejectReason.GUARD_DISABLED)
        if (durationSeconds <= 0) {
            return StartAdmission.Refused(RejectReason.INVALID_COMMAND)
        }
        // The out-of-plan timer is a parent action on the TV lock surface only; remote grants
        // and redemption keep their existing in-plan behavior.
        if (outsidePlan && (origin != SessionOrigin.LOCAL || route != null)) {
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
        if (expectedEpoch != null && expectedEpoch != policyEpoch) {
            return StartAdmission.Refused(RejectReason.STALE_EPOCH)
        }
        if (guardStatus == GuardStatus.PARENT_OVERRIDE) {
            return StartAdmission.Refused(RejectReason.PARENT_OVERRIDE)
        }
        if (session != null) return StartAdmission.Refused(RejectReason.BUSY)
        if (studyRound != null) return StartAdmission.Refused(RejectReason.BUSY)
        if (!confirmingPending && pendingRemoteStart != null) {
            return StartAdmission.Refused(RejectReason.BUSY)
        }
        val nowMs = System.currentTimeMillis()
        // V45: the parent timer deliberately ignores rest, the entertainment window, the daily
        // quota, route counts and the public cap. It still requires the child lock surface, a lit
        // screen and a single active session.
        if (!outsidePlan && breakUntil > nowMs) {
            return StartAdmission.Refused(RejectReason.RESTING)
        }
        if (!isScreenInteractive()) return StartAdmission.Refused(RejectReason.SCREEN_OFF)
        val now = LocalDateTime.now()
        val window = if (outsidePlan) null else {
            currentWindow(configured, now)
                ?: return StartAdmission.Refused(RejectReason.OUTSIDE_WINDOW)
        }
        if (expiresAtEpochMs != null && nowMs > expiresAtEpochMs) {
            return StartAdmission.Refused(RejectReason.EXPIRED)
        }
        if (!outsidePlan && !configured.hasTimedApps()) {
            return StartAdmission.Refused(RejectReason.NO_TIMED_APPS)
        }
        // Unlimited-only scope never needs a countdown; the parent page shows this as its own
        // hint, while the runtime keeps the admission closed even if an old client asks.
        if (outsidePlan && !configured.hasManualTimedApps(installedPackages)) {
            return StartAdmission.Refused(RejectReason.NO_TIMED_APPS)
        }
        // Every local route (quiz, study reward, direct start, the lock-screen timer and the
        // parent Web timer) shares one visible app scope: entertainment, unlimited, and the
        // study apps the parent authorized. A remote fruit start is the exception: the phone
        // names entertainment packages, but execution must match that same visible scope.
        // Other remote grants keep their own explicit scope.
        val scope = when {
            origin == SessionOrigin.LOCAL -> resolveSessionScope(configured)
            remoteVisibleScope -> resolveSessionScope(configured, requestedScope)
            else -> resolveScope(configured, requestedScope)
        } ?: return StartAdmission.Refused(RejectReason.INVALID_SCOPE)
        val secondsLeft = window?.let { (it.endSecond - secondOfDay(now)).coerceAtLeast(1) } ?: 0
        // V29 has no automatic/manual switch on the page: the local rules are the manual rules.
        // The web page no longer exposes a per-session or per-round cap; a parent-chosen
        // duration and a quiz reward both obey the day's quota. Remote hosted grants keep
        // their own server-side budget.
        val budgetSeconds = when {
            outsidePlan -> durationSeconds.coerceAtMost(ManualSessionLimits.MAX_MINUTES * 60L)
            // V44 child routes share one planner with the TV/Web projection. The planner owns
            // the cap/quota/count arithmetic; this call is the final authorization.
            origin == SessionOrigin.LOCAL && route != null -> {
                val decision = routeDecision(
                    configured = configured,
                    route = route,
                    now = now,
                    quizEarnedSeconds = durationSeconds.takeIf { route == UnlockRoute.QUIZ },
                    busy = false,
                    fruit = fruit,
                )
                if (!decision.available) {
                    return StartAdmission.Refused(decision.reason.toRejectReason())
                }
                decision.grantSeconds
            }
            origin == SessionOrigin.LOCAL -> {
                val limit = configured.dailyLimitFor(now.dayOfWeek.value)
                val dailyRemaining = limit?.let { (it * 60L - usedSeconds).coerceAtLeast(0L) }
                    ?: Long.MAX_VALUE
                if (dailyRemaining <= 0L) return StartAdmission.Refused(RejectReason.DAILY_LIMIT)
                minOf(durationSeconds, dailyRemaining)
            }
            else -> durationSeconds
        }
        return StartAdmission.Ready(
            configured = configured,
            scope = scope,
            budgetSeconds = budgetSeconds,
            restAfterSeconds = if (outsidePlan) 0L else {
                configured.restFor(now.dayOfWeek.value) * 60L
            },
            nowMs = nowMs,
            windowEndEpochMs = if (window == null) 0L else nowMs + secondsLeft * 1_000L,
        )
    }

    private fun commitStart(
        admission: StartAdmission.Ready,
        origin: SessionOrigin,
        remoteRecordId: Int = 0,
        route: UnlockRoute? = null,
        outsidePlan: Boolean = false,
        sessionId: String? = null,
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
            route = route,
            outsidePlan = outsidePlan,
            sessionIdOverride = sessionId,
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
        /** False for an auto-started dispatch: the confirmation start publishes the only frame. */
        announce: Boolean = true,
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
        if (!announce) return
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
            evaluateNow()
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
        LocalPolicyStore.saveSettledGrantRecordIds(
            settledGrantRecords,
            hosted = hostedStorage,
            hostedScope = hostedScope,
        )
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

    /**
     * Session visible scope: configured entertainment, unlimited and (when enabled) study apps.
     * A null [requested] means all of them (local routes plus an all-apps grant). A fruit remote
     * start passes its entertainment subset: it must intersect to at least one runnable
     * entertainment package, then the local unlimited/study additions are restored — never a
     * silent widening to every visible app when the intersection is empty.
     */
    private fun resolveSessionScope(
        configured: LocalRules,
        requested: Set<String>? = null,
    ): ResolvedScope? {
        val visible = configured.sessionVisiblePackages(installedPackages)
        if (visible.isEmpty()) return null
        if (requested == null) return ResolvedScope(AccessMode.APPS, visible)
        val entertainment = requested intersect visible
        if (entertainment.isEmpty()) return null
        val packages = (entertainment +
            configured.effectiveUnlimitedPackages +
            configured.manualStudyRewardPackages()) intersect visible
        return if (packages.isEmpty()) null else ResolvedScope(AccessMode.APPS, packages)
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
            // A round cannot settle into a reward on a new day's rules.
            cancelStudyRound("DAY_ROLLOVER", bringHome = true)
            dayKey = today.toString()
            usedSeconds = dayUsage[dayKey] ?: 0L
            // V44 counts are scoped to one local day. Any session that crossed midnight was
            // already settled above into the date it started, so the new day starts at zero.
            routeUsage = routeUsage?.normalizedFor(dayKey)
            // The armed grant's window/deadline belongs to the old day; never carry an orange
            // confirm button into a new quota day.
            pendingRemoteStart?.let { rememberSettledGrantRecord(it.recordId) }
            pendingRemoteStart = null
            checkpointRuntime(durable = true, forceSync = true)
        }
        if (breakUntil > 0 && now >= breakUntil) {
            breakUntil = 0L
            lastSessionEndedAt = 0L
            checkpointRuntime(durable = true)
            if (guardStatus == GuardStatus.ENFORCING && session == null && !studyMode &&
                isScreenInteractive()
            ) {
                // Rest is over. Re-evaluate only with the screen on; while the screen is off
                // the lock keeps its rest state and transitions after the screen lights.
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
            if (!windowOpen || deadlinePassed) {
                PolicyFlowTrace.event(
                    "grant.expire",
                    "recordId" to armed.recordId,
                    "windowOpen" to windowOpen,
                    "deadlinePassed" to deadlinePassed,
                )
                rememberSettledGrantRecord(armed.recordId)
                pendingRemoteStart = null
                lockReason = LockReason.NONE
                checkpointRuntime(durable = true, forceSync = true)
                if (guardStatus == GuardStatus.ENFORCING && !studyMode) {
                    evaluateNow()
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
                pauseSession(now, emitCue = true, reason = SessionPauseReason.SCREEN_OFF)
            }
            studyRound?.takeIf { !it.isPaused }?.let { pauseStudyRound("SCREEN_OFF") }
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

        studyRound?.let { round ->
            tickStudyRound(round)
            return
        }

        val current = session
        if (current != null) {
            if (current.isPaused) {
                // Covers a missed screen-on broadcast. An unlimited app in front stays paused
                // until the foreground report names a timed app again.
                if (current.pauseReason == SessionPauseReason.UNLIMITED_APP) {
                    publish()
                    return
                }
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

        evaluateNow()
    }

    @Synchronized
    private fun evaluateNow() {
        if (guardStatus != GuardStatus.ENFORCING || studyMode) return
        val configured = rules?.normalized()
        if (configured == null || !configured.isUsable || !LocalPolicyStore.pinConfigured) {
            lockReason = LockReason.SETUP_REQUIRED
            nextWindowLabel = ""
            restRemainingSeconds = 0
            publish()
            return
        }
        if (!configured.hasTimedApps()) {
            lockReason = LockReason.NO_TIMED_APPS
            nextWindowLabel = ""
            restRemainingSeconds = 0
            publish()
            return
        }
        lockManualWaiting(configured)
        publish()
    }

    /** Rest completed with the screen on: re-evaluate the manual waiting state. */
    private fun enterRestCompletedState() {
        // The window may have closed or the daily quota may have run out while the child was
        // resting, so the lock state must be recomputed rather than blindly kept.
        evaluateNow()
    }

    private fun lockManualWaiting(configured: LocalRules) {
        val now = LocalDateTime.now()
        val nowMs = System.currentTimeMillis()
        restRemainingSeconds = LocalRestPolicy.remainingSeconds(breakUntil, nowMs)
        lockReason = if (restRemainingSeconds > 0) LockReason.RESTING else LockReason.MANUAL_WAITING
        nextWindowLabel = nextWindowLabelFor(configured, now)
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
        route: UnlockRoute? = null,
        outsidePlan: Boolean = false,
        /** V58: the fruit debit must name the session before the session is created. */
        sessionIdOverride: String? = null,
    ) {
        // A started plan is the only state that owns the timer; never leave an armed grant
        // beside it (a confirm already cleared it, this is the defensive path).
        pendingRemoteStart = null
        activeRemoteRecordId = remoteRecordId
        // Set before the durable write so a process death cannot lose a parent-confirmed local
        // start or the grant record that owns the session.
        sessionOrigin = origin?.toSessionOrigin(remoteRecordId)
        // The parent's out-of-plan timer is deliberately absent from the hosted usage ledger.
        sessionReportUsage = !outsidePlan && usageSink != null
        val sessionId = sessionIdOverride?.takeIf { it.isNotBlank() } ?: newRuleSessionId()
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
            outsidePlan = outsidePlan,
        )
        session = LocalActiveSession(
            plan = plan,
            remainingSeconds = budgetSeconds,
            startedAtEpochMs = nowMs,
            usageDate = dayKey,
            wallDeadlineEpochMs = wallDeadlineFor(plan, nowMs),
        )
        // V44: the session and its route count become durable in the same snapshot below.
        // Only a successfully established session consumes one count; every finish path keeps
        // the increment, so leaving early never refunds it.
        if (route != null) {
            routeUsage = normalizedRouteUsage().withGrant(route, dayKey)
        }
        lockReason = LockReason.NONE
        restRemainingSeconds = 0L
        nextWindowLabel = ""
        spokenThresholds.clear()
        lastUsageReportedSeconds = 0L
        markCheckpoint(nowMs)
        checkpointRuntime(durable = true, forceSync = true)
        if (!outsidePlan) {
            emitUsage(session!!, RuleUsageEventType.START, 0L, usedTodayFor(session!!, 0L))
        }
        PolicyVoiceBus.emit(PolicyVoiceCue.START)
        publish()
    }

    /** Enter the study launcher from the locked screen. No timer, ledger, plan or PIN. */
    @Synchronized
    fun enterStudyMode(): Boolean {
        if (!initialized || studyMode) return false
        if (guardStatus != GuardStatus.ENFORCING || session != null) return false
        if (studyRound != null) return false
        // An empty study scope or the study master switch off keeps the launcher closed; the
        // lock-screen entry becomes a no-op and the focused footer explains the missing setup.
        val configured = rules?.normalized() ?: return false
        if (!configured.isUsable || !configured.studyRouteActive) return false
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
        evaluateNow()
        publish()
    }

    /**
     * Parent override: end the child's segment (if any) and unlock for parent use. Time spent
     * here is never child usage; on exit the lock screen is recomputed, never a new session.
     */
    @Synchronized
    fun beginParentOverride(durationMs: Long = 0L) {
        // A parent taking over ends the child's focus round; it must not keep counting behind
        // the parent's back or leave a reward armed.
        cancelStudyRound("PARENT_OVERRIDE", bringHome = false)
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
        evaluateNow()
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
            configuredRestMinutes = configured.restFor(LocalDate.now().dayOfWeek.value),
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
        lockReason = LockReason.MANUAL_WAITING
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
        val outsidePlan = current.plan.outsidePlan
        val endBySwitch = forSwitch || crossDay
        // A rest belongs to the mode that earned it. computeRest decides from the same rules
        // the old post-settlement branch used, but the cursor now rides in the settlement
        // snapshot, so a process death cannot separate the end from its rest.
        val restApplies = !outsidePlan && !endBySwitch && when {
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
        // V45: an out-of-plan parent timer writes neither the daily ledger nor rest. The
        // original plan state is intentionally left as it was before the timer started.
        val usedToday = if (outsidePlan) {
            usedSeconds
        } else {
            recordSettlement(
                sessionId = current.plan.sessionId ?: current.plan.id,
                usageDate = current.usageDate,
                consumedSeconds = consumed,
                endedAtEpochMs = endedAt,
                reason = reportedEnd,
                remoteRecordId = activeRemoteRecordId,
            )
        }
        if (!outsidePlan) {
            lastSessionEndedAt = if (restUntil > 0L) endedAt else 0L
            breakUntil = restUntil
        }
        session = null
        sessionOrigin = null
        activeRemoteRecordId = 0
        sessionReportUsage = false
        spokenThresholds.clear()
        lastUsageReportedSeconds = 0L
        // One durable commit carries session=null, the daily total, the settlement record and
        // the rest cursor. A crash after this point can no longer resurrect the session.
        checkpointRuntime(durable = true, forceSync = true)
        if (!outsidePlan) {
            emitUsage(current, RuleUsageEventType.END, consumed, usedToday, reportedEnd, endedAt)
        }
        if (endBySwitch) {
            lockReason = LockReason.NONE
            restRemainingSeconds = 0
            guardStatus = GuardStatus.ENFORCING
            publish()
            return
        }
        Log.i(
            TAG,
            "session end reason=$reportedEnd end=$endedAt consumed=$consumed outside=$outsidePlan",
        )
        if (outsidePlan) {
            // Return to the child's current plan state: the same window/quota/rest facts that
            // were true before the timer started decide the next screen.
            restRemainingSeconds = 0
            guardStatus = GuardStatus.ENFORCING
            evaluateNow()
            publish()
            if (emitCue && reasonOverride == null) PolicyVoiceBus.emit(PolicyVoiceCue.TIME_UP)
            return
        }
        when {
            parentStop -> {
                restRemainingSeconds = 0
                // Only a parent takeover (which is about to unlock the TV for the parent)
                // skips the child's break.
                if (restUntil > 0L) lockReason = LockReason.RESTING
                else enterPostSessionWaiting()
            }
            reportedEnd == PolicyEndReason.USER_EXIT -> {
                restRemainingSeconds = 0
                // Leaving early must not be a way to skip the break.
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
                lockReason = LockReason.MANUAL_WAITING
            }
            endReason == LocalSessionEnd.SESSION_LIMIT -> {
                restRemainingSeconds = 0
                // The plan's snapshot covers a natural end.
                if (restUntil > 0L) lockReason = LockReason.RESTING
                else enterPostSessionWaiting()
            }
            else -> {
                restRemainingSeconds = 0
                lockReason = LockReason.MANUAL_WAITING
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

    /** Today's route counts, using the current local day even if a stale value was restored. */
    private fun normalizedRouteUsage(): RouteUsage =
        (routeUsage ?: RouteUsage(date = dayKey)).normalizedFor(dayKey)

    /**
     * The active data source's fruit account, projected for the planner. LOCAL reads the V58
     * ledger; HOSTED reads the orchard device-context cache for the current cloud member. The
     * two documents are never mixed: switching source switches this projection only.
     */
    private fun currentFruitFacts(selectedFruits: Int? = null): FruitFacts {
        if (source == PolicySource.HOSTED) {
            val hosted = hostedFruitFactsForScope() ?: return FruitFacts.UNAVAILABLE
            if (!hosted.routeEnabled) return FruitFacts.UNAVAILABLE
            return FruitFacts(
                accountPresent = true,
                accountEnabled = true,
                exchangeRateMinutesPerFruit = hosted.exchangeRateMinutesPerFruit,
                availableFruitHundredths = hosted.availableFruitHundredths,
                selectedFruits = selectedFruits,
                supported = true,
                hostedMaxFruits = hosted.maxFruits,
                hostedRemaining = hosted.dailyRemaining,
            )
        }
        if (source != PolicySource.LOCAL) return FruitFacts.UNAVAILABLE
        val memberId = FamilyHub.state.value.activeUserId
        if (memberId.isBlank()) return FruitFacts()
        val account = LocalFruitAccountStore.load(memberId) ?: return FruitFacts()
        return FruitFacts(
            accountPresent = true,
            accountEnabled = account.enabled,
            exchangeRateMinutesPerFruit = account.exchangeRateMinutesPerFruit,
            availableFruitHundredths = account.availableFruitHundredths,
            selectedFruits = selectedFruits,
            supported = true,
        )
    }

    /**
     * Reads the hosted fruit cache for the current scope. The cached field is only trusted for
     * the scope it was loaded under; a member switch re-reads that member's own document.
     */
    /** The cached orchard projection for the active hosted member; null outside hosted execution. */
    @Synchronized
    fun currentHostedFruitFacts(): HostedFruitFacts? = hostedFruitFactsForScope()

    private fun hostedFruitFactsForScope(): HostedFruitFacts? {
        if (source != PolicySource.HOSTED) return null
        val scope = hostedScope ?: return null
        if (scope != hostedFruitFactsScope) {
            hostedFruitFactsScope = scope
            hostedFruitFacts = LocalPolicyStore.loadHostedFruitFacts(scope)
                ?.takeIf { it.memberId == hostedSubjectId }
        }
        return hostedFruitFacts?.takeIf { it.memberId == hostedSubjectId }
    }

    /**
     * Writes the orchard's fruit context into the HOSTED cache and republishes the lock screen.
     * Only the coordinator calls this, and only with the cloud member that is active now; a
     * late response for a previous member is rejected instead of overwriting the new cache.
     */
    @Synchronized
    fun setHostedFruitFacts(facts: HostedFruitFacts?): Boolean {
        if (source != PolicySource.HOSTED) return false
        val scope = hostedScope ?: return false
        if (facts != null && facts.memberId != hostedSubjectId) return false
        hostedFruitFacts = facts
        hostedFruitFactsScope = scope
        if (facts != null) {
            LocalPolicyStore.saveHostedFruitFacts(facts, scope)
        }
        publish()
        return true
    }

    /**
     * Settles fruit reservations left by a crash. Only the active member can own a session in
     * this runtime, so another member's pending row cannot describe a started session and is
     * restored. The rows themselves stay in their own account documents; nothing is merged.
     */
    private fun reconcileFruitLedgers() {
        if (source != PolicySource.LOCAL) return
        val family = FamilyHub.state.value
        val now = System.currentTimeMillis()
        val activeSessionId = session?.plan?.sessionId
        family.members
            .filter { it.status == MemberStatus.ACTIVE }
            .forEach { member ->
                val sessionExists: (String) -> Boolean = if (member.userId == family.activeUserId) {
                    { id -> id == activeSessionId || settledSessions.containsKey(id) }
                } else {
                    { false }
                }
                val result = LocalFruitAccountStore.reconcilePending(
                    memberId = member.userId,
                    now = now,
                    sessionExists = sessionExists,
                ) ?: return@forEach
                if (result.committedEntryIds.isNotEmpty() || result.voidedEntryIds.isNotEmpty()) {
                    PolicyFlowTrace.event(
                        "fruit.reconcile",
                        "member" to member.userId,
                        "committed" to result.committedEntryIds.size,
                        "voided" to result.voidedEntryIds.size,
                        "balance" to result.account.availableFruitHundredths,
                    )
                }
            }
    }

    /**
     * The one V44 projection used by admission and by the published UI state. [busy] is false
     * when the caller has already checked session/study/rest gates; the entry checks below
     * remain in [admitStart] so the planner never becomes the only gate.
     */
    private fun routeDecision(
        configured: LocalRules,
        route: UnlockRoute,
        now: LocalDateTime,
        quizEarnedSeconds: Long? = null,
        busy: Boolean = false,
        fruit: FruitFacts = FruitFacts.UNAVAILABLE,
    ): UnlockRouteDecision {
        val plan = UnlockRoutePlanner.plan(
            rules = configured,
            usage = normalizedRouteUsage(),
            usedSeconds = usedSeconds,
            date = dayKey,
            dayOfWeek = now.dayOfWeek.value,
            secondOfDay = secondOfDay(now),
            facts = UnlockRouteFacts(
                resting = breakUntil > System.currentTimeMillis(),
                busy = busy,
            ),
            quizEarnedSeconds = quizEarnedSeconds,
            fruit = fruit,
        )
        return plan.decision(route)
    }

    private fun unlockRoutePlan(
        now: LocalDateTime,
        fruit: FruitFacts = currentFruitFacts(),
    ): UnlockRoutePlan? {
        val configured = rules?.normalized()?.takeIf { it.isUsable } ?: return null
        return UnlockRoutePlanner.plan(
            rules = withHostedFruitSwitch(configured),
            usage = normalizedRouteUsage(),
            usedSeconds = usedSeconds,
            date = dayKey,
            dayOfWeek = now.dayOfWeek.value,
            secondOfDay = secondOfDay(now),
            facts = UnlockRouteFacts(
                resting = breakUntil > System.currentTimeMillis(),
                busy = session != null || studyRound != null || pendingRemoteStart != null,
            ),
            fruit = fruit,
        )
    }

    /**
     * The hosted fruit route is driven by the orchard's live verdict, not by a LOCAL switch that
     * a cloud snapshot deliberately does not carry. The local daily counter is bypassed because
     * the backend already enforces its own daily count; [FruitFacts.hostedRemaining] supplies
     * the display value. Every other route and every LOCAL plan is untouched.
     */
    private fun withHostedFruitSwitch(configured: LocalRules): LocalRules {
        if (source != PolicySource.HOSTED) return configured
        val hosted = hostedFruitFactsForScope()
        return configured.copy(
            fruitExchangeEnabled = hosted?.routeEnabled == true,
            fruitExchangeDailyLimitCount = -1,
        )
    }

    /**
     * The quiz route's actual grant for [earnedSeconds], in seconds. The report and the start
     * must show/consume this same value so a 60-minute result cannot promise more than the
     * public cap or the leftover quota.
     */
    @Synchronized
    fun quizGrantSeconds(earnedSeconds: Long): Long {
        val configured = rules?.normalized()?.takeIf { it.isUsable } ?: return earnedSeconds
        return routeDecision(
            configured = configured,
            route = UnlockRoute.QUIZ,
            now = LocalDateTime.now(),
            quizEarnedSeconds = earnedSeconds,
            busy = false,
        ).grantSeconds
    }

    private fun UnlockRouteReason.toRejectReason(): RejectReason = when (this) {
        UnlockRouteReason.READY -> RejectReason.INVALID_COMMAND
        UnlockRouteReason.OUTSIDE_WINDOW -> RejectReason.OUTSIDE_WINDOW
        UnlockRouteReason.RESTING -> RejectReason.RESTING
        UnlockRouteReason.BUSY -> RejectReason.BUSY
        UnlockRouteReason.QUOTA_EXHAUSTED -> RejectReason.DAILY_LIMIT
        UnlockRouteReason.COUNT_ZERO,
        UnlockRouteReason.COUNT_EXHAUSTED,
        -> RejectReason.ROUTE_LIMIT
        UnlockRouteReason.SWITCH_OFF,
        UnlockRouteReason.INVALID_RULES,
        UnlockRouteReason.STUDY_NOT_CONFIGURED,
        UnlockRouteReason.NOT_EARNED,
        // The fruit wheel already names these on its card; reaching a start with one means the
        // account changed after the page was drawn.
        UnlockRouteReason.ACCOUNT_DISABLED,
        UnlockRouteReason.BALANCE_EMPTY,
        UnlockRouteReason.BALANCE_BELOW_ONE,
        UnlockRouteReason.CAP_TOO_SMALL,
        UnlockRouteReason.EXCHANGE_TOO_LARGE,
        UnlockRouteReason.WINDOW_TOO_SHORT,
        -> RejectReason.INVALID_COMMAND
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

    /**
     * The default member's legacy repository absorbs every tick, while its subject document is
     * written on durable transitions. Prefer the newer session checkpoint so a killed process
     * does not lose up to one flush interval of already-used time. A session present on only one
     * side wins: a durable clear writes both, so a one-sided session means the other copy lagged.
     * Without a session the subject document stays canonical.
     */
    private fun freshestRuntime(
        subject: RuntimeSnapshot?,
        legacy: RuntimeSnapshot?,
    ): RuntimeSnapshot? {
        if (subject == null) return legacy
        if (legacy == null) return subject
        val subjectSession = subject.session
        val legacySession = legacy.session
        if (subjectSession == null) return legacySession?.let { legacy } ?: subject
        if (legacySession == null) return subject
        return if (legacySession.checkpointWallMs > subjectSession.checkpointWallMs) {
            legacy
        } else {
            subject
        }
    }

    /** The single durable runtime document: session, ledger and armed grant together. */
    private fun runtimeSnapshot(): RuntimeSnapshot = RuntimeSnapshot(
        session = sessionSnapshot(),
        ledger = RuntimeLedger(
            days = LinkedHashMap(dayUsage),
            studyDays = LinkedHashMap(studyDays),
            breakUntilEpochMs = breakUntil,
            lastEndedAtEpochMs = lastSessionEndedAt,
            settled = LinkedHashMap(settledSessions),
            pendingGrant = pendingRemoteStart?.toPendingSnapshot(),
            routeUsage = routeUsage,
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
            pauseReason = current.pauseReason.takeIf { current.isPaused },
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
            outsidePlan = current.plan.outsidePlan,
        )
    }

    /**
     * Commits the whole runtime state as one MMKV value. High-frequency checkpoints use an
     * ordinary encode; transitions settle durably and are mirrored to the legacy keys so an
     * older APK can still roll back without losing the session.
     */
    private fun checkpointRuntime(durable: Boolean = false, forceSync: Boolean = false) {
        if (!TvStore.isReady) return
        val hosted = hostedStorage
        val snapshot = runtimeSnapshot()
        val written = PolicyStateRepository.saveRuntime(
            snapshot = snapshot,
            durable = durable,
            forceSync = forceSync,
            hosted = hosted,
            hostedScope = hostedScope,
        )
        if (!written) {
            Log.w(TAG, "runtime checkpoint failed")
            return
        }
        if (durable) {
            checkpointFlushCounter = 0
            if (!hosted) {
                // The subject document is the canonical V40 copy. It is only committed on
                // durable transitions for now; the legacy mirror still absorbs the
                // high-frequency ticks. The hosted partition never writes either: those keys
                // belong to the TV's own child mode and must survive an unbind untouched.
                FamilyHub.saveActiveRuntime(snapshot, currentSavedStudyRound())
                mirrorRuntimeLegacy()
            }
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
                    outsidePlan = current.plan.outsidePlan,
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
        PolicyStateRepository.saveGuard(
            guardSnapshot,
            hosted = hostedStorage,
            hostedScope = hostedScope,
        )
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
        val studyUsed = studyUsedSeconds(dayKey)
        val studyLimit = configured?.studyDailyLimitFor(nowDateTime.dayOfWeek.value)
        val entertainmentUnlimited = configured?.effectiveUnlimitedPackages.orEmpty()
        val fruitFacts = currentFruitFacts()
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
            // V44 routes are independent. The study master switch still hides its launcher tiles
            // while keeping the stored app lists. The web reads the raw switches from the rules
            // projection, not from here.
            quizGateEnabled = configured?.quizRouteActive == true,
            studyEarnEnabled = configured?.studyRouteActive == true,
            directStartEnabled = configured?.directRouteActive == true,
            unlockRoutePlan = unlockRoutePlan(nowDateTime, fruitFacts),
            fruitAvailableFruitHundredths = if (fruitFacts.supported) {
                fruitFacts.availableFruitHundredths
            } else {
                0L
            },
            fruitExchangeRateMinutesPerFruit = if (fruitFacts.supported) {
                fruitFacts.exchangeRateMinutesPerFruit
            } else {
                0
            },
            studyModeAvailable = configured?.studyRouteActive == true,
            studyModeActive = studyMode,
            studyPackages = configured?.takeIf { it.studyRouteActive }
                ?.effectiveStudyPackages.orEmpty(),
            studyEarnPackages = configured?.takeIf { it.studyRouteActive }
                ?.effectiveStudyEarnPackages.orEmpty(),
            studyEarnStudyMinutes = configured?.studyEarnStudyMinutes
                ?: LocalRules.DEFAULT_STUDY_EARN_STUDY_MINUTES,
            // The reward shown to the child is the effective Y, never an old value above X
            // or above the public cap.
            studyEarnRewardMinutes = configured?.effectiveStudyRewardMinutes
                ?: LocalRules.DEFAULT_STUDY_EARN_REWARD_MINUTES,
            studyRound = studyRound?.let { round ->
                StudyRoundUi(
                    studyPackage = round.studyPackage,
                    targetPackage = round.targetPackage,
                    totalSeconds = round.totalSeconds,
                    remainingSeconds = round.remainingSeconds,
                    studiedSeconds = round.studiedSeconds,
                    studyLimitSeconds = round.totalSeconds,
                    studyLimitUnlimited = studyLimit == null,
                    rewardSeconds = round.rewardSeconds,
                    rewardEnabled = round.rewardEnabled,
                    rewardAtSeconds = (configured?.studyEarnStudyMinutes ?: 0) * 60L,
                    rewardAttempted = round.rewardAttempted,
                    rewardGranted = round.rewardGranted,
                    paused = round.isPaused,
                )
            },
            noTimedApps = configured != null && configured.isUsable && !configured.hasTimedApps(),
            // Study mode and entertainment both publish a concrete APPS scope. A SCREEN scope
            // would let a study app launch during entertainment (and anything launch in study).
            accessMode = if (configured != null) AccessMode.APPS else AccessMode.SCREEN,
            allowedPackages = when {
                studyMode -> configured?.effectiveStudyPackages.orEmpty() + entertainmentUnlimited
                pending != null -> pending.allowedPackages + entertainmentUnlimited
                // The parent timer's scope is fixed at start (entertainment + unlimited +
                // enabled study-reward apps); a later rule edit must not widen it.
                current != null && current.plan.outsidePlan -> current.plan.allowedPackages
                // A hosted manual segment is one concrete grant. The base rule may allow more
                // apps, so falling through would widen the grant after the child confirms.
                // Unbound local sessions keep the live base scope below.
                current != null &&
                    source == PolicySource.HOSTED &&
                    current.plan.activation == PolicyActivation.MANUAL &&
                    current.plan.accessMode == AccessMode.APPS &&
                    current.plan.allowedPackages.isNotEmpty() ->
                    current.plan.allowedPackages + entertainmentUnlimited
                // A local session shows every authorized app: entertainment, unlimited and
                // (when the study switch is on) study apps. Idle keeps the narrower base scope
                // so the lock-screen gateway and the quiz target list stay unchanged.
                current != null && source == PolicySource.LOCAL ->
                    configured?.sessionVisiblePackages(installedPackages).orEmpty()
                else -> configured?.timedPackages().orEmpty() + entertainmentUnlimited
            },
            unlimitedPackages = entertainmentUnlimited,
            totalSeconds = current?.totalSeconds ?: pending?.durationSeconds ?: 0L,
            remainingSeconds = current?.remainingSeconds ?: pending?.durationSeconds ?: 0L,
            effectiveRemainingSeconds = effectiveRemaining,
            activeSessionOutsidePlan = current?.plan?.outsidePlan == true,
            userName = pending?.userName.orEmpty(),
            userIconUrl = pending?.userIconUrl.orEmpty(),
            pinConfigured = LocalPolicyStore.pinConfigured,
            bound = false,
            deviceNo = "",
            bindingDeferred = false,
            localRulesConfigured = configured?.isUsable == true,
            setupRequired = isSetupRequired,
            dailyUsedSeconds = usedSeconds,
            dailyLimitSeconds = (configured?.dailyLimitFor(nowDateTime.dayOfWeek.value) ?: 0)
                .toLong() * 60L,
            studyDailyUsedSeconds = studyUsed,
            studyDailyLimitSeconds = (studyLimit ?: 0).toLong() * 60L,
            studyRemainingSeconds = studyLimit
                ?.let { (it * 60L - studyUsed).coerceAtLeast(0L) }
                ?: 0L,
            studyLimitUnlimited = configured != null && studyLimit == null,
            currentDayGroupLabel = configured?.let {
                dayGroupLabel(it, nowDateTime.dayOfWeek.value)
            }.orEmpty(),
            sessionLimitSeconds = configured?.effectiveDirectMinutes?.toLong()?.times(60L) ?: 0L,
            sessionCapSeconds = configured?.effectiveSessionCapMinutes?.toLong()?.times(60L)
                ?: 0L,
            configuredRestMinutes =
                configured?.restFor(nowDateTime.dayOfWeek.value) ?: 0,
            configuredBufferMinutes =
                configured?.screenOffBufferFor(nowDateTime.dayOfWeek.value) ?: 0,
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
        val next = nextWindow(configured, now) ?: return ""
        return labelFor(next.first, next.second)
    }

    /**
     * The next window start after [now], including a later window today. Returns
     * (dayOffset, minuteOfDay), or null if every template has no windows.
     */
    private fun nextWindow(configured: LocalRules, now: LocalDateTime): Pair<Int, Int>? {
        val currentSecond = secondOfDay(now)
        val today = now.dayOfWeek.value
        for (offset in 0..7) {
            val day = ((today - 1 + offset) % 7) + 1
            val windows = configured.windowsFor(day).sortedBy { it.startMinute }
            val candidate = if (offset == 0) {
                windows.firstOrNull { it.startSecond > currentSecond }
            } else {
                windows.firstOrNull()
            } ?: continue
            return offset to candidate.startMinute
        }
        return null
    }

    private fun dayGroupLabel(configured: LocalRules, dayOfWeek: Int): String {
        val group = configured.groupForDay(dayOfWeek) ?: return ""
        return group.days.joinToString(" ") {
            DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
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
        val next = nextWindow(configured, now) ?: return 0L
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
            bufferMinutes = rules?.screenOffBufferFor(LocalDate.now().dayOfWeek.value)
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

    /**
     * Idle for a remote fruit start: the guard's own surface (child desktop, parent center,
     * lock screen) or a stock launcher. Any other app means someone is using the TV.
     */
    private fun isIdleForeground(pkg: String): Boolean {
        if (pkg == context?.packageName) return true
        return HomeGuardService.stockHomes()?.containsKey(pkg) == true
    }

    private fun currentWindow(configured: LocalRules, now: LocalDateTime): LocalTimeWindow? =
        configured.windowsFor(now.dayOfWeek.value)
            .firstOrNull { it.contains(secondOfDay(now)) }

    private fun secondOfDay(now: LocalDateTime): Int =
        now.hour * 3600 + now.minute * 60 + now.second

    private fun rejectForLock(reason: LockReason): RejectReason = when (reason) {
        LockReason.RESTING -> RejectReason.RESTING
        LockReason.OUTSIDE_WINDOW, LockReason.TIME_UP -> RejectReason.OUTSIDE_WINDOW
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
        routeUsage = null
        dayUsage.clear()
        studyDays.clear()
        foregroundPackage = null
        foregroundObservedAtMs = 0L
        breakUntil = 0L
        lastSessionEndedAt = 0L
        settledSessions.clear()
        parentOverrideUntil = 0L
        guardStatus = GuardStatus.ENFORCING
        guardSnapshot = GuardSnapshot()
        recoveryComplete = false
        activation = Activation.MANUAL
        source = PolicySource.LOCAL
        hostedExecutionLoaded = false
        policyEpoch = 0L
        studyMode = false
        studyRound = null
        studyRoundSavedTick = 0L
        LockLaunchPermit.clearRoundPackage()
        installedPackages = emptySet()
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
        // The V40 subject cache survives an in-memory reset; without this the next initialize()
        // restores the previous test's session and ledger.
        FamilyHub.state.value.members.forEach { LocalFruitAccountStore.deleteMember(it.userId) }
        FamilyHub.resetSubjectDocumentsForTest()
        LocalPolicyStore.resetLocalSetup()
        LocalPolicyStore.clearSettledGrantRecordIds()
        LocalPolicyStore.clearHostedExecution()
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
            activation = PolicyActivation.MANUAL,
            budgetSeconds = seconds.coerceAtLeast(1L),
            endReason = PolicyEndReason.SESSION_LIMIT,
            restAfterSeconds = 0L,
            accessMode = AccessMode.APPS,
            allowedPackages = rules?.sessionVisiblePackages(installedPackages).orEmpty(),
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
        studyDays.clear()
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
        checkpointRuntime(durable = true, forceSync = true)
        publish()
        evaluateNow()
    }

    /**
     * Effective entertainment scope. All-apps expands to the installed launchers minus the study
     * and unlimited lists. A package in more than one category follows study > unlimited >
     * entertainment.
     */
    private fun LocalRules.timedPackages(): Set<String> =
        if (allowsAllEntertainmentApps) {
            installedPackages - studyPackages - unlimitedPackages
        } else {
            allowedPackages - studyPackages - unlimitedPackages
        }

    /** True when at least one entertainment app can start; study/unlimited-only does not count. */
    private fun LocalRules.hasTimedApps(): Boolean =
        if (allowsAllEntertainmentApps) {
            installedPackages.any { it !in studyPackages && it !in unlimitedPackages }
        } else {
            (allowedPackages - studyPackages - unlimitedPackages).isNotEmpty()
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
