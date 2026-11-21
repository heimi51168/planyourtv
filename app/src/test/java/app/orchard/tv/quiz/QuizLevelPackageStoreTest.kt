package app.orchard.tv.quiz

import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizCollectionLevel
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizConfigDocument
import com.planyourtv.policy.quiz.QuizLevel
import com.planyourtv.policy.quiz.QuizLevelPackage
import com.planyourtv.policy.quiz.QuizPackageSource
import com.planyourtv.policy.quiz.QuizPresetBank
import com.planyourtv.policy.quiz.QuizTypeFilter
import com.planyourtv.policy.quiz.QuizWrongReviewLevel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizLevelPackageStoreTest {
    private val now = 1_760_000_000_000L

    private fun scope(subject: String = "CHINESE") = QuizAnswerScope.create(
        grade = 4,
        semester = 1,
        subject = subject,
        textbook = "BUBIAN",
        scopeType = "UNIT",
        unitNo = 2,
        difficulty = null,
    )!!

    private fun courseLevel(id: String, name: String, subject: String = "CHINESE") =
        QuizLevel.create(scope(subject), name = name, id = id)!!

    private fun document(levels: List<QuizLevel> = emptyList()): QuizConfigDocument =
        QuizLocalConfigDocument.create(
            installId = "test-install-id",
            nowMs = now,
            config = QuizConfig(),
            legacyAnswerDisplaySec = 3,
            levels = levels,
            migrated = false,
        )

    @Test
    fun absentExtensionBecomesPresentWithoutTouchingLegacyLevelsOrOtherKeys() {
        val base = document(listOf(courseLevel("lvl-a", "语文")))
        val extensions = JSONObject(base.extensions.toString())
        extensions.getJSONObject("local").put("keepMe", "yes")
        extensions.put("other", JSONObject().put("value", 7))
        val withOtherKeys = base.copy(extensions = extensions)
        assertTrue(QuizLevelPackageStore.read(withOtherKeys) is QuizLevelPackageStore.Extension.Absent)

        val pkg = QuizLevelPackage(
            id = "lvl-a",
            name = "语文",
            sources = listOf(
                QuizPackageSource.Course(scope(), QuizTypeFilter.SINGLE),
                QuizPackageSource.WrongReview,
            ),
            enabled = false,
        )
        val updated = QuizLevelPackageStore.withPackages(withOtherKeys, listOf(pkg))
        val read = QuizLevelPackageStore.read(updated)
        assertTrue(read is QuizLevelPackageStore.Extension.Present)
        assertEquals(listOf(pkg), (read as QuizLevelPackageStore.Extension.Present).packages)
        assertFalse(read.overLimit)
        assertEquals("yes", updated.extensions.getJSONObject("local").getString("keepMe"))
        assertEquals(7, updated.extensions.getJSONObject("other").getInt("value"))
        assertEquals(base.levels, updated.levels)
        // The source document is not mutated by a rejected/successful save.
        assertTrue(QuizLevelPackageStore.read(base) is QuizLevelPackageStore.Extension.Absent)
    }

    @Test
    fun malformedExtensionsFailClosed() {
        val base = document()
        val notAnObject = JSONObject(base.extensions.toString()).put("local", "broken")
        assertTrue(
            QuizLevelPackageStore.read(base.copy(extensions = notAnObject))
                is QuizLevelPackageStore.Extension.Broken,
        )

        val wrongVersion = documentWithMarker(
            JSONObject().put("version", 1).put("levels", JSONArray()),
        )
        assertTrue(
            QuizLevelPackageStore.read(wrongVersion) is QuizLevelPackageStore.Extension.Broken,
        )

        val invalidLevel = documentWithMarker(
            JSONObject().put("version", 2).put(
                "levels",
                JSONArray().put(
                    JSONObject()
                        .put("id", "lvl-a")
                        .put("sources", JSONArray()),
                ),
            ),
        )
        assertTrue(
            QuizLevelPackageStore.read(invalidLevel) is QuizLevelPackageStore.Extension.Broken,
        )
    }

    @Test
    fun migrationPreservesOrderNamesAndUniqueIds() {
        val levels = QuizLevelPackageStore.migrate(
            courseLevels = listOf(
                courseLevel("lvl-shared", "语文"),
                courseLevel("lvl-math", "数学", subject = "MATH"),
            ),
            customLevels = listOf(
                QuizCollectionLevel("lvl-shared", "成语", "col-a"),
                QuizCollectionLevel("lvl-custom", "", "col-b"),
            ),
            wrongLevel = QuizWrongReviewLevel("wrong-1", "错题"),
        )
        assertEquals(5, levels.size)
        assertEquals("语文", levels[0].name)
        assertEquals("lvl-shared", levels[0].id)
        assertEquals("col-a", (levels[2].sources.single() as QuizPackageSource.Collection).collectionId)
        assertNotEquals(levels[0].id, levels[2].id)
        assertEquals("错题", levels[4].name)
        assertEquals(QuizPackageSource.WrongReview, levels[4].sources.single())
    }

    @Test
    fun overLimitIsReportedButEveryMigratedPackageIsKept() {
        val packages = (0 until QuizLevelPackage.MAX_LEVELS + 1).map {
            QuizLevelPackage(
                id = "lvl-$it",
                name = "",
                sources = listOf(QuizPackageSource.Collection("col-$it")),
            )
        }
        val read = QuizLevelPackageStore.read(documentWithPackages(packages))
        assertTrue(read is QuizLevelPackageStore.Extension.Present)
        val present = read as QuizLevelPackageStore.Extension.Present
        assertTrue(present.overLimit)
        assertEquals(packages.size, present.packages.size)
    }

    @Test
    fun emptyPackageListIsAValidExplicitState() {
        val read = QuizLevelPackageStore.read(documentWithPackages(emptyList()))
        assertTrue(read is QuizLevelPackageStore.Extension.Present)
        assertTrue((read as QuizLevelPackageStore.Extension.Present).packages.isEmpty())
        assertFalse(read.overLimit)
    }

    @Test
fun presetSourceIsWrittenAtVersionThreeAndReadBack() {
    val preset = QuizLevelPackage(
        id = "lvl-life",
        name = "生活常识",
        sources = listOf(QuizPackageSource.Preset(QuizPresetBank.PARENT_LIFE)),
    )
    val saved = QuizLevelPackageStore.withPackages(document(), listOf(preset))
    val marker = saved.extensions.getJSONObject("local").getJSONObject("levelPackages")
    assertEquals(3, marker.getInt("version"))
    assertEquals(
        "parent-life",
        marker.getJSONArray("levels").getJSONObject(0)
            .getJSONArray("sources").getJSONObject(0).getString("bankId"),
    )
    val read = QuizLevelPackageStore.read(saved)
    assertTrue(read is QuizLevelPackageStore.Extension.Present)
    assertEquals(
        listOf(preset),
        (read as QuizLevelPackageStore.Extension.Present).packages,
    )
}

@Test
fun versionTwoDocumentsStayReadable() {
    val v2 = documentWithMarker(
        JSONObject().put("version", 2).put(
            "levels",
            JSONArray().put(
                JSONObject()
                    .put("id", "lvl-a")
                    .put("sources", JSONArray().put(JSONObject().put("type", "wrong"))),
            ),
        ),
    )
    val read = QuizLevelPackageStore.read(v2)
    assertTrue(read is QuizLevelPackageStore.Extension.Present)
    assertEquals(1, (read as QuizLevelPackageStore.Extension.Present).packages.size)
}

@Test
fun unknownPresetBankFailsClosed() {
    val broken = documentWithMarker(
        JSONObject().put("version", 3).put(
            "levels",
            JSONArray().put(
                JSONObject()
                    .put("id", "lvl-a")
                    .put(
                        "sources",
                        JSONArray().put(
                            JSONObject().put("type", "preset").put("bankId", "unknown-bank"),
                        ),
                    ),
            ),
        ),
    )
    assertTrue(
        QuizLevelPackageStore.read(broken) is QuizLevelPackageStore.Extension.Broken,
    )
}

private fun documentWithPackages(packages: List<QuizLevelPackage>): QuizConfigDocument =
        QuizLevelPackageStore.withPackages(document(), packages)

    private fun documentWithMarker(marker: JSONObject): QuizConfigDocument {
        val base = document()
        val extensions = JSONObject(base.extensions.toString())
        extensions.put("local", JSONObject().put("levelPackages", marker))
        return base.copy(extensions = extensions)
    }
}
