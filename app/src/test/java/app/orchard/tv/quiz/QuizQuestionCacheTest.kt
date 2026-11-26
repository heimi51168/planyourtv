package app.orchard.tv.quiz

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.planyourtv.policy.quiz.CourseOrigin
import com.planyourtv.policy.quiz.QuizAnswerKind
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizLevelPackage
import com.planyourtv.policy.quiz.QuizPackageSource
import com.planyourtv.policy.quiz.QuizPresetBank
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionCodec
import com.planyourtv.policy.quiz.QuizSessionStatus
import com.planyourtv.policy.quiz.QuizTypeFilter
import com.planyourtv.policy.quiz.QuizVisual
import com.planyourtv.policy.quiz.QuizRichVisual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Random
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class QuizQuestionCacheTest {
    private val now = 1_760_000_000_000L
    private val testDb = "quiz_cache_test.db"
    // Production capacity is 100,000; filling that in a unit test only tests
    // SQLite endurance. The boundary behavior is independent of the number.
    private val smallCapacity = 25

    private fun item(index: Int) = QuizItem(
        questionId = "q_000000$index",
        prompt = "Question $index",
        options = listOf("A", "B", "C", "D"),
        answerIndex = 1,
        subject = "math",
        grade = "g1",
        difficulty = "easy",
        points = 1,
        timeLimitSec = 60,
        recommendedTimeSec = 60,
    )

    private fun bulkItem(index: Int) = item(index).copy(
        questionId = "q_bulk_%04d".format(index),
        prompt = "Bulk question $index",
    )

    private fun visual(seed: Char) = QuizVisual(
        rich = QuizRichVisual(
            assetId = "qb/figure",
            sha256 = "sha256:" + seed.toString().repeat(64),
            width = 960,
            height = 640,
        ),
    )

    private fun scopedItem(index: Int, grade: Int = 4) = item(index).copy(
        scopeGrade = grade,
        scopeSemester = 1,
        scopeSubject = "MATH",
        scopeTextbook = "RENJIAO",
        scopeType = "UNIT",
        scopeUnitNo = 3,
    )

    private fun midtermItem(index: Int, subject: String, textbook: String) =
        scopedItem(index, grade = 4).copy(
            scopeSubject = subject,
            scopeTextbook = textbook,
            scopeType = "MIDTERM",
            scopeUnitNo = null,
            difficulty = "hard",
        )

    private fun courseScope(subject: String, unitNo: Int) = QuizAnswerScope.create(
        grade = 4,
        semester = 1,
        subject = subject,
        textbook = "RENJIAO",
        scopeType = "UNIT",
        unitNo = unitNo,
        difficulty = null,
    )!!

    private fun itemIn(index: Int, subject: String, unitNo: Int) =
        scopedItem(index).copy(scopeSubject = subject, scopeUnitNo = unitNo)

    private fun multipleItemIn(index: Int, subject: String, unitNo: Int) =
        itemIn(index, subject, unitNo).copy(
            answerKind = QuizAnswerKind.MULTIPLE,
            answerIndices = listOf(0, 2),
        )

    /** Forces the round-robin source offset and every draw offset. */
    private class FixedRandom(private val value: Int) : Random() {
        override fun nextInt(bound: Int): Int = if (bound <= 0) 0 else value % bound
    }

    private fun session(id: String, count: Int) = QuizSession(
        id = id,
        source = "web",
        createdAt = now,
        updatedAt = now,
        expiresAt = now + 24 * 60 * 60 * 1000L,
        status = QuizSessionStatus.READY,
        language = "zh",
        config = QuizConfig(pullCount = count),
        items = (1..count).map(::item),
    )

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(QuizQuestionCache.initForTest(context, testDb))
        QuizQuestionCache.clearBank(
            QuizQuestionCache.LOCAL_SCOPE,
            QuizQuestionCache.ClearMode.ALL,
            QuizQuestionCache.BankSource.ALL,
        )
        QuizQuestionCache.clearCloudScopes()
        QuizQuestionCache.releaseOrphans(null)
    }

    @Test
    fun replacePublishesEveryItemAsAvailable() {
        assertTrue(QuizQuestionCache.replaceLocalSession(session("sess_a", 3), now))
        assertEquals(3, QuizQuestionCache.availableCount(nowMs = now))
    }

    @Test
    fun visualReferenceRoundTripsThroughTheCloudCache() {
        val item = item(7).copy(visual = visual('a'))
        val scope = "cloud:visual-test"
        assertTrue(
            QuizQuestionCache.replaceCloudSet(
                attemptUid = "attempt_visual_1",
                items = listOf(item),
                expiresAt = now + 60_000L,
                scopeKey = scope,
                nowMs = now,
            ),
        )
        val reserved = QuizQuestionCache.reserve(1, "sess_visual", scope, now)
        assertEquals(1, reserved.size)
        assertEquals(item.visual, reserved.single().question.visual)
    }

    @Test
    fun visualReferenceParticipatesInTheDuplicateFingerprint() {
        val first = item(8).copy(visual = visual('a'))
        val second = item(8).copy(visual = visual('b'))
        assertEquals(1, QuizQuestionCache.appendLocalQuestions("batch_visual_a", listOf(first), now).appended)
        assertEquals(1, QuizQuestionCache.appendLocalQuestions("batch_visual_b", listOf(second), now).appended)
        // Same text and answer as `second`; only a new import batch must not
        // make it fresh again.
        assertEquals(
            1,
            QuizQuestionCache.appendLocalQuestions("batch_visual_c", listOf(second), now).duplicates,
        )
    }

    @Test
    fun reserveMarksRowsAndReleaseReturnsThem() {
        QuizQuestionCache.replaceLocalSession(session("sess_b", 3), now)
        val reserved = QuizQuestionCache.reserve(2, "sess_b", QuizQuestionCache.LOCAL_SCOPE, now)
        assertEquals(2, reserved.size)
        assertEquals(1, QuizQuestionCache.counts(nowMs = now).available)
        assertEquals(2, QuizQuestionCache.counts(nowMs = now).reserved)
        assertTrue(QuizQuestionCache.release("sess_b"))
        assertEquals(3, QuizQuestionCache.counts(nowMs = now).available)
    }

    @Test
    fun reserveReturnsWhatIsLeftWhenThePoolIsSmaller() {
        QuizQuestionCache.replaceLocalSession(session("sess_c", 2), now)
        val reserved = QuizQuestionCache.reserve(5, "sess_c", QuizQuestionCache.LOCAL_SCOPE, now)
        assertEquals(2, reserved.size)
        assertEquals(0, QuizQuestionCache.availableCount(nowMs = now))
    }

    @Test
    fun consumeKeepsRowsForAuditAndDoesNotMakeThemAvailable() {
        val consumedBefore = QuizQuestionCache.counts(nowMs = now).consumed
        QuizQuestionCache.replaceLocalSession(session("sess_d", 2), now)
        QuizQuestionCache.reserve(1, "sess_d", QuizQuestionCache.LOCAL_SCOPE, now)
        assertTrue(QuizQuestionCache.consume("sess_d", now))
        assertEquals(1, QuizQuestionCache.counts(nowMs = now).available)
        assertEquals(consumedBefore + 1, QuizQuestionCache.counts(nowMs = now).consumed)
    }

    @Test
    fun publishingAgainReplacesOnlyUnconsumedRows() {
        val consumedBefore = QuizQuestionCache.counts(nowMs = now).consumed
        QuizQuestionCache.replaceLocalSession(session("sess_e", 2), now)
        QuizQuestionCache.reserve(1, "sess_e", QuizQuestionCache.LOCAL_SCOPE, now)
        QuizQuestionCache.consume("sess_e", now)
        assertTrue(QuizQuestionCache.replaceLocalSession(session("sess_f", 1), now))
        val counts = QuizQuestionCache.counts(nowMs = now)
        assertEquals(1, counts.available)
        assertEquals(consumedBefore + 1, counts.consumed)
    }

    @Test
    fun removePublishedCompensatesAFailedPublish() {
        QuizQuestionCache.replaceLocalSession(session("sess_g", 2), now)
        assertTrue(QuizQuestionCache.removePublished("sess_g"))
        assertEquals(0, QuizQuestionCache.availableCount(nowMs = now))
    }

    @Test
    fun clearingUnconsumedDoesNotDeleteConsumedRows() {
        QuizQuestionCache.replaceLocalSession(session("sess_h", 2), now)
        QuizQuestionCache.reserve(1, "sess_h", QuizQuestionCache.LOCAL_SCOPE, now)
        QuizQuestionCache.consume("sess_h", now)
        assertTrue(QuizQuestionCache.clearUnconsumed())
        val counts = QuizQuestionCache.counts(nowMs = now)
        assertEquals(0, counts.available)
        assertEquals(0, counts.reserved)
        assertEquals(1, counts.consumed)
    }

    @Test
    fun appendAccumulatesAndDeduplicatesByFingerprint() {
        val first = QuizQuestionCache.appendLocalQuestions("batch_1", (1..3).map(::item), now)
        assertEquals(3, first.appended)
        assertEquals(0, first.duplicates)

        val second = QuizQuestionCache.appendLocalQuestions("batch_2", (1..5).map(::item), now)
        assertEquals(2, second.appended)
        assertEquals(3, second.duplicates)
        assertEquals(5, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).total)
    }

    @Test
    fun retryingTheSameBatchIsIdempotent() {
        QuizQuestionCache.appendLocalQuestions("batch_same", (1..3).map(::item), now)
        val retry = QuizQuestionCache.appendLocalQuestions("batch_same", (1..3).map(::item), now)
        assertEquals(0, retry.appended)
        assertEquals(3, retry.duplicates)
        assertEquals(3, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).total)
    }

    @Test
    fun answerScopeFiltersCountsAndReservations() {
        QuizQuestionCache.appendLocalQuestions(
            "batch_scoped",
            (1..2).map { scopedItem(it, grade = 4) } +
                (3..4).map { scopedItem(it, grade = 5) },
            now,
        )
        val scope = QuizAnswerScope.create(
            grade = 4,
            semester = 1,
            subject = "MATH",
            textbook = "RENJIAO",
            scopeType = "UNIT",
            unitNo = 3,
            difficulty = null,
        )!!
        assertEquals(2, QuizQuestionCache.availableCount(answerScope = scope))
        val reserved = QuizQuestionCache.reserveForRound(
            count = 5,
            sessionId = "sess_scoped",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 2,
            nowMs = now,
            answerScope = scope,
        )
        assertEquals(2, reserved.size)
        assertTrue(reserved.all { it.question.scopeGrade == 4 })
    }

    @Test
    fun facetSnapshotGroupsRowsWithoutDecodingEveryQuestion() {
        QuizQuestionCache.appendLocalQuestions(
            "batch_facets",
            (1..2).map { scopedItem(it, grade = 4) } +
                (3..3).map { scopedItem(it, grade = 4).copy(scopeSubject = "CHINESE") } +
                (4..6).map { scopedItem(it, grade = 5) } +
                listOf(item(7)),
            now,
        )
        val snapshot = QuizQuestionCache.facetSnapshot(QuizQuestionCache.LOCAL_SCOPE, now)
        fun subjectCount(grade: Int, subject: String) = snapshot.groups
            .filter { it.grade == grade && it.subject == subject }
            .sumOf { it.count }
        assertEquals(7, snapshot.total)
        assertEquals(7, snapshot.groups.sumOf { it.count })
        assertEquals(2, subjectCount(4, "MATH"))
        assertEquals(1, subjectCount(4, "CHINESE"))
        assertEquals(3, subjectCount(5, "MATH"))
        // The unscoped row is its own group and is not dropped from the total.
        assertEquals(1, snapshot.groups.filter { it.grade == null }.sumOf { it.count })
    }

    @Test
    fun facetSnapshotKeepsSingleAndMultipleCountsSeparate() {
        QuizQuestionCache.appendLocalQuestions(
            "batch_facets_kind",
            (1..2).map { scopedItem(it) } +
                (3..3).map { multipleItemIn(it, "MATH", 3) },
            now,
        )
        val snapshot = QuizQuestionCache.facetSnapshot(QuizQuestionCache.LOCAL_SCOPE, now)
        fun kindCount(kind: String) = snapshot.groups
            .filter { it.answerKind == kind }
            .sumOf { it.count }
        assertEquals(2, kindCount(QuizAnswerKind.SINGLE.wire))
        assertEquals(1, kindCount(QuizAnswerKind.MULTIPLE.wire))
        assertEquals(3, snapshot.groups.sumOf { it.count })
    }

    @Test
    fun wildcardSubjectScopeMatchesEverySubjectWithinTheSameGrade() {
        QuizQuestionCache.appendLocalQuestions(
            "batch_all_subjects",
            (1..2).map { scopedItem(it, grade = 4).copy(scopeSubject = "CHINESE") } +
                (3..4).map { scopedItem(it, grade = 4).copy(scopeSubject = "MATH") } +
                (5..6).map { scopedItem(it, grade = 5).copy(scopeSubject = "MATH") },
            now,
        )
        val scope = QuizAnswerScope.create(
            grade = 4,
            semester = 1,
            subject = QuizAnswerScope.SUBJECT_ALL,
            textbook = QuizAnswerScope.TEXTBOOK_ALL,
            scopeType = "UNIT",
            unitNo = 3,
            difficulty = null,
        )!!
        // Grade still filters; only subject/textbook are wildcards.
        assertEquals(4, QuizQuestionCache.availableCount(answerScope = scope))
        val reserved = QuizQuestionCache.reserveForRound(
            count = 10,
            sessionId = "sess_all_subjects",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 4,
            nowMs = now,
            answerScope = scope,
        )
        assertEquals(4, reserved.size)
        assertTrue(reserved.all { it.question.scopeGrade == 4 })
        assertEquals(
            setOf("CHINESE", "MATH"),
            reserved.map { it.question.scopeSubject }.toSet(),
        )
    }

    @Test
    fun wildcardSubjectScopeInterleavesSubjectsEvenWhenOneBlockComesFirst() {
        // Mirrors the bundled bank: grade 4 midterm hard has a Chinese block
        // first, then math, English, and science. FIFO alone would reserve only
        // Chinese questions for a 5-question round.
        val items = (1..23).map { midtermItem(it, "CHINESE", "BUBIAN") } +
            (24..68).map { midtermItem(it, "MATH", "RENJIAO") } +
            (69..89).map { midtermItem(it, "ENGLISH", "RENJIAO") } +
            (90..113).map { midtermItem(it, "SCIENCE", "KEJIAO") }
        QuizQuestionCache.appendLocalQuestions("batch_seed_order", items, now)
        val scope = QuizAnswerScope.create(
            grade = 4,
            semester = 1,
            subject = QuizAnswerScope.SUBJECT_ALL,
            textbook = QuizAnswerScope.TEXTBOOK_ALL,
            scopeType = "MIDTERM",
            unitNo = null,
            difficulty = "HARD",
        )!!
        assertEquals(113, QuizQuestionCache.availableCount(answerScope = scope))
        val reserved = QuizQuestionCache.reserveForRound(
            count = 5,
            sessionId = "sess_seed_order",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 5,
            nowMs = now,
            answerScope = scope,
        )
        assertEquals(5, reserved.size)
        assertEquals(
            setOf("CHINESE", "MATH", "ENGLISH", "SCIENCE"),
            reserved.map { it.question.scopeSubject }.toSet(),
        )
    }

    @Test
    fun wildcardTextbookScopeStillFiltersSubject() {
        QuizQuestionCache.appendLocalQuestions(
            "batch_all_books",
            (1..2).map { scopedItem(it).copy(scopeSubject = "CHINESE", scopeTextbook = "BUBIAN") } +
                (3..4).map { scopedItem(it).copy(scopeSubject = "CHINESE", scopeTextbook = "SUJIAO") } +
                (5..6).map { scopedItem(it).copy(scopeSubject = "MATH", scopeTextbook = "RENJIAO") },
            now,
        )
        val scope = QuizAnswerScope.create(
            grade = 4,
            semester = 1,
            subject = "CHINESE",
            textbook = QuizAnswerScope.TEXTBOOK_ALL,
            scopeType = "UNIT",
            unitNo = 3,
            difficulty = null,
        )!!
        assertEquals(4, QuizQuestionCache.availableCount(answerScope = scope))
    }

    @Test
    fun duplicateFlagsMarkBankAndEarlierBatchRows() {
        QuizQuestionCache.appendLocalQuestions("batch_flags", listOf(item(1)), now)
        assertEquals(
            listOf(true, false, true, false),
            QuizQuestionCache.duplicateFlags(
                listOf(item(1), item(2), item(2), item(3)),
            ),
        )
    }

    @Test
    fun reserveForRoundDoesNotStrandRowsWhenTheBankIsTooSmall() {
        QuizQuestionCache.appendLocalQuestions("batch_short", (1..2).map(::item), now)
        val rejected = QuizQuestionCache.reserveForRound(
            count = 2,
            sessionId = "sess_short_a",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 3,
            nowMs = now,
        )
        assertTrue(rejected.isEmpty())
        assertEquals(2, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).unused)

        val accepted = QuizQuestionCache.reserveForRound(
            count = 2,
            sessionId = "sess_short_b",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 2,
            nowMs = now,
        )
        assertEquals(2, accepted.size)
        assertEquals(2, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).inRound)
    }

    @Test
    fun answersAreWrittenBackAndDuplicateSettlementIsIdempotent() {
        QuizQuestionCache.appendLocalQuestions("batch_answer", (1..3).map(::item), now)
        val reserved = QuizQuestionCache.reserveForRound(
            count = 2,
            sessionId = "sess_answer",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 1,
            nowMs = now,
        )
        val answers = listOf(
            QuizQuestionCache.AnswerWrite(
                cacheId = reserved[0].cacheId,
                questionId = reserved[0].question.questionId,
                selectedIndex = 1,
                correct = true,
                timedOut = false,
                answeredAt = now + 1,
            ),
            QuizQuestionCache.AnswerWrite(
                cacheId = reserved[1].cacheId,
                questionId = reserved[1].question.questionId,
                selectedIndex = null,
                correct = false,
                timedOut = true,
                answeredAt = now + 2,
            ),
        )
        assertTrue(QuizQuestionCache.settleRound("sess_answer", QuizQuestionCache.LOCAL_SCOPE, answers, now + 3))
        var counts = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now + 3)
        assertEquals(1, counts.correct)
        assertEquals(1, counts.wrong)
        assertEquals(1, counts.unused)

        val wrong = QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, "wrong", nowMs = now + 3)
        assertEquals(1, wrong.size)
        assertEquals(1, wrong.single().wrongCount)
        assertTrue(wrong.single().timedOut)
        assertEquals(null, wrong.single().selectedIndex)

        assertTrue(QuizQuestionCache.settleRound("sess_answer", QuizQuestionCache.LOCAL_SCOPE, answers, now + 4))
        counts = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now + 4)
        assertEquals(1, counts.correct)
        assertEquals(1, counts.wrong)
        assertEquals(
            1,
            QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, "wrong", nowMs = now + 4)
                .single()
                .wrongCount,
        )
    }

    @Test
    fun settlingReleasesQuestionsThatWereNeverAnswered() {
        QuizQuestionCache.appendLocalQuestions("batch_partial", (1..3).map(::item), now)
        val reserved = QuizQuestionCache.reserveForRound(
            count = 3,
            sessionId = "sess_partial",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 1,
            nowMs = now,
        )
        val answer = QuizQuestionCache.AnswerWrite(
            cacheId = reserved[0].cacheId,
            questionId = reserved[0].question.questionId,
            selectedIndex = 1,
            correct = true,
            timedOut = false,
            answeredAt = now + 1,
        )
        assertTrue(QuizQuestionCache.settleRound("sess_partial", QuizQuestionCache.LOCAL_SCOPE, listOf(answer), now + 2))
        val counts = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now + 2)
        assertEquals(2, counts.unused)
        assertEquals(1, counts.correct)
        assertEquals(0, counts.inRound)
    }

    @Test
    fun orphanReservationsAreReleasedButTheActiveRoundIsKept() {
        QuizQuestionCache.appendLocalQuestions("batch_orphan", (1..2).map(::item), now)
        QuizQuestionCache.reserveForRound(2, "sess_orphan_a", QuizQuestionCache.LOCAL_SCOPE, 1, now)
        assertTrue(QuizQuestionCache.releaseOrphans("sess_other"))
        assertEquals(2, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).unused)

        QuizQuestionCache.reserveForRound(2, "sess_orphan_b", QuizQuestionCache.LOCAL_SCOPE, 1, now)
        assertTrue(QuizQuestionCache.releaseOrphans("sess_orphan_b"))
        assertEquals(2, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).inRound)
    }

    @Test
    fun localAndCloudScopesAreIsolated() {
        QuizQuestionCache.appendLocalQuestions("batch_scope", (1..2).map(::item), now)
        val cloudScope = QuizQuestionCache.cloudScope("dev1")
        assertTrue(
            QuizQuestionCache.replaceCloudSet(
                "attempt_1",
                (3..5).map(::item),
                now + 60_000,
                cloudScope,
                now,
            ),
        )
        assertEquals(2, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).total)
        assertEquals(3, QuizQuestionCache.bankCounts(cloudScope, now).total)

        val reserved = QuizQuestionCache.reserveForRound(
            1,
            "sess_cloud",
            cloudScope,
            1,
            now,
        )
        assertEquals(1, reserved.size)
        assertEquals(0, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).inRound)

        assertTrue(QuizQuestionCache.clearCloudScopes())
        assertEquals(2, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).total)
        assertEquals(0, QuizQuestionCache.bankCounts(cloudScope, now).total)
    }

    @Test
    fun appendStopsAtCapacityWithoutDeletingExistingRows() {
        val all = (1..smallCapacity).map(::bulkItem)
        val first = QuizQuestionCache.appendLocalQuestions(
            "batch_full",
            all,
            now,
            capacity = smallCapacity,
        )
        assertEquals(smallCapacity, first.appended)
        assertEquals(0, first.rejected)

        val extra = QuizQuestionCache.appendLocalQuestions(
            "batch_extra",
            listOf(bulkItem(1).copy(questionId = "q_extra_0001", prompt = "One more")),
            now,
            capacity = smallCapacity,
        )
        assertEquals(0, extra.appended)
        assertEquals(1, extra.rejected)
        assertTrue(extra.capacityReached)
        assertEquals(smallCapacity, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).total)
    }

    @Test
    fun listBankFiltersByStateAndResult() {
        QuizQuestionCache.appendLocalQuestions("batch_list", (1..3).map(::item), now)
        val reserved = QuizQuestionCache.reserveForRound(
            2,
            "sess_list",
            QuizQuestionCache.LOCAL_SCOPE,
            1,
            now,
        )
        val answers = listOf(
            QuizQuestionCache.AnswerWrite(reserved[0].cacheId, reserved[0].question.questionId, 1, true, false, now + 1),
            QuizQuestionCache.AnswerWrite(reserved[1].cacheId, reserved[1].question.questionId, 0, false, false, now + 2),
        )
        QuizQuestionCache.writeAnswers("sess_list", QuizQuestionCache.LOCAL_SCOPE, answers, now + 3)

        assertEquals(3, QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, "all", nowMs = now + 3).size)
        assertEquals(1, QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, "unused", nowMs = now + 3).size)
        assertEquals(2, QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, "answered", nowMs = now + 3).size)
        assertEquals(1, QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, "wrong", nowMs = now + 3).size)
        assertEquals(1, QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, "correct", nowMs = now + 3).size)
    }

    @Test
    fun migrationMovesCloudRowsAndPreservesLocalBankRows() {
        val context = RuntimeEnvironment.getApplication()
        val dbName = "quiz_cache_migration_test.db"
        QuizQuestionCache.closeForTest()
        context.deleteDatabase(dbName)
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            "CREATE TABLE quiz_question_cache (" +
                "cache_id TEXT PRIMARY KEY, " +
                "question_json TEXT NOT NULL, " +
                "source_kind TEXT NOT NULL, " +
                "source_ref TEXT, " +
                "scope_key TEXT NOT NULL, " +
                "fingerprint TEXT NOT NULL, " +
                "state TEXT NOT NULL, " +
                "reserved_by TEXT, " +
                "received_at INTEGER NOT NULL, " +
                "expires_at INTEGER, " +
                "reserved_at INTEGER, " +
                "consumed_at INTEGER)",
        )
        fun insert(item: QuizItem, sourceKind: String, state: String, sourceRef: String) {
            db.insertOrThrow(
                "quiz_question_cache",
                null,
                ContentValues().apply {
                    put("cache_id", UUID.randomUUID().toString())
                    put("question_json", QuizSessionCodec.encodeItem(item).toString())
                    put("source_kind", sourceKind)
                    put("source_ref", sourceRef)
                    put("scope_key", QuizQuestionCache.LOCAL_SCOPE)
                    put("fingerprint", UUID.randomUUID().toString())
                    put("state", state)
                    put("received_at", now)
                    put("consumed_at", if (state == "CONSUMED") now else null as Long?)
                },
            )
        }
        insert(item(1), "LOCAL", "AVAILABLE", "local:old:q1")
        insert(item(2), "LOCAL", "CONSUMED", "local:old:q2")
        insert(item(3), "CLOUD", "AVAILABLE", "cloud:attempt:q3")
        db.version = 1
        db.close()

        try {
            assertTrue(QuizQuestionCache.initForTest(context, dbName))
            val local = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now)
            assertEquals(2, local.total)
            assertEquals(1, local.unused)
            assertEquals(1, local.legacy)
            assertEquals(1, QuizQuestionCache.bankCounts("cloud:legacy", now).total)
            val legacy = QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, "answered", nowMs = now)
            assertEquals(1, legacy.size)
            assertEquals("legacy", legacy.single().status(now))
            // The idempotent onOpen column add is what makes this insert possible.
            val imported = QuizQuestionCache.importLocalQuestions(
                "col-migrated",
                "import-migrated",
                listOf(item(9)),
                now,
            )
            assertEquals(1, imported.appended)
            assertEquals(1, QuizQuestionCache.availableCollectionCount("col-migrated", now))
        } finally {
            QuizQuestionCache.closeForTest()
            context.deleteDatabase(dbName)
            assertTrue(QuizQuestionCache.initForTest(context, testDb))
        }
    }

    @Test
    fun importedRowsAreOnlyVisibleToTheirTag() {
        val tag = "col-import-a"
        val imported = listOf(item(1), item(2), item(3))
        val preview = QuizQuestionCache.previewLocalImport(tag, imported, now)
        assertEquals(3, preview.wouldAppend)
        assertEquals(QuizQuestionCache.BANK_CAPACITY, preview.remaining)
        assertEquals(3, preview.preview.size)

        val result = QuizQuestionCache.importLocalQuestions(tag, "import-a1", imported, now)
        assertEquals(3, result.appended)
        assertEquals(0, result.rejected)
        assertEquals(QuizQuestionCache.BANK_CAPACITY - 3, result.remaining)
        assertEquals(3, QuizQuestionCache.availableCollectionCount(tag, now))
        assertEquals(0, QuizQuestionCache.availableCollectionCount("col-import-b", now))

        val custom = QuizQuestionCache.reserveCollectionForRound(2, "sess_custom", tag, 1, now)
        assertEquals(2, custom.size)
        assertTrue(custom.all { it.collectionId == tag })

        QuizQuestionCache.appendLocalQuestions("batch_course", (4..5).map(::scopedItem), now)
        val scope = QuizAnswerScope.create(
            grade = 4,
            semester = 1,
            subject = "MATH",
            textbook = "RENJIAO",
            scopeType = "UNIT",
            unitNo = 3,
            difficulty = null,
        )!!
        val course = QuizQuestionCache.reserveForRound(
            count = 5,
            sessionId = "sess_course",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 1,
            nowMs = now,
            answerScope = scope,
        )
        assertEquals(2, course.size)
        assertTrue(course.all { it.collectionId == null })
    }

    @Test
    fun importDuringAReservedRoundOnlyFeedsTheNextRound() {
        QuizQuestionCache.appendLocalQuestions("batch_active_round", (1..2).map(::item), now)
        val active = QuizQuestionCache.reserveForRound(
            count = 2,
            sessionId = "sess_active_round",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 2,
            nowMs = now,
        )
        assertEquals(2, active.size)

        // The parent web page allows a manual import while the TV is answering;
        // an import only appends, so the reserved rows stay with their round.
        val imported = QuizQuestionCache.importLocalQuestions(
            "col-active-round",
            "import-active-round",
            listOf(item(3)),
            now + 1,
        )
        assertEquals(1, imported.appended)

        val counts = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now + 2)
        assertEquals(3, counts.total)
        assertEquals(2, counts.inRound)
        assertEquals(1, counts.unused)

        val next = QuizQuestionCache.reserveForRound(
            count = 2,
            sessionId = "sess_next_round",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 1,
            nowMs = now + 3,
        )
        assertEquals(1, next.size)
        assertEquals(item(3).questionId, next.single().question.questionId)
    }

    @Test
    fun importSplitsDuplicatesIntoFileTagAndCrossSourceBuckets() {
        QuizQuestionCache.appendLocalQuestions("batch_ai", listOf(item(1)), now)
        val tagA = "col-a"
        val batch = listOf(item(1), item(2), item(2), item(3))

        val first = QuizQuestionCache.importLocalQuestions(tagA, "import-a1", batch, now)
        assertEquals(3, first.appended)
        assertEquals(1, first.fileDuplicates)
        assertEquals(0, first.collectionDuplicates)
        assertEquals(1, first.crossSourceDuplicates)
        assertEquals(4, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).total)

        val preview = QuizQuestionCache.previewLocalImport(tagA, batch, now)
        assertEquals(0, preview.wouldAppend)
        assertEquals(1, preview.fileDuplicates)
        assertEquals(3, preview.collectionDuplicates)

        val retry = QuizQuestionCache.importLocalQuestions(tagA, "import-a2", batch, now)
        assertEquals(0, retry.appended)
        assertEquals(1, retry.fileDuplicates)
        assertEquals(3, retry.collectionDuplicates)
        assertEquals(4, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).total)

        val other = QuizQuestionCache.importLocalQuestions("col-b", "import-b1", batch, now)
        assertEquals(3, other.appended)
        assertEquals(1, other.fileDuplicates)
        assertEquals(0, other.collectionDuplicates)
        assertEquals(3, other.crossSourceDuplicates)
    }

    @Test
    fun aiDedupeIgnoresImportedRows() {
        val tag = "col-ai-isolation"
        QuizQuestionCache.importLocalQuestions(tag, "import-ai-isolation", listOf(item(7)), now)
        assertEquals(listOf(false, true), QuizQuestionCache.duplicateFlags(listOf(item(7), item(7))))
    }

    @Test
    fun clearingTheBankIsSourceAware() {
        val tagA = "col-clear-a"
        val tagB = "col-clear-b"
        QuizQuestionCache.importLocalQuestions(tagA, "import-clear-a", listOf(item(1), item(2)), now)
        QuizQuestionCache.importLocalQuestions(tagB, "import-clear-b", listOf(item(3)), now)
        QuizQuestionCache.appendLocalQuestions("batch_clear_ai", listOf(item(4)), now)
        assertEquals(
            QuizQuestionCache.SourceCounts(course = 1, custom = 3),
            QuizQuestionCache.sourceCounts(nowMs = now),
        )

        assertTrue(
            QuizQuestionCache.clearBank(
                QuizQuestionCache.LOCAL_SCOPE,
                QuizQuestionCache.ClearMode.ALL,
            ),
        )
        assertEquals(
            QuizQuestionCache.SourceCounts(course = 0, custom = 3),
            QuizQuestionCache.sourceCounts(nowMs = now),
        )

        assertTrue(
            QuizQuestionCache.clearBank(
                QuizQuestionCache.LOCAL_SCOPE,
                QuizQuestionCache.ClearMode.ALL,
                QuizQuestionCache.BankSource.CUSTOM,
                tagA,
            ),
        )
        assertEquals(0, QuizQuestionCache.availableCollectionCount(tagA, now))
        assertEquals(1, QuizQuestionCache.availableCollectionCount(tagB, now))

        assertTrue(
            QuizQuestionCache.clearBank(
                QuizQuestionCache.LOCAL_SCOPE,
                QuizQuestionCache.ClearMode.ALL,
                QuizQuestionCache.BankSource.ALL,
            ),
        )
        assertEquals(0, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).total)
    }

    @Test
    fun importIsAllOrNothingWhenTheBankIsFull() {
        val all = (1..smallCapacity).map(::bulkItem)
        assertEquals(
            smallCapacity,
            QuizQuestionCache.appendLocalQuestions(
                "batch_import_full",
                all,
                now,
                capacity = smallCapacity,
            ).appended,
        )
        val tag = "col-full"
        val preview = QuizQuestionCache.previewLocalImport(
            tag,
            listOf(item(1), item(2)),
            now,
            capacity = smallCapacity,
        )
        assertEquals(0, preview.remaining)
        assertEquals(2, preview.wouldAppend)

        val result = QuizQuestionCache.importLocalQuestions(
            tag,
            "import-full",
            listOf(item(1), item(2)),
            now,
            capacity = smallCapacity,
        )
        assertEquals(0, result.appended)
        assertTrue(result.capacityReached)
        assertEquals(smallCapacity, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).total)
        assertEquals(0, QuizQuestionCache.availableCollectionCount(tag, now))
    }

    @Test
    fun batchedImportMatchesOneRunAndRetryIsIdempotent() {
        val tag = "col-batch-" + UUID.randomUUID()
        val all = (1..500).map(::bulkItem)
        val batches = all.chunked(200)
        var appended = 0
        batches.forEachIndexed { index, batch ->
            appended += QuizQuestionCache.importLocalQuestions(
                tag,
                "import-batch-$index",
                batch,
                now,
            ).appended
        }
        assertEquals(500, appended)
        assertEquals(500, QuizQuestionCache.availableCollectionCount(tag, now))
        assertEquals(500, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).total)

        // A failed run is retried from the start; fingerprints must prevent a
        // second copy of every batch that already succeeded.
        var retryAppended = 0
        var retryDuplicates = 0
        batches.forEachIndexed { index, batch ->
            val result = QuizQuestionCache.importLocalQuestions(
                tag,
                "import-retry-$index",
                batch,
                now,
            )
            retryAppended += result.appended
            retryDuplicates += result.collectionDuplicates
        }
        assertEquals(0, retryAppended)
        assertEquals(500, retryDuplicates)
        assertEquals(500, QuizQuestionCache.availableCollectionCount(tag, now))
    }

    @Test
    fun wrongReviewOnlyReselectsConsumedWrongRowsAndClearsThemOnCorrect() {
        QuizQuestionCache.appendLocalQuestions("batch_wrong_review", (1..3).map(::item), now)
        val seed = QuizQuestionCache.reserveForRound(
            count = 3,
            sessionId = "sess_wrong_seed",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 3,
            nowMs = now,
        )
        assertEquals(3, seed.size)
        assertTrue(
            QuizQuestionCache.settleRound(
                "sess_wrong_seed",
                QuizQuestionCache.LOCAL_SCOPE,
                seed.mapIndexed { index, cached ->
                    QuizQuestionCache.AnswerWrite(
                        cacheId = cached.cacheId,
                        questionId = cached.question.questionId,
                        selectedIndex = 0,
                        correct = index == 0,
                        timedOut = false,
                        answeredAt = now + index + 1,
                    )
                },
                now + 10,
            ),
        )
        assertEquals(2, QuizQuestionCache.wrongReviewCount())
        assertEquals(0, QuizQuestionCache.availableCount(nowMs = now + 10))

        val review = QuizQuestionCache.reserveWrongForRound(
            count = 5,
            sessionId = "sess_wrong_review",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 1,
            nowMs = now + 20,
        )
        assertEquals(2, review.size)
        assertTrue(review.none { it.cacheId == seed[0].cacheId })
        // Re-asking a wrong row must not hand it back to course/custom levels.
        assertEquals(0, QuizQuestionCache.availableCount(nowMs = now + 20))
        assertEquals(2, QuizQuestionCache.wrongReviewCount())

        val correct = QuizQuestionCache.AnswerWrite(
            cacheId = review[0].cacheId,
            questionId = review[0].question.questionId,
            selectedIndex = 1,
            correct = true,
            timedOut = false,
            answeredAt = now + 30,
        )
        assertTrue(
            QuizQuestionCache.writeAnswers(
                "sess_wrong_review",
                QuizQuestionCache.LOCAL_SCOPE,
                listOf(correct),
                now + 31,
            ),
        )
        assertEquals(1, QuizQuestionCache.wrongReviewCount())
        assertTrue(
            QuizQuestionCache.writeAnswers(
                "sess_wrong_review",
                QuizQuestionCache.LOCAL_SCOPE,
                listOf(correct),
                now + 32,
            ),
        )
        assertEquals(1, QuizQuestionCache.wrongReviewCount())
    }

    @Test
    fun aFailedWrongReviewReservationDoesNotReleaseTheRows() {
        QuizQuestionCache.appendLocalQuestions("batch_wrong_short", listOf(item(1)), now)
        val seed = QuizQuestionCache.reserveForRound(
            count = 1,
            sessionId = "sess_wrong_short_seed",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 1,
            nowMs = now,
        )
        assertTrue(
            QuizQuestionCache.settleRound(
                "sess_wrong_short_seed",
                QuizQuestionCache.LOCAL_SCOPE,
                listOf(
                    QuizQuestionCache.AnswerWrite(
                        cacheId = seed.single().cacheId,
                        questionId = seed.single().question.questionId,
                        selectedIndex = 0,
                        correct = false,
                        timedOut = false,
                        answeredAt = now + 1,
                    ),
                ),
                now + 2,
            ),
        )

        assertTrue(
            QuizQuestionCache.reserveWrongForRound(
                count = 5,
                sessionId = "sess_wrong_too_many",
                scopeKey = QuizQuestionCache.LOCAL_SCOPE,
                minRequired = 2,
                nowMs = now + 3,
            ).isEmpty(),
        )
        // The rejected attempt must leave the seed round's ownership intact.
        assertTrue(
            QuizQuestionCache.writeAnswers(
                "sess_wrong_other",
                QuizQuestionCache.LOCAL_SCOPE,
                listOf(
                    QuizQuestionCache.AnswerWrite(
                        cacheId = seed.single().cacheId,
                        questionId = seed.single().question.questionId,
                        selectedIndex = 1,
                        correct = true,
                        timedOut = false,
                        answeredAt = now + 4,
                    ),
                ),
                now + 5,
            ),
        )
        assertEquals(1, QuizQuestionCache.wrongReviewCount())

        val short = QuizQuestionCache.reserveWrongForRound(
            count = 5,
            sessionId = "sess_wrong_short_review",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 1,
            nowMs = now + 6,
        )
        assertEquals(1, short.size)
    }

    @Test
    fun wrongReviewCollapsesDuplicateContentWithinOneRound() {
        QuizQuestionCache.appendLocalQuestions("batch_wrong_dupe_ai", listOf(item(1)), now)
        val imported = QuizQuestionCache.importLocalQuestions(
            "col-wrong-dupe",
            "import-wrong-dupe",
            listOf(item(1)),
            now,
        )
        assertEquals(1, imported.appended)
        assertEquals(1, imported.crossSourceDuplicates)

        val reserved = QuizQuestionCache.reserveForRound(
            count = 2,
            sessionId = "sess_wrong_dupe_seed",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 2,
            nowMs = now,
        )
        assertEquals(2, reserved.size)
        assertTrue(
            QuizQuestionCache.settleRound(
                "sess_wrong_dupe_seed",
                QuizQuestionCache.LOCAL_SCOPE,
                reserved.map { cached ->
                    QuizQuestionCache.AnswerWrite(
                        cacheId = cached.cacheId,
                        questionId = cached.question.questionId,
                        selectedIndex = 0,
                        correct = false,
                        timedOut = false,
                        answeredAt = now + 1,
                    )
                },
                now + 2,
            ),
        )
        assertEquals(2, QuizQuestionCache.wrongReviewCount())

        val review = QuizQuestionCache.reserveWrongForRound(
            count = 5,
            sessionId = "sess_wrong_dupe_review",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 1,
            nowMs = now + 3,
        )
        assertEquals(1, review.size)
        // The duplicate row stays wrong for a later round; only this round dedupes.
        assertEquals(2, QuizQuestionCache.wrongReviewCount())
    }

    @Test
    fun packageRoundRobinDrawsFromEverySourceAndHonorsTypeFilters() {
        val chinese = courseScope("CHINESE", 2)
        val math = courseScope("MATH", 1)
        QuizQuestionCache.appendLocalQuestions(
            "batch_pkg_types",
            (1..4).map { itemIn(it, "CHINESE", 2) } +
                (5..8).map { multipleItemIn(it, "MATH", 1) },
            now,
        )
        val pkg = QuizLevelPackage.create(
            listOf(
                QuizPackageSource.Course(chinese, QuizTypeFilter.SINGLE),
                QuizPackageSource.Course(math, QuizTypeFilter.MULTIPLE),
            ),
        )!!

        val availability = QuizQuestionCache.packageAvailability(pkg, now)
        assertEquals(listOf(4, 4), availability.sourceCounts)
        assertEquals(8, availability.available)
        assertEquals(
            QuizQuestionCache.AnswerKindCounts(total = 4, single = 4, multiple = 0),
            QuizQuestionCache.availableTypeCounts(nowMs = now, answerScope = chinese),
        )
        assertEquals(
            QuizQuestionCache.AnswerKindCounts(total = 4, single = 0, multiple = 4),
            QuizQuestionCache.availableTypeCounts(nowMs = now, answerScope = math),
        )

        val picked = QuizQuestionCache.reservePackageForRound(
            level = pkg,
            count = 4,
            sessionId = "sess_pkg_types",
            minRequired = 4,
            nowMs = now,
            random = Random(7),
        )
        assertEquals(4, picked.size)
        assertEquals(2, picked.count { it.question.scopeSubject == "CHINESE" })
        assertEquals(2, picked.count { it.question.scopeSubject == "MATH" })
        assertTrue(
            picked.filter { it.question.scopeSubject == "CHINESE" }
                .none { it.question.isMultipleChoice },
        )
        assertTrue(
            picked.filter { it.question.scopeSubject == "MATH" }
                .all { it.question.isMultipleChoice },
        )
    }

    @Test
    fun packageRoundRobinStartsAtARandomSourceOffset() {
        val chinese = courseScope("CHINESE", 2)
        val math = courseScope("MATH", 1)
        QuizQuestionCache.appendLocalQuestions(
            "batch_pkg_offset",
            (1..8).map { itemIn(it, "CHINESE", 2) } +
                (9..16).map { itemIn(it, "MATH", 1) },
            now,
        )
        val pkg = QuizLevelPackage.create(
            listOf(
                QuizPackageSource.Course(chinese),
                QuizPackageSource.Course(math),
            ),
        )!!

        val fromFirst = QuizQuestionCache.reservePackageForRound(
            level = pkg,
            count = 1,
            sessionId = "sess_pkg_offset_first",
            minRequired = 1,
            nowMs = now,
            random = FixedRandom(0),
        )
        val fromSecond = QuizQuestionCache.reservePackageForRound(
            level = pkg,
            count = 1,
            sessionId = "sess_pkg_offset_second",
            minRequired = 1,
            nowMs = now,
            random = FixedRandom(1),
        )
        assertEquals("CHINESE", fromFirst.single().question.scopeSubject)
        assertEquals("MATH", fromSecond.single().question.scopeSubject)
    }

    @Test
    fun shortPackageRoundRollsBackAndLeavesTheBankAvailable() {
        val chinese = courseScope("CHINESE", 2)
        val math = courseScope("MATH", 1)
        QuizQuestionCache.appendLocalQuestions(
            "batch_pkg_short",
            listOf(itemIn(1, "CHINESE", 2), itemIn(2, "MATH", 1)),
            now,
        )
        val pkg = QuizLevelPackage.create(
            listOf(
                QuizPackageSource.Course(chinese),
                QuizPackageSource.Course(math),
            ),
        )!!

        assertTrue(
            QuizQuestionCache.reservePackageForRound(
                level = pkg,
                count = 3,
                sessionId = "sess_pkg_short",
                minRequired = 3,
                nowMs = now,
            ).isEmpty(),
        )
        assertEquals(2, QuizQuestionCache.counts(nowMs = now).available)
        assertEquals(2, QuizQuestionCache.packageAvailability(pkg, now).available)
        // The rejected attempt must not have stranded reservations.
        assertEquals(
            QuizQuestionCache.packageAvailability(pkg, now).available,
            QuizQuestionCache.reservePackageForRound(
                level = pkg,
                count = 2,
                sessionId = "sess_pkg_short_retry",
                minRequired = 2,
                nowMs = now,
            ).size,
        )
    }

    @Test
    fun crossSourceDuplicateContentCountsOnceInAPackageRound() {
        val chinese = courseScope("CHINESE", 2)
        QuizQuestionCache.appendLocalQuestions(
            "batch_pkg_dupe_course",
            listOf(itemIn(1, "CHINESE", 2)),
            now,
        )
        val imported = QuizQuestionCache.importLocalQuestions(
            "col-pkg-dupe",
            "import-pkg-dupe",
            listOf(itemIn(1, "CHINESE", 2)),
            now,
        )
        assertEquals(1, imported.appended)
        val pkg = QuizLevelPackage.create(
            listOf(
                QuizPackageSource.Course(chinese),
                QuizPackageSource.Collection("col-pkg-dupe"),
            ),
        )!!
        assertEquals(1, QuizQuestionCache.packageAvailability(pkg, now).available)
        assertTrue(
            QuizQuestionCache.reservePackageForRound(
                level = pkg,
                count = 2,
                sessionId = "sess_pkg_dupe",
                minRequired = 2,
                nowMs = now,
            ).isEmpty(),
        )
        assertEquals(2, QuizQuestionCache.availableCount(nowMs = now))
    }

    @Test
    fun wrongReviewSourceClearsMistakesBeforeNormalSourcesReuseThem() {
        val chinese = courseScope("CHINESE", 2)
        QuizQuestionCache.appendLocalQuestions(
            "batch_pkg_wrong_course",
            (1..2).map { itemIn(it, "CHINESE", 2) },
            now,
        )
        QuizQuestionCache.importLocalQuestions(
            "col-pkg-wrong",
            "import-pkg-wrong",
            listOf(itemIn(1, "CHINESE", 2), itemIn(3, "CHINESE", 2)),
            now,
        )
        val seed = QuizQuestionCache.reserveForRound(
            count = 2,
            sessionId = "sess_pkg_seed",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 2,
            nowMs = now,
            answerScope = chinese,
        )
        assertEquals(2, seed.size)
        assertTrue(
            QuizQuestionCache.settleRound(
                "sess_pkg_seed",
                QuizQuestionCache.LOCAL_SCOPE,
                seed.map { cached ->
                    QuizQuestionCache.AnswerWrite(
                        cacheId = cached.cacheId,
                        questionId = cached.question.questionId,
                        selectedIndex = 0,
                        correct = cached.question.questionId != "q_0000001",
                        timedOut = false,
                        answeredAt = now + 1,
                    )
                },
                now + 2,
            ),
        )
        assertEquals(1, QuizQuestionCache.wrongReviewCount())

        val pkg = QuizLevelPackage.create(
            listOf(
                QuizPackageSource.Course(chinese),
                QuizPackageSource.Collection("col-pkg-wrong"),
                QuizPackageSource.WrongReview,
            ),
        )!!
        val picked = QuizQuestionCache.reservePackageForRound(
            level = pkg,
            count = 2,
            sessionId = "sess_pkg_wrong_review",
            minRequired = 2,
            nowMs = now + 3,
            random = Random(1),
        )
        assertEquals(2, picked.size)
        // Wrong review re-asks q1; the identical tag copy is blocked instead of
        // duplicating it, and q3 fills the round.
        assertEquals(
            setOf("q_0000001", "q_0000003"),
            picked.map { it.question.questionId }.toSet(),
        )
        val reasked = picked.first { it.question.questionId == "q_0000001" }
        assertTrue(
            QuizQuestionCache.writeAnswers(
                "sess_pkg_wrong_review",
                QuizQuestionCache.LOCAL_SCOPE,
                listOf(
                    QuizQuestionCache.AnswerWrite(
                        cacheId = reasked.cacheId,
                        questionId = reasked.question.questionId,
                        selectedIndex = 1,
                        correct = true,
                        timedOut = false,
                        answeredAt = now + 4,
                    ),
                ),
                now + 5,
            ),
        )
        assertEquals(0, QuizQuestionCache.wrongReviewCount())
    }

    /** Seeds one preset pair plus one AI pair for each of two members. */
    private fun seedOriginBuckets() {
        freshSubjectCache()
        val scope = courseScope("CHINESE", 2)
        QuizQuestionCache.appendLocalQuestions(
            "batch_origin_preset",
            (1..2).map { itemIn(it, scope.subject, 2) },
            now,
        )
        QuizQuestionCache.appendLocalQuestions(
            "batch_origin_ai_a",
            (3..4).map { itemIn(it, scope.subject, 2) },
            now,
            ownerUserId = "member_a",
        )
        QuizQuestionCache.appendLocalQuestions(
            "batch_origin_ai_b",
            (5..6).map { itemIn(it, scope.subject, 2) },
            now,
            ownerUserId = "member_b",
        )
    }

    @Test
    fun courseOriginCountsOnlyItsOwnBucket() {
        seedOriginBuckets()
        val scope = courseScope("CHINESE", 2)
        fun total(origin: CourseOrigin, subject: String? = null) =
            QuizQuestionCache.availableTypeCounts(
                nowMs = now,
                answerScope = scope,
                subjectUserId = subject,
                origin = origin,
            ).total

        assertEquals(2, total(CourseOrigin.PRESET))
        assertEquals(2, total(CourseOrigin.PRESET, "member_a"))
        assertEquals(2, total(CourseOrigin.AI, "member_a"))
        assertEquals(2, total(CourseOrigin.AI, "member_b"))
        assertEquals(6, total(CourseOrigin.ALL, null))
    }

    @Test
    fun courseOriginDrawsOnlyItsOwnRowsAndProjectsSiblings() {
        seedOriginBuckets()
        val scope = courseScope("CHINESE", 2)
        val preset = QuizLevelPackage.create(
            listOf(QuizPackageSource.Course(scope, origin = CourseOrigin.PRESET)),
        )!!
        val ai = QuizLevelPackage.create(
            listOf(QuizPackageSource.Course(scope, origin = CourseOrigin.AI)),
        )!!
        val legacy = QuizLevelPackage.create(listOf(QuizPackageSource.Course(scope)))!!

        assertEquals(2, QuizQuestionCache.packageAvailability(preset, now).available)
        assertEquals(
            2,
            QuizQuestionCache.packageAvailability(ai, now, subjectUserId = "member_a").available,
        )
        // A sibling preview reads the AI rows through the projected alias.
        assertEquals(
            2,
            QuizQuestionCache.packageAvailability(
                ai,
                now,
                subjectUserId = "member_b",
                activeSubjectUserId = "member_a",
            ).available,
        )
        assertEquals(
            0,
            QuizQuestionCache.packageAvailability(ai, now, subjectUserId = "member_c").available,
        )
        assertEquals(6, QuizQuestionCache.packageAvailability(legacy, now).available)

        val presetPick = QuizQuestionCache.reservePackageForRound(
            level = preset,
            count = 2,
            sessionId = "sess_origin_preset",
            minRequired = 2,
            nowMs = now,
            random = FixedRandom(0),
        )
        assertEquals(setOf("q_0000001", "q_0000002"), presetPick.map { it.question.questionId }.toSet())

        val aiPick = QuizQuestionCache.reservePackageForRound(
            level = ai,
            count = 2,
            sessionId = "sess_origin_ai",
            minRequired = 2,
            nowMs = now,
            subjectUserId = "member_a",
            random = FixedRandom(0),
        )
        assertEquals(setOf("q_0000003", "q_0000004"), aiPick.map { it.question.questionId }.toSet())
    }

    @Test
