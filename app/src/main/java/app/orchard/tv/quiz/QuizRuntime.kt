package app.orchard.tv.quiz

import app.orchard.tv.policy.PolicyModeCoordinator
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizConfigDocument
import com.planyourtv.policy.quiz.QuizLevel
import com.planyourtv.policy.quiz.QuizRewardProfile
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionStatus
import com.planyourtv.policy.quiz.QuizSource
import com.planyourtv.policy.quiz.QuizSourceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide view of the single quiz record. It owns no timer and no question
 * bank; it only maps the durable session to what TV screens need. All methods
 * may block on Keystore/storage, so callers run them off the frame thread.
 */
object QuizRuntime {
    /**
     * One parent-designed level plus how many bank questions it can use now.
     * A course level carries [scope]; a custom-tag level carries
     * [collectionId] and [tagName] instead. A wrong-review level has neither
     * and is marked by [wrongReview].
     */
    data class LevelState(
        val id: String,
        val name: String,
        val scope: QuizAnswerScope? = null,
        val collectionId: String? = null,
        val tagName: String = "",
        val available: Int,
        /** Set only for cloud levels; the QB level id used by `serve-next`. Never zero. */
        val qbLevelId: Long? = null,
        /**
         * Questions this level pulls per round. Cloud levels carry the parent's
         * per-level count from the QCD; local levels keep the shared round size.
         */
        val questionCount: Int? = null,
        /** The optional local level that re-asks every wrong answer in the bank. */
        val wrongReview: Boolean = false,
    ) {
        val isCustom: Boolean get() = collectionId != null
        val isCloud: Boolean get() = qbLevelId != null
    }

    private val EXPIRY_BOUND_STATUSES = setOf(
        QuizSessionStatus.DRAFT,
        QuizSessionStatus.READY,
    )

    /** Same statuses QuizSettings treats as an ongoing round that must not be disturbed. */
    private val ACTIVE_ROUND_STATUSES = setOf(
        QuizSessionStatus.RUNNING,
        QuizSessionStatus.SETTLING,
        QuizSessionStatus.STARTING,
        QuizSessionStatus.START_FAILED,
    )

