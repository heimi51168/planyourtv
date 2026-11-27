package com.planyourtv.policy.quiz

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

sealed interface QuizDocDecodeResult {
    data class Success(val document: QuizConfigDocument) : QuizDocDecodeResult
    data class Failure(val issues: List<QuizDocIssue>) : QuizDocDecodeResult
}

/**
 * Strict QCD v1 codec. Structural problems reject the whole document; range and
 * cross-field rules live in [QuizConfigDocumentValidator].
 *
 * Unknown fields are preserved at the top level and inside each known section so
 * a cloud document can round-trip through the TV without losing fields the TV
 * does not execute yet. Unknown fields inside a reward profile are intentionally
 * dropped: profiles have complete-replacement semantics.
 */
object QuizConfigDocumentCodec {
    fun encode(document: QuizConfigDocument): String = encodeDocument(document).toString()

    fun encodeDocument(document: QuizConfigDocument): JSONObject {
        val root = copyObject(document.extraTopLevel)
        root.put("schemaVersion", document.schemaVersion)
        root.put("documentId", document.documentId)
        root.put("revision", document.revision)
        root.put("levelsEtag", document.levelsEtag)
        root.put("etag", document.etag)
        root.put("origin", document.origin.name)
        root.put("updatedAt", document.updatedAt)
        root.put("updatedBy", document.updatedBy.name)
        root.put("enabled", document.enabled)
        root.put("rules", encodeRules(document.rules))
        root.put("reward", encodeReward(document.reward))
        root.put("quota", encodeQuota(document.quota))
        root.put("calendar", encodeCalendar(document.calendar))
        root.put("questionPolicy", encodeQuestionPolicy(document.questionPolicy))
        root.put("requirements", encodeRequirements(document.requirements))
        root.put("levels", encodeLevels(document.levels))
        root.put("extensions", copyObject(document.extensions))
        return root
    }

    internal fun encodeLevel(level: QuizLevelDoc): JSONObject {
        val out = copyObject(level.extra)
        out.put("levelId", level.levelId)
        out.put("origin", level.origin.name)
        out.put("name", level.name)
        out.put("enabled", level.enabled)
        out.put("sortNo", level.sortNo)
        out.put("sources", enumArray(level.sources))
        out.put("subjects", JSONArray(level.subjects))
        out.put("scopeType", level.scopeType ?: JSONObject.NULL)
        out.put("unitNos", intArray(level.unitNos))
        out.put("unitGroups", intArray(level.unitGroups))
        out.put("grade", level.grade ?: JSONObject.NULL)
        out.put("semester", level.semester ?: JSONObject.NULL)
        out.put("textbookId", level.textbookId ?: JSONObject.NULL)
        out.put("textbook", level.textbook ?: JSONObject.NULL)
        out.put("difficulty", level.difficulty ?: JSONObject.NULL)
        out.put("advisoryQuestionCount", level.advisoryQuestionCount ?: JSONObject.NULL)
        out.put("questionTypes", enumArray(level.questionTypes))
        if (level.sourceScopes != null) {
            out.put("sourceScopes", copyObject(level.sourceScopes))
        }
        if (level.localScope != null) {
            out.put("localScope", encodeLocalScope(level.localScope))
        }
        return out
    }

    fun decode(raw: String): QuizDocDecodeResult {
        if (raw.isBlank()) {
            return QuizDocDecodeResult.Failure(listOf(QuizDocIssue("document", "EMPTY")))
        }
        val root = runCatching { JSONObject(raw) }.getOrElse {
            return QuizDocDecodeResult.Failure(listOf(QuizDocIssue("document", "INVALID_JSON")))
        }
        return decode(root)
    }

