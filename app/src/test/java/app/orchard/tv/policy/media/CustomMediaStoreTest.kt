package app.orchard.tv.policy.media

import android.content.Context
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
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
 * The upload cache decides what the TV may execute and what a remote delete may remove. These
 * tests pin the strict read contract (any field that cannot be used to play the file invalidates
 * the entry) and the GC guard that refuses to drop a file a hosted manifest still references.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CustomMediaStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        CustomMediaStore.clearAll(context)
        HostedMediaStore.clearAll(context)
    }

    @After
    fun tearDown() {
        CustomMediaStore.clearAll(context)
        HostedMediaStore.clearAll(context)
    }

    private fun writeEntry(
        kind: CustomMediaKind = CustomMediaKind.IMAGE,
        id: Long = 55L,
        bytes: Long = 16L,
        fileBytes: Long = bytes,
        sha: String = "b".repeat(64),
        version: Long = 1L,
        durationMs: Long = if (kind == CustomMediaKind.IMAGE) 0L else 10_000L,
        width: Int = PolicyImageValidation.WIDTH,
        height: Int = PolicyImageValidation.HEIGHT,
        fps: Int = 30,
        hasAudio: Boolean? = false,
        name: String = "测试素材",
        ext: String = if (kind == CustomMediaKind.AUDIO) "mp3" else "jpg",
        downloadedAt: Long = 1_000L,
        mutateInfo: ((JSONObject) -> Unit)? = null,
    ): File {
        val dir = CustomMediaStore.dirFor(context, kind, id)!!
        dir.mkdirs()
        File(dir, "data.bin").writeBytes(ByteArray(fileBytes.toInt()) { it.toByte() })
        val info = JSONObject()
            .put("id", id)
            .put("kind", kind.wire)
            .put("name", name)
            .put("ext", ext)
            .put("sha256", sha)
            .put("bytes", bytes)
            .put("durationMs", durationMs)
            .put("width", width)
            .put("height", height)
            .put("fps", fps)
            .put("hasAudio", hasAudio ?: JSONObject.NULL)
            .put("version", version)
            .put("downloadedAt", downloadedAt)
        mutateInfo?.invoke(info)
        File(dir, "info.json").writeText(info.toString())
        return dir
    }

    private fun hostedItem(key: String = "custom:55", memberId: Long = 7L): HostedMediaItem =
        HostedMediaItem(
            key = key,
            file = "files/a.jpg",
            poster = null,
            kind = PolicyMediaKind.IMAGE,
            sizeBytes = 16L,
            durationMs = 0L,
            hasAudio = false,
        )

    private fun hostedAudio(key: String = "custom:55"): HostedAudio = HostedAudio(
        key = key,
        id = key.removePrefix("custom:"),
        source = "custom",
        name = "上传音频",
        nameEn = null,
        artist = null,
        license = null,
        file = "files/bgm.mp3",
        sha256 = "a".repeat(64),
        sizeBytes = 16L,
        durationMs = 150_000L,
        ext = "mp3",
        version = 1L,
        createdAtMs = 2L,
    )

    private fun writeManifest(
        memberId: Long = 7L,
        items: List<HostedMediaItem> = emptyList(),
        quizIntro: HostedMediaItem? = null,
        audio: HostedAudio? = null,
        quizAudio: HostedAudio? = null,
    ): Boolean = HostedMediaStore.writeManifest(
        context,
        HostedManifest(
            memberId = memberId,
            publishedAtMs = 1L,
            sound = true,
            wallpaper = HostedWallpaper(
                mode = HostedWallpaperMode.CAROUSEL,
                activeKey = null,
                items = items,
                intervalMs = HOSTED_WALLPAPER_INTERVAL_MS,
            ),
            quizIntro = quizIntro,
            source = HostedMediaSource(memberId, items.size, null),
            audio = audio,
            quizAudio = quizAudio,
            quizAudioEnabled = quizAudio != null,
        ),
    )

    @Test
    fun imageEntryRoundTripsEveryField() {
        writeEntry(downloadedAt = 4_000L)
        val local = CustomMediaStore.read(context, CustomMediaKind.IMAGE, 55L)
        assertNotNull(local)
        local!!
        assertEquals(55L, local.id)
        assertEquals("custom:55", local.key)
        assertEquals(CustomMediaKind.IMAGE, local.kind)
        assertEquals("b".repeat(64), local.sha256)
        assertEquals(16L, local.sizeBytes)
        assertEquals(0L, local.durationMs)
        assertEquals(PolicyImageValidation.WIDTH, local.width)
        assertEquals(PolicyImageValidation.HEIGHT, local.height)
        assertEquals(false, local.hasAudio)
        assertEquals(1L, local.version)
        assertEquals("测试素材", local.name)
        assertEquals(4_000L, local.downloadedAtMs)
    }

    @Test
    fun unknownHasAudioStaysNullInsteadOfBecomingSilent() {
        writeEntry(hasAudio = null)
        val local = CustomMediaStore.read(context, CustomMediaKind.IMAGE, 55L)
        assertNotNull(local)
        assertNull(local!!.hasAudio)
    }

    @Test
    fun anEntryReadAsTheWrongKindIsRejected() {
        writeEntry(kind = CustomMediaKind.VIDEO, ext = "mp4", width = 1920, height = 1080)
        assertNull(CustomMediaStore.read(context, CustomMediaKind.IMAGE, 55L))
        assertNotNull(CustomMediaStore.read(context, CustomMediaKind.VIDEO, 55L))
    }

    @Test
    fun aFileShorterThanTheDeclaredByteCountIsRejected() {
        writeEntry(bytes = 16L, fileBytes = 8L)
        assertNull(CustomMediaStore.read(context, CustomMediaKind.IMAGE, 55L))
    }

    @Test
    fun aNegativeVersionIsRejected() {
        writeEntry(version = -1L)
        assertNull(CustomMediaStore.read(context, CustomMediaKind.IMAGE, 55L))
    }

    @Test
    fun aMalformedShaIsRejected() {
        writeEntry(sha = "not-a-sha")
        assertNull(CustomMediaStore.read(context, CustomMediaKind.IMAGE, 55L))
    }

    @Test
    fun anInfoIdThatDoesNotMatchTheDirectoryIsRejected() {
        writeEntry(mutateInfo = { it.put("id", 56) })
        assertNull(CustomMediaStore.read(context, CustomMediaKind.IMAGE, 55L))
    }

    @Test
    fun anImageWithTheWrongDimensionsIsRejected() {
        writeEntry(width = 1280, height = 720)
        assertNull(CustomMediaStore.read(context, CustomMediaKind.IMAGE, 55L))
    }

    @Test
    fun aVideoWithTheWrongAspectRatioIsRejected() {
        writeEntry(kind = CustomMediaKind.VIDEO, ext = "mp4", width = 1920, height = 1000)
        assertNull(CustomMediaStore.read(context, CustomMediaKind.VIDEO, 55L))
    }

    @Test
    fun aVideoWithoutDurationIsRejected() {
        writeEntry(kind = CustomMediaKind.VIDEO, ext = "mp4", durationMs = 0L)
        assertNull(CustomMediaStore.read(context, CustomMediaKind.VIDEO, 55L))
    }

    @Test
    fun anAudioWithoutDurationIsRejected() {
        writeEntry(kind = CustomMediaKind.AUDIO, ext = "mp3", durationMs = 0L)
        assertNull(CustomMediaStore.read(context, CustomMediaKind.AUDIO, 55L))
    }

    @Test
    fun anAudioLongerThanTheProbeLimitIsRejected() {
        writeEntry(
            kind = CustomMediaKind.AUDIO,
            ext = "mp3",
            durationMs = PolicyAudioLibrary.MAX_AUDIO_DURATION_MS + 1,
        )
        assertNull(CustomMediaStore.read(context, CustomMediaKind.AUDIO, 55L))
    }

    @Test
    fun readByKeyFindsMediaAndAudio() {
        writeEntry(kind = CustomMediaKind.IMAGE, id = 55L)
        writeEntry(kind = CustomMediaKind.AUDIO, id = 77L, ext = "mp3")
        assertEquals(CustomMediaKind.IMAGE, CustomMediaStore.readByKey(context, "custom:55")?.kind)
        assertEquals(CustomMediaKind.AUDIO, CustomMediaStore.readByKey(context, "custom:77")?.kind)
        assertNull(CustomMediaStore.readByKey(context, "custom:99"))
        assertNull(CustomMediaStore.readByKey(context, "video:55"))
    }

    @Test
    fun idOfKeyOnlyAcceptsTheCustomPrefixAndADecimalId() {
        assertEquals(55L, CustomMediaStore.idOfKey("custom:55"))
        assertNull(CustomMediaStore.idOfKey("media:55"))
        assertNull(CustomMediaStore.idOfKey("custom:0"))
        assertNull(CustomMediaStore.idOfKey("custom:../55"))
        assertNull(CustomMediaStore.idOfKey("custom:"))
    }

    @Test
    fun listSkipsIncompleteEntriesAndForeignDirectories() {
        writeEntry(kind = CustomMediaKind.IMAGE, id = 1L, downloadedAt = 1_000L)
        writeEntry(kind = CustomMediaKind.AUDIO, id = 2L, ext = "mp3", downloadedAt = 3_000L)
        val broken = writeEntry(kind = CustomMediaKind.IMAGE, id = 3L)
        File(broken, "data.bin").delete()
        File(CustomMediaStore.bucketDir(context, CustomMediaKind.VIDEO), "notes").mkdirs()
        File(CustomMediaStore.bucketDir(context, CustomMediaKind.VIDEO), "0").mkdirs()

        val listed = CustomMediaStore.list(context)
        assertEquals(listOf(2L, 1L), listed.map { it.id })
    }

    @Test
    fun deleteRemovesTheEntry() {
        writeEntry()
        assertTrue(CustomMediaStore.delete(context, CustomMediaKind.IMAGE, 55L))
        assertNull(CustomMediaStore.read(context, CustomMediaKind.IMAGE, 55L))
        assertFalse(CustomMediaStore.delete(context, CustomMediaKind.IMAGE, 55L))
    }

    @Test
    fun aHostedWallpaperItemProtectsTheUploadFromGarbageCollection() {
        writeEntry(id = 55L)
        assertTrue(writeManifest(items = listOf(hostedItem("custom:55"))))
        assertTrue(CustomMediaStore.isReferencedByHosted(context, 55L))
        assertFalse(CustomMediaStore.isReferencedByHosted(context, 56L))
    }

    @Test
    fun aHostedQuizIntroProtectsTheUploadFromGarbageCollection() {
        writeEntry(id = 55L)
        assertTrue(writeManifest(quizIntro = hostedItem("custom:55")))
        assertTrue(CustomMediaStore.isReferencedByHosted(context, 55L))
    }

    @Test
    fun hostedBackgroundAndQuizAudioAreBothProtected() {
        writeEntry(kind = CustomMediaKind.AUDIO, id = 77L, ext = "mp3")
        assertTrue(writeManifest(audio = hostedAudio("custom:77")))
        assertTrue(CustomMediaStore.isReferencedByHosted(context, 77L))

        writeEntry(kind = CustomMediaKind.AUDIO, id = 88L, ext = "mp3")
        assertTrue(writeManifest(quizAudio = hostedAudio("custom:88")))
        assertTrue(CustomMediaStore.isReferencedByHosted(context, 88L))
    }

    @Test
    fun aRetiredPreloadIndexStillProtectsTheUpload() {
        writeEntry(id = 55L)
        assertTrue(
            HostedMediaStore.writePreloadIndex(
                context,
                HostedPreloadIndex(
                    memberId = 7L,
                    requestId = "req-1",
                    libraryVersion = "v1",
                    updatedAtMs = 1L,
                    items = listOf(hostedItem("custom:55")),
                ),
            ),
        )
        assertTrue(CustomMediaStore.isReferencedByHosted(context, 55L))
    }

    @Test
    fun cleanupStaleOnlyRemovesKilledDownloadTemporaries() {
        val download = CustomMediaStore.downloadDir(context)
        val stale = File(download, "custom-video-1.part").apply { writeBytes(ByteArray(4)) }
        stale.setLastModified(System.currentTimeMillis() - 48L * 60 * 60 * 1000)
        val fresh = File(download, "custom-video-2.part").apply { writeBytes(ByteArray(4)) }
        val ready = writeEntry()

        CustomMediaStore.cleanupStale(context)

        assertFalse(stale.exists())
        assertTrue(fresh.exists())
        assertTrue(File(ready, "data.bin").isFile)
    }

    @Test
    fun clearAllDropsTheWholeUploadCache() {
        writeEntry()
        writeEntry(kind = CustomMediaKind.AUDIO, id = 77L, ext = "mp3")
        CustomMediaStore.clearAll(context)
        assertNull(CustomMediaStore.read(context, CustomMediaKind.IMAGE, 55L))
        assertNull(CustomMediaStore.read(context, CustomMediaKind.AUDIO, 77L))
    }

    @Test
    fun maxBytesMatchesEachKind() {
        assertEquals(PolicyVideoValidation.MAX_WALLPAPER_FILE_BYTES, CustomMediaStore.maxBytes(CustomMediaKind.VIDEO))
        assertEquals(PolicyImageValidation.MAX_STORED_BYTES, CustomMediaStore.maxBytes(CustomMediaKind.IMAGE))
        assertEquals(PolicyAudioLibrary.MAX_AUDIO_BYTES, CustomMediaStore.maxBytes(CustomMediaKind.AUDIO))
    }
}