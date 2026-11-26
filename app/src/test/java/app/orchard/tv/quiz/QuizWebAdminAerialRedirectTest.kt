package app.orchard.tv.quiz

import fi.iki.elonen.NanoHTTPD.Method
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bound-TV redirect must move only the HTML shell, never its assets or APIs. */
class QuizWebAdminAerialRedirectTest {
    @Test
    fun mapsTheShellToTheTokenRoot() {
        assertEquals("/abcd1234/", QuizWebAdmin.aerialRootForQuizUri("/abcd1234/quiz"))
        assertEquals("/abcd1234/", QuizWebAdmin.aerialRootForQuizUri("/abcd1234/quiz/"))
        assertEquals(
            "/abcd1234/",
            QuizWebAdmin.aerialRootForQuizUri("/abcd1234/quiz?from=qr#parent"),
        )
    }

    @Test
    fun leavesAssetsAndOtherPathsAlone() {
        assertNull(QuizWebAdmin.aerialRootForQuizUri("/abcd1234/"))
        assertNull(QuizWebAdmin.aerialRootForQuizUri("/abcd1234/quiz.js"))
        assertNull(QuizWebAdmin.aerialRootForQuizUri("/abcd1234/quiz.css"))
        assertNull(QuizWebAdmin.aerialRootForQuizUri("/abcd1234/quizzes"))
        assertNull(QuizWebAdmin.aerialRootForQuizUri("/abcd1234/api/quiz/status"))
    }

    @Test
    fun redirectsOnlyTheBoundQuizShell() {
        assertTrue(QuizWebAdmin.isBoundQuizShellRedirect(Method.GET, "/quiz", "REMOTE_BOUND"))
        assertTrue(QuizWebAdmin.isBoundQuizShellRedirect(Method.GET, "/quiz/", "REMOTE_BOUND"))
        assertFalse(QuizWebAdmin.isBoundQuizShellRedirect(Method.POST, "/quiz", "REMOTE_BOUND"))
        assertFalse(QuizWebAdmin.isBoundQuizShellRedirect(Method.GET, "/quiz", "OK"))
        assertFalse(QuizWebAdmin.isBoundQuizShellRedirect(Method.GET, "/quiz", "NOT_READY"))
        assertFalse(QuizWebAdmin.isBoundQuizShellRedirect(Method.GET, "/quiz", "REMOTE_MODE"))
        assertFalse(
            QuizWebAdmin.isBoundQuizShellRedirect(Method.GET, "/quiz", "LOCAL_MODE_REQUIRED"),
        )
        assertFalse(QuizWebAdmin.isBoundQuizShellRedirect(Method.GET, "/quiz.js", "REMOTE_BOUND"))
        assertFalse(QuizWebAdmin.isBoundQuizShellRedirect(Method.GET, "/", "REMOTE_BOUND"))
    }
}