    fun decode(root: JSONObject): QuizDocDecodeResult {
        val issues = mutableListOf<QuizDocIssue>()
        val schemaVersion = root.requiredInt("schemaVersion", "", issues)
        if (schemaVersion != null && schemaVersion != QUIZ_DOC_SCHEMA_VERSION) {
            issues += QuizDocIssue("schemaVersion", "UNSUPPORTED_SCHEMA")
        }
        val documentId = root.requiredString("documentId", "", issues)
        val revision = root.requiredLong("revision", "", issues)
        val levelsEtag = root.requiredString("levelsEtag", "", issues)
        val etag = root.requiredString("etag", "", issues)
        val origin = root.requiredEnum<QuizDocOrigin>("origin", "", issues)
        val updatedAt = root.requiredString("updatedAt", "", issues)
        val updatedBy = root.requiredEnum<QuizDocUpdatedBy>("updatedBy", "", issues)
        val enabled = root.requiredBoolean("enabled", "", issues)
        val rulesJson = root.requiredObject("rules", "", issues)
        val rewardJson = root.requiredObject("reward", "", issues)
        val quotaJson = root.requiredObject("quota", "", issues)
        val calendarJson = root.requiredObject("calendar", "", issues)
        val policyJson = root.requiredObject("questionPolicy", "", issues)
        val requirementsJson = root.requiredObject("requirements", "", issues)
        val levelsJson = root.requiredArray("levels", "", issues)
        val extensions = root.optionalObject("extensions", "", issues)?.let(::copyObject)

        val rules = rulesJson?.let { parseRules(it, "rules", issues) }
        val reward = rewardJson?.let { parseReward(it, "reward", issues) }
        val quota = quotaJson?.let { parseQuota(it, "quota", issues) }
        val calendar = calendarJson?.let { parseCalendar(it, "calendar", issues) }
        val policy = policyJson?.let { parseQuestionPolicy(it, "questionPolicy", issues) }
        val requirements = requirementsJson?.let { parseRequirements(it, "requirements", issues) }
        val levels = levelsJson?.let { parseLevels(it, "levels", issues) }

        val complete = schemaVersion != null && schemaVersion == QUIZ_DOC_SCHEMA_VERSION &&
            documentId != null && revision != null && levelsEtag != null && etag != null &&
            origin != null && updatedAt != null && updatedBy != null && enabled != null &&
            rules != null && reward != null && quota != null && calendar != null &&
            policy != null && requirements != null && levels != null
        if (issues.isNotEmpty() || !complete) {
            return QuizDocDecodeResult.Failure(issues.ifEmpty { listOf(QuizDocIssue("document", "INVALID")) })
        }
        return QuizDocDecodeResult.Success(
            QuizConfigDocument(
                schemaVersion = schemaVersion!!,
                documentId = documentId!!,
                revision = revision!!,
                levelsEtag = levelsEtag!!,
                etag = etag!!,
                origin = origin!!,
                updatedAt = updatedAt!!,
                updatedBy = updatedBy!!,
                enabled = enabled!!,
                rules = rules!!,
                reward = reward!!,
                quota = quota!!,
                calendar = calendar!!,
                questionPolicy = policy!!,
                requirements = requirements!!,
                levels = levels!!,
                extensions = extensions ?: JSONObject(),
                extraTopLevel = collectExtras(root, TOP_LEVEL_KEYS),
            ),
        )
    }

    private fun parseRules(root: JSONObject, prefix: String, issues: MutableList<QuizDocIssue>): QuizRules? {
        val questionCount = root.requiredInt("questionCount", prefix, issues)
        val timeLimitSec = root.requiredInt("timeLimitSec", prefix, issues)
        // Optional for documents written before per-difficulty windows. The
        // default window is the compatibility fallback for every difficulty.
        val easyTimeLimitSec = root.optionalInt("easyTimeLimitSec", prefix, issues) ?: timeLimitSec ?: 60
        val mediumTimeLimitSec = root.optionalInt("mediumTimeLimitSec", prefix, issues) ?: timeLimitSec ?: 60
        val hardTimeLimitSec = root.optionalInt("hardTimeLimitSec", prefix, issues) ?: timeLimitSec ?: 60
        val quizTextAuto = root.optionalBoolean("quizTextAuto", prefix, issues) ?: true
        val quizTvSizeInch = root.optionalInt("quizTvSizeInch", prefix, issues) ?: 42
        val quizTextScalePct = root.optionalInt("quizTextScalePct", prefix, issues) ?: 100
        val feedbackJson = root.requiredObject("feedback", prefix, issues)
        val feedback = feedbackJson?.let { parseFeedback(it, field(prefix, "feedback"), issues) }
        val shuffleOptions = root.requiredBoolean("shuffleOptions", prefix, issues)
        val showPinyin = root.requiredBoolean("showPinyin", prefix, issues)
        val sourceOrderJson = root.requiredArray("sourceOrder", prefix, issues)
        val sourceOrder = sourceOrderJson?.let {
            enumArray<QuizQuestionSource>(it, field(prefix, "sourceOrder"), issues)
        }
        val subjectsJson = root.optionalArray("subjects", prefix, issues)
        val subjects = subjectsJson?.let { stringArray(it, field(prefix, "subjects"), issues) } ?: emptyList()
        val legacyJson = root.optionalObject("legacy", prefix, issues)
        val legacy = legacyJson?.let { parseLegacy(it, field(prefix, "legacy"), issues) }
        if (easyTimeLimitSec !in QUIZ_DOC_MIN_TIME_LIMIT_SEC..QUIZ_DOC_MAX_TIME_LIMIT_SEC) {
            issues += QuizDocIssue(field(prefix, "easyTimeLimitSec"), "OUT_OF_RANGE")
        }
        if (mediumTimeLimitSec !in QUIZ_DOC_MIN_TIME_LIMIT_SEC..QUIZ_DOC_MAX_TIME_LIMIT_SEC) {
            issues += QuizDocIssue(field(prefix, "mediumTimeLimitSec"), "OUT_OF_RANGE")
        }
        if (hardTimeLimitSec !in QUIZ_DOC_MIN_TIME_LIMIT_SEC..QUIZ_DOC_MAX_TIME_LIMIT_SEC) {
            issues += QuizDocIssue(field(prefix, "hardTimeLimitSec"), "OUT_OF_RANGE")
        }
        if (quizTvSizeInch !in 24..100) {
            issues += QuizDocIssue(field(prefix, "quizTvSizeInch"), "OUT_OF_RANGE")
        }
        if (quizTextScalePct !in 60..240) {
            issues += QuizDocIssue(field(prefix, "quizTextScalePct"), "OUT_OF_RANGE")
        }
        if (questionCount == null || timeLimitSec == null || feedback == null ||
            shuffleOptions == null || showPinyin == null || sourceOrder == null
        ) {
            return null
        }
        return QuizRules(
            questionCount = questionCount,
            timeLimitSec = timeLimitSec,
            easyTimeLimitSec = easyTimeLimitSec,
            mediumTimeLimitSec = mediumTimeLimitSec,
            hardTimeLimitSec = hardTimeLimitSec,
            quizTextAuto = quizTextAuto,
            quizTvSizeInch = quizTvSizeInch,
            quizTextScalePct = quizTextScalePct,
            feedback = feedback,
            shuffleOptions = shuffleOptions,
            showPinyin = showPinyin,
            sourceOrder = sourceOrder,
            subjects = subjects,
            legacy = legacy,
            extra = collectExtras(root, RULES_KEYS),
        )
    }

