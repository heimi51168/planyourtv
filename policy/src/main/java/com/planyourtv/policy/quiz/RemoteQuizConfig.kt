package com.planyourtv.policy.quiz

import org.json.JSONObject
import java.util.Locale

/**
 * TV-side mirror of the backend `device_quiz_config` row.
 *
 * The backend is authoritative once the TV is bound. The local web may still edit the config
 * while the source is LOCAL; those edits are uploaded as a new `localVersion` and the cloud
 * answers with a new `version`. Points are an internal compatibility assumption only: the wire
 * field must stay 1 and no UI ever displays it.
 */
data class RemoteQuizConfig(
    val deviceNo: String = "",
    val familyId: Long? = null,
    val enabled: Boolean,
    val source: QuizSource,
    val pullCount: Int,
    val minutesPerQuestion: Int,
    val pointsPerQuestion: Int,
    val defaultTimeLimitSec: Int,
    val answerDisplaySec: Int,
    val shuffleOptions: Boolean,
    val maxEarnedMinutes: Int,
    /** 答题范围；null = 绑定出题范围。 */
    val scope: QuizAnswerScope?,
    /** 响应里是否带有范围字段；旧后端不带时必须保留本地范围。 */
    val scopeProvided: Boolean,
    val sessionConfig: JSONObject?,
    val version: Long,
    val appliedVersion: Long,
    val localVersion: Long,
    val localUploaded: Boolean,
    val reportSource: String = "",
) {
    /** Applies the cloud fields onto a legacy challenge config without dropping its extras. */
    fun toQuizConfig(legacy: QuizConfig): QuizConfig = legacy.copy(
        defaultTimeLimitSec = defaultTimeLimitSec,
        minutesPerPoint = minutesPerQuestion,
        maxEarnedMinutes = maxEarnedMinutes.coerceIn(1, QUIZ_MAX_EARNED_MINUTES),
        shuffleOptions = shuffleOptions,
        answerDisplaySec = normalizeAnswerHoldSec(answerDisplaySec),
        pullCount = pullCount,
        minutesPerQuestion = minutesPerQuestion,
        pointsPerQuestion = 1,
    )

    companion object {
        /**
         * Returns null for a missing/empty node. An object that exists but violates the contract
         * is also rejected; callers log it and keep the last applied config rather than guessing.
         */
        fun parse(json: JSONObject?): RemoteQuizConfig? {
            if (json == null || json.length() == 0) return null
            val source = when (json.optString("source", "").uppercase(Locale.ROOT)) {
                QuizSource.LOCAL.name -> QuizSource.LOCAL
                QuizSource.CLOUD.name -> QuizSource.CLOUD
                else -> return null
            }
            if (!json.has("enabled")) return null
            val enabled = json.optBoolean("enabled", true)
            val pullCount = json.optInt("pullCount", -1)
            if (pullCount !in QUIZ_MIN_PULL_COUNT..QUIZ_MAX_PULL_COUNT) return null
            val minutesPerQuestion = json.optInt("minutesPerQuestion", -1)
            if (minutesPerQuestion !in MIN_MINUTES_PER_QUESTION..MAX_MINUTES_PER_QUESTION) return null
            // The wire field exists only so old backends and the local model share one shape.
            // Any value other than 1 means the contract changed and the TV must not guess.
            if (json.optInt("pointsPerQuestion", 1) != 1) return null
            val timeLimit = json.optInt("defaultTimeLimitSec", -1)
            if (timeLimit !in QUIZ_MIN_TIME_LIMIT_SEC..QUIZ_MAX_TIME_LIMIT_SEC) return null
            val answerDisplay = json.optInt("answerDisplaySec", -1)
            // Accept the old auto-advance range until every bound TV row is
            // migrated; toQuizConfig maps anything above the new cap to the
            // 3-second default instead of rejecting the whole cloud config.
            if (answerDisplay !in QUIZ_MIN_ANSWER_DISPLAY_SEC..QUIZ_LEGACY_MAX_ANSWER_DISPLAY_SEC) {
                return null
            }
            val maxEarnedMinutes = json.optInt("maxEarnedMinutes", -1)
            if (maxEarnedMinutes !in 1..QUIZ_MAX_EARNED_MINUTES) return null
            val familyId = if (json.has("familyId") && !json.isNull("familyId")) {
                json.optLong("familyId", 0L).takeIf { it > 0L }
            } else {
                null
            }
            // 后端把 null 也序列化出来，所以“字段存在”= 本次范围有效（null 表示显式跟随）；
            // 字段缺失只可能来自旧后端，此时保留本地范围。
            val scopeProvided = json.has("scope")
            val scope = if (scopeProvided) {
                if (json.isNull("scope")) null else QuizAnswerScope.parse(json.optJSONObject("scope")) ?: return null
            } else {
                null
            }
            return RemoteQuizConfig(
                deviceNo = json.optString("deviceNo", ""),
                familyId = familyId,
                enabled = enabled,
                source = source,
                pullCount = pullCount,
                minutesPerQuestion = minutesPerQuestion,
                pointsPerQuestion = 1,
                defaultTimeLimitSec = timeLimit,
                answerDisplaySec = normalizeAnswerHoldSec(answerDisplay),
                shuffleOptions = json.optBoolean("shuffleOptions", true),
                maxEarnedMinutes = maxEarnedMinutes,
                scope = scope,
                scopeProvided = scopeProvided,
                sessionConfig = json.optJSONObject("sessionConfig"),
                version = json.optLong("version", 0L).coerceAtLeast(0L),
                appliedVersion = json.optLong("appliedVersion", 0L).coerceAtLeast(0L),
                localVersion = json.optLong("localVersion", 0L).coerceAtLeast(0L),
                localUploaded = json.optBoolean("localUploaded", false),
                reportSource = json.optString("reportSource", ""),
            )
        }

        const val MIN_MINUTES_PER_QUESTION = 1
        const val MAX_MINUTES_PER_QUESTION = 30
    }
}
