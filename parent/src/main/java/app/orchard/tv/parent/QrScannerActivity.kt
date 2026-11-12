package app.orchard.tv.parent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import app.orchard.tv.parent.databinding.ActivityQrScannerBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * QR scanner backed by CameraX, with ZXing used only for decoding.
 *
 * The previous scanner was zxing-android-embedded 4.3.0, which drives the
 * deprecated Camera1 API. On some current phones (Android 16, vivo V2309A) that
 * compatibility layer delivers poorly focused frames, so the TV QR was only
 * recognized by luck even after switching the library to continuous focus. The
 * phone's browser reads the same QR immediately, and it uses the Camera2 stack;
 * CameraX uses that stack too, so the camera comes from CameraX and only the
 * decoder stays ZXing.
 *
 * ImageAnalysis frames arrive in sensor orientation. The luminance plane is
 * rotated to match the display before decoding, and decoding is throttled so a
 * frame that fails does not keep a CPU core busy while the parent re-aims.
 */
class QrScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScannerBinding
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val uiExecutor by lazy { ContextCompat.getMainExecutor(this) }
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.cancelButton.setOnClickListener { finish() }
        startCamera()
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            failAndFinish()
            return
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = try {
                future.get()
            } catch (e: Exception) {
                failAndFinish()
                return@addListener
            }
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.preview.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(Size(1280, 720))
                .build()
                .also { it.setAnalyzer(analysisExecutor, QrAnalyzer(::deliver)) }
            analysis = imageAnalysis
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis,
                )
            } catch (e: Exception) {
                failAndFinish()
            }
        }, uiExecutor)
    }

    private fun deliver(text: String) {
        if (delivered) return
        delivered = true
        uiExecutor.execute {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, text))
            finish()
        }
    }

    private fun failAndFinish() {
        Toast.makeText(this, R.string.scan_failed, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        analysis?.clearAnalyzer()
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT = "app.orchard.tv.parent.SCAN_RESULT"
    }
}

/**
 * Converts CameraX luminance frames to a ZXing bitmap and decodes QR codes.
 * Runs on a single analysis thread; [MultiFormatReader] is not thread-safe.
 */
private class QrAnalyzer(
    private val onDecoded: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8",
                DecodeHintType.ALSO_INVERTED to true,
            ),
        )
    }
    private var lastAttempt = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAttempt < DECODE_INTERVAL_MS) return
            lastAttempt = now

            val frame = Luminance.from(image) ?: return
            val source = PlanarYUVLuminanceSource(
                frame.data,
                frame.width,
                frame.height,
                0,
                0,
                frame.width,
                frame.height,
                false,
            )
            val result = try {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            } catch (e: NotFoundException) {
                null
            } catch (e: Exception) {
                null
            } finally {
                reader.reset()
            }
            if (result != null) onDecoded(result.text)
        } finally {
            image.close()
        }
    }

    /** Luminance plane copied out of the YUV frame and rotated upright. */
    private class Luminance(val data: ByteArray, val width: Int, val height: Int) {
        companion object {
            fun from(image: ImageProxy): Luminance? {
                val plane = image.planes.firstOrNull() ?: return null
                val buffer = plane.buffer
                val width = image.width
                val height = image.height
                if (width <= 0 || height <= 0) return null
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride
                if (rowStride <= 0 || pixelStride <= 0) return null

                val data = ByteArray(width * height)
                if (pixelStride == 1 && rowStride >= width) {
                    for (row in 0 until height) {
                        val offset = row * rowStride
                        if (offset + width > buffer.limit()) return null
                        buffer.position(offset)
                        buffer.get(data, row * width, width)
                    }
                } else {
                    for (row in 0 until height) {
                        val rowStart = row * rowStride
                        for (col in 0 until width) {
                            val index = rowStart + col * pixelStride
                            if (index >= buffer.limit()) return null
                            data[row * width + col] = buffer.get(index)
                        }
                    }
                }
                return rotate(data, width, height, image.imageInfo.rotationDegrees)
            }

            private fun rotate(data: ByteArray, width: Int, height: Int, degrees: Int): Luminance {
                return when (((degrees % 360) + 360) % 360) {
                    90 -> {
                        val out = ByteArray(data.size)
                        val outWidth = height
                        for (y in 0 until height) {
                            val srcRow = y * width
                            val outX = height - 1 - y
                            for (x in 0 until width) {
                                out[x * outWidth + outX] = data[srcRow + x]
                            }
                        }
                        Luminance(out, height, width)
                    }
                    180 -> {
                        val out = ByteArray(data.size)
                        for (y in 0 until height) {
                            val srcRow = y * width
                            val outRow = (height - 1 - y) * width
                            for (x in 0 until width) {
                                out[outRow + (width - 1 - x)] = data[srcRow + x]
                            }
                        }
                        Luminance(out, width, height)
                    }
                    270 -> {
                        val out = ByteArray(data.size)
                        for (y in 0 until height) {
                            val srcRow = y * width
                            for (x in 0 until width) {
                                out[(width - 1 - x) * height + y] = data[srcRow + x]
                            }
                        }
                        Luminance(out, height, width)
                    }
                    else -> Luminance(data, width, height)
                }
            }
        }
    }

    private companion object {
        /** At most one decode every 100 ms; the camera keeps focusing in between. */
        const val DECODE_INTERVAL_MS = 100L
    }
}
