package app.orchard.tv.quiz

import app.orchard.tv.policy.PolicyModeCoordinator
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.quiz.QuizSessionStatus

/**
 * The local quiz on/off switch, shared by the TV parent center and the phone page.
 *
 * Turning it off also drops a session that has not started yet, matching the
 * phone page: a later enable must not resurrect an old challenge.
 */
object QuizSettings {
    enum class SetResult { Changed, SessionRunning, StorageError }

    private val ACTIVE_STATUSES = setOf(
        QuizSessionStatus.RUNNING,
        QuizSessionStatus.SETTLING,
        QuizSessionStatus.STARTING,
        QuizSessionStatus.START_FAILED,
    )

    fun isEnabled(): Boolean =
        if (PolicyModeCoordinator.mode == PolicyMode.LOCAL) {
            // An unreadable document is not "probably enabled": fail explicit.
            QuizConfigDocumentStore.localDocument()?.enabled ?: false
        } else {
            QuizSessionStore.enabled()
        }

    fun setEnabled(enabled: Boolean): SetResult {
        if (!enabled) {
            val current = QuizRuntime.refresh().session
            if (current?.status in ACTIVE_STATUSES) return SetResult.SessionRunning
            if (current == null && QuizSessionStore.hasStoredSession()) {
                // An undecodable record has no session object to clean up.
                // Disabling the quiz is an explicit reset from the parent, so
                // drop it instead of leaving the TV stuck on the setup QR.
                QuizQuestionCache.releaseOrphans(null)
                if (!QuizSessionStore.quarantineUnreadableSession()) {
                    return SetResult.StorageError
                }
            }
            if (current != null) {
                if (QuizChallenge.isCloudSession(current)) {
                    CloudQuizPreparer.discardCloudState(PolicyModeCoordinator.state.value.deviceNo)
                } else {
                    val scope = QuizRecovery.scopeFor(
                        current,
                        PolicyModeCoordinator.state.value.deviceNo,
                    )
                    if (scope != null) {
                        QuizQuestionCache.settleRound(
                            current.id,
                            scope,
                            QuizRecovery.answerWrites(current),
                        )
                    }
                    QuizQuestionCache.release(current.id)
                    if (!QuizSessionStore.clearSession()) return SetResult.StorageError
                }
            }
        }
        if (PolicyModeCoordinator.mode == PolicyMode.LOCAL) {
            // The local document owns the switch. Keep the old boolean as a
            // mirror for readers that predate the document; a failed document
            // write is reported instead of leaving the two disagreeing.
            when (QuizConfigDocumentStore.markEnabled(enabled)) {
                is QuizDocSaveResult.Ok -> Unit
                is QuizDocSaveResult.Conflict,
                is QuizDocSaveResult.Invalid,
                QuizDocSaveResult.Storage,
                -> return SetResult.StorageError
            }
        }
        QuizSessionStore.setEnabled(enabled)
        QuizConfigSync.afterLocalEnabledChange(enabled)
        QuizRuntime.refresh()
        return SetResult.Changed
    }
}
