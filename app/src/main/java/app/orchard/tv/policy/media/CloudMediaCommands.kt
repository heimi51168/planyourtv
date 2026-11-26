package app.orchard.tv.policy.media

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * V56 remote media commands. The MQTT ack only says “accepted”: a download progresses in the
 * background and the remote page reads its real state from [CloudMediaStateReporter]. Nothing
 * here writes configuration; the backend clears references before it sends a delete.
 */
object CloudMediaCommands {
    private const val TAG = "CloudMediaCommand"
    private const val SCHEMA = 1
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val shaPattern = Regex("[a-f0-9]{64}")
    private val idPattern = Regex("[1-9][0-9]{0,18}")
    private val extPattern = Regex("[a-zA-Z0-9]{1,8}")

    fun handle(context: Context, data: JSONObject?): Boolean {
        if (data == null) {
            Log.w(TAG, "wallpaper_media command without data")
            return false
        }
        if (data.optInt("schema", 0) != SCHEMA) {
            Log.w(TAG, "wallpaper_media schema unsupported=${data.optInt("schema", 0)}")
            return false
        }
        val action = data.optString("action", "").trim()
        val kind = data.optString("kind", "").trim().uppercase()
        val officialId = data.optString("officialId", "").trim()
        val custom = data.optJSONObject("custom")
        val accepted = when (action) {
            "download" -> when (kind) {
                "MEDIA" -> downloadOfficialMedia(context, officialId)
                "AUDIO" -> downloadOfficialAudio(context, officialId)
                "CUSTOM_MEDIA", "CUSTOM_AUDIO" -> downloadCustom(context, kind, custom)
                else -> reject("download kind=$kind")
            }
            "cancel" -> when (kind) {
                "MEDIA" -> OfficialMediaDownloader.cancel(officialId)
                "AUDIO" -> OfficialAudioDownloader.cancel(officialId)
                "CUSTOM_MEDIA", "CUSTOM_AUDIO" -> cancelCustom(context, kind, custom)
                else -> reject("cancel kind=$kind")
            }
            "deleteLocal" -> when (kind) {
                "MEDIA" -> deleteOfficialMedia(context, officialId)
                "AUDIO" -> deleteOfficialAudio(context, officialId)
                "CUSTOM_MEDIA", "CUSTOM_AUDIO" -> deleteCustom(context, kind, custom)
                else -> reject("deleteLocal kind=$kind")
            }
            "refreshCatalog" -> refreshCatalog(context, kind)
            "refreshCustomLibrary" -> {
                scope.launch {
                    runCatching {
                        CustomMediaLibraryClient.refreshNow(
                            context = context.applicationContext,
                            downloadMissing = true,
                            forceRetry = true,
                        )
                    }.onFailure { Log.w(TAG, "refresh custom library failed", it) }
                }
                true
            }
            "reportState" -> {
                if (!CloudMediaStateReporter.isEnabled()) {
                    reject("reportState before media center is enabled")
                } else {
                    scope.launch { CloudMediaStateReporter.reportNow(context.applicationContext) }
                    true
                }
            }
            else -> reject("action=$action")
        }
        CloudMediaStateReporter.requestSoon()
        return accepted
    }

    private fun downloadOfficialMedia(context: Context, id: String): Boolean {
        if (!id.startsWith("official:")) return reject("media id=$id")
        val item = OfficialMediaCatalog.find(context, id) ?: return reject("unknown media id=$id")
        return when (val result = OfficialMediaDownloader.start(context, id, item.roles)) {
            is OfficialMediaDownloader.StartResult.Started,
            is OfficialMediaDownloader.StartResult.AlreadyReady,
            is OfficialMediaDownloader.StartResult.Running,
            -> true
            else -> reject("media download id=$id result=${result.javaClass.simpleName}")
        }
    }

