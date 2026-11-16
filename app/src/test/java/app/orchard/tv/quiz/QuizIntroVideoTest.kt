package app.orchard.tv.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Local uploads follow Aerial's owner-video containers: MP4 and MOV only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizIntroVideoTest {
    @Test
    fun keepsMp4AndMovNames() {
        assertEquals("trip.mp4", QuizIntroVideo.titleOf("/sdcard/trip.mp4"))
        assertEquals("trip.MOV", QuizIntroVideo.titleOf("C:\\films\\trip.MOV"))
    }

    @Test
    fun rejectsOtherContainersAndNamesWithoutAnExtension() {
        assertNull(QuizIntroVideo.titleOf("trip.m4v"))
        assertNull(QuizIntroVideo.titleOf("trip.webm"))
        assertNull(QuizIntroVideo.titleOf("noextension"))
    }
}