    data class UiState(
        val loaded: Boolean = false,
        val enabled: Boolean = true,
        /**
         * Whether the lock desktop shows its dedicated quiz tile. Local mode
         * reads the display-only switch; cloud keeps the legacy feature flag.
         */
        val lockEntryVisible: Boolean = false,
        val session: QuizSession? = null,
        val providerConfigured: Boolean = false,
        val consentAccepted: Boolean = false,
        /** The stored session itself cannot be decoded. */
        val unreadable: Boolean = false,
        /**
         * The local QCD is missing or failed validation. Kept separate from
         * [unreadable] so a corrupt document cannot hide a running round; it
         * only blocks starting a new one.
         */
        val documentUnreadable: Boolean = false,
        /**
         * Bound TV: the active cloud QCD could not be projected into a round.
         * The code is internal; screens map it to human copy. Null in local mode
         * and when the plan is ready.
         */
        val cloudPlanError: String? = null,
        val cloudCheckinBlocked: Boolean = false,
        val cloudCheckinMessage: String? = null,
        /**
         * Exact cloud payout frozen by the active QCD, for the start card only.
         * The executed payout always comes from the frozen READY session.
         */
        val cloudRewardProfile: QuizRewardProfile? = null,
        val cacheAvailable: Int = 0,
        val sourceConfig: QuizSourceConfig = QuizSourceConfig(),
        val bankCounts: QuizQuestionCache.BankCounts = QuizQuestionCache.BankCounts(),
        val bankState: String = "empty",
        val localBankActive: Boolean = true,
        val defaultChallengeConfig: QuizConfig = QuizConfig(),
        /** Ordered levels; the TV shows exactly this list to the child. */
        val answerLevels: List<LevelState> = emptyList(),
        /** First level, kept for the cloud/legacy single-scope surfaces. */
        val answerScope: QuizAnswerScope? = null,
        val activeScope: String? = null,
        val activeScopeAvailable: Int = 0,
    ) {
        val bankUnused: Int get() = bankCounts.unused
        val bankTotal: Int get() = bankCounts.total
        val sessionState: String
            get() = when {
                unreadable -> "unreadable"
                session == null -> "none"
                // Only an unpublished session is bound by expiresAt. A RUNNING round
                // is governed by its per-question deadline, and a short SETTLING/
                // START_FAILED record must stay readable until the parent clears it.
                session.status in EXPIRY_BOUND_STATUSES &&
                    session.expiresAt <= System.currentTimeMillis() -> "expired"
                else -> session.status.name.lowercase()
            }

        val entry: String
            get() = when (sessionState) {
                // An unreadable record still needs the setup entry: the parent can
                // only reset it through the quiz page. An expired record does too;
                // opening the page clears it and shows the setup QR.
                "draft", "none", "unreadable", "expired" -> "setup"
                "ready" -> "ready"
                "running" -> "running"
                "settling" -> "settling"
                "starting" -> "starting"
                "start_failed" -> "start_failed"
                else -> "blocked"
            }

        val questionCount: Int
            get() = session?.items?.size ?: 0

        /**
         * For a v4 READY session the phone published a pool; the engine will
         * reserve only [QuizConfig.pullCount] of it. Legacy sessions keep their
         * exact published list. This is display-only and never drives judging.
         */
        val readyQuestionCount: Int
            get() {
                val current = session ?: return 0
                if (current.originSchemaVersion < 4 || current.status != QuizSessionStatus.READY) {
                    return current.items.size
                }
                return current.items.size.coerceAtMost(
                    current.config.pullCount.coerceAtMost(activeScopeAvailable),
                )
            }

        val challengeConfig: QuizConfig
            get() = session?.config ?: defaultChallengeConfig

        val source: QuizSource
            get() = sourceConfig.source
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Re-reads the single record. An expired DRAFT/READY is kept until the child
     * opens the entry, which clears it and shows the normal setup row.
     */
    @Synchronized
    fun refresh(): UiState {
        val session = QuizSessionStore.loadSession()
        val unreadable = session == null && QuizSessionStore.hasStoredSession()
        val localMode = PolicyModeCoordinator.mode == PolicyMode.LOCAL
        var localDocument: QuizConfigDocument? = null
        var documentUnreadable = false
        if (localMode) {
            when (val loaded = QuizConfigDocumentStore.ensureLocalDocument()) {
                is QuizDocLoadResult.Ok -> localDocument = loaded.document
                is QuizDocLoadResult.Unreadable, QuizDocLoadResult.Storage ->
                    documentUnreadable = true
            }
        }
        // Bound TV: the cloud QCD is the only execution source. A missing,
        // corrupt or unsupported active document is an explicit error; it never
        // falls back to the local document or the legacy device config.
        var cloudPlan: QuizCloudRoundPlan? = null
        var cloudPlanError: String? = null
        if (!localMode) {
            when (val planned = QuizCloudRoundPlanner.plan()) {
                is QuizCloudPlanResult.Ready -> cloudPlan = planned.plan
                is QuizCloudPlanResult.Rejected -> cloudPlanError = planned.code
            }
        }
        // Local mode has no quiz feature switch: a legacy document that still
        // says enabled=false is ignored, and the child can always use the quiz
        // entries. The dedicated lock tile is governed by the display switch.
        val enabled = if (localMode) {
            true
        } else {
            cloudPlan?.document?.enabled ?: false
        }
        val lockEntryVisible = if (localMode) {
            QuizSessionStore.lockEntryVisible()
        } else {
            enabled
        }
        val sourceConfig = QuizSessionStore.loadSourceConfig()
        val defaultConfig = if (localMode) {
            localDocument?.toLegacyConfig() ?: QuizConfig()
        } else {
            cloudPlan?.config ?: QuizConfig()
        }
        val bankCounts = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE)
        val activeScope = session?.let {
            QuizRecovery.scopeFor(it, PolicyModeCoordinator.state.value.deviceNo)
        }
        val activeAvailable = activeScope?.let { QuizQuestionCache.availableCount(it) } ?: 0
        val documentLevels = localDocument?.localLevels()
        if (localMode && localDocument != null && documentLevels == null) {
            documentUnreadable = true
        }
        val courseLevels: List<LevelState> = if (localMode) {
            (documentLevels ?: emptyList()).map { level: QuizLevel ->
                LevelState(
                    id = level.id,
                    name = level.name,
                    scope = level.scope,
                    available = QuizQuestionCache.availableCount(
                        QuizQuestionCache.LOCAL_SCOPE,
                        answerScope = level.scope,
                    ),
                )
            }
        } else {
            cloudPlan?.levels?.map { level ->
                LevelState(
                    id = level.id,
                    name = level.name,
                    available = level.available,
                    qbLevelId = level.qbLevelId,
                    questionCount = level.questionCount,
                )
            } ?: emptyList()
        }
        // Custom levels always come after course levels. Each draws only from
        // its own tag; they never gain a curriculum scope. A cloud document
        // cannot carry them, so querying the local store here would mix modes.
        val customLevels = if (localMode) {
            val tagNames = QuizCollectionStore.collections().associate { it.id to it.name }
            QuizCollectionStore.customLevels().map { level ->
                LevelState(
                    id = level.id,
                    name = level.name,
                    collectionId = level.collectionId,
                    tagName = tagNames[level.collectionId].orEmpty(),
                    available = QuizQuestionCache.availableCollectionCount(level.collectionId),
                )
            }
        } else {
            emptyList()
        }
        // Wrong review always comes last; it has no curriculum scope and no
        // custom tag. An empty name falls back to the localized TV title.
        val wrongLevels = if (localMode) {
            QuizWrongReviewLevelStore.level()?.let { level ->
                listOf(
                    LevelState(
                        id = level.id,
                        name = level.name,
                        available = QuizQuestionCache.wrongReviewCount(),
                        wrongReview = true,
                    ),
                )
            }.orEmpty()
        } else {
            emptyList()
        }
        val answerLevels = courseLevels + customLevels + wrongLevels
        // This legacy field always means a curriculum scope; a custom level in
        // front must not turn it into null for cloud/reporting callers.
        val answerScope = courseLevels.firstOrNull()?.scope
        // A wrong book past its week is practice in itself: the start still has to be
        // reachable when no level has unused questions left (see WrongBookPolicy), so
        // its count joins what counts as playable.
        val wrongBook = if (localMode) {
            runCatching { WrongBookPolicy.state() }.getOrNull()
        } else {
            null
        }
        val wrongBookPlays = wrongBook?.count?.takeIf { it > 0 } ?: 0
        val playable = maxOf(answerLevels.maxOfOrNull { it.available } ?: 0, wrongBookPlays)
        val bankState = when {
            !localMode && cloudPlanError != null -> "blocked"
            localMode && documentUnreadable -> "blocked"
            !localMode && !enabled -> "disabled"
            // Local mode hides the start page when no level has questions. The
            // cloud start page stays visible whenever the parent configured
            // levels, so an unavailable reason can be shown instead of the QR.
            localMode && playable <= 0 -> "empty"
            !localMode && answerLevels.isEmpty() -> "empty"
            else -> "ready"
        }
        val next = UiState(
            loaded = true,
            enabled = enabled,
            lockEntryVisible = lockEntryVisible,
            session = session,
            providerConfigured = QuizSessionStore.providerConfigured(),
            consentAccepted = QuizSessionStore.consentAccepted(),
            unreadable = unreadable,
            documentUnreadable = documentUnreadable,
            cloudPlanError = cloudPlanError,
            cloudCheckinBlocked = cloudPlan?.checkinBlocked ?: false,
            cloudCheckinMessage = cloudPlan?.checkinMessage,
            cloudRewardProfile = cloudPlan?.profile,
            cacheAvailable = bankCounts.unused,
            sourceConfig = sourceConfig,
            bankCounts = bankCounts,
            bankState = bankState,
            localBankActive = localMode,
            defaultChallengeConfig = defaultConfig,
            answerLevels = answerLevels,
            answerScope = answerScope,
            activeScope = activeScope,
            activeScopeAvailable = activeAvailable,
        )
        _state.value = next
        return next
    }

    /** A cheap snapshot for callers that just saved a session and already know its values. */
    fun publishState(next: UiState) {
        _state.value = next
    }

    /** True while a round may be on screen or still settling; web unlock must not interrupt it. */
    fun isRoundActive(): Boolean {
        val status = refresh().session?.status ?: return false
        return status in ACTIVE_ROUND_STATUSES
    }
}