    private fun downloadOfficialAudio(context: Context, id: String): Boolean {
        if (!id.startsWith("official:")) return reject("audio id=$id")
        if (OfficialAudioCatalog.find(context, id) == null) return reject("unknown audio id=$id")
        return when (val result = OfficialAudioDownloader.start(context, id)) {
            is OfficialAudioDownloader.StartResult.Started,
            is OfficialAudioDownloader.StartResult.AlreadyReady,
            is OfficialAudioDownloader.StartResult.Running,
            -> true
            else -> reject("audio download id=$id result=${result.javaClass.simpleName}")
        }
    }

    private fun deleteOfficialMedia(context: Context, id: String): Boolean {
        if (!id.startsWith("official:")) return reject("media delete id=$id")
        val active = OfficialMediaDownloader.progress(id)
        if (active?.state == OfficialMediaDownloader.STATE_DOWNLOADING ||
            active?.state == OfficialMediaDownloader.STATE_VERIFYING
        ) {
            return reject("media delete while downloading id=$id")
        }
        return when (val result = PolicyVideoLibrary.delete(context, id)) {
            PolicyVideoLibrary.EditResult.Ok -> true
            else -> reject("media delete id=$id result=${result.javaClass.simpleName}")
        }
    }

    private fun deleteOfficialAudio(context: Context, id: String): Boolean {
        if (!id.startsWith("official:")) return reject("audio delete id=$id")
        val active = OfficialAudioDownloader.progress(id)
        if (active?.state == OfficialAudioDownloader.STATE_DOWNLOADING ||
            active?.state == OfficialAudioDownloader.STATE_VERIFYING
        ) {
            return reject("audio delete while downloading id=$id")
        }
        return when (val result = PolicyVideoLibrary.deleteLockAudio(context, id)) {
            PolicyVideoLibrary.EditResult.Ok -> true
            else -> reject("audio delete id=$id result=${result.javaClass.simpleName}")
        }
    }

    private fun downloadCustom(context: Context, commandKind: String, custom: JSONObject?): Boolean {
        val remote = parseCustom(custom) ?: return false
        if (commandKind == "CUSTOM_AUDIO" && !remote.kind.isAudio) {
            return reject("custom audio kind mismatch id=${remote.id}")
        }
        if (commandKind == "CUSTOM_MEDIA" && remote.kind.isAudio) {
            return reject("custom media kind mismatch id=${remote.id}")
        }
        return when (val result = CustomMediaDownloader.start(context, remote)) {
            is CustomMediaDownloader.StartResult.Started,
            is CustomMediaDownloader.StartResult.AlreadyReady,
            is CustomMediaDownloader.StartResult.Running,
            -> true
            CustomMediaDownloader.StartResult.NoSpace ->
                reject("custom download no space id=${remote.id}")
            CustomMediaDownloader.StartResult.Invalid ->
                reject("custom download invalid id=${remote.id}")
        }
    }

    private fun cancelCustom(context: Context, commandKind: String, custom: JSONObject?): Boolean {
        val kind = resolveCustomKind(context, commandKind, custom) ?: return false
        val id = customId(custom) ?: return false
        return CustomMediaDownloader.cancel(context, kind, id)
    }

    /**
     * Deletes only the cached upload; the cloud upload is owned by the backend. A file still
     * named by a published hosted manifest stays until the next config sync removes it.
     */
    private fun deleteCustom(context: Context, commandKind: String, custom: JSONObject?): Boolean {
        val id = customId(custom) ?: return false
        val kinds = if (commandKind == "CUSTOM_AUDIO") {
            listOf(CustomMediaKind.AUDIO)
        } else {
            val declared = CustomMediaKind.fromWire(custom?.optString("kind"))
            if (declared != null && !declared.isAudio) {
                listOf(declared)
            } else {
                listOf(CustomMediaKind.VIDEO, CustomMediaKind.IMAGE)
            }
        }
        if (kinds.any { CustomMediaDownloader.isRunning(it, id) }) {
            return reject("custom delete while downloading id=$id")
        }
        if (CustomMediaStore.isReferencedByHosted(context, id)) {
            return reject("custom delete still referenced by hosted cache id=$id")
        }
        var deleted = true
        kinds.forEach { kind ->
            if (CustomMediaStore.read(context, kind, id) != null &&
                !CustomMediaStore.delete(context, kind, id)
            ) {
                deleted = false
            }
            CustomMediaDownloader.forget(context, kind, id)
        }
        // The backend may delete an upload this TV never downloaded; that is still a success.
        if (!deleted) reject("custom delete failed id=$id")
        return deleted
    }

