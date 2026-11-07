package app.orchard.tv.policy

import android.util.Base64
import com.planyourtv.policy.CommandSigningState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature

/**
 * These cases are the execution gate for every MQTT command. The broker account is per-device,
 * but a modified APK can extract it; only the server signature makes a command authoritative.
 * Replay/expiry/wrong-device checks must fail closed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MqttCommandVerifierTest {
    private lateinit var keys: KeyPair
    private lateinit var state: CommandSigningState

    @Before
    fun setUp() {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(256)
        keys = generator.generateKeyPair()
        state = CommandSigningState(
            known = true,
            enabled = true,
            keyId = "test-key-1",
            publicKey = Base64.encodeToString(keys.public.encoded, Base64.NO_WRAP),
            fetchedAtMs = System.currentTimeMillis(),
        )
    }

    @Test
    fun validSignedCommandReturnsInnerPayload() {
        val envelope = command("tv-1", "cmd-valid")
        val payload = MqttCommandVerifier.verify(envelope, "tv-1", state)
        assertNotNull(payload)
        assertEquals("unlock", payload!!.optString("action"))
    }

    @Test
    fun unsignedAndTamperedCommandsAreRejected() {
        val unsigned = JSONObject()
            .put("commandId", "cmd-unsigned")
            .put("deviceNo", "tv-1")
            .put("action", "unlock")
        assertNull(MqttCommandVerifier.verify(unsigned, "tv-1", state))

        val tampered = command("tv-1", "cmd-tampered").put("sig", "AAAA")
        assertNull(MqttCommandVerifier.verify(tampered, "tv-1", state))
    }

    @Test
    fun outerAndInnerMismatchIsRejected() {
        val now = System.currentTimeMillis()
        val payload = payload("tv-1", "cmd-inner", now - 1_000, now + 60_000)
        val envelope = envelope("tv-1", "cmd-outer", now - 1_000, now + 60_000, payload)
        assertNull(MqttCommandVerifier.verify(envelope, "tv-1", state))
    }

    @Test
    fun expiredCommandIsRejected() {
        val now = System.currentTimeMillis()
        val envelope = command("tv-1", "cmd-expired", now - 300_000, now - 1_000)
        assertNull(MqttCommandVerifier.verify(envelope, "tv-1", state))
    }

    @Test
    fun commandIssuedTooFarInFutureIsRejected() {
        val now = System.currentTimeMillis()
        val envelope = command("tv-1", "cmd-future", now + 180_000, now + 240_000)
        assertNull(MqttCommandVerifier.verify(envelope, "tv-1", state))
    }

    @Test
    fun replayIsRejected() {
        val envelope = command("tv-1", "cmd-replay")
        assertNotNull(MqttCommandVerifier.verify(envelope, "tv-1", state))
        assertNull(MqttCommandVerifier.verify(envelope, "tv-1", state))
    }

    @Test
    fun wrongDeviceIsRejected() {
        val envelope = command("tv-2", "cmd-other-device")
        assertNull(MqttCommandVerifier.verify(envelope, "tv-1", state))
    }

    @Test
    fun wrongKeyIdAndDisabledStateAreRejected() {
        val envelope = command("tv-1", "cmd-wrong-key")
        assertNull(MqttCommandVerifier.verify(envelope, "tv-1", state.copy(keyId = "other-key")))
        assertNull(MqttCommandVerifier.verify(envelope, "tv-1", state.copy(enabled = false)))
        assertNull(MqttCommandVerifier.verify(envelope, "tv-1", state.copy(publicKey = "")))
    }

    private fun command(deviceNo: String, commandId: String, issuedAt: Long = 0L, expiresAt: Long = 0L): JSONObject {
        val now = System.currentTimeMillis()
        val issued = if (issuedAt == 0L) now - 1_000 else issuedAt
        val expires = if (expiresAt == 0L) now + 60_000 else expiresAt
        return envelope(deviceNo, commandId, issued, expires, payload(deviceNo, commandId, issued, expires))
    }

    private fun payload(deviceNo: String, commandId: String, issuedAt: Long, expiresAt: Long): JSONObject =
        JSONObject()
            .put("deviceNo", deviceNo)
            .put("commandId", commandId)
            .put("issuedAt", issuedAt)
            .put("expiresAt", expiresAt)
            .put("action", "unlock")

    private fun envelope(
        deviceNo: String,
        commandId: String,
        issuedAt: Long,
        expiresAt: Long,
        payload: JSONObject,
    ): JSONObject {
        val payloadBytes = payload.toString().toByteArray(Charsets.UTF_8)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keys.private)
        signer.update(payloadBytes)
        return JSONObject()
            .put("commandId", commandId)
            .put("deviceNo", deviceNo)
            .put("issuedAt", issuedAt)
            .put("expiresAt", expiresAt)
            .put("signedPayload", Base64.encodeToString(payloadBytes, Base64.NO_WRAP))
            .put("sig", Base64.encodeToString(signer.sign(), Base64.NO_WRAP))
            .put("keyId", state.keyId)
            .put("signatureAlg", "SHA256withECDSA")
    }
}