    private fun parseFeedback(root: JSONObject, prefix: String, issues: MutableList<QuizDocIssue>): QuizFeedbackRules? {
        val extraHoldSec = root.requiredInt("extraHoldSec", prefix, issues)
        val answerRevealSec = root.requiredInt("answerRevealSec", prefix, issues)
        val autoNext = root.requiredBoolean("autoNext", prefix, issues)
        if (extraHoldSec == null || answerRevealSec == null || autoNext == null) return null
        return QuizFeedbackRules(
            extraHoldSec = extraHoldSec,
            answerRevealSec = answerRevealSec,
            autoNext = autoNext,
            extra = collectExtras(root, FEEDBACK_KEYS),
        )
    }

    private fun parseLegacy(root: JSONObject, prefix: String, issues: MutableList<QuizDocIssue>): QuizLegacyRules =
        QuizLegacyRules(
            minCorrectCount = root.optionalInt("minCorrectCount", prefix, issues) ?: 1,
            minutesPerPoint = root.optionalInt("minutesPerPoint", prefix, issues) ?: 5,
            minutesPerQuestion = root.optionalInt("minutesPerQuestion", prefix, issues) ?: 5,
            pointsPerQuestion = root.optionalInt("pointsPerQuestion", prefix, issues) ?: 1,
            extra = collectExtras(root, LEGACY_KEYS),
        )

    private fun parseReward(root: JSONObject, prefix: String, issues: MutableList<QuizDocIssue>): QuizReward? {
        val mode = root.requiredEnum<QuizRewardMode>("mode", prefix, issues)
        val profilesJson = root.optionalObject("profiles", prefix, issues)
        val profiles = profilesJson?.let { parseProfiles(it, field(prefix, "profiles"), issues) }
            ?: QuizRewardProfiles()
        if (mode == null) return null
        return QuizReward(
            mode = mode,
            profiles = profiles,
            extra = collectExtras(root, REWARD_KEYS),
        )
    }

    private fun parseProfiles(root: JSONObject, prefix: String, issues: MutableList<QuizDocIssue>): QuizRewardProfiles {
        val default = root.optionalObject("default", prefix, issues)?.let {
            parseRewardProfile(it, field(prefix, "default"), issues)
        }
        val weekday = root.optionalObject("weekday", prefix, issues)?.let {
            parseRewardProfile(it, field(prefix, "weekday"), issues)
        }
        val weekend = root.optionalObject("weekend", prefix, issues)?.let {
            parseRewardProfile(it, field(prefix, "weekend"), issues)
        }
        return QuizRewardProfiles(
            default = default,
            weekday = weekday,
            weekend = weekend,
            extra = collectExtras(root, PROFILES_KEYS),
        )
    }

