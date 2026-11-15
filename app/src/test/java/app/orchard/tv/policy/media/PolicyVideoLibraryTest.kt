package app.orchard.tv.policy.media

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

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

    private fun prefs() = context.getSharedPreferences("quiz-intro", Context.MODE_PRIVATE)
}
