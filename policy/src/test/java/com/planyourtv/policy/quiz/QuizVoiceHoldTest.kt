package com.planyourtv.policy.quiz

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizVoiceHoldTest {
    @Test
    fun generatedConstantsMatchTheMeasuredVoiceFixture() {
        val root = JSONObject(fixture("quiz/voice_hold.json"))
        assertEquals(QuizVoiceHold.SAFETY_MARGIN_MS.toInt(), root.getInt("safetyMarginMs"))

        val wrong = root.getJSONObject("wrong")
        val wrongMax = maxDurationMs(wrong)
        assertEquals(QuizVoiceHold.WRONG_MAX_VOICE_MS, wrongMax.toLong())
        assertEquals(
            QuizVoiceHold.WRONG_HOLD_MS,
            wrongMax + QuizVoiceHold.SAFETY_MARGIN_MS,
        )

        val timeout = root.getJSONObject("timeout")
        val timeoutMax = maxDurationMs(timeout)
        assertEquals(QuizVoiceHold.TIMEOUT_MAX_VOICE_MS, timeoutMax.toLong())
        assertEquals(
            QuizVoiceHold.TIMEOUT_HOLD_MS,
            timeoutMax + QuizVoiceHold.SAFETY_MARGIN_MS,
        )
        assertTrue(QuizVoiceHold.TIMEOUT_HOLD_MS > QuizVoiceHold.WRONG_HOLD_MS)
    }

    @Test
    fun correctAnswersHaveNoHoldWhileWrongAndTimeoutUseTheirOwnFloor() {
        assertEquals(0L, QuizVoiceHold.holdMs(correct = true, timedOut = false, extraHoldSec = 0))
        assertEquals(0L, QuizVoiceHold.holdMs(correct = true, timedOut = true, extraHoldSec = 10))
        assertEquals(
            QuizVoiceHold.WRONG_HOLD_MS,
            QuizVoiceHold.holdMs(correct = false, timedOut = false, extraHoldSec = 0),
        )
        assertEquals(
            QuizVoiceHold.TIMEOUT_HOLD_MS,
            QuizVoiceHold.holdMs(correct = false, timedOut = true, extraHoldSec = 0),
        )
    }

    @Test
    fun configuredMinimumNeverShortensTheVoiceFloorAndWinsWhenLonger() {
        // Below the voice floor, the clip still finishes first.
        assertEquals(
            QuizVoiceHold.WRONG_HOLD_MS,
            QuizVoiceHold.holdMs(correct = false, timedOut = false, extraHoldSec = 2),
        )
        // Above it, the configured minimum total stay is what the child gets.
        assertEquals(
            10_000L,
            QuizVoiceHold.holdMs(correct = false, timedOut = false, extraHoldSec = 10),
        )
        assertEquals(
            30_000L,
            QuizVoiceHold.holdMs(correct = false, timedOut = true, extraHoldSec = 30),
        )
    }

    @Test
    fun outOfRangeExtraHoldCannotShortenTheFloor() {
        assertEquals(
            QuizVoiceHold.WRONG_HOLD_MS,
            QuizVoiceHold.holdMs(correct = false, timedOut = false, extraHoldSec = -1),
        )
        assertEquals(
            QuizVoiceHold.WRONG_HOLD_MS,
            QuizVoiceHold.holdMs(
                correct = false,
                timedOut = false,
                extraHoldSec = QUIZ_DOC_MAX_EXTRA_HOLD_SEC + 1,
            ),
        )
    }

    private fun maxDurationMs(group: JSONObject): Long {
        val files = group.getJSONArray("files")
        var max = 0L
        for (index in 0 until files.length()) {
            val duration = files.getJSONObject(index).getLong("durationMs")
            assertTrue("duration must be positive", duration > 0)
            if (duration > max) max = duration
        }
        assertTrue("group must contain at least one voice file", max > 0)
        assertEquals(group.getLong("maxVoiceMs"), max)
        assertEquals(group.getLong("holdMs"), max + QuizVoiceHold.SAFETY_MARGIN_MS)
        return max
    }

    private fun fixture(name: String): String = checkNotNull(
        javaClass.classLoader?.getResource(name),
    ) { "missing fixture $name" }.readText(Charsets.UTF_8)
}
