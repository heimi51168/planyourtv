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
 * The hosted cache is the only data source of the CLOUD child surfaces. These tests pin the
 * two properties execution depends on: a complete manifest is published atomically, and a
 * missing file is skipped rather than replaced by a local upload.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HostedMediaStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        HostedMediaStore.clearAll(context)
    }

    @After
    fun tearDown() {
        HostedMediaStore.clearAll(context)
    }

    private fun writeMedia(memberId: Long, name: String, size: Int = 16): File {
        val file = File(HostedMediaStore.filesDir(context, memberId), name)
        file.writeBytes(ByteArray(size) { it.toByte() })
        return file
    }

    private fun item(
        key: String,
        file: String,
        hasAudio: Boolean? = false,
        durationMs: Long = 5_000L,
        kind: PolicyMediaKind = PolicyMediaKind.VIDEO,
        poster: String? = null,
    ): HostedMediaItem = HostedMediaItem(
        key = key,
        file = file,
        poster = poster,
        kind = kind,
        sizeBytes = 16L,
        durationMs = if (kind == PolicyMediaKind.IMAGE) 0L else durationMs,
        hasAudio = hasAudio,
    )

    private fun audio(
        file: String = "files/bgm.mp3",
        key: String = "audio:official:playful:1",
        id: String = "official:playful",
        source: String = "official",
        sizeBytes: Long = 16L,
        durationMs: Long = 150_544L,
    ): HostedAudio = HostedAudio(
        key = key,
        id = id,
        source = source,
        name = "俏皮时光",
        nameEn = "Playful",
        artist = "Ahjay Stelino",
        license = "Authorized for PlanYourTV",
        file = file,
        sha256 = "a".repeat(64),
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        ext = "mp3",
        version = 1L,
        createdAtMs = 2L,
    )

    private fun manifest(
        memberId: Long = 7L,
        mode: HostedWallpaperMode = HostedWallpaperMode.CAROUSEL,
        activeKey: String? = null,
        items: List<HostedMediaItem>,
        quizIntro: HostedMediaItem? = null,
        sound: Boolean = true,
        rotateDays: Int = 1,
        source: HostedMediaOrigin = HostedMediaOrigin.CUSTOM,
        intervalMs: Long = HOSTED_WALLPAPER_INTERVAL_MS,
        audio: HostedAudio? = null,
        quizAudio: HostedAudio? = null,
        quizAudioEnabled: Boolean = false,
        schema: Int = HOSTED_MEDIA_SCHEMA,
    ): HostedManifest = HostedManifest(
        memberId = memberId,
        publishedAtMs = 1L,
        sound = sound,
        wallpaper = HostedWallpaper(mode, activeKey, items, rotateDays, source, intervalMs),
        quizIntro = quizIntro,
        source = HostedMediaSource(memberId, items.size, null),
        audio = audio,
        quizAudio = quizAudio,
        quizAudioEnabled = quizAudioEnabled,
        schema = schema,
    )

    @Test
    fun manifestRoundTripsThePlaylistInterval() {
        writeMedia(7L, "a.jpg")
        writeMedia(7L, "b.jpg")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                mode = HostedWallpaperMode.CAROUSEL,
                source = HostedMediaOrigin.PLAYLIST,
                intervalMs = 45_000L,
                items = listOf(
                    item("theme:a.jpg:1", "files/a.jpg", kind = PolicyMediaKind.IMAGE),
                    item("theme:b.jpg:1", "files/b.jpg", kind = PolicyMediaKind.IMAGE),
                ),
            ),
        )

        val snapshot = HostedMediaStore.readSnapshot(context, 7L)!!
        assertEquals(45_000L, snapshot.selection.intervalMs)
        assertEquals(HostedMediaOrigin.PLAYLIST, HostedMediaStore.readManifest(context, 7L)!!.wallpaper.source)
    }

    @Test
    fun schemaThreeWithoutAValidIntervalIsNotRead() {
        // The planner never writes this; a corrupt schema 3 manifest must fail visibly instead of
        // guessing a hold for the child's lock screen.
        HostedMediaStore.writeManifest(
            context,
            manifest(
                intervalMs = 5_000L,
                items = listOf(item("custom:11:1", "files/a.mp4")),
            ),
        )
        val file = File(HostedMediaStore.memberDir(context, 7L), "manifest.json")
        file.writeText(JSONObject(file.readText()).put("schema", 3).toString())

        assertNull(HostedMediaStore.readManifest(context, 7L))
    }

    @Test
    fun schemaFourRoundTripsBackgroundMusic() {
        writeMedia(7L, "a.mp4")
        writeMedia(7L, "bgm.mp3")
        val published = manifest(
            items = listOf(item("custom:11:1", "files/a.mp4")),
            audio = audio(),
        )
        assertTrue(HostedMediaStore.writeManifest(context, published))

        val read = HostedMediaStore.readManifest(context, 7L)!!
        assertEquals(HOSTED_MEDIA_SCHEMA, read.schema)
        assertEquals("official:playful", read.audio!!.id)
        assertEquals("俏皮时光", read.audio!!.name)
        assertEquals(150_544L, read.audio!!.durationMs)

        val snapshot = HostedMediaStore.readSnapshot(context, 7L)!!
        val entry = snapshot.audio!!
        assertEquals("official:playful", entry.id)
        assertEquals("俏皮时光", entry.name)
        assertEquals("bgm.mp3", entry.file.name)
        assertEquals(PolicyAudioEntry.SOURCE_OFFICIAL, entry.source)
    }

    @Test
    fun schemaFiveRoundTripsAPictureQuizIntroAndQuizAudio() {
        writeMedia(7L, "a.mp4")
        writeMedia(7L, "quiz.jpg")
        writeMedia(7L, "quiz.mp3")
        assertTrue(
            HostedMediaStore.writeManifest(
                context,
                manifest(
                    items = listOf(item("custom:11:1", "files/a.mp4")),
                    quizIntro = item(
                        "custom:13:1",
                        "files/quiz.jpg",
                        kind = PolicyMediaKind.IMAGE,
                    ),
                    quizAudio = audio(
                        file = "files/quiz.mp3",
                        key = "audio:custom:456:1760000000000",
                        id = "custom:456",
                        source = "custom",
                    ),
                    quizAudioEnabled = true,
                ),
            ),
        )

        val read = HostedMediaStore.readManifest(context, 7L)!!
        assertEquals(HOSTED_MEDIA_SCHEMA, read.schema)
        assertEquals(PolicyMediaKind.IMAGE, read.quizIntro!!.kind)
        assertEquals("custom:456", read.quizAudio!!.id)
        assertTrue(read.quizAudioEnabled)

        val snapshot = HostedMediaStore.readSnapshot(context, 7L)!!
        assertNotNull(snapshot.quizSource)
        assertEquals("custom:456", snapshot.quizAudio!!.id)
        assertTrue(snapshot.quizAudioEnabled)
    }

    @Test
    fun schemaFiveDisabledQuizAudioStaysCachedButIsNotExecuted() {
        writeMedia(7L, "a.mp4")
        writeMedia(7L, "quiz.mp3")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                items = listOf(item("custom:11:1", "files/a.mp4")),
                quizAudio = audio(file = "files/quiz.mp3"),
            ),
        )

        val read = HostedMediaStore.readManifest(context, 7L)!!
        assertNotNull(read.quizAudio)
        assertEquals(false, read.quizAudioEnabled)
        val snapshot = HostedMediaStore.readSnapshot(context, 7L)!!
        assertNotNull(snapshot.quizAudio)
        assertEquals(false, snapshot.quizAudioEnabled)
    }

    @Test
    fun schemaFourIgnoresQuizAudioEvenWhenTheFieldIsPresent() {
        // A manifest downgraded to schema 4 must never play audio the old contract cannot know.
        writeMedia(7L, "a.mp4")
        writeMedia(7L, "quiz.mp3")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                items = listOf(item("custom:11:1", "files/a.mp4")),
                quizAudio = audio(file = "files/quiz.mp3"),
                quizAudioEnabled = true,
            ),
        )
        val file = File(HostedMediaStore.memberDir(context, 7L), "manifest.json")
        file.writeText(JSONObject(file.readText()).put("schema", 4).toString())

        val read = HostedMediaStore.readManifest(context, 7L)!!
        assertEquals(4, read.schema)
        assertNull(read.quizAudio)
        assertEquals(false, read.quizAudioEnabled)
    }

    @Test
    fun malformedSchemaFiveQuizAudioIsNotRead() {
        writeMedia(7L, "a.mp4")
        writeMedia(7L, "quiz.mp3")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                items = listOf(item("custom:11:1", "files/a.mp4")),
                quizAudio = audio(file = "files/quiz.mp3"),
                quizAudioEnabled = true,
            ),
        )
        val file = File(HostedMediaStore.memberDir(context, 7L), "manifest.json")
        val json = JSONObject(file.readText())
        json.getJSONObject("quizAudio").put("sha256", "not-a-hash")
        file.writeText(json.toString())

        assertNull(HostedMediaStore.readManifest(context, 7L))
    }

    @Test
    fun unknownHasAudioIsWrittenConservatively() {
        // "Unknown" must never downgrade to "silent": a schema 4 reader would drop the film's
        // audio track from the sound decision.
        writeMedia(7L, "a.mp4")
        writeMedia(7L, "a.jpg")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                schema = 4,
                items = listOf(
                    item("custom:11:1", "files/a.mp4", hasAudio = null),
                    item(
                        "theme:a.jpg:1",
                        "files/a.jpg",
                        kind = PolicyMediaKind.IMAGE,
                        hasAudio = null,
                    ),
                ),
            ),
        )

        val file = File(HostedMediaStore.memberDir(context, 7L), "manifest.json")
        val items = JSONObject(file.readText())
            .getJSONObject("wallpaper")
            .getJSONArray("items")
        assertEquals(true, items.getJSONObject(0).getBoolean("hasAudio"))
        assertEquals(false, items.getJSONObject(1).getBoolean("hasAudio"))

        val read = HostedMediaStore.readManifest(context, 7L)!!
        assertEquals(true, read.wallpaper.items[0].hasAudio)
        assertEquals(false, read.wallpaper.items[1].hasAudio)
    }

    @Test
    fun schemaThreeReadsWithoutBackgroundMusic() {
        writeMedia(7L, "bgm.mp3")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                items = listOf(item("custom:11:1", "files/a.mp4")),
                audio = audio(),
            ),
        )
        val file = File(HostedMediaStore.memberDir(context, 7L), "manifest.json")
        file.writeText(JSONObject(file.readText()).put("schema", 3).toString())

        assertNull(HostedMediaStore.readManifest(context, 7L)!!.audio)
        assertNull(HostedMediaStore.readSnapshot(context, 7L)!!.audio)
    }

    @Test
    fun malformedSchemaFourAudioIsNotRead() {
        writeMedia(7L, "bgm.mp3")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                items = listOf(item("custom:11:1", "files/a.mp4")),
                audio = audio(),
            ),
        )
        val file = File(HostedMediaStore.memberDir(context, 7L), "manifest.json")
        val json = JSONObject(file.readText())
        json.getJSONObject("audio").put("sha256", "not-a-hash")
        file.writeText(json.toString())

        assertNull(HostedMediaStore.readManifest(context, 7L))
    }

    @Test
    fun missingBackgroundMusicFileProducesNoTrack() {
        writeMedia(7L, "a.mp4")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                items = listOf(item("custom:11:1", "files/a.mp4")),
                audio = audio(file = "files/missing.mp3"),
            ),
        )

        assertNull(HostedMediaStore.readSnapshot(context, 7L)!!.audio)
    }

    @Test
    fun cleanupKeepsTheSelectedBackgroundMusic() {
        val wallpaper = writeMedia(7L, "a.mp4")
        val bgm = writeMedia(7L, "bgm.mp3")
        val stale = writeMedia(7L, "stale.mp3")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                items = listOf(item("custom:11:1", "files/a.mp4")),
                audio = audio(),
            ),
        )

        HostedMediaStore.cleanupUnreferenced(
            context,
            HostedMediaStore.readManifest(context, 7L)!!,
        )

        assertTrue(wallpaper.isFile)
        assertTrue(bgm.isFile)
        assertFalse(stale.exists())
    }

    @Test
    fun schemaTwoManifestKeepsTheLegacyHold() {
        writeMedia(7L, "a.mp4")
        HostedMediaStore.writeManifest(
            context,
            manifest(items = listOf(item("custom:11:1", "files/a.mp4"))),
        )
        val file = File(HostedMediaStore.memberDir(context, 7L), "manifest.json")
        val json = JSONObject(file.readText()).put("schema", 2)
        json.getJSONObject("wallpaper").remove("intervalMs")
        file.writeText(json.toString())

        val manifest = HostedMediaStore.readManifest(context, 7L)!!
        assertEquals(HOSTED_WALLPAPER_INTERVAL_MS, manifest.wallpaper.intervalMs)
    }

    @Test
    fun carouselSnapshotResolvesEveryPublishedFile() {
        writeMedia(7L, "a.mp4")
        writeMedia(7L, "b.mp4")
        val published = manifest(
            items = listOf(
                item("custom:11:1", "files/a.mp4", hasAudio = true),
                item("custom:12:1", "files/b.mp4"),
            ),
        )
        assertTrue(HostedMediaStore.writeManifest(context, published))

        val snapshot = HostedMediaStore.readSnapshot(context, 7L)
        assertNotNull(snapshot)
        snapshot!!

        assertEquals(7L, snapshot.memberId)
        assertTrue(snapshot.sound)
        assertEquals(HOSTED_WALLPAPER_INTERVAL_MS, snapshot.selection.intervalMs)
        assertEquals(
            listOf("hosted:7:custom:11:1", "hosted:7:custom:12:1"),
            snapshot.selection.items.map { it.id },
        )
        assertTrue(snapshot.selection.items.first().source.hasAudio)
        assertEquals("hosted:7:custom:11:1", snapshot.lockRoleId)
        assertEquals(16L, snapshot.selection.items.first().source.uri.let { File(it.path!!).length() })
        assertNull(snapshot.quizSource)
    }

    @Test
    fun fixedSnapshotKeepsOnlyTheActiveFile() {
        writeMedia(7L, "a.mp4")
        writeMedia(7L, "b.mp4")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                mode = HostedWallpaperMode.FIXED,
                activeKey = "custom:12:1",
                items = listOf(
                    item("custom:11:1", "files/a.mp4"),
                    item("custom:12:1", "files/b.mp4"),
                ),
            ),
        )

        val snapshot = HostedMediaStore.readSnapshot(context, 7L)!!
        assertEquals(listOf("hosted:7:custom:12:1"), snapshot.selection.items.map { it.id })
        assertEquals("hosted:7:custom:12:1", snapshot.lockRoleId)
    }

    @Test
    fun fixedSnapshotUsesTheFirstFileWhenTheActiveOneIsMissing() {
        // V51 product rule: a fixed key that is not in the rotation uses the first item; a
        // rotation whose files are all gone still leaves the default wallpaper (no local file).
        writeMedia(7L, "a.mp4")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                mode = HostedWallpaperMode.FIXED,
                activeKey = "custom:12:1",
                items = listOf(
                    item("custom:11:1", "files/a.mp4"),
                    item("custom:12:1", "files/b.mp4"),
                ),
            ),
        )

        val snapshot = HostedMediaStore.readSnapshot(context, 7L)!!
        assertEquals(listOf("hosted:7:custom:11:1"), snapshot.selection.items.map { it.id })
        assertEquals("hosted:7:custom:11:1", snapshot.lockRoleId)
    }

    @Test
    fun fixedSnapshotWithoutFilesLeavesTheDefaultWallpaper() {
        HostedMediaStore.writeManifest(
            context,
            manifest(
                mode = HostedWallpaperMode.FIXED,
                activeKey = "custom:11:1",
                items = listOf(item("custom:11:1", "files/missing.mp4")),
            ),
        )

        val snapshot = HostedMediaStore.readSnapshot(context, 7L)!!
        assertTrue(snapshot.selection.items.isEmpty())
        assertEquals(HostedMediaRoute.NONE_ID, snapshot.lockRoleId)
    }

    @Test
    fun dailySnapshotSelectsOneItemPerEpochDayBlock() {
        writeMedia(7L, "a.jpg")
        writeMedia(7L, "b.jpg")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                mode = HostedWallpaperMode.DAILY,
                rotateDays = 1,
                source = HostedMediaOrigin.THEME,
                items = listOf(
                    item("theme:a.jpg:1", "files/a.jpg", kind = PolicyMediaKind.IMAGE),
                    item("theme:b.jpg:1", "files/b.jpg", kind = PolicyMediaKind.IMAGE),
                ),
            ),
        )
        val published = HostedMediaStore.readManifest(context, 7L)!!

        val even = HostedMediaStore.snapshotOf(context, published, todayEpochDay = 2L)
        val odd = HostedMediaStore.snapshotOf(context, published, todayEpochDay = 3L)

        assertEquals(listOf("hosted:7:theme:a.jpg:1"), even.selection.items.map { it.id })
        assertEquals(listOf("hosted:7:theme:b.jpg:1"), odd.selection.items.map { it.id })
    }

    @Test
    fun intervalRotationGroupsDays() {
        writeMedia(7L, "a.jpg")
        writeMedia(7L, "b.jpg")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                mode = HostedWallpaperMode.DAILY,
                rotateDays = 3,
                source = HostedMediaOrigin.THEME,
                items = listOf(
                    item("theme:a.jpg:1", "files/a.jpg", kind = PolicyMediaKind.IMAGE),
                    item("theme:b.jpg:1", "files/b.jpg", kind = PolicyMediaKind.IMAGE),
                ),
            ),
        )
        val published = HostedMediaStore.readManifest(context, 7L)!!

        // Epoch days 3..5 are group 1 (odd -> b); 6..8 are group 2 (even -> a).
        assertEquals(
            listOf("hosted:7:theme:b.jpg:1"),
            HostedMediaStore.snapshotOf(context, published, todayEpochDay = 3L)
                .selection.items.map { it.id },
        )
        assertEquals(
            listOf("hosted:7:theme:b.jpg:1"),
            HostedMediaStore.snapshotOf(context, published, todayEpochDay = 5L)
                .selection.items.map { it.id },
        )
        assertEquals(
            listOf("hosted:7:theme:a.jpg:1"),
            HostedMediaStore.snapshotOf(context, published, todayEpochDay = 6L)
                .selection.items.map { it.id },
        )
    }

    @Test
    fun hostedImageUsesItsOwnFileAsPoster() {
        writeMedia(7L, "a.jpg")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                mode = HostedWallpaperMode.CAROUSEL,
                source = HostedMediaOrigin.THEME,
                items = listOf(
                    item("theme:a.jpg:1", "files/a.jpg", kind = PolicyMediaKind.IMAGE),
                ),
            ),
        )

        val snapshot = HostedMediaStore.readSnapshot(context, 7L)!!
        val source = snapshot.selection.items.single().source
        assertEquals(PolicyMediaKind.IMAGE, source.kind)
        assertEquals(File(source.uri.path!!), source.poster)
        assertEquals(0L, source.durationMs)
        assertFalse(source.hasAudio)
    }

    @Test
    fun schemaOneManifestIsReadAsCustomWithOneDayRotation() {
        val dir = HostedMediaStore.memberDir(context, 7L).apply { mkdirs() }
        File(dir, "files").mkdirs()
        File(dir, "files/a.mp4").writeBytes(ByteArray(16))
        File(dir, "manifest.json").writeText(
            JSONObject()
                .put("schema", 1)
                .put("memberId", 7L)
                .put("publishedAtMs", 1L)
                .put("sound", true)
                .put(
                    "wallpaper",
                    JSONObject()
                        .put("mode", "CAROUSEL")
                        .put("activeKey", JSONObject.NULL)
                        .put(
                            "items",
                            org.json.JSONArray().put(
                                JSONObject()
                                    .put("key", "custom:11:1")
                                    .put("file", "files/a.mp4")
                                    .put("poster", JSONObject.NULL)
                                    .put("kind", "VIDEO")
                                    .put("sizeBytes", 16L)
                                    .put("durationMs", 5_000L)
                                    .put("hasAudio", false),
                            ),
                        ),
                )
                .put("quizIntro", JSONObject.NULL)
                .put("source", JSONObject().put("learnerId", 7L).put("itemCount", 1))
                .toString(),
        )

        val manifest = HostedMediaStore.readManifest(context, 7L)
        assertNotNull(manifest)
        assertEquals(HostedMediaOrigin.CUSTOM, manifest!!.wallpaper.source)
        assertEquals(1, manifest.wallpaper.rotateDays)
        assertEquals(listOf("hosted:7:custom:11:1"), HostedMediaStore.snapshotOf(context, manifest)
            .selection.items.map { it.id })
    }

    @Test
    fun missingCarouselFileIsSkipped() {
        writeMedia(7L, "a.mp4")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                items = listOf(
                    item("custom:11:1", "files/a.mp4"),
                    item("custom:12:1", "files/missing.mp4"),
                ),
            ),
        )

        val snapshot = HostedMediaStore.readSnapshot(context, 7L)!!
        assertEquals(listOf("hosted:7:custom:11:1"), snapshot.selection.items.map { it.id })
    }

    @Test
    fun quizIntroNeedsItsOwnFile() {
        writeMedia(7L, "a.mp4")
        writeMedia(7L, "intro.mp4")
        HostedMediaStore.writeManifest(
            context,
            manifest(
                items = listOf(item("custom:11:1", "files/a.mp4")),
                quizIntro = item("custom:21:1", "files/intro.mp4"),
            ),
        )

        val snapshot = HostedMediaStore.readSnapshot(context, 7L)!!
        assertNotNull(snapshot.quizSource)
        assertEquals("intro.mp4", File(snapshot.quizSource!!.uri.path!!).name)
    }

    @Test
    fun manifestBelongingToAnotherMemberIsNotAdopted() {
        HostedMediaStore.writeManifest(
            context,
            manifest(items = listOf(item("custom:11:1", "files/a.mp4"))),
        )
        val source = File(HostedMediaStore.memberDir(context, 7L), "manifest.json")
        val target = File(HostedMediaStore.memberDir(context, 8L), "manifest.json")
        target.parentFile?.mkdirs()
        target.writeText(source.readText())

        assertNull(HostedMediaStore.readManifest(context, 8L))
        assertNull(HostedMediaStore.readSnapshot(context, 8L))
    }

    @Test
    fun unsupportedSchemaIsNotRead() {
        val dir = HostedMediaStore.memberDir(context, 7L).apply { mkdirs() }
        File(dir, "manifest.json").writeText(
            JSONObject().put("schema", HOSTED_MEDIA_SCHEMA + 1).put("memberId", 7L).toString(),
        )

        assertNull(HostedMediaStore.readManifest(context, 7L))
    }

    @Test
    fun fileForRejectsTraversalAndNestedNames() {
        assertNull(HostedMediaStore.fileFor(context, 7L, null))
        assertNull(HostedMediaStore.fileFor(context, 7L, "a.mp4"))
        assertNull(HostedMediaStore.fileFor(context, 7L, "files/../a.mp4"))
        assertNull(HostedMediaStore.fileFor(context, 7L, "files/a/b.mp4"))
        assertNull(HostedMediaStore.fileFor(context, 7L, "files/.part-1.tmp"))
        assertNotNull(HostedMediaStore.fileFor(context, 7L, "files/a.mp4"))
    }

    @Test
    fun cleanupRemovesUnreferencedFilesAndKeepsPosters() {
        val keep = writeMedia(7L, "keep.mp4")
        val poster = writeMedia(7L, "keep.poster.jpg")
        val stale = writeMedia(7L, "stale.mp4")
        val entry = item("custom:11:1", "files/keep.mp4").copy(poster = "files/keep.poster.jpg")
        HostedMediaStore.writeManifest(
            context,
            manifest(items = listOf(entry)),
        )

        HostedMediaStore.cleanupUnreferenced(
            context,
            HostedMediaStore.readManifest(context, 7L)!!,
        )

        assertTrue(keep.isFile)
        assertTrue(poster.isFile)
        assertFalse(stale.exists())
    }

    @Test
    fun evictionRemovesOnlyNonActiveMembersOldestFirst() {
        writeMedia(1L, "a.mp4", size = 4096)
        writeMedia(2L, "b.mp4", size = 4096)
        File(HostedMediaStore.memberDir(context, 1L), "last_used.json")
            .writeText(JSONObject().put("atMs", 10L).toString())
        File(HostedMediaStore.memberDir(context, 2L), "last_used.json")
            .writeText(JSONObject().put("atMs", 20L).toString())

        val activeBytes = HostedMediaStore.memberBytes(context, 2L)
        HostedMediaStore.enforceTotalLimit(
            context,
            activeMemberId = 2L,
            maxBytes = activeBytes,
        )

        assertFalse(HostedMediaStore.memberDir(context, 1L).exists())
        assertTrue(HostedMediaStore.memberDir(context, 2L).isDirectory)
    }

    @Test
    fun touchRecordsTheLastPublishedMember() {
        writeMedia(7L, "a.mp4")
        HostedMediaStore.touch(context, 7L)

        assertEquals(7L, HostedMediaStore.lastMemberId(context))
        assertTrue(File(HostedMediaStore.memberDir(context, 7L), "last_used.json").isFile)
    }

    private fun preloadIndex(
        memberId: Long = 7L,
        requestId: String = "req-1",
        libraryVersion: String = "ver-1",
        items: List<HostedMediaItem>,
    ): HostedPreloadIndex = HostedPreloadIndex(
        memberId = memberId,
        requestId = requestId,
        libraryVersion = libraryVersion,
        updatedAtMs = 5L,
        items = items,
    )

    @Test
    fun preloadIndexRoundTripsTheWholeLibrary() {
        writeMedia(7L, "a.mp4")
        val index = preloadIndex(
            items = listOf(
                item("custom:11:1", "files/a.mp4", hasAudio = true),
                item("theme:kids.jpg:1", "files/kids.jpg", kind = PolicyMediaKind.IMAGE),
            ),
        )

        assertTrue(HostedMediaStore.writePreloadIndex(context, index))

        val read = HostedMediaStore.readPreloadIndex(context, 7L)!!
        assertEquals(7L, read.memberId)
        assertEquals("req-1", read.requestId)
        assertEquals("ver-1", read.libraryVersion)
        assertEquals(5L, read.updatedAtMs)
        assertEquals(listOf("custom:11:1", "theme:kids.jpg:1"), read.items.map { it.key })
        assertEquals(true, read.items.first().hasAudio)
        assertEquals(PolicyMediaKind.IMAGE, read.items[1].kind)
    }

    @Test
    fun preloadIndexForAnotherMemberIsNotAdopted() {
        assertTrue(
            HostedMediaStore.writePreloadIndex(context, preloadIndex(items = emptyList())),
        )
        val source = File(HostedMediaStore.memberDir(context, 7L), "preload.json")
        val target = File(HostedMediaStore.memberDir(context, 8L), "preload.json")
        target.parentFile?.mkdirs()
        target.writeText(source.readText())

        assertNull(HostedMediaStore.readPreloadIndex(context, 8L))
    }

    @Test
    fun unsupportedPreloadSchemaIsNotRead() {
        val dir = HostedMediaStore.memberDir(context, 7L).apply { mkdirs() }
        File(dir, "preload.json").writeText(
            JSONObject()
                .put("schema", PRELOAD_SCHEMA + 1)
                .put("memberId", 7L)
                .put("requestId", "req-1")
                .put("libraryVersion", "ver-1")
                .put("items", org.json.JSONArray())
                .toString(),
        )

        assertNull(HostedMediaStore.readPreloadIndex(context, 7L))
    }

    @Test
    fun cleanupKeepsFilesReferencedOnlyByThePreloadIndex() {
        // A save publishes from the assets-first cache; cleanup must not treat the rest of that
        // library as garbage, or the next save has to download the same files again.
        val published = writeMedia(7L, "published.mp4")
        val downloaded = writeMedia(7L, "downloaded.mp4")
        val poster = writeMedia(7L, "downloaded.poster.jpg")
        val stale = writeMedia(7L, "stale.mp4")
        HostedMediaStore.writeManifest(
            context,
            manifest(items = listOf(item("custom:11:1", "files/published.mp4"))),
        )
        HostedMediaStore.writePreloadIndex(
            context,
            preloadIndex(
                items = listOf(
                    item("custom:12:1", "files/downloaded.mp4")
                        .copy(poster = "files/downloaded.poster.jpg"),
                ),
            ),
        )

        HostedMediaStore.cleanupUnreferenced(
            context,
            HostedMediaStore.readManifest(context, 7L)!!,
        )

        assertTrue(published.isFile)
        assertTrue(downloaded.isFile)
        assertTrue(poster.isFile)
        assertFalse(stale.exists())
    }

    @Test
    fun installKeepsAnAssetsFirstCacheWithoutManifest() {
        assertTrue(
            HostedMediaStore.writePreloadIndex(
                context,
                preloadIndex(items = listOf(item("custom:11:1", "files/a.mp4"))),
            ),
        )

        HostedMediaStore.install(context)

        assertNotNull(HostedMediaStore.readPreloadIndex(context, 7L))
    }

    @Test
    fun installDeletesStalePreloadTemps() {
        val temp = HostedMediaStore.newPreloadTempFile(context, 7L)
        assertTrue(temp.isFile)

        HostedMediaStore.install(context)

        assertFalse(temp.exists())
    }

    @Test
    fun evictionNeverRemovesThePreloadTarget() {
        writeMedia(1L, "a.mp4", size = 4096)
        writeMedia(2L, "b.mp4", size = 4096)
        File(HostedMediaStore.memberDir(context, 1L), "last_used.json")
            .writeText(JSONObject().put("atMs", 10L).toString())
        File(HostedMediaStore.memberDir(context, 2L), "last_used.json")
            .writeText(JSONObject().put("atMs", 20L).toString())

        HostedMediaStore.enforceTotalLimit(
            context,
            activeMemberId = 2L,
            maxBytes = HostedMediaStore.memberBytes(context, 2L),
            protectedMemberIds = setOf(1L),
        )

        assertTrue(HostedMediaStore.memberDir(context, 1L).isDirectory)
        assertTrue(HostedMediaStore.memberDir(context, 2L).isDirectory)
    }
}