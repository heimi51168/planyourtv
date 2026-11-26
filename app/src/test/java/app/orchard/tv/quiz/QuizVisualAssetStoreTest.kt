package app.orchard.tv.quiz

import android.content.Context
import com.planyourtv.policy.quiz.QuizItem
import com.planyourtv.policy.quiz.QuizRichVisual
import com.planyourtv.policy.quiz.QuizVisual
import java.io.File
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuizVisualAssetStoreTest {
    private lateinit var context: Context
    private val assetId = "test/figure"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        QuizVisualAssetStore.install(context)
        QuizVisualAssetStore.clearNamespace(assetId)
    }

    @After
    fun tearDown() {
        QuizVisualAssetStore.clearNamespace(assetId)
    }

    @Test
    fun fileForReadsOnlyTheContentAddressedNamespace() {
        val bytes = pngBytes()
        val hex = QuizVisualAssetStore.sha256(bytes)
        val dir = File(File(context.filesDir, "quiz-assets/$assetId"), hex)
        assertTrue(dir.mkdirs())
        File(dir, "media.png").writeBytes(bytes)

        val rich = rich(hex)
        val file = QuizVisualAssetStore.fileFor(rich)
        assertTrue(file != null && file.isFile)
        assertTrue(QuizVisualAssetStore.hasAllAssets(listOf(item(rich))))

        val wrong = rich.copy(sha256 = "sha256:" + "0".repeat(64))
        assertNull(QuizVisualAssetStore.fileFor(wrong))
        assertFalse(QuizVisualAssetStore.hasAllAssets(listOf(item(wrong))))
    }

    @Test
    fun rejectsUnsafeAssetIdsAndNonPngReferences() {
        val bytes = pngBytes()
        val hex = QuizVisualAssetStore.sha256(bytes)
        assertNull(QuizVisualAssetStore.fileFor(rich(hex).copy(assetId = "../escape")))
        assertNull(QuizVisualAssetStore.fileFor(rich(hex).copy(mime = "image/jpeg")))
        assertNull(QuizVisualAssetStore.fileFor(rich(hex).copy(kind = "video")))
        assertNull(QuizVisualAssetStore.fileFor(rich(hex).copy(sha256 = "sha256:short")))

        val nonPng = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        var failed = false
        try {
            QuizVisualAssetStore.validatePng(nonPng)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun onlyHttpsPngUrlsAreAllowed() {
        assertTrue(
            QuizVisualAssetStore.isAllowedImageUrl(
                "https://quiz-assets.oss-cn-hangzhou.aliyuncs.com/qb_figure/a.png",
            ),
        )
        assertFalse(QuizVisualAssetStore.isAllowedImageUrl("http://example.com/a.png"))
        assertFalse(QuizVisualAssetStore.isAllowedImageUrl("https://example.com/a.jpg"))
        assertFalse(QuizVisualAssetStore.isAllowedImageUrl("https://user@example.com/a.png"))
        assertFalse(QuizVisualAssetStore.isAllowedImageUrl("nope"))
    }

    @Test
    fun pruneKeepsTheCurrentRoundsHash() {
        val first = pngBytes(1)
        val second = pngBytes(2)
        writeAsset(QuizVisualAssetStore.sha256(first), first)
        writeAsset(QuizVisualAssetStore.sha256(second), second)

        QuizVisualAssetStore.prune(
            protectedShas = setOf(QuizVisualAssetStore.sha256(first)),
            maxBytes = 0L,
        )

        assertTrue(QuizVisualAssetStore.fileFor(rich(QuizVisualAssetStore.sha256(first))) != null)
        assertNull(QuizVisualAssetStore.fileFor(rich(QuizVisualAssetStore.sha256(second))))
    }

    private fun writeAsset(hex: String, bytes: ByteArray) {
        val dir = File(File(context.filesDir, "quiz-assets/$assetId"), hex)
        assertTrue(dir.mkdirs())
        File(dir, "media.png").writeBytes(bytes)
    }

    private fun rich(hex: String) = QuizRichVisual(
        assetId = assetId,
        sha256 = "sha256:$hex",
        width = 1,
        height = 1,
    )

    private fun item(rich: QuizRichVisual) = QuizItem(
        questionId = "q_1234567",
        prompt = "如图，答案是多少？",
        options = listOf("1", "2", "3", "4"),
        answerIndex = 0,
        visual = QuizVisual(rich = rich),
    )

    private fun pngBytes(seed: Int = 0): ByteArray {
        val header = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
        )
        return header + ByteArray(32) { seed.toByte() }
    }
}