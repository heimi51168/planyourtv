package app.orchard.tv.quiz

import android.util.Log
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizLevel
import java.util.Locale

/**
 * The ordered quiz levels the parent designs on the phone.
 *
 * The list is the single source of truth. Older installs only had one answer
 * scope, either a stored custom scope or a scope derived from the generation
 * settings; the first read migrates that effective scope into one level so a
 * child can keep starting rounds after the upgrade. A fresh install starts
 * with no levels and the TV asks the parent to add one.
 */
object QuizAnswerScopeStore {
    private const val TAG = "QuizAnswerScopeStore"

    fun levels(): List<QuizLevel> {
        val stored = QuizSessionStore.loadAnswerScopes()
        if (stored != null) return stored
        if (QuizSessionStore.hasAnswerScopes()) {
            // The list was written but no longer parses. Do not silently replace
            // it with a guess; an empty list keeps the TV from starting a round
            // on a range the parent never chose.
            Log.e(TAG, "stored answer levels are corrupt; keeping an empty list")
            return emptyList()
        }
        val migrated = migrateLegacyScope()
        if (!QuizSessionStore.saveAnswerScopes(migrated)) {
            Log.e(TAG, "failed to persist migrated answer levels")
        }
        return migrated
    }

    /** Saves the whole ordered list atomically through [QuizSessionStore]. */
    fun save(levels: List<QuizLevel>): Boolean = QuizSessionStore.saveAnswerScopes(levels)

    /** The selected level by id, or null when the parent removed it meanwhile. */
    fun level(id: String?): QuizLevel? {
        if (id.isNullOrBlank()) return levels().firstOrNull()
        return levels().firstOrNull { it.id == id }
    }

    /** First level, used where the legacy single-scope surface still needs one. */
    fun defaultScope(): QuizAnswerScope? = levels().firstOrNull()?.scope

    /**
     * The scope the generation settings currently describe. Only a page cached
     * before the level list uses this to migrate an explicit "follow
     * generation" save into one frozen level.
     */
    fun generationScope(): QuizAnswerScope? = boundScope()

    fun signature(levels: List<QuizLevel>): String =
        levels.joinToString(";") { it.scope.signature() }

    fun currentSignature(): String = signature(levels())

    /**
     * The old format stored either "null" (bound to the generation settings) or
     * one custom scope. Freeze whichever scope is effective today into level 1;
     * later generation edits no longer move it, matching the new list model.
     */
    private fun migrateLegacyScope(): List<QuizLevel> {
        val scope = if (!QuizSessionStore.hasAnswerScope() ||
            QuizSessionStore.answerScopeIsBound()
        ) {
            boundScope()
        } else {
            QuizSessionStore.loadAnswerScope().also {
                if (it == null) Log.e(TAG, "legacy answer scope is corrupt")
            }
        }
        return scope?.let { listOf(QuizLevel(id = "legacy-1", name = "", scope = it)) }
            ?: emptyList()
    }

    private fun boundScope(): QuizAnswerScope? {
        val prefs = QuizStudyProfile.current()
        if (prefs == null) {
            Log.i(TAG, "no generation prefs; migrating to an empty level list")
            return null
        }
        val subject = prefs.optString("subject", "").uppercase(Locale.ROOT)
        val allSubjects = subject == "ALL"
        val scopeType = prefs.optString("scope", "").uppercase(Locale.ROOT)
        val scope = QuizAnswerScope.create(
            grade = prefs.optInt("grade", 0),
            semester = prefs.optInt("semester", 0),
            subject = if (allSubjects) QuizAnswerScope.SUBJECT_ALL else subject,
            // 全科出题不带教材；绑定范围也不能悄悄按某一科教材过滤。
            textbook = if (allSubjects) {
                QuizAnswerScope.TEXTBOOK_ALL
            } else {
                prefs.optString("textbook", "")
            },
            scopeType = scopeType,
            unitNo = if (scopeType == "UNIT") prefs.optInt("unit", 0) else null,
            difficulty = prefs.optString("difficulty", "").ifBlank { null },
        )
        if (scope == null) {
            Log.i(TAG, "generation prefs cannot map to an answer scope; starting empty")
        }
        return scope
    }
}
