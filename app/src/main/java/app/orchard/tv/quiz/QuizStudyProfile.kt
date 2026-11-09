package app.orchard.tv.quiz

import android.util.Log
import com.planyourtv.policy.quiz.QuizCurriculum
import com.planyourtv.policy.quiz.QuizUnitCatalog
import org.json.JSONObject
import java.util.Locale

/**
 * 电视本地出题配置（年级/学科/教材/单元）。
 *
 * 只服务本地 AI 出题，以及旧版本升级时一次性初始化答题范围
 * （[QuizAnswerScopeStore]）。云端题库的答题范围以 [QuizAnswerScope] 为准，
 * 改这里不会再影响云端取题。
 *
 * 首次安装还没有保存过偏好时使用与设置页一致的默认值；已保存但内容损坏时
 * 显式报错，不悄悄换一套别的年级。
 */
object QuizStudyProfile {
    private const val TAG = "QuizStudyProfile"

    private const val DEFAULT_GRADE = "4a"
    private const val DEFAULT_SUBJECT = "math"
    private const val DEFAULT_TEXTBOOK = "renjiao"
    // The bundled sample bank follows this scope, so a fresh TV can start a
    // local round before a parent generates anything.
    private const val DEFAULT_SCOPE = "midterm"
    private const val DEFAULT_UNIT = 3
    private const val DEFAULT_DIFFICULTY = ""

    private val DIFFICULTIES = setOf("", "easy", "medium", "hard")
    private val SCOPES = setOf("unit", "midterm", "final", "review")

    /** 返回 null 表示本地已保存的偏好损坏；调用方必须显式失败，不能猜默认值。 */
    fun current(): JSONObject? {
        val curriculum = QuizCurriculum.current() ?: return null
        val stored = QuizSessionStore.loadGenerationPrefs()
            ?: return build(DEFAULT_GRADE, DEFAULT_SUBJECT, DEFAULT_TEXTBOOK,
                DEFAULT_SCOPE, DEFAULT_UNIT)
        val grade = stored.optString("grade", "")
        val subject = stored.optString("subject", "")
        val textbook = stored.optString("textbook", "")
        val scope = stored.optString("scope", "")
        val unit = stored.optInt("unit", 0)
        val difficulty = stored.optString("difficulty", "").trim().lowercase()
        val gradeNo = curriculum.gradeNoForCode(grade)
        val semester = curriculum.semesterForCode(grade)
        val unitMax = gradeNo?.let { gradeNo ->
            QuizUnitCatalog.current()
                ?.unitMax(gradeNo, semester ?: 0, subject, textbook)
                ?: curriculum.unitMax(gradeNo)
        }
        if (gradeNo == null || semester == null || unitMax == null ||
            !curriculum.isSubjectSupported(gradeNo, subject) ||
            !curriculum.isTextbookSupported(gradeNo, subject, textbook) ||
            scope !in SCOPES ||
            (scope == "unit" && unit !in 1..unitMax) ||
            difficulty !in DIFFICULTIES
        ) {
            Log.e(TAG, "stored generation prefs invalid: $stored")
            return null
        }
        return build(grade, subject, textbook, scope, unit, difficulty)
    }

    private fun build(
        grade: String,
        subject: String,
        textbook: String,
        scope: String,
        unit: Int,
        difficulty: String = DEFAULT_DIFFICULTY,
    ): JSONObject {
        val semester = if (grade.endsWith("b")) 2 else 1
        return JSONObject()
            .put("grade", grade[0] - '0')
            .put("semester", semester)
            .put("subject", subject.uppercase(Locale.ROOT))
            .put("textbook", textbook.uppercase(Locale.ROOT))
            .put("scope", scope.uppercase(Locale.ROOT))
            .put("unit", if (scope == "unit") unit else JSONObject.NULL)
            .put("difficulty", difficulty)
    }
}
