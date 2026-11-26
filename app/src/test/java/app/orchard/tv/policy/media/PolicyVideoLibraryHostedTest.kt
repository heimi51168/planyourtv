package app.orchard.tv.policy.media

import android.content.Context
import android.net.Uri
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

/**
 * V50: the route is the only switch between the local library and the hosted cache. In CLOUD the
 * read entries must answer from the snapshot alone — no local upload, no local BGM, no local
 * wallpaper — and switching back must restore the untouched local selection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PolicyVideoLibraryHostedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs().edit().clear().commit()
        HostedMediaRoute.setCloud(false)
        HostedMediaRoute.setSnapshot(null)
        PolicyVideoLibrary.installMemberScope { null }
    }

    @After
    fun tearDown() {
        HostedMediaRoute.setCloud(false)
        HostedMediaRoute.setSnapshot(null)
        PolicyVideoLibrary.installMemberScope { null }
        prefs().edit().clear().commit()
    }

    private fun prefs() = context.getSharedPreferences("quiz-intro", Context.MODE_PRIVATE)

    private fun hostedItem(id: String, hasAudio: Boolean = false): PolicyWallpaperItem =
        PolicyWallpaperItem(
            id = id,
            source = PolicyVideoSource(
                uri = Uri.fromFile(File(context.filesDir, "hosted-$id.mp4")),
                official = false,
                poster = null,
                kind = PolicyMediaKind.VIDEO,
                hasAudio = hasAudio,
                durationMs = 5_000L,
            ),
        )

    private fun snapshot(
        sound: Boolean = true,
        items: List<PolicyWallpaperItem> = listOf(hostedItem("hosted:7:a")),
        quizSource: PolicyVideoSource? = null,
    ): HostedMediaSnapshot = HostedMediaSnapshot(
        memberId = 7L,
        sound = sound,
        selection = PolicyWallpaperSelection(items, HOSTED_WALLPAPER_INTERVAL_MS),
        lockRoleId = items.firstOrNull()?.id ?: HostedMediaRoute.NONE_ID,
        quizSource = quizSource,
    )

    @Test
    fun cloudWithoutSnapshotUsesEmptyDefaultsNotLocalSelection() {
        prefs().edit()
            .putString(PolicyVideoRole.LOCK_WALLPAPER.prefKey, "official:forward")
            .putString(PolicyVideoRole.QUIZ_INTRO.prefKey, "official:hero")
            .putString("lock_sound", "true")
            .putInt("media_schema", 1)
            .commit()
        HostedMediaRoute.setCloud(true)

        assertNull(PolicyVideoLibrary.quizSource(context))
        assertNull(PolicyVideoLibrary.lockSource(context))
        assertTrue(PolicyVideoLibrary.lockSelection(context).items.isEmpty())
        assertEquals(
            HOSTED_WALLPAPER_INTERVAL_MS,
            PolicyVideoLibrary.lockSelection(context).intervalMs,
        )
        assertEquals(HostedMediaRoute.NONE_ID, PolicyVideoLibrary.lockRoleId(context))
        assertFalse(PolicyVideoLibrary.lockSound(context))
        assertFalse(PolicyVideoLibrary.lockSoundPreference(context))
        assertNull(PolicyVideoLibrary.lockAudio(context))
        assertNull(PolicyVideoLibrary.source(context, PolicyVideoRole.LOCK_WALLPAPER))
        assertNull(PolicyVideoLibrary.source(context, PolicyVideoRole.QUIZ_INTRO))
    }

    @Test
    fun cloudReadsTheSnapshotThroughEveryReadEntry() {
        val intro = PolicyVideoSource(
            uri = Uri.fromFile(File(context.filesDir, "intro.mp4")),
            official = false,
            poster = null,
        )
        HostedMediaRoute.setSnapshot(snapshot(items = listOf(hostedItem("hosted:7:a")), quizSource = intro))
        HostedMediaRoute.setCloud(true)

        val selection = PolicyVideoLibrary.lockSelection(context)
        assertEquals(listOf("hosted:7:a"), selection.items.map { it.id })
        assertEquals(selection.items.first().source.uri, PolicyVideoLibrary.lockSource(context)?.uri)
        assertEquals("hosted:7:a", PolicyVideoLibrary.lockRoleId(context))
        assertEquals(intro.uri, PolicyVideoLibrary.quizSource(context)?.uri)
        assertEquals(intro.uri, PolicyVideoLibrary.source(context, PolicyVideoRole.QUIZ_INTRO)?.uri)
        assertEquals(
            selection.items.first().source.uri,
            PolicyVideoLibrary.source(context, PolicyVideoRole.LOCK_WALLPAPER)?.uri,
        )
    }

    @Test
    fun cloudSoundNeedsANonMutedTrackIndependentOfThePreference() {
        HostedMediaRoute.setSnapshot(snapshot(sound = true, items = listOf(hostedItem("hosted:7:a"))))
        HostedMediaRoute.setCloud(true)
        assertTrue(PolicyVideoLibrary.lockSoundPreference(context))
        assertFalse(PolicyVideoLibrary.lockSound(context))

        HostedMediaRoute.setSnapshot(
            snapshot(sound = true, items = listOf(hostedItem("hosted:7:a", hasAudio = true))),
        )
        assertTrue(PolicyVideoLibrary.lockSound(context))

        HostedMediaRoute.setSnapshot(
            snapshot(sound = false, items = listOf(hostedItem("hosted:7:a", hasAudio = true))),
        )
        assertFalse(PolicyVideoLibrary.lockSoundPreference(context))
        assertFalse(PolicyVideoLibrary.lockSound(context))
    }

    @Test
    fun localWritesInCloudSucceedButExecutionKeepsTheSnapshot() {
        HostedMediaRoute.setSnapshot(snapshot(sound = true, items = listOf(hostedItem("hosted:7:a"))))
        HostedMediaRoute.setCloud(true)

        assertTrue(PolicyVideoLibrary.setLockSound(context, false))
        assertEquals(PolicyVideoLibrary.EditResult.Ok, PolicyVideoLibrary.selectLockAudio(context, null))
        assertTrue(PolicyVideoLibrary.lockSoundPreference(context))
    }

    @Test
    fun leavingCloudRestoresTheLocalSelection() {
        HostedMediaRoute.setSnapshot(snapshot(items = listOf(hostedItem("hosted:7:a"))))
        HostedMediaRoute.setCloud(true)
        assertFalse(PolicyVideoLibrary.lockSelection(context).items.isEmpty())

        HostedMediaRoute.setCloud(false)

        assertTrue(PolicyVideoLibrary.lockSelection(context).items.isEmpty())
        assertNull(PolicyVideoLibrary.lockRoleId(context))
        assertNull(PolicyVideoLibrary.quizSource(context))
        assertNull(PolicyVideoLibrary.lockAudio(context))
        assertFalse(PolicyVideoLibrary.lockSound(context))
    }
}