    private fun parseRewardProfile(
        root: JSONObject,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): QuizRewardProfile? {
        val correctJson = root.requiredObject("correctSeconds", prefix, issues)
        val penaltyJson = root.requiredObject("penaltySeconds", prefix, issues)
        val correct = correctJson?.let {
            parseDifficultySeconds(it, field(prefix, "correctSeconds"), issues)
        }
        val penalty = penaltyJson?.let {
            parseDifficultySeconds(it, field(prefix, "penaltySeconds"), issues)
        }
        val maxEarned = root.requiredInt("maxEarnedSeconds", prefix, issues)
        val minUnlock = root.requiredInt("minUnlockSeconds", prefix, issues)
        if (correct == null || penalty == null || maxEarned == null || minUnlock == null) return null
        return QuizRewardProfile(
            correctSeconds = correct,
            penaltySeconds = penalty,
            maxEarnedSeconds = maxEarned,
            minUnlockSeconds = minUnlock,
            extra = collectExtras(root, PROFILE_KEYS),
        )
    }

    private fun parseDifficultySeconds(
        root: JSONObject,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): QuizDifficultySeconds? {
        val easy = root.requiredInt("EASY", prefix, issues)
        val medium = root.requiredInt("MEDIUM", prefix, issues)
        val hard = root.requiredInt("HARD", prefix, issues)
        if (easy == null || medium == null || hard == null) return null
        return QuizDifficultySeconds(
            easy = easy,
            medium = medium,
            hard = hard,
            extra = collectExtras(root, DIFFICULTY_KEYS),
        )
    }

    private fun parseQuota(root: JSONObject, prefix: String, issues: MutableList<QuizDocIssue>): QuizQuota? {
        val daily = root.requiredInt("dailyMaxSeconds", prefix, issues)
        val enforcedBy = root.requiredEnum<QuizQuotaEnforcedBy>("enforcedBy", prefix, issues)
        if (daily == null || enforcedBy == null) return null
        return QuizQuota(
            dailyMaxSeconds = daily,
            enforcedBy = enforcedBy,
            extra = collectExtras(root, QUOTA_KEYS),
        )
    }

    private fun parseCalendar(root: JSONObject, prefix: String, issues: MutableList<QuizDocIssue>): QuizCalendar? {
        val timezone = root.requiredString("timezone", prefix, issues)
        val weekendJson = root.requiredArray("weekendDays", prefix, issues)
        val weekendDays = weekendJson?.let { intArray(it, field(prefix, "weekendDays"), issues) }
        if (timezone == null || weekendDays == null) return null
        return QuizCalendar(
            timezone = timezone,
            weekendDays = weekendDays,
            extra = collectExtras(root, CALENDAR_KEYS),
        )
    }

    private fun parseQuestionPolicy(
        root: JSONObject,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): QuizQuestionPolicy? {
        val allowedJson = root.requiredArray("allowedTypes", prefix, issues)
        val allowedTypes = allowedJson?.let {
            enumArray<QuizQuestionType>(it, field(prefix, "allowedTypes"), issues)
        }
        val judgeMode = root.requiredEnum<QuizJudgeMode>("judgeMode", prefix, issues)
        if (allowedTypes == null || judgeMode == null) return null
        return QuizQuestionPolicy(
            allowedTypes = allowedTypes,
            judgeMode = judgeMode,
            extra = collectExtras(root, QUESTION_POLICY_KEYS),
        )
    }

    private fun parseRequirements(
        root: JSONObject,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): QuizRequirements? {
        val requiredJson = root.optionalArray("required", prefix, issues)
        val required = requiredJson?.let {
            enumArray<QuizCapability>(it, field(prefix, "required"), issues)
        } ?: emptyList()
        val optionalJson = root.optionalArray("optional", prefix, issues)
        val optional = optionalJson?.let {
            enumArray<QuizCapability>(it, field(prefix, "optional"), issues)
        } ?: emptyList()
        return QuizRequirements(
            required = required,
            optional = optional,
            extra = collectExtras(root, REQUIREMENTS_KEYS),
        )
    }

    private fun parseLevels(root: JSONArray, prefix: String, issues: MutableList<QuizDocIssue>): List<QuizLevelDoc>? {
        val levels = ArrayList<QuizLevelDoc>(root.length())
        for (index in 0 until root.length()) {
            val item = root.optJSONObject(index)
            val itemPath = "$prefix[$index]"
            if (item == null) {
                issues += QuizDocIssue(itemPath, "NOT_OBJECT")
                return null
            }
            levels += parseLevel(item, itemPath, index, issues) ?: return null
        }
        return levels
    }

