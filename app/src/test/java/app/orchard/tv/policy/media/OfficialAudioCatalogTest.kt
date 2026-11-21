package app.orchard.tv.policy.media

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The music manifest is untrusted cloud input; every rejection below must stay closed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OfficialAudioCatalogTest {
    private val manifestUrl = "https://media.planyourtv.me/media/policy-audio/manifest.json"
    private val host = "https://media.planyourtv.me"
    private val sha = "a".repeat(64)

    @Test
    fun validManifestKeepsAttributionAndContentAddressedUrl() {
        val catalog = parse(manifest()).catalog
        assertNotNull(catalog)
        assertEquals("kids-2026-11", catalog!!.pack)
        assertEquals(1, catalog.tracks.size)
        val track = catalog.tracks.single()
        assertEquals("official:morning", track.id)
        assertEquals("清晨", track.name)
        assertEquals("Morning", track.nameEn)
        assertEquals("Ahjay Stelino", track.artist)
        assertEquals("CC-BY-4.0", track.license)
        assertEquals("https://example.com/music/morning", track.sourceUrl)
        assertEquals(95_000L, track.durationMs)
        assertEquals(1_500_000L, track.bytes)
        assertEquals(sha, track.sha256)
        assertEquals("$host/media/policy-audio/kids-2026-11/$sha.mp3", track.url)
    }

    @Test
    fun objectUrlMustBeExactContentOnTheManifestHost() {
        assertRejected(track().put("url", "https://cdn.example.com/media/policy-audio/kids-2026-11/$sha.mp3"))
        assertRejected(track().put("url", "http://media.planyourtv.me/media/policy-audio/kids-2026-11/$sha.mp3"))
        assertRejected(track().put("url", "$host/media/policy-audio/kids-2026-11/$sha.mp3?x=1"))
        assertRejected(track().put("url", "$host/media/policy-audio/kids-2026-11/$sha.mp3#x"))
        assertRejected(track().put("url", "https://user@media.planyourtv.me/media/policy-audio/kids-2026-11/$sha.mp3"))
        assertRejected(track().put("url", "https://media.planyourtv.me:8443/media/policy-audio/kids-2026-11/$sha.mp3"))
        assertRejected(track().put("url", "$host/media/policy-audio/bad pack/$sha.mp3"))
        assertRejected(track().put("url", "$host/media/policy-audio/kids-2026-11/${"b".repeat(64)}.mp3"))
        assertRejected(track().put("url", "$host/media/policy-audio/kids-2026-11/not-a-hash.mp3"))
        assertRejected(track().put("url", "$host/media/policy-audio/kids-2026-11/%2e%2e/$sha.mp3"))
        assertRejected(track().put("url", "$host/other/$sha.mp3"))
        // A track from an earlier publishing batch remains valid when the manifest pack moves.
        val previousPack = parse(manifest(JSONArray().put(
            track().put("url", "$host/media/policy-audio/kids-2026-10/$sha.mp3"),
        )))
        assertNotNull(previousPack.catalog)
        assertEquals(
            "$host/media/policy-audio/kids-2026-10/$sha.mp3",
            previousPack.catalog!!.tracks.single().url,
        )
    }

    @Test
    fun attributionMustBeSafeAndLicenseIsRequired() {
        assertRejected(track().put("license", ""))
        assertRejected(track().apply { remove("license") })
        assertRejected(track().put("license", "x".repeat(81)))
        assertRejected(track().put("license", "CC\nBY"))
        assertRejected(track().put("artist", 3))
        assertRejected(track().put("artist", "x".repeat(81)))
        assertRejected(track().put("sourceUrl", "http://example.com/music"))
        assertRejected(track().put("sourceUrl", "https://user:pass@example.com/music"))
        assertRejected(track().put("sourceUrl", "javascript:alert(1)"))
        // An optional field may be absent, but a supplied one cannot be blank.
        assertRejected(track().put("sourceUrl", " "))
        val noAttribution = parse(manifest(JSONArray().put(track().put("artist", JSONObject.NULL))))
        assertNotNull(noAttribution.catalog)
        assertNull(noAttribution.catalog!!.tracks.single().artist)
    }

    @Test
    fun limitsAndDuplicatesAreRejected() {
        assertRejected(track().put("durationMs", 0))
        assertRejected(track().put("durationMs", PolicyAudioLibrary.MAX_AUDIO_DURATION_MS + 1))
        assertRejected(track().put("bytes", 0))
        assertRejected(track().put("bytes", PolicyAudioLibrary.MAX_AUDIO_BYTES + 1))
        assertRejected(track().put("sha256", "A".repeat(64)))
        assertRejected(track().put("id", "official:Bad Slug"))
        assertRejected(track().put("name", " "))
        assertRejected(track().put("name", "x".repeat(81)))
        assertRejected(track().put("nameEn", "x".repeat(81)))
        val duplicates = JSONArray().put(track()).put(track())
        assertRejected(manifest(duplicates))
        val tooMany = JSONArray()
        repeat(OfficialAudioCatalog.MAX_TRACKS + 1) { index ->
            tooMany.put(track().put("id", "official:track-$index"))
        }
        assertRejected(manifest(tooMany))
    }

    @Test
    fun envelopeAndUrlArePartOfTheTrustBoundary() {
        assertRejected(JSONObject(manifest()).put("schema", 2).toString())
        assertRejected(JSONObject(manifest()).put("pack", "bad pack").toString())
        assertRejected(JSONObject(manifest()).remove("updatedAt").toString())
        assertRejected(JSONObject(manifest()).remove("tracks").toString())
        assertRejected(JSONObject(manifest()).put("tracks", JSONArray().put(JSONObject.NULL)).toString())
        assertRejected("{")
        val parsed = OfficialAudioCatalog.parse(
            manifest(),
            "http://media.planyourtv.me/media/policy-audio/manifest.json",
        )
        assertNull(parsed.catalog)
        assertEquals("AUDIO_CATALOG_INVALID", parsed.error)
    }

    @Test
    fun anEmptyButWellFormedCatalogueIsAllowed() {
        val raw = JSONObject(manifest()).put("tracks", JSONArray()).toString()
        val catalog = OfficialAudioCatalog.parse(raw, manifestUrl).catalog
        assertNotNull(catalog)
        assertEquals(0, catalog!!.tracks.size)
    }

    private fun parse(raw: String): OfficialAudioCatalog.ParseResult =
        OfficialAudioCatalog.parse(raw, manifestUrl)

    private fun assertRejected(item: JSONObject) = assertRejected(manifest(JSONArray().put(item)))

    private fun assertRejected(raw: String) {
        val parsed = OfficialAudioCatalog.parse(raw, manifestUrl)
        assertNull(parsed.catalog)
        assertEquals("AUDIO_CATALOG_INVALID", parsed.error)
    }

    private fun manifest(tracks: JSONArray = JSONArray().put(track())): String = JSONObject()
        .put("schema", 1)
        .put("pack", "kids-2026-11")
        .put("updatedAt", "2026-11-21T00:00:00Z")
        .put("tracks", tracks)
        .toString()

    private fun track(): JSONObject = JSONObject()
        .put("id", "official:morning")
        .put("name", "清晨")
        .put("nameEn", "Morning")
        .put("artist", "Ahjay Stelino")
        .put("license", "CC-BY-4.0")
        .put("sourceUrl", "https://example.com/music/morning")
        .put("durationMs", 95_000L)
        .put("bytes", 1_500_000L)
        .put("sha256", sha)
        .put("url", "$host/media/policy-audio/kids-2026-11/$sha.mp3")
}