fun presetBankDrawsOnlyItsOwnUnscopedRowsAndCountsIndependently() {
    QuizQuestionCache.replaceSeedQuestionsStreaming(
        batchId = "preset:parent-life:1",
        items = (1..3).map(::item).asSequence(),
        nowMs = now,
        sourceRefPrefix = "preset:parent-life:",
    )
    // A curriculum seed row sits in the same public bank but must never be
    // drawn by the preset source (its scope columns exclude unscoped rows).
    QuizQuestionCache.replaceSeedQuestionsStreaming(
        batchId = "preset-v4",
        items = sequenceOf(scopedItem(9)),
        nowMs = now,
        sourceRefPrefix = "preset-v",
    )
    val life = QuizLevelPackage.create(
        listOf(QuizPackageSource.Preset(QuizPresetBank.PARENT_LIFE)),
    )!!
    fun counts() = QuizQuestionCache.bankCounts(
        QuizQuestionCache.LOCAL_SCOPE,
        nowMs = now,
        presetBank = QuizPresetBank.PARENT_LIFE,
    )

    assertEquals(3, QuizQuestionCache.packageAvailability(life, now).available)
    assertEquals(3, counts().total)
    assertEquals(3, counts().unused)
    assertEquals(
        1,
        QuizQuestionCache.packageAvailability(
            QuizLevelPackage.create(
                listOf(QuizPackageSource.Course(courseScope("MATH", 3))),
            )!!,
            now,
        ).available,
    )

    val picked = QuizQuestionCache.reservePackageForRound(
        level = life,
        count = 3,
        sessionId = "sess_preset_life",
        minRequired = 3,
        nowMs = now,
        random = FixedRandom(0),
    )
    assertEquals(3, picked.size)
    assertTrue(picked.all { it.question.scopeSubject.isEmpty() })
    assertTrue(QuizQuestionCache.consume("sess_preset_life", now))

    val consumed = counts()
    assertEquals(3, consumed.total)
    assertEquals(0, consumed.unused)
}

