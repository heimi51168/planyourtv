package app.orchard.tv.quiz

import android.util.Log
import com.planyourtv.policy.quiz.QuizAnswerScope
import java.util.Locale

/**
 * 本地答题范围的唯一入口。
 *
 * 两种模式：
 * - BOUND：真实范围由出题配置实时推导，出题配置改了答题范围跟着改；
 * - CUSTOM：电视里保存一份独立的六字段快照。
 *
 * 存储沿用旧格式：`"null"`（或从未保存过）表示绑定出题范围，具体对象表示
 * 自定义范围。这样旧版本保存的 "null" 升级后自然变成新的绑定语义，不需要
 * 迁移 SharedPreferences。
 */
object QuizAnswerScopeStore {
    private const val TAG = "QuizAnswerScopeStore"

    enum class Mode { BOUND, CUSTOM }

    fun mode(): Mode =
        if (!QuizSessionStore.hasAnswerScope() || QuizSessionStore.answerScopeIsBound()) {
            Mode.BOUND
        } else {
            Mode.CUSTOM
        }

    /** 解析当前生效范围；null 表示无法映射（按“不过滤”处理，和旧行为一致）。 */
    fun loadOrInitialize(): QuizAnswerScope? {
        if (!QuizSessionStore.hasAnswerScope()) {
            // 新安装默认绑定出题范围。写入显式 "null" 标记，让同步和设置页
            // 对“从未保存”与“明确绑定”得到同一个答案。
            if (!QuizSessionStore.saveAnswerScope(null)) {
                Log.e(TAG, "failed to persist default bound answer scope")
            }
            return boundScope()
        }
        if (QuizSessionStore.answerScopeIsBound()) return boundScope()
        val stored = QuizSessionStore.loadAnswerScope()
        if (stored == null) {
            Log.e(TAG, "stored answer scope is corrupt; keeping bound behaviour until next save")
        }
        return stored
    }

    fun signature(scope: QuizAnswerScope?): String =
        scope?.signature() ?: QuizAnswerScope.FOLLOW_SIGNATURE

    fun currentSignature(): String = signature(loadOrInitialize())

    private fun boundScope(): QuizAnswerScope? {
        val prefs = QuizStudyProfile.current()
        if (prefs == null) {
            Log.e(TAG, "generation prefs invalid; bound answer scope resolves to no filter")
            return null
        }
        val subject = prefs.optString("subject", "").uppercase(Locale.ROOT)
        if (subject == "ALL") {
            Log.e(TAG, "generation prefs use ALL subjects; bound answer scope resolves to no filter")
            return null
        }
        val scopeType = prefs.optString("scope", "").uppercase(Locale.ROOT)
        val scope = QuizAnswerScope.create(
            grade = prefs.optInt("grade", 0),
            semester = prefs.optInt("semester", 0),
            subject = subject,
            textbook = prefs.optString("textbook", ""),
            scopeType = scopeType,
            unitNo = if (scopeType == "UNIT") prefs.optInt("unit", 0) else null,
            difficulty = prefs.optString("difficulty", "").ifBlank { null },
        )
        if (scope == null) {
            Log.e(TAG, "generation prefs cannot map to an answer scope; resolving to no filter")
        }
        return scope
    }
}
