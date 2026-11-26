package app.orchard.tv.policy.media

import android.content.Context
import app.orchard.tv.quiz.QuizIntroVideo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Role slots share one preference store. Clearing one role must never clear the
 * other, and legacy `builtin:*` ids must disappear exactly once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PolicyVideoLibraryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs().edit().clear().commit()
        PolicyVideoLibrary.installMemberScope { null }
        File(context.filesDir, "quiz-intro").deleteRecursively()
        File(context.filesDir, "policy-audio").deleteRecursively()
        File(context.filesDir, "policy-videos").deleteRecursively()
    }

    @Test
    fun migrationDropsBundledIdsAndKeepsOneOtherSlot() {
        prefs().edit()
            .putString(PolicyVideoRole.QUIZ_INTRO.prefKey, "builtin:hero")
            .putString(PolicyVideoRole.LOCK_WALLPAPER.prefKey, "builtin:forward")
            .commit()

        PolicyVideoLibrary.ensureMigrated(context)

        assertNull(prefs().getString(PolicyVideoRole.QUIZ_INTRO.prefKey, null))
        assertNull(prefs().getString(PolicyVideoRole.LOCK_WALLPAPER.prefKey, null))
        assertEquals(1, prefs().getInt("media_schema", 0))
    }

    @Test
    fun clearingQuizKeepsTheLockChoice() {
        prefs().edit()
            .putString(PolicyVideoRole.QUIZ_INTRO.prefKey, "official:hero")
            .putString(PolicyVideoRole.LOCK_WALLPAPER.prefKey, "official:forward")
            .putInt("media_schema", 1)
            .commit()

        val result = PolicyVideoLibrary.select(
            context,
            setOf(PolicyVideoRole.QUIZ_INTRO),
            "",
        )

        assertEquals(PolicyVideoLibrary.EditResult.Ok, result)
        assertNull(prefs().getString(PolicyVideoRole.QUIZ_INTRO.prefKey, null))
        assertEquals(
            "official:forward",
            prefs().getString(PolicyVideoRole.LOCK_WALLPAPER.prefKey, null),
        )
        assertNull(PolicyVideoLibrary.quizSource(context))
        // The file does not exist in this test; the stored reference is what matters here.
        assertEquals(
            "official:forward",
            prefs().getString(PolicyVideoRole.LOCK_WALLPAPER.prefKey, null),
        )
    }

    @Test
    fun clearingBothRolesLeavesNoSelection() {
        prefs().edit()
            .putString(PolicyVideoRole.QUIZ_INTRO.prefKey, "official:hero")
            .putString(PolicyVideoRole.LOCK_WALLPAPER.prefKey, "official:hero")
            .putInt("media_schema", 1)
            .commit()

        val result = PolicyVideoLibrary.select(
            context,
            PolicyVideoRole.entries.toSet(),
            "",
        )

        assertEquals(PolicyVideoLibrary.EditResult.Ok, result)
        assertNull(prefs().getString(PolicyVideoRole.QUIZ_INTRO.prefKey, null))
        assertNull(prefs().getString(PolicyVideoRole.LOCK_WALLPAPER.prefKey, null))
    }

    @Test
    fun emptyRoleSetAndUnknownIdsAreRejected() {
        assertEquals(
            PolicyVideoLibrary.EditResult.RoleInvalid,
            PolicyVideoLibrary.select(context, emptySet(), "official:hero"),
        )
        assertEquals(
            PolicyVideoLibrary.EditResult.NotFound,
            PolicyVideoLibrary.select(context, setOf(PolicyVideoRole.QUIZ_INTRO), "official:hero"),
        )
        assertEquals(
            PolicyVideoLibrary.EditResult.NotFound,
            PolicyVideoLibrary.select(context, setOf(PolicyVideoRole.QUIZ_INTRO), "builtin:hero"),
        )
        assertEquals(
            PolicyVideoLibrary.EditResult.NotFound,
            PolicyVideoLibrary.delete(context, "../hero"),
        )
        assertNull(PolicyVideoLibrary.source(context, PolicyVideoRole.QUIZ_INTRO))
    }

    @Test
    fun lockRoleIdReportsAConfiguredWallpaperWithoutOpeningItsFile() {
        prefs().edit()
            .putString(PolicyVideoRole.LOCK_WALLPAPER.prefKey, "official:forward")
            .putInt("media_schema", 1)
            .commit()

        assertEquals("official:forward", PolicyVideoLibrary.lockRoleId(context))
    }

    @Test
    fun lockRoleIdIgnoresAnUnconfiguredOrMalformedSlot() {
        prefs().edit().putInt("media_schema", 1).commit()
        assertNull(PolicyVideoLibrary.lockRoleId(context))

        prefs().edit()
            .putString(PolicyVideoRole.LOCK_WALLPAPER.prefKey, "../not-a-role")
            .commit()
        assertNull(PolicyVideoLibrary.lockRoleId(context))
    }

    @Test
    fun migrationDoesNotReopenAfterTheFirstRun() {
        prefs().edit()
            .putString(PolicyVideoRole.QUIZ_INTRO.prefKey, "keep-me")
            .putInt("media_schema", 1)
            .commit()

        PolicyVideoLibrary.ensureMigrated(context)

        assertEquals(
            "keep-me",
            prefs().getString(PolicyVideoRole.QUIZ_INTRO.prefKey, null),
        )
    }

    @Test
    fun legacySelectionMigratesToTheUpgradeMemberOnly() {
        val video = localVideo(durationMs = 12_000, hasAudio = true)
        prefs().edit()
            .putString(PolicyVideoRole.QUIZ_INTRO.prefKey, video)
            .putString(PolicyVideoRole.LOCK_WALLPAPER.prefKey, video)
            .putBoolean("lock_sound", false)
            .putInt("media_schema", 1)
            .commit()

        PolicyVideoLibrary.migrateMemberPreferences(context, "usr_a")

        assertEquals(
            video,
            PolicyVideoLibrary.roleId(context, PolicyVideoRole.QUIZ_INTRO, "usr_a"),
        )
        assertEquals(
            video,
            PolicyVideoLibrary.roleId(context, PolicyVideoRole.LOCK_WALLPAPER, "usr_a"),
        )
        assertFalse(PolicyVideoLibrary.lockSoundPreference(context, "usr_a"))
        // A sibling never inherits the device-wide choice made before the split.
        assertNull(PolicyVideoLibrary.roleId(context, PolicyVideoRole.QUIZ_INTRO, "usr_b"))
        assertNull(PolicyVideoLibrary.roleId(context, PolicyVideoRole.LOCK_WALLPAPER, "usr_b"))
        assertTrue(PolicyVideoLibrary.lockSoundPreference(context, "usr_b"))

        // The migration is one-shot: a later member cannot capture the legacy values.
        PolicyVideoLibrary.migrateMemberPreferences(context, "usr_b")
        assertNull(PolicyVideoLibrary.roleId(context, PolicyVideoRole.LOCK_WALLPAPER, "usr_b"))
    }

    @Test
    fun membersKeepIndependentWallpaperAndIntroChoices() {
        val first = localVideo(durationMs = 12_000, hasAudio = true)
        val second = localVideo(durationMs = 12_000, hasAudio = false)
        prefs().edit().putInt("media_schema", 1).commit()

        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.selectFor(
                context,
                PolicyVideoRole.entries.toSet(),
                first,
                "usr_a",
            ),
        )
        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.selectFor(
                context,
                PolicyVideoRole.entries.toSet(),
                second,
                "usr_b",
            ),
        )
        assertEquals(
            first,
            PolicyVideoLibrary.roleId(context, PolicyVideoRole.LOCK_WALLPAPER, "usr_a"),
        )
        assertEquals(
            second,
            PolicyVideoLibrary.roleId(context, PolicyVideoRole.LOCK_WALLPAPER, "usr_b"),
        )
        assertEquals(
            first,
            PolicyVideoLibrary.roleId(context, PolicyVideoRole.QUIZ_INTRO, "usr_a"),
        )
        assertEquals(
            second,
            PolicyVideoLibrary.roleId(context, PolicyVideoRole.QUIZ_INTRO, "usr_b"),
        )

        // Clearing one member's intro must not touch the same member's wallpaper or the sibling.
        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.selectFor(
                context,
                setOf(PolicyVideoRole.QUIZ_INTRO),
                "",
                "usr_a",
            ),
        )
        assertNull(PolicyVideoLibrary.roleId(context, PolicyVideoRole.QUIZ_INTRO, "usr_a"))
        assertEquals(
            first,
            PolicyVideoLibrary.roleId(context, PolicyVideoRole.LOCK_WALLPAPER, "usr_a"),
        )
        assertEquals(
            second,
            PolicyVideoLibrary.roleId(context, PolicyVideoRole.QUIZ_INTRO, "usr_b"),
        )
    }

    @Test
    fun deletingASharedFileClearsEveryMembersSlot() {
        val video = localVideo(durationMs = 12_000, hasAudio = true)
        prefs().edit().putInt("media_schema", 1).commit()
        PolicyVideoLibrary.selectFor(
            context,
            setOf(PolicyVideoRole.LOCK_WALLPAPER),
            video,
            "usr_a",
        )
        PolicyVideoLibrary.selectFor(
            context,
            setOf(PolicyVideoRole.LOCK_WALLPAPER),
            video,
            "usr_b",
        )

        assertEquals(PolicyVideoLibrary.EditResult.Ok, PolicyVideoLibrary.delete(context, video))

        assertNull(PolicyVideoLibrary.roleId(context, PolicyVideoRole.LOCK_WALLPAPER, "usr_a"))
        assertNull(PolicyVideoLibrary.roleId(context, PolicyVideoRole.LOCK_WALLPAPER, "usr_b"))
    }

    @Test
    fun wallpaperSoundIsPerMember() {
        val video = localVideo(durationMs = 600_000, hasAudio = true)
        prefs().edit().putInt("media_schema", 1).commit()
        PolicyVideoLibrary.selectFor(
            context,
            setOf(PolicyVideoRole.LOCK_WALLPAPER),
            video,
            "usr_a",
        )
        PolicyVideoLibrary.selectFor(
            context,
            setOf(PolicyVideoRole.LOCK_WALLPAPER),
            video,
            "usr_b",
        )

        assertTrue(PolicyVideoLibrary.setLockSound(context, false, "usr_a"))
        assertFalse(PolicyVideoLibrary.lockSoundPreference(context, "usr_a"))
        assertTrue(PolicyVideoLibrary.lockSoundPreference(context, "usr_b"))
    }

    @Test
    fun localVideoAndPictureCanFillBothRoles() {
        // Local uploads already passed the TV's own format checks, so a long film and a still
        // picture may each serve as either the wallpaper or the quiz intro.
        val long = localVideo(durationMs = 600_000, hasAudio = true)
        assertEquals(
            PolicyVideoRole.entries.toSet(),
            PolicyVideoLibrary.capabilityRoles(context, long),
        )

        val short = localVideo(durationMs = 12_000, hasAudio = false)
        assertEquals(
            PolicyVideoRole.entries.toSet(),
            PolicyVideoLibrary.capabilityRoles(context, short),
        )

        val picture = localImage()
        assertEquals(
            PolicyVideoRole.entries.toSet(),
            PolicyVideoLibrary.capabilityRoles(context, picture),
        )
    }

    @Test
    fun soundDefaultsOnButOnlyResolvesForAnAudioWallpaper() {
        prefs().edit().putInt("media_schema", 1).commit()
        assertTrue(QuizIntroVideo.lockSound(context))
        assertFalse(PolicyVideoLibrary.lockSound(context))
        assertFalse(PolicyVideoLibrary.setLockSound(context, true))
        assertTrue(QuizIntroVideo.lockSound(context))

        val silent = localVideo(durationMs = 600_000, hasAudio = false)
        setLockRole(silent)
        assertFalse(PolicyVideoLibrary.lockSound(context))
        assertFalse(PolicyVideoLibrary.setLockSound(context, true))

        val audio = localVideo(durationMs = 600_000, hasAudio = true)
        setLockRole(audio)
        assertTrue(PolicyVideoLibrary.lockSound(context))
        assertTrue(PolicyVideoLibrary.setLockSound(context, false))
        assertFalse(PolicyVideoLibrary.lockSound(context))
        assertTrue(PolicyVideoLibrary.setLockSound(context, true))
        assertTrue(PolicyVideoLibrary.lockSound(context))
    }

    @Test
    fun lockPlaylistKeepsTapOrderAndLegacyAlias() {
        val first = localVideo(durationMs = 600_000, hasAudio = true)
        val second = localVideo(durationMs = 600_000, hasAudio = false)
        prefs().edit().putInt("media_schema", 1).commit()

        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.saveLockPlaylistFor(
                context,
                listOf(second, first),
                60_000,
                "usr_a",
            ),
        )

        val selection = PolicyVideoLibrary.lockSelectionFor(context, "usr_a")
        assertEquals(listOf(second, first), selection.items.map { it.id })
        assertEquals(60_000L, selection.intervalMs)
        // Builds from before the rotation only know the first item's legacy slot.
        assertEquals(
            second,
            PolicyVideoLibrary.roleId(context, PolicyVideoRole.LOCK_WALLPAPER, "usr_a"),
        )
        // Another member keeps their own rotation.
        assertEquals(emptyList<String>(), PolicyVideoLibrary.lockSelectionFor(context, "usr_b").items.map { it.id })
    }

    @Test
    fun legacySingleWallpaperIsAOneItemRotation() {
        val video = localVideo(durationMs = 600_000, hasAudio = true)
        prefs().edit()
            .putString(PolicyVideoRole.LOCK_WALLPAPER.prefKey, video)
            .putInt("media_schema", 1)
            .commit()

        val selection = PolicyVideoLibrary.lockSelectionFor(context, null)

        assertEquals(listOf(video), selection.items.map { it.id })
        assertEquals(PolicyVideoLibrary.WALLPAPER_DEFAULT_INTERVAL_MS, selection.intervalMs)
    }

    @Test
    fun purePictureRotationKeepsEveryPictureAndInterval() {
        val first = localImage()
        val second = localImage()
        prefs().edit().putInt("media_schema", 1).commit()

        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.saveLockPlaylistFor(
                context,
                listOf(second, first),
                45_000,
                "usr_a",
            ),
        )

        val selection = PolicyVideoLibrary.lockSelectionFor(context, "usr_a")
        assertEquals(listOf(second, first), selection.items.map { it.id })
        assertTrue(selection.items.all { it.source.kind == PolicyMediaKind.IMAGE })
        assertTrue(selection.items.all { it.source.durationMs == 0L })
        assertEquals(45_000L, selection.intervalMs)
        assertTrue(PolicyVideoLibrary.capabilityRoles(context, first)
            .contains(PolicyVideoRole.QUIZ_INTRO))
    }

    @Test
    fun mixedRotationKeepsPicturesAndVideosInTapOrder() {
        val picture = localImage()
        val silentFilm = localVideo(durationMs = 8_000, hasAudio = false)
        val audioFilm = localVideo(durationMs = 21_000, hasAudio = true)
        prefs().edit().putInt("media_schema", 1).commit()

        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.saveLockPlaylistFor(
                context,
                listOf(picture, audioFilm, silentFilm),
                30_000,
                "usr_a",
            ),
        )

        val selection = PolicyVideoLibrary.lockSelectionFor(context, "usr_a")
        assertEquals(listOf(picture, audioFilm, silentFilm), selection.items.map { it.id })
        assertEquals(
            listOf(PolicyMediaKind.IMAGE, PolicyMediaKind.VIDEO, PolicyMediaKind.VIDEO),
            selection.items.map { it.source.kind },
        )
        // The rotation advances images by the interval but needs each film's own duration.
        assertEquals(listOf(0L, 21_000L, 8_000L), selection.items.map { it.source.durationMs })
        assertEquals(listOf(false, true, false), selection.items.map { it.source.hasAudio })
    }

    @Test
    fun singleLockSelectionReplacesTheRotation() {
        val first = localVideo(durationMs = 600_000, hasAudio = true)
        val second = localVideo(durationMs = 600_000, hasAudio = true)
        prefs().edit().putInt("media_schema", 1).commit()
        PolicyVideoLibrary.saveLockPlaylistFor(context, listOf(first, second), 30_000, "usr_a")

        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.selectFor(
                context,
                setOf(PolicyVideoRole.LOCK_WALLPAPER),
                second,
                "usr_a",
            ),
        )

        val selection = PolicyVideoLibrary.lockSelectionFor(context, "usr_a")
        assertEquals(listOf(second), selection.items.map { it.id })
        assertEquals(
            second,
            PolicyVideoLibrary.roleId(context, PolicyVideoRole.LOCK_WALLPAPER, "usr_a"),
        )
    }

    @Test
    fun deletingAnItemRepairsEveryMembersRotation() {
        val first = localVideo(durationMs = 600_000, hasAudio = true)
        val second = localVideo(durationMs = 600_000, hasAudio = false)
        val third = localVideo(durationMs = 600_000, hasAudio = true)
        prefs().edit().putInt("media_schema", 1).commit()
        PolicyVideoLibrary.saveLockPlaylistFor(
            context,
            listOf(first, second, third),
            45_000,
            "usr_a",
        )
        PolicyVideoLibrary.saveLockPlaylistFor(context, listOf(first, second), 30_000, "usr_b")

        assertEquals(PolicyVideoLibrary.EditResult.Ok, PolicyVideoLibrary.delete(context, first))

        val left = PolicyVideoLibrary.lockSelectionFor(context, "usr_a")
        assertEquals(listOf(second, third), left.items.map { it.id })
        assertEquals(45_000L, left.intervalMs)
        assertEquals(
            second,
            PolicyVideoLibrary.roleId(context, PolicyVideoRole.LOCK_WALLPAPER, "usr_a"),
        )
        assertEquals(
            listOf(second),
            PolicyVideoLibrary.lockSelectionFor(context, "usr_b").items.map { it.id },
        )
    }

    @Test
    fun lockSourceSkipsAnUnreadableItemInTheRotation() {
        val video = localVideo(durationMs = 600_000, hasAudio = true)
        prefs().edit()
            .putString(
                "lock_playlist",
                """{"v":1,"intervalMs":30000,"ids":["official:missing","$video"]}""",
            )
            .putString(PolicyVideoRole.LOCK_WALLPAPER.prefKey, "official:missing")
            .putInt("media_schema", 1)
            .commit()

        // The first id is stored but its file is gone; the rotation still has a picture to show.
        assertEquals(listOf(video), PolicyVideoLibrary.lockSelection(context).items.map { it.id })
        val source = PolicyVideoLibrary.lockSource(context)
        assertTrue(source?.uri?.path?.contains(video) == true)
    }

    @Test
    fun playlistRejectsDuplicatesLimitsAndOutOfRangeIntervals() {
        val video = localVideo(durationMs = 600_000, hasAudio = true)
        prefs().edit().putInt("media_schema", 1).commit()

        assertEquals(
            PolicyVideoLibrary.EditResult.PlaylistInvalid,
            PolicyVideoLibrary.saveLockPlaylistFor(context, listOf(video, video), 30_000, null),
        )
        assertEquals(
            PolicyVideoLibrary.EditResult.PlaylistTooLong,
            PolicyVideoLibrary.saveLockPlaylistFor(
                context,
                List(PolicyVideoLibrary.WALLPAPER_MAX_ITEMS + 1) { "official:item$it" },
                30_000,
                null,
            ),
        )
        assertEquals(
            PolicyVideoLibrary.EditResult.PlaylistInvalid,
            PolicyVideoLibrary.saveLockPlaylistFor(
                context,
                listOf(video),
                PolicyVideoLibrary.WALLPAPER_MIN_INTERVAL_MS - 1,
                null,
            ),
        )
        assertEquals(
            PolicyVideoLibrary.EditResult.NotFound,
            PolicyVideoLibrary.saveLockPlaylistFor(
                context,
                listOf("0123456789abcdef0123456789abcdef"),
                30_000,
                null,
            ),
        )
    }

    @Test
    fun playlistSoundTurnsOnForAnyAudioItem() {
        val silent = localVideo(durationMs = 600_000, hasAudio = false)
        val audio = localVideo(durationMs = 600_000, hasAudio = true)
        prefs().edit().putInt("media_schema", 1).commit()
        PolicyVideoLibrary.saveLockPlaylistFor(
            context,
            listOf(silent, audio),
            30_000,
            "usr_a",
        )

        PolicyVideoLibrary.setLockSound(context, false, "usr_a")
        assertFalse(PolicyVideoLibrary.lockSoundPreference(context, "usr_a"))
        assertTrue(PolicyVideoLibrary.setLockSound(context, true, "usr_a"))
        assertTrue(PolicyVideoLibrary.lockSoundPreference(context, "usr_a"))

        PolicyVideoLibrary.saveLockPlaylistFor(context, listOf(silent), 30_000, "usr_a")
        PolicyVideoLibrary.setLockSound(context, false, "usr_a")
        assertFalse(PolicyVideoLibrary.setLockSound(context, true, "usr_a"))
    }

    @Test
    fun backgroundMusicCanEnableTheMasterSoundSwitch() {
        val music = localAudio("music.mp3")
        prefs().edit().putInt("media_schema", 1).commit()
        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.selectLockAudioFor(context, music, "usr_a"),
        )

        PolicyVideoLibrary.setLockSound(context, false, "usr_a")
        assertTrue(PolicyVideoLibrary.setLockSound(context, true, "usr_a"))
        assertTrue(PolicyVideoLibrary.lockSoundPreference(context, "usr_a"))

        // The music was the only sound source; after clearing it the switch needs video audio.
        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.selectLockAudioFor(context, "", "usr_a"),
        )
        PolicyVideoLibrary.setLockSound(context, false, "usr_a")
        assertFalse(PolicyVideoLibrary.setLockSound(context, true, "usr_a"))
    }

    @Test
