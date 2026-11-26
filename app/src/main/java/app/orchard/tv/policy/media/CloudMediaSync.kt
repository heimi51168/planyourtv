package app.orchard.tv.policy.media

import android.content.Context
import app.orchard.tv.policy.PolicyCloud
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.update.LegacyUpdateTls
import com.planyourtv.log.TvLog
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicyRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

/**
 * V54: only the exact planned track may reuse the cached file. Checking just "the file is usable"
 * let a track switch reuse the previous selection and report the new plan as unchanged.
 */
internal fun reusableHostedAudio(planned: HostedAudioPlan?, existing: HostedAudio?): HostedAudio? =
    existing?.takeIf { planned != null && planned.key == it.key }

/**
 * Pulls the cloud child media into [HostedMediaStore] and publishes the execution view through
 * [HostedMediaRoute]. This is the only component that touches the network for hosted media.
 *
 * Invariants (V50):
 * - Only CLOUD mode with a confirmed member may sync. Switching to LOCAL/NONE clears the route
 *   snapshot but keeps files, so switching back is a cache read with no network dependency.
 * - A sync either publishes one complete manifest or leaves the previous one untouched. A failed
 *   download never replaces a working cache with a partial one.
 * - The URL returned by the backend is used for this fetch only; it is never written to disk or
 *   logged.
 * - No component here writes a local upload and no local store reads this cache. The only bridge
 *   between the two worlds is the route's in-memory snapshot.
 */
object CloudMediaSync {
    private const val TAG = "CloudMedia"
    private const val DEBOUNCE_MS = 300L
    private const val PERIODIC_MS = 10 * 60_000L
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val BUFFER_BYTES = 64 * 1024
    /** Unknown size from an older backend; only used for the free-space estimate. */
    private const val ASSUMED_IMAGE_BYTES = PolicyImageValidation.MAX_STORED_BYTES
    private const val ASSUMED_VIDEO_BYTES = PolicyVideoValidation.MAX_WALLPAPER_FILE_BYTES
    private const val ASSUMED_QUIZ_BYTES = PolicyVideoValidation.MAX_QUIZ_FILE_BYTES
    private const val POSTER_RESERVE_BYTES = PolicyVideoValidation.MAX_POSTER_BYTES
    private const val MIDNIGHT_GRACE_MS = 500L
    private val TV_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    private var installed = false

    @Volatile
    private var appContext: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<String>(Channel.CONFLATED)
    private val syncLock = Mutex()

    /**
     * V52: serializes every cache mutation (publish writes, preload commits, cleanup). Downloads
     * stay outside it; only file placement, index/manifest writes and deletion take the lock.
     */
    private val mediaLock = Mutex()

    /** requestId-scoped jobs; a duplicate MQTT command must not restart the downloads. */
    private val preloadJobs = ConcurrentHashMap<String, Boolean>()

    /** Invalidates an in-flight sync after unbind or a mode change. */
    private val generation = AtomicLong(0L)

    @Volatile
    private var lastPublishedMemberId: Long? = null

    @Volatile
    private var lastObservedMemberId: Long? = null

    /** True until the first confirmed member of this CLOUD entry has been handled. */
    @Volatile
    private var awaitingMember = true

    fun install(context: Context) {
        if (installed) return
        installed = true
        appContext = context.applicationContext
        HostedMediaRoute.setCloud(PolicyModeCoordinator.isCloud())
        scope.launch { consumeRequests() }
        scope.launch { periodicLoop() }
        scope.launch { midnightLoop() }
        scope.launch { observeLifecycle() }
        TvLog.i(TAG, "hosted media sync installed")
    }

    /** Coalesces bursts (config poll + MQTT + member switch) into one fetch. */
    fun request(reason: String) {
        if (!installed) return
        if (!PolicyModeCoordinator.isCloud()) return
        requests.trySend(reason.trim().ifEmpty { "request" })
    }

    /**
     * The backend confirmed this TV is no longer bound to the family. Cloud-owned media is the
     * family's content, so it is deleted; the switch back to NONE keeps only local files.
     */
    fun clearForUnbind() {
        if (!installed) return
        generation.incrementAndGet()
        lastPublishedMemberId = null
        lastObservedMemberId = null
        awaitingMember = true
        // The removed family's media must stop being the execution source immediately, not on
        // the next lifecycle emission.
        HostedMediaRoute.setCloud(false)
        CloudMediaStateReporter.disable()
        CustomMediaLibraryClient.disable()
        val context = appContext ?: return
        HostedMediaStore.clearAll(context)
        CustomMediaStore.clearAll(context)
        TvLog.i(TAG, "hosted media cleared after unbind")
    }

    private suspend fun consumeRequests() {
        for (reason in requests) {
            delay(DEBOUNCE_MS)
            var latest = reason
            while (true) {
                val next = requests.tryReceive().getOrNull() ?: break
                latest = next
            }
            syncLock.withLock { syncOnce(latest) }
        }
    }

    private suspend fun periodicLoop() {
        while (scope.isActive) {
            delay(PERIODIC_MS)
            if (!PolicyModeCoordinator.isCloud()) continue
            syncLock.withLock { syncOnce("periodic") }
        }
    }

    /**
     * DAILY rotation must turn over at local midnight with no network dependency. This only
     * re-reads the published manifest; a failed/absent cache keeps the current snapshot.
     */
    private suspend fun midnightLoop() {
        while (scope.isActive) {
            delay(millisUntilNextMidnight())
            if (!PolicyModeCoordinator.isCloud()) continue
            republishDailySnapshot()
        }
    }

    private fun republishDailySnapshot() {
        val context = appContext ?: return
        val member = PolicyRuntime.state.value.cloudActiveMemberId
            ?: lastPublishedMemberId
            ?: return
        val manifest = HostedMediaStore.readManifest(context, member) ?: return
        if (manifest.wallpaper.mode != HostedWallpaperMode.DAILY) return
        HostedMediaRoute.setSnapshot(HostedMediaStore.snapshotOf(context, manifest))
        TvLog.i(TAG, "member=$member reason=midnight daily snapshot refreshed")
    }

    internal fun millisUntilNextMidnight(now: ZonedDateTime = ZonedDateTime.now(TV_ZONE)): Long {
        val next = now.toLocalDate().plusDays(1).atStartOfDay(TV_ZONE)
        return Duration.between(now, next).toMillis().coerceAtLeast(1_000L) + MIDNIGHT_GRACE_MS
    }

    private suspend fun observeLifecycle() {
        combine(
            PolicyModeCoordinator.state.map { it.mode }.distinctUntilChanged(),
            PolicyRuntime.state.map { it.cloudActiveMemberId }.distinctUntilChanged(),
        ) { mode, member -> mode to member }
            .collect { (mode, member) -> onLifecycle(mode, member) }
    }

