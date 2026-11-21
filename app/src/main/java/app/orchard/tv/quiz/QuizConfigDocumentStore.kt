package app.orchard.tv.quiz

import android.util.Log
import androidx.annotation.VisibleForTesting
import app.orchard.tv.policy.FamilyHub
import com.planyourtv.policy.PolicyRuntime
import com.planyourtv.policy.TvStore
import com.planyourtv.policy.quiz.QUIZ_DEFAULT_ANSWER_DISPLAY_SEC
import com.planyourtv.policy.quiz.QuizConfig
import com.planyourtv.policy.quiz.QuizConfigDocument
import com.planyourtv.policy.quiz.QuizConfigDocumentCodec
import com.planyourtv.policy.quiz.QuizConfigDocumentValidator
import com.planyourtv.policy.quiz.QuizDocDecodeResult
import com.planyourtv.policy.quiz.QuizDocIssue
import com.planyourtv.policy.quiz.QuizDocUpdatedBy
import com.planyourtv.policy.quiz.QuizLevel
import com.planyourtv.policy.quiz.QuizLevelPackage
import com.planyourtv.policy.quiz.QuizPackageSource
import com.planyourtv.policy.quiz.QuizSessionCodec
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Outcome of loading (or first-time creating) the local QCD. */
sealed interface QuizDocLoadResult {
    data class Ok(
        val document: QuizConfigDocument,
        val migrated: Boolean,
    ) : QuizDocLoadResult

    /** The stored document cannot be trusted. The caller must not fall back to the old keys. */
    data class Unreadable(val errors: List<String>) : QuizDocLoadResult

    data object Storage : QuizDocLoadResult
}

/** Outcome of a write. Only [Ok] changed the stored document. */
sealed interface QuizDocSaveResult {
    data class Ok(val document: QuizConfigDocument) : QuizDocSaveResult

    /** [current] is null when the stored document is unreadable. */
    data class Conflict(val current: QuizConfigDocument?) : QuizDocSaveResult

    data class Invalid(val issues: List<QuizDocIssue>) : QuizDocSaveResult

    data object Storage : QuizDocSaveResult
}

/**
 * 一份云端孩子级 QCD 及同步元数据。
 *
 * `document` 是可执行的完整 QCD；`checkin`/`levelStatus` 是每天都会变化的运行时状态，
 * 故意不写进文档（否则 etag 会随“今天有没有题”抖动）。`rulesPart`/`rulesHttpEtag`
 * 用于下次 GET 的 If-None-Match。
 */
data class CloudQuizDocument(
    val document: QuizConfigDocument,
    val fetchedAt: Long,
    val qbRevision: String,
    val levelsSource: String,
    val checkin: JSONObject,
    val levelStatus: JSONObject,
    val rulesHttpEtag: String? = null,
    val rulesPart: JSONObject? = null,
)

/**
 * The cloud envelope that owns `quiz.doc.active`, plus the member it belongs to.
 * The planner needs the envelope's `levelStatus`/`checkin`, which are deliberately
 * kept out of the executable document.
 */
data class ActiveCloudQuizDocument(
    val learnerId: Long,
    val cloud: CloudQuizDocument,
)

/** 绑定态的云端同步状态；只存放状态码，不存放文档内容或服务器内部信息。 */
data class QuizDocSyncState(
    val state: String,
    val pendingMutationId: String? = null,
    val baseRevision: Long? = null,
    val lastError: String? = null,
    val lastSyncAt: Long? = null,
)

/**
 * The single owner of the local quiz configuration document.
 *
 * Phase 1 keeps one document in `quiz.doc.local`; the old `quiz.challengeConfig`
 * and `quiz.answerScopes` keys are read once during migration and are never
 * rewritten afterwards. A document that fails decoding or validation stays
 * active as an explicit error: silently falling back to the old keys would let
 * a corrupt write behave like a valid but different configuration.
 */
object QuizConfigDocumentStore {
    private val core = QuizConfigDocumentCore(
        encrypt = QuizCrypto::encrypt,
        decrypt = QuizCrypto::decrypt,
    )

    fun ensureLocalDocument(nowMs: Long = System.currentTimeMillis()): QuizDocLoadResult =
        core.ensureLocalDocument(nowMs)