fun backgroundMusicIsChosenPerMemberAndDeletedForEveryone() {
        val first = localAudio("first.mp3")
        val second = localAudio("second.mp3")
        prefs().edit().putInt("media_schema", 1).commit()

        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.selectLockAudioFor(context, first, "usr_a"),
        )
        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.selectLockAudioFor(context, second, "usr_b"),
        )
        assertEquals(first, PolicyVideoLibrary.lockAudioFor(context, "usr_a")?.id)
        assertEquals(second, PolicyVideoLibrary.lockAudioFor(context, "usr_b")?.id)

        // The track is one shared file; deleting it clears every member that selected it.
        assertEquals(PolicyVideoLibrary.EditResult.Ok, PolicyVideoLibrary.deleteLockAudio(context, first))
        assertNull(PolicyVideoLibrary.lockAudioFor(context, "usr_a"))
        assertEquals(second, PolicyVideoLibrary.lockAudioFor(context, "usr_b")?.id)
        assertFalse(File(File(context.filesDir, "policy-audio"), first).exists())

        // The empty id removes the music; malformed ids are never accepted.
        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.selectLockAudioFor(context, "", "usr_b"),
        )
        assertNull(PolicyVideoLibrary.lockAudioFor(context, "usr_b"))
        assertEquals(
            PolicyVideoLibrary.EditResult.NotFound,
            PolicyVideoLibrary.selectLockAudioFor(
                context,
                "0123456789abcdef0123456789abcdef",
                "usr_a",
            ),
        )
        assertEquals(
            PolicyVideoLibrary.EditResult.NotFound,
            PolicyVideoLibrary.deleteLockAudio(context, "../audio"),
        )
    }

    @Test
    fun officialMusicResolvesOnlyAfterADownloadedCopyExists() {
        val id = "official:morning"
        // Snapshot reads the active member scope; this test selects for usr_a explicitly.
        PolicyVideoLibrary.installMemberScope { "usr_a" }
        prefs().edit().putInt("media_schema", 1).commit()
        assertTrue(PolicyAudioLibrary.isAudioId(id))
        assertTrue(PolicyAudioLibrary.isOfficialId(id))
        assertEquals(
            PolicyVideoLibrary.EditResult.NotFound,
            PolicyVideoLibrary.selectLockAudioFor(context, id, "usr_a"),
        )

        val file = officialAudio(id)
        assertEquals(1, OfficialAudioStore.list(context).size)
        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.selectLockAudioFor(context, id, "usr_a"),
        )
        val selected = PolicyVideoLibrary.lockAudioFor(context, "usr_a")
        assertEquals(id, selected?.id)
        assertEquals(PolicyAudioEntry.SOURCE_OFFICIAL, selected?.source)
        assertEquals("Morning", selected?.nameEn)
        assertEquals("Ahjay Stelino", selected?.artist)
        assertEquals(file, selected?.file)

        PolicyVideoLibrary.setLockSound(context, false, "usr_a")
        assertTrue(PolicyVideoLibrary.setLockSound(context, true, "usr_a"))
        val snapshot = PolicyVideoLibrary.snapshot(context, autoRefreshCatalog = false)
            .getJSONObject("audio")
        assertEquals(id, snapshot.getString("selectedId"))
        val cloud = snapshot.getJSONArray("cloudItems")
        assertEquals(1, cloud.length())
        assertEquals("READY", cloud.getJSONObject(0).getString("state"))
        assertTrue(cloud.getJSONObject(0).getBoolean("selected"))

        // Deleting a download removes only the device copy and clears the member selection.
        assertEquals(PolicyVideoLibrary.EditResult.Ok, PolicyVideoLibrary.deleteLockAudio(context, id))
        assertNull(PolicyVideoLibrary.lockAudioFor(context, "usr_a"))
        assertFalse(file.exists())
        assertEquals(
            PolicyVideoLibrary.EditResult.NotFound,
            PolicyVideoLibrary.deleteLockAudio(context, id),
        )
    }

    @Test
    fun wallpaperConfigCommitsRotationSoundAndMusicTogether() {
        val film = localVideo(durationMs = 600_000, hasAudio = true)
        val picture = localImage()
        val music = localAudio("theme.mp3")
        prefs().edit().putInt("media_schema", 1).commit()

        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.saveLockConfigFor(
                context,
                listOf(film, picture),
                45_000,
                sound = true,
                audioId = music,
                memberId = "usr_a",
            ),
        )

        val selection = PolicyVideoLibrary.lockSelectionFor(context, "usr_a")
        assertEquals(listOf(film, picture), selection.items.map { it.id })
        assertEquals(45_000L, selection.intervalMs)
        assertTrue(PolicyVideoLibrary.lockSoundPreference(context, "usr_a"))
        assertEquals(music, PolicyVideoLibrary.lockAudioFor(context, "usr_a")?.id)
        assertEquals(
            film,
            PolicyVideoLibrary.roleId(context, PolicyVideoRole.LOCK_WALLPAPER, "usr_a"),
        )

        // A rejected one-save leaves every part of the previous configuration intact.
        assertEquals(
            PolicyVideoLibrary.EditResult.RoleInvalid,
            PolicyVideoLibrary.saveLockConfigFor(
                context,
                listOf(picture),
                30_000,
                sound = true,
                audioId = "",
                memberId = "usr_a",
            ),
        )
        assertEquals(
            listOf(film, picture),
            PolicyVideoLibrary.lockSelectionFor(context, "usr_a").items.map { it.id },
        )
        assertEquals(45_000L, PolicyVideoLibrary.lockSelectionFor(context, "usr_a").intervalMs)
        assertTrue(PolicyVideoLibrary.lockSoundPreference(context, "usr_a"))
        assertEquals(music, PolicyVideoLibrary.lockAudioFor(context, "usr_a")?.id)

        // Choosing the default wallpaper still clears the rotation, sound and track in one step.
        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.saveLockConfigFor(
                context,
                emptyList(),
                45_000,
                sound = false,
                audioId = "",
                memberId = "usr_a",
            ),
        )
        assertEquals(emptyList<String>(), PolicyVideoLibrary.lockSelectionFor(context, "usr_a").items.map { it.id })
        assertFalse(PolicyVideoLibrary.lockSoundPreference(context, "usr_a"))
        assertNull(PolicyVideoLibrary.lockAudioFor(context, "usr_a"))
    }

    @Test
    fun quizIntroAcceptsAPictureAndCustomAudioInOneSave() {
        val picture = localImage()
        val music = localAudio("intro.mp3")
        PolicyVideoLibrary.installMemberScope { "usr_a" }
        prefs().edit().putInt("media_schema", 1).commit()

        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.saveQuizIntroFor(
                context,
                picture,
                audioEnabled = true,
                audioId = music,
                memberId = "usr_a",
            ),
        )

        val intro = PolicyVideoLibrary.quizIntroFor(context, "usr_a")
        assertTrue(intro.visual?.uri?.path?.contains(picture) == true)
        assertEquals(PolicyMediaKind.IMAGE, intro.visual?.kind)
        assertEquals(music, intro.audio?.id)
        assertTrue(intro.audioEnabled)
        assertTrue(intro.customAudio)

        val snapshot = PolicyVideoLibrary.snapshot(context, autoRefreshCatalog = false)
        assertEquals(picture, snapshot.getJSONObject("roles").getString("quiz"))
        val quizAudio = snapshot.getJSONObject("quizAudio")
        assertTrue(quizAudio.getBoolean("enabled"))
        assertEquals(music, quizAudio.getString("selectedId"))
        val audioItems = snapshot.getJSONObject("audio").getJSONArray("items")
        assertEquals(1, audioItems.length())
        assertTrue(audioItems.getJSONObject(0).getBoolean("quizSelected"))
        val roles = audioItems.getJSONObject(0).getJSONArray("roles")
        assertTrue((0 until roles.length()).any { roles.getString(it) == "quiz" })
    }

    @Test
    fun downloadedCloudPictureCanOpenTheLocalQuizIntro() {
        // The manifest offers cloud pictures as wallpapers only. Once the bytes are on this
        // TV, the same downloaded file is a local visual and the quiz intro can show it.
        val picture = officialImage("official:cloud-picture")
        PolicyVideoLibrary.installMemberScope { "usr_a" }
        prefs().edit().putInt("media_schema", 1).commit()

        assertTrue(OfficialMediaStore.read(context, picture) != null)
        assertTrue(OfficialMediaStore.list(context).any { it.id == picture })
        assertTrue(
            PolicyVideoLibrary.capabilityRoles(context, picture)
                .containsAll(PolicyVideoRole.entries),
        )
        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.saveQuizIntroFor(context, picture, false, "", "usr_a"),
        )
        val intro = PolicyVideoLibrary.quizIntroFor(context, "usr_a")
        assertEquals(PolicyMediaKind.IMAGE, intro.visual?.kind)
        assertTrue(intro.visual?.uri?.path?.contains("cloud-picture") == true)

        val snapshot = PolicyVideoLibrary.snapshot(context, autoRefreshCatalog = false)
        val items = snapshot.getJSONArray("items")
        val serialized = (0 until items.length()).map { items.getJSONObject(it) }
        val ids = serialized.map { it.getString("id") }
        assertTrue("downloaded picture missing from snapshot: $ids", ids.contains(picture))
        val item = serialized.first { it.getString("id") == picture }
        val capabilities = item.getJSONArray("capabilityRoles")
        assertTrue((0 until capabilities.length()).any { capabilities.getString(it) == "quiz" })
    }

    @Test
    fun quizIntroRejectsAnEnabledSwitchWithoutItsAudio() {
        val picture = localImage()
        val music = localAudio("intro.mp3")
        prefs().edit().putInt("media_schema", 1).commit()

        assertEquals(
            PolicyVideoLibrary.EditResult.RoleInvalid,
            PolicyVideoLibrary.saveQuizIntroFor(context, picture, true, "", "usr_a"),
        )
        assertEquals(
            PolicyVideoLibrary.EditResult.RoleInvalid,
            PolicyVideoLibrary.saveQuizIntroFor(context, "", true, music, "usr_a"),
        )
        assertEquals(
            PolicyVideoLibrary.EditResult.NotFound,
            PolicyVideoLibrary.saveQuizIntroFor(
                context,
                picture,
                false,
                "0123456789abcdef0123456789abcdef",
                "usr_a",
            ),
        )
        assertNull(PolicyVideoLibrary.roleId(context, PolicyVideoRole.QUIZ_INTRO, "usr_a"))

        // A silent picture with a stored-but-disabled track is valid and stays silent.
        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.saveQuizIntroFor(context, picture, false, music, "usr_a"),
        )
        val intro = PolicyVideoLibrary.quizIntroFor(context, "usr_a")
        assertTrue(intro.visual?.uri?.path?.contains(picture) == true)
        assertEquals(music, intro.audio?.id)
        assertFalse(intro.audioEnabled)
        assertFalse(intro.customAudio)
    }

    @Test
    fun deletingQuizAudioClearsItsSwitchAndKeepsTheVisual() {
        val picture = localImage()
        val music = localAudio("intro.mp3")
        prefs().edit().putInt("media_schema", 1).commit()
        assertEquals(
            PolicyVideoLibrary.EditResult.Ok,
            PolicyVideoLibrary.saveQuizIntroFor(context, picture, true, music, "usr_a"),
        )

        assertEquals(PolicyVideoLibrary.EditResult.Ok, PolicyVideoLibrary.deleteLockAudio(context, music))

        val intro = PolicyVideoLibrary.quizIntroFor(context, "usr_a")
        assertTrue(intro.visual?.uri?.path?.contains(picture) == true)
        assertNull(intro.audio)
        assertFalse(intro.audioEnabled)
        assertFalse(intro.customAudio)
        assertNull(prefs().getString("quiz_audio:usr_a", null))
        // The switch is removed with the track; a missing switch reads as off.
        assertFalse(prefs().getBoolean("quiz_audio_enabled:usr_a", false))
    }

    @Test
    fun musicSelectionIsNotAWallpaperEdit() {
        // A music change refreshes the music player, but reloading the rotation would restart
        // the picture or film the child is watching.
        assertTrue(PolicyVideoLibrary.isMediaPreferenceKey(PolicyVideoRole.LOCK_WALLPAPER.prefKey))
        assertTrue(PolicyVideoLibrary.isMediaPreferenceKey("lock_playlist:usr_a"))
        assertFalse(PolicyVideoLibrary.isMediaPreferenceKey("lock_audio"))
        assertFalse(PolicyVideoLibrary.isMediaPreferenceKey("lock_audio:usr_a"))
        assertTrue(PolicyVideoLibrary.isAudioPreferenceKey("lock_audio"))
        assertTrue(PolicyVideoLibrary.isAudioPreferenceKey("lock_audio:usr_a"))
        assertFalse(PolicyVideoLibrary.isAudioPreferenceKey("lock_playlist:usr_a"))
    }

    @Test
    fun deletingAnAudioFileSkipsASelectionWhoseFileAlreadyDisappeared() {
        val id = localAudio("gone.mp3")
        prefs().edit()
            .putInt("media_schema", 1)
            .putString("lock_audio:usr_a", id)
            .commit()
        File(File(context.filesDir, "policy-audio"), id).deleteRecursively()

        assertEquals(PolicyVideoLibrary.EditResult.Ok, PolicyVideoLibrary.deleteLockAudio(context, id))
        assertNull(PolicyVideoLibrary.lockAudioFor(context, "usr_a"))
    }

    /** A tiny complete local entry; the library only reads metadata here, never decodes it. */
    private fun localVideo(durationMs: Long, hasAudio: Boolean): String {
        val id = UUID.randomUUID().toString().replace("-", "")
        val dir = File(File(context.filesDir, "quiz-intro"), id)
        check(dir.mkdirs())
        File(dir, "video.mp4").writeBytes(byteArrayOf(0, 0, 0, 1))
        File(dir, "info.json").writeText(
            """
            {"kind":"video","name":"$id.mp4","durationMs":$durationMs,
             "width":1920,"height":1080,"hasAudio":$hasAudio,"createdAt":1}
            """.trimIndent(),
        )
        return id
    }

    /** A tiny complete local picture; the rotation only reads its metadata here. */
    private fun localImage(): String {
        val id = UUID.randomUUID().toString().replace("-", "")
        val dir = File(File(context.filesDir, "quiz-intro"), id)
        check(dir.mkdirs())
        File(dir, "image.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        File(dir, "info.json").writeText(
            """
            {"kind":"image","name":"$id.jpg","durationMs":0,
             "width":1920,"height":1080,"hasAudio":false,"createdAt":1}
            """.trimIndent(),
        )
        return id
    }

    /** A complete downloaded official picture; the manifest still lists it as lock-only. */
    private fun officialImage(id: String): String {
        val dir = File(File(context.filesDir, "policy-videos/official"), id.removePrefix("official:"))
        check(dir.mkdirs())
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        File(dir, "image.jpg").writeBytes(bytes)
        File(dir, "info.json").writeText(
            """
            {"id":"$id","kind":"image","name":"Cloud picture","nameEn":"Cloud picture",
             "roles":["lock"],"width":1920,"height":1080,"bytes":${bytes.size},
             "sha256":"${"b".repeat(64)}","downloadedAt":1}
            """.trimIndent(),
        )
        return id
    }

    /** A complete verified official download, without touching the network. */
    private fun officialAudio(id: String): File {
        val slug = id.removePrefix("official:")
        val dir = File(File(File(context.filesDir, "policy-audio"), "official"), slug)
        check(dir.mkdirs())
        val file = File(dir, "audio.bin")
        file.writeBytes(byteArrayOf(0x49, 0x44, 0x33))
        File(dir, "info.json").writeText(
            """
            {"id":"$id","name":"清晨","nameEn":"Morning","artist":"Ahjay Stelino",
             "license":"CC-BY-4.0","sourceUrl":"https://example.com/music/morning",
             "sha256":"${"a".repeat(64)}","bytes":3,"durationMs":60000,"downloadedAt":1}
            """.trimIndent(),
        )
        return file
    }

    private fun localAudio(name: String): String {
        val id = UUID.randomUUID().toString().replace("-", "")
        val dir = File(File(context.filesDir, "policy-audio"), id)
        check(dir.mkdirs())
        File(dir, "audio.bin").writeBytes(byteArrayOf(0x49, 0x44, 0x33))
        File(dir, "info.json").writeText(
            """
            {"name":"$name","sizeBytes":3,"durationMs":60000,"createdAt":1}
            """.trimIndent(),
        )
        return id
    }

    private fun setLockRole(id: String) {
        prefs().edit()
            .putString(PolicyVideoRole.LOCK_WALLPAPER.prefKey, id)
            .putInt("media_schema", 1)
            .commit()
    }

    private fun prefs() = context.getSharedPreferences("quiz-intro", Context.MODE_PRIVATE)
}