    /**
     * Mode/member reactivity only reads the two existing state flows; it never decides anything
     * about remote policy. Local and cloud still switch data sources here and only here.
     */
    private fun onLifecycle(mode: PolicyMode, member: Long?) {
        if (mode != PolicyMode.CLOUD) {
            if (HostedMediaRoute.isCloud()) {
                HostedMediaRoute.setCloud(false)
                TvLog.i(TAG, "cloud exit; hosted files kept for the next cloud entry")
            }
            // State reporting and the upload library are cloud-only; a local mode must never
            // keep polling the cloud or publishing the previous family's device state.
            CloudMediaStateReporter.disable()
            CustomMediaLibraryClient.disable()
            generation.incrementAndGet()
            lastPublishedMemberId = null
            lastObservedMemberId = null
            awaitingMember = true
            return
        }
        val entering = !HostedMediaRoute.isCloud()
        HostedMediaRoute.setCloud(true)
        if (entering) {
            awaitingMember = true
            TvLog.i(TAG, "cloud entry; waiting for the active child")
        }
        if (member == null) {
            // Memory-only active member: right after process start it is unknown until the
            // first config poll. Keep the last complete cache on screen meanwhile; it is
            // replaced as soon as the backend names the member.
            if (awaitingMember && lastPublishedMemberId == null) {
                val cached = appContext?.let { HostedMediaStore.lastMemberId(it) }
                if (cached != null) publishCached(cached)
            }
            return
        }
        val changed = member != lastObservedMemberId
        lastObservedMemberId = member
        if (lastPublishedMemberId != member) {
            HostedMediaRoute.setSnapshot(null)
            lastPublishedMemberId = member
            publishCached(member)
        }
        if (changed || awaitingMember) {
            val reason = if (awaitingMember) "enter-cloud" else "member-changed"
            awaitingMember = false
            request(reason)
        }
    }

    private fun publishCached(memberId: Long) {
        val context = appContext ?: return
        val snapshot = HostedMediaStore.readSnapshot(context, memberId)
        if (snapshot == null) {
            TvLog.i(TAG, "member=$memberId no hosted cache yet")
            return
        }
        HostedMediaRoute.setSnapshot(snapshot)
        TvLog.i(
            TAG,
            "member=$memberId published cached wallpaper=${snapshot.selection.items.size} " +
                "quiz=${snapshot.quizSource != null}",
        )
    }

    private suspend fun syncOnce(reason: String) {
        val context = appContext ?: return
        val startedGeneration = generation.get()
        if (!PolicyModeCoordinator.isCloud()) {
            TvLog.i(TAG, "reason=$reason skipped=not-cloud")
            return
        }
        val member = PolicyRuntime.state.value.cloudActiveMemberId
        if (member == null || member <= 0L) {
            writeStatus(context, "skipped", HostedMediaError.NO_MEMBER, null, reason, 0, null)
            TvLog.w(TAG, "member=none reason=$reason result=no-member")
            return
        }
        TvLog.i(TAG, "member=$member reason=$reason result=start")
        val payload = PolicyCloud.fetchConfiguredApps(member).getOrElse { error ->
            writeStatus(context, "failed", HostedMediaError.NETWORK, member, reason, 0, null)
            // The exception message can carry HTTP details but never the signed URL; keep the
            // class only so a transient timeout stays distinguishable from a rejected payload.
            TvLog.w(TAG, "member=$member reason=$reason result=network", error)
            return
        }
        if (aborted(startedGeneration)) {
            TvLog.i(TAG, "member=$member reason=$reason result=aborted")
            return
        }
        // V56 capability gate: the backend only emits `wallpaperMediaSchema` for a TV that
        // reported the media-center capability. An old backend never enables the reporter or
        // the upload library, so this new build keeps the V54 behaviour against it.
        val mediaCenter = payload.optInt("wallpaperMediaSchema", 4) >= WALLPAPER_MEDIA_SCHEMA
        if (mediaCenter) {
            CloudMediaStateReporter.enable(context)
            CustomMediaLibraryClient.enable(context)
        } else {
            CloudMediaStateReporter.disable()
            CustomMediaLibraryClient.disable()
        }
        val planned = when (val planResult = HostedMediaPlanner.plan(payload)) {
            is HostedMediaPlanResult.Rejected -> {
                writeStatus(
                    context = context,
                    result = "rejected",
                    code = planResult.error,
                    memberId = member,
                    reason = reason,
                    items = 0,
                    quizIntroId = null,
                )
                TvLog.w(TAG, "member=$member reason=$reason result=rejected code=${planResult.error.wire}")
                return
            }
            is HostedMediaPlanResult.Success -> planResult.plan
        }
        if (planned.memberId != member) {
            writeStatus(context, "rejected", HostedMediaError.BAD_PAYLOAD, member, reason, 0, null)
            TvLog.w(TAG, "member=$member reason=$reason result=member-changed-during-fetch")
            return
        }
        // Publish mutates files/, so it shares the lock with preload commits and cleanup. Its
        // downloads are part of that window on purpose: a concurrent cleanup must not delete a
        // publish temp file before it is renamed into the content-addressed namespace.
        mediaLock.withLock {
            publish(context, member, reason, planned, startedGeneration)
        }
    }

    /**
     * V52 assets-first: the backend asks the TV to cache one member's whole wallpaper library
     * before the parent page allows a save. This never publishes: the route snapshot keeps the
     * previous wallpaper until the parent actually saves. Returns the MQTT ack value immediately;
     * downloads run in the background and report through the device HTTP channel.
     */
    fun onPreloadCommand(data: JSONObject?): Boolean {
        if (data == null) {
            TvLog.w(TAG, "preload command without data")
            return false
        }
        val schema = data.optInt("schema", 0)
        if (schema != PRELOAD_SCHEMA) {
            TvLog.w(TAG, "preload schema unsupported schema=$schema")
            return false
        }
        val requestId = data.optString("requestId", "").trim()
        val libraryVersion = data.optString("libraryVersion", "").trim()
        val memberId = data.optLong("memberId", 0L)
        val configuredApps = data.optJSONObject("configuredApps")
        if (requestId.isEmpty() || libraryVersion.isEmpty() || memberId <= 0L ||
            configuredApps == null
        ) {
            TvLog.w(TAG, "preload command invalid: requestId=$requestId memberId=$memberId")
            return false
        }
        val payloadMemberId = configuredApps.optLong("learnerId", 0L)
        if (payloadMemberId != memberId) {
            TvLog.w(TAG, "preload member mismatch command=$memberId payload=$payloadMemberId")
            return false
        }
        val jobKey = "$memberId:$requestId"
        if (preloadJobs.containsKey(jobKey)) {
            TvLog.i(TAG, "member=$memberId request=$requestId preload already running; ack only")
            return true
        }
        val context = appContext
        val existing = context?.let { HostedMediaStore.readPreloadIndex(it, memberId) }
        if (context != null && existing != null &&
            existing.requestId == requestId && existing.libraryVersion == libraryVersion
        ) {
            // Idempotent replay: report the persisted state, never restart the downloads.
            TvLog.i(TAG, "member=$memberId request=$requestId preload already finished; re-report")
            scope.launch { reportPreloadIndex(context, memberId, existing) }
            return true
        }
        if (preloadJobs.putIfAbsent(jobKey, true) != null) return true
        scope.launch {
            try {
                runPreload(memberId, requestId, libraryVersion, configuredApps)
            } finally {
                preloadJobs.remove(jobKey)
            }
        }
        return true
    }