    /**
     * Creates the initial local document for [targetUserId] while another member is still
     * active. With a [sourceUserId] the source child's document is cloned (its level
     * structure and quiz settings, never any progress); without one a clean default document
     * is written so the new child cannot inherit the first child's legacy settings implicitly.
     */
    fun provisionLocalDocument(
        targetUserId: String,
        sourceUserId: String? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = core.provisionLocalDocument(targetUserId, sourceUserId, nowMs)

    fun localDocument(): QuizConfigDocument? = core.localDocument()

    /**
     * V41: reads another member's local QCD without moving the TV's active
     * pointer. Returns [QuizDocLoadResult.Unreadable] when the stored document
     * is corrupt; the caller must not silently substitute the active child's
     * document.
     */
    fun localDocumentFor(userId: String): QuizDocLoadResult = core.localDocumentFor(userId)

    /** Writes another member's local QCD; the active pointer is not touched. */
    fun saveLocalFor(
        userId: String,
        document: QuizConfigDocument,
        baseRevision: Long?,
        updatedBy: QuizDocUpdatedBy = QuizDocUpdatedBy.TV_WEB,
        nowMs: Long = System.currentTimeMillis(),
    ): QuizDocSaveResult = core.saveLocalFor(
        userId,
        document,
        baseRevision,
        updatedBy,
        nowMs,
    )

    /** Replaces another member's level-package extension. */
    fun updateLevelPackagesFor(
        userId: String,
        packages: List<QuizLevelPackage>,
        nowMs: Long = System.currentTimeMillis(),
    ): QuizDocSaveResult = core.updateLevelPackagesFor(userId, packages, nowMs)

    /**
     * 当前执行文档：有 `quiz.doc.active` 时读它；没有时回退到本机文档（未绑定态）。
     * active 键存在但无法解码时返回 null，**绝不**静默改用 local——那会把损坏伪装成
     * 另一份有效配置。
     */
    fun activeDocument(): QuizConfigDocument? = core.activeDocument()

    /**
     * The cloud envelope behind the active document. Cloud rounds need its
     * `levelStatus` (per-level availability) and `checkin` gate, which are not
     * part of the executable QCD itself. Returns null when the active document
     * is local, missing, or has no matching cached envelope.
     */
    fun activeCloudDocument(): ActiveCloudQuizDocument? = core.activeCloudDocument()

    fun cloudDocument(learnerId: Long): CloudQuizDocument? = core.cloudDocument(learnerId)

    fun saveCloudDocument(learnerId: Long, cloud: CloudQuizDocument): Boolean =
        core.saveCloudDocument(learnerId, cloud)

    /** 先写云缓存，再原子切换 active；任一步失败都不改变当前执行文档。 */
    fun activateCloud(learnerId: Long, cloud: CloudQuizDocument): Boolean =
        core.activateCloud(learnerId, cloud)

    fun clearActiveDocument(): Boolean = core.clearActiveDocument()

    fun clearCloudDocuments(): Boolean = core.clearCloudDocuments()

    fun cloudLearnerIds(): List<Long> = core.cloudLearnerIds()

    /** 只清同步状态码，不动本机/云端文档；换绑或解绑时用。 */
    fun clearSyncStates(): Boolean = core.clearSyncStates()

    fun syncState(learnerId: Long): QuizDocSyncState? = core.syncState(learnerId)

    fun saveSyncState(learnerId: Long, state: QuizDocSyncState): Boolean =
        core.saveSyncState(learnerId, state)

    fun saveLocal(
        document: QuizConfigDocument,
        baseRevision: Long?,
        updatedBy: QuizDocUpdatedBy = QuizDocUpdatedBy.TV_LOCAL_EDIT,
        nowMs: Long = System.currentTimeMillis(),
    ): QuizDocSaveResult = core.saveLocal(document, baseRevision, updatedBy, nowMs)

    fun updateLevels(
        levels: List<QuizLevel>,
        nowMs: Long = System.currentTimeMillis(),
    ): QuizDocSaveResult = core.updateLevels(levels, nowMs)

    /** Writes the local package extension without touching the legacy levels. */
    fun updateLevelPackages(
        packages: List<QuizLevelPackage>,
        nowMs: Long = System.currentTimeMillis(),
    ): QuizDocSaveResult = core.updateLevelPackages(packages, nowMs)

    fun markEnabled(
        enabled: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): QuizDocSaveResult = core.markEnabled(enabled, nowMs)

    @VisibleForTesting
    internal fun core(encrypt: (String) -> String?, decrypt: (String) -> String?): QuizConfigDocumentCore =
        QuizConfigDocumentCore(encrypt, decrypt)
}

internal class QuizConfigDocumentCore(
    private val encrypt: (String) -> String?,
    private val decrypt: (String) -> String?,
) {
    private val lock = Any()

    /** Local quiz documents are per child; the default member keeps the legacy key names. */
    private fun key(base: String): String = QuizSubjectScope.key(base)

    fun ensureLocalDocument(nowMs: Long = System.currentTimeMillis()): QuizDocLoadResult =
        synchronized(lock) {
            if (!TvStore.isReady) return QuizDocLoadResult.Storage
            when (val stored = readStored()) {
                is Stored.Present -> {
                    clearDiagnostic()
                    QuizDocLoadResult.Ok(stored.document, migrated = false)
                }
                Stored.Absent -> migrate(nowMs)
                is Stored.Broken -> {
                    diagnose("UNREADABLE", stored.errors, nowMs)
                    QuizDocLoadResult.Unreadable(stored.errors)
                }
            }
        }

    fun localDocument(): QuizConfigDocument? =
        (ensureLocalDocument() as? QuizDocLoadResult.Ok)?.document

    /**
     * Writes the target child's first document under its own key. The raw clone path keeps
     * the encrypted envelope unchanged; a missing source falls back to a valid default
     * document rather than letting the one-time legacy migration copy the first child.
     */
    fun provisionLocalDocument(
        targetUserId: String,
        sourceUserId: String?,
        nowMs: Long,
    ): Boolean = synchronized(lock) {
        if (!TvStore.isReady || targetUserId.isBlank()) return false
        val family = FamilyHub.state.value
        if (!family.ready) return false
        val targetKey = QuizSubjectScope.keyFor(family, targetUserId, KEY_LOCAL)
        if (TvStore.getString(targetKey) != null) return true
        val source = sourceUserId?.trim()?.takeIf { it.isNotEmpty() && it != targetUserId }
        if (source != null) {
            val raw = TvStore.getString(QuizSubjectScope.keyFor(family, source, KEY_LOCAL))
            // The clone carries levels and quiz settings, never the source
            // child's private tags. Packages that reference a private tag are
            // removed; course and wrong-review levels stay.
            val stripped = raw?.let { stripPrivateTagPackages(it) }
            if (stripped != null) return TvStore.putStringDurable(targetKey, stripped)
        }
        val document = QuizLocalConfigDocument.create(
            installId = installId(),
            nowMs = nowMs,
            config = QuizConfig(),
            legacyAnswerDisplaySec = QUIZ_DEFAULT_ANSWER_DISPLAY_SEC,
            levels = emptyList(),
            migrated = false,
        )
        val issues = QuizLocalConfigDocument.localWriteIssues(document)
        if (issues.isNotEmpty()) {
            Log.e(TAG, "default quiz document is invalid: ${issues.map { it.code }}")
            return false
        }
        val encrypted = encrypt(QuizConfigDocumentCodec.encode(document)) ?: return false
        TvStore.putStringDurable(targetKey, encrypted)
    }

    /**
     * Re-encodes a source document after removing every package that draws
     * from a private tag. Content is never copied to the new member, so a
     * cloned tag level would only ever show an empty bank.
     */
    private fun stripPrivateTagPackages(raw: String): String? {
        val json = decrypt(raw) ?: return null
        val decoded = QuizConfigDocumentCodec.decode(json)
        if (decoded !is QuizDocDecodeResult.Success) return null
        var document = decoded.document
        val extension = QuizLevelPackageStore.read(document)
        if (extension is QuizLevelPackageStore.Extension.Present) {
            val kept = extension.packages.filterNot { pkg ->
                pkg.sources.any { it is QuizPackageSource.Collection }
            }
            if (kept.size != extension.packages.size) {
                document = QuizLevelPackageStore.withPackages(document, kept)
            }
        }
        return encrypt(QuizConfigDocumentCodec.encode(document))
    }

    /**
     * Reads one member's local document by id. Unlike [ensureLocalDocument]
     * this never creates or migrates a document: the active child's legacy
     * migration must not be copied into a sibling.
     */
    fun localDocumentFor(userId: String): QuizDocLoadResult = synchronized(lock) {
        if (!TvStore.isReady) return QuizDocLoadResult.Storage
        val localKey = localKeyFor(userId) ?: return QuizDocLoadResult.Storage
        when (val stored = readStored(localKey)) {
            is Stored.Present -> QuizDocLoadResult.Ok(stored.document, migrated = false)
            Stored.Absent -> QuizDocLoadResult.Unreadable(listOf("quiz.doc.local:ABSENT"))
            is Stored.Broken -> QuizDocLoadResult.Unreadable(stored.errors)
        }
    }

    fun saveLocalFor(
        userId: String,
        document: QuizConfigDocument,
        baseRevision: Long?,
        updatedBy: QuizDocUpdatedBy,
        nowMs: Long,
    ): QuizDocSaveResult = synchronized(lock) {
        val localKey = localKeyFor(userId) ?: return QuizDocSaveResult.Storage
        saveLocalAt(localKey, document, baseRevision, updatedBy, nowMs)
    }

    fun updateLevelPackagesFor(
        userId: String,
        packages: List<QuizLevelPackage>,
        nowMs: Long,
    ): QuizDocSaveResult = synchronized(lock) {
        val localKey = localKeyFor(userId) ?: return QuizDocSaveResult.Storage
        val current = when (val stored = readStored(localKey)) {
            is Stored.Present -> stored.document
            else -> return QuizDocSaveResult.Storage
        }
        val updated = QuizLevelPackageStore.withPackages(current, packages)
        saveLocalAt(
            localKey,
            updated,
            current.revision,
            QuizDocUpdatedBy.TV_LOCAL_EDIT,
            nowMs,
        )
    }

    private fun localKeyFor(userId: String): String? {
        val id = userId.trim()
        if (id.isEmpty()) return null
        val family = FamilyHub.state.value
        if (!family.ready) return null
        return QuizSubjectScope.keyFor(family, id, KEY_LOCAL)
    }

    fun activeDocument(): QuizConfigDocument? = synchronized(lock) {
        if (!TvStore.isReady) return null
        if (TvStore.getString(key(KEY_ACTIVE)) == null) {
            // 未绑定：本机文档就是执行文档。已绑定却没有 active（首次同步前或存储失败）
            // 时不能静默改用 local——那会让家长以为云端配置生效。
            if (PolicyRuntime.state.value.bound) {
                Log.w(TAG, "bound device has no active quiz document; local fallback refused")
                return null
            }
            return localDocument()
        }
        readActiveDocumentLocked()
    }

    fun activeCloudDocument(): ActiveCloudQuizDocument? = synchronized(lock) {
        if (!TvStore.isReady || !PolicyRuntime.state.value.bound) return null
        val active = readActiveDocumentLocked() ?: return null
        // 文档身份用 documentId + revision：缓存可能先于 active 切换写入了更新的
        // revision，此时信封的 levelStatus 与仍在执行的文档不对应，必须拒绝。
        fun matches(cached: CloudQuizDocument): Boolean =
            cached.document.documentId == active.documentId &&
                cached.document.revision == active.revision
        val recorded = TvStore.getString(key(KEY_ACTIVE_LEARNER))?.trim()?.toLongOrNull()
        if (recorded != null && recorded > 0L) {
            val cached = cloudDocument(recorded)
            if (cached != null && matches(cached)) {
                return ActiveCloudQuizDocument(recorded, cached)
            }
        }
        // 旧版本没有记录 active learner：按文档身份在缓存里认领最近抓取的一份。
        var best: ActiveCloudQuizDocument? = null
        for (learnerId in cloudLearnerIdsLocked()) {
            val cached = cloudDocument(learnerId) ?: continue
            if (!matches(cached)) continue
            val currentBest = best
            if (currentBest == null || cached.fetchedAt > currentBest.cloud.fetchedAt) {
                best = ActiveCloudQuizDocument(learnerId, cached)
            }
        }
        val found = best ?: run {
            Log.e(TAG, "active cloud document has no cached envelope")
            return null
        }
        TvStore.putStringDurable(key(KEY_ACTIVE_LEARNER), found.learnerId.toString())
        found
    }

    fun cloudDocument(learnerId: Long): CloudQuizDocument? = synchronized(lock) {
        if (!TvStore.isReady || learnerId <= 0L) return null
        val raw = TvStore.getString(cloudKey(learnerId)) ?: return null
        val json = decrypt(raw)
        if (json == null) {
            Log.e(TAG, "cloud quiz document cannot be decrypted: learner=$learnerId")
            return null
        }
        decodeCloudEnvelope(json)
    }

    fun saveCloudDocument(learnerId: Long, cloud: CloudQuizDocument): Boolean =
        synchronized(lock) {
            if (!TvStore.isReady || learnerId <= 0L) return false
            writeCloudDocument(learnerId, cloud)
        }

    fun activateCloud(learnerId: Long, cloud: CloudQuizDocument): Boolean =
        synchronized(lock) {
            if (!TvStore.isReady || learnerId <= 0L) return false
            if (!writeCloudDocument(learnerId, cloud)) return false
            if (!writeActive(cloud.document)) {
                Log.e(TAG, "failed to switch the active quiz document; keeping the previous one")
                return false
            }
            if (!TvStore.putStringDurable(key(KEY_ACTIVE_LEARNER), learnerId.toString())) {
                // The identity is recoverable from the cached envelopes by documentId.
                Log.w(TAG, "failed to record the active quiz learner; will re-derive it")
            }
            true
        }

    fun clearActiveDocument(): Boolean = synchronized(lock) {
        if (!TvStore.isReady) return false
        val removed = TvStore.removeDurable(key(KEY_ACTIVE))
        val learnerRemoved = TvStore.removeDurable(key(KEY_ACTIVE_LEARNER))
        removed && learnerRemoved
    }

    fun clearCloudDocuments(): Boolean = synchronized(lock) {
        if (!TvStore.isReady) return false
        var ok = true
        cloudLearnerIdsLocked().forEach { learnerId ->
            ok = TvStore.removeDurable(cloudKey(learnerId)) && ok
        }
        ok = TvStore.removeDurable(key(KEY_CLOUD_INDEX)) && ok
        ok
    }

    fun cloudLearnerIds(): List<Long> = synchronized(lock) {
        if (!TvStore.isReady) emptyList() else cloudLearnerIdsLocked()
    }

    fun clearSyncStates(): Boolean = synchronized(lock) {
        if (!TvStore.isReady) return false
        var ok = true
        syncLearnerIdsLocked().forEach { learnerId ->
            ok = TvStore.removeDurable(syncKey(learnerId)) && ok
        }
        ok = TvStore.removeDurable(key(KEY_SYNC_INDEX)) && ok
        ok
    }

    fun syncState(learnerId: Long): QuizDocSyncState? = synchronized(lock) {
        if (!TvStore.isReady || learnerId <= 0L) return null
        val raw = TvStore.getString(syncKey(learnerId)) ?: return null
        try {
            val json = JSONObject(raw)
            QuizDocSyncState(
                state = json.optString("state", "").trim().ifEmpty { return null },
                pendingMutationId = json.optString("pendingMutationId", "")
                    .trim().ifEmpty { null },
                baseRevision = if (json.has("baseRevision") && !json.isNull("baseRevision")) {
                    json.optLong("baseRevision", 0L)
                } else {
                    null
                },
                lastError = json.optString("lastError", "").trim().ifEmpty { null },
                lastSyncAt = if (json.has("lastSyncAt") && !json.isNull("lastSyncAt")) {
                    json.optLong("lastSyncAt", 0L)
                } else {
                    null
                },
            )
        } catch (error: Exception) {
            Log.e(TAG, "sync state is corrupt: learner=$learnerId", error)
            null
        }
    }

    fun saveSyncState(learnerId: Long, state: QuizDocSyncState): Boolean =
        synchronized(lock) {
            if (!TvStore.isReady || learnerId <= 0L) return false
            // 先记索引再写状态：索引写失败时状态键不会被孤立（TvStore 不支持键枚举）。
            if (!touchSyncIndex(learnerId)) return false
            val json = JSONObject()
                .put("state", state.state)
                .put("pendingMutationId", state.pendingMutationId ?: JSONObject.NULL)
                .put("baseRevision", state.baseRevision ?: JSONObject.NULL)
                .put("lastError", state.lastError ?: JSONObject.NULL)
                .put("lastSyncAt", state.lastSyncAt ?: JSONObject.NULL)
            TvStore.putStringDurable(syncKey(learnerId), json.toString())
        }

    private fun writeCloudDocument(learnerId: Long, cloud: CloudQuizDocument): Boolean {
        val envelope = JSONObject()
            .put("fetchedAt", cloud.fetchedAt)
            .put("qbRevision", cloud.qbRevision)
            .put("levelsSource", cloud.levelsSource)
            .put("checkin", cloud.checkin)
            .put("levelStatus", cloud.levelStatus)
            .put("rulesHttpEtag", cloud.rulesHttpEtag ?: JSONObject.NULL)
            .put("rulesPart", cloud.rulesPart ?: JSONObject.NULL)
            .put("document", QuizConfigDocumentCodec.encodeDocument(cloud.document))
        val encrypted = encrypt(envelope.toString()) ?: return false
        if (!TvStore.putStringDurable(cloudKey(learnerId), encrypted)) return false
        return touchCloudIndex(learnerId)
    }

    private fun decodeCloudEnvelope(raw: String): CloudQuizDocument? {
        val json = try {
            JSONObject(raw)
        } catch (error: Exception) {
            Log.e(TAG, "cloud quiz envelope is invalid JSON", error)
            return null
        }
        val documentJson = json.optJSONObject("document")
            ?: run {
                Log.e(TAG, "cloud quiz envelope missing document")
                return null
            }
        val document = when (val decoded = QuizConfigDocumentCodec.decode(documentJson)) {
            is QuizDocDecodeResult.Success -> decoded.document
            is QuizDocDecodeResult.Failure -> {
                Log.e(TAG, "cloud quiz document is corrupt: ${decoded.issues}")
                return null
            }
        }
        if (document.origin != com.planyourtv.policy.quiz.QuizDocOrigin.CLOUD) {
            Log.e(TAG, "cloud quiz document has a non-cloud origin: ${document.origin}")
            return null
        }
        val issues = QuizConfigDocumentValidator.validate(document)
        if (issues.isNotEmpty()) {
            Log.e(TAG, "cloud quiz document is invalid: $issues")
            return null
        }
        val checkin = json.optJSONObject("checkin")
        val levelStatus = json.optJSONObject("levelStatus")
        if (checkin == null || levelStatus == null) {
            Log.e(TAG, "cloud quiz envelope missing runtime status")
            return null
        }
        return CloudQuizDocument(
            document = document,
            fetchedAt = json.optLong("fetchedAt", 0L),
            qbRevision = json.optString("qbRevision", ""),
            levelsSource = json.optString("levelsSource", ""),
            checkin = checkin,
            levelStatus = levelStatus,
            rulesHttpEtag = json.optString("rulesHttpEtag", "").trim().ifEmpty { null },
            rulesPart = json.optJSONObject("rulesPart"),
        )
    }

    private fun readActiveDocumentLocked(): QuizConfigDocument? {
        val raw = TvStore.getString(key(KEY_ACTIVE)) ?: return null
        val json = decrypt(raw)
        if (json == null) {
            Log.e(TAG, "active quiz document cannot be decrypted; refusing to fall back to local")
            return null
        }
        return when (val decoded = QuizConfigDocumentCodec.decode(json)) {
            is QuizDocDecodeResult.Success -> {
                val issues = QuizConfigDocumentValidator.validate(decoded.document)
                if (issues.isNotEmpty()) {
                    Log.e(TAG, "active quiz document is invalid: $issues")
                    null
                } else {
                    decoded.document
                }
            }
            is QuizDocDecodeResult.Failure -> {
                Log.e(TAG, "active quiz document is corrupt: ${decoded.issues}")
                null
            }
        }
    }

    private fun writeActive(document: QuizConfigDocument): Boolean {
        val encrypted = encrypt(QuizConfigDocumentCodec.encode(document)) ?: return false
        return TvStore.putStringDurable(key(KEY_ACTIVE), encrypted)
    }

    private fun cloudLearnerIdsLocked(): List<Long> {
        val raw = TvStore.getString(key(KEY_CLOUD_INDEX)) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optLong(index, 0L).takeIf { it > 0L }
            }
        } catch (error: Exception) {
            Log.e(TAG, "cloud cache index is corrupt; treating it as empty", error)
            emptyList()
        }
    }

    /** 最近使用的孩子排在最前；最多保留 [CLOUD_CACHE_LIMIT] 份。 */
    private fun touchCloudIndex(learnerId: Long): Boolean {
        val ids = cloudLearnerIdsLocked().filter { it != learnerId }.toMutableList()
        ids.add(0, learnerId)
        val kept = ids.take(CLOUD_CACHE_LIMIT)
        val dropped = ids.drop(CLOUD_CACHE_LIMIT)
        dropped.forEach { old ->
            if (!TvStore.removeDurable(cloudKey(old))) {
                Log.w(TAG, "failed to evict cloud quiz cache: learner=$old")
            }
        }
        val array = JSONArray()
        kept.forEach { array.put(it) }
        return TvStore.putStringDurable(key(KEY_CLOUD_INDEX), array.toString())
    }

    private fun syncLearnerIdsLocked(): List<Long> {
        val raw = TvStore.getString(key(KEY_SYNC_INDEX)) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optLong(index, 0L).takeIf { it > 0L }
            }
        } catch (error: Exception) {
            Log.e(TAG, "sync state index is corrupt; treating it as empty", error)
            emptyList()
        }
    }

    /** 同步状态没有 LRU 语义，只去重并限制总量，避免换绑后索引无限增长。 */
    private fun touchSyncIndex(learnerId: Long): Boolean {
        val ids = syncLearnerIdsLocked().filter { it != learnerId }.toMutableList()
        ids.add(0, learnerId)
        val dropped = ids.drop(SYNC_STATE_LIMIT)
        dropped.forEach { old ->
            if (!TvStore.removeDurable(syncKey(old))) {
                Log.w(TAG, "failed to evict sync state: learner=$old")
            }
        }
        val array = JSONArray()
        ids.take(SYNC_STATE_LIMIT).forEach { array.put(it) }
        return TvStore.putStringDurable(key(KEY_SYNC_INDEX), array.toString())
    }

    private fun cloudKey(learnerId: Long): String =
        QuizSubjectScope.key("$KEY_CLOUD_PREFIX$learnerId")

    private fun syncKey(learnerId: Long): String =
        QuizSubjectScope.key("$KEY_SYNC_PREFIX$learnerId")

    fun saveLocal(
        document: QuizConfigDocument,
        baseRevision: Long?,
        updatedBy: QuizDocUpdatedBy,
        nowMs: Long,
    ): QuizDocSaveResult = synchronized(lock) {
        saveLocalAt(key(KEY_LOCAL), document, baseRevision, updatedBy, nowMs)
    }

    private fun saveLocalAt(
        localKey: String,
        document: QuizConfigDocument,
        baseRevision: Long?,
        updatedBy: QuizDocUpdatedBy,
        nowMs: Long,
    ): QuizDocSaveResult {
        if (!TvStore.isReady) return QuizDocSaveResult.Storage
        val installId = installId()
        if (document.documentId != QuizLocalConfigDocument.documentId(installId)) {
            return QuizDocSaveResult.Invalid(
                listOf(QuizDocIssue("documentId", "LOCAL_DOCUMENT_ID_REQUIRED")),
            )
        }
        val stored = readStored(localKey)
        val current = (stored as? Stored.Present)?.document
        if (baseRevision != null) {
            val currentRevision = current?.revision ?: 0L
            if (current == null || baseRevision != currentRevision) {
                return QuizDocSaveResult.Conflict(current)
            }
        }
        // The server-side revision input is ignored: the TV owns the counter and a
        // client cannot jump ahead by sending a larger number.
        val next = QuizLocalConfigDocument.withContentHashes(
            document.copy(
                revision = (current?.revision ?: 0L) + 1L,
                updatedAt = isoTime(nowMs),
                updatedBy = updatedBy,
            ),
        )
        val issues = QuizLocalConfigDocument.localWriteIssues(next)
        if (issues.isNotEmpty()) return QuizDocSaveResult.Invalid(issues)
        if (!writeLocal(localKey, next)) return QuizDocSaveResult.Storage
        markMigrated()
        clearDiagnostic()
        return QuizDocSaveResult.Ok(next)
    }

    fun updateLevels(
        levels: List<QuizLevel>,
        nowMs: Long,
    ): QuizDocSaveResult = synchronized(lock) {
        val current = (ensureLocalDocument(nowMs) as? QuizDocLoadResult.Ok)?.document
            ?: return QuizDocSaveResult.Storage
        val updated = current.copy(
            levels = levels.mapIndexed { index, level ->
                QuizLocalConfigDocument.levelDoc(level, index)
            },
        )
        writeWithRetry(updated, current.revision, QuizDocUpdatedBy.TV_LOCAL_EDIT, nowMs)
    }

    fun updateLevelPackages(
        packages: List<QuizLevelPackage>,
        nowMs: Long,
    ): QuizDocSaveResult = synchronized(lock) {
        val current = (ensureLocalDocument(nowMs) as? QuizDocLoadResult.Ok)?.document
            ?: return QuizDocSaveResult.Storage
        val updated = QuizLevelPackageStore.withPackages(current, packages)
        writeWithRetry(updated, current.revision, QuizDocUpdatedBy.TV_LOCAL_EDIT, nowMs)
    }

    fun markEnabled(
        enabled: Boolean,
        nowMs: Long,
    ): QuizDocSaveResult = synchronized(lock) {
        val current = (ensureLocalDocument(nowMs) as? QuizDocLoadResult.Ok)?.document
            ?: return QuizDocSaveResult.Storage
        if (current.enabled == enabled) return QuizDocSaveResult.Ok(current)
        writeWithRetry(
            current.copy(enabled = enabled),
            current.revision,
            QuizDocUpdatedBy.TV_LOCAL_EDIT,
            nowMs,
        )
    }

    private fun writeWithRetry(
        document: QuizConfigDocument,
        baseRevision: Long,
        updatedBy: QuizDocUpdatedBy,
        nowMs: Long,
    ): QuizDocSaveResult {
        val first = saveLocal(document, baseRevision, updatedBy, nowMs)
        if (first !is QuizDocSaveResult.Conflict) return first
        val current = first.current ?: return first
        return saveLocal(document, current.revision, updatedBy, nowMs)
    }

    private fun migrate(nowMs: Long): QuizDocLoadResult {
        // Only the device default member may adopt the pre-V40 single-child
        // legacy keys. A later member must start from an empty document; the
        // caller provisions it when the member is added.
        val family = FamilyHub.state.value
        if (family.ready && family.activeUserId != family.defaultUserId) {
            return createCleanDefault(nowMs)
        }
        val configRaw = QuizSessionStore.challengeConfigRawForMigration()
        val hasConfig = QuizSessionStore.hasChallengeConfig()
        val config = when {
            configRaw != null -> QuizSessionCodec.decodeConfig(configRaw)
            hasConfig -> null
            else -> QuizConfig()
        } ?: return migrationInvalid(nowMs)

        val levels = QuizAnswerScopeStore.legacyLevelsForMigration()
            ?: return migrationInvalid(nowMs)
        val hasLegacy = hasConfig ||
            QuizSessionStore.hasAnswerScopes() ||
            QuizSessionStore.hasAnswerScope()
        val installId = installId()
        val document = QuizLocalConfigDocument.create(
            installId = installId,
            nowMs = nowMs,
            config = config,
            legacyAnswerDisplaySec = configRaw?.optInt(
                "answerDisplaySec",
                QUIZ_DEFAULT_ANSWER_DISPLAY_SEC,
            ) ?: QUIZ_DEFAULT_ANSWER_DISPLAY_SEC,
            levels = levels,
            migrated = hasLegacy,
        )
        val issues = QuizLocalConfigDocument.localWriteIssues(document)
        if (issues.isNotEmpty()) {
            Log.e(TAG, "legacy quiz configuration cannot migrate: $issues")
            diagnose("MIGRATION_INVALID", issues.map { it.toString() }, nowMs)
            return QuizDocLoadResult.Unreadable(issues.map { it.toString() })
        }
        if (!writeLocal(key(KEY_LOCAL), document)) return QuizDocLoadResult.Storage
        markMigrated()
        clearDiagnostic()
        Log.i(TAG, "local quiz document created (migrated=$hasLegacy, levels=${document.levels.size})")
        return QuizDocLoadResult.Ok(document, migrated = hasLegacy)
    }

    private fun createCleanDefault(nowMs: Long): QuizDocLoadResult {
        val document = QuizLocalConfigDocument.create(
            installId = installId(),
            nowMs = nowMs,
            config = QuizConfig(),
            legacyAnswerDisplaySec = QUIZ_DEFAULT_ANSWER_DISPLAY_SEC,
            levels = emptyList(),
            migrated = false,
        )
        val issues = QuizLocalConfigDocument.localWriteIssues(document)
        if (issues.isNotEmpty()) {
            Log.e(TAG, "default quiz document is invalid: ${issues.map { it.code }}")
            return QuizDocLoadResult.Unreadable(issues.map { it.toString() })
        }
        if (!writeLocal(key(KEY_LOCAL), document)) return QuizDocLoadResult.Storage
        markMigrated()
        clearDiagnostic()
        return QuizDocLoadResult.Ok(document, migrated = false)
    }

    private fun migrationInvalid(nowMs: Long): QuizDocLoadResult {
        val errors = listOf("legacyConfig:MIGRATION_INVALID")
        Log.e(TAG, "legacy quiz configuration is invalid; keeping it read-only")
        diagnose("MIGRATION_INVALID", errors, nowMs)
        return QuizDocLoadResult.Unreadable(errors)
    }

    private sealed interface Stored {
        data object Absent : Stored
        data class Present(val document: QuizConfigDocument) : Stored
        data class Broken(val errors: List<String>) : Stored
    }

    private fun readStored(localKey: String = key(KEY_LOCAL)): Stored {
        val raw = TvStore.getString(localKey) ?: return Stored.Absent
        val json = decrypt(raw) ?: return Stored.Broken(listOf("quiz.doc.local:DECRYPT"))
        val decoded = QuizConfigDocumentCodec.decode(json)
        if (decoded is QuizDocDecodeResult.Failure) {
            return Stored.Broken(decoded.issues.map { it.toString() })
        }
        val document = (decoded as QuizDocDecodeResult.Success).document
        val expectedId = QuizLocalConfigDocument.documentId(installIdForRead(document))
        if (document.documentId != expectedId) {
            return Stored.Broken(listOf("documentId:INSTALL_ID_MISMATCH"))
        }
        val issues = QuizLocalConfigDocument.localReadIssues(document)
        if (issues.isNotEmpty()) {
            return Stored.Broken(issues.map { it.toString() })
        }
        return Stored.Present(document)
    }

    /**
     * The stored install id wins. A document restored without the id key is
     * adopted so the device keeps its local identity instead of losing it.
     */
    private fun installIdForRead(document: QuizConfigDocument): String {
        storedInstallId()?.let { return it }
        val suffix = document.documentId.removePrefix(QuizLocalConfigDocument.LOCAL_DOCUMENT_PREFIX)
        if (suffix != document.documentId && suffix.isNotBlank()) {
            TvStore.putStringDurable(KEY_INSTALL_ID, suffix)
            return suffix
        }
        return installId()
    }

    private fun installId(): String {
        storedInstallId()?.let { return it }
        val generated = UUID.randomUUID().toString()
        TvStore.putStringDurable(KEY_INSTALL_ID, generated)
        return generated
    }

    private fun storedInstallId(): String? =
        TvStore.getString(KEY_INSTALL_ID)?.trim()?.takeIf { INSTALL_ID_RE.matches(it) }

    private fun writeLocal(localKey: String, document: QuizConfigDocument): Boolean {
        val encrypted = encrypt(QuizConfigDocumentCodec.encode(document)) ?: return false
        return TvStore.putStringDurable(localKey, encrypted)
    }

    private fun migrated(): Boolean = TvStore.getString(KEY_MIGRATED) == "1"

    private fun markMigrated() {
        if (!migrated()) TvStore.putStringDurable(KEY_MIGRATED, "1")
    }

    /** Diagnostics only: never store the document body or decrypted content. */
    private fun diagnose(code: String, errors: List<String>, nowMs: Long) {
        val payload = JSONObject()
            .put("at", nowMs)
            .put("code", code)
            .put("errors", JSONArray(errors.take(MAX_DIAGNOSTIC_ERRORS)))
        TvStore.putStringDurable(KEY_UNREADABLE, payload.toString())
        Log.e(TAG, "local quiz document unusable: code=$code errors=${payload.getJSONArray("errors")}")
    }

    private fun clearDiagnostic() {
        if (TvStore.getString(KEY_UNREADABLE) != null) TvStore.removeDurable(KEY_UNREADABLE)
    }

    private fun isoTime(nowMs: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(nowMs))

    companion object {
        private const val TAG = "QuizDocStore"
        private const val KEY_LOCAL = "quiz.doc.local"
        private const val KEY_ACTIVE = "quiz.doc.active"
        private const val KEY_ACTIVE_LEARNER = "quiz.doc.active.learner"
        private const val KEY_CLOUD_PREFIX = "quiz.doc.cloud."
        private const val KEY_CLOUD_INDEX = "quiz.doc.cloud.index"
        private const val KEY_SYNC_INDEX = "quiz.doc.sync.index"
        private const val KEY_SYNC_PREFIX = "quiz.doc.sync."
        private const val KEY_INSTALL_ID = "quiz.doc.installId"
        private const val KEY_MIGRATED = "quiz.feedbackVoiceHoldMigrated.v1"
        private const val KEY_UNREADABLE = "quiz.doc.unreadable.v1"
        private const val MAX_DIAGNOSTIC_ERRORS = 8
        private const val CLOUD_CACHE_LIMIT = 5
        private const val SYNC_STATE_LIMIT = 20
        private val INSTALL_ID_RE = Regex("^[A-Za-z0-9-]{8,64}$")
    }
}
