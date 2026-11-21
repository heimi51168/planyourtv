package app.orchard.tv.quiz

import android.util.Log
import app.orchard.tv.policy.PolicyModeCoordinator
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.quiz.CourseOrigin
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizCollectionLevel
import com.planyourtv.policy.quiz.QuizConfigDocument
import com.planyourtv.policy.quiz.QuizLevel
import com.planyourtv.policy.quiz.QuizLevelPackage
import com.planyourtv.policy.quiz.QuizPackageSource
import com.planyourtv.policy.quiz.QuizWrongReviewLevel
import org.json.JSONObject

/**
 * The local level list as level packages.
 *
 * Storage lives inside the local QCD's preserved `extensions.local` object, so
 * a package edit and a rules edit share one revision and one etag. The old
 * `levels`, custom-tag and wrong-review keys are left untouched: an older APK
 * can still read them after a downgrade, while this build uses packages.
 */
object QuizLevelPackageStore {
    private const val TAG = "QuizPackageStore"
    private const val EXTENSION_KEY = "levelPackages"
    private const val EXTENSION_VERSION = 3
    /** Version 2 documents predate the preset source and remain readable. */
    private const val MIN_READ_VERSION = 2

    sealed interface LoadResult {
        data class Ok(
            val document: QuizConfigDocument,
            val packages: List<QuizLevelPackage>,
            val migrated: Boolean = false,
            /** Migration preserved more than [QuizLevelPackage.MAX_LEVELS]. */
            val overLimit: Boolean = false,
        ) : LoadResult

        /** The extension or the legacy source it migrates from is corrupt. */
        data class Unreadable(val errors: List<String>) : LoadResult

        data object Storage : LoadResult
    }

    /** Outcome of reading only the extension, without writing a migrated one. */
    sealed interface Extension {
        data object Absent : Extension
        data class Present(
            val packages: List<QuizLevelPackage>,
            val overLimit: Boolean,
        ) : Extension
        data class Broken(val errors: List<String>) : Extension
    }

    private val lock = Any()

    fun load(nowMs: Long = System.currentTimeMillis()): LoadResult = synchronized(lock) {
        val loaded = when (val result = QuizConfigDocumentStore.ensureLocalDocument(nowMs)) {
            is QuizDocLoadResult.Ok -> result
            is QuizDocLoadResult.Unreadable -> return LoadResult.Unreadable(result.errors)
            QuizDocLoadResult.Storage -> return LoadResult.Storage
        }
        val document = loaded.document
        when (val extension = read(document)) {
            is Extension.Present -> LoadResult.Ok(
                document = document,
                packages = extension.packages,
                migrated = false,
                overLimit = extension.overLimit,
            )
            is Extension.Broken -> LoadResult.Unreadable(extension.errors)
            Extension.Absent -> migrate(document, nowMs)
        }
    }

    private fun migrate(document: QuizConfigDocument, nowMs: Long): LoadResult {
        val courseLevels = document.localLevels()
            ?: return LoadResult.Unreadable(listOf("legacyLevels:CORRUPT"))
        val packages = migrate(
            courseLevels = courseLevels,
            customLevels = QuizCollectionStore.customLevels(),
            wrongLevel = QuizWrongReviewLevelStore.level(),
        )
        val saved = QuizConfigDocumentStore.updateLevelPackages(packages, nowMs)
        return when (saved) {
            is QuizDocSaveResult.Ok -> LoadResult.Ok(
                document = saved.document,
                packages = packages,
                migrated = true,
                overLimit = packages.size > QuizLevelPackage.MAX_LEVELS,
            )
            is QuizDocSaveResult.Invalid -> LoadResult.Unreadable(
                saved.issues.map { it.toString() },
            )
            is QuizDocSaveResult.Conflict -> {
                Log.e(TAG, "package migration lost a revision race")
                LoadResult.Unreadable(listOf("levelPackages:CONFLICT"))
            }
            QuizDocSaveResult.Storage -> LoadResult.Storage
        }
    }