    private suspend fun runPreload(
        memberId: Long,
        requestId: String,
        libraryVersion: String,
        configuredApps: JSONObject,
    ) {
        val context = appContext ?: return
        if (!installed) return
        val startedGeneration = generation.get()
        val library = when (val parsed = HostedMediaPlanner.library(configuredApps)) {
            is HostedMediaLibraryResult.Rejected -> {
                val progress = PreloadProgress(parsed.keys)
                parsed.keys.forEach { progress.markFailed(it, parsed.error) }
                reportPreload(memberId, requestId, libraryVersion, progress)
                writeStatus(context, "rejected", parsed.error, memberId, "preload", 0, null)
                TvLog.w(
                    TAG,
                    "member=$memberId request=$requestId preload rejected " +
                        "code=${parsed.error.wire} keys=${parsed.keys.size}",
                )
                return
            }
            is HostedMediaLibraryResult.Success -> parsed.library
        }
        val items = library.allItems()
        if (library.memberId != memberId) {
            // Command and payload disagree. Report the derivable keys as failed so the page can
            // retry; the report endpoint itself validates the member against the family.
            val progress = PreloadProgress(items.map { it.key })
            items.forEach { progress.markFailed(it.key, HostedMediaError.BAD_PAYLOAD) }
            reportPreload(memberId, requestId, libraryVersion, progress)
            TvLog.e(
                TAG,
                "member=$memberId request=$requestId payload member mismatch=${library.memberId}",
            )
            return
        }
        val quizKey = library.quizIntro?.key
        val reusable = HashMap<String, HostedMediaItem>()
        HostedMediaStore.readManifest(context, memberId)?.let { manifest ->
            manifest.wallpaper.items.forEach { entry ->
                if (isUsable(context, memberId, entry)) reusable[entry.key] = entry
            }
            manifest.quizIntro?.let { entry ->
                if (isUsable(context, memberId, entry)) reusable[entry.key] = entry
            }
        }
        HostedMediaStore.readPreloadIndex(context, memberId)?.items?.forEach { entry ->
            if (isUsable(context, memberId, entry)) reusable[entry.key] = entry
        }
        val progress = PreloadProgress(items.map { it.key })
        items.forEach { item -> reusable[item.key]?.let { progress.markReady(item.key, it) } }
        val missing = items.filter { reusable[it.key] == null }

        if (missing.isNotEmpty()) {
            val needed = missing.sumOf { item ->
                item.sizeBytes ?: when {
                    item.key == quizKey -> ASSUMED_QUIZ_BYTES
                    item.kind == PolicyMediaKind.IMAGE -> ASSUMED_IMAGE_BYTES
                    else -> ASSUMED_VIDEO_BYTES
                }
            } + missing.count { it.kind == PolicyMediaKind.VIDEO } * POSTER_RESERVE_BYTES +
                HostedMediaStore.RESERVED_BYTES
            val free = HostedMediaStore.filesDir(context, memberId).usableSpace
            if (free < needed) {
                missing.forEach { progress.markFailed(it.key, HostedMediaError.NO_SPACE) }
                reportPreload(memberId, requestId, libraryVersion, progress)
                writeStatus(
                    context, "failed", HostedMediaError.NO_SPACE, memberId, "preload", 0, null,
                )
                TvLog.w(
                    TAG,
                    "member=$memberId request=$requestId preload no-space " +
                        "need=$needed free=$free",
                )
                return
            }
        }

        TvLog.i(
            TAG,
            "member=$memberId request=$requestId preload start " +
                "items=${items.size} missing=${missing.size}",
        )
        var wasAborted = false
        for (item in missing) {
            if (generation.get() != startedGeneration) {
                wasAborted = true
                break
            }
            try {
                val entry = preloadItem(
                    context = context,
                    memberId = memberId,
                    item = item,
                    quiz = item.key == quizKey,
                    requestId = requestId,
                    libraryVersion = libraryVersion,
                    ready = progress.readyEntries(),
                    startedGeneration = startedGeneration,
                )
                progress.markReady(item.key, entry)
                reportPreload(memberId, requestId, libraryVersion, progress)
            } catch (failure: SyncFailure) {
                progress.markFailed(item.key, failure.error)
                reportPreload(memberId, requestId, libraryVersion, progress)
                TvLog.w(
                    TAG,
                    "member=$memberId request=$requestId item failed " +
                        "key=${item.key} code=${failure.error.wire}",
                )
            } catch (abort: PreloadAborted) {
                wasAborted = true
                break
            } catch (error: IOException) {
                progress.markFailed(item.key, HostedMediaError.NETWORK)
                reportPreload(memberId, requestId, libraryVersion, progress)
                TvLog.w(
                    TAG,
                    "member=$memberId request=$requestId item failed key=${item.key}",
                    error,
                )
            }
        }
        if (wasAborted) {
            TvLog.i(TAG, "member=$memberId request=$requestId preload aborted")
            return
        }
        // Trim the index to this library, then delete files neither the manifest nor the index
        // references. Eviction shares the same window: publish holds mediaLock end-to-end, so
        // deleting another member here must not interleave with that member's downloads.
        val indexWritten = mediaLock.withLock {
            val written = HostedMediaStore.writePreloadIndex(
                context,
                HostedPreloadIndex(
                    memberId = memberId,
                    requestId = requestId,
                    libraryVersion = libraryVersion,
                    updatedAtMs = System.currentTimeMillis(),
                    items = progress.readyEntries(),
                ),
            )
            if (written) HostedMediaStore.cleanupPreloadUnreferenced(context, memberId)
            // The preload target may be another member than the child on screen: protect both
            // from eviction, or a "ready" library could vanish between the page check and save.
            val activeMember = PolicyRuntime.state.value.cloudActiveMemberId
                ?: HostedMediaStore.lastMemberId(context)
            HostedMediaStore.enforceTotalLimit(
                context,
                activeMemberId = activeMember,
                protectedMemberIds = setOf(memberId),
            )
            written
        }
        val failure = progress.failedError()
        if (!indexWritten) {
            writeStatus(
                context, "failed", HostedMediaError.WRITE_FAILED, memberId, "preload",
                progress.readyEntries().size, null,
            )
        } else {
            writeStatus(
                context,
                if (failure == null) "preloaded" else "failed",
                failure,
                memberId,
                "preload",
                progress.readyEntries().size,
                quizIntroIdOf(library),
            )
        }
        // The final report is sent after trimming, so the page never reads a stale ready set.
        reportPreload(memberId, requestId, libraryVersion, progress)
        TvLog.i(
            TAG,
            "member=$memberId request=$requestId preload done " +
                "ready=${progress.readyEntries().size}/${items.size} " +
                "failed=${failure?.wire ?: "-"}",
        )
    }

