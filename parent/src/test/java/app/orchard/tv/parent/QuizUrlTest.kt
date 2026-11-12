package app.orchard.tv.parent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizUrlTest {
    @Test
    fun keepsTheExactQrAddress() {
        assertEquals(
            "http://192.0.2.10:8790/abcd2345/quiz",
            QuizUrl.normalize("http://192.0.2.10:8790/abcd2345/quiz"),
        )
    }

    @Test
    fun addsTheDefaultScheme() {
        assertEquals(
            "http://192.0.2.10:8790/abcd2345/quiz",
            QuizUrl.normalize("192.0.2.10:8790/abcd2345/quiz"),
        )
    }

    @Test
    fun normalizesATrailingSlash() {
        assertEquals(
            "http://192.0.2.10:8790/abcd2345/quiz",
            QuizUrl.normalize("http://192.0.2.10:8790/abcd2345/quiz/"),
        )
    }

    @Test
    fun appendsQuizToTheBaseAddress() {
        assertEquals(
            "http://192.0.2.10:8790/abcd2345/quiz",
            QuizUrl.normalize("http://192.0.2.10:8790/abcd2345/"),
        )
    }

    @Test
    fun extractsAnAddressFromScannedText() {
        assertEquals(
            "http://192.0.2.10:8790/abcd2345/quiz",
            QuizUrl.normalize("Parent Center\nhttp://192.0.2.10:8790/abcd2345/quiz\n"),
        )
    }

    @Test
    fun keepsHttps() {
        assertEquals(
            "https://tv.example:9443/abcd2345/quiz",
            QuizUrl.normalize("https://tv.example:9443/abcd2345/quiz"),
        )
    }

    @Test
    fun rejectsOtherSchemes() {
        assertNull(QuizUrl.normalize("ftp://192.0.2.10/abcd2345/quiz"))
        assertNull(QuizUrl.normalize("file:///sdcard/abcd2345/quiz"))
    }

    @Test
    fun rejectsWrongPages() {
        assertNull(QuizUrl.normalize("http://192.0.2.10:8790/quiz"))
        assertNull(QuizUrl.normalize("http://192.0.2.10:8790/abcd2345/other"))
        assertNull(QuizUrl.normalize("http://192.0.2.10:8790/"))
    }

    @Test
    fun rejectsCredentialsInTheAddress() {
        assertNull(QuizUrl.normalize("http://parent:secret@192.0.2.10:8790/abcd2345/quiz"))
    }

    @Test
    fun rejectsATokenThatIsTooShort() {
        assertNull(QuizUrl.normalize("http://192.0.2.10:8790/abc/quiz"))
    }

    @Test
    fun comparesOriginsIgnoringThePath() {
        assertTrue(
            QuizUrl.sameOrigin(
                "http://192.0.2.10:8790/abcd2345/quiz",
                "http://192.0.2.10:8790/api/quiz/start",
            ),
        )
        assertFalse(
            QuizUrl.sameOrigin(
                "http://192.0.2.10:8790/abcd2345/quiz",
                "http://192.0.2.10:8791/abcd2345/quiz",
            ),
        )
        assertFalse(
            QuizUrl.sameOrigin(
                "http://192.0.2.10:8790/abcd2345/quiz",
                "https://192.0.2.10:8790/abcd2345/quiz",
            ),
        )
    }

    @Test
    fun treatsDefaultPortsAsEqual() {
        assertTrue(QuizUrl.sameOrigin("http://tv.example/a/quiz", "http://tv.example:80/a/quiz"))
    }
}
