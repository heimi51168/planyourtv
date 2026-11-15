package app.orchard.tv.policy.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Single-editor lease used by the web parent center. Only two phones compete for it; the TV
 * parent center is read-only and must never appear as a lease owner. Time and tokens are
 * injected so the test is deterministic.
 */
class ParentEditorLeaseTest {
    private var now = 1_000L
    private var tokenSeq = 0
    private val engine = ParentEditorLeaseEngine(
        now = { now },
        randomToken = { "token-${++tokenSeq}" },
    )

    private fun acquire(): String {
        val result = engine.acquireWeb()
        assertTrue(result is ParentEditorLeaseEngine.AcquireResult.Ok)
        return (result as ParentEditorLeaseEngine.AcquireResult.Ok).token
    }

    @Test
    fun firstPhoneOwnsTheLease() {
        val token = acquire()
        assertEquals(ParentEditorLeaseEngine.Owner.WEB, engine.state().owner)
        assertTrue(engine.webTokenValid(ParentWebCrypto.sha256Hex(token)))
    }

    @Test
    fun secondPhoneIsRejectedUntilTheFirstReleases() {
        val token = acquire()
        assertEquals(
            ParentEditorLeaseEngine.AcquireResult.WebEditing,
            engine.acquireWeb(),
        )
        engine.releaseWeb(ParentWebCrypto.sha256Hex(token))
        assertEquals(ParentEditorLeaseEngine.Owner.IDLE, engine.state().owner)
        assertTrue(engine.acquireWeb() is ParentEditorLeaseEngine.AcquireResult.Ok)
    }

    @Test
    fun anotherTokenCannotRenewTheLease() {
        val token = acquire()
        assertEquals(
            ParentEditorLeaseEngine.HeartbeatResult.TakenOver,
            engine.heartbeat(ParentWebCrypto.sha256Hex("other-token")),
        )
        assertTrue(
            engine.heartbeat(ParentWebCrypto.sha256Hex(token)) is
                ParentEditorLeaseEngine.HeartbeatResult.Ok,
        )
    }

    @Test
    fun explicitTakeoverRevokesTheFirstPage() {
        val token = acquire()
        engine.invalidateWeb("takeover")
        assertEquals(ParentEditorLeaseEngine.Owner.IDLE, engine.state().owner)
        assertEquals(
            ParentEditorLeaseEngine.HeartbeatResult.TakenOver,
            engine.heartbeat(ParentWebCrypto.sha256Hex(token)),
        )
    }

    @Test
    fun missingHeartbeatExpiresTheWebEditor() {
        val token = acquire()
        val tokenDigest = ParentWebCrypto.sha256Hex(token)
        now += ParentEditorLeaseEngine.WEB_TTL_MS
        assertEquals(
            ParentEditorLeaseEngine.HeartbeatResult.Expired,
            engine.heartbeat(tokenDigest),
        )
        assertEquals(ParentEditorLeaseEngine.Owner.IDLE, engine.state().owner)
    }

    @Test
    fun heartbeatCannotExtendPastTheHardCap() {
        val token = acquire()
        val tokenDigest = ParentWebCrypto.sha256Hex(token)
        val acquiredAt = now
        while (now - acquiredAt < ParentEditorLeaseEngine.WEB_HARD_CAP_MS -
            ParentEditorLeaseEngine.HEARTBEAT_MS
        ) {
            now += ParentEditorLeaseEngine.HEARTBEAT_MS
            assertTrue(engine.heartbeat(tokenDigest) is ParentEditorLeaseEngine.HeartbeatResult.Ok)
        }
        now += ParentEditorLeaseEngine.HEARTBEAT_MS
        assertEquals(
            ParentEditorLeaseEngine.HeartbeatResult.Expired,
            engine.heartbeat(tokenDigest),
        )
        assertEquals(ParentEditorLeaseEngine.Owner.IDLE, engine.state().owner)
    }

    @Test
    fun externalInvalidationReleasesTheLease() {
        val token = acquire()
        engine.invalidate("screen_off")
        assertFalse(engine.webTokenValid(ParentWebCrypto.sha256Hex(token)))
        assertEquals(ParentEditorLeaseEngine.Owner.IDLE, engine.state().owner)
    }
}
