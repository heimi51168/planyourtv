package app.orchard.tv.quiz

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionCodec
import com.planyourtv.policy.quiz.QuizSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class QuizQuestionCacheTest {
    private val now = 1_760_000_000_000L
    private val testDb = "quiz_cache_test.db"

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

    private fun scopedItem(index: Int, grade: Int = 4) = item(index).copy(
        scopeGrade = grade,
        scopeSemester = 1,
        scopeSubject = "MATH",
        scopeTextbook = "RENJIAO",
        scopeType = "UNIT",
        scopeUnitNo = 3,
    )

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
        val all = (1..QuizQuestionCache.BANK_CAPACITY).map(::bulkItem)
        val first = QuizQuestionCache.appendLocalQuestions("batch_full", all, now)
        assertEquals(QuizQuestionCache.BANK_CAPACITY, first.appended)
        assertEquals(0, first.rejected)

        val extra = QuizQuestionCache.appendLocalQuestions(
            "batch_extra",
            listOf(bulkItem(1).copy(questionId = "q_extra_0001", prompt = "One more")),
            now,
        )
        assertEquals(0, extra.appended)
        assertEquals(1, extra.rejected)
        assertTrue(extra.capacityReached)
        assertEquals(QuizQuestionCache.BANK_CAPACITY, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).total)
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
        val all = (1..QuizQuestionCache.BANK_CAPACITY).map(::bulkItem)
        assertEquals(
            QuizQuestionCache.BANK_CAPACITY,
            QuizQuestionCache.appendLocalQuestions("batch_import_full", all, now).appended,
        )
        val tag = "col-full"
        val preview = QuizQuestionCache.previewLocalImport(tag, listOf(item(1), item(2)), now)
        assertEquals(0, preview.remaining)
        assertEquals(2, preview.wouldAppend)

        val result = QuizQuestionCache.importLocalQuestions(
            tag,
            "import-full",
            listOf(item(1), item(2)),
            now,
        )
        assertEquals(0, result.appended)
        assertTrue(result.capacityReached)
        assertEquals(QuizQuestionCache.BANK_CAPACITY, QuizQuestionCache.bankCounts(QuizQuestionCache.LOCAL_SCOPE, now).total)
        assertEquals(0, QuizQuestionCache.availableCollectionCount(tag, now))
    }
}