    /**
     * One legacy level becomes one single-source package in the old visible
     * order: course levels, then custom tags, then the wrong-review level.
     * Duplicate legacy ids are kept unique so the web can address every row.
     */
    fun migrate(
        courseLevels: List<QuizLevel>,
        customLevels: List<QuizCollectionLevel>,
        wrongLevel: QuizWrongReviewLevel?,
    ): List<QuizLevelPackage> {
        val result = ArrayList<QuizLevelPackage>()
        val used = HashSet<String>()
        fun add(
            sources: List<QuizPackageSource>,
            name: String,
            preferredId: String,
        ) {
            val preferred = QuizLevelPackage.create(sources, name, preferredId)
            if (preferred != null && used.add(preferred.id)) {
                result += preferred
                return
            }
            var candidate = QuizLevelPackage.create(sources, name, null)
                ?: QuizLevelPackage.create(sources, "", null)
                ?: return
            while (!used.add(candidate.id)) {
                candidate = QuizLevelPackage.create(sources, candidate.name, null) ?: return
            }
            result += candidate
        }
        courseLevels.forEach { level ->
            add(
                sources = listOf(QuizPackageSource.Course(level.scope)),
                name = level.name,
                preferredId = level.id,
            )
        }
        customLevels.forEach { level ->
            add(
                sources = listOf(QuizPackageSource.Collection(level.collectionId)),
                name = level.name,
                preferredId = level.id,
            )
        }
        wrongLevel?.let { level ->
            add(
                sources = listOf(QuizPackageSource.WrongReview),
                name = level.name,
                preferredId = level.id,
            )
        }
        return result
    }

    fun read(document: QuizConfigDocument): Extension {
        val extensions = document.extensions
        if (extensions.has("local") && !extensions.isNull("local")) {
            if (extensions.optJSONObject("local") == null) {
                return Extension.Broken(listOf("extensions.local:INVALID"))
            }
        }
        val local = extensions.optJSONObject("local") ?: return Extension.Absent
        if (!local.has(EXTENSION_KEY) || local.isNull(EXTENSION_KEY)) {
            return Extension.Absent
        }
        val marker = local.optJSONObject(EXTENSION_KEY)
            ?: return Extension.Broken(listOf("levelPackages:INVALID"))
        if (marker.optInt("version", -1) !in MIN_READ_VERSION..EXTENSION_VERSION) {
            return Extension.Broken(listOf("levelPackages:VERSION"))
        }
        val array = marker.optJSONArray("levels")
            ?: return Extension.Broken(listOf("levelPackages.levels:INVALID"))
        val packages = QuizLevelPackage.parseList(array, max = Int.MAX_VALUE)
            ?: return Extension.Broken(listOf("levelPackages.levels:INVALID"))
        return Extension.Present(
            packages = packages,
            overLimit = packages.size > QuizLevelPackage.MAX_LEVELS,
        )
    }

    /**
     * The first course scope across the stored packages, for the single-scope
     * cloud contract. Present packages own the answer, even when they contain
     * no course source; an absent or broken extension falls back to the legacy
     * list so a pre-migration TV reports what it still uses.
     *
     * A preset (or legacy combined) course wins over an AI-only one: the cloud
     * scope describes the shared curriculum, not content one child generated.
     */
    fun defaultCourseScope(nowMs: Long = System.currentTimeMillis()): QuizAnswerScope? {
        if (PolicyModeCoordinator.mode != PolicyMode.LOCAL) {
            return QuizAnswerScopeStore.defaultScope()
        }
        val document = when (val loaded = QuizConfigDocumentStore.ensureLocalDocument(nowMs)) {
            is QuizDocLoadResult.Ok -> loaded.document
            is QuizDocLoadResult.Unreadable,
            QuizDocLoadResult.Storage,
            -> return QuizAnswerScopeStore.defaultScope()
        }
        val packages = when (val extension = read(document)) {
            is Extension.Present -> extension.packages
            Extension.Absent,
            is Extension.Broken,
            -> return QuizAnswerScopeStore.defaultScope()
        }
        val courses = packages.asSequence()
            .flatMap { it.sources.asSequence() }
            .filterIsInstance<QuizPackageSource.Course>()
        return courses.firstOrNull { it.origin != CourseOrigin.AI }?.scope
            ?: courses.firstOrNull()?.scope
    }

    /** Returns a copy with only the level-package extension replaced. */
    fun withPackages(
        document: QuizConfigDocument,
        packages: List<QuizLevelPackage>,
    ): QuizConfigDocument {
        // JSONObject is mutable and `copy()` shares it; deep-copy both levels
        // so a rejected save cannot mutate the document other callers hold.
        val extensions = JSONObject(document.extensions.toString())
        val local = extensions.optJSONObject("local")?.let { JSONObject(it.toString()) }
            ?: JSONObject()
        local.put(
            EXTENSION_KEY,
            JSONObject()
                .put("version", EXTENSION_VERSION)
                .put("levels", QuizLevelPackage.encodeList(packages)),
        )
        extensions.put("local", local)
        return document.copy(extensions = extensions)
    }
}