    private fun parseLevel(
        root: JSONObject,
        prefix: String,
        index: Int,
        issues: MutableList<QuizDocIssue>,
    ): QuizLevelDoc? {
        val levelId = root.requiredString("levelId", prefix, issues)
        val origin = root.requiredEnum<QuizLevelOrigin>("origin", prefix, issues)
        val sourcesJson = root.requiredArray("sources", prefix, issues)
        val sources = sourcesJson?.let {
            enumArray<QuizLevelSource>(it, field(prefix, "sources"), issues)
        }
        val subjectsJson = root.optionalArray("subjects", prefix, issues)
        val subjects = subjectsJson?.let {
            stringArray(it, field(prefix, "subjects"), issues)
        } ?: emptyList()
        val unitNosJson = root.optionalArray("unitNos", prefix, issues)
        val unitNos = unitNosJson?.let {
            intArray(it, field(prefix, "unitNos"), issues)
        } ?: emptyList()
        val unitGroupsJson = root.optionalArray("unitGroups", prefix, issues)
        val unitGroups = unitGroupsJson?.let {
            intArray(it, field(prefix, "unitGroups"), issues)
        } ?: emptyList()
        val questionTypesJson = root.optionalArray("questionTypes", prefix, issues)
        val questionTypes = questionTypesJson?.let {
            enumArray<QuizQuestionType>(it, field(prefix, "questionTypes"), issues)
        } ?: emptyList()
        val sourceScopes = root.optionalObject("sourceScopes", prefix, issues)?.let(::copyObject)
        val localScopeJson = root.optionalObject("localScope", prefix, issues)
        val localScope = localScopeJson?.let {
            parseLocalScope(it, field(prefix, "localScope"), issues)
        }
        if (levelId == null || origin == null || sources == null) return null
        return QuizLevelDoc(
            levelId = levelId,
            origin = origin,
            name = root.optionalString("name", prefix, issues) ?: "",
            enabled = root.optionalBoolean("enabled", prefix, issues) ?: true,
            sortNo = root.optionalInt("sortNo", prefix, issues) ?: index,
            sources = sources,
            subjects = subjects,
            scopeType = normalizeCode(root.optionalString("scopeType", prefix, issues)),
            unitNos = unitNos,
            unitGroups = unitGroups,
            grade = root.optionalInt("grade", prefix, issues),
            semester = root.optionalInt("semester", prefix, issues),
            textbookId = root.optionalLong("textbookId", prefix, issues),
            textbook = normalizeCode(root.optionalString("textbook", prefix, issues)),
            difficulty = normalizeCode(root.optionalString("difficulty", prefix, issues)),
            advisoryQuestionCount = root.optionalInt("advisoryQuestionCount", prefix, issues),
            questionTypes = questionTypes,
            sourceScopes = sourceScopes,
            localScope = localScope,
            extra = collectExtras(root, LEVEL_KEYS),
        )
    }

    private fun parseLocalScope(
        root: JSONObject,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): QuizLocalScope? {
        val grade = root.requiredInt("grade", prefix, issues)
        val semester = root.requiredInt("semester", prefix, issues)
        val subject = root.requiredString("subject", prefix, issues)
        val textbook = root.requiredString("textbook", prefix, issues)
        val scopeType = root.requiredString("scopeType", prefix, issues)
        if (grade == null || semester == null || subject == null || textbook == null || scopeType == null) {
            return null
        }
        return QuizLocalScope(
            grade = grade,
            semester = semester,
            subject = subject.trim().uppercase(Locale.ROOT),
            textbook = textbook.trim().uppercase(Locale.ROOT),
            scopeType = scopeType.trim().uppercase(Locale.ROOT),
            unitNo = root.optionalInt("unitNo", prefix, issues),
            difficulty = normalizeCode(root.optionalString("difficulty", prefix, issues)),
            extra = collectExtras(root, LOCAL_SCOPE_KEYS),
        )
    }

    private fun encodeRules(rules: QuizRules): JSONObject {
        val out = copyObject(rules.extra)
        out.put("questionCount", rules.questionCount)
        out.put("timeLimitSec", rules.timeLimitSec)
        out.put("easyTimeLimitSec", rules.easyTimeLimitSec)
        out.put("mediumTimeLimitSec", rules.mediumTimeLimitSec)
        out.put("hardTimeLimitSec", rules.hardTimeLimitSec)
        out.put("quizTextAuto", rules.quizTextAuto)
        out.put("quizTvSizeInch", rules.quizTvSizeInch)
        out.put("quizTextScalePct", rules.quizTextScalePct)
        out.put("feedback", encodeFeedback(rules.feedback))
        out.put("shuffleOptions", rules.shuffleOptions)
        out.put("showPinyin", rules.showPinyin)
        out.put("sourceOrder", enumArray(rules.sourceOrder))
        out.put("subjects", JSONArray(rules.subjects))
        if (rules.legacy != null) out.put("legacy", encodeLegacy(rules.legacy))
        return out
    }

