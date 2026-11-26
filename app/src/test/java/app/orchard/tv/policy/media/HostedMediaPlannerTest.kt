package app.orchard.tv.policy.media

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V50 acceptance matrix of the payload -> plan step. The planner is the one place that decides
 * which backend fields the TV is willing to cache, so every rejection below is a contract test,
 * not an implementation detail: an old or malformed backend must be visible as a failure instead
 * of looking like "the parent chose the default wallpaper".
 *
 * Robolectric provides a real `org.json`; the framework stub would make every payload fail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HostedMediaPlannerTest {
    private fun video(
        id: Long,
        version: Long = 1,
        kind: String = "VIDEO",
        size: Long = 1024L,
        duration: Long = 5_000L,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("version", version)
        .put("kind", kind)
        .put("url", "https://example.test/media/$id.mp4")
        .put("ext", "mp4")
        .put("sizeBytes", size)
        .put("durationMs", duration)

    private fun customWallpaper(
        mode: String = "FIXED",
        items: List<JSONObject> = listOf(video(11), video(12)),
        activeId: Long = 11L,
        quizIntro: Any? = JSONObject.NULL,
        includeQuizIntro: Boolean = true,
    ): JSONObject = JSONObject()
        .put("landscapeMode", mode)
        .put("landscapeActiveId", activeId)
        .put("landscapeItems", JSONArray(items))
        .also { if (includeQuizIntro) it.put("quizIntro", quizIntro ?: JSONObject.NULL) }

    private fun media(
        file: String,
        kind: String = "VIDEO",
        version: Long = 1,
        orientation: String = "landscape",
        id: String = "media:$file",
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("kind", kind)
        .put("file", file)
        .put("url", "https://example.test/wallpapers/$file")
        .put("ext", file.substringAfterLast('.'))
        .put("version", version)
        .put("orientation", orientation)
        .put("sizeBytes", 2048L)
        .put("durationMs", if (kind == "VIDEO") 10_000L else 0L)

    private fun audio(
        key: String = "official:playful",
        source: String = "official",
        cacheKey: String = "audio:${key}:1",
        name: String = "俏皮时光",
        url: String = "https://media.test/policy-audio/playful.mp3",
        sha256: String = "a".repeat(64),
        sizeBytes: Long = 4_818_350L,
        durationMs: Long = 150_544L,
        ext: String = "mp3",
        version: Long = 1L,
    ): JSONObject = JSONObject()
        .put("key", cacheKey)
        .put("id", key)
        .put("source", source)
        .put("name", name)
        .put("nameEn", "Playful")
        .put("artist", "Ahjay Stelino")
        .put("license", "Authorized for PlanYourTV")
        .put("url", url)
        .put("sha256", sha256)
        .put("sizeBytes", sizeBytes)
        .put("durationMs", durationMs)
        .put("ext", ext)
        .put("version", version)

    private fun payload(
        learnerId: Long = 7L,
        wallpaper: JSONObject? = customWallpaper(),
        sound: Any? = true,
        theme: String = "NONE",
        rotateMode: String = "FIXED",
        rotateDays: Int = 1,
        // The real configured-apps payload always carries wallpaperMedia since V51; null here
        // deliberately omits it to model an older backend.
        media: List<JSONObject>? = emptyList(),
        item: String = "ALL",
        playlist: List<String>? = null,
        playlistSet: Boolean? = null,
        intervalMs: Any? = 30_000L,
        quizIntro: Any? = null,
        includeQuizIntro: Boolean = false,
        backgroundMusic: String? = null,
        backgroundMusicItem: JSONObject? = null,
        includeBackgroundMusic: Boolean = false,
        mediaSchema: Int? = null,
        quizAudio: String? = null,
        quizAudioEnabled: Boolean? = null,
        quizAudioItem: JSONObject? = null,
        includeQuizAudio: Boolean = false,
    ): JSONObject = JSONObject()
        .put("learnerId", learnerId)
        .also { if (mediaSchema != null) it.put("wallpaperMediaSchema", mediaSchema) }
        .also {
            if (includeQuizAudio) {
                it.put("wallpaperQuizAudio", quizAudio ?: JSONObject.NULL)
                it.put("wallpaperQuizAudioEnabled", quizAudioEnabled ?: false)
                it.put("wallpaperQuizAudioItem", quizAudioItem ?: JSONObject.NULL)
            }
        }
        .also { if (wallpaper != null) it.put("customWallpaper", wallpaper) }
        .also { if (sound != null) it.put("wallpaperSound", sound) }
        .put("wallpaperTheme", theme)
        .put("wallpaperRotateMode", rotateMode)
        .put("wallpaperRotateDays", rotateDays)
        .put("wallpaperItem", item)
        .also { if (media != null) it.put("wallpaperMedia", JSONArray(media)) }
        .also { if (playlistSet != null) it.put("wallpaperPlaylistSet", playlistSet) }
        .also { if (playlist != null) it.put("wallpaperPlaylist", JSONArray(playlist)) }
        // V53 configured-apps always emits the key (possibly JSON null); tests that model an
        // explicit choice pass includeQuizIntro = true.
        .also { if (playlist != null) it.put("wallpaperIntervalMs", intervalMs ?: JSONObject.NULL) }
        .also {
            if (includeQuizIntro) {
                it.put("wallpaperQuizIntro", quizIntro ?: JSONObject.NULL)
            }
        }
        // V54: both keys are always present on a new backend; omit them to model an older one.
        .also {
            if (includeBackgroundMusic) {
                it.put("wallpaperBackgroundMusic", backgroundMusic ?: JSONObject.NULL)
                it.put("wallpaperBackgroundMusicItem", backgroundMusicItem ?: JSONObject.NULL)
            }
        }

    private fun success(result: HostedMediaPlanResult): HostedMediaPlan =
        (result as HostedMediaPlanResult.Success).plan

    private fun rejection(result: HostedMediaPlanResult): HostedMediaError =
        (result as HostedMediaPlanResult.Rejected).error

    @Test
    fun rejectsPayloadWithoutLearner() {
        assertEquals(
            HostedMediaError.NO_MEMBER,
            rejection(HostedMediaPlanner.plan(payload(learnerId = 0L))),
        )
    }

    @Test
    fun rejectsPayloadWithoutCustomWallpaper() {
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(HostedMediaPlanner.plan(payload(wallpaper = null))),
        )
    }

    @Test
    fun rejectsPayloadWithoutQuizIntroField() {
        // The key is always present (possibly JSON null) since V50; its absence is an old backend.
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(
                HostedMediaPlanner.plan(
                    payload(wallpaper = customWallpaper(includeQuizIntro = false)),
                ),
            ),
        )
    }

    @Test
    fun rejectsNonBooleanSound() {
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(HostedMediaPlanner.plan(payload(sound = "true"))),
        )
    }

    @Test
    fun rejectsUnsupportedMode() {
        assertEquals(
            HostedMediaError.BAD_MODE,
            rejection(HostedMediaPlanner.plan(payload(wallpaper = customWallpaper(mode = "LOOP")))),
        )
    }

    @Test
    fun keepsOnlyVideosAndSortsById() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(
                        mode = "CAROUSEL",
                        items = listOf(video(12), video(13, kind = "IMAGE"), video(11)),
                    ),
                ),
            ),
        )

        assertEquals(listOf("custom:11:1", "custom:12:1"), plan.wallpaperItems.map { it.key })
        assertEquals(2, plan.sourceItemCount)
        assertTrue(plan.quizIntro == null)
    }

    @Test
    fun fixedKeepsTheActiveVideoOnly() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(wallpaper = customWallpaper(mode = "FIXED", activeId = 12L)),
            ),
        )

        assertEquals(listOf("custom:12:1"), plan.wallpaperItems.map { it.key })
        assertEquals("custom:12:1", plan.activeKey)
        assertEquals(2, plan.sourceItemCount)
    }

    @Test
    fun fixedFallsBackToFirstVideoWhenActiveIsMissing() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(wallpaper = customWallpaper(mode = "FIXED", activeId = 99L)),
            ),
        )

        assertEquals(listOf("custom:11:1"), plan.wallpaperItems.map { it.key })
        assertEquals("custom:11:1", plan.activeKey)
    }

    @Test
    fun noVideosProducesAnEmptyButValidPlan() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(wallpaper = customWallpaper(items = listOf(video(13, kind = "IMAGE")))),
            ),
        )

        assertTrue(plan.wallpaperItems.isEmpty())
        assertNull(plan.activeKey)
        assertEquals(0, plan.sourceItemCount)
    }

    @Test
    fun carouselHasNoActiveKey() {
        val plan = success(
            HostedMediaPlanner.plan(payload(wallpaper = customWallpaper(mode = "CAROUSEL"))),
        )

        assertNull(plan.activeKey)
        assertEquals(2, plan.wallpaperItems.size)
    }

    @Test
    fun rejectsDuplicateKeys() {
        // Two versions of one media id must not collide inside one rotation.
        val plan = HostedMediaPlanner.plan(
            payload(
                wallpaper = customWallpaper(
                    mode = "CAROUSEL",
                    items = listOf(video(11, version = 1), video(11, version = 1)),
                ),
            ),
        )
        assertEquals(HostedMediaError.ITEM_INVALID, rejection(plan))
    }

    @Test
    fun rejectsItemWithoutUsableKeyOrExtension() {
        assertEquals(
            HostedMediaError.ITEM_INVALID,
            rejection(
                HostedMediaPlanner.plan(
                    payload(wallpaper = customWallpaper(items = listOf(video(0)))),
                ),
            ),
        )
        assertEquals(
            HostedMediaError.ITEM_INVALID,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        wallpaper = customWallpaper(
                            items = listOf(video(11).put("ext", "../mp4")),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun quizIntroMustBeAVideo() {
        assertEquals(
            HostedMediaError.ITEM_INVALID,
            rejection(
                HostedMediaPlanner.plan(
                    payload(wallpaper = customWallpaper(quizIntro = video(21, kind = "IMAGE"))),
                ),
            ),
        )
    }

    @Test
    fun quizIntroOverSixteenMebibytesIsRejected() {
        assertEquals(
            HostedMediaError.QUIZ_TOO_LARGE,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        wallpaper = customWallpaper(
                            quizIntro = video(21, size = PolicyVideoValidation.MAX_QUIZ_FILE_BYTES + 1),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun quizIntroOverFifteenSecondsIsRejected() {
        assertEquals(
            HostedMediaError.QUIZ_TOO_LONG,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        wallpaper = customWallpaper(
                            quizIntro = video(21, duration = PolicyVideoValidation.MAX_QUIZ_DURATION_MS + 1),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun quizIntroWithoutProbedMetadataIsRejected() {
        val missingSize = video(21).also { it.remove("sizeBytes") }
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(
                HostedMediaPlanner.plan(
                    payload(wallpaper = customWallpaper(quizIntro = missingSize)),
                ),
            ),
        )
    }

    @Test
    fun validQuizIntroIsCarriedWithItsItemId() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(wallpaper = customWallpaper(quizIntro = video(21, size = 1_000_000L))),
            ),
        )

        assertEquals("custom:21:1", plan.quizIntro?.key)
        assertEquals(21L, plan.quizIntroItemId)
    }

    @Test
    fun nullQuizIntroKeepsTheItemIdFallback() {
        val wallpaper = customWallpaper(quizIntro = JSONObject.NULL)
            .put("quizIntroItemId", 21L)
        val plan = success(HostedMediaPlanner.plan(payload(wallpaper = wallpaper)))

        assertNull(plan.quizIntro)
        assertEquals(21L, plan.quizIntroItemId)
    }

    @Test
    fun rejectsPayloadWithoutThemeFields() {
        // V51 fields are required even when a custom video wins: an old backend must be visible.
        val withoutTheme = payload().also { it.remove("wallpaperTheme") }
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(HostedMediaPlanner.plan(withoutTheme)),
        )
        val withoutRotate = payload().also { it.remove("wallpaperRotateMode") }
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(HostedMediaPlanner.plan(withoutRotate)),
        )
    }

    @Test
    fun customVideoWinsEvenWhenTheThemeIsUnsupported() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(mode = "CAROUSEL"),
                    theme = "POEM",
                ),
            ),
        )

        assertEquals(HostedMediaOrigin.CUSTOM, plan.source)
        assertEquals(2, plan.wallpaperItems.size)
    }

    @Test
    fun poemThemesAreRejectedWhenThereIsNoCustomVideo() {
        val emptyCustom = customWallpaper(items = emptyList())
        for (theme in listOf("POEM", "POEM_VIDEO")) {
            assertEquals(
                HostedMediaError.THEME_UNSUPPORTED,
                rejection(
                    HostedMediaPlanner.plan(
                        payload(wallpaper = emptyCustom, theme = theme, media = listOf(media("a.jpg"))),
                    ),
                ),
            )
        }
    }

    @Test
    fun mediaThemeBuildsThemeKeysAndKeepsImages() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(items = emptyList()),
                    theme = "MEDIA",
                    rotateMode = "SCROLL",
                    media = listOf(
                        media("kids.jpg", kind = "IMAGE"),
                        media("fish.mp4", kind = "VIDEO"),
                        media("portrait.jpg", kind = "IMAGE", orientation = "portrait"),
                    ),
                ),
            ),
        )

        assertEquals(HostedMediaOrigin.THEME, plan.source)
        assertEquals(HostedWallpaperMode.CAROUSEL, plan.mode)
        assertEquals(listOf("theme:kids.jpg:1", "theme:fish.mp4:1"), plan.wallpaperItems.map { it.key })
        assertEquals(listOf("media:kids.jpg", "media:fish.mp4"), plan.wallpaperItems.map { it.id })
        assertEquals(listOf(PolicyMediaKind.IMAGE, PolicyMediaKind.VIDEO), plan.wallpaperItems.map { it.kind })
        assertEquals(listOf(0, 1), plan.wallpaperItems.map { it.order })
        assertEquals(2, plan.sourceItemCount)
    }

    @Test
    fun mediaThemeWithoutPlayableItemPublishesEmptyNone() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(items = emptyList()),
                    theme = "MEDIA",
                    media = listOf(media("portrait.jpg", orientation = "portrait")),
                ),
            ),
        )

        assertEquals(HostedMediaOrigin.NONE, plan.source)
        assertTrue(plan.wallpaperItems.isEmpty())
        assertEquals(0, plan.sourceItemCount)
    }

    @Test
    fun dailyRotationUsesOneDay() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(items = emptyList()),
                    theme = "MEDIA",
                    rotateMode = "DAILY",
                    media = listOf(media("a.jpg", kind = "IMAGE"), media("b.jpg", kind = "IMAGE")),
                ),
            ),
        )

        assertEquals(HostedWallpaperMode.DAILY, plan.mode)
        assertEquals(1, plan.rotateDays)
        assertEquals(2, plan.wallpaperItems.size)
    }

    @Test
    fun intervalRotationCarriesTheDayCount() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(items = emptyList()),
                    theme = "MEDIA",
                    rotateMode = "INTERVAL",
                    rotateDays = 3,
                    media = listOf(media("a.jpg", kind = "IMAGE"), media("b.jpg", kind = "IMAGE")),
                ),
            ),
        )

        assertEquals(HostedWallpaperMode.DAILY, plan.mode)
        assertEquals(3, plan.rotateDays)
    }

    @Test
    fun intervalOutsideOneToThirtyIsRejected() {
        val emptyCustom = customWallpaper(items = emptyList())
        val media = listOf(media("a.jpg", kind = "IMAGE"))
        for (days in listOf(0, 31)) {
            assertEquals(
                HostedMediaError.BAD_PAYLOAD,
                rejection(
                    HostedMediaPlanner.plan(
                        payload(
                            wallpaper = emptyCustom,
                            theme = "MEDIA",
                            rotateMode = "INTERVAL",
                            rotateDays = days,
                            media = media,
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun fixedMediaSelectsTheNamedItem() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(items = emptyList()),
                    theme = "MEDIA",
                    rotateMode = "FIXED",
                    item = "media:b.jpg",
                    media = listOf(media("a.jpg", kind = "IMAGE"), media("b.jpg", kind = "IMAGE")),
                ),
            ),
        )

        assertEquals(HostedWallpaperMode.FIXED, plan.mode)
        assertEquals(listOf("theme:b.jpg:1"), plan.wallpaperItems.map { it.key })
        assertEquals("theme:b.jpg:1", plan.activeKey)
    }

    @Test
    fun fixedMediaMissingItemFallsBackToFirstWithAWarning() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(items = emptyList()),
                    theme = "MEDIA",
                    rotateMode = "FIXED",
                    item = "media:missing.jpg",
                    media = listOf(media("a.jpg", kind = "IMAGE")),
                ),
            ),
        )

        assertEquals(listOf("theme:a.jpg:1"), plan.wallpaperItems.map { it.key })
    }

    @Test
    fun unknownThemeModeAndItemShapeAreRejected() {
        val emptyCustom = customWallpaper(items = emptyList())
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(HostedMediaPlanner.plan(payload(wallpaper = emptyCustom, theme = "UNKNOWN"))),
        )
        assertEquals(
            HostedMediaError.BAD_MODE,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        wallpaper = emptyCustom,
                        theme = "MEDIA",
                        rotateMode = "LOOP",
                        media = listOf(media("a.jpg", kind = "IMAGE")),
                    ),
                ),
            ),
        )
        assertEquals(
            HostedMediaError.ITEM_INVALID,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        wallpaper = emptyCustom,
                        theme = "MEDIA",
                        rotateMode = "FIXED",
                        media = listOf(media("a.jpg", kind = "IMAGE", version = 0)),
                    ),
                ),
            ),
        )
    }

    @Test
    fun explicitPlaylistOverridesCustomVideosAndKeepsTheOrder() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(mode = "CAROUSEL"),
                    media = listOf(media("kids.jpg", kind = "IMAGE")),
                    playlist = listOf("media:kids.jpg", "custom:12"),
                    playlistSet = true,
                    intervalMs = 45_000L,
                ),
            ),
        )

        assertEquals(HostedMediaOrigin.PLAYLIST, plan.source)
        assertEquals(HostedWallpaperMode.CAROUSEL, plan.mode)
        assertEquals(
            listOf("theme:kids.jpg:1", "custom:12:1"),
            plan.wallpaperItems.map { it.key },
        )
        assertEquals(45_000L, plan.intervalMs)
        assertEquals(2, plan.sourceItemCount)
        assertNull(plan.activeKey)
    }

    @Test
    fun emptyExplicitPlaylistMeansTheDefaultWallpaper() {
        // The parent emptied the list on purpose: existing custom videos must not win.
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(mode = "CAROUSEL"),
                    playlist = emptyList(),
                    playlistSet = true,
                ),
            ),
        )

        assertEquals(HostedMediaOrigin.NONE, plan.source)
        assertEquals(HostedWallpaperMode.FIXED, plan.mode)
        assertTrue(plan.wallpaperItems.isEmpty())
        assertEquals(30_000L, plan.intervalMs)
    }

    @Test
    fun singlePlaylistItemIsFixedAndCarriesTheActiveKey() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(items = emptyList()),
                    media = listOf(media("a.jpg", kind = "IMAGE")),
                    playlist = listOf("media:a.jpg"),
                    playlistSet = true,
                ),
            ),
        )

        assertEquals(HostedWallpaperMode.FIXED, plan.mode)
        assertEquals(listOf("theme:a.jpg:1"), plan.wallpaperItems.map { it.key })
        assertEquals("theme:a.jpg:1", plan.activeKey)
    }

    @Test
    fun playlistWithoutTheSetFlagKeepsV52Precedence() {
        // A pre-V53 backend has no playlist decision; an empty list must not erase the videos.
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(mode = "CAROUSEL"),
                    playlist = emptyList(),
                ),
            ),
        )

        assertEquals(HostedMediaOrigin.CUSTOM, plan.source)
        assertEquals(2, plan.wallpaperItems.size)
    }

    @Test
    fun playlistSetFalseKeepsV52Precedence() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(mode = "CAROUSEL"),
                    playlist = emptyList(),
                    playlistSet = false,
                ),
            ),
        )

        assertEquals(HostedMediaOrigin.CUSTOM, plan.source)
    }

    @Test
    fun playlistItemOutsideTheLibraryIsRejected() {
        assertEquals(
            HostedMediaError.ITEM_INVALID,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        media = listOf(media("a.jpg", kind = "IMAGE")),
                        playlist = listOf("media:missing.jpg"),
                        playlistSet = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun duplicatePlaylistItemIsRejected() {
        assertEquals(
            HostedMediaError.ITEM_INVALID,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        media = listOf(media("a.jpg", kind = "IMAGE")),
                        playlist = listOf("media:a.jpg", "media:a.jpg"),
                        playlistSet = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun playlistIntervalOutsideFifteenSecondsToFiveMinutesIsRejected() {
        val media = listOf(media("a.jpg", kind = "IMAGE"))
        for (interval in listOf(14_999L, 300_001L, "30000")) {
            assertEquals(
                HostedMediaError.BAD_PAYLOAD,
                rejection(
                    HostedMediaPlanner.plan(
                        payload(
                            media = media,
                            playlist = listOf("media:a.jpg"),
                            playlistSet = true,
                            intervalMs = interval,
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun playlistLongerThanTwentyItemsIsRejected() {
        val catalog = (1..HOSTED_PLAYLIST_MAX_ITEMS + 1).map { index ->
            media("p$index.jpg", kind = "IMAGE")
        }
        assertEquals(
            HostedMediaError.ITEM_INVALID,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        media = catalog,
                        playlist = catalog.map { it.getString("id") },
                        playlistSet = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun unifiedQuizIntroResolvesABuiltInVideo() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(items = emptyList()),
                    media = listOf(media("fish.mp4"), media("kids.jpg", kind = "IMAGE")),
                    playlist = listOf("media:fish.mp4"),
                    playlistSet = true,
                    includeQuizIntro = true,
                    quizIntro = "media:fish.mp4",
                ),
            ),
        )

        assertEquals("theme:fish.mp4:1", plan.quizIntro?.key)
        assertNull(plan.quizIntroItemId)
    }

    @Test
    fun unifiedQuizIntroResolvesTheChildsOwnVideo() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(mode = "CAROUSEL"),
                    playlist = listOf("custom:11"),
                    playlistSet = true,
                    includeQuizIntro = true,
                    quizIntro = "custom:12",
                ),
            ),
        )

        assertEquals("custom:12:1", plan.quizIntro?.key)
        assertEquals(12L, plan.quizIntroItemId)
    }

    @Test
    fun unifiedQuizIntroMustPointAtAVideo() {
        assertEquals(
            HostedMediaError.ITEM_INVALID,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        wallpaper = customWallpaper(items = emptyList()),
                        media = listOf(media("kids.jpg", kind = "IMAGE")),
                        playlist = emptyList(),
                        playlistSet = true,
                        includeQuizIntro = true,
                        quizIntro = "media:kids.jpg",
                    ),
                ),
            ),
        )
    }

    @Test
    fun unifiedQuizIntroWithoutProbedMetadataIsRejected() {
        val noSize = media("fish.mp4").also { it.remove("sizeBytes") }
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        wallpaper = customWallpaper(items = emptyList()),
                        media = listOf(noSize),
                        playlist = emptyList(),
                        playlistSet = true,
                        includeQuizIntro = true,
                        quizIntro = "media:fish.mp4",
                    ),
                ),
            ),
        )
    }

    @Test
    fun explicitNullUnifiedQuizIntroDoesNotUseTheLegacyIntro() {
        val wallpaper = customWallpaper(quizIntro = video(21)).put("quizIntroItemId", 21L)
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = wallpaper,
                    playlist = listOf("custom:11"),
                    playlistSet = true,
                    includeQuizIntro = true,
                    quizIntro = null,
                ),
            ),
        )

        assertNull(plan.quizIntro)
        assertNull(plan.quizIntroItemId)
    }

    @Test
    fun libraryUsesTheUnifiedQuizIntro() {
        val parsed = library(
            payload(
                wallpaper = customWallpaper(items = emptyList()),
                theme = "MEDIA",
                media = listOf(media("fish.mp4")),
                includeQuizIntro = true,
                quizIntro = "media:fish.mp4",
            ),
        )

        assertEquals("theme:fish.mp4:1", parsed.quizIntro?.key)
        assertEquals(listOf("theme:fish.mp4:1"), parsed.allItems().map { it.key })
    }

    private fun library(result: JSONObject): HostedMediaLibrary =
        (HostedMediaPlanner.library(result) as HostedMediaLibraryResult.Success).library

    private fun libraryRejection(result: JSONObject): HostedMediaLibraryResult.Rejected =
        HostedMediaPlanner.library(result) as HostedMediaLibraryResult.Rejected

    @Test
    fun libraryCollectsEveryCandidateNotOnlyTheEffectiveSelection() {
        // V52 assets-first: preload caches the whole library so that any later selection is
        // local. A FIXED selection must therefore not narrow what gets downloaded.
        val parsed = library(
            payload(
                wallpaper = customWallpaper(
                    mode = "FIXED",
                    activeId = 12L,
                    items = listOf(video(11), video(12)),
                    quizIntro = video(21),
                ),
                theme = "MEDIA",
                rotateMode = "FIXED",
                item = "media:fish.mp4",
                media = listOf(
                    media("kids.jpg", kind = "IMAGE"),
                    media("fish.mp4"),
                ),
            ),
        )

        assertEquals(7L, parsed.memberId)
        assertEquals(
            listOf("theme:kids.jpg:1", "theme:fish.mp4:1"),
            parsed.themeItems.map { it.key },
        )
        assertEquals(
            listOf("custom:11:1", "custom:12:1"),
            parsed.customItems.map { it.key },
        )
        assertEquals("custom:21:1", parsed.quizIntro?.key)
        assertEquals(
            listOf(
                "theme:kids.jpg:1",
                "theme:fish.mp4:1",
                "custom:11:1",
                "custom:12:1",
                "custom:21:1",
            ),
            parsed.allItems().map { it.key },
        )
    }

    @Test
    fun librarySkipsPortraitThemeMedia() {
        val parsed = library(
            payload(
                wallpaper = customWallpaper(items = emptyList()),
                theme = "MEDIA",
                media = listOf(
                    media("wide.jpg", kind = "IMAGE"),
                    media("tall.jpg", kind = "IMAGE", orientation = "portrait"),
                ),
            ),
        )

        assertEquals(listOf("theme:wide.jpg:1"), parsed.themeItems.map { it.key })
        assertTrue(parsed.customItems.isEmpty())
        assertNull(parsed.quizIntro)
    }

    @Test
    fun libraryRejectsOldBackendAndHarvestsKeysForTheReport() {
        val rejected = libraryRejection(
            payload(
                wallpaper = customWallpaper(includeQuizIntro = false),
                theme = "MEDIA",
                media = listOf(media("a.jpg", kind = "IMAGE")),
            ),
        )

        assertEquals(HostedMediaError.BAD_PAYLOAD, rejected.error)
        assertEquals(
            listOf("theme:a.jpg:1", "custom:11:1", "custom:12:1"),
            rejected.keys,
        )
    }

    @Test
    fun libraryRejectsWithoutMember() {
        assertEquals(
            HostedMediaError.NO_MEMBER,
            libraryRejection(payload(learnerId = 0L)).error,
        )
    }

    @Test
    fun libraryRejectsDuplicateThemeKeys() {
        val rejected = libraryRejection(
            payload(
                wallpaper = customWallpaper(items = emptyList()),
                theme = "MEDIA",
                media = listOf(
                    media("a.jpg", kind = "IMAGE"),
                    media("a.jpg", kind = "IMAGE"),
                ),
            ),
        )

        assertEquals(HostedMediaError.ITEM_INVALID, rejected.error)
        assertEquals(listOf("theme:a.jpg:1"), rejected.keys)
    }

    @Test
    fun noBackgroundMusicFieldsKeepsTheV53Plan() {
        // A V53 backend never emits the pair; its absence means "no music", not "bad payload".
        val plan = success(HostedMediaPlanner.plan(payload()))

        assertNull(plan.audio)
    }

    @Test
    fun officialBackgroundMusicIsCarried() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    includeBackgroundMusic = true,
                    backgroundMusic = "official:playful",
                    backgroundMusicItem = audio(),
                ),
            ),
        )

        val bgm = plan.audio!!
        assertEquals("audio:official:playful:1", bgm.key)
        assertEquals("official:playful", bgm.id)
        assertEquals("official", bgm.source)
        assertEquals("俏皮时光", bgm.name)
        assertEquals("Playful", bgm.nameEn)
        assertEquals("Ahjay Stelino", bgm.artist)
        assertEquals(4_818_350L, bgm.sizeBytes)
        assertEquals(150_544L, bgm.durationMs)
        assertEquals("mp3", bgm.ext)
        assertEquals(1L, bgm.version)
    }

    @Test
    fun customBackgroundMusicIsCarried() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    includeBackgroundMusic = true,
                    backgroundMusic = "custom:12",
                    backgroundMusicItem = audio(
                        key = "custom:12",
                        source = "custom",
                        cacheKey = "audio:custom:12:1760000000000",
                        name = "宝贝的歌",
                        url = "https://oss.test/user_audio/12.mp3",
                        version = 1_760_000_000_000L,
                    ),
                ),
            ),
        )

        assertEquals("custom:12", plan.audio!!.id)
        assertEquals("custom", plan.audio!!.source)
        assertEquals(1_760_000_000_000L, plan.audio!!.version)
    }

    @Test
    fun backgroundMusicKeyWithoutItsItemIsRejected() {
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        includeBackgroundMusic = true,
                        backgroundMusic = "official:playful",
                    ),
                ),
            ),
        )
    }

    @Test
    fun backgroundMusicItemWithoutAKeyIsRejected() {
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        includeBackgroundMusic = true,
                        backgroundMusicItem = audio(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun backgroundMusicMustMatchItsItem() {
        val wrongId = audio().put("id", "official:other")
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        includeBackgroundMusic = true,
                        backgroundMusic = "official:playful",
                        backgroundMusicItem = wrongId,
                    ),
                ),
            ),
        )
        val wrongSource = audio().put("source", "custom")
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        includeBackgroundMusic = true,
                        backgroundMusic = "official:playful",
                        backgroundMusicItem = wrongSource,
                    ),
                ),
            ),
        )
    }

    @Test
    fun backgroundMusicKeyMustBeResolvable() {
        for (key in listOf("media:playful", "official:", "custom:0", "custom:abc")) {
            assertEquals(
                HostedMediaError.BAD_PAYLOAD,
                rejection(
                    HostedMediaPlanner.plan(
                        payload(
                            includeBackgroundMusic = true,
                            backgroundMusic = key,
                            backgroundMusicItem = audio(key = key),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun backgroundMusicCarriesOnlyUsableMetadata() {
        val badItems = listOf(
            audio(url = "http://media.test/policy-audio/playful.mp3"),
            audio(sha256 = "not-a-hash"),
            audio(sizeBytes = 0L),
            audio(sizeBytes = PolicyAudioLibrary.MAX_AUDIO_BYTES + 1),
            audio(durationMs = 0L),
            audio(durationMs = PolicyAudioLibrary.MAX_AUDIO_DURATION_MS + 1),
            audio(ext = "wav"),
            audio(version = 0L),
            audio(cacheKey = "playful"),
        )
        for (item in badItems) {
            assertEquals(
                HostedMediaError.BAD_PAYLOAD,
                rejection(
                    HostedMediaPlanner.plan(
                        payload(
                            includeBackgroundMusic = true,
                            backgroundMusic = "official:playful",
                            backgroundMusicItem = item,
                        ),
                    ),
                ),
            )
        }
    }

    // ----- V56 schema 5: pictures, relaxed quiz caps and the quiz-intro audio track ----------

    @Test
    fun schemaNewerThanSupportedIsRejected() {
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(HostedMediaPlanner.plan(payload(mediaSchema = WALLPAPER_MEDIA_SCHEMA + 1))),
        )
    }

    @Test
    fun schema5AllowsACatalogImageAsTheQuizIntro() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(items = emptyList()),
                    media = listOf(media("kids.jpg", kind = "IMAGE")),
                    mediaSchema = 5,
                    includeQuizIntro = true,
                    quizIntro = "media:kids.jpg",
                ),
            ),
        )

        assertEquals("theme:kids.jpg:1", plan.quizIntro?.key)
        assertEquals(PolicyMediaKind.IMAGE, plan.quizIntro?.kind)
        assertEquals(5, plan.mediaSchema)
    }

    @Test
    fun schema5AllowsTheChildsOwnImageAsTheQuizIntro() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(
                        items = listOf(video(11), video(13, kind = "IMAGE")),
                    ),
                    mediaSchema = 5,
                    includeQuizIntro = true,
                    quizIntro = "custom:13",
                ),
            ),
        )

        assertEquals("custom:13:1", plan.quizIntro?.key)
        assertEquals(13L, plan.quizIntroItemId)
        assertEquals(PolicyMediaKind.IMAGE, plan.quizIntro?.kind)
    }

    @Test
    fun schema5RemovesTheQuizVideoSizeAndDurationCaps() {
        // V56: a ready 17.9 MB / 20 s quiz clip is a legal choice; only schema 4 keeps the caps.
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(
                        items = listOf(
                            video(11),
                            video(
                                21,
                                size = PolicyVideoValidation.MAX_QUIZ_FILE_BYTES + 1_900_001L,
                                duration = PolicyVideoValidation.MAX_QUIZ_DURATION_MS + 5_000L,
                            ),
                        ),
                    ),
                    mediaSchema = 5,
                    includeQuizIntro = true,
                    quizIntro = "custom:21",
                ),
            ),
        )

        assertEquals("custom:21:1", plan.quizIntro?.key)
        assertEquals(PolicyMediaKind.VIDEO, plan.quizIntro?.kind)
    }

    @Test
    fun schema5AllowsASavedCustomImageInThePlaylist() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(
                        items = listOf(video(11), video(13, kind = "IMAGE")),
                    ),
                    mediaSchema = 5,
                    playlist = listOf("custom:13", "custom:11"),
                    playlistSet = true,
                ),
            ),
        )

        assertEquals(
            listOf("custom:13:1", "custom:11:1"),
            plan.wallpaperItems.map { it.key },
        )
        assertEquals(
            listOf(PolicyMediaKind.IMAGE, PolicyMediaKind.VIDEO),
            plan.wallpaperItems.map { it.kind },
        )
    }

    @Test
    fun schema4RejectsAPlaylistThatNamesACustomImage() {
        // The same row saved by an old backend must stay a visible failure, not a silent drop.
        assertEquals(
            HostedMediaError.ITEM_INVALID,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        wallpaper = customWallpaper(
                            items = listOf(video(11), video(13, kind = "IMAGE")),
                        ),
                        playlist = listOf("custom:13"),
                        playlistSet = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun schema5UsesACustomImageWhenNoExplicitPlaylistWasSaved() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(items = listOf(video(13, kind = "IMAGE"))),
                    mediaSchema = 5,
                ),
            ),
        )

        assertEquals(HostedMediaOrigin.CUSTOM, plan.source)
        assertEquals(listOf("custom:13:1"), plan.wallpaperItems.map { it.key })
        assertEquals(PolicyMediaKind.IMAGE, plan.wallpaperItems.single().kind)
    }

    @Test
    fun libraryOnSchema5IncludesCustomImages() {
        val parsed = library(
            payload(
                wallpaper = customWallpaper(
                    items = listOf(video(11), video(13, kind = "IMAGE")),
                ),
                mediaSchema = 5,
            ),
        )

        assertEquals(listOf("custom:11:1", "custom:13:1"), parsed.customItems.map { it.key })
    }

    @Test
    fun schema5CarriesTheQuizIntroAudio() {
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(items = emptyList()),
                    media = listOf(media("fish.mp4")),
                    mediaSchema = 5,
                    includeQuizIntro = true,
                    quizIntro = "media:fish.mp4",
                    includeQuizAudio = true,
                    quizAudio = "official:playful",
                    quizAudioEnabled = true,
                    quizAudioItem = audio(),
                ),
            ),
        )

        assertEquals("audio:official:playful:1", plan.quizAudio?.key)
        assertEquals("official:playful", plan.quizAudio?.id)
        assertTrue(plan.quizAudioEnabled)
    }

    @Test
    fun schema5DisabledQuizAudioDoesNotEnterTheCapture() {
        // The row keeps the track so the parent can re-enable it; the TV must not download it.
        val plan = success(
            HostedMediaPlanner.plan(
                payload(
                    wallpaper = customWallpaper(items = emptyList()),
                    media = listOf(media("fish.mp4")),
                    mediaSchema = 5,
                    includeQuizIntro = true,
                    quizIntro = "media:fish.mp4",
                    includeQuizAudio = true,
                    quizAudio = "official:playful",
                    quizAudioEnabled = false,
                    quizAudioItem = audio(),
                ),
            ),
        )

        assertNull(plan.quizAudio)
        assertEquals(false, plan.quizAudioEnabled)
    }

    @Test
    fun schema4RejectsAnEnabledQuizAudio() {
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        wallpaper = customWallpaper(items = emptyList()),
                        media = listOf(media("fish.mp4")),
                        mediaSchema = 4,
                        includeQuizIntro = true,
                        quizIntro = "media:fish.mp4",
                        includeQuizAudio = true,
                        quizAudio = "official:playful",
                        quizAudioEnabled = true,
                        quizAudioItem = audio(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun quizAudioEnabledWithoutATrackIsRejected() {
        assertEquals(
            HostedMediaError.ITEM_INVALID,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        wallpaper = customWallpaper(items = emptyList()),
                        media = listOf(media("fish.mp4")),
                        mediaSchema = 5,
                        includeQuizIntro = true,
                        quizIntro = "media:fish.mp4",
                        includeQuizAudio = true,
                        quizAudioEnabled = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun quizAudioEnabledWithoutAQuizVisualIsRejected() {
        assertEquals(
            HostedMediaError.ITEM_INVALID,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        mediaSchema = 5,
                        includeQuizIntro = true,
                        quizIntro = null,
                        includeQuizAudio = true,
                        quizAudio = "official:playful",
                        quizAudioEnabled = true,
                        quizAudioItem = audio(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun quizAudioKeyWithoutItsItemIsRejected() {
        assertEquals(
            HostedMediaError.BAD_PAYLOAD,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        wallpaper = customWallpaper(items = emptyList()),
                        media = listOf(media("fish.mp4")),
                        mediaSchema = 5,
                        includeQuizIntro = true,
                        quizIntro = "media:fish.mp4",
                        includeQuizAudio = true,
                        quizAudio = "official:playful",
                        quizAudioEnabled = true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun quizAudioEnabledFlagMustBeABoolean() {
        val result = payload(
            wallpaper = customWallpaper(items = emptyList()),
            media = listOf(media("fish.mp4")),
            mediaSchema = 5,
            includeQuizIntro = true,
            quizIntro = "media:fish.mp4",
            includeQuizAudio = true,
            quizAudio = "official:playful",
            quizAudioItem = audio(),
        ).put("wallpaperQuizAudioEnabled", "yes")

        assertEquals(HostedMediaError.BAD_PAYLOAD, rejection(HostedMediaPlanner.plan(result)))
    }

    @Test
    fun schema5TreatsABlankOfficialIdAsALegacyThemeEntry() {
        // V56 sends null for the legacy catalog rows, but an empty string means the same thing:
        // the row has no official id. Rejecting it would take the whole hosted plan down.
        val themeEntry = media("kid.jpg", kind = "IMAGE").put("officialId", "")
        val parsed = library(
            payload(
                wallpaper = customWallpaper(items = emptyList()),
                theme = "MEDIA",
                item = "media:kid.jpg",
                media = listOf(themeEntry),
                mediaSchema = 5,
            ),
        )

        assertEquals(1, parsed.themeItems.size)
        assertNull(parsed.themeItems.single().officialId)
    }

    @Test
    fun schema5TreatsANullOfficialIdAsALegacyThemeEntry() {
        val themeEntry = media("kid.jpg", kind = "IMAGE").put("officialId", JSONObject.NULL)
        val parsed = library(
            payload(
                wallpaper = customWallpaper(items = emptyList()),
                theme = "MEDIA",
                item = "media:kid.jpg",
                media = listOf(themeEntry),
                mediaSchema = 5,
            ),
        )

        assertEquals(1, parsed.themeItems.size)
        assertNull(parsed.themeItems.single().officialId)
    }

    @Test
    fun schema5StillRejectsANonOfficialCatalogId() {
        val themeEntry = media("fish.mp4").put("officialId", "youtube:fish")
        assertEquals(
            HostedMediaError.ITEM_INVALID,
            rejection(
                HostedMediaPlanner.plan(
                    payload(
                        wallpaper = customWallpaper(items = emptyList()),
                        media = listOf(themeEntry),
                        mediaSchema = 5,
                    ),
                ),
            ),
        )
    }
}