/** A private database keeps the subject meta table from leaking between tests. */
    private fun freshSubjectCache() {
        QuizQuestionCache.closeForTest()
        assertTrue(
            QuizQuestionCache.initForTest(
                RuntimeEnvironment.getApplication(),
                "quiz_subject_${UUID.randomUUID()}.db",
            ),
        )
    }

    @Test
    fun reconcileAdoptsTheUpgradedSingleChildState() {
        freshSubjectCache()
        assertTrue(QuizQuestionCache.replaceLocalSession(session("sess_legacy", 3), now))
        assertEquals(
            1,
            QuizQuestionCache.reserve(1, "sess_legacy", QuizQuestionCache.LOCAL_SCOPE, now).size,
        )
        assertTrue(QuizQuestionCache.consume("sess_legacy", now))
        assertEquals(2, QuizQuestionCache.counts(QuizQuestionCache.LOCAL_SCOPE, now).available)
        assertEquals(1, QuizQuestionCache.counts(QuizQuestionCache.LOCAL_SCOPE, now).consumed)
        // An upgraded database has rows but no owner: the rows belong to the first child.
        assertNull(QuizQuestionCache.subjectStateOwner())

        assertTrue(QuizQuestionCache.reconcileSubjectState("member_default"))
        assertEquals("member_default", QuizQuestionCache.subjectStateOwner())
        val adopted = QuizQuestionCache.counts(QuizQuestionCache.LOCAL_SCOPE, now)
        assertEquals(2, adopted.available)
        assertEquals(1, adopted.consumed)

        // The second child starts from the full, untouched bank...
        assertTrue(QuizQuestionCache.switchSubjectState("member_second"))
        val second = QuizQuestionCache.counts(QuizQuestionCache.LOCAL_SCOPE, now)
        assertEquals(3, second.available)
        assertEquals(0, second.consumed)

        // ...and switching back restores the first child's state exactly.
        assertTrue(QuizQuestionCache.switchSubjectState("member_default"))
        val back = QuizQuestionCache.counts(QuizQuestionCache.LOCAL_SCOPE, now)
        assertEquals(2, back.available)
        assertEquals(1, back.consumed)
    }

    @Test
    fun subjectSwitchReleasesReservedRowsIntoTheSourceSnapshot() {
        freshSubjectCache()
        assertTrue(QuizQuestionCache.replaceLocalSession(session("sess_round", 3), now))
        assertTrue(QuizQuestionCache.reconcileSubjectState("member_a"))
        assertEquals(
            1,
            QuizQuestionCache.reserve(1, "round_a", QuizQuestionCache.LOCAL_SCOPE, now).size,
        )
        assertEquals(1, QuizQuestionCache.counts(QuizQuestionCache.LOCAL_SCOPE, now).reserved)

        assertTrue(QuizQuestionCache.switchSubjectState("member_b"))
        val other = QuizQuestionCache.counts(QuizQuestionCache.LOCAL_SCOPE, now)
        assertEquals(3, other.available)
        assertEquals(0, other.reserved)

        assertTrue(QuizQuestionCache.switchSubjectState("member_a"))
        val restored = QuizQuestionCache.counts(QuizQuestionCache.LOCAL_SCOPE, now)
        assertEquals(3, restored.available)
        assertEquals(0, restored.reserved)
    }

    @Test
    fun subjectSwitchKeepsSparseSnapshotsAndAnswerDetails() {
        freshSubjectCache()
        assertTrue(QuizQuestionCache.replaceLocalSession(session("sess_sparse", 3), now))
        assertTrue(QuizQuestionCache.reconcileSubjectState("member_a"))
        val reserved = QuizQuestionCache.reserveForRound(
            count = 2,
            sessionId = "sess_sparse",
            scopeKey = QuizQuestionCache.LOCAL_SCOPE,
            minRequired = 1,
            nowMs = now,
        )
        val answers = listOf(
            QuizQuestionCache.AnswerWrite(
                cacheId = reserved[0].cacheId,
                questionId = reserved[0].question.questionId,
                selectedIndex = 1,
                correct = true,
                timedOut = false,
                answeredAt = now + 1,
            ),
            QuizQuestionCache.AnswerWrite(
                cacheId = reserved[1].cacheId,
                questionId = reserved[1].question.questionId,
                selectedIndex = null,
                correct = false,
                timedOut = true,
                answeredAt = now + 2,
            ),
        )
        assertTrue(
            QuizQuestionCache.settleRound(
                "sess_sparse",
                QuizQuestionCache.LOCAL_SCOPE,
                answers,
                now + 3,
            ),
        )

        assertTrue(QuizQuestionCache.switchSubjectState("member_b"))
        // The new active child starts from the untouched bank.
        assertEquals(
            3,
            QuizQuestionCache.counts(QuizQuestionCache.LOCAL_SCOPE, now + 3).available,
        )
        // The previous child's sparse snapshot still projects every answer detail.
        val sibling = QuizQuestionCache.bankCounts(
            QuizQuestionCache.LOCAL_SCOPE,
            nowMs = now + 3,
            subjectUserId = "member_a",
            activeSubjectUserId = "member_b",
        )
        assertEquals(1, sibling.correct)
        assertEquals(1, sibling.wrong)
        assertEquals(1, sibling.unused)
        val wrong = QuizQuestionCache.listBank(
            QuizQuestionCache.LOCAL_SCOPE,
            "wrong",
            nowMs = now + 3,
            subjectUserId = "member_a",
            activeSubjectUserId = "member_b",
        )
        assertEquals(1, wrong.size)
        assertEquals(1, wrong.single().wrongCount)
        assertTrue(wrong.single().timedOut)
        assertNull(wrong.single().selectedIndex)

        // Switching back restores the same details on the materialized rows.
        assertTrue(QuizQuestionCache.switchSubjectState("member_a"))
        val restored = QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now + 3)
        assertEquals(1, restored.correct)
        assertEquals(1, restored.wrong)
        assertEquals(1, restored.unused)
        assertEquals(
            1,
            QuizQuestionCache.listBank(QuizQuestionCache.LOCAL_SCOPE, "wrong", nowMs = now + 3)
                .single()
                .wrongCount,
        )
    }
}
