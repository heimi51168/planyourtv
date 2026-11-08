package com.planyourtv.policy.quiz

import org.json.JSONObject
import java.util.Locale

/**
 * 答题范围（含难度）。与主后端 `device_quiz_config` 的范围列一一对应。
 *
 * 难度属于范围：没有范围就没有难度过滤。取值范围与后端白名单完全一致，
 * 解析失败必须显式拒绝，不能猜一个默认范围。
 */
data class QuizAnswerScope(
    val grade: Int,
    val semester: Int,
    val subject: String,
    val textbook: String,
    val scopeType: String,
    val unitNo: Int?,
    val difficulty: String?,
) {
    fun encode(): JSONObject = JSONObject()
        .put("grade", grade)
        .put("semester", semester)
        .put("subject", subject)
        .put("textbook", textbook)
        .put("scopeType", scopeType)
        .put("unitNo", unitNo ?: JSONObject.NULL)
        .put("difficulty", difficulty ?: JSONObject.NULL)

    /** 只用于本地比较；不参与协议。 */
    fun signature(): String = listOf(
        grade,
        semester,
        subject,
        textbook,
        scopeType,
        unitNo ?: "-",
        difficulty ?: "-",
    ).joinToString("|")

    companion object {
        const val FOLLOW_SIGNATURE = "FOLLOW"

        /** 通配值：答题范围不限制学科 / 教材。 */
        const val SUBJECT_ALL = "ALL"
        const val TEXTBOOK_ALL = "ALL"

        private val SUBJECTS = setOf("CHINESE", "MATH", "ENGLISH", "SCIENCE", SUBJECT_ALL)
        private val TEXTBOOKS = setOf(
            "BUBIAN", "RENJIAO", "SUJIAO", "BEISHIDA", "QINGDAO",
            "PEP", "WAIYAN", "YILIN", "KEJIAO", "GENERAL", TEXTBOOK_ALL,
        )
        private val SCOPE_TYPES = setOf("UNIT", "MIDTERM", "FINAL", "REVIEW")
        private val DIFFICULTIES = setOf("EASY", "MEDIUM", "HARD")

        /** 供 facets / 表单校验复用，取值范围与 [create] 完全一致。 */
        fun isSupportedSubject(value: String): Boolean =
            value.trim().uppercase(Locale.ROOT) in SUBJECTS

        fun isSupportedTextbook(value: String): Boolean =
            value.trim().uppercase(Locale.ROOT) in TEXTBOOKS

        fun isSupportedScopeType(value: String): Boolean =
            value.trim().uppercase(Locale.ROOT) in SCOPE_TYPES

        fun isSupportedDifficulty(value: String): Boolean =
            value.trim().uppercase(Locale.ROOT) in DIFFICULTIES

        /** 严格解析：字段缺失、类型不符或取值不在白名单内都返回 null。 */
        fun parse(json: JSONObject?): QuizAnswerScope? {
            if (json == null) return null
            val grade = requiredInt(json, "grade") ?: return null
            val semester = requiredInt(json, "semester") ?: return null
            val subject = requiredString(json, "subject") ?: return null
            val textbook = requiredString(json, "textbook") ?: return null
            val scopeType = requiredString(json, "scopeType") ?: return null
            val unitNo = optionalInt(json, "unitNo")
            val difficulty = optionalString(json, "difficulty")
            return create(grade, semester, subject, textbook, scopeType, unitNo, difficulty)
        }

        /** 由已归一化的值构造；任何越界都返回 null，由调用方显式失败。 */
        fun create(
            grade: Int,
            semester: Int,
            subject: String,
            textbook: String,
            scopeType: String,
            unitNo: Int?,
            difficulty: String?,
        ): QuizAnswerScope? {
            val normalizedSubject = subject.trim().uppercase(Locale.ROOT)
            val normalizedTextbook = textbook.trim().uppercase(Locale.ROOT)
            val normalizedScope = scopeType.trim().uppercase(Locale.ROOT)
            val normalizedDifficulty = difficulty?.trim()?.uppercase(Locale.ROOT)
            if (grade !in 1..6 || semester !in 1..2) return null
            if (normalizedSubject !in SUBJECTS) return null
            if (normalizedTextbook !in TEXTBOOKS) return null
            if (normalizedScope !in SCOPE_TYPES) return null
            if (normalizedScope == "UNIT") {
                if (unitNo == null || unitNo !in 1..8) return null
            } else if (unitNo != null) {
                return null
            }
            if (normalizedDifficulty != null && normalizedDifficulty !in DIFFICULTIES) return null
            return QuizAnswerScope(
                grade = grade,
                semester = semester,
                subject = normalizedSubject,
                textbook = normalizedTextbook,
                scopeType = normalizedScope,
                unitNo = unitNo,
                difficulty = normalizedDifficulty,
            )
        }

        private fun requiredInt(json: JSONObject, name: String): Int? {
            if (!json.has(name) || json.isNull(name)) return null
            return intValue(json.opt(name))
        }

        private fun optionalInt(json: JSONObject, name: String): Int? {
            if (!json.has(name) || json.isNull(name)) return null
            return intValue(json.opt(name))
        }

        private fun intValue(value: Any?): Int? {
            if (value !is Number) return null
            val double = value.toDouble()
            if (double != value.toLong().toDouble()) return null
            return value.toInt()
        }

        private fun requiredString(json: JSONObject, name: String): String? {
            if (!json.has(name) || json.isNull(name)) return null
            return json.opt(name) as? String
        }

        private fun optionalString(json: JSONObject, name: String): String? {
            if (!json.has(name) || json.isNull(name)) return null
            return json.opt(name) as? String
        }
    }
}
