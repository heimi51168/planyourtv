package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizDocOrigin
import com.planyourtv.policy.quiz.QuizDocUpdatedBy
import com.planyourtv.policy.quiz.QuizLevel
import com.planyourtv.policy.quiz.QuizLevelOrigin
import com.planyourtv.policy.quiz.QuizSessionCodec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The local QCD store is the only writer of `quiz.doc.local`. These tests use
 * identity codecs because Robolectric has no AndroidKeyStore for QuizCrypto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizConfigDocumentStoreTest {
    private val now = 1_760_000_000_000L
    private val core = QuizConfigDocumentStore.core(
        encrypt = { it },
        decrypt = { it },
    )

    @Before
    fun setUp() {
        TvStore.init(RuntimeEnvironment.getApplication())
        clearState()
    }

    @After
    fun tearDown() {
        clearState()
    }

    @Test
    fun freshInstallCreatesLocalDocumentOnce() {
        val first = core.ensureLocalDocument(now)
        assertTrue(first is QuizDocLoadResult.Ok)
        val created = first as QuizDocLoadResult.Ok
        assertFalse(created.migrated)
        val document = created.document
        assertEquals(1L, document.revision)
        assertEquals(QuizDocOrigin.LOCAL, document.origin)
        assertEquals(QuizDocUpdatedBy.TV_LOCAL_EDIT, document.updatedBy)
        assertTrue(document.documentId.startsWith(QuizLocalConfigDocument.LOCAL_DOCUMENT_PREFIX))
        assertEquals(5, document.rules.questionCount)
        assertEquals(10, document.rules.feedback.extraHoldSec)
        assertTrue(document.etag.startsWith("sha256:"))
        assertTrue(document.levelsEtag.startsWith("sha256:"))
        // A fresh TV starts with the mixed-subject level first, then the
        // focused math default, so the child can pick either on the TV.
        val projectedLevels = document.localLevels()
        assertNotNull(projectedLevels)
        assertEquals(2, projectedLevels!!.size)
        assertEquals(QuizAnswerScope.SUBJECT_ALL, projectedLevels[0].scope.subject)
        assertEquals(QuizAnswerScope.TEXTBOOK_ALL, projectedLevels[0].scope.textbook)
        assertEquals("MIDTERM", projectedLevels[0].scope.scopeType)
        assertEquals("MATH", projectedLevels[1].scope.subject)
        // The install id is persisted and is the document id suffix.
        val installId = TvStore.getString(KEY_INSTALL_ID)
        assertNotNull(installId)
        assertEquals(QuizLocalConfigDocument.documentId(installId!!), document.documentId)

        val second = core.ensureLocalDocument(now + 1_000)
        assertTrue(second is QuizDocLoadResult.Ok)
        val reopened = second as QuizDocLoadResult.Ok
        assertFalse(reopened.migrated)
        assertEquals(document.revision, reopened.document.revision)
        assertEquals(document.etag, reopened.document.etag)
    }

    @Test
    fun legacyConfigAndLevelsMigrateWithoutRewritingOldKeys() {
        val legacyConfig = QuizSessionCodec.encodeConfig(
            QuizConfig(
                defaultTimeLimitSec = 90,
                minCorrectCount = 2,
                maxEarnedMinutes = 45,
                shuffleOptions = false,
                answerDisplaySec = 30,
                pullCount = 8,
                easyRewardMinutes = 2,
                mediumRewardMinutes = 3,
                hardRewardMinutes = 7,
                easyPenaltyMinutes = 1,
                mediumPenaltyMinutes = 2,
                hardPenaltyMinutes = 3,
                minUnlockMinutes = 4,
            ),
        ).toString()
        val legacyLevels = JSONArray().put(testLevel().encode()).toString()
        TvStore.putStringDurable(KEY_CHALLENGE_CONFIG, legacyConfig)
        TvStore.putStringDurable(KEY_ANSWER_SCOPES, legacyLevels)

        val result = core.ensureLocalDocument(now)
        assertTrue(result is QuizDocLoadResult.Ok)
        val migrated = result as QuizDocLoadResult.Ok
        assertTrue(migrated.migrated)
        val document = migrated.document
        assertEquals(QuizDocOrigin.LOCAL_MIGRATED, document.origin)
        assertEquals(QuizDocUpdatedBy.MIGRATION, document.updatedBy)
        assertEquals(90, document.rules.timeLimitSec)
        assertEquals(8, document.rules.questionCount)
        assertEquals(2, document.rules.legacy?.minCorrectCount)
        assertEquals(false, document.rules.shuffleOptions)
        // answerDisplaySec was a total hold; it must never become an extra hold.
        // The migrated config takes the product's 10-second feedback default.
        assertEquals(10, document.rules.feedback.extraHoldSec)
        assertEquals(30, document.extensions.getJSONObject("local").getInt("legacyAnswerDisplaySec"))
        assertEquals(120, document.reward.profiles.default!!.correctSeconds.easy)
        assertEquals(180, document.reward.profiles.default!!.correctSeconds.medium)
        assertEquals(420, document.reward.profiles.default!!.correctSeconds.hard)
        assertEquals(60, document.reward.profiles.default!!.penaltySeconds.easy)
        assertEquals(2_700, document.reward.profiles.default!!.maxEarnedSeconds)
        assertEquals(240, document.reward.profiles.default!!.minUnlockSeconds)
        assertEquals(1, document.levels.size)
        assertEquals(QuizLevelOrigin.LOCAL, document.levels[0].origin)
        assertTrue(document.levels[0].levelId.startsWith(QuizLocalConfigDocument.LOCAL_LEVEL_PREFIX))
        assertEquals("MIDTERM", document.levels[0].localScope?.scopeType)

        val projected = document.localLevels()
        assertNotNull(projected)
        assertEquals(1, projected!!.size)
        assertEquals(testLevel().scope.signature(), projected[0].scope.signature())

        // The one-way migration reads the old keys but must not rewrite them.
        assertEquals(legacyConfig, TvStore.getString(KEY_CHALLENGE_CONFIG))
        assertEquals(legacyLevels, TvStore.getString(KEY_ANSWER_SCOPES))
        assertNull(TvStore.getString(KEY_ANSWER_SCOPE))
        assertEquals("1", TvStore.getString(KEY_MIGRATED))

        val second = core.ensureLocalDocument(now + 1_000) as QuizDocLoadResult.Ok
        assertFalse(second.migrated)
        assertEquals(document.revision, second.document.revision)
    }

    @Test
    fun invalidLegacyConfigBlocksMigrationAndKeepsEveryOldByte() {
        val brokenConfig = "{not-json"
        TvStore.putStringDurable(KEY_CHALLENGE_CONFIG, brokenConfig)

        val result = core.ensureLocalDocument(now)
        assertTrue(result is QuizDocLoadResult.Unreadable)
        assertNull(TvStore.getString(KEY_LOCAL))
        assertEquals(brokenConfig, TvStore.getString(KEY_CHALLENGE_CONFIG))
        val diagnostic = JSONObject(TvStore.getString(KEY_UNREADABLE)!!)
        assertEquals("MIGRATION_INVALID", diagnostic.getString("code"))

        val second = core.ensureLocalDocument(now + 1_000)
        assertTrue(second is QuizDocLoadResult.Unreadable)
        assertNull(TvStore.getString(KEY_LOCAL))
    }

    @Test
    fun corruptStoredDocumentNeverFallsBackToLegacyKeys() {
        TvStore.putStringDurable(KEY_LOCAL, "plain:v1:broken")
        TvStore.putStringDurable(
            KEY_CHALLENGE_CONFIG,
            QuizSessionCodec.encodeConfig(QuizConfig()).toString(),
        )

        val result = core.ensureLocalDocument(now)
        assertTrue(result is QuizDocLoadResult.Unreadable)
        assertEquals("plain:v1:broken", TvStore.getString(KEY_LOCAL))
        val diagnostic = JSONObject(TvStore.getString(KEY_UNREADABLE)!!)
        assertEquals("UNREADABLE", diagnostic.getString("code"))
        assertTrue(diagnostic.getJSONArray("errors").length() > 0)
    }

    @Test
    fun recoverySaveWithoutBaseRevisionReplacesCorruptDocument() {
        val first = core.ensureLocalDocument(now) as QuizDocLoadResult.Ok
        val installId = first.document.documentId
            .removePrefix(QuizLocalConfigDocument.LOCAL_DOCUMENT_PREFIX)
        TvStore.putStringDurable(KEY_LOCAL, "plain:v1:broken")
        assertTrue(core.ensureLocalDocument(now) is QuizDocLoadResult.Unreadable)

        val replacement = QuizLocalConfigDocument.create(
            installId = installId,
            nowMs = now,
            config = QuizConfig(pullCount = 6),
            legacyAnswerDisplaySec = 3,
            levels = emptyList(),
            migrated = false,
        )
        val saved = core.saveLocal(
            replacement,
            baseRevision = null,
            updatedBy = QuizDocUpdatedBy.TV_LOCAL_EDIT,
            nowMs = now,
        )
        assertTrue(saved is QuizDocSaveResult.Ok)
        assertEquals(1L, (saved as QuizDocSaveResult.Ok).document.revision)

        val reopened = core.ensureLocalDocument(now + 1_000)
        assertTrue(reopened is QuizDocLoadResult.Ok)
        assertEquals(6, (reopened as QuizDocLoadResult.Ok).document.rules.questionCount)
        assertNull(TvStore.getString(KEY_UNREADABLE))
    }

    @Test
    fun staleBaseRevisionReportsConflictWithCurrentDocument() {
        val document = (core.ensureLocalDocument(now) as QuizDocLoadResult.Ok).document
        val firstSave = core.saveLocal(
            document.copy(enabled = false),
            baseRevision = document.revision,
            updatedBy = QuizDocUpdatedBy.TV_LOCAL_EDIT,
            nowMs = now + 1_000,
        )
        assertTrue(firstSave is QuizDocSaveResult.Ok)
        val stored = (firstSave as QuizDocSaveResult.Ok).document
        assertEquals(2L, stored.revision)
        assertFalse(stored.enabled)

        val stale = core.saveLocal(
            document.copy(enabled = true),
            baseRevision = document.revision,
            updatedBy = QuizDocUpdatedBy.TV_LOCAL_EDIT,
            nowMs = now + 2_000,
        )
        assertTrue(stale is QuizDocSaveResult.Conflict)
        val conflict = stale as QuizDocSaveResult.Conflict
        assertEquals(2L, conflict.current?.revision)
        assertFalse(conflict.current!!.enabled)
    }

    @Test
    fun saveRejectsForeignDocumentId() {
        core.ensureLocalDocument(now)
        val foreign = QuizLocalConfigDocument.create(
            installId = "another-install-id",
            nowMs = now,
            config = QuizConfig(),
            legacyAnswerDisplaySec = 3,
            levels = emptyList(),
            migrated = false,
        )
        val result = core.saveLocal(
            foreign,
            baseRevision = null,
            updatedBy = QuizDocUpdatedBy.TV_LOCAL_EDIT,
            nowMs = now,
        )
        assertTrue(result is QuizDocSaveResult.Invalid)
        assertEquals(
            "LOCAL_DOCUMENT_ID_REQUIRED",
            (result as QuizDocSaveResult.Invalid).issues[0].code,
        )
    }

    @Test
    fun markEnabledUpdatesTheDocumentOnlyWhenItChanges() {
        val document = (core.ensureLocalDocument(now) as QuizDocLoadResult.Ok).document
        assertTrue(document.enabled)

        val disabled = core.markEnabled(false, now + 1_000)
        assertTrue(disabled is QuizDocSaveResult.Ok)
        assertEquals(2L, (disabled as QuizDocSaveResult.Ok).document.revision)
        assertFalse(core.localDocument()!!.enabled)

        val noop = core.markEnabled(false, now + 2_000)
        assertTrue(noop is QuizDocSaveResult.Ok)
        assertEquals(2L, (noop as QuizDocSaveResult.Ok).document.revision)
    }

    @Test
    fun cloudActivationWritesEnvelopeAndSwitchesActiveDocument() {
        core.ensureLocalDocument(now)
        val cloud = cloudDocument(learnerId = 7, revision = 3)

        assertTrue(core.activateCloud(7, cloud))

        val active = core.activeDocument()
        assertNotNull(active)
        assertEquals(QuizDocOrigin.CLOUD, active!!.origin)
        assertEquals(3L, active.revision)
        assertEquals("quizcfg:family:3:member:9", active.documentId)

        val cached = core.cloudDocument(7)
        assertNotNull(cached)
        assertEquals(cloud.qbRevision, cached!!.qbRevision)
        assertEquals("CUSTOM", cached.levelsSource)
        assertTrue(cached.checkin.getBoolean("checkedToday"))
        assertEquals(5, cached.levelStatus.getJSONObject("cloud:11").getInt("availableCount"))
        assertEquals(listOf(7L), core.cloudLearnerIds())
    }

    @Test
    fun cloudCacheKeepsAtMostFiveLearnersAndEvictsLeastRecentlyUsed() {
        core.ensureLocalDocument(now)
        (1L..6L).forEach { learnerId ->
            assertTrue(core.activateCloud(learnerId, cloudDocument(learnerId, 1)))
        }

        assertEquals(listOf(6L, 5L, 4L, 3L, 2L), core.cloudLearnerIds())
        assertNull(core.cloudDocument(1))
        assertNotNull(core.cloudDocument(2))

        // 再次访问 3 会把它移到最前，淘汰最旧的 2。
        assertTrue(core.saveCloudDocument(3, cloudDocument(3, 2)))
        assertEquals(listOf(3L, 6L, 5L, 4L, 2L), core.cloudLearnerIds())
    }

    @Test
    fun brokenActiveDocumentNeverFallsBackToLocal() {
        core.ensureLocalDocument(now)
        TvStore.putStringDurable(KEY_ACTIVE, "{not-json")

        assertNull(core.activeDocument())
    }

    @Test
    fun syncStateRoundTripsAndCanBeCleared() {
        core.ensureLocalDocument(now)
        assertTrue(
            core.saveSyncState(
                7,
                QuizDocSyncState(
                    state = QuizConfigDocumentSync.STATE_BOUND_OFFLINE,
                    lastError = "NETWORK",
                    baseRevision = 4,
                    lastSyncAt = now,
                ),
            ),
        )

        val state = core.syncState(7)
        assertNotNull(state)
        assertEquals(QuizConfigDocumentSync.STATE_BOUND_OFFLINE, state!!.state)
        assertEquals("NETWORK", state.lastError)
        assertEquals(4L, state.baseRevision)
        assertEquals(now, state.lastSyncAt)

        assertTrue(core.clearSyncStates())
        assertNull(core.syncState(7))
    }

    @Test
    fun updateLevelsWritesLocalPrefixedLevelsAndProjectsThemBack() {
        core.ensureLocalDocument(now)
        val level = testLevel()

        val saved = core.updateLevels(listOf(level), now + 1_000)
        assertTrue(saved is QuizDocSaveResult.Ok)
        val document = (saved as QuizDocSaveResult.Ok).document
        assertEquals(1, document.levels.size)
        assertEquals("local:lvl-test-1", document.levels[0].levelId)
        assertEquals(QuizLevelOrigin.LOCAL, document.levels[0].origin)
        assertEquals(0, document.levels[0].sortNo)

        val projected = document.localLevels()
        assertNotNull(projected)
        assertEquals(listOf(level.id), projected!!.map { it.id })
        assertEquals(level.scope.signature(), projected[0].scope.signature())
    }

    private fun cloudDocument(learnerId: Long, revision: Long): CloudQuizDocument {
        val local = (core.ensureLocalDocument(now) as QuizDocLoadResult.Ok).document
        val document = local.copy(
            origin = QuizDocOrigin.CLOUD,
            documentId = "quizcfg:family:3:member:9",
            revision = revision,
            levels = emptyList(),
            levelsEtag = "sha256:" + "a".repeat(64),
            etag = "sha256:" + "b".repeat(64),
        )
        return CloudQuizDocument(
            document = document,
            fetchedAt = now,
            qbRevision = "sha256:" + "c".repeat(64),
            levelsSource = "CUSTOM",
            checkin = JSONObject().put("checkedToday", true),
            levelStatus = JSONObject().put(
                "cloud:11",
                JSONObject().put("qbLevelId", 11).put("available", true)
                    .put("availableCount", 5).put("questionCount", 5),
            ),
            rulesHttpEtag = "sha256:" + "d".repeat(64),
            rulesPart = JSONObject().put("schemaVersion", 1),
        )
    }

    private fun testLevel(): QuizLevel {
        val scope = requireNotNull(
            QuizAnswerScope.create(
                grade = 3,
                semester = 1,
                subject = "MATH",
                textbook = "XINANDA",
                scopeType = "MIDTERM",
                unitNo = null,
                difficulty = "EASY",
            ),
        )
        return requireNotNull(QuizLevel.create(scope, name = "期末", id = "lvl-test-1"))
    }

    private fun clearState() {
        listOf(
            KEY_LOCAL,
            KEY_ACTIVE,
            KEY_CLOUD_INDEX,
            KEY_SYNC_INDEX,
            KEY_INSTALL_ID,
            KEY_MIGRATED,
            KEY_UNREADABLE,
            KEY_CHALLENGE_CONFIG,
            KEY_ANSWER_SCOPES,
            KEY_ANSWER_SCOPE,
            KEY_GENERATION_PREFS,
        ).forEach { TvStore.removeDurable(it) }
        (1L..8L).forEach { learnerId ->
            TvStore.removeDurable("quiz.doc.cloud.$learnerId")
            TvStore.removeDurable("quiz.doc.sync.$learnerId")
        }
    }

    private companion object {
        private const val KEY_LOCAL = "quiz.doc.local"
        private const val KEY_ACTIVE = "quiz.doc.active"
        private const val KEY_CLOUD_INDEX = "quiz.doc.cloud.index"
        private const val KEY_SYNC_INDEX = "quiz.doc.sync.index"
        private const val KEY_INSTALL_ID = "quiz.doc.installId"
        private const val KEY_MIGRATED = "quiz.feedbackVoiceHoldMigrated.v1"
        private const val KEY_UNREADABLE = "quiz.doc.unreadable.v1"
        private const val KEY_CHALLENGE_CONFIG = "quiz.challengeConfig"
        private const val KEY_ANSWER_SCOPES = "quiz.answerScopes"
        private const val KEY_ANSWER_SCOPE = "quiz.answerScope"
        private const val KEY_GENERATION_PREFS = "quiz.generationPrefs"
    }
}
