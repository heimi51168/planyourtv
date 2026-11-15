package app.orchard.tv.quiz

import com.planyourtv.policy.quiz.QuizScoring
import com.planyourtv.policy.quiz.QuizScoringV6
import com.planyourtv.policy.quiz.QuizSession

/**
 * Exact payout of a round, in seconds.
 *
 * A v6 round — local or cloud — pays the profile frozen at start, so the report
 * shows exactly what the member config promised. The backend recomputes the
 * same formula from the document it owns at settlement time. A v5 record keeps
 * the minute engine it was created with. Null means the record settles
 * elsewhere: cloud v5 rounds stay backend-authoritative and v2/v3/v4 records
 * use the legacy fields handled directly by [QuizChallenge].
 */
object QuizSettlement {
    fun earnedSeconds(session: QuizSession): Int? {
        val run = session.run ?: return null
        val profile = session.rewardProfile
        if (session.originSchemaVersion >= 6 && profile != null) {
            return QuizScoringV6.score(session.items, run.submissions, profile).earnedSeconds
        }
        if (QuizChallenge.isCloudSession(session)) return null
        return when {
            session.originSchemaVersion >= 5 ->
                QuizScoring.score(session.items, run.submissions, session.config).earnedMinutes * 60
            else -> null
        }
    }
}
