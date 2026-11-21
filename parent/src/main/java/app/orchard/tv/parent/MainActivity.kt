package app.orchard.tv.parent

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.text.format.Formatter
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import app.orchard.tv.parent.databinding.ActivityMainBinding
import app.orchard.tv.parent.databinding.DialogParentUpdateBinding
import java.io.File

/**
 * A browser shell around the Parent Center page served by the TV.
 *
 * It deliberately contains no Parent Center UI of its own: the page is the same
 * one a phone browser would open, and the TV server remains the only source of
 * truth. The activity adds what a browser gives a page for free: QR pairing,
 * remembered TVs, file upload, downloads and external links. The device list is
 * phone-local so one phone can switch between several TVs without an account.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: ParentPrefs
    private lateinit var updater: ParentUpdater
    private lateinit var installer: ParentInstaller
    private var devices = DeviceSnapshot(emptyList(), null)
    private var pairedUrl: String? = null
    private var pageError = false
    private var shellBridgeJs = ""
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingSave: PendingSave? = null

    private var deviceListDialog: AlertDialog? = null
    private var deviceListContainer: LinearLayout? = null
    private var deviceListScroll: ScrollView? = null
    private var versionDialog: AlertDialog? = null
    private var versionDialogBinding: DialogParentUpdateBinding? = null
    private var versionCheckBusy = false
    private var versionCheckQueued = false
    private var downloadDialog: AlertDialog? = null
    private var pendingRelease: ParentUpdater.Release? = null
    private var waitingForPermission: ParentUpdater.Release? = null

    /**
     * One dialog owns the whole update conversation: the installed version is
     * always visible, and only the latest-version row and the buttons change.
     */
    private sealed interface VersionState {
        object Checking : VersionState
        data class Available(val release: ParentUpdater.Release) : VersionState
        data class UpToDate(val latestName: String?, val latestCode: Int) : VersionState
        object Failed : VersionState
    }

    private val openSingleFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            deliverFiles(uri?.let { listOf(it) })
        }

    private val openMultipleFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            deliverFiles(uris.ifEmpty { null })
        }

    // Media uploads open the system gallery (photo picker) directly; the
    // document picker stays for pages that ask for non-media files.
    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            deliverFiles(uri?.let { listOf(it) })
        }

    private val pickMultipleMedia =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
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

    private val scanQr =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val contents = result.data?.getStringExtra(QrScannerActivity.EXTRA_RESULT)
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
        devices = prefs.load().normalized()
        shellBridgeJs = runCatching {
            assets.open("shell_bridge.js").bufferedReader().use { it.readText() }
        }.getOrDefault("")

        setupTopMenu()
        setupWebView()
        setupPairing()
        setupUpdates()
        onBackPressedDispatcher.addCallback(this, backCallback)

        val current = devices.current()
        if (current != null) {
            openDevice(current.copy(lastUsedAt = System.currentTimeMillis()))
        } else {
            showPairing()
        }
        checkForUpdatesOnLaunch()
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
        binding.errorRepairButton.setOnClickListener { showDeviceList() }
    }

    private fun connectFromInput() {
        connect(binding.urlInput.text?.toString().orEmpty())
    }

    private fun connect(raw: String) {
        val url = QuizUrl.normalize(raw)
        val key = url?.let { QuizUrl.deviceKey(it) }
        if (url == null || key == null) {
            if (binding.pairingView.visibility == View.VISIBLE) {
                showManualEntry()
                showPairError(R.string.pair_invalid_url)
            } else {
                toast(R.string.pair_invalid_url)
            }
            return
        }
        val existing = devices.devices.firstOrNull { it.key == key }
        val sameOrigin = devices.devices.firstOrNull { QuizUrl.sameOrigin(it.url, url) }
        val name = existing?.name ?: sameOrigin?.name ?: nextDefaultName()
        hideKeyboard()
        openDevice(PairedDevice(key, name, url, System.currentTimeMillis()))
    }

    private fun nextDefaultName(): String {
        var index = devices.devices.size + 1
        var name: String
        do {
            name = getString(R.string.device_default_name, index)
            index++
        } while (devices.devices.any { it.name == name })
        return name
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
        try {
            scanQr.launch(Intent(this, QrScannerActivity::class.java))
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

    private fun openDevice(device: PairedDevice) {
        devices = DeviceSnapshot(
            devices = ParentDevices.upsert(devices.devices, device),
            currentKey = device.key,
        ).normalized()
        prefs.save(devices)
        pairedUrl = device.url
        binding.pairError.visibility = View.GONE
        binding.pairingView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        deviceListDialog?.dismiss()
        binding.webView.clearHistory()
        updateMenu()
        loadPaired()
    }

    private fun loadPaired() {
        val url = pairedUrl ?: return
        preparePageLoad()
        binding.webView.loadUrl(url)
    }

    /**
     * Reloads the document that is already showing. Loading the saved address
     * again would be a fragment-only navigation once the page has consumed its
     * #parent link, and WebView resolves that without replacing the document,
     * so the SPA would keep its old page and assets after a TV web update.
     */
    private fun reloadPaired() {
        if (pairedUrl == null) return
        preparePageLoad()
        binding.webView.reload()
    }

    private fun preparePageLoad() {
        pageError = false
        binding.errorView.visibility = View.GONE
        binding.loadingView.visibility = View.VISIBLE
        binding.webView.visibility = View.VISIBLE
    }

    private fun showPairing() {
        if (devices.devices.isNotEmpty()) {
            showDeviceList()
            return
        }
        pairedUrl = null
        pageError = false
        devices = devices.copy(currentKey = null)
        prefs.save(devices)
        deviceListDialog?.dismiss()
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
        updateMenu()
    }

    // ---------------------------------------------------------------- devices

    private fun showDeviceList() {
        if (devices.devices.isEmpty()) {
            showPairing()
            return
        }
        if (deviceListDialog != null) return

        val view = layoutInflater.inflate(R.layout.dialog_devices, null)
        val container = view.findViewById<LinearLayout>(R.id.deviceList)
        deviceListContainer = container
        deviceListScroll = view.findViewById(R.id.deviceListScroll)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.devices_title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        deviceListDialog = dialog
        dialog.setOnDismissListener {
            if (deviceListDialog === dialog) {
                deviceListDialog = null
                deviceListContainer = null
                deviceListScroll = null
            }
        }
        view.findViewById<View>(R.id.addDeviceButton).setOnClickListener {
            dialog.dismiss()
            beginScan()
        }
        view.findViewById<View>(R.id.manualDeviceButton).setOnClickListener {
            dialog.dismiss()
            showManualDeviceDialog()
        }
        dialog.show()
        populateDeviceList()
    }

    private fun populateDeviceList() {
        val container = deviceListContainer ?: return
        container.removeAllViews()
        ParentDevices.sorted(devices.devices, devices.currentKey).forEach { device ->
            val row = layoutInflater.inflate(R.layout.item_device, container, false)
            row.findViewById<TextView>(R.id.deviceName).text = device.name
            row.findViewById<TextView>(R.id.deviceAddress).text =
                runCatching { Uri.parse(device.url).authority }.getOrNull() ?: device.url
            val isCurrent = device.key == devices.currentKey
            row.setBackgroundResource(
                if (isCurrent) R.drawable.bg_device_item_current else R.drawable.bg_device_item,
            )
            row.findViewById<View>(R.id.deviceCurrent).visibility =
                if (isCurrent) View.VISIBLE else View.GONE
            row.setOnClickListener {
                if (device.key == devices.currentKey) {
                    deviceListDialog?.dismiss()
                } else {
                    openDevice(device.copy(lastUsedAt = System.currentTimeMillis()))
                }
            }
            row.findViewById<View>(R.id.deviceMore).setOnClickListener {
                showDeviceOptions(device)
            }
            container.addView(row)
        }
        fitDeviceListHeight()
    }

    /**
     * The dialog wraps a short list instead of reserving a fixed panel, but a
     * long list still stops at a comfortable height and scrolls.
     */
    private fun fitDeviceListHeight() {
        val scroll = deviceListScroll ?: return
        val content = deviceListContainer ?: return
        scroll.post {
            val height = content.height.coerceAtMost(dp(280))
            if (height > 0 && scroll.layoutParams.height != height) {
                scroll.layoutParams = scroll.layoutParams.apply { this.height = height }
            }
        }
    }

    private fun showDeviceOptions(device: PairedDevice) {
        AlertDialog.Builder(this)
            .setTitle(device.name)
            .setItems(
                arrayOf(
                    getString(R.string.devices_rename),
                    getString(R.string.devices_delete),
                ),
            ) { _, which ->
                if (which == 0) showRenameDialog(device) else confirmDeleteDevice(device)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRenameDialog(device: PairedDevice) {
        val input = EditText(this).apply {
            setText(device.name)
            setSelection(text.length)
            hint = getString(R.string.device_rename_hint)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(ParentDevices.MAX_NAME_LENGTH))
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.device_rename_title)
            .setView(container)
            .setPositiveButton(R.string.device_rename_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = ParentDevices.normalizeName(input.text?.toString())
                if (name == null) {
                    input.error = getString(R.string.device_name_required)
                } else {
                    val updated = devices.devices.map {
                        if (it.key == device.key) it.copy(name = name) else it
                    }
                    devices = devices.copy(devices = updated).normalized()
                    prefs.save(devices)
                    populateDeviceList()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun confirmDeleteDevice(device: PairedDevice) {
        AlertDialog.Builder(this)
            .setTitle(R.string.device_delete_title)
            .setMessage(getString(R.string.device_delete_message, device.name))
            .setPositiveButton(R.string.device_delete_confirm) { _, _ -> deleteDevice(device) }
            .setNegativeButton(R.string.device_delete_cancel, null)
            .show()
    }

    private fun deleteDevice(device: PairedDevice) {
        val wasCurrent = device.key == devices.currentKey
        val remaining = devices.devices.filterNot { it.key == device.key }
        devices = DeviceSnapshot(remaining, devices.currentKey).normalized()
        prefs.save(devices)
        if (remaining.isEmpty()) {
            deviceListDialog?.dismiss()
            showPairing()
            return
        }
        if (wasCurrent) {
            devices.current()?.let { next ->
                openDevice(next.copy(lastUsedAt = System.currentTimeMillis()))
            } ?: showPairing()
        } else {
            populateDeviceList()
        }
    }

    private fun showManualDeviceDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.pair_url_hint)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            typeface = Typeface.MONOSPACE
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.devices_manual_title)
            .setView(container)
            .setPositiveButton(R.string.devices_manual_connect, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val raw = input.text?.toString().orEmpty()
                if (QuizUrl.normalize(raw) == null) {
                    input.error = getString(R.string.pair_invalid_url)
                } else {
                    dialog.dismiss()
                    connect(raw)
                }
            }
        }
        dialog.show()
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

    private fun setupTopMenu() {
        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_devices -> {
                    showDeviceList()
                    true
                }
                R.id.action_check_update -> {
                    manualCheckForUpdate()
                    true
                }
                R.id.action_copy_link -> {
                    copyCurrentLink()
                    true
                }
                R.id.action_refresh -> {
                    reloadPaired()
                    true
                }
                R.id.action_open_browser -> {
                    pairedUrl?.let { openExternal(Uri.parse(it)) }
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
        menu.findItem(R.id.action_devices).isVisible = devices.devices.isNotEmpty()
        menu.findItem(R.id.action_copy_link).isVisible = paired
        menu.findItem(R.id.action_refresh).isVisible = paired
        menu.findItem(R.id.action_open_browser).isVisible = paired
        menu.findItem(R.id.action_check_update).isVisible = true
    }

    /**
     * Copies this TV's saved Parent Center address. The address is the same one the
     * TV's QR code carries, so it can be opened on another device or kept for later.
     */
    private fun copyCurrentLink() {
        val url = pairedUrl ?: return
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        val label = devices.current()?.name ?: getString(R.string.app_name)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, url))
        // Android 13+ shows the system's own clipboard confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            toast(R.string.link_copied)
        }
    }

    // ---------------------------------------------------------------- updates

    private fun setupUpdates() {
        updater = ParentUpdater(this)
        installer = ParentInstaller(this)
        installer.onResult = { status, _ ->
            if (!isFinishing && !isDestroyed) {
                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> {
                        pendingRelease = null
                        hideUpdateBanner()
                        toast(R.string.update_installed)
                    }
                    PackageInstaller.STATUS_FAILURE_ABORTED -> Unit
                    else -> showInstallFailed()
                }
            }
        }
        installer.register()
        binding.updateBanner.setOnClickListener { openVersionDialog() }
        binding.updateBannerText.setOnClickListener { openVersionDialog() }
        binding.updateBannerView.setOnClickListener { openVersionDialog() }
        binding.updateBannerDismiss.setOnClickListener { dismissUpdateBanner() }
    }

    private fun checkForUpdatesOnLaunch() {
        updater.cachedRelease()?.let { release ->
            pendingRelease = release
            if (!updater.isDismissed(release)) showUpdateBanner(release)
        }
        updater.check(force = false) { result ->
            if (isFinishing || isDestroyed) return@check
            handleCheckResult(result)
            runQueuedVersionCheck()
        }
    }

    private fun manualCheckForUpdate() {
        if (versionDialog != null) return
        showVersionDialog(VersionState.Checking)
        startVersionCheck()
    }

    /**
     * A banner tap shows the cached release without touching the network. The
     * menu entry always opens the dialog first and checks in the background, so
     * the installed version is visible before any request completes.
     */
    private fun openVersionDialog() {
        val release = pendingRelease ?: return
        showVersionDialog(VersionState.Available(release))
    }

    private fun startVersionCheck() {
        if (versionCheckBusy) return
        versionCheckBusy = true
        renderVersionState(VersionState.Checking)
        val started = updater.check(force = true) { result ->
            versionCheckBusy = false
            if (isFinishing || isDestroyed) return@check
            handleCheckResult(result)
            runQueuedVersionCheck()
        }
        if (!started) {
            // The once-a-day launch check is still in flight. Retry as soon as
            // it reports back; the dialog stays on "checking" meanwhile.
            versionCheckQueued = true
        }
    }

    private fun runQueuedVersionCheck() {
        if (!versionCheckQueued) return
        versionCheckQueued = false
        versionCheckBusy = false
        startVersionCheck()
    }

    private fun handleCheckResult(result: ParentUpdater.CheckResult) {
        when (result) {
            is ParentUpdater.CheckResult.Available -> {
                pendingRelease = result.release
                if (!updater.isDismissed(result.release)) {
                    showUpdateBanner(result.release)
                }
                renderVersionState(VersionState.Available(result.release))
            }
            is ParentUpdater.CheckResult.UpToDate -> {
                pendingRelease = null
                hideUpdateBanner()
                renderVersionState(
                    VersionState.UpToDate(result.latestName, result.latestCode),
                )
            }
            ParentUpdater.CheckResult.Failed -> {
                renderVersionState(VersionState.Failed)
            }
        }
    }

    private fun showVersionDialog(initial: VersionState) {
        if (versionDialog != null) {
            renderVersionState(initial)
            return
        }
        val content = DialogParentUpdateBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.update_dialog_title)
            .setView(content.root)
            .setPositiveButton(R.string.update_check_again, null)
            .setNegativeButton(R.string.update_close, null)
            .create()
        versionDialog = dialog
        versionDialogBinding = content
        dialog.setOnDismissListener {
            if (versionDialog === dialog) {
                versionDialog = null
                versionDialogBinding = null
            }
        }
        dialog.show()
        // Both secondary actions ("稍后"/"关闭") only hide the dialog. The
        // primary button is rebound for each state below.
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            dialog.dismiss()
        }
        renderVersionState(initial)
    }

    private fun renderVersionState(state: VersionState) {
        val content = versionDialogBinding ?: return
        val dialog = versionDialog ?: return
        content.updateCurrentVersion.text =
            versionText(BuildConfig.VERSION_NAME)
        val muted = ContextCompat.getColor(this, R.color.muted)
        val ink = ContextCompat.getColor(this, R.color.ink)
        val brand = ContextCompat.getColor(this, R.color.brand)
        val danger = ContextCompat.getColor(this, R.color.danger)
        val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        content.updateStatusHint.visibility = View.GONE
        negative.setText(R.string.update_close)
        positive.isVisible = true
        positive.setOnClickListener(null)
        when (state) {
            VersionState.Checking -> {
                content.updateLatestVersion.setText(R.string.update_checking)
                content.updateLatestVersion.setTextColor(muted)
                positive.isVisible = false
            }
            is VersionState.Available -> {
                val release = state.release
                content.updateLatestVersion.text =
                    versionText(release.versionName)
                content.updateLatestVersion.setTextColor(brand)
                val size = if (release.sizeBytes > 0L) {
                    Formatter.formatShortFileSize(this, release.sizeBytes)
                } else {
                    getString(R.string.update_size_unknown)
                }
                content.updateStatusHint.text = getString(R.string.update_available_hint, size)
                content.updateStatusHint.setTextColor(muted)
                content.updateStatusHint.visibility = View.VISIBLE
                negative.setText(R.string.update_later)
                positive.text = getString(
                    R.string.update_install_version,
                    versionText(release.versionName),
                )
                positive.setOnClickListener {
                    dialog.dismiss()
                    startUpdate(release)
                }
            }
            is VersionState.UpToDate -> {
                val latestName = state.latestName
                when {
                    latestName.isNullOrBlank() -> {
                        content.updateLatestVersion.setText(R.string.update_unknown_version)
                        content.updateLatestVersion.setTextColor(muted)
                    }
                    // A dev build can be newer than the published metadata. In
                    // that case the newest available build is the installed one,
                    // so repeating an older number under "Latest" would read
                    // like a downgrade.
                    state.latestCode in 1 until BuildConfig.VERSION_CODE -> {
                        content.updateLatestVersion.text =
                            versionText(BuildConfig.VERSION_NAME)
                        content.updateLatestVersion.setTextColor(ink)
                    }
                    else -> {
                        content.updateLatestVersion.text =
                            versionText(latestName)
                        content.updateLatestVersion.setTextColor(ink)
                    }
                }
                content.updateStatusHint.setText(R.string.update_up_to_date_title)
                content.updateStatusHint.setTextColor(brand)
                content.updateStatusHint.visibility = View.VISIBLE
                positive.setText(R.string.update_check_again)
                positive.setOnClickListener { startVersionCheck() }
            }
            VersionState.Failed -> {
                content.updateLatestVersion.setText(R.string.update_unknown_version)
                content.updateLatestVersion.setTextColor(muted)
                content.updateStatusHint.setText(R.string.update_failed_message)
                content.updateStatusHint.setTextColor(danger)
                content.updateStatusHint.visibility = View.VISIBLE
                positive.setText(R.string.update_retry)
                positive.setOnClickListener { startVersionCheck() }
            }
        }
    }

    private fun versionText(name: String): String {
        val clean = name.trim().removePrefix("v").removePrefix("V")
        if (clean.isEmpty()) return getString(R.string.update_unknown_version)
        return getString(R.string.update_version_name, clean)
    }

    private fun showUpdateBanner(release: ParentUpdater.Release) {
        pendingRelease = release
        binding.updateBannerText.text = getString(R.string.update_banner, release.versionName)
        binding.updateBanner.visibility = View.VISIBLE
    }

    private fun hideUpdateBanner() {
        binding.updateBanner.visibility = View.GONE
    }

    private fun dismissUpdateBanner() {
        pendingRelease?.let { updater.dismiss(it) }
        hideUpdateBanner()
    }

    private fun startUpdate(release: ParentUpdater.Release) {
        if (!installer.canInstall()) {
            requestInstallPermission(release)
            return
        }
        waitingForPermission = null
        showDownloadDialog(release)
    }

    private fun requestInstallPermission(release: ParentUpdater.Release) {
        waitingForPermission = release
        AlertDialog.Builder(this)
            .setTitle(R.string.update_permission_title)
            .setMessage(R.string.update_permission_message)
            .setPositiveButton(R.string.open_settings) { _, _ -> openInstallPermissionSettings() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> waitingForPermission = null }
            .setOnCancelListener { waitingForPermission = null }
            .show()
    }

    private fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
        } catch (e: ActivityNotFoundException) {
            toast(R.string.update_install_failed_message)
        }
    }

    private fun showDownloadDialog(release: ParentUpdater.Release) {
        if (downloadDialog != null) return
        val view = layoutInflater.inflate(R.layout.dialog_update, null)
        val progress = view.findViewById<ProgressBar>(R.id.updateProgress)
        val status = view.findViewById<TextView>(R.id.updateStatus)
        progress.isIndeterminate = true
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.update_downloading_title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel) { _, _ -> updater.cancelDownload() }
            .create()
        dialog.setOnCancelListener { updater.cancelDownload() }
        dialog.setOnDismissListener {
            if (downloadDialog === dialog) downloadDialog = null
        }
        downloadDialog = dialog
        dialog.show()
        val started = updater.download(
            release = release,
            onProgress = { percent ->
                if (downloadDialog === dialog) {
                    progress.isIndeterminate = false
                    progress.progress = percent
                    status.text = getString(R.string.update_downloading_progress, percent)
                }
            },
        ) { result ->
            if (downloadDialog === dialog) {
                dialog.dismiss()
                downloadDialog = null
            }
            when (result) {
                is ParentUpdater.DownloadResult.Ok -> installDownloaded(result.file, release)
                is ParentUpdater.DownloadResult.Failed -> showDownloadFailed(result.reason)
                ParentUpdater.DownloadResult.Cancelled -> Unit
            }
        }
        if (!started) {
            dialog.dismiss()
            downloadDialog = null
            toast(R.string.update_checking)
        }
    }

    private fun installDownloaded(file: File, release: ParentUpdater.Release) {
        when (installer.start(file)) {
            ParentInstaller.Start.STARTED -> file.delete()
            ParentInstaller.Start.NEED_PERMISSION -> requestInstallPermission(release)
            ParentInstaller.Start.FAILED -> showInstallFailed()
        }
    }

    private fun showDownloadFailed(reason: ParentUpdater.Failure) {
        val message = when (reason) {
            ParentUpdater.Failure.NETWORK -> R.string.update_download_failed_message
            ParentUpdater.Failure.VERIFY -> R.string.update_verify_failed_message
            ParentUpdater.Failure.NOT_NEWER -> R.string.update_not_newer
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.update_download_failed_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showInstallFailed() {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_install_failed_title)
            .setMessage(R.string.update_install_failed_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
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
            val mediaType = visualMediaType(types)
            return try {
                when {
                    mediaType != null -> {
                        val request = PickVisualMediaRequest(mediaType)
                        if (multiple) {
                            pickMultipleMedia.launch(request)
                        } else {
                            pickMedia.launch(request)
                        }
                    }
                    multiple -> openMultipleFiles.launch(types)
                    else -> openSingleFile.launch(types)
                }
                true
            } catch (e: Exception) {
                fileChooserCallback = null
                callback.onReceiveValue(null)
                false
            }
        }

        /**
         * The photo picker covers an accept list made only of image and/or
         * video types; every other request keeps the document picker.
         */
        private fun visualMediaType(
            types: Array<String>,
        ): ActivityResultContracts.PickVisualMedia.VisualMediaType? {
            if (types.isEmpty()) return null
            val image = types.all { it.startsWith("image/") }
            val video = types.all { it.startsWith("video/") }
            return when {
                image -> ActivityResultContracts.PickVisualMedia.ImageOnly
                video -> ActivityResultContracts.PickVisualMedia.VideoOnly
                types.all { it.startsWith("image/") || it.startsWith("video/") } ->
                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                else -> null
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

        /**
         * The page's "log out" action means choosing another TV; the TV-side
         * session is cleared by the page first. The saved device list stays.
         */
        @JavascriptInterface
        fun logout() {
            runOnUiThread {
                if (!isTrustedPage()) return@runOnUiThread
                if (devices.devices.isEmpty()) showPairing() else showDeviceList()
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        val release = waitingForPermission
        if (release != null && installer.canInstall()) {
            waitingForPermission = null
            startUpdate(release)
        }
    }

    override fun onPause() {
        binding.webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        installer.unregister()
        installer.onResult = null
        updater.close()
        downloadDialog?.dismiss()
        versionDialog?.dismiss()
        deviceListDialog?.dismiss()
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