    private suspend fun preloadItem(
        context: Context,
        memberId: Long,
        item: HostedMediaPlanItem,
        quiz: Boolean,
        requestId: String,
        libraryVersion: String,
        ready: List<HostedMediaItem>,
        startedGeneration: Long,
    ): HostedMediaItem {
        val temp = HostedMediaStore.newPreloadTempFile(context, memberId)
        val fetched = try {
            fetchToTemp(context, item, quiz, temp)
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
        val info = try {
            validatePreloadTemp(temp, item, quiz)
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
        try {
            return mediaLock.withLock {
                if (generation.get() != startedGeneration) throw PreloadAborted()
                val target = commitPreloadFile(context, memberId, temp, item, fetched)
                val poster = if (item.kind == PolicyMediaKind.VIDEO) {
                    writePoster(context, memberId, target)
                } else {
                    null
                }
                val entry = HostedMediaItem(
                    key = item.key,
                    file = HostedMediaStore.relativeFileName(target),
                    poster = poster?.relative,
                    kind = item.kind,
                    sizeBytes = target.length(),
                    durationMs = info?.durationMs ?: 0L,
                    hasAudio = info?.hasAudio ?: false,
                )
                val next = ready + entry
                val written = HostedMediaStore.writePreloadIndex(
                    context,
                    HostedPreloadIndex(
                        memberId = memberId,
                        requestId = requestId,
                        libraryVersion = libraryVersion,
                        updatedAtMs = System.currentTimeMillis(),
                        items = next,
                    ),
                )
                if (!written) throw SyncFailure(HostedMediaError.WRITE_FAILED, "preload index")
                entry
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun validatePreloadTemp(
        temp: File,
        item: HostedMediaPlanItem,
        quiz: Boolean,
    ): PolicyVideoValidation.Info? {
        if (item.sizeBytes != null && temp.length() != item.sizeBytes) {
            throw SyncFailure(HostedMediaError.ITEM_INVALID, "declared size mismatch")
        }
        if (item.kind == PolicyMediaKind.IMAGE) {
            when (val checked = PolicyImageValidation.inspectStored(temp)) {
                is PolicyImageValidation.Result.Rejected ->
                    throw SyncFailure(HostedMediaError.ITEM_INVALID, checked.code)
                is PolicyImageValidation.Result.Ok -> Unit
            }
            return null
        }
        val info = when (
            val checked = PolicyVideoValidation.validateLocalUpload(
                temp,
                if (quiz) PolicyVideoValidation.MAX_QUIZ_DURATION_MS else null,
            )
        ) {
            is PolicyVideoValidation.Result.Rejected ->
                throw SyncFailure(HostedMediaError.ITEM_INVALID, checked.code)
            is PolicyVideoValidation.Result.Ok -> checked.info
        }
        if (quiz) {
            if (temp.length() > PolicyVideoValidation.MAX_QUIZ_FILE_BYTES) {
                throw SyncFailure(HostedMediaError.QUIZ_TOO_LARGE, "size")
            }
            if (info.durationMs > PolicyVideoValidation.MAX_QUIZ_DURATION_MS) {
                throw SyncFailure(HostedMediaError.QUIZ_TOO_LONG, "duration")
            }
        }
        return info
    }

    private fun commitPreloadFile(
        context: Context,
        memberId: Long,
        temp: File,
        item: HostedMediaPlanItem,
        fetched: Fetched,
    ): File {
        val target = File(HostedMediaStore.filesDir(context, memberId), "${fetched.sha}.${item.ext}")
        return try {
            if (target.isFile && target.length() == fetched.total) {
                temp.delete()
            } else {
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            }
            target
        } catch (error: IOException) {
            throw SyncFailure(HostedMediaError.WRITE_FAILED, error.javaClass.simpleName)
        }
    }

    private suspend fun reportPreload(
        memberId: Long,
        requestId: String,
        libraryVersion: String,
        progress: PreloadProgress,
    ) {
        val deviceNo = PolicyRuntime.state.value.deviceNo
        if (deviceNo.isBlank()) {
            TvLog.w(TAG, "member=$memberId request=$requestId report skipped: no device number")
            return
        }
        PolicyCloud.reportWallpaperPreload(
            requestId = requestId,
            memberId = memberId,
            libraryVersion = libraryVersion,
            items = progress.toJson(),
        ).onFailure { error ->
            TvLog.w(TAG, "member=$memberId request=$requestId preload report failed", error)
        }
    }

    /** Replays a finished request from the index; missing files are reported as failed. */
    private suspend fun reportPreloadIndex(
        context: Context,
        memberId: Long,
        index: HostedPreloadIndex,
    ) {
        val progress = PreloadProgress(index.items.map { it.key })
        index.items.forEach { entry ->
            if (isUsable(context, memberId, entry)) {
                progress.markReady(entry.key, entry)
            } else {
                progress.markFailed(entry.key, HostedMediaError.ITEM_INVALID)
            }
        }
        reportPreload(memberId, index.requestId, index.libraryVersion, progress)
    }

    /** Members with an in-flight preload; eviction must not remove their download targets. */
    private fun preloadMembers(): Set<Long> =
        preloadJobs.keys.mapNotNull { it.substringBefore(':').toLongOrNull() }.toSet()

    private fun quizIntroIdOf(library: HostedMediaLibrary): Long? =
        library.quizIntro?.id?.removePrefix("custom:")?.toLongOrNull()

    private fun publish(
        context: Context,
        member: Long,
        reason: String,
        plan: HostedMediaPlan,
        startedGeneration: Long,
    ) {
        val existing = HostedMediaStore.readManifest(context, member)
        val reusable = HashMap<String, HostedMediaItem>()
        existing?.wallpaper?.items?.forEach { entry ->
            if (isUsable(context, member, entry)) reusable[entry.key] = entry
        }
        existing?.quizIntro?.let { entry ->
            if (isUsable(context, member, entry)) reusable[entry.key] = entry
        }
        // V52: assets-first preload uses the same content-addressed namespace. A save after a
        // successful preload must publish from these files without downloading them again.
        HostedMediaStore.readPreloadIndex(context, member)?.items?.forEach { entry ->
            if (isUsable(context, member, entry)) reusable[entry.key] = entry
        }
        // Files the preload index protects even if this publish fails and rolls back.
        val preloadProtected = HostedMediaStore.preloadReferenced(context, member)
        val planItems = plan.wallpaperItems + listOfNotNull(plan.quizIntro)
        val missing = planItems.distinctBy { it.key }.filter { reusable[it.key] == null }
        // V54: background music follows the same on-demand rule as the wallpaper items.
        val reusableAudio = reusableHostedAudio(plan.audio, existing?.audio)
            ?.takeIf { isUsableAudio(context, member, it) }
        val audioMissing = plan.audio != null && reusableAudio == null
        // V56: the quiz-intro track is a third, independent selection.
        val reusableQuizAudio = reusableHostedAudio(plan.quizAudio, existing?.quizAudio)
            ?.takeIf { isUsableAudio(context, member, it) }
        val quizAudioMissing = plan.quizAudio != null && reusableQuizAudio == null
        val schema5 = usesSchema5(plan)

        if (missing.isNotEmpty() || audioMissing || quizAudioMissing) {
            val needed = missing.sumOf { item ->
                item.sizeBytes ?: when {
                    plan.quizIntro?.key == item.key -> ASSUMED_QUIZ_BYTES
                    item.kind == PolicyMediaKind.IMAGE -> ASSUMED_IMAGE_BYTES
                    else -> ASSUMED_VIDEO_BYTES
                }
            } + missing.count { it.kind == PolicyMediaKind.VIDEO } * POSTER_RESERVE_BYTES +
                (plan.audio?.sizeBytes ?: 0L) +
                (plan.quizAudio?.sizeBytes ?: 0L) +
                HostedMediaStore.RESERVED_BYTES
            val free = HostedMediaStore.filesDir(context, member).usableSpace
            if (free < needed) {
                writeStatus(context, "failed", HostedMediaError.NO_SPACE, member, reason, 0, null)
                TvLog.w(TAG, "member=$member reason=$reason result=no-space need=$needed free=$free")
                return
            }
        }

        val downloaded = HashMap<String, HostedMediaItem>()
        val createdFiles = ArrayList<File>()
        try {
            for (item in missing) {
                if (aborted(startedGeneration)) {
                    deleteNewFiles(existing, createdFiles, preloadProtected)
                    TvLog.i(TAG, "member=$member reason=$reason result=aborted")
                    return
                }
                val quiz = plan.quizIntro?.key == item.key
                // V56: stills and the 16 MiB / 15 s caps are schema-5-only; schema 4 keeps the
                // old video-only contract so a cache written for old APKs never changes meaning.
                val legacyQuizLimit = quiz && plan.mediaSchema < WALLPAPER_MEDIA_SCHEMA
                if (quiz && item.kind == PolicyMediaKind.IMAGE && legacyQuizLimit) {
                    throw SyncFailure(HostedMediaError.ITEM_INVALID, "quiz intro is not a video")
                }
                val downloadedFile = download(context, member, item, legacyQuizLimit)
                if (downloadedFile.created) createdFiles += downloadedFile.file
                if (item.sizeBytes != null && downloadedFile.file.length() != item.sizeBytes) {
                    throw SyncFailure(HostedMediaError.ITEM_INVALID, "declared size mismatch")
                }
                if (item.kind == PolicyMediaKind.IMAGE) {
                    // Cloud pictures are prepared by the upload tool and are never re-encoded:
                    // their bytes are the SHA-256 addressed cache entry.
                    when (val checked = PolicyImageValidation.inspectStored(downloadedFile.file)) {
                        is PolicyImageValidation.Result.Rejected -> {
                            throw SyncFailure(HostedMediaError.ITEM_INVALID, checked.code)
                        }
                        is PolicyImageValidation.Result.Ok -> Unit
                    }
                    downloaded[item.key] = HostedMediaItem(
                        key = item.key,
                        file = HostedMediaStore.relativeFileName(downloadedFile.file),
                        poster = null,
                        kind = PolicyMediaKind.IMAGE,
                        sizeBytes = downloadedFile.file.length(),
                        durationMs = 0L,
                        hasAudio = false,
                    )
                } else {
                    val checked = PolicyVideoValidation.validateLocalUpload(
                        downloadedFile.file,
                        if (legacyQuizLimit) PolicyVideoValidation.MAX_QUIZ_DURATION_MS else null,
                    )
                    val info = when (checked) {
                        is PolicyVideoValidation.Result.Rejected -> {
                            throw SyncFailure(HostedMediaError.ITEM_INVALID, checked.code)
                        }
                        is PolicyVideoValidation.Result.Ok -> checked.info
                    }
                    if (legacyQuizLimit) {
                        if (downloadedFile.file.length() > PolicyVideoValidation.MAX_QUIZ_FILE_BYTES) {
                            throw SyncFailure(HostedMediaError.QUIZ_TOO_LARGE, "size")
                        }
                        if (info.durationMs > PolicyVideoValidation.MAX_QUIZ_DURATION_MS) {
                            throw SyncFailure(HostedMediaError.QUIZ_TOO_LONG, "duration")
                        }
                    }
                    val poster = writePoster(context, member, downloadedFile.file)
                    if (poster.created && poster.file != null) createdFiles += poster.file
                    downloaded[item.key] = HostedMediaItem(
                        key = item.key,
                        file = HostedMediaStore.relativeFileName(downloadedFile.file),
                        poster = poster.relative,
                        kind = PolicyMediaKind.VIDEO,
                        sizeBytes = downloadedFile.file.length(),
                        durationMs = info.durationMs,
                        hasAudio = info.hasAudio,
                    )
                }
            }

            val wallpaperItems = plan.wallpaperItems.map { item ->
                reusable[item.key] ?: downloaded.getValue(item.key)
            }
            val quizIntro = plan.quizIntro?.let { item ->
                reusable[item.key] ?: downloaded.getValue(item.key)
            }
            val audio = plan.audio?.let { planned ->
                reusableAudio ?: downloadAudio(context, member, planned, createdFiles)
            }
            val quizAudio = plan.quizAudio?.let { planned ->
                reusableQuizAudio ?: downloadAudio(context, member, planned, createdFiles)
            }
            val manifest = HostedManifest(
                memberId = member,
                publishedAtMs = System.currentTimeMillis(),
                sound = plan.sound,
                wallpaper = HostedWallpaper(
                    mode = plan.mode,
                    activeKey = plan.activeKey,
                    items = wallpaperItems,
                    rotateDays = plan.rotateDays,
                    source = plan.source,
                    intervalMs = plan.intervalMs,
                ),
                quizIntro = quizIntro,
                source = HostedMediaSource(member, plan.sourceItemCount, plan.quizIntroItemId),
                audio = audio,
                quizAudio = quizAudio,
                quizAudioEnabled = plan.quizAudioEnabled && quizAudio != null,
                // V56 writes schema 5 only when an old APK could not execute the plan. A plain
                // video wallpaper / video intro stays on schema 4 so a rollback keeps working.
                schema = if (schema5) HOSTED_MEDIA_SCHEMA else 4,
            )
            if (aborted(startedGeneration)) {
                deleteNewFiles(existing, createdFiles, preloadProtected)
                TvLog.i(TAG, "member=$member reason=$reason result=aborted")
                return
            }
            val unchanged = existing != null && missing.isEmpty() && !audioMissing &&
                !quizAudioMissing && sameMetadata(existing, manifest)
            if (unchanged) {
                HostedMediaRoute.setSnapshot(HostedMediaStore.snapshotOf(context, manifest))
                HostedMediaStore.touch(context, member)
                writeStatus(
                    context, "unchanged", null, member, reason,
                    wallpaperItems.size, plan.quizIntroItemId,
                )
                TvLog.i(
                    TAG,
                    "member=$member reason=$reason result=unchanged items=${wallpaperItems.size}",
                )
                return
            }
            if (!HostedMediaStore.writeManifest(context, manifest)) {
                throw SyncFailure(HostedMediaError.WRITE_FAILED, "manifest")
            }
            val snapshot = HostedMediaStore.snapshotOf(context, manifest)
            HostedMediaRoute.setSnapshot(snapshot)
            HostedMediaStore.touch(context, member)
            HostedMediaStore.cleanupUnreferenced(context, manifest)
            // A preload for another child may still be downloading; its cache must survive this
            // publish's eviction pass or the page would see ready assets disappear mid-sync.
            HostedMediaStore.enforceTotalLimit(
                context,
                member,
                protectedMemberIds = preloadMembers(),
            )
            writeStatus(
                context,
                if (missing.isEmpty()) "metadata" else "published",
                null,
                member,
                reason,
                wallpaperItems.size,
                plan.quizIntroItemId,
            )
            TvLog.i(
                TAG,
                "member=$member reason=$reason result=published items=${wallpaperItems.size} " +
                    "downloaded=${missing.size} sound=${plan.sound} " +
                    "audio=${plan.audio?.id ?: "-"} schema=${plan.mediaSchema} " +
                    "quiz=${plan.quizIntro?.key ?: "-"} " +
                    "quizAudio=${if (plan.quizAudioEnabled) plan.quizAudio?.id ?: "-" else "-"} " +
                    "playlist=${wallpaperItems.joinToString(",") { it.key }}",
            )
        } catch (failure: SyncFailure) {
            deleteNewFiles(existing, createdFiles, preloadProtected)
            writeStatus(context, "failed", failure.error, member, reason, 0, null)
            TvLog.w(
                TAG,
                "member=$member reason=$reason result=failed code=${failure.error.wire} " +
                    "detail=${failure.detail}",
            )
        } catch (error: IOException) {
            deleteNewFiles(existing, createdFiles, preloadProtected)
            writeStatus(context, "failed", HostedMediaError.NETWORK, member, reason, 0, null)
            TvLog.w(TAG, "member=$member reason=$reason result=failed", error)
        }
    }

    private fun aborted(startedGeneration: Long): Boolean =
        generation.get() != startedGeneration || !PolicyModeCoordinator.isCloud()

    private fun isUsable(context: Context, member: Long, entry: HostedMediaItem): Boolean {
        val file = HostedMediaStore.fileFor(context, member, entry.file) ?: return false
        if (!file.isFile || file.length() <= 0L) return false
        if (entry.sizeBytes > 0L && file.length() != entry.sizeBytes) return false
        return true
    }

    private fun isUsableAudio(context: Context, member: Long, entry: HostedAudio): Boolean {
        val file = HostedMediaStore.fileFor(context, member, entry.file) ?: return false
        if (!file.isFile || file.length() <= 0L) return false
        if (entry.sizeBytes > 0L && file.length() != entry.sizeBytes) return false
        return true
    }

    /** All values that can change without re-downloading a byte. */
    /**
     * V56 downgrade safety: schema 5 is written only when a schema-4 reader cannot execute the
     * plan (image quiz intro, quiz-intro audio, or a custom image in the wallpaper playlist).
     * Everything else stays on schema 4 so installing an older APK keeps the current cache
     * readable.
     */
    private fun usesSchema5(plan: HostedMediaPlan): Boolean =
        plan.quizIntro?.kind == PolicyMediaKind.IMAGE ||
            plan.quizAudio != null ||
            plan.quizAudioEnabled ||
            plan.wallpaperItems.any {
                it.kind == PolicyMediaKind.IMAGE && it.id.startsWith("custom:")
            }

    private fun sameMetadata(existing: HostedManifest, next: HostedManifest): Boolean =
        existing.sound == next.sound &&
            existing.wallpaper.mode == next.wallpaper.mode &&
            existing.wallpaper.activeKey == next.wallpaper.activeKey &&
            existing.wallpaper.rotateDays == next.wallpaper.rotateDays &&
            existing.wallpaper.source == next.wallpaper.source &&
            existing.wallpaper.intervalMs == next.wallpaper.intervalMs &&
            existing.wallpaper.items.map { it.key } == next.wallpaper.items.map { it.key } &&
            existing.quizIntro?.key == next.quizIntro?.key &&
            existing.audio?.key == next.audio?.key &&
            existing.quizAudio?.key == next.quizAudio?.key &&
            existing.quizAudioEnabled == next.quizAudioEnabled &&
            existing.schema == next.schema &&
            existing.source.quizIntroItemId == next.source.quizIntroItemId

    private fun deleteNewFiles(
        existing: HostedManifest?,
        files: List<File>,
        extraProtected: Set<String> = emptySet(),
    ) {
        if (files.isEmpty()) return
        val referenced = HashSet<String>(extraProtected)
        existing?.let { manifest ->
            manifest.wallpaper.items.forEach { entry ->
                entry.file.let { referenced += it }
                entry.poster?.let { referenced += it }
            }
            manifest.quizIntro?.let { entry ->
                referenced += entry.file
                entry.poster?.let { referenced += it }
            }
            manifest.audio?.let { entry -> referenced += entry.file }
            manifest.quizAudio?.let { entry -> referenced += entry.file }
        }
        files.forEach { file ->
            val relative = HostedMediaStore.relativeFileName(file)
            if (relative !in referenced && file.isFile && !file.delete()) {
                TvLog.w(TAG, "unable to delete abandoned file name=${file.name}")
            }
        }
    }

    /**
     * One GET with a hard byte ceiling. The response is written to a temporary file; the
     * content-addressed rename is separated so assets-first downloads can stage outside `files/`.
     */
    private fun download(
        context: Context,
        member: Long,
        item: HostedMediaPlanItem,
        legacyQuizLimit: Boolean,
    ): Downloaded {
        val temp = HostedMediaStore.newTempFile(context, member)
        // V56: an official download or a remote upload that the TV already holds is copied from
        // its verified device cache instead of fetching the same bytes twice. A checksum
        // mismatch is logged and the authoritative source URL is fetched, never trusted blindly.
        var fetched = cachedSource(context, item)?.let { (source, sha) ->
            copyVerified(source, sha, temp, "media ${item.key}")
        }
        if (fetched == null) {
            temp.delete()
            fetched = fetchToTemp(context, item, legacyQuizLimit, temp)
        }
        val target = File(HostedMediaStore.filesDir(context, member), "${fetched.sha}.${item.ext}")
        if (target.isFile && target.length() == fetched.total) {
            temp.delete()
            return Downloaded(target, created = false)
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return Downloaded(target, created = true)
    }

    /**
     * The device-level cache file for one plan item, with the SHA-256 it was verified against.
     * Official entries must still match the current catalogue; custom entries must match the
     * latest signed library version. Anything else is a cache miss, not a downgrade.
     */
    private fun cachedSource(context: Context, item: HostedMediaPlanItem): Pair<File, String>? {
        val officialId = item.officialId
        if (officialId != null) {
            val catalogItem = OfficialMediaCatalog.find(context, officialId) ?: return null
            if (catalogItem.kind != item.kind) return null
            val local = OfficialMediaStore.read(context, officialId) ?: return null
            if (local.item.sha256 != catalogItem.sha256) return null
            if (item.sizeBytes != null && local.item.bytes != item.sizeBytes) return null
            return local.file to local.item.sha256
        }
        if (!item.id.startsWith("custom:")) return null
        val customId = CustomMediaStore.idOfKey(item.id) ?: return null
        val kind = when (item.kind) {
            PolicyMediaKind.VIDEO -> CustomMediaKind.VIDEO
            PolicyMediaKind.IMAGE -> CustomMediaKind.IMAGE
        }
        val local = CustomMediaStore.read(context, kind, customId) ?: return null
        val remote = CustomMediaLibraryClient.latest(kind, customId) ?: return null
        if (local.sha256 != remote.sha256) return null
        if (!local.ext.equals(item.ext, ignoreCase = true)) return null
        if (item.sizeBytes != null && local.sizeBytes != item.sizeBytes) return null
        return local.file to local.sha256
    }

    /** Copies [source] into [temp], verifying [expectedSha]; a mismatch leaves no temp file. */
    private fun copyVerified(
        source: File,
        expectedSha: String,
        temp: File,
        label: String,
    ): Fetched? {
        if (!source.isFile || source.length() <= 0L) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            source.inputStream().use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        total += read
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (!sha.equals(expectedSha, ignoreCase = true)) {
                temp.delete()
                TvLog.w(TAG, "cached $label checksum mismatch; fetching the source")
                return null
            }
            TvLog.i(TAG, "reused device cache for $label bytes=$total")
            Fetched(total, sha)
        } catch (error: IOException) {
            temp.delete()
            TvLog.w(TAG, "could not reuse cached $label; fetching the source", error)
            null
        }
    }

    /**
     * Downloads one item into [temp] and returns its size and SHA-256. The caller owns [temp]:
     * on failure this method deletes it, and on success the caller either renames it or deletes
     * it when the content-addressed target already exists.
     */
    private fun fetchToTemp(
        context: Context,
        item: HostedMediaPlanItem,
        legacyQuizLimit: Boolean,
        temp: File,
    ): Fetched {
        val cap = when {
            legacyQuizLimit -> PolicyVideoValidation.MAX_QUIZ_FILE_BYTES
            item.kind == PolicyMediaKind.IMAGE -> PolicyImageValidation.MAX_STORED_BYTES
            else -> PolicyVideoValidation.MAX_WALLPAPER_FILE_BYTES
        }
        val capError = if (legacyQuizLimit) {
            HostedMediaError.QUIZ_TOO_LARGE
        } else {
            HostedMediaError.ITEM_INVALID
        }
        return fetchToTemp(context, item.url, item.sizeBytes, cap, capError, temp)
    }

    /** V54: a background track has its own byte ceiling and no image/video metadata. */
    private fun fetchAudioToTemp(
        context: Context,
        audio: HostedAudioPlan,
        temp: File,
    ): Fetched = fetchToTemp(
        context,
        audio.url,
        audio.sizeBytes,
        PolicyAudioLibrary.MAX_AUDIO_BYTES,
        HostedMediaError.ITEM_INVALID,
        temp,
    )

    private fun fetchToTemp(
        context: Context,
        url: String,
        sizeBytes: Long?,
        cap: Long,
        capError: HostedMediaError,
        temp: File,
    ): Fetched {
        if (sizeBytes != null && sizeBytes > cap) {
            throw SyncFailure(capError, "declared size above cap")
        }
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                LegacyUpdateTls.configure(context, this)
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Accept", "*/*")
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw SyncFailure(HostedMediaError.NETWORK, "http $code")
            }
            val declared = connection.getHeaderField("Content-Length")?.toLongOrNull()
            if (declared != null && declared > cap) {
                throw SyncFailure(capError, "content length above cap")
            }
            if (sizeBytes != null && declared != null && declared != sizeBytes) {
                throw SyncFailure(HostedMediaError.ITEM_INVALID, "declared size mismatch")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        total += read
                        if (total > cap) {
                            throw SyncFailure(capError, "stream above cap")
                        }
                        if (sizeBytes != null && total > sizeBytes) {
                            throw SyncFailure(HostedMediaError.ITEM_INVALID, "stream above declared size")
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                }
            }
            if (total <= 0L) throw SyncFailure(HostedMediaError.ITEM_INVALID, "empty body")
            if (sizeBytes != null && total != sizeBytes) {
                throw SyncFailure(HostedMediaError.ITEM_INVALID, "declared size mismatch")
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            return Fetched(total, sha)
        } catch (failure: SyncFailure) {
            temp.delete()
            throw failure
        } catch (error: IOException) {
            temp.delete()
            throw SyncFailure(HostedMediaError.NETWORK, error.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * V54: downloads and verifies the one selected background track. The manifest stores only
     * the verified local file; a checksum or decode failure keeps the previous music.
     */
    private fun downloadAudio(
        context: Context,
        member: Long,
        audio: HostedAudioPlan,
        createdFiles: MutableList<File>,
    ): HostedAudio {
        val temp = HostedMediaStore.newTempFile(context, member)
        try {
            // V56: the official audio library and the device upload cache are the same bytes
            // the remote page downloaded; reuse them instead of a second network fetch.
            var fetched = cachedAudioSource(context, audio)?.let { source ->
                copyVerified(source, audio.sha256, temp, "audio ${audio.key}")
            }
            if (fetched == null) {
                temp.delete()
                fetched = fetchAudioToTemp(context, audio, temp)
            }
            if (!fetched.sha.equals(audio.sha256, ignoreCase = true) ||
                fetched.total != audio.sizeBytes
            ) {
                throw SyncFailure(HostedMediaError.ITEM_INVALID, "audio checksum mismatch")
            }
            if (!PolicyAudioLibrary.looksLikeAudio(temp)) {
                throw SyncFailure(HostedMediaError.ITEM_INVALID, "audio format")
            }
            val duration = when (val probe = PolicyAudioLibrary.probe(temp)) {
                is PolicyAudioLibrary.ProbeResult.Rejected ->
                    throw SyncFailure(HostedMediaError.ITEM_INVALID, probe.code)
                is PolicyAudioLibrary.ProbeResult.Ok -> probe.durationMs
            }
            val target = File(
                HostedMediaStore.filesDir(context, member),
                "${fetched.sha}.${audio.ext}",
            )
            if (target.isFile && target.length() == fetched.total) {
                temp.delete()
            } else {
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                createdFiles += target
            }
            return HostedAudio(
                key = audio.key,
                id = audio.id,
                source = audio.source,
                name = audio.name,
                nameEn = audio.nameEn,
                artist = audio.artist,
                license = audio.license,
                file = HostedMediaStore.relativeFileName(target),
                sha256 = audio.sha256,
                sizeBytes = target.length(),
                durationMs = duration,
                ext = audio.ext,
                version = audio.version,
                createdAtMs = System.currentTimeMillis(),
            )
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /** A track already verified by the official library or the upload cache, if any. */
    private fun cachedAudioSource(context: Context, audio: HostedAudioPlan): File? {
        return when (audio.source) {
            "official" -> {
                val local = OfficialAudioStore.read(context, audio.id) ?: return null
                if (!local.track.sha256.equals(audio.sha256, ignoreCase = true)) return null
                if (local.file.length() != audio.sizeBytes) return null
                local.file
            }
            "custom" -> {
                val id = CustomMediaStore.idOfKey(audio.id) ?: return null
                val local = CustomMediaStore.read(context, CustomMediaKind.AUDIO, id) ?: return null
                if (!local.sha256.equals(audio.sha256, ignoreCase = true)) return null
                if (local.sizeBytes != audio.sizeBytes) return null
                local.file
            }
            else -> null
        }
    }

    private fun writePoster(context: Context, member: Long, video: File): Poster {
        // Same content-addressed stem as the film: one cover per film inside this member's cache.
        val name = "${video.nameWithoutExtension}.jpg"
        val target = File(HostedMediaStore.filesDir(context, member), name)
        if (target.isFile && target.length() > 0L) {
            return Poster(HostedMediaStore.relativeFileName(target), created = false, file = target)
        }
        val temp = HostedMediaStore.newTempFile(context, member)
        return try {
            val written = FileOutputStream(temp).use { output ->
                PolicyVideoValidation.writePoster(
                    video,
                    PolicyVideoValidation.POSTER_AT_MS,
                    output,
                )
            }
            if (!written || temp.length() <= 0L) {
                temp.delete()
                // A cover is an optimisation: the child surfaces already draw the default
                // poster while the first frame decodes. This is not an execution fallback.
                TvLog.w(TAG, "member=$member poster unavailable")
                return Poster(null, created = false, file = null)
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            Poster(HostedMediaStore.relativeFileName(target), created = true, file = target)
        } catch (error: IOException) {
            temp.delete()
            TvLog.w(TAG, "member=$member poster write failed", error)
            Poster(null, created = false, file = null)
        }
    }

    private fun writeStatus(
        context: Context,
        result: String,
        code: HostedMediaError?,
        memberId: Long?,
        reason: String,
        items: Int,
        quizIntroId: Long?,
    ) {
        HostedMediaStore.writeStatus(
            context,
            HostedMediaStatus(
                result = result,
                code = code?.wire,
                memberId = memberId,
                reason = reason,
                atMs = System.currentTimeMillis(),
                items = items,
                quizIntroId = quizIntroId,
                bytes = memberId?.let { HostedMediaStore.memberBytes(context, it) } ?: 0L,
            ),
        )
    }

    private class Fetched(val total: Long, val sha: String)

    /** Unbind/mode reset invalidated an in-flight preload; the job stops without reporting. */
    private class PreloadAborted : Exception()

    /** Full items snapshot reported to the backend after every completed item. */
    private class PreloadProgress(keys: List<String>) {
        private val states = LinkedHashMap<String, String>()
        private val errors = HashMap<String, String>()
        private val ready = ArrayList<HostedMediaItem>()

        init {
            keys.forEach { states[it] = PRELOAD_STATE_PREPARING }
        }

        fun markReady(key: String, entry: HostedMediaItem) {
            states[key] = PRELOAD_STATE_READY
            errors.remove(key)
            ready.removeAll { it.key == key }
            ready += entry
        }

        fun markFailed(key: String, error: HostedMediaError) {
            if (states[key] == PRELOAD_STATE_READY) return
            states[key] = PRELOAD_STATE_FAILED
            errors[key] = error.wire
        }

        fun readyEntries(): List<HostedMediaItem> = ready.toList()

        fun failedError(): HostedMediaError? {
            val wire = errors.values.firstOrNull() ?: return null
            return HostedMediaError.entries.firstOrNull { it.wire == wire }
        }

        fun toJson(): JSONArray {
            val array = JSONArray()
            states.forEach { (key, state) ->
                array.put(
                    JSONObject()
                        .put("key", key)
                        .put("state", state)
                        .put("error", errors[key] ?: JSONObject.NULL),
                )
            }
            return array
        }
    }

    private class Downloaded(val file: File, val created: Boolean)

    private class Poster(val relative: String?, val created: Boolean, val file: File?)

    private class SyncFailure(
        val error: HostedMediaError,
        val detail: String,
    ) : IOException("$error: $detail")
}