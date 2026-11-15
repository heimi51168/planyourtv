package app.orchard.tv.parent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import app.orchard.tv.parent.databinding.ActivityQrScannerBinding
import zxingcpp.BarcodeReader
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Full-screen TV pairing scanner with browser-like aim and zoom.
 *
 * CameraX owns the Camera2 preview, continuous AF/AE, pinch and slider zoom,
 * and the torch. zxing-cpp decodes every analysis frame in native code from the
 * YUV Y plane plus the CameraX rotation, so no Kotlin luminance copy or
 * rotation is needed. The previous Java ZXing path decoded at most once every
 * 100 ms, which missed the short sharp frames that the phone browser catches.
 */
class QrScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScannerBinding
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val uiExecutor by lazy { ContextCompat.getMainExecutor(this) }
    private val focusHandler = Handler(Looper.getMainLooper())
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var camera: Camera? = null

    @Volatile
    private var delivered = false

    /**
     * Keeps the centre of the scan frame in focus/exposure while the parent
     * aims, and again after they rotate the phone. A tap or a pinch restarts
     * this timer so the user's own focus point is not overridden immediately.
     */
    private val focusRefresh = object : Runnable {
        override fun run() {
            if (delivered) return
            focusCenter(resetTimer = false)
            focusHandler.postDelayed(this, FOCUS_REFRESH_MS)
        }
    }

    private val scaleDetector by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cam = camera ?: return false
                val state = cam.cameraInfo.zoomState.value ?: return false
                val ratio = (state.zoomRatio * detector.scaleFactor)
                    .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                cam.cameraControl.setZoomRatio(ratio)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                focusCenter()
            }
        })
    }

    private val tapDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                focusAt(e.x, e.y)
                return true
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // The scanner is a black camera surface; keep the system bars dark and
        // use light icons so they do not flash as light strips over the preview.
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.cancelButton.setOnClickListener { finish() }
        setupZoomControl()
        setupPreviewGestures()
        startCamera()
    }

    private fun setupZoomControl() {
        binding.zoomControls.visibility = View.GONE
        binding.zoomBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                camera?.cameraControl?.setLinearZoom(progress / 100f)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                focusCenter()
            }
        })
    }

    private fun setupPreviewGestures() {
        binding.preview.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (!scaleDetector.isInProgress) tapDetector.onTouchEvent(event)
            true
        }
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
            // The provider can finish after the user has already cancelled.
            if (isFinishing || isDestroyed) return@addListener
            val provider = try {
                future.get()
            } catch (e: Exception) {
                failAndFinish()
                return@addListener
            }
            cameraProvider = provider

            // 1080p keeps more pixels on a TV QR across the room. If the device
            // cannot deliver 1080p for both streams CameraX falls back lower.
            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                    ),
                )
                .build()
            val previewUseCase = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.setSurfaceProvider(binding.preview.surfaceProvider) }

            // Loading the native decoder can fail on an unsupported ABI or a
            // damaged install. Report it instead of crashing the app.
            val analyzer = try {
                QrAnalyzer(::deliver)
            } catch (t: Throwable) {
                failAndFinish()
                return@addListener
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.setAnalyzer(analysisExecutor, analyzer) }

            preview = previewUseCase
            analysis = imageAnalysis
            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    previewUseCase,
                    imageAnalysis,
                )
            } catch (e: Exception) {
                failAndFinish()
                return@addListener
            }
            updateTargetRotation()
            bindCameraControls()
            binding.preview.post { focusCenter() }
            focusHandler.postDelayed(focusRefresh, FOCUS_REFRESH_MS)
        }, uiExecutor)
    }

    private fun bindCameraControls() {
        val cam = camera ?: return
        if (cam.cameraInfo.hasFlashUnit()) {
            binding.torchButton.visibility = View.VISIBLE
            binding.torchButton.setOnCheckedChangeListener { _, checked ->
                cam.cameraControl.enableTorch(checked)
            }
        } else {
            binding.torchButton.visibility = View.GONE
        }

        cam.cameraInfo.zoomState.observe(this) { state ->
            val supported = state.maxZoomRatio > state.minZoomRatio
            binding.zoomControls.visibility = if (supported) View.VISIBLE else View.GONE
            if (supported) {
                binding.zoomBar.progress = (state.linearZoom * 100f).roundToInt()
                binding.zoomLabel.text =
                    String.format(Locale.getDefault(), "%.1f×", state.zoomRatio)
            }
        }
    }

    private fun updateTargetRotation() {
        val rotation = binding.preview.display?.rotation ?: return
        preview?.targetRotation = rotation
        analysis?.targetRotation = rotation
    }

    private fun focusCenter(resetTimer: Boolean = true) {
        val view = binding.preview
        if (view.width == 0 || view.height == 0) return
        focusAt(view.width / 2f, view.height / 2f, resetTimer)
    }

    private fun focusAt(x: Float, y: Float, resetTimer: Boolean = true) {
        val cam = camera ?: return
        val view = binding.preview
        if (view.width == 0 || view.height == 0) return
        val point = view.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        if (cam.cameraInfo.isFocusMeteringSupported(action)) {
            cam.cameraControl.startFocusAndMetering(action)
        }
        if (resetTimer && !delivered) {
            focusHandler.removeCallbacks(focusRefresh)
            focusHandler.postDelayed(focusRefresh, FOCUS_REFRESH_MS)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.preview.post {
            updateTargetRotation()
            focusCenter()
        }
    }

    private fun deliver(text: String) {
        if (delivered) return
        delivered = true
        focusHandler.removeCallbacks(focusRefresh)
        uiExecutor.execute {
            if (isFinishing || isDestroyed) return@execute
            setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, text))
            finish()
        }
    }

    private fun failAndFinish() {
        Toast.makeText(this, R.string.scan_failed, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        focusHandler.removeCallbacksAndMessages(null)
        cameraProvider?.unbindAll()
        analysis?.clearAnalyzer()
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT = "app.orchard.tv.parent.SCAN_RESULT"
        private const val FOCUS_REFRESH_MS = 2500L
    }
}

/**
 * Decodes QR frames in native zxing-cpp. CameraX hands us YUV_420_888; the
 * native reader consumes the Y plane and rotation directly, so this runs on
 * every frame instead of copying and rotating it first.
 */
private class QrAnalyzer(
    private val onDecoded: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader = BarcodeReader(
        BarcodeReader.Options(
            formats = setOf(BarcodeReader.Format.QR_CODE),
            tryHarder = true,
            // CameraX already supplies the rotation. The TV QR is dark on
            // white, so the extra inverted pass would only add work.
            tryRotate = false,
            tryInvert = false,
            tryDownscale = false,
            maxNumberOfSymbols = 1,
        ),
    )

    override fun analyze(image: ImageProxy) {
        try {
            val text = reader.read(image)
                .firstOrNull { !it.text.isNullOrBlank() }
                ?.text
            if (text != null) onDecoded(text)
        } catch (_: Exception) {
            // Most frames contain no QR code; keep the camera running.
        } finally {
            image.close()
        }
    }
}
