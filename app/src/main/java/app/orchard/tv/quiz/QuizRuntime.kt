package app.orchard.tv.quiz

import app.orchard.tv.policy.PolicyModeCoordinator
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizLevel
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
     * [collectionId] and [tagName] instead. Exactly one of the two is set.
     */
    data class LevelState(
        val id: String,
        val name: String,
        val scope: QuizAnswerScope? = null,
        val collectionId: String? = null,
        val tagName: String = "",
        val available: Int,
    ) {
        val isCustom: Boolean get() = collectionId != null
    }

    private val EXPIRY_BOUND_STATUSES = setOf(
        QuizSessionStatus.DRAFT,
        QuizSessionStatus.READY,
    )

    data class UiState(
        val loaded: Boolean = false,
        val enabled: Boolean = true,
        val session: QuizSession? = null,
        val providerConfigured: Boolean = false,
        val consentAccepted: Boolean = false,
        val unreadable: Boolean = false,
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
        val enabled = QuizSessionStore.enabled()
        val sourceConfig = QuizSessionStore.loadSourceConfig()
        val defaultConfig = QuizSessionStore.loadChallengeConfig() ?: QuizConfig()
        val bankCounts = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE)
        val activeScope = session?.let {
            QuizRecovery.scopeFor(it, PolicyModeCoordinator.state.value.deviceNo)
        }
        val activeAvailable = activeScope?.let { QuizQuestionCache.availableCount(it) } ?: 0
        val courseLevels = QuizAnswerScopeStore.levels().map { level: QuizLevel ->
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
        // Custom levels always come after course levels. Each draws only from
        // its own tag; they never gain a curriculum scope.
        val tagNames = QuizCollectionStore.collections().associate { it.id to it.name }
        val customLevels = QuizCollectionStore.customLevels().map { level ->
            LevelState(
                id = level.id,
                name = level.name,
                collectionId = level.collectionId,
                tagName = tagNames[level.collectionId].orEmpty(),
                available = QuizQuestionCache.availableCollectionCount(level.collectionId),
            )
        }
        val answerLevels = courseLevels + customLevels
        // This legacy field always means a curriculum scope; a custom level in
        // front must not turn it into null for cloud/reporting callers.
        val answerScope = courseLevels.firstOrNull()?.scope
        // Keep the start entry visible while any level has questions, even if
        // none can fill a full round: the start card blocks a short level and
        // leaves the QR as the parent's recovery path.
        val playable = answerLevels.maxOfOrNull { it.available } ?: 0
        // v5 has no minimum-correct gate, but an explicitly picked level must
        // supply the configured round. Only the empty page is QR-only.
        val bankState = when {
            !enabled -> "disabled"
            playable <= 0 -> "empty"
            else -> "ready"
        }
        val next = UiState(
            loaded = true,
            enabled = enabled,
            session = session,
            providerConfigured = QuizSessionStore.providerConfigured(),
            consentAccepted = QuizSessionStore.consentAccepted(),
            unreadable = unreadable,
            cacheAvailable = bankCounts.unused,
            sourceConfig = sourceConfig,
            bankCounts = bankCounts,
            bankState = bankState,
            localBankActive = sourceConfig.source != QuizSource.CLOUD,
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
}
