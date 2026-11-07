package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizPendingKind
import com.planyourtv.policy.quiz.QuizSource
import com.planyourtv.policy.quiz.QuizSourceConfig
import com.planyourtv.policy.quiz.QuizSyncState
import com.planyourtv.policy.quiz.RemoteQuizConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class QuizConfigSyncTest {
    private val now = 1_760_000_000_000L

    private fun localConfig() = QuizConfig(
        defaultTimeLimitSec = 45,
        minutesPerPoint = 4,
        minCorrectCount = 2,
        maxEarnedMinutes = 30,
        answerDisplaySec = 20,
        shuffleOptions = false,
        pullCount = 6,
        minutesPerQuestion = 4,
        pointsPerQuestion = 1,
    )

    private fun remote(
        version: Long,
        source: QuizSource = QuizSource.LOCAL,
        enabled: Boolean = true,
        pullCount: Int = 8,
        minutesPerQuestion: Int = 7,
        defaultTimeLimitSec: Int = 50,
        answerDisplaySec: Int = 25,
        shuffleOptions: Boolean = true,
        maxEarnedMinutes: Int = 40,
        localVersion: Long = 0L,
    ) = RemoteQuizConfig(
        deviceNo = "tv-1",
        familyId = 12L,
        enabled = enabled,
        source = source,
        pullCount = pullCount,
        minutesPerQuestion = minutesPerQuestion,
        pointsPerQuestion = 1,
        defaultTimeLimitSec = defaultTimeLimitSec,
        answerDisplaySec = answerDisplaySec,
        shuffleOptions = shuffleOptions,
        maxEarnedMinutes = maxEarnedMinutes,
        scope = null,
        scopeProvided = false,
        sessionConfig = null,
        version = version,
        appliedVersion = 0L,
        localVersion = localVersion,
        localUploaded = false,
    )

    private fun seedSource(configVersion: Long, localVersion: Long, source: QuizSource = QuizSource.LOCAL) {
        QuizSessionStore.saveSourceConfig(
            QuizSourceConfig(
                enabled = true,
                source = source,
                configVersion = configVersion,
                localVersion = localVersion,
                updatedAt = now,
            ),
        )
    }

    private fun seedSync(state: QuizSyncState) {
        QuizSessionStore.saveSyncState(state)
    }

    @Before
    fun setUp() {
        assertTrue(TvStore.init(RuntimeEnvironment.getApplication()))
        QuizSessionStore.clearSession()
        QuizSessionStore.setEnabled(true)
        assertTrue(QuizSessionStore.saveChallengeConfig(localConfig()))
        seedSource(configVersion = 0, localVersion = 0)
        seedSync(QuizSyncState())
    }

    @Test
    fun bindingReportUploadsMinutesAndAnInternalPointOfOne() {
        seedSource(configVersion = 4, localVersion = 3)
        val payload = QuizConfigSync.reportPayload()
        // Binding alone selects CLOUD; the backend normalizes this field.
        assertEquals("CLOUD", payload.getString("source"))
        assertEquals(6, payload.getInt("pullCount"))
        assertEquals(4, payload.getInt("minutesPerQuestion"))
        // Internal compatibility field only; no UI ever renders it.
        assertEquals(1, payload.getInt("pointsPerQuestion"))
        assertEquals(45, payload.getInt("defaultTimeLimitSec"))
        assertEquals(20, payload.getInt("answerDisplaySec"))
        assertEquals(false, payload.getBoolean("shuffleOptions"))
        assertEquals(30, payload.getInt("maxEarnedMinutes"))
        assertEquals(3L, payload.getLong("localVersion"))
        assertTrue(payload.has("sessionConfig"))
    }

    @Test
    fun newerLocalConfigIsAppliedAndClearsTheSyncState() {
        seedSource(configVersion = 2, localVersion = 2)
        seedSync(QuizSyncState(pendingLocal = false, updatedAt = now))

        val result = QuizConfigSync.applyRemote(remote(version = 3), now)
        assertEquals(QuizConfigSync.ApplyResult.Applied, result)

        val config = QuizSessionStore.loadChallengeConfig()!!
        assertEquals(8, config.pullCount)
        assertEquals(7, config.minutesPerQuestion)
        assertEquals(7, config.minutesPerPoint)
        assertEquals(50, config.defaultTimeLimitSec)
        assertEquals(25, config.answerDisplaySec)
        assertTrue(config.shuffleOptions)
        assertEquals(40, config.maxEarnedMinutes)
        // Fields owned by the local editor survive a cloud apply.
        assertEquals(2, config.minCorrectCount)

        val source = QuizSessionStore.loadSourceConfig()
        assertEquals(3L, source.configVersion)
        assertEquals(QuizSource.LOCAL, source.source)
        val sync = QuizSessionStore.loadSyncState()
        assertFalse(sync.pendingLocal)
        assertFalse(sync.conflict)
        assertEquals(QuizPendingKind.NONE, sync.pendingKind)
    }

    @Test
    fun sameOrOlderVersionNeverStepsBack() {
        seedSource(configVersion = 5, localVersion = 5)
        assertEquals(
            QuizConfigSync.ApplyResult.Ignored,
            QuizConfigSync.applyRemote(remote(version = 5), now),
        )
        assertEquals(
            QuizConfigSync.ApplyResult.Ignored,
            QuizConfigSync.applyRemote(remote(version = 4), now),
        )
        assertEquals(6, QuizSessionStore.loadChallengeConfig()!!.pullCount)
        assertEquals(5L, QuizSessionStore.loadSourceConfig().configVersion)
    }

    @Test
    fun pendingLocalEditIsAConflictInsteadOfBeingOverwritten() {
        seedSource(configVersion = 2, localVersion = 5)
        seedSync(
            QuizSyncState(
                pendingLocal = true,
                pendingKind = QuizPendingKind.EDIT,
                lastError = "",
                updatedAt = now,
            ),
        )

        val result = QuizConfigSync.applyRemote(remote(version = 3), now)
        assertEquals(QuizConfigSync.ApplyResult.Conflict, result)

        assertEquals(6, QuizSessionStore.loadChallengeConfig()!!.pullCount)
        assertEquals(2L, QuizSessionStore.loadSourceConfig().configVersion)
        val sync = QuizSessionStore.loadSyncState()
        assertTrue(sync.conflict)
        assertTrue(sync.pendingLocal)
        assertEquals("CONFLICT", sync.lastError)
    }

    @Test
    fun aFreshBindingAcceptsTheParentManagedCloudConfig() {
        seedSource(configVersion = 0, localVersion = 1)
        seedSync(
            QuizSyncState(
                pendingLocal = true,
                pendingKind = QuizPendingKind.BINDING,
                updatedAt = now,
            ),
        )

        val result = QuizConfigSync.applyRemote(remote(version = 1, source = QuizSource.CLOUD), now)
        assertEquals(QuizConfigSync.ApplyResult.Applied, result)

        val source = QuizSessionStore.loadSourceConfig()
        assertEquals(QuizSource.CLOUD, source.source)
        assertEquals(1L, source.configVersion)
        val sync = QuizSessionStore.loadSyncState()
        assertFalse(sync.pendingLocal)
        assertFalse(sync.conflict)
        assertEquals(8, QuizSessionStore.loadChallengeConfig()!!.pullCount)
    }

    @Test
    fun cloudSourceIsAppliedNowThatRemoteQuestionsShip() {
        seedSource(configVersion = 2, localVersion = 2)
        val result = QuizConfigSync.applyRemote(remote(version = 3, source = QuizSource.CLOUD), now)
        assertEquals(QuizConfigSync.ApplyResult.Applied, result)

        assertEquals(8, QuizSessionStore.loadChallengeConfig()!!.pullCount)
        val source = QuizSessionStore.loadSourceConfig()
        assertEquals(3L, source.configVersion)
        assertEquals(QuizSource.CLOUD, source.source)
        assertEquals("", QuizSessionStore.loadSyncState().lastError)
    }

    @Test
    fun unboundResetsTheCloudOriginButKeepsTheConfiguredChallenge() {
        seedSource(configVersion = 4, localVersion = 6, source = QuizSource.CLOUD)
        QuizSessionStore.setEnabled(false)
        seedSync(
            QuizSyncState(
                pendingLocal = true,
                conflict = true,
                pendingKind = QuizPendingKind.EDIT,
                lastError = "CONFLICT",
                updatedAt = now,
            ),
        )

        QuizConfigSync.onUnbound()

        val source = QuizSessionStore.loadSourceConfig()
        assertEquals(QuizSource.LOCAL, source.source)
        assertEquals(0L, source.configVersion)
        assertEquals(6L, source.localVersion)
        assertFalse(source.enabled)
        assertFalse(QuizSessionStore.enabled())
        assertEquals(6, QuizSessionStore.loadChallengeConfig()!!.pullCount)
        val sync = QuizSessionStore.loadSyncState()
        assertFalse(sync.pendingLocal)
        assertFalse(sync.conflict)
        assertEquals(QuizPendingKind.NONE, sync.pendingKind)
        assertEquals("", sync.lastError)
    }

    @Test
    fun aConfirmedBindingQueuesTheChallengeReport() {
        seedSource(configVersion = 9, localVersion = 3, source = QuizSource.CLOUD)
        QuizSessionStore.setEnabled(false)

        QuizConfigSync.onBindingConfirmed("tv-1")

        val source = QuizSessionStore.loadSourceConfig()
        assertEquals(QuizSource.CLOUD, source.source)
        assertEquals(0L, source.configVersion)
        assertEquals(3L, source.localVersion)
        assertFalse(source.enabled)
        val sync = QuizSessionStore.loadSyncState()
        assertTrue(sync.pendingLocal)
        assertFalse(sync.conflict)
        assertEquals(QuizPendingKind.BINDING, sync.pendingKind)
        assertEquals(6, QuizSessionStore.loadChallengeConfig()!!.pullCount)
    }
}
