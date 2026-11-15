package app.orchard.tv.quiz

import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizDocOrigin
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 云端版本决策是纯逻辑：网络失败/写入失败都不应该走到这里。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizConfigDocumentSyncDecisionTest {
    private val now = 1_760_000_000_000L
    private val rulesEtag = "sha256:" + "d".repeat(64)
    private val levelsEtagA = "sha256:" + "a".repeat(64)
    private val levelsEtagB = "sha256:" + "b".repeat(64)

    @Before
    fun setUp() {
        TvStore.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun firstRemoteDocumentApplies() {
        assertEquals(
            RevisionDecision.Apply,
            QuizConfigDocumentSync.decide(null, cloud(revision = 1)),
        )
    }

    @Test
    fun newerRevisionAppliesAndOlderRevisionIsIgnored() {
        val previous = cloud(revision = 3)
        assertEquals(
            RevisionDecision.Apply,
            QuizConfigDocumentSync.decide(previous, cloud(revision = 4)),
        )
        assertEquals(
            RevisionDecision.Stale,
            QuizConfigDocumentSync.decide(previous, cloud(revision = 2)),
        )
    }

    @Test
    fun identicalDocumentIsUnchanged() {
        assertEquals(
            RevisionDecision.Unchanged,
            QuizConfigDocumentSync.decide(cloud(revision = 3), cloud(revision = 3)),
        )
    }

    @Test
    fun qbOnlyLevelsChangeUnderSameRevisionApplies() {
        val previous = cloud(revision = 3, levelsEtag = levelsEtagA, etag = "sha256:" + "1".repeat(64))
        val candidate = cloud(revision = 3, levelsEtag = levelsEtagB, etag = "sha256:" + "2".repeat(64))

        assertEquals(
            RevisionDecision.Apply,
            QuizConfigDocumentSync.decide(previous, candidate),
        )
    }

    @Test
    fun rulesChangeUnderReusedRevisionIsRejected() {
        val previous = cloud(revision = 3, rulesEtag = rulesEtag)
        val candidate = cloud(
            revision = 3,
            rulesEtag = "sha256:" + "e".repeat(64),
        )

        assertEquals(
            RevisionDecision.Reuse,
            QuizConfigDocumentSync.decide(previous, candidate),
        )
    }

    @Test
    fun differentDocumentIdUnderSameRevisionIsRejected() {
        val previous = cloud(revision = 3, documentId = "quizcfg:family:3:member:9")
        val candidate = cloud(revision = 3, documentId = "quizcfg:family:4:member:10")

        assertEquals(
            RevisionDecision.Reuse,
            QuizConfigDocumentSync.decide(previous, candidate),
        )
    }

    private fun cloud(
        revision: Long,
        documentId: String = "quizcfg:family:3:member:9",
        levelsEtag: String = levelsEtagA,
        etag: String = "sha256:" + "1".repeat(64),
        rulesEtag: String = this.rulesEtag,
    ): CloudQuizDocument {
        val local = QuizLocalConfigDocument.create(
            installId = "install-id-test",
            nowMs = now,
            config = QuizConfig(),
            legacyAnswerDisplaySec = 3,
            levels = emptyList(),
            migrated = false,
        )
        val document = local.copy(
            origin = QuizDocOrigin.CLOUD,
            documentId = documentId,
            revision = revision,
            levels = emptyList(),
            levelsEtag = levelsEtag,
            etag = etag,
        )
        return CloudQuizDocument(
            document = document,
            fetchedAt = now,
            qbRevision = "sha256:" + "c".repeat(64),
            levelsSource = "CUSTOM",
            checkin = JSONObject(),
            levelStatus = JSONObject(),
            rulesHttpEtag = rulesEtag,
            rulesPart = JSONObject().put("schemaVersion", 1),
        )
    }
}
