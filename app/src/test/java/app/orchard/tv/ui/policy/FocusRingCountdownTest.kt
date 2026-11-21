package app.orchard.tv.ui.policy

import com.planyourtv.policy.StudyRoundUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The focus ring counts down the reward goal, not the whole study window. */
class FocusRingCountdownTest {
    private fun round(
        totalSeconds: Long = 25 * 60L,
        remainingSeconds: Long = 22 * 60L,
        studiedSeconds: Long = 3 * 60L,
        rewardEnabled: Boolean = true,
        rewardAtSeconds: Long = 25 * 60L,
        rewardAttempted: Boolean = false,
    ) = StudyRoundUi(
        studyPackage = "com.example.study",
        totalSeconds = totalSeconds,
        remainingSeconds = remainingSeconds,
        studiedSeconds = studiedSeconds,
        rewardEnabled = rewardEnabled,
        rewardAtSeconds = rewardAtSeconds,
        rewardAttempted = rewardAttempted,
    )

    @Test
    fun aReachableRewardCountsDownFromTheRewardGoal() {
        val ring = focusRingCountdown(round())

        assertTrue(ring.reward)
        assertEquals(22 * 60L, ring.remainingSeconds)
        assertEquals(25 * 60L, ring.totalSeconds)
    }

    @Test
    fun aShortStudyWindowCannotPretendToReachTheReward() {
        val ring = focusRingCountdown(
            round(
                totalSeconds = 24 * 60L + 41L,
                remainingSeconds = 21 * 60L + 2L,
                studiedSeconds = 3 * 60L + 39L,
            ),
        )

        assertFalse(ring.reward)
        assertEquals(21 * 60L + 2L, ring.remainingSeconds)
        assertEquals(24 * 60L + 41L, ring.totalSeconds)
    }

    @Test
    fun afterTheOneRewardAttemptTheSessionRemainderTakesOver() {
        val ring = focusRingCountdown(
            round(
                remainingSeconds = 2 * 60L,
                studiedSeconds = 23 * 60L,
                rewardAttempted = true,
            ),
        )

        assertFalse(ring.reward)
        assertEquals(2 * 60L, ring.remainingSeconds)
        assertEquals(25 * 60L, ring.totalSeconds)
    }

    @Test
    fun aPlainStudyRoundHasNoRewardClock() {
        val ring = focusRingCountdown(
            round(
                rewardEnabled = false,
                rewardAtSeconds = 0L,
                studiedSeconds = 30L,
            ),
        )

        assertFalse(ring.reward)
        assertEquals(22 * 60L, ring.remainingSeconds)
        assertEquals(25 * 60L, ring.totalSeconds)
    }
}