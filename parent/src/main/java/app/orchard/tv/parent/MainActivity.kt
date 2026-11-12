package app.orchard.tv.parent

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.orchard.tv.parent.databinding.ActivityMainBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * A browser shell around the Parent Center page served by the TV.
 *
 * It deliberately contains no Parent Center UI of its own: the page is the same
 * one a phone browser would open, and the TV server remains the only source of
 * truth. The activity adds the four things a browser gives a page for free:
 * QR pairing, remembered address, file upload, and downloads/clipboard/external
 * links that a bare WebView cannot complete.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: ParentPrefs
    private var pairedUrl: String? = null
    private var pageError = false
    private var shellBridgeJs = ""
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingSave: PendingSave? = null

    private val openSingleFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            deliverFiles(uri?.let { listOf(it) })
        }

    private val openMultipleFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            deliverFiles(uris.ifEmpty { null })
        }

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val save = pendingSave
            pendingSave = null
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null && save != null) {
                writeDownload(uri, save)
            }
        }

    private val requestCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            when {
                granted -> launchScanner()
                shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ->
                    toast(R.string.camera_permission_hint)
                else -> showCameraBlockedDialog()
            }
        }

    private val scanQr = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) connect(contents)
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (pairedUrl != null && !pageError && binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = ParentPrefs(this)
        shellBridgeJs = runCatching {
            assets.open("shell_bridge.js").bufferedReader().use { it.readText() }
        }.getOrDefault("")

        setupToolbar()
        setupWebView()
        setupPairing()
        onBackPressedDispatcher.addCallback(this, backCallback)

        val saved = prefs.quizUrl?.let { QuizUrl.normalize(it) }
        if (saved != null) openPaired(saved) else showPairing()
    }

    // ---------------------------------------------------------------- pairing

    private fun setupPairing() {
        binding.scanButton.setOnClickListener { beginScan() }
        binding.manualToggle.setOnClickListener { showManualEntry() }
        binding.connectButton.setOnClickListener { connectFromInput() }
        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                connectFromInput()
                true
            } else {
                false
            }
        }
        binding.retryButton.setOnClickListener { loadPaired() }
        binding.errorRepairButton.setOnClickListener { confirmRepair() }
    }

    private fun connectFromInput() {
        connect(binding.urlInput.text?.toString().orEmpty())
    }

    private fun connect(raw: String) {
        val url = QuizUrl.normalize(raw)
        if (url == null) {
            if (binding.pairingView.visibility == View.VISIBLE) {
                showManualEntry()
                showPairError(R.string.pair_invalid_url)
            } else {
                toast(R.string.pair_invalid_url)
            }
            return
        }
        hideKeyboard()
        openPaired(url)
    }

    private fun beginScan() {
        binding.pairError.visibility = View.GONE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            launchScanner()
        } else {
            requestCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.scan_prompt))
            setBeepEnabled(false)
            setOrientationLocked(false)
            setBarcodeImageEnabled(false)
        }
        try {
            scanQr.launch(options)
        } catch (e: Exception) {
            toast(R.string.scan_failed)
        }
    }

    private fun showPairError(message: Int) {
        binding.pairError.setText(message)
        binding.pairError.visibility = View.VISIBLE
    }

    private fun showManualEntry() {
        if (binding.manualEntry.visibility == View.VISIBLE) return
        binding.manualToggle.visibility = View.GONE
        binding.manualEntry.visibility = View.VISIBLE
        binding.urlInput.requestFocus()
        binding.urlInput.post {
            getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(binding.urlInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun openPaired(url: String) {
        pairedUrl = url
        prefs.quizUrl = url
        binding.pairError.visibility = View.GONE
        binding.pairingView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        binding.toolbar.subtitle = runCatching { Uri.parse(url).authority }.getOrNull()
        binding.webView.clearHistory()
        updateMenu()
        loadPaired()
    }

    private fun loadPaired() {
        val url = pairedUrl ?: return
        pageError = false
        binding.errorView.visibility = View.GONE
        binding.loadingView.visibility = View.VISIBLE
        binding.webView.visibility = View.VISIBLE
        binding.webView.loadUrl(url)
    }

    private fun showPairing() {
        pairedUrl = null
        pageError = false
        prefs.clear()
        binding.webView.stopLoading()
        binding.webView.loadUrl("about:blank")
        binding.webView.clearHistory()
        binding.webView.visibility = View.GONE
        binding.loadingView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        binding.pairingView.visibility = View.VISIBLE
        binding.manualEntry.visibility = View.GONE
        binding.manualToggle.visibility = View.VISIBLE
        binding.urlInput.setText("")
        binding.pairError.visibility = View.GONE
        binding.toolbar.subtitle = null
        updateMenu()
    }

    private fun confirmRepair() {
        AlertDialog.Builder(this)
            .setTitle(R.string.repair_title)
            .setMessage(R.string.repair_message)
            .setPositiveButton(R.string.repair_confirm) { _, _ ->
                showPairing()
                beginScan()
            }
            .setNegativeButton(R.string.repair_cancel, null)
            .show()
    }

    private fun showCameraBlockedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.camera_blocked_title)
            .setMessage(R.string.camera_blocked_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null),
                    ),
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- toolbar

    private fun setupToolbar() {
        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> {
                    if (pairedUrl != null) loadPaired()
                    true
                }
                R.id.action_open_browser -> {
                    pairedUrl?.let { openExternal(Uri.parse(it)) }
                    true
                }
                R.id.action_repair -> {
                    confirmRepair()
                    true
                }
                else -> false
            }
        }
        updateMenu()
    }

    private fun updateMenu() {
        val paired = pairedUrl != null
        val menu = binding.toolbar.menu
        menu.findItem(R.id.action_refresh).isVisible = paired
        menu.findItem(R.id.action_open_browser).isVisible = paired
        menu.findItem(R.id.action_repair).isVisible = paired
    }

    // ---------------------------------------------------------------- web view

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            // The page is local and trusted; file:// access is not needed.
            allowFileAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            // Needed for the import picker's content:// URI, nothing else.
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = false
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The page has its own light palette; never let the system darken it.
            binding.webView.isForceDarkAllowed = false
        }
        binding.webView.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_raised))
        binding.webView.webViewClient = ShellWebViewClient()
        binding.webView.webChromeClient = ShellWebChromeClient()
        binding.webView.addJavascriptInterface(ShellBridge(), BRIDGE_NAME)
        binding.webView.setDownloadListener { url, _, _, _, _ ->
            if (url.startsWith("http://") || url.startsWith("https://")) {
                // A plain download link is better handed to the system browser;
                // the page's own exports are blob: URLs handled by the bridge.
                openExternal(Uri.parse(url))
            } else {
                toast(R.string.download_unsupported)
            }
        }
    }

    private inner class ShellWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
        ): Boolean {
            if (request?.isForMainFrame != true) return false
            return handleNavigation(request.url)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (pairedUrl == null) return
            pageError = false
            binding.errorView.visibility = View.GONE
            binding.loadingView.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (pairedUrl == null) return
            binding.loadingView.visibility = View.GONE
            if (!pageError) {
                binding.errorView.visibility = View.GONE
                binding.webView.visibility = View.VISIBLE
            }
            if (shellBridgeJs.isNotEmpty()) {
                view?.evaluateJavascript(shellBridgeJs, null)
            }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?,
        ) {
            if (request?.isForMainFrame != true || pairedUrl == null) return
            val reason = error?.description?.toString()?.takeIf { it.isNotBlank() }
                ?: getString(R.string.error_unknown)
            showError(getString(R.string.error_message, reason))
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?,
        ) {
            if (request?.isForMainFrame != true || pairedUrl == null) return
            val status = errorResponse?.statusCode ?: return
            if (status < 400) return
            val detail = if (status == 410) {
                getString(R.string.error_local_only)
            } else {
                getString(R.string.error_http, status)
            }
            showError(getString(R.string.error_message, detail))
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?,
        ) {
            handler?.cancel()
            if (pairedUrl != null) {
                showError(getString(R.string.error_message, getString(R.string.error_tls)))
            }
        }
    }

    private inner class ShellWebChromeClient : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView?,
            callback: ValueCallback<Array<Uri>>?,
            params: FileChooserParams?,
        ): Boolean {
            if (callback == null) return false
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = callback

            val multiple = params?.mode == FileChooserParams.MODE_OPEN_MULTIPLE
            val types = MimeChooser.types(params?.acceptTypes?.toList())
            return try {
                if (multiple) openMultipleFiles.launch(types) else openSingleFile.launch(types)
                true
            } catch (e: Exception) {
                fileChooserCallback = null
                callback.onReceiveValue(null)
                false
            }
        }

        override fun onJsConfirm(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?,
        ): Boolean {
            if (result == null) return false
            AlertDialog.Builder(this@MainActivity)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }

        override fun onJsAlert(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?,
        ): Boolean {
            if (result == null) return false
            AlertDialog.Builder(this@MainActivity)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setOnCancelListener { result.confirm() }
                .show()
            return true
        }
    }

    private fun handleNavigation(uri: Uri?): Boolean {
        if (uri == null) return false
        if (uri.scheme == "about") return false
        val paired = pairedUrl ?: return true
        if (QuizUrl.sameOrigin(uri.toString(), paired)) return false
        if (uri.scheme == "http" || uri.scheme == "https") {
            openExternal(uri)
        }
        // Non-HTTP schemes are blocked rather than handed to the system.
        return true
    }

    private fun showError(message: String) {
        pageError = true
        binding.loadingView.visibility = View.GONE
        binding.webView.visibility = View.GONE
        binding.errorMessage.text = message
        binding.errorView.visibility = View.VISIBLE
    }

    private fun deliverFiles(uris: List<Uri>?) {
        val callback = fileChooserCallback ?: return
        fileChooserCallback = null
        callback.onReceiveValue(if (uris.isNullOrEmpty()) null else uris.toTypedArray())
    }

    private fun isTrustedPage(): Boolean {
        val paired = pairedUrl ?: return false
        val current = binding.webView.url ?: return false
        return QuizUrl.sameOrigin(current, paired)
    }

    // ---------------------------------------------------------------- bridge

    /**
     * Called from shell_bridge.js. All methods hop to the UI thread; the
     * WebView invokes them on a private bridge thread.
     */
    inner class ShellBridge {
        @JavascriptInterface
        fun saveDownload(filename: String?, mimeType: String?, base64: String?) {
            runOnUiThread {
                if (!isTrustedPage() || base64 == null) return@runOnUiThread
                if (base64.length > MAX_BASE64_CHARS) {
                    toast(R.string.file_too_large)
                    return@runOnUiThread
                }
                startSave(sanitizeFilename(filename), sanitizeMime(mimeType), base64)
            }
        }

        @JavascriptInterface
        fun copyText(text: String?) {
            val value = text?.take(MAX_CLIPBOARD_CHARS) ?: return
            runOnUiThread {
                if (!isTrustedPage()) return@runOnUiThread
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), value))
            }
        }

        @JavascriptInterface
        fun openExternal(url: String?) {
            val value = url ?: return
            runOnUiThread {
                if (!isTrustedPage()) return@runOnUiThread
                val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return@runOnUiThread
                openExternal(uri)
            }
        }
    }

    private fun startSave(filename: String, mimeType: String, base64: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_TITLE, filename)
        pendingSave = PendingSave(base64)
        try {
            createDocument.launch(intent)
        } catch (e: ActivityNotFoundException) {
            pendingSave = null
            toast(R.string.save_failed)
        }
    }

    private fun writeDownload(uri: Uri, save: PendingSave) {
        Thread {
            val saved = runCatching {
                val bytes = Base64.decode(save.base64, Base64.DEFAULT)
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("no output stream")
            }.isSuccess
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    toast(if (saved) R.string.saved else R.string.save_failed)
                }
            }
        }.start()
    }

    private fun sanitizeFilename(name: String?): String {
        val clean = name.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { it.code in 0x20..0x7E || it.code > 0x7F }
            .trim()
        return clean.ifBlank { "download" }.take(MAX_FILENAME_CHARS)
    }

    private fun sanitizeMime(mime: String?): String {
        val value = mime?.trim()?.lowercase().orEmpty()
        return if (value.matches(MIME_PATTERN)) value else "application/octet-stream"
    }

    // ---------------------------------------------------------------- intents

    private fun openExternal(uri: Uri) {
        if (uri.scheme != "http" && uri.scheme != "https") return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.external_unavailable)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        imm.hideSoftInputFromWindow(binding.urlInput.windowToken, 0)
        binding.urlInput.clearFocus()
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onPause() {
        binding.webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        pendingSave = null
        (binding.webView.parent as? ViewGroup)?.removeView(binding.webView)
        binding.webView.apply {
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }

    private data class PendingSave(val base64: String)

    private companion object {
        const val BRIDGE_NAME = "ParentShell"
        const val MAX_BASE64_CHARS = 16 * 1024 * 1024
        const val MAX_CLIPBOARD_CHARS = 200_000
        const val MAX_FILENAME_CHARS = 120
        val MIME_PATTERN = Regex("[a-z0-9.+-]+/[a-z0-9.+-]+")
    }
}
