package app.orchard.tv.policy.media

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The manifest is untrusted cloud input; every rejection below must stay closed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OfficialMediaCatalogTest {
    private val manifestUrl = "https://media.planyourtv.me/media/policy-videos/manifest.json"
    private val host = "https://media.planyourtv.me"

    @Test
    fun validManifestKeepsMetadataRolesAndUrls() {
        val result = OfficialMediaCatalog.parse(manifest(), manifestUrl)
        val catalog = result.catalog
        assertNotNull(catalog)
        assertEquals("2026-11-14", catalog!!.pack)
        assertEquals(1, catalog.items.size)
        val item = catalog.items.single()
        assertEquals("official:hero", item.id)
        assertEquals("勇者无畏", item.name)
        assertEquals("Fearless", item.nameEn)
        assertEquals(setOf(PolicyVideoRole.QUIZ_INTRO, PolicyVideoRole.LOCK_WALLPAPER), item.roles)
        assertEquals(10_077L, item.durationMs)
        assertEquals(3_849_248L, item.bytes)
        assertEquals("$host/media/policy-videos/hero.mp4", item.url)
        assertEquals("$host/media/policy-videos/hero.jpg", item.poster.url)
        assertEquals(2_000L, item.poster.atMs)
    }

    @Test
    fun officialFilmMayKeepItsSourceBitrate() {
        // The cloud library keeps the 1080p source, so the local 4 MiB upload
        // limit must not apply to official entries.
        val result = OfficialMediaCatalog.parse(
            manifest(JSONArray().put(item().put("bytes", 17_726_732L))),
            manifestUrl,
        )
        val catalog = result.catalog
        assertNotNull(catalog)
        assertEquals(17_726_732L, catalog!!.items.single().bytes)
    }

    @Test
    fun videoUrlMustStayOnTheManifestHost() {
        assertRejected(item().put("url", "https://cdn.example.com/media/policy-videos/hero.mp4"))
        assertRejected(item().put("url", "http://media.planyourtv.me/media/policy-videos/hero.mp4"))
        assertRejected(item().put("url", "$host/media/policy-videos/%2e%2e/hero.mp4"))
        assertRejected(item().put("url", "$host/media/policy-videos/../hero.mp4"))
        assertRejected(item().put("url", "$host/media/policy-videos/hero.mp4?x=1"))
        assertRejected(item().put("url", "$host/media/policy-videos/hero.mp4#x"))
        assertRejected(item().put("url", "https://user@media.planyourtv.me/media/policy-videos/hero.mp4"))
        assertRejected(item().put("url", "https://media.planyourtv.me:8443/media/policy-videos/hero.mp4"))
        assertRejected(item().put("url", "$host/other/hero.mp4"))
    }

    @Test
    fun posterMustFollowTheSameUrlRules() {
        val poster = poster().put("url", "https://cdn.example.com/media/policy-videos/hero.jpg")
        assertRejected(item().put("poster", poster))
    }

    @Test
    fun duplicateAndMalformedItemsAreRejected() {
        val videos = JSONArray().put(item()).put(item())
        assertRejected(manifest(videos))
        assertRejected(item().put("roles", JSONArray().put("both")))
        assertRejected(item().put("roles", JSONArray().put("quiz").put("quiz")))
        assertRejected(item().put("roles", JSONArray()))
        assertRejected(item().put("durationMs", PolicyVideoValidation.MAX_DURATION_MS + 1))
        assertRejected(item().put("durationMs", 0))
        assertRejected(item().put("width", 1440).put("height", 1080))
        assertRejected(item().put("bytes", PolicyVideoValidation.MAX_OFFICIAL_FILE_BYTES + 1))
        assertRejected(item().put("sha256", "A".repeat(64)))
        assertRejected(item().put("hasAudio", "yes"))
        assertRejected(item().put("name", " "))
        assertRejected(item().put("name", "x".repeat(81)))
        assertRejected(item().put("poster", poster().put("bytes", PolicyVideoValidation.MAX_POSTER_BYTES + 1)))
        assertRejected(item().put("poster", poster().put("atMs", 20_000L)))
    }

    @Test
    fun envelopeMustBeSchemaOneAndParseable() {
        assertRejected(JSONObject(manifest()).put("schema", 2).toString())
        assertRejected(JSONObject(manifest()).put("pack", "bad pack").toString())
        assertRejected(JSONObject(manifest())
            .put("videos", JSONArray().put(JSONObject.NULL))
            .toString())
        val tooMany = JSONArray()
        repeat(13) { index -> tooMany.put(item().put("id", "official:film-$index")) }
        assertRejected(manifest(tooMany))
        assertRejected("{")
        // The manifest URL itself is part of the trust boundary.
        val parsed = OfficialMediaCatalog.parse(
            manifest(),
            "http://media.planyourtv.me/media/policy-videos/manifest.json",
        )
        assertNull(parsed.catalog)
        assertEquals("CATALOG_INVALID", parsed.error)
    }

    @Test
    fun roleWireConversionTreatsBothAsARequestOnlyPair() {
        assertEquals(setOf(PolicyVideoRole.QUIZ_INTRO), PolicyVideoRole.fromRequest("quiz"))
        assertEquals(setOf(PolicyVideoRole.LOCK_WALLPAPER), PolicyVideoRole.fromRequest("lock"))
        assertEquals(PolicyVideoRole.entries.toSet(), PolicyVideoRole.fromRequest("both"))
        assertNull(PolicyVideoRole.fromRequest("all"))
        assertEquals(emptySet<PolicyVideoRole>(), PolicyVideoRole.fromOptionalRequest(null))
        assertEquals(emptySet<PolicyVideoRole>(), PolicyVideoRole.fromOptionalRequest(""))
    }

    private fun assertRejected(item: JSONObject) = assertRejected(manifest(JSONArray().put(item)))

    private fun assertRejected(raw: String) {
        val parsed = OfficialMediaCatalog.parse(raw, manifestUrl)
        assertNull(parsed.catalog)
        assertEquals("CATALOG_INVALID", parsed.error)
    }

    private fun manifest(videos: JSONArray = JSONArray().put(item())): String = JSONObject()
        .put("schema", 1)
        .put("pack", "2026-11-14")
        .put("updatedAt", "2026-11-14T00:00:00Z")
        .put("videos", videos)
        .toString()

    private fun item(): JSONObject = JSONObject()
        .put("id", "official:hero")
        .put("name", "勇者无畏")
        .put("nameEn", "Fearless")
        .put("roles", JSONArray().put("quiz").put("lock"))
        .put("durationMs", 10_077L)
        .put("width", 1_920)
        .put("height", 1_080)
        .put("fps", 30)
        .put("bytes", 3_849_248L)
        .put("sha256", "a".repeat(64))
        .put("hasAudio", true)
        .put("url", "$host/media/policy-videos/hero.mp4")
        .put("poster", poster())

    private fun poster(): JSONObject = JSONObject()
        .put("url", "$host/media/policy-videos/hero.jpg")
        .put("sha256", "b".repeat(64))
        .put("bytes", 50_000L)
        .put("atMs", 2_000L)
}
