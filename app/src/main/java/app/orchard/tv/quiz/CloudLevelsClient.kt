package app.orchard.tv.quiz

import java.io.IOException
import org.json.JSONObject

/**
 * 云端关卡部分：QB `deviceLevels` 的电视端视图。
 *
 * 该文件只定义契约与错误分类，真正的 HTTP/`qbs_` 会话复用由 [QbClient] 负责。
 * `learnerId` 是 QB 的 `qb_learner.id`，与主后端使用的 `memberId` 不是同一个 id；
 * 电视必须同时持有两者才能分别调用 QB 与主后端。
 */
data class CloudLevel(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val sortNo: Int,
    val sources: List<String>,
    val subjects: List<String>,
    val scopeType: String?,
    val unitNos: List<Int>,
    val unitGroups: List<Int>,
    val textbookId: Long?,
    val grade: Int?,
    val semester: Int?,
    val difficulty: String?,
    val advisoryQuestionCount: Int?,
    val questionTypes: List<String>,
    val sourceScopes: JSONObject,
    val origin: String,
    val questionCount: Int,
    val available: Boolean,
    val availableCount: Int,
    val unavailableReason: String?,
)

/** QB 当前选中的孩子；`memberId` 用于主后端 quiz-rules。 */
data class CloudLearner(
    val learnerId: Long,
    val memberId: Long?,
    val name: String,
    val selected: Boolean,
)

data class CloudLevelsBundle(
    val learnerId: Long,
    val memberId: Long?,
    val levels: List<CloudLevel>,
    val checkin: JSONObject,
    val revision: String,
    val levelsSource: String,
)

/** 只映射「是否值得重试」和「凭证/内容是否坏了」，不携带服务器内部细节。 */
sealed interface CloudLevelsResult {
    data class Ok(val bundle: CloudLevelsBundle) : CloudLevelsResult

    data class Unauthorized(val message: String) : CloudLevelsResult

    data class Retryable(val message: String) : CloudLevelsResult

    data class Rejected(val message: String) : CloudLevelsResult

    data class Invalid(val message: String) : CloudLevelsResult
}

object CloudLevelsClient {
    private val LEVELS_REVISION = Regex("^sha256:[0-9a-f]{64}$")

    fun fetch(learnerId: Long): CloudLevelsResult {
        if (learnerId <= 0L) return CloudLevelsResult.Invalid("learnerId")
        return try {
            CloudLevelsResult.Ok(QbClient.deviceLevels(learnerId))
        } catch (error: QbClient.QbContractException) {
            // 契约不符是确定性失败：重试不会变好，按 INVALID_DOCUMENT 处理并保留上一版。
            CloudLevelsResult.Invalid(error.message.orEmpty())
        } catch (error: QbClient.QbException) {
            classify(error)
        } catch (error: IOException) {
            CloudLevelsResult.Retryable(error.message.orEmpty())
        }
    }

    fun selectedLearner(): CloudLevelsResult {
        return try {
            val selection = QbClient.deviceLearners()
            val selected = selection.learners.firstOrNull { it.selected }
            if (selected == null) {
                return CloudLevelsResult.Rejected("NO_SELECTED_LEARNER")
            }
            CloudLevelsResult.Ok(
                CloudLevelsBundle(
                    learnerId = selected.learnerId,
                    memberId = selected.memberId,
                    levels = emptyList(),
                    checkin = JSONObject(),
                    revision = "",
                    levelsSource = "",
                ),
            )
        } catch (error: QbClient.QbContractException) {
            // 契约不符是确定性失败：重试不会变好，按 INVALID_DOCUMENT 处理并保留上一版。
            CloudLevelsResult.Invalid(error.message.orEmpty())
        } catch (error: QbClient.QbException) {
            classify(error)
        } catch (error: IOException) {
            CloudLevelsResult.Retryable(error.message.orEmpty())
        }
    }

    internal fun classify(error: QbClient.QbException): CloudLevelsResult = when {
        error.httpCode == 401 || error.httpCode == 403 ||
            error.apiCode == 401 || error.apiCode == 403 ->
            CloudLevelsResult.Unauthorized(error.message.orEmpty())
        error.httpCode >= 500 || error.httpCode == 0 ->
            CloudLevelsResult.Retryable(error.message.orEmpty())
        else -> CloudLevelsResult.Rejected(error.message.orEmpty())
    }

    internal fun revisionIsValid(revision: String): Boolean =
        LEVELS_REVISION.matches(revision)
}
