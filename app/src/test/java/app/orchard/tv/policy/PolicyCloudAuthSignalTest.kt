package app.orchard.tv.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/enroll/status` reports a revoked credential as HTTP 200 with business code 401, unlike
 * interceptor-protected endpoints that use HTTP 401. Missing either signal leaves a legitimately
 * unbound TV stuck in the old guard state.
 */
class PolicyCloudAuthSignalTest {
    @Test
    fun httpUnauthorizedIsRecognized() {
        assertTrue(isCredentialUnauthorized(httpCode = 401, apiCode = 200))
    }

    @Test
    fun businessUnauthorizedIsRecognized() {
        assertTrue(isCredentialUnauthorized(httpCode = 200, apiCode = 401))
    }

    @Test
    fun otherFailuresAreNotTreatedAsRevocation() {
        assertFalse(isCredentialUnauthorized(httpCode = 200, apiCode = 403))
        assertFalse(isCredentialUnauthorized(httpCode = 409, apiCode = 409))
        assertFalse(isCredentialUnauthorized(httpCode = 500, apiCode = 500))
    }
}
