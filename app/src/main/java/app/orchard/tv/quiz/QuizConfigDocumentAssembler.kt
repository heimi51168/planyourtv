package app.orchard.tv.quiz

import com.planyourtv.policy.quiz.QuizConfigDocument
import com.planyourtv.policy.quiz.QuizConfigDocumentCodec
import com.planyourtv.policy.quiz.QuizConfigDocumentEtag
import com.planyourtv.policy.quiz.QuizConfigDocumentValidator
import com.planyourtv.policy.quiz.QuizDeviceCapabilities
import com.planyourtv.policy.quiz.QuizDocDecodeResult
import com.planyourtv.policy.quiz.QuizDocIssue
import com.planyourtv.policy.quiz.QuizLevelOrigin
import com.planyourtv.policy.quiz.QuizLevelSource
import com.planyourtv.policy.quiz.QuizQuestionType
import org.json.JSONArray
import org.json.JSONObject

/**
 * 把主后端的「规则部分」和 QB 的「关卡部分」组装成一份完整 QCD。
 *
 * 组装是纯函数：网络失败、文档非法、能力不满足都由调用方决定保留旧 activeDoc。
 * 关卡可用性/打卡状态属于易变运行时信息，**不写进 QCD**（否则 etag 会随可用题数抖动），
 * 而是随缓存信封单独保存。
 */
object QuizConfigDocumentAssembler {

    sealed interface Result {
        data class Ok(val bundle: AssembledCloudDocument) : Result

        data class Invalid(val issues: List<QuizDocIssue>) : Result

        data class Unsupported(val issues: List<QuizDocIssue>) : Result
    }

    data class AssembledCloudDocument(
        val document: QuizConfigDocument,
        val qbLearnerId: Long,
        val memberId: Long?,
        val qbRevision: String,
        val levelsSource: String,
        val checkin: JSONObject,
        val levelStatus: JSONObject,
        val levels: List<CloudLevel>,
    )

    fun assemble(rules: JSONObject, levels: CloudLevelsBundle): Result {
        val issues = mutableListOf<QuizDocIssue>()
        val levelJson = JSONArray()
        val status = JSONObject()
        levels.levels.forEachIndexed { index, level ->
            val prefix = "levels[$index]"
            val sources = enumList<QuizLevelSource>(level.sources, prefix, "sources", issues)
            val questionTypes = enumList<QuizQuestionType>(
                level.questionTypes,
                prefix,
                "questionTypes",
                issues,
            )
            if (questionTypes.isEmpty()) {
                issues += QuizDocIssue("$prefix.questionTypes", "EMPTY")
            }
            val qcdLevelId = "cloud:${level.id}"
            val encoded = JSONObject()
                .put("levelId", qcdLevelId)
                .put("origin", QuizLevelOrigin.CLOUD.name)
                .put("name", level.name)
                .put("enabled", level.enabled)
                .put("sortNo", level.sortNo)
                .put("sources", JSONArray(sources.map { it.name }))
                .put("subjects", JSONArray(level.subjects))
                .put("scopeType", level.scopeType ?: JSONObject.NULL)
                .put("unitNos", JSONArray(level.unitNos))
                .put("unitGroups", JSONArray(level.unitGroups))
                .put("grade", level.grade ?: JSONObject.NULL)
                .put("semester", level.semester ?: JSONObject.NULL)
                .put("textbookId", level.textbookId ?: JSONObject.NULL)
                .put("textbook", JSONObject.NULL)
                .put("difficulty", level.difficulty?.uppercase() ?: JSONObject.NULL)
                .put("advisoryQuestionCount", level.advisoryQuestionCount ?: JSONObject.NULL)
                .put("questionTypes", JSONArray(questionTypes.map { it.name }))
                .put("sourceScopes", level.sourceScopes)
            levelJson.put(encoded)
            status.put(
                qcdLevelId,
                JSONObject()
                    .put("qbLevelId", level.id)
                    .put("available", level.available)
                    .put("availableCount", level.availableCount)
                    .put(
                        "unavailableReason",
                        level.unavailableReason ?: JSONObject.NULL,
                    )
                    .put("questionCount", level.questionCount),
            )
        }
        if (issues.isNotEmpty()) return Result.Invalid(issues)

        val root = JSONObject(rules.toString())
        // 规则响应没有关卡；即使未来带了占位，也以 QB 契约字段为准。
        root.put("levels", levelJson)
        root.put("levelsEtag", "")
        // 主后端 ETag 只覆盖规则部分，QCD 的 etag 必须由电视基于完整文档重算。
        root.put("etag", "")

        val decoded = when (val result = QuizConfigDocumentCodec.decode(root)) {
            is QuizDocDecodeResult.Success -> result.document
            is QuizDocDecodeResult.Failure -> return Result.Invalid(result.issues)
        }
        val validation = QuizConfigDocumentValidator.validate(decoded)
        if (validation.isNotEmpty()) return Result.Invalid(validation)
        val capabilityIssues = QuizDeviceCapabilities.issues(decoded, cloudRound = true)
        if (capabilityIssues.isNotEmpty()) return Result.Unsupported(capabilityIssues)

        val levelsEtag = QuizConfigDocumentEtag.levelsEtag(decoded.levels)
        val withLevelsEtag = decoded.copy(
            levelsEtag = levelsEtag,
            extensions = cloudExtensions(decoded.extensions, levels),
        )
        val document = withLevelsEtag.copy(
            etag = QuizConfigDocumentEtag.etag(withLevelsEtag),
        )
        return Result.Ok(
            AssembledCloudDocument(
                document = document,
                qbLearnerId = levels.learnerId,
                memberId = levels.memberId,
                qbRevision = levels.revision,
                levelsSource = levels.levelsSource,
                checkin = JSONObject(levels.checkin.toString()),
                levelStatus = status,
                levels = levels.levels,
            ),
        )
    }

    /**
     * 只记录稳定契约字段：levelsSource/qbRevision。打卡状态和可用题数会随每天状态变化，
     * 放进文档会让 etag 无意义抖动；它们保存在云文档信封里。
     */
    private fun cloudExtensions(existing: JSONObject, levels: CloudLevelsBundle): JSONObject {
        val extensions = JSONObject(existing.toString())
        extensions.put(
            "cloud",
            JSONObject()
                .put("levelsSource", levels.levelsSource)
                .put("levelsRevision", levels.revision),
        )
        return extensions
    }

    private inline fun <reified T : Enum<T>> enumList(
        values: List<String>,
        prefix: String,
        field: String,
        issues: MutableList<QuizDocIssue>,
    ): List<T> {
        val out = ArrayList<T>(values.size)
        values.forEach { value ->
            val parsed = runCatching { enumValueOf<T>(value) }.getOrNull()
            if (parsed == null) {
                issues += QuizDocIssue("$prefix.$field", "INVALID")
            } else {
                out += parsed
            }
        }
        return out
    }
}