    private fun encodeFeedback(feedback: QuizFeedbackRules): JSONObject {
        val out = copyObject(feedback.extra)
        out.put("extraHoldSec", feedback.extraHoldSec)
        out.put("answerRevealSec", feedback.answerRevealSec)
        out.put("autoNext", feedback.autoNext)
        return out
    }

    private fun encodeLegacy(legacy: QuizLegacyRules): JSONObject {
        val out = copyObject(legacy.extra)
        out.put("minCorrectCount", legacy.minCorrectCount)
        out.put("minutesPerPoint", legacy.minutesPerPoint)
        out.put("minutesPerQuestion", legacy.minutesPerQuestion)
        out.put("pointsPerQuestion", legacy.pointsPerQuestion)
        return out
    }

    private fun encodeReward(reward: QuizReward): JSONObject {
        val out = copyObject(reward.extra)
        out.put("mode", reward.mode.name)
        out.put("profiles", encodeProfiles(reward.profiles))
        return out
    }

    private fun encodeProfiles(profiles: QuizRewardProfiles): JSONObject {
        val out = copyObject(profiles.extra)
        out.put("default", profiles.default?.let(::encodeRewardProfile) ?: JSONObject.NULL)
        out.put("weekday", profiles.weekday?.let(::encodeRewardProfile) ?: JSONObject.NULL)
        out.put("weekend", profiles.weekend?.let(::encodeRewardProfile) ?: JSONObject.NULL)
        return out
    }

    private fun encodeRewardProfile(profile: QuizRewardProfile): JSONObject {
        val out = copyObject(profile.extra)
        out.put("correctSeconds", encodeDifficultySeconds(profile.correctSeconds))
        out.put("penaltySeconds", encodeDifficultySeconds(profile.penaltySeconds))
        out.put("maxEarnedSeconds", profile.maxEarnedSeconds)
        out.put("minUnlockSeconds", profile.minUnlockSeconds)
        return out
    }

    private fun encodeDifficultySeconds(value: QuizDifficultySeconds): JSONObject {
        val out = copyObject(value.extra)
        out.put("EASY", value.easy)
        out.put("MEDIUM", value.medium)
        out.put("HARD", value.hard)
        return out
    }

    private fun encodeQuota(quota: QuizQuota): JSONObject {
        val out = copyObject(quota.extra)
        out.put("dailyMaxSeconds", quota.dailyMaxSeconds)
        out.put("enforcedBy", quota.enforcedBy.name)
        return out
    }

    private fun encodeCalendar(calendar: QuizCalendar): JSONObject {
        val out = copyObject(calendar.extra)
        out.put("timezone", calendar.timezone)
        out.put("weekendDays", intArray(calendar.weekendDays))
        return out
    }

    private fun encodeQuestionPolicy(policy: QuizQuestionPolicy): JSONObject {
        val out = copyObject(policy.extra)
        out.put("allowedTypes", enumArray(policy.allowedTypes))
        out.put("judgeMode", policy.judgeMode.name)
        return out
    }

    private fun encodeRequirements(requirements: QuizRequirements): JSONObject {
        val out = copyObject(requirements.extra)
        out.put("required", enumArray(requirements.required))
        out.put("optional", enumArray(requirements.optional))
        return out
    }

    private fun encodeLevels(levels: List<QuizLevelDoc>): JSONArray {
        val out = JSONArray()
        levels.forEach { out.put(encodeLevel(it)) }
        return out
    }

    private fun encodeLocalScope(scope: QuizLocalScope): JSONObject {
        val out = copyObject(scope.extra)
        out.put("grade", scope.grade)
        out.put("semester", scope.semester)
        out.put("subject", scope.subject)
        out.put("textbook", scope.textbook)
        out.put("scopeType", scope.scopeType)
        out.put("unitNo", scope.unitNo ?: JSONObject.NULL)
        out.put("difficulty", scope.difficulty ?: JSONObject.NULL)
        return out
    }

    private fun JSONObject.requiredString(
        name: String,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): String? {
        if (!has(name) || isNull(name)) {
            issues += QuizDocIssue(field(prefix, name), "MISSING")
            return null
        }
        val value = opt(name)
        if (value !is String) {
            issues += QuizDocIssue(field(prefix, name), "NOT_STRING")
            return null
        }
        return value
    }

