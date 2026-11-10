package app.orchard.tv.quiz

import android.util.Log
import app.orchard.tv.policy.PolicyModeCoordinator
import com.planyourtv.policy.quiz.QuizSession
import com.planyourtv.policy.quiz.QuizSessionStatus

/**
 * Reconciles the durable session with the question cache after an upgrade,
 * process death, or a mode change. It never deletes answered rows and never
 * touches the local bank while finishing a cloud round.
 */
object QuizRecovery {
    private const val TAG = "QuizRecovery"

    @Synchronized
    fun reconcile(nowMs: Long = System.currentTimeMillis()): Boolean {
        val inspection = QuizSessionStore.inspectSession()
        val session = inspection.session
        if (session == null) {
            if (inspection.hasRecord) {
                if (inspection.decrypted) {
                    // A record the current codec rejects would otherwise block
                    // the quiz entry forever. Log the field-level reason, move
                    // the bytes aside for a later lenient decoder, and let the
                    // child fall through to the bank/level start screen.
                    Log.w(
                        TAG,
                        "unreadable quiz session quarantined: " +
                            inspection.errors.joinToString(",").ifEmpty { "unknown" },
                    )
                    if (!QuizSessionStore.quarantineUnreadableSession()) {
                        Log.w(TAG, "could not quarantine unreadable quiz session")
                    }
                    QuizRuntime.refresh()
                } else {
                    // Keep a record whose Keystore key is temporarily
                    // unavailable; clearing it here would destroy a valid round.
                    Log.w(TAG, "quiz session decryption failed; keeping the record")
                }
            }
            QuizQuestionCache.releaseOrphans(null)
            return true
        }
        val scope = scopeFor(session, PolicyModeCoordinator.state.value.deviceNo)
        when (session.status) {
            QuizSessionStatus.READY -> {
                if (session.source == QbClient.CLOUD_SESSION_SOURCE) {
                    QuizQuestionCache.releaseOrphans(session.id)
                } else {
                    importLegacyLocalReady(nowMs)
                }
            }
            QuizSessionStatus.DRAFT -> {
                QuizSessionStore.clearSession()
                QuizQuestionCache.releaseOrphans(null)
                QuizRuntime.refresh()
            }
            QuizSessionStatus.RUNNING -> {
                if (scope != null) {
                    QuizQuestionCache.writeAnswers(
                        session.id,
                        scope,
                        answerWrites(session),
                        nowMs,
                    )
                }
                // Keep this round's reservations; release anything left by a
                // previous process or by a cloud attempt that is gone.
                QuizQuestionCache.releaseOrphans(session.id)
            }
            QuizSessionStatus.SETTLING,
            QuizSessionStatus.STARTING,
            QuizSessionStatus.START_FAILED -> {
                if (scope != null) {
                    QuizQuestionCache.settleRound(
                        session.id,
                        scope,
                        answerWrites(session),
                        nowMs,
                    )
                }
                QuizQuestionCache.releaseOrphans(session.id)
            }
        }
        return true
    }

    /**
     * Older builds published a local READY session. Its items become ordinary
     * bank rows, so the next tap starts from the bank instead of resurrecting
     * the old publish flow.
     */
    @Synchronized
    fun importLegacyLocalReady(nowMs: Long = System.currentTimeMillis()): Boolean {
        val session = QuizSessionStore.loadSession() ?: return false
        if (session.status != QuizSessionStatus.READY ||
            session.source == QbClient.CLOUD_SESSION_SOURCE
        ) {
            return false
        }
        val result = QuizQuestionCache.importLegacyReady(session, nowMs)
        if (result.rejected > 0) return false
        QuizSessionStore.clearSession()
        QuizQuestionCache.releaseOrphans(null)
        QuizRuntime.refresh()
        return true
    }

    fun answerWrites(session: QuizSession): List<QuizQuestionCache.AnswerWrite> {
        val run = session.run ?: return emptyList()
        return run.submissions.mapNotNull { submission ->
            val item = session.items.firstOrNull { it.questionId == submission.questionId }
                ?: return@mapNotNull null
            val selected = submission.selectedIndex?.let { displayIndex ->
                if (item.optionOrder.isEmpty()) displayIndex else item.optionOrder.getOrNull(displayIndex)
            }
            QuizQuestionCache.AnswerWrite(
                cacheId = item.cacheId,
                questionId = item.questionId,
                selectedIndex = selected,
                correct = submission.correct,
                timedOut = submission.timedOut,
                answeredAt = submission.answeredAt,
            )
        }
    }

    fun scopeFor(session: QuizSession, deviceNo: String): String? {
        if (session.source != QbClient.CLOUD_SESSION_SOURCE) {
            return QuizQuestionCache.LOCAL_SCOPE
        }
        if (deviceNo.isBlank()) return null
        return QuizQuestionCache.cloudScope(deviceNo)
    }
}
