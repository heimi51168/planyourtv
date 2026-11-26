package app.orchard.tv.policy.media

import android.content.Context
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Remote commands only ever touch the cache; configuration is cleared by the backend before a
 * delete is sent. These tests pin the validation surface and the two execution safety rules:
 * a malformed custom payload is rejected, and an upload still named by a hosted manifest is not
 * deleted under a running surface.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CloudMediaCommandsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        CustomMediaStore.clearAll(context)
        HostedMediaStore.clearAll(context)
        CloudMediaStateReporter.disable()
        CustomMediaDownloader.forget(context, CustomMediaKind.IMAGE, 55L)
        CustomMediaDownloader.forget(context, CustomMediaKind.VIDEO, 55L)
        CustomMediaDownloader.forget(context, CustomMediaKind.AUDIO, 77L)
    }

    @After
    fun tearDown() {
        CustomMediaStore.clearAll(context)
        HostedMediaStore.clearAll(context)
        CloudMediaStateReporter.disable()
    }

    private fun writeEntry(
        kind: CustomMediaKind = CustomMediaKind.IMAGE,
        id: Long = 55L,
    ) {
        val dir = CustomMediaStore.dirFor(context, kind, id)!!
        dir.mkdirs()
        File(dir, "data.bin").writeBytes(ByteArray(16) { it.toByte() })
        val info = JSONObject()
            .put("id", id)
            .put("kind", kind.wire)
            .put("name", "测试")
            .put("ext", if (kind == CustomMediaKind.AUDIO) "mp3" else "jpg")
            .put("sha256", "b".repeat(64))
            .put("bytes", 16)
            .put("durationMs", if (kind == CustomMediaKind.IMAGE) 0 else 10_000)
            .put("width", PolicyImageValidation.WIDTH)
            .put("height", PolicyImageValidation.HEIGHT)
            .put("fps", 30)
            .put("hasAudio", false)
            .put("version", 1)
        File(dir, "info.json").writeText(info.toString())
    }

    private fun customPayload(
        action: String = "deleteLocal",
        kind: String = "CUSTOM_MEDIA",
        id: String = "55",
        itemKind: String? = null,
        sha: String = "b".repeat(64),
        url: String = "https://example.invalid/a.jpg",
        sizeBytes: Long = 16L,
        durationMs: Long = 0L,
    ): JSONObject {
        val custom = JSONObject()
            .put("id", id)
            .put("name", "测试")
            .put("ext", "jpg")
            .put("version", 1)
            .put("url", url)
            .put("sha256", sha)
            .put("sizeBytes", sizeBytes)
            .put("durationMs", durationMs)
        itemKind?.let { custom.put("kind", it) }
        return JSONObject()
            .put("schema", 1)
            .put("action", action)
            .put("kind", kind)
            .put("custom", custom)
    }

    @Test
    fun aMissingPayloadIsRejected() {
        assertFalse(CloudMediaCommands.handle(context, null))
    }

    @Test
    fun anUnsupportedSchemaIsRejected() {
        assertFalse(
            CloudMediaCommands.handle(
                context,
                JSONObject().put("schema", 2).put("action", "reportState"),
            ),
        )
    }

    @Test
    fun anUnknownActionIsRejected() {
        assertFalse(
            CloudMediaCommands.handle(
                context,
                JSONObject().put("schema", 1).put("action", "formatDisk"),
            ),
        )
    }

    @Test
    fun anUnknownKindIsRejected() {
        assertFalse(
            CloudMediaCommands.handle(
                context,
                JSONObject()
                    .put("schema", 1)
                    .put("action", "download")
                    .put("kind", "FIRMWARE"),
            ),
        )
    }

    @Test
    fun aCustomPayloadWithAMalformedShaIsRejected() {
        assertFalse(
            CloudMediaCommands.handle(
                context,
                customPayload(action = "download", sha = "deadbeef"),
            ),
        )
    }

    @Test
    fun aCustomPayloadWithAPlainHttpUrlIsRejected() {
        assertFalse(
            CloudMediaCommands.handle(
                context,
                customPayload(action = "download", url = "http://example.invalid/a.jpg"),
            ),
        )
    }

    @Test
    fun anOversizedCustomPayloadIsRejected() {
        assertFalse(
            CloudMediaCommands.handle(
                context,
                customPayload(
                    action = "download",
                    sizeBytes = PolicyImageValidation.MAX_STORED_BYTES + 1,
                ),
            ),
        )
    }

    @Test
    fun aCustomAudioCommandCannotTargetAVideoPayload() {
        assertFalse(
            CloudMediaCommands.handle(
                context,
                customPayload(
                    action = "download",
                    kind = "CUSTOM_AUDIO",
                    itemKind = "VIDEO",
                    durationMs = 10_000L,
                ),
            ),
        )
    }

    @Test
    fun reportStateIsRejectedBeforeTheMediaCenterIsEnabled() {
        assertFalse(
            CloudMediaCommands.handle(
                context,
                JSONObject().put("schema", 1).put("action", "reportState"),
            ),
        )
    }

    @Test
    fun deleteLocalRemovesAnUnreferencedCachedUpload() {
        writeEntry(id = 55L)
        assertTrue(CloudMediaCommands.handle(context, customPayload()))
        assertNull(CustomMediaStore.read(context, CustomMediaKind.IMAGE, 55L))
    }

    @Test
    fun deleteLocalRemovesACachedAudioUpload() {
        writeEntry(kind = CustomMediaKind.AUDIO, id = 77L)
        assertTrue(
            CloudMediaCommands.handle(
                context,
                customPayload(kind = "CUSTOM_AUDIO", id = "77"),
            ),
        )
        assertNull(CustomMediaStore.read(context, CustomMediaKind.AUDIO, 77L))
    }

    @Test
    fun deleteLocalSucceedsWhenTheTvNeverDownloadedTheUpload() {
        assertTrue(CloudMediaCommands.handle(context, customPayload(id = "99")))
    }

    @Test
    fun deleteLocalIsRejectedWhileAHostedManifestStillNamesTheUpload() {
        writeEntry(id = 55L)
        assertTrue(
            HostedMediaStore.writeManifest(
                context,
                HostedManifest(
                    memberId = 7L,
                    publishedAtMs = 1L,
                    sound = true,
                    wallpaper = HostedWallpaper(
                        mode = HostedWallpaperMode.CAROUSEL,
                        activeKey = null,
                        items = listOf(
                            HostedMediaItem(
                                key = "custom:55",
                                file = "files/a.jpg",
                                poster = null,
                                kind = PolicyMediaKind.IMAGE,
                                sizeBytes = 16L,
                                durationMs = 0L,
                                hasAudio = false,
                            ),
                        ),
                        intervalMs = HOSTED_WALLPAPER_INTERVAL_MS,
                    ),
                    quizIntro = null,
                    source = HostedMediaSource(7L, 1, null),
                ),
            ),
        )
        assertFalse(CloudMediaCommands.handle(context, customPayload()))
        assertNotNull(CustomMediaStore.read(context, CustomMediaKind.IMAGE, 55L))
    }

    @Test
    fun aDeleteOfAnEntryThatIsAlreadyGoneIsStillAccepted() {
        writeEntry(id = 55L)
        assertTrue(CloudMediaCommands.handle(context, customPayload()))
        assertTrue(CloudMediaCommands.handle(context, customPayload()))
    }
}