    private fun JSONObject.optionalString(
        name: String,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): String? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name)
        if (value !is String) {
            issues += QuizDocIssue(field(prefix, name), "NOT_STRING")
            return null
        }
        return value
    }

    private fun JSONObject.requiredInt(
        name: String,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): Int? {
        if (!has(name) || isNull(name)) {
            issues += QuizDocIssue(field(prefix, name), "MISSING")
            return null
        }
        val value = opt(name)
        if (value !is Number || !value.isIntInRange()) {
            issues += QuizDocIssue(field(prefix, name), "NOT_INTEGER")
            return null
        }
        return value.toInt()
    }

    private fun JSONObject.optionalInt(
        name: String,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): Int? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name)
        if (value !is Number || !value.isIntInRange()) {
            issues += QuizDocIssue(field(prefix, name), "NOT_INTEGER")
            return null
        }
        return value.toInt()
    }

    private fun JSONObject.requiredLong(
        name: String,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): Long? {
        if (!has(name) || isNull(name)) {
            issues += QuizDocIssue(field(prefix, name), "MISSING")
            return null
        }
        val value = opt(name)
        if (value !is Number || value.toDouble() != value.toLong().toDouble()) {
            issues += QuizDocIssue(field(prefix, name), "NOT_INTEGER")
            return null
        }
        return value.toLong()
    }

    private fun JSONObject.optionalLong(
        name: String,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): Long? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name)
        if (value !is Number || value.toDouble() != value.toLong().toDouble()) {
            issues += QuizDocIssue(field(prefix, name), "NOT_INTEGER")
            return null
        }
        return value.toLong()
    }

    private fun JSONObject.requiredBoolean(
        name: String,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): Boolean? {
        if (!has(name) || isNull(name)) {
            issues += QuizDocIssue(field(prefix, name), "MISSING")
            return null
        }
        val value = opt(name)
        if (value !is Boolean) {
            issues += QuizDocIssue(field(prefix, name), "NOT_BOOLEAN")
            return null
        }
        return value
    }

    private fun JSONObject.optionalBoolean(
        name: String,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): Boolean? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name)
        if (value !is Boolean) {
            issues += QuizDocIssue(field(prefix, name), "NOT_BOOLEAN")
            return null
        }
        return value
    }

    private fun JSONObject.requiredObject(
        name: String,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): JSONObject? {
        if (!has(name) || isNull(name)) {
            issues += QuizDocIssue(field(prefix, name), "MISSING")
            return null
        }
        val value = opt(name)
        if (value !is JSONObject) {
            issues += QuizDocIssue(field(prefix, name), "NOT_OBJECT")
            return null
        }
        return value
    }

    private fun JSONObject.optionalObject(
        name: String,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): JSONObject? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name)
        if (value !is JSONObject) {
            issues += QuizDocIssue(field(prefix, name), "NOT_OBJECT")
            return null
        }
        return value
    }

    private fun JSONObject.requiredArray(
        name: String,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): JSONArray? {
        if (!has(name) || isNull(name)) {
            issues += QuizDocIssue(field(prefix, name), "MISSING")
            return null
        }
        val value = opt(name)
        if (value !is JSONArray) {
            issues += QuizDocIssue(field(prefix, name), "NOT_ARRAY")
            return null
        }
        return value
    }

    private fun JSONObject.optionalArray(
        name: String,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): JSONArray? {
        if (!has(name) || isNull(name)) return null
        val value = opt(name)
        if (value !is JSONArray) {
            issues += QuizDocIssue(field(prefix, name), "NOT_ARRAY")
            return null
        }
        return value
    }

    private inline fun <reified T : Enum<T>> JSONObject.requiredEnum(
        name: String,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): T? {
        val text = requiredString(name, prefix, issues) ?: return null
        val value = enumValues<T>().firstOrNull { it.name == text.trim().uppercase(Locale.ROOT) }
        if (value == null) {
            issues += QuizDocIssue(field(prefix, name), "UNKNOWN_VALUE")
            return null
        }
        return value
    }

    private inline fun <reified T : Enum<T>> enumArray(
        root: JSONArray,
        prefix: String,
        issues: MutableList<QuizDocIssue>,
    ): List<T>? {
        val values = ArrayList<T>(root.length())
        var valid = true
        for (index in 0 until root.length()) {
            val item = root.opt(index)
            val itemPath = "$prefix[$index]"
            if (item !is String) {
                issues += QuizDocIssue(itemPath, "NOT_STRING")
                valid = false
                continue
            }
            val parsed = enumValues<T>().firstOrNull { it.name == item.trim().uppercase(Locale.ROOT) }
            if (parsed == null) {
                issues += QuizDocIssue(itemPath, "UNKNOWN_VALUE")
                valid = false
            } else {
                values += parsed
            }
        }
        return if (valid) values else null
    }

    private fun stringArray(root: JSONArray, prefix: String, issues: MutableList<QuizDocIssue>): List<String>? {
        val values = ArrayList<String>(root.length())
        var valid = true
        for (index in 0 until root.length()) {
            val item = root.opt(index)
            if (item !is String) {
                issues += QuizDocIssue("$prefix[$index]", "NOT_STRING")
                valid = false
            } else {
                values += item.trim().uppercase(Locale.ROOT)
            }
        }
        return if (valid) values else null
    }

    private fun intArray(root: JSONArray, prefix: String, issues: MutableList<QuizDocIssue>): List<Int>? {
        val values = ArrayList<Int>(root.length())
        var valid = true
        for (index in 0 until root.length()) {
            val item = root.opt(index)
            if (item !is Number || !item.isIntInRange()) {
                issues += QuizDocIssue("$prefix[$index]", "NOT_INTEGER")
                valid = false
            } else {
                values += item.toInt()
            }
        }
        return if (valid) values else null
    }

    private fun <T : Enum<T>> enumArray(values: List<T>): JSONArray {
        val out = JSONArray()
        values.forEach { out.put(it.name) }
        return out
    }

    private fun intArray(values: List<Int>): JSONArray {
        val out = JSONArray()
        values.forEach { out.put(it) }
        return out
    }

    private fun normalizeCode(value: String?): String? =
        value?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

    private fun Number.isIntInRange(): Boolean {
        val asLong = toLong()
        return toDouble() == asLong.toDouble() &&
            asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    }

    private fun field(prefix: String, name: String): String =
        if (prefix.isEmpty()) name else "$prefix.$name"

    private fun collectExtras(source: JSONObject, known: Set<String>): JSONObject {
        val extra = JSONObject()
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in known) extra.put(key, copyValue(source.opt(key)))
        }
        return extra
    }

    internal fun copyObject(source: JSONObject): JSONObject {
        val out = JSONObject()
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out.put(key, copyValue(source.opt(key)))
        }
        return out
    }

    private fun copyArray(source: JSONArray): JSONArray {
        val out = JSONArray()
        for (index in 0 until source.length()) {
            out.put(copyValue(source.opt(index)))
        }
        return out
    }

    private fun copyValue(value: Any?): Any? = when (value) {
        is JSONObject -> copyObject(value)
        is JSONArray -> copyArray(value)
        else -> value
    }

    private val TOP_LEVEL_KEYS = setOf(
        "schemaVersion", "documentId", "revision", "levelsEtag", "etag", "origin",
        "updatedAt", "updatedBy", "enabled", "rules", "reward", "quota", "calendar",
        "questionPolicy", "requirements", "levels", "extensions",
    )
    private val RULES_KEYS = setOf(
        "questionCount", "timeLimitSec", "easyTimeLimitSec", "mediumTimeLimitSec",
        "hardTimeLimitSec", "quizTextAuto", "quizTvSizeInch", "quizTextScalePct",
        "feedback", "shuffleOptions", "showPinyin", "sourceOrder", "subjects", "legacy",
    )
    private val FEEDBACK_KEYS = setOf("extraHoldSec", "answerRevealSec", "autoNext")
    private val LEGACY_KEYS = setOf(
        "minCorrectCount", "minutesPerPoint", "minutesPerQuestion", "pointsPerQuestion",
    )
    private val REWARD_KEYS = setOf("mode", "profiles")
    private val PROFILES_KEYS = setOf("default", "weekday", "weekend")
    private val PROFILE_KEYS = setOf(
        "correctSeconds", "penaltySeconds", "maxEarnedSeconds", "minUnlockSeconds",
    )
    private val DIFFICULTY_KEYS = setOf("EASY", "MEDIUM", "HARD")
    private val QUOTA_KEYS = setOf("dailyMaxSeconds", "enforcedBy")
    private val CALENDAR_KEYS = setOf("timezone", "weekendDays")
    private val QUESTION_POLICY_KEYS = setOf("allowedTypes", "judgeMode")
    private val REQUIREMENTS_KEYS = setOf("required", "optional")
    private val LEVEL_KEYS = setOf(
        "levelId", "origin", "name", "enabled", "sortNo", "sources", "subjects",
        "scopeType", "unitNos", "unitGroups", "grade", "semester", "textbookId",
        "textbook", "difficulty", "advisoryQuestionCount", "questionTypes",
        "sourceScopes", "localScope",
    )
    private val LOCAL_SCOPE_KEYS = setOf(
        "grade", "semester", "subject", "textbook", "scopeType", "unitNo", "difficulty",
    )
}