    private fun refreshCatalog(context: Context, kind: String): Boolean = when (kind) {
        "MEDIA" -> OfficialMediaCatalog.requestRefresh(context, force = true).also {
            if (it) reportCatalogLater()
        }
        "AUDIO" -> OfficialAudioCatalog.requestRefresh(context, force = true).also {
            if (it) reportCatalogLater()
        }
        else -> reject("refreshCatalog kind=$kind")
    }

    /** The refresh itself is asynchronous; report packs once it has had time to finish. */
    private fun reportCatalogLater() {
        scope.launch {
            delay(3_000L)
            CloudMediaStateReporter.requestSoon()
            delay(12_000L)
            CloudMediaStateReporter.requestSoon()
            delay(30_000L)
            CloudMediaStateReporter.requestSoon()
        }
    }

    private fun parseCustom(custom: JSONObject?): CustomMediaRemote? {
        if (custom == null) return null
        val id = customId(custom) ?: return null
        val kind = CustomMediaKind.fromWire(custom.optString("kind")) ?: return null
        val name = custom.optString("name", "").trim().ifBlank { "custom:$id" }
        val ext = custom.optString("ext", "").trim()
        if (!extPattern.matches(ext)) {
            reject("custom ext id=$id")
            return null
        }
        val version = custom.optLong("version", -1L)
        if (version < 0L) return null
        val url = custom.optString("url", "").trim()
        if (!url.startsWith("https://")) return null
        val sha = custom.optString("sha256", "").trim().lowercase()
        if (!shaPattern.matches(sha)) return null
        val size = custom.optLong("sizeBytes", 0L)
        if (size <= 0L || size > CustomMediaStore.maxBytes(kind)) return null
        val duration = custom.optLong("durationMs", -1L)
        if (duration < 0L) return null
        if (kind != CustomMediaKind.IMAGE && duration <= 0L) return null
        val hasAudio = if (custom.has("hasAudio") && !custom.isNull("hasAudio")) {
            custom.optBoolean("hasAudio")
        } else {
            null
        }
        return CustomMediaRemote(
            id = id,
            kind = kind,
            name = name,
            ext = ext,
            version = version,
            url = url,
            sha256 = sha,
            sizeBytes = size,
            durationMs = duration,
            hasAudio = hasAudio,
        )
    }

    private fun customId(custom: JSONObject?): Long? {
        val raw = custom?.optString("id", "")?.trim().orEmpty()
        if (!idPattern.matches(raw)) return null
        return raw.toLongOrNull()
    }

    private fun resolveCustomKind(
        context: Context,
        commandKind: String,
        custom: JSONObject?,
    ): CustomMediaKind? {
        if (commandKind == "CUSTOM_AUDIO") return CustomMediaKind.AUDIO
        val id = customId(custom) ?: return null
        val declared = CustomMediaKind.fromWire(custom?.optString("kind"))
        if (declared != null && !declared.isAudio) return declared
        if (CustomMediaDownloader.isRunning(CustomMediaKind.VIDEO, id)) {
            return CustomMediaKind.VIDEO
        }
        if (CustomMediaDownloader.isRunning(CustomMediaKind.IMAGE, id)) {
            return CustomMediaKind.IMAGE
        }
        if (CustomMediaStore.read(context, CustomMediaKind.VIDEO, id) != null) {
            return CustomMediaKind.VIDEO
        }
        if (CustomMediaStore.read(context, CustomMediaKind.IMAGE, id) != null) {
            return CustomMediaKind.IMAGE
        }
        return null
    }

    private fun reject(reason: String): Boolean {
        Log.w(TAG, "rejected: $reason")
        return false
    }
}