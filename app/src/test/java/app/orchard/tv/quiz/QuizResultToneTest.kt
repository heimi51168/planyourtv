package app.orchard.tv.quiz

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizResultToneTest {
    @Test
    fun zeroScoreWithNoThresholdStaysGreen() {
        assertFalse(QuizResultTone.isGold(scoreMinutes = 0, unlockReached = true))
    }

    @Test
    fun reachedThresholdWithScoreIsGold() {
        assertTrue(QuizResultTone.isGold(scoreMinutes = 10, unlockReached = true))
    }

    @Test
    fun aboveThresholdIsGold() {
        assertTrue(QuizResultTone.isGold(scoreMinutes = 15, unlockReached = true))
    }

    @Test
    fun belowThresholdIsGreen() {
        assertFalse(QuizResultTone.isGold(scoreMinutes = 5, unlockReached = false))
    }

    @Test
    fun negativeScoreIsNeverGold() {
        assertFalse(QuizResultTone.isGold(scoreMinutes = -1, unlockReached = true))
    }
}
