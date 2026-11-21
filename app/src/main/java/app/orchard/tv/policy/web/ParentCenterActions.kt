package app.orchard.tv.policy.web

import android.content.Context
import app.orchard.tv.BuildConfig
import app.orchard.tv.policy.FamilyAvatarStore
import app.orchard.tv.policy.FamilyHub
import app.orchard.tv.policy.LocalPolicyRuntime
import app.orchard.tv.policy.PolicyInstalledApps
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.PolicyProduct
import app.orchard.tv.policy.SourceEntrySettings
import app.orchard.tv.policy.SubjectSwitcher
import app.orchard.tv.policy.UserConfigPinSettings
import app.orchard.tv.policy.media.OfficialAudioCatalog
import app.orchard.tv.policy.media.OfficialAudioDownloader
import app.orchard.tv.policy.media.OfficialMediaCatalog
import app.orchard.tv.policy.media.OfficialMediaDownloader
import app.orchard.tv.policy.media.PolicyVideoLibrary
import app.orchard.tv.policy.media.PolicyVideoRole
import app.orchard.tv.quiz.QuizAccess
import app.orchard.tv.quiz.QuizIntroVideo
import app.orchard.tv.quiz.QuizSettings
import app.orchard.tv.quiz.QuizSubjectScope
import app.orchard.tv.update.Updates
import com.planyourtv.policy.FamilyIds
import com.planyourtv.policy.FamilyMember
import com.planyourtv.policy.LocalDayGroup
import com.planyourtv.policy.LocalPin
import com.planyourtv.policy.LocalRecovery
import com.planyourtv.policy.LocalRules
import com.planyourtv.policy.LocalRulesError
import com.planyourtv.policy.LocalTimeWindow
import com.planyourtv.policy.MemberPin
import com.planyourtv.policy.MemberStatus
import com.planyourtv.policy.PinResult
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicyRuntime
import com.planyourtv.policy.RemoteHandoffState
import com.planyourtv.policy.SessionState
import com.planyourtv.policy.SwitchPinExempt
import com.planyourtv.policy.UnlockRoute
import com.planyourtv.policy.UnlockRoutePlan
import com.planyourtv.policy.UnlockRoutePlanner
import com.planyourtv.policy.quiz.QuizCurriculum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * State projection and the only write path of the web parent center.
 *
 * Every mutating method re-validates the same conditions the TV parent center enforces. The
 * HTTP layer is responsible for the editor lease, the request revisions and the body limit;
 * this object never trusts the page for mode, scope or read-only state.
 *
 * Stale writes are rejected with `expected*Revision` compared against the live revisions, so a
 * TV-side edit or a cloud mirror cannot be silently overwritten by an old tab.
 */
object ParentCenterActions {
    const val EDITOR_REQUIRED = "EDITOR_REQUIRED"

    /**
     * Web rules contract. V5 makes the three child unlock routes independent, adds the shared
     * public per-session cap and one daily count per route. V3/V4 pages cannot express those
     * fields and are rejected so they reload before writing.
     */
    const val RULES_CONTRACT_VERSION = 5
    private const val MIN_RULES_CONTRACT_VERSION = 5

    data class Outcome(
        val ok: Boolean,
        val code: String,
        val messageKey: String,
        val retryAfterSeconds: Long = 0L,
        val recoveryCode: String? = null,
        val snapshot: JSONObject? = null,
    )

    private const val MODE_SWITCH_TIMEOUT_MS = 25_000L
    private const val DEFAULT_PIN_CACHE_MS = 60_000L

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var defaultPinCached = false
    @Volatile
    private var defaultPinCheckedAt = 0L

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun stop() {
        scope.cancel()
        appContext = null
    }

    // ---------------------------------------------------------------- snapshot

    fun snapshot(context: Context): JSONObject {
        val ui = PolicyModeCoordinator.state.value
        val cloud = PolicyRuntime.state.value
        val remote = PolicyRuntime.remotePolicy.value
        val bound = PolicyProduct.cloudEnabled && cloud.bound
        val handoffDone = bound && remote?.handoffState == RemoteHandoffState.DONE
        val readOnlyLocal = bound
        val childOn = ui.mode != PolicyMode.NONE
        val localSessionActive =
            ui.mode == PolicyMode.LOCAL && ui.sessionState == SessionState.ACTIVE
        val rules = LocalPolicyRuntime.currentRules ?: LocalRules.DEFAULTS
        val ruleSets = LocalPolicyRuntime.currentRuleSets
        val family = FamilyHub.state.value

        val result = JSONObject()
            .put("ok", true)
            // APP filing number, injected at build time. Empty in community builds; the web UI
            // hides the row, and the phone shell hides it until its own filing is added.
            .put("appRecordNumber", BuildConfig.APP_RECORD_NUMBER)
            .put("mode", ui.mode.name)
            .put("loaded", ui.loaded)
            .put("guardState", ui.guardState.name)
            .put("sessionState", ui.sessionState.name)
            .put("activation", ui.activation.name)
            .put("source", ui.source.name)
            .put("setupRequired", ui.setupRequired)
            .put("pinConfigured", ui.pinConfigured)
            .put("defaultPin", defaultPinCached())
            .put(
                "parentUnlockRemainingSeconds",
                PolicyModeCoordinator.parentProofRemainingMs() / 1000,
            )
            .put("rulesRevision", LocalPolicyRuntime.rulesEpoch)
            .put("modeRevision", modeRevision())
            .put("autoUpdate", Updates.auto)
            .put("update", updateJson(context))
            .put("sourceEntriesVisible", SourceEntrySettings.isVisible())
            // Defaults to true: the web user-configuration tab keeps asking for the parent PIN.
            .put("userConfigRequirePin", UserConfigPinSettings.isRequired())
            .put("quizEntryVisible", QuizSettings.isLockEntryVisible())
            .put("introVideo", QuizIntroVideo.snapshot(context))
            // The main page only needs the current roles. Opening "Videos & wallpapers"
            // triggers GET /api/parent/media, which is the one place that refreshes the
            // catalogue, so merely opening the parent center never contacts the media host.
            .put("media", PolicyVideoLibrary.snapshot(context, autoRefreshCatalog = false))
            .put("pinSyncPending", PolicyRuntime.isPasswordSyncPending())
            .put("cloudEnabled", PolicyProduct.cloudEnabled)
            .put("deviceNo", cloud.deviceNo)
            .put("bound", bound)
            .put("readOnlyLocal", readOnlyLocal)
            .put("handoffDone", handoffDone)
            .put("childModeOn", childOn)
            .put("localSessionActive", localSessionActive)
            .put("family", familyJson(family))
            // The member sheet offers the curriculum's own grade list, so every language
            // shows the same labels as the quiz page without duplicating them in JS.
            .put("gradeOptions", gradeOptionsJson())
            .put(
                "members",
                JSONArray().also { array ->
                    family.members
                        .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
                        .forEach { array.put(memberJson(it, family)) }
                },
            )
            .put(
                "activeSubject",
                if (family.ready) {
                    JSONObject()
                        .put("familyId", family.familyId)
                        .put("userId", family.activeUserId)
                        .put("epoch", family.activeEpoch)
                } else {
                    JSONObject.NULL
                },
            )
            .put("rules", rulesJson(rules))
            .put(
                "capabilities",
                JSONObject()
                    .put("canEditRules", ui.mode == PolicyMode.LOCAL && !readOnlyLocal)
                    // Child-mode off and first-PIN setup stay on the TV: they change the
                    // authorization the web page itself depends on.
                    .put("canToggleChildMode", false)
                    .put("canChangePin", ui.mode == PolicyMode.LOCAL && !readOnlyLocal)
                    .put("canToggleQuizEntry", ui.mode == PolicyMode.LOCAL && !readOnlyLocal)
                    .put("canToggleSources", ui.mode == PolicyMode.LOCAL && !readOnlyLocal)
                    .put("canEditIntroVideos", ui.mode == PolicyMode.LOCAL && !readOnlyLocal)
                    .put("canEndSession", localSessionActive)
                    .put("canSetupPin", false)
                    // Family profiles are local identity, not a policy template. They follow the
                    // same LOCAL/unbound restriction as the rule editor.
                    .put("canEditFamily", ui.mode == PolicyMode.LOCAL && !readOnlyLocal)
                    .put(
                        "canAddMember",
                        family.canAddMember && ui.mode == PolicyMode.LOCAL && !readOnlyLocal,
                    )
                    // Remote switching shares SubjectSwitcher's lock-screen/busy gate with the
                    // TV; it is offered wherever the family editor is available.
                    .put(
                        "canSwitchSubject",
                        family.ready && family.canSwitchMember &&
                            ui.mode == PolicyMode.LOCAL && !readOnlyLocal,
                    )
                    .put("cloudEnabled", PolicyProduct.cloudEnabled),
            )
            .put(
                "usage",
                JSONObject()
                    .put("totalSeconds", ui.totalSeconds)
                    .put("remainingSeconds", ui.remainingSeconds)
                    .put("effectiveRemainingSeconds", ui.effectiveRemainingSeconds)
                    .put("dailyUsedSeconds", ui.dailyUsedSeconds)
                    .put("dailyLimitSeconds", ui.dailyLimitSeconds)
                    .put("studyDailyUsedSeconds", ui.studyDailyUsedSeconds)
                    .put("studyDailyLimitSeconds", ui.studyDailyLimitSeconds)
                    .put("studyRemainingSeconds", ui.studyRemainingSeconds)
                    .put("studyLimitUnlimited", ui.studyLimitUnlimited)
                    .put("currentDayGroupLabel", ui.currentDayGroupLabel)
                    .put("sessionLimitSeconds", ui.sessionLimitSeconds)
                    .put("sessionCapSeconds", ui.sessionCapSeconds)
                    // Read-only day progress for the TV popup/other surfaces. The configuration
                    // page itself must only render configured values, never today's usage.
                    .put("routeCounts", routeCountsJson(ui.unlockRoutePlan, rules))
                    .put("configuredRestMinutes", ui.configuredRestMinutes)
                    .put("configuredBufferMinutes", ui.configuredBufferMinutes)
                    .put("restRemainingSeconds", ui.restRemainingSeconds)
                    .put("lockReason", ui.lockReason.name)
                    .put("nextWindowLabel", ui.nextWindowLabel)
                    .put("todayWindowsLabel", ui.todayWindowsLabel)
                    .put("windowRemainingSeconds", ui.windowRemainingSeconds)
                    .put("nextWindowInSeconds", ui.nextWindowInSeconds),
            )
            .put("control", ParentControlActions.statusJson())
        if (ruleSets != null) {
            result.put(
                "ruleSets",
                JSONObject().put("manual", ruleSetJson(ruleSets.manual)),
            )
        }
        return result
    }

    /**
     * The TV's software update, as the web "More" page shows it. The update code owns the flow;
     * this projection only tells the page what state to render and which manual fallback exists.
     */
    private fun updateJson(context: Context): JSONObject {
        val app = context.applicationContext
        val install = Updates.install
        val state = when (install) {
            Updates.Install.Idle -> "idle"
            is Updates.Install.Downloading -> "downloading"
            Updates.Install.Waiting -> "waiting"
            Updates.Install.Opened -> "opened"
            Updates.Install.NeedsPermission -> "needsPermission"
            Updates.Install.NeedsStoragePermission -> "needsStoragePermission"
            is Updates.Install.Failed -> "failed"
        }
        val failure = (install as? Updates.Install.Failed)?.reason?.let {
            when (it) {
                Updates.Failure.Download -> "download"
                Updates.Failure.Blocked -> "blocked"
                Updates.Failure.NotOfficial -> "notOfficial"
                Updates.Failure.NotNewer -> "notNewer"
            }
        }
        val notes = JSONArray()
        Updates.notes.take(5).forEach { release ->
            val changes = JSONArray()
            release.changes.take(8).forEach(changes::put)
            notes.put(JSONObject().put("version", release.version).put("changes", changes))
        }
        return JSONObject()
            .put("current", Updates.current)
            .put("available", Updates.available ?: JSONObject.NULL)
            .put("checking", Updates.checking)
            .put("checkFailed", Updates.checkFailed)
            .put("lastCheckedAt", Updates.lastCheckedAt())
            .put("installState", state)
            .put("installFraction", (install as? Updates.Install.Downloading)?.fraction ?: 0.0)
            .put("installFailure", failure ?: JSONObject.NULL)
            .put("auto", Updates.auto)
            .put("autoReady", Updates.autoReady ?: JSONObject.NULL)
            .put("autoBusy", Updates.autoBusy)
            .put("autoFailure", Updates.autoFailure ?: JSONObject.NULL)
            .put("autoSupported", Updates.silentInstallSupported(app))
            .put("installPermissionMissing", Updates.installPermissionMissing(app))
            .put("manualSupported", Updates.canInstall(app))
            .put("notes", notes)
    }

    private fun familyJson(family: FamilyHub.UiState): JSONObject = JSONObject()
        .put("available", family.ready)
        .put("id", family.familyId)
        .put("name", family.familyName)
        .put("revision", family.revision)
        .put("defaultUserId", family.defaultUserId)
        .put("activeUserId", family.activeUserId)
        .put("activeEpoch", family.activeEpoch)
        .put("canAddMember", family.canAddMember)
        .put("error", family.error)

    private fun memberJson(member: FamilyMember, family: FamilyHub.UiState): JSONObject {
        val avatarUrl = member.avatarImage?.let { path ->
            "/api/parent/avatar?userId=" + member.userId +
                "&rev=" + member.avatarRevision +
                "&name=" + path.substringAfterLast('/')
        }
        return JSONObject()
            .put("userId", member.userId)
            .put("familyId", member.familyId)
            .put("name", member.displayName)
            .put("avatarPreset", member.avatarPreset)
            .put("avatarRevision", member.avatarRevision)
            .put("avatarUrl", avatarUrl ?: JSONObject.NULL)
            .put("role", member.role.name)
            .put("source", member.source.name)
            .put("status", member.status.name)
            .put("sortOrder", member.sortOrder)
            .put("grade", member.grade ?: JSONObject.NULL)
            .put("semester", member.semester ?: JSONObject.NULL)
            // Only the "still initial" flag, never the salt or hash.
            .put("switchPinInitial", member.switchPinInitial)
            .put("switchPinRequired", member.switchPinRequired)
            .put("switchPinExemptDays", JSONArray(member.switchPinExemptDays))
            .put("switchPinExemptStartMinute", member.switchPinExemptStartMinute)
            .put("switchPinExemptEndMinute", member.switchPinExemptEndMinute)
            .put("isDefault", member.userId == family.defaultUserId)
            .put("isActive", member.userId == family.activeUserId)
    }

    private fun gradeOptionsJson(): JSONArray = JSONArray().apply {
        val curriculum = QuizCurriculum.current() ?: return@apply
        curriculum.grades.sortedBy { it.no }.forEach { grade ->
            put(
                JSONObject()
                    .put("no", grade.no)
                    .put(
                        "labels",
                        JSONObject().also { labels ->
                            grade.labels.forEach { (language, value) ->
                                labels.put(language, value)
                            }
                        },
                    ),
            )
        }
    }

    private fun ruleSetJson(set: com.planyourtv.policy.LocalRuleSet): JSONObject = JSONObject()
        .put("dayGroups", dayGroupsJson(set.dayGroups))
        .put("weekdayWindows", windowsJson(set.weekdayWindows))
        .put("weekendWindows", windowsJson(set.weekendWindows))
        .put("autoDailyLimitMinutes", set.autoDailyLimitMinutes)
        .put("weekendDailyLimitMinutes", set.weekendDailyLimitMinutes)
        .put("sessionLimitMinutes", set.sessionLimitMinutes)
        .put("sessionCapMinutes", set.sessionCapMinutes)
        .put("quizDailyLimitCount", set.quizDailyLimitCount)
        .put("studyRewardDailyLimitCount", set.studyRewardDailyLimitCount)
        .put("directDailyFreeCount", set.directDailyFreeCount)
        .put("restMinutes", set.restMinutes)
        .put("weekendRestMinutes", set.weekendRestMinutes ?: JSONObject.NULL)
        .put("sessionScreenOffBufferMinutes", set.sessionScreenOffBufferMinutes ?: JSONObject.NULL)
        .put(
            "weekendSessionScreenOffBufferMinutes",
            set.weekendSessionScreenOffBufferMinutes ?: JSONObject.NULL,
        )
        .put("quizGateEnabled", set.quizGateEnabled)
        .put("studyEarnEnabled", set.studyEarnEnabled)
        .put("directStartEnabled", set.directStartEnabled)

    /** Read-only projection of today's route counts; the configuration page never renders it. */
    private fun routeCountsJson(plan: UnlockRoutePlan?, rules: LocalRules): JSONObject {
        val result = JSONObject().put("date", plan?.date ?: LocalDate.now().toString())
        UnlockRoute.entries.forEach { route ->
            val decision = plan?.decision(route)
            val limit = decision?.dailyLimit ?: rules.dailyLimitFor(route)
            val used = decision?.used ?: 0
            result.put(
                routeKey(route),
                JSONObject()
                    .put("used", used)
                    .put("limit", limit)
                    .put(
                        "remaining",
                        decision?.remaining
                            ?: if (limit < 0) UnlockRoutePlanner.UNLIMITED_REMAINING
                            else (limit - used).coerceAtLeast(0),
                    ),
            )
        }
        return result
    }

    private fun routeKey(route: UnlockRoute): String = when (route) {
        UnlockRoute.QUIZ -> "quiz"
        UnlockRoute.STUDY_REWARD -> "studyReward"
        UnlockRoute.DIRECT -> "directFree"
    }

    fun rulesJson(rules: LocalRules): JSONObject = JSONObject()
        .put("rulesContractVersion", RULES_CONTRACT_VERSION)
        .put("dayGroups", dayGroupsJson(rules.dayGroups))
        .put("weekdayWindows", windowsJson(rules.weekdayWindows))
        .put("weekendWindows", windowsJson(rules.weekendWindows))
        .put("autoDailyLimitMinutes", rules.autoDailyLimitMinutes)
        .put("weekendDailyLimitMinutes", rules.weekendDailyLimitMinutes)
        .put("sessionLimitMinutes", rules.sessionLimitMinutes)
        // V5 keeps the old alias for readers that still use the V4 field name.
        .put("directSessionMinutes", rules.sessionLimitMinutes)
        .put("sessionCapMinutes", rules.sessionCapMinutes)
        .put("quizDailyLimitCount", rules.quizDailyLimitCount)
        .put("studyRewardDailyLimitCount", rules.studyRewardDailyLimitCount)
        .put("directDailyFreeCount", rules.directDailyFreeCount)
        .put("restMinutes", rules.restMinutes)
        .put("weekendRestMinutes", rules.weekendRestMinutes)
        .put("sessionScreenOffBufferMinutes", rules.sessionScreenOffBufferMinutes)
        .put(
            "weekendSessionScreenOffBufferMinutes",
            rules.weekendSessionScreenOffBufferMinutes,
        )
        .put("allApps", rules.allowsAllEntertainmentApps)
        .put(
            "allowedPackages",
            JSONArray(rules.effectiveAllowedPackages - ALL_VALID_APP_SENTINEL),
        )
        .put("studyPackages", JSONArray(rules.effectiveStudyPackages))
        .put("unlimitedPackages", JSONArray(rules.effectiveUnlimitedPackages))
        .put("studyEarnPackages", JSONArray(rules.effectiveStudyEarnPackages))
        .put("studyEarnStudyMinutes", rules.studyEarnStudyMinutes)
        .put("studyEarnRewardMinutes", rules.studyEarnRewardMinutes)
        .put("quizGateEnabled", rules.quizGateEnabled)
        .put("studyEarnEnabled", rules.studyEarnEnabled)
        .put("directStartEnabled", rules.directStartEnabled)

    private fun windowsJson(windows: List<LocalTimeWindow>): JSONArray {
        val array = JSONArray()
        windows.forEach { window ->
            array.put(JSONObject().put("start", window.startMinute).put("end", window.endMinute))
        }
        return array
    }

    /** Schema-3 schedule. `null` is an unlimited quota; zero is a finite, locked quota. */
    private fun dayGroupsJson(groups: List<com.planyourtv.policy.LocalDayGroup>): JSONArray {
        val array = JSONArray()
        groups.forEach { group ->
            array.put(
                JSONObject()
                    .put("days", JSONArray(group.days.sorted()))
                    .put("studyWindows", windowsJson(group.studyWindows))
                    .put(
                        "studyDailyLimitMinutes",
                        group.studyDailyLimitMinutes ?: JSONObject.NULL,
                    )
                    .put("entertainmentWindows", windowsJson(group.entertainmentWindows))
                    .put(
                        "entertainmentDailyLimitMinutes",
                        group.entertainmentDailyLimitMinutes ?: JSONObject.NULL,
                    ),
            )
        }
        return array
    }

    fun modeRevision(): String {
        val ui = PolicyModeCoordinator.state.value
        val cloud = PolicyRuntime.state.value
        val remote = PolicyRuntime.remotePolicy.value
        val seed = listOf(
            ui.mode.name,
            ui.guardState.name,
            ui.sessionState.name,
            ui.activation.name,
            ui.source.name,
            ui.setupRequired.toString(),
            ui.pinConfigured.toString(),
            cloud.bound.toString(),
            remote?.handoffState?.name ?: "NONE",
            remote?.mode?.name ?: "NONE",
            (remote?.configVersion ?: 0L).toString(),
            ui.lockReason.name,
        ).joinToString("|")
        return ParentWebCrypto.sha256Hex(seed).take(16)
    }

    // ------------------------------------------------------------------- writes

    fun setRules(body: JSONObject): Outcome {
        val ui = PolicyModeCoordinator.state.value
        if (ui.mode != PolicyMode.LOCAL) return error("LOCAL_ONLY")
        if (PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound) {
            return error("READ_ONLY")
        }
        val expected = body.optLong("expectedRulesRevision", -1L)
        if (expected < 0L || expected != LocalPolicyRuntime.rulesEpoch) {
            return conflict("STATE_CHANGED")
        }
        val before = LocalPolicyRuntime.currentRules
        val rulesBody = body.optJSONObject("rules") ?: return error("RULES_INVALID")
        // V42 has no compatibility path for the schedule: a page that cannot express groups must
        // reload rather than have its flat weekday/weekend projection silently merged into the
        // group schedule. V44 raises the contract to V5: a V3/V4 page cannot express the route
        // counts or the shared cap and must reload before writing.
        val contract = rulesBody.optInt("rulesContractVersion", 0)
        if (contract !in MIN_RULES_CONTRACT_VERSION..RULES_CONTRACT_VERSION ||
            !rulesBody.has("dayGroups")
        ) {
            return error("RULES_CLIENT_OUTDATED")
        }
        val next = when (val parsed = parseRules(rulesBody, before)) {
            is ParsedRules.Valid -> parsed.rules
            is ParsedRules.Invalid -> return error(parsed.code)
        }
        val validation = next.validationError()
        if (validation != LocalRulesError.NONE) {
            val code = when (validation) {
                LocalRulesError.OVERLAPPING_WINDOWS -> "RULES_OVERLAP"
                // Both are only reachable from a day group in schema 3.
                LocalRulesError.TOO_MANY_WINDOWS,
                LocalRulesError.INVALID_WINDOW,
                LocalRulesError.INVALID_DAY_GROUPS,
                -> "RULES_INVALID_DAY_GROUPS"
                LocalRulesError.INVALID_ALLOWED_PACKAGES -> "RULES_INVALID_APPS"
                LocalRulesError.INVALID_STUDY_PACKAGES -> "RULES_INVALID_STUDY_APPS"
                LocalRulesError.INVALID_UNLIMITED_PACKAGES -> "RULES_INVALID_UNLIMITED_APPS"
                LocalRulesError.INVALID_STUDY_EARN -> "RULES_INVALID_STUDY_EARN"
                else -> "RULES_INVALID"
            }
            return error(code)
        }
        if (!LocalPolicyRuntime.setRules(next)) return error("RULES_INVALID")
        val scopeChanged = before == null ||
            before.effectiveAllowedPackages != next.effectiveAllowedPackages ||
            before.effectiveStudyPackages != next.effectiveStudyPackages ||
            before.effectiveUnlimitedPackages != next.effectiveUnlimitedPackages ||
            before.effectiveStudyEarnPackages != next.effectiveStudyEarnPackages ||
            before.studyEarnStudyMinutes != next.studyEarnStudyMinutes ||
            before.studyEarnRewardMinutes != next.studyEarnRewardMinutes ||
            before.quizGateEnabled != next.quizGateEnabled ||
            before.studyEarnEnabled != next.studyEarnEnabled ||
            before.directStartEnabled != next.directStartEnabled ||
            before.quizDailyLimitCount != next.quizDailyLimitCount ||
            before.studyRewardDailyLimitCount != next.studyRewardDailyLimitCount ||
            before.directDailyFreeCount != next.directDailyFreeCount
        // Time rules are a next-session snapshot. Check them first: when one save mixes both
        // kinds, the deferred half is the part a parent must not misread as already live.
        val timeRulesChanged = before != null && (
            before.dayGroups != next.dayGroups ||
            before.sessionLimitMinutes != next.sessionLimitMinutes ||
            before.sessionCapMinutes != next.sessionCapMinutes ||
            before.restMinutes != next.restMinutes ||
            before.sessionScreenOffBufferMinutes != next.sessionScreenOffBufferMinutes
        )
        val messageKey = when {
            timeRulesChanged -> "SAVED_NEXT_SESSION"
            scopeChanged -> "APP_SCOPE_SAVED"
            else -> "SAVED"
        }
        return ok(messageKey)
    }

    fun setChildMode(body: JSONObject): Outcome {
        val ui = PolicyModeCoordinator.state.value
        val expectedMode = body.optString("expectedModeRevision", "")
        if (expectedMode.isNotBlank() && expectedMode != modeRevision()) {
            return conflict("STATE_CHANGED")
        }
        val enabled = body.optBoolean("enabled", false)
        if (enabled) return enableChildMode(body)
        return disableChildMode(body)
    }

    private fun enableChildMode(body: JSONObject): Outcome {
        if (!PolicyModeCoordinator.state.value.loaded) return error("NOT_READY")
        if (PolicyModeCoordinator.mode != PolicyMode.NONE) {
            return ok("CHILD_MODE_ON")
        }
        val needsPin = runCatching { PolicyModeCoordinator.needsLocalPinSetup() }
            .getOrDefault(true)
        var recoveryCode: String? = null
        if (needsPin) {
            val pin = body.optString("pin", "")
            if (!LocalPin.isValid(pin)) return error("SETUP_PIN_REQUIRED")
            if (PolicyModeCoordinator.isDefaultPinValue(pin)) return error("PIN_TOO_SIMPLE")
            recoveryCode = PolicyModeCoordinator.completeLocalSetup(pin)
                ?: return error("PIN_SETUP_FAILED")
            invalidateDefaultPinCache()
        }
        val bound = PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound
        val target = if (bound) PolicyMode.CLOUD else PolicyMode.LOCAL
        val result = runBlocking {
            withTimeoutOrNull(MODE_SWITCH_TIMEOUT_MS) {
                PolicyModeCoordinator.requestModeSwitch(target, "", invalidateWeb = false)
            }
        } ?: return error("TIMEOUT")
        return switchOutcome(result, "CHILD_MODE_ON", recoveryCode)
    }

    private fun disableChildMode(body: JSONObject): Outcome {
        val mode = PolicyModeCoordinator.mode
        if (mode == PolicyMode.NONE) return ok("CHILD_MODE_OFF")
        val supplied = body.optString("pin", "")
        val pin = supplied.ifBlank { PolicyModeCoordinator.sharedParentPin().orEmpty() }
        if (!LocalPin.isValid(pin)) return error("PIN_REQUIRED")
        val result = runBlocking {
            withTimeoutOrNull(MODE_SWITCH_TIMEOUT_MS) {
                PolicyModeCoordinator.requestModeSwitch(
                    PolicyMode.NONE,
                    pin,
                    invalidateWeb = false,
                )
            }
        } ?: return error("TIMEOUT")
        return switchOutcome(result, "CHILD_MODE_OFF", null)
    }

    private fun switchOutcome(
        result: PolicyModeCoordinator.SwitchResult,
        successKey: String,
        recoveryCode: String?,
    ): Outcome = when (result) {
        PolicyModeCoordinator.SwitchResult.Success ->
            ok(successKey).copy(recoveryCode = recoveryCode)
        is PolicyModeCoordinator.SwitchResult.WrongPin -> error("WRONG_PIN")
        PolicyModeCoordinator.SwitchResult.PendingMustBeCanceled -> error("PENDING_MUST_BE_CANCELED")
        is PolicyModeCoordinator.SwitchResult.CloudSettlementFailed ->
            error("CLOUD_SETTLEMENT_FAILED")
        PolicyModeCoordinator.SwitchResult.NotReady -> error("NOT_READY")
    }

    /** First-run PIN setup without switching the guard on. */
    fun setupPin(body: JSONObject): Outcome {
        if (PolicyModeCoordinator.mode != PolicyMode.NONE) return error("MODE_NOT_NONE")
        val pin = body.optString("pin", "")
        if (!LocalPin.isValid(pin)) return error("PIN_INVALID")
        if (PolicyModeCoordinator.isDefaultPinValue(pin)) return error("PIN_TOO_SIMPLE")
        val recovery = PolicyModeCoordinator.completeLocalSetup(pin)
            ?: return error("PIN_SETUP_FAILED")
        invalidateDefaultPinCache()
        return ok("PIN_SAVED").copy(recoveryCode = recovery)
    }

    fun changePin(body: JSONObject): Outcome {
        val ui = PolicyModeCoordinator.state.value
        if (ui.mode != PolicyMode.LOCAL) return error("LOCAL_ONLY")
        if (PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound) {
            return error("READ_ONLY")
        }
        val newPin = body.optString("newPin", "")
        if (!LocalPin.isValid(newPin)) return error("PIN_INVALID")
        if (PolicyModeCoordinator.isDefaultPinValue(newPin)) return error("PIN_TOO_SIMPLE")
        // The login is the identity; only a password change asks for the current password
        // again. There is no shared/blank fallback.
        val oldPin = body.optString("oldPin", "")
        if (!LocalPin.isValid(oldPin)) return error("OLD_PIN_REQUIRED")
        if (oldPin == newPin) return error("PIN_UNCHANGED")
        if (!PolicyModeCoordinator.changePin(oldPin, newPin)) return error("WRONG_PIN")
        invalidateDefaultPinCache()
        return ok("PIN_SAVED")
    }

    /**
     * Forgot-password reset from the pre-login page. The one-time recovery code shown at setup
     * is the only proof; it never replaces the normal login after the PIN is known. A successful
     * reset also clears both in-memory and persisted PIN lockouts so the parent can log in with
     * the new PIN immediately, and revokes every web session opened under the old PIN.
     */
    fun recoverPin(body: JSONObject): Outcome {
        val ui = PolicyModeCoordinator.state.value
        if (ui.mode != PolicyMode.LOCAL) return error("LOCAL_ONLY")
        if (PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound) {
            return error("READ_ONLY")
        }
        val newPin = body.optString("newPin", "")
        // Validate the replacement before spending a recovery attempt on a malformed request.
        if (!LocalPin.isValid(newPin)) return error("NEW_PIN_INVALID")
        if (PolicyModeCoordinator.isDefaultPinValue(newPin)) return error("PIN_TOO_SIMPLE")
        val code = LocalRecovery.normalize(body.optString("recoveryCode", ""))
            ?: return error("RECOVERY_INVALID")
        return when (LocalPolicyRuntime.verifyRecovery(code)) {
            PinResult.OK -> {
                if (!LocalPolicyRuntime.resetWithRecovery(code, newPin)) {
                    return error("RECOVERY_FAILED")
                }
                QuizAccess.onPinReset()
                QuizAccess.invalidateWebSession("pin_reset")
                ParentWebAccess.invalidate("pin_reset")
                invalidateDefaultPinCache()
                ok("PIN_RESET")
            }
            PinResult.LOCKED_OUT -> Outcome(
                ok = false,
                code = "RECOVERY_LOCKED",
                messageKey = "RECOVERY_LOCKED",
                retryAfterSeconds = LocalPolicyRuntime.recoveryLockSeconds(),
            )
            PinResult.NOT_SET -> error("RECOVERY_NOT_SET")
            PinResult.WRONG -> error("RECOVERY_INVALID")
        }
    }

    fun setAutoUpdate(body: JSONObject): Outcome {
        val context = appContext ?: return error("NOT_READY")
        val enabled = body.optBoolean("enabled", false)
        Updates.setAuto(context, enabled)
        return ok("AUTO_UPDATE_SAVED")
    }

    /** A deliberate "check now" from the "More" page. */
    fun requestUpdateCheck(): Outcome {
        if (appContext == null) return error("NOT_READY")
        if (!Updates.requestCheck()) return error("UPDATE_BUSY")
        return ok("UPDATE_CHECKING")
    }

    /**
     * The manual fallback after a failed or unavailable silent update. The download and the
     * installer continue on the TV; the phone page polls the snapshot for progress.
     */
    fun installUpdate(): Outcome {
        val context = appContext ?: return error("NOT_READY")
        if (!Updates.canInstall(context)) return error("UPDATE_UNSUPPORTED")
        if (Updates.available == null) return error("UPDATE_NONE")
        if (!Updates.requestInstall(context)) return error("UPDATE_BUSY")
        return ok("UPDATE_STARTED")
    }

    /**
     * Family profiles are local identity, not a cloud-owned policy. The page still needs the
     * editor lease; this method repeats the mode/binding check for the raw avatar route too.
     */
    fun canEditFamily(): Boolean {
        val ui = PolicyModeCoordinator.state.value
        // Cloud mode is single-member until the backend carries per-member policy. The web
        // capabilities already hide the editor there; this check keeps a raw request honest.
        if (ui.mode != PolicyMode.LOCAL || !ui.loaded) return false
        if (PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound) return false
        return FamilyHub.isReady
    }

    /**
     * Adds a local child without changing which member the TV executes. The member always
     * starts from clean local defaults: no rules, quiz settings, level packages or AI
     * generation parameters are copied from a sibling, so a mismatched grade/textbook range
     * cannot leak into the new member. Usage, the date ledger, sessions, study rounds and
     * quiz progress are never copied either.
     * Cloud mode is refused by [canEditFamily] because the backend is still single-member.
     */
    fun addMember(body: JSONObject): Outcome {
        if (!canEditFamily()) {
            return error(
                if (!FamilyHub.isReady) "FAMILY_NOT_READY" else "READ_ONLY",
            )
        }
        val family = FamilyHub.state.value
        if (!family.canAddMember) return error("FAMILY_NOT_READY")
        val name = body.optString("name", "")
        val preset = body.optString("avatarPreset", FamilyAvatarStore.DEFAULT_PRESET)
        val scope = memberGradeSelection(body) ?: return error("SUBJECT_GRADE_INVALID")
        // Switch protection is opt-in. Members added with it off may stay on the initial
        // credential; turning it on later forces the parent to set a custom password.
        val switchPinRequired = body.optBoolean("switchPinRequired", false)
        val switchPin = body.optString("switchPin", "").trim().takeIf { it.isNotEmpty() }
        if (switchPin != null && !MemberPin.isValid(switchPin)) {
            return error("SUBJECT_PIN_INVALID")
        }
        if (switchPinRequired && switchPin == null) return error("SUBJECT_PIN_REQUIRED")
        if (switchPinRequired && switchPin == MemberPin.INITIAL) {
            return error("SUBJECT_PIN_WEAK")
        }
        val exempt = switchPinExemptSelection(
            body = body,
            defaultDays = emptyList(),
            defaultStartMinute = SwitchPinExempt.DEFAULT_START_MINUTE,
            defaultEndMinute = SwitchPinExempt.DEFAULT_END_MINUTE,
        ) ?: return error("SUBJECT_EXEMPT_INVALID")
        val member = FamilyHub.addMember(
            displayName = name,
            avatarPreset = preset,
            grade = scope.first,
            semester = scope.second,
            switchPin = switchPin,
            switchPinRequired = switchPinRequired,
            switchPinExemptDays = exempt.days,
            switchPinExemptStartMinute = exempt.startMinute,
            switchPinExemptEndMinute = exempt.endMinute,
        ) ?: return error(
            when {
                FamilyIds.normalizeDisplayName(name) == null -> "SUBJECT_NAME_INVALID"
                FamilyAvatarStore.resolvePreset(preset) == null ->
                    "SUBJECT_AVATAR_INVALID"
                else -> "SUBJECT_ADD_FAILED"
            },
        )
        // A clean QCD has no levels and no copied quiz settings. AI generation preferences
        // are a separate per-member key and stay absent until the parent configures them.
        QuizSubjectScope.provisionLocalDocument(member.userId, null)
        return ok("SUBJECT_ADDED")
    }

    /**
     * Renames the member and/or changes the avatar preset. Uploaded images have their own raw
     * route; this action never accepts a path from the page.
     */
    fun updateMemberProfile(body: JSONObject): Outcome {
        if (!canEditFamily()) {
            return error(
                if (!FamilyHub.isReady) "FAMILY_NOT_READY" else "READ_ONLY",
            )
        }
        val userId = body.optString("userId", "").trim()
        if (userId.isEmpty()) return error("SUBJECT_REQUIRED")
        val name = body.optString("name", "")
        val preset = body.optString("avatarPreset", "")
        val scope = memberGradeSelection(body) ?: return error("SUBJECT_GRADE_INVALID")
        // Absent keeps the member's current protection so a raw request can never disable it.
        val switchPinRequired = if (body.has("switchPinRequired")) {
            body.optBoolean("switchPinRequired", false)
        } else {
            null
        }
        // Blank means "keep the current password". Turning protection on requires a new one.
        val switchPin = body.optString("switchPin", "").trim().takeIf { it.isNotEmpty() }
        if (switchPin != null && !MemberPin.isValid(switchPin)) {
            return error("SUBJECT_PIN_INVALID")
        }
        if (switchPin == MemberPin.INITIAL) return error("SUBJECT_PIN_WEAK")
        val existing = FamilyHub.member(userId) ?: return error("SUBJECT_NOT_FOUND")
        val exempt = switchPinExemptSelection(
            body = body,
            defaultDays = existing.switchPinExemptDays,
            defaultStartMinute = existing.switchPinExemptStartMinute,
            defaultEndMinute = existing.switchPinExemptEndMinute,
        ) ?: return error("SUBJECT_EXEMPT_INVALID")
        val failure = FamilyHub.updateProfile(
            userId,
            name,
            preset,
            grade = scope.first,
            semester = scope.second,
            switchPin = switchPin,
            switchPinRequired = switchPinRequired,
            switchPinExemptDays = exempt.days,
            switchPinExemptStartMinute = exempt.startMinute,
            switchPinExemptEndMinute = exempt.endMinute,
        )
        return if (failure == null) ok("SUBJECT_SAVED") else error(failure)
    }

    /**
     * Removes a local child. The route already verified the parent PIN; this method keeps the
     * roster guards honest for a raw request. The default member anchors the legacy
     * single-child keys and the active member is executing on the TV, so both are refused
     * until the parent switches away (the default can never be removed).
     */
    fun deleteMember(body: JSONObject): Outcome {
        if (!canEditFamily()) {
            return error(
                if (!FamilyHub.isReady) "FAMILY_NOT_READY" else "READ_ONLY",
            )
        }
        val userId = body.optString("userId", "").trim()
        if (userId.isEmpty()) return error("SUBJECT_REQUIRED")
        val family = FamilyHub.state.value
        val member = family.members.firstOrNull {
            it.userId == userId && it.status == MemberStatus.ACTIVE
        } ?: return error("SUBJECT_NOT_FOUND")
        if (member.userId == family.defaultUserId) return error("SUBJECT_DEFAULT")
        if (member.userId == family.activeUserId) return error("SUBJECT_ACTIVE")
        val context = appContext ?: return error("NOT_READY")
        val failure = FamilyHub.removeMember(member.userId)
        if (failure != null) {
            return error(
                if (failure == "SUBJECT_UPDATE_FAILED") "SUBJECT_DELETE_FAILED" else failure,
            )
        }
        FamilyAvatarStore.deleteAllForMember(context, member.familyId, member.userId)
        return ok("SUBJECT_DELETED")
    }

    /**
     * Whether the web "user configuration" tab keeps asking for the parent PIN. Disabling is
     * protected by a single-use USER_CONFIG proof in the HTTP layer; enabling only restores
     * protection and needs no proof. Both directions are device-local.
     */
    fun setUserConfigPin(requirePin: Boolean): Outcome {
        val ui = PolicyModeCoordinator.state.value
        if (ui.mode != PolicyMode.LOCAL) return error("LOCAL_ONLY")
        if (PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound) {
            return error("READ_ONLY")
        }
        if (!UserConfigPinSettings.setRequired(requirePin)) return error("SAVE_FAILED")
        return ok("USER_CONFIG_PIN_SAVED")
    }

    /**
     * Moves the TV's active member from the Web. Authentication is the parent-center login; the
     * move itself uses the same [SubjectSwitcher] gate as the TV, so a running timer, quiz,
     * study round, temporary unlock or non-lock-screen state refuses with [TV_BUSY].
     */
    fun activateSubject(body: JSONObject): Outcome {
        if (!canEditFamily()) {
            return error(
                if (!FamilyHub.isReady) "FAMILY_NOT_READY" else "READ_ONLY",
            )
        }
        val userId = body.optString("userId", "").trim()
        if (userId.isEmpty()) return error("SUBJECT_REQUIRED")
        val family = FamilyHub.state.value
        val member = family.members.firstOrNull {
            it.userId == userId && it.status == MemberStatus.ACTIVE
        } ?: return error("SUBJECT_NOT_FOUND")
        if (member.userId == family.activeUserId) return ok("SUBJECT_SWITCHED")
        val expected = body.optLong("expectedActiveEpoch", -1L).takeIf { it >= 0L }
        return when (SubjectSwitcher.switchTo(member.userId, expected)) {
            SubjectSwitcher.Result.OK -> ok("SUBJECT_SWITCHED")
            SubjectSwitcher.Result.STATE_CHANGED -> conflict("STATE_CHANGED")
            SubjectSwitcher.Result.BUSY,
            SubjectSwitcher.Result.NOT_LOCKED -> error("TV_BUSY")
            SubjectSwitcher.Result.CLOUD_MODE -> error("READ_ONLY")
            SubjectSwitcher.Result.UNKNOWN_MEMBER -> error("SUBJECT_NOT_FOUND")
            SubjectSwitcher.Result.NOT_READY -> error("FAMILY_NOT_READY")
            SubjectSwitcher.Result.FAILED -> error("SAVE_FAILED")
        }
    }

    /**
     * Reads the optional member default scope. 0/absent means "all", which is how members
     * created before these fields existed are represented. A semester needs a grade.
     */
    private fun memberGradeSelection(body: JSONObject): Pair<Int?, Int?>? {
        val grade = body.optInt("grade", 0)
        val semester = body.optInt("semester", 0)
        if (grade !in 0..9 || semester !in 0..2) return null
        if (semester != 0 && grade == 0) return null
        return grade.takeIf { it > 0 } to semester.takeIf { it > 0 }
    }

    private data class MemberExemptSelection(
        val days: List<Int>,
        val startMinute: Int,
        val endMinute: Int,
    )

    /**
     * Reads the optional switch-password waiver. Absent fields keep the supplied defaults, so
     * renaming a member never clears its days. Any malformed value rejects the whole save
     * instead of silently widening or narrowing a security exemption.
     */
    private fun switchPinExemptSelection(
        body: JSONObject,
        defaultDays: List<Int>,
        defaultStartMinute: Int,
        defaultEndMinute: Int,
    ): MemberExemptSelection? {
        val days = if (body.has("switchPinExemptDays")) {
            val array = body.optJSONArray("switchPinExemptDays") ?: return null
            val parsed = ArrayList<Int>(array.length())
            for (index in 0 until array.length()) {
                val value = array.opt(index)
                if (value !is Int || value !in 1..7) return null
                parsed.add(value)
            }
            SwitchPinExempt.normalizeDays(parsed) ?: return null
        } else {
            SwitchPinExempt.normalizeDays(defaultDays) ?: return null
        }
        val start = if (body.has("switchPinExemptStartMinute")) {
            body.opt("switchPinExemptStartMinute") as? Int ?: return null
        } else {
            defaultStartMinute
        }
        val end = if (body.has("switchPinExemptEndMinute")) {
            body.opt("switchPinExemptEndMinute") as? Int ?: return null
        } else {
            defaultEndMinute
        }
        if (!SwitchPinExempt.isValidWindow(start, end)) return null
        return MemberExemptSelection(days, start, end)
    }

    fun deleteMemberAvatar(body: JSONObject): Outcome {
        if (!canEditFamily()) {
            return error(
                if (!FamilyHub.isReady) "FAMILY_NOT_READY" else "READ_ONLY",
            )
        }
        val userId = body.optString("userId", "").trim()
        if (userId.isEmpty()) return error("SUBJECT_REQUIRED")
        val context = appContext ?: return error("NOT_READY")
        val member = FamilyHub.state.value.members.firstOrNull { it.userId == userId }
            ?: return error("SUBJECT_NOT_FOUND")
        FamilyAvatarStore.deleteForMember(context, member)
        return ok("SUBJECT_SAVED")
    }

    fun setSourceEntries(body: JSONObject): Outcome {
        val ui = PolicyModeCoordinator.state.value
        if (ui.mode == PolicyMode.NONE) return error("NOT_READY")
        if (PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound) {
            return error("READ_ONLY")
        }
        val enabled = body.optBoolean("enabled", !SourceEntrySettings.isVisible())
        if (!SourceEntrySettings.setVisible(enabled)) return error("SAVE_FAILED")
        return ok("SOURCE_SAVED")
    }

    /**
     * Display-only: shows or hides the dedicated quiz tile on the lock desktop.
     * It never enables, disables or deletes a challenge.
     */
    fun setQuizEntry(body: JSONObject): Outcome {
        val ui = PolicyModeCoordinator.state.value
        if (ui.mode == PolicyMode.NONE) return error("NOT_READY")
        if (PolicyProduct.cloudEnabled && PolicyRuntime.state.value.bound) {
            return error("READ_ONLY")
        }
        val visible = body.optBoolean("visible", false)
        if (QuizSettings.isLockEntryVisible() == visible) return ok("QUIZ_ENTRY_SAVED")
        runBlocking {
            withTimeoutOrNull(10_000L) {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    QuizSettings.setLockEntryVisible(visible)
                }
            }
        } ?: return error("TIMEOUT")
        return ok("QUIZ_ENTRY_SAVED")
    }

    // ----------------------------------------------------------------- intro videos

    /**
     * Chooses a film from the local intro library. An empty id restores the built-in film.
     * These writes intentionally do not check the cloud read-only state: the films never
     * leave this TV, and the HTTP layer already requires the single parent-center editor lease.
     */
    fun selectIntroVideo(context: Context, body: JSONObject): Outcome {
        val id = body.optString("id", "").trim()
        if (id.isEmpty()) {
            QuizIntroVideo.clearActive(context)
            return ok("INTRO_VIDEO_RESTORED")
        }
        if (!QuizIntroVideo.select(context, id)) return error("INTRO_VIDEO_NOT_FOUND")
        return ok("INTRO_VIDEO_SELECTED")
    }

    fun deleteIntroVideo(context: Context, body: JSONObject): Outcome {
        val id = body.optString("id", "").trim()
        if (!QuizIntroVideo.delete(context, id)) return error("INTRO_VIDEO_NOT_FOUND")
        return ok("INTRO_VIDEO_DELETED")
    }

    // ----------------------------------------------------------------- cloud media
    //
    // The catalogue is read-only and a film is fetched only when the parent asks for this one
    // id. These writes are device-local like the intro upload: a bound TV still lets the holder
    // of the editor lease choose its own wallpaper, and the lease is checked by the HTTP layer.

    fun refreshMedia(context: Context): Outcome {
        if (!OfficialMediaCatalog.isConfigured()) return error("CATALOG_UNAVAILABLE")
        OfficialMediaCatalog.requestRefresh(context.applicationContext, force = true)
        return ok("MEDIA_REFRESHING")
    }

    fun downloadMedia(context: Context, body: JSONObject): Outcome {
        val id = body.optString("id", "").trim()
        if (id.isEmpty()) return error("MEDIA_NOT_FOUND")
        val roles = PolicyVideoRole.fromOptionalRequest(body.optString("role", ""))
            ?: return error("MEDIA_ROLE_INVALID")
        return when (OfficialMediaDownloader.start(context.applicationContext, id, roles)) {
            is OfficialMediaDownloader.StartResult.Started -> ok("MEDIA_DOWNLOAD_STARTED")
            is OfficialMediaDownloader.StartResult.AlreadyReady -> ok("MEDIA_ALREADY_READY")
            is OfficialMediaDownloader.StartResult.Running -> ok("MEDIA_DOWNLOADING")
            OfficialMediaDownloader.StartResult.Busy -> error("MEDIA_BUSY")
            OfficialMediaDownloader.StartResult.NotFound -> error("MEDIA_NOT_FOUND")
            OfficialMediaDownloader.StartResult.RoleInvalid -> error("MEDIA_ROLE_INVALID")
            OfficialMediaDownloader.StartResult.NoSpace -> error("MEDIA_NO_SPACE")
        }
    }

    fun cancelMediaDownload(body: JSONObject): Outcome {
        val id = body.optString("id", "").trim()
        if (id.isEmpty() || !OfficialMediaDownloader.cancel(id)) {
            return error("MEDIA_NOT_FOUND")
        }
        return ok("MEDIA_CANCELLED")
    }

    fun selectMedia(context: Context, body: JSONObject): Outcome {
        val roles = PolicyVideoRole.fromRequest(body.optString("role", ""))
            ?: return error("MEDIA_ROLE_INVALID")
        val id = body.optString("id", "").trim()
        return when (PolicyVideoLibrary.select(context.applicationContext, roles, id)) {
            PolicyVideoLibrary.EditResult.Ok -> ok("MEDIA_SELECTED")
            PolicyVideoLibrary.EditResult.NotFound -> error("MEDIA_NOT_FOUND")
            PolicyVideoLibrary.EditResult.NotReady -> error("MEDIA_NOT_READY")
            PolicyVideoLibrary.EditResult.Busy -> error("MEDIA_BUSY")
            PolicyVideoLibrary.EditResult.RoleInvalid -> error("MEDIA_ROLE_INVALID")
            PolicyVideoLibrary.EditResult.PlaylistTooLong,
            PolicyVideoLibrary.EditResult.PlaylistInvalid -> error("MEDIA_ROLE_INVALID")
            PolicyVideoLibrary.EditResult.DeleteFailed -> error("MEDIA_DOWNLOAD_FAILED")
        }
    }

    /**
     * Replaces the lock wallpaper rotation. The page sends the ids in the order the child should
     * see them; an empty array clears the rotation. The TV re-checks every id and the interval.
     */
    fun saveMediaPlaylist(context: Context, body: JSONObject): Outcome {
        val array = body.optJSONArray("ids") ?: return error("MEDIA_PLAYLIST_INVALID")
        if (array.length() > PolicyVideoLibrary.WALLPAPER_MAX_ITEMS) {
            return error("MEDIA_PLAYLIST_TOO_LONG")
        }
        val ids = ArrayList<String>(array.length())
        for (index in 0 until array.length()) {
            ids += array.optString(index, "").trim()
        }
        val intervalMs = body.optLong(
            "intervalMs",
            PolicyVideoLibrary.WALLPAPER_DEFAULT_INTERVAL_MS,
        )
        return when (
            PolicyVideoLibrary.saveLockPlaylist(context.applicationContext, ids, intervalMs)
        ) {
            PolicyVideoLibrary.EditResult.Ok -> ok("MEDIA_PLAYLIST_SAVED")
            PolicyVideoLibrary.EditResult.NotFound -> error("MEDIA_NOT_FOUND")
            PolicyVideoLibrary.EditResult.PlaylistTooLong -> error("MEDIA_PLAYLIST_TOO_LONG")
            PolicyVideoLibrary.EditResult.PlaylistInvalid -> error("MEDIA_PLAYLIST_INVALID")
            PolicyVideoLibrary.EditResult.RoleInvalid -> error("MEDIA_ROLE_INVALID")
            PolicyVideoLibrary.EditResult.DeleteFailed -> error("MEDIA_DOWNLOAD_FAILED")
            PolicyVideoLibrary.EditResult.NotReady -> error("MEDIA_NOT_READY")
            PolicyVideoLibrary.EditResult.Busy -> error("MEDIA_BUSY")
        }
    }

    fun deleteMedia(context: Context, body: JSONObject): Outcome {
        val id = body.optString("id", "").trim()
        if (id.isEmpty()) return error("MEDIA_NOT_FOUND")
        return when (PolicyVideoLibrary.delete(context.applicationContext, id)) {
            PolicyVideoLibrary.EditResult.Ok -> ok("MEDIA_DELETED")
            PolicyVideoLibrary.EditResult.NotFound -> error("MEDIA_NOT_FOUND")
            PolicyVideoLibrary.EditResult.Busy -> error("MEDIA_BUSY")
            PolicyVideoLibrary.EditResult.DeleteFailed -> error("MEDIA_DELETE_FAILED")
            PolicyVideoLibrary.EditResult.NotReady,
            PolicyVideoLibrary.EditResult.RoleInvalid,
            PolicyVideoLibrary.EditResult.PlaylistTooLong,
            PolicyVideoLibrary.EditResult.PlaylistInvalid -> error("MEDIA_NOT_FOUND")
        }
    }

    /**
     * The child wallpaper sound switch. It only changes the child lock/Home player; parent mode
     * never reads this preference. Turning it on requires a selected video with a decodable
     * audio track, while turning it off is always allowed.
     */
    fun setMediaSound(context: Context, body: JSONObject): Outcome {
        val enabled = body.optBoolean("enabled", false)
        if (!PolicyVideoLibrary.setLockSound(context.applicationContext, enabled)) {
            return error("MEDIA_SOUND_UNAVAILABLE")
        }
        return ok("MEDIA_SOUND_SAVED")
    }

    fun refreshAudio(context: Context): Outcome {
        if (!OfficialAudioCatalog.isConfigured()) return error("AUDIO_CATALOG_UNAVAILABLE")
        OfficialAudioCatalog.requestRefresh(context.applicationContext, force = true)
        return ok("AUDIO_REFRESHING")
    }

    fun downloadAudio(context: Context, body: JSONObject): Outcome {
        val id = body.optString("id", "").trim()
        if (id.isEmpty()) return error("AUDIO_NOT_FOUND")
        return when (OfficialAudioDownloader.start(context.applicationContext, id)) {
            is OfficialAudioDownloader.StartResult.Started -> ok("AUDIO_DOWNLOAD_STARTED")
            is OfficialAudioDownloader.StartResult.AlreadyReady -> ok("AUDIO_ALREADY_READY")
            is OfficialAudioDownloader.StartResult.Running -> ok("AUDIO_DOWNLOADING")
            OfficialAudioDownloader.StartResult.Busy -> error("AUDIO_BUSY")
            OfficialAudioDownloader.StartResult.NotFound -> error("AUDIO_NOT_FOUND")
            OfficialAudioDownloader.StartResult.NoSpace -> error("AUDIO_NO_SPACE")
        }
    }

    fun cancelAudioDownload(body: JSONObject): Outcome {
        val id = body.optString("id", "").trim()
        if (id.isEmpty() || !OfficialAudioDownloader.cancel(id)) {
            return error("AUDIO_NOT_FOUND")
        }
        return ok("AUDIO_CANCELLED")
    }

    /**
     * The child wallpaper's background music. Unlike the embedded video sound switch this is
     * its own track, stored per member and played under every rotation item; an empty id
     * removes it. A cloud id resolves only after its verified download exists. The HTTP layer
     * has already checked the editor lease.
     */
    fun selectAudio(context: Context, body: JSONObject): Outcome {
        val id = body.optString("id", "").trim()
        return when (PolicyVideoLibrary.selectLockAudio(context.applicationContext, id)) {
            PolicyVideoLibrary.EditResult.Ok -> ok("AUDIO_SELECTED")
            PolicyVideoLibrary.EditResult.NotFound -> error("AUDIO_NOT_FOUND")
            PolicyVideoLibrary.EditResult.DeleteFailed -> error("AUDIO_DELETE_FAILED")
            PolicyVideoLibrary.EditResult.NotReady,
            PolicyVideoLibrary.EditResult.Busy,
            PolicyVideoLibrary.EditResult.RoleInvalid,
            PolicyVideoLibrary.EditResult.PlaylistTooLong,
            PolicyVideoLibrary.EditResult.PlaylistInvalid -> error("AUDIO_NOT_FOUND")
        }
    }

    /**
     * Deletes one uploaded track or downloaded official copy and clears every member's selection
     * that used it. An official cloud entry itself is read-only and remains in the catalogue.
     */
    fun deleteAudio(context: Context, body: JSONObject): Outcome {
        val id = body.optString("id", "").trim()
        return when (PolicyVideoLibrary.deleteLockAudio(context.applicationContext, id)) {
            PolicyVideoLibrary.EditResult.Ok -> ok("AUDIO_DELETED")
            PolicyVideoLibrary.EditResult.NotFound -> error("AUDIO_NOT_FOUND")
            PolicyVideoLibrary.EditResult.DeleteFailed -> error("AUDIO_DELETE_FAILED")
            PolicyVideoLibrary.EditResult.NotReady,
            PolicyVideoLibrary.EditResult.Busy,
            PolicyVideoLibrary.EditResult.RoleInvalid,
            PolicyVideoLibrary.EditResult.PlaylistTooLong,
            PolicyVideoLibrary.EditResult.PlaylistInvalid -> error("AUDIO_NOT_FOUND")
        }
    }

    fun endSession(body: JSONObject): Outcome {
        if (PolicyModeCoordinator.mode != PolicyMode.LOCAL) return error("LOCAL_ONLY")
        if (!body.optBoolean("confirm", false)) return error("CONFIRM_REQUIRED")
        if (LocalPolicyRuntime.state.value.sessionState != SessionState.ACTIVE) {
            return error("SESSION_NOT_ACTIVE")
        }
        LocalPolicyRuntime.endSessionByParent()
        return ok("SESSION_ENDED")
    }

    // -------------------------------------------------------------------- apps

    fun appsJson(context: Context): JSONArray {
        val array = JSONArray()
        PolicyInstalledApps.scan(context)
            .take(300)
            .forEach { app ->
                array.put(
                    JSONObject()
                        .put("packageName", app.packageName)
                        .put("label", app.label),
                )
            }
        return array
    }

    fun icon(context: Context, packageName: String): ByteArray? {
        if (packageName.length !in 1..255) return null
        return PolicyInstalledApps.iconBytes(context, packageName, 96, 64 * 1024)
    }

    // ------------------------------------------------------------------ helpers

    fun ok(messageKey: String): Outcome =
        Outcome(ok = true, code = "OK", messageKey = messageKey)

    fun error(code: String): Outcome =
        Outcome(ok = false, code = code, messageKey = code)

    private fun conflict(code: String): Outcome = Outcome(
        ok = false,
        code = code,
        messageKey = code,
        retryAfterSeconds = 0L,
    )

    private fun defaultPinCached(): Boolean {
        val now = System.currentTimeMillis()
        if (now - defaultPinCheckedAt < DEFAULT_PIN_CACHE_MS) return defaultPinCached
        defaultPinCheckedAt = now
        defaultPinCached = runCatching { PolicyModeCoordinator.isDefaultPin() }
            .getOrDefault(false)
        return defaultPinCached
    }

    private fun invalidateDefaultPinCache() {
        defaultPinCheckedAt = 0L
    }

    private fun parseRules(json: JSONObject, previous: LocalRules?): ParsedRules {
        val dayGroups = parseDayGroups(json.optJSONArray("dayGroups"))
            ?: return ParsedRules.Invalid("RULES_INVALID_DAY_GROUPS")
        // V44: `directSessionMinutes` is the V5 name for the direct "each viewing" value.
        // `sessionLimitMinutes` stays accepted as the old alias; if both are sent they must agree.
        val direct = when {
            json.has("directSessionMinutes") -> json.optInt("directSessionMinutes", -1)
            json.has("sessionLimitMinutes") -> json.optInt("sessionLimitMinutes", -1)
            else -> LocalRules.DEFAULT_DIRECT_SESSION_MINUTES
        }
        val cap = if (json.has("sessionCapMinutes")) {
            json.optInt("sessionCapMinutes", -1)
        } else {
            -1
        }
        if (cap !in LocalRules.MIN_SESSION_CAP_MINUTES..LocalRules.MAX_SESSION_CAP_MINUTES) {
            return ParsedRules.Invalid("RULES_INVALID_SESSION_CAP")
        }
        if (json.has("directSessionMinutes") && json.has("sessionLimitMinutes") &&
            json.optInt("directSessionMinutes", -1) != json.optInt("sessionLimitMinutes", -2)
        ) {
            return ParsedRules.Invalid("RULES_INVALID_DIRECT_SESSION")
        }
        if (direct !in LocalRules.MIN_WRITE_DIRECT_SESSION_MINUTES..cap) {
            return ParsedRules.Invalid("RULES_INVALID_DIRECT_SESSION")
        }
        val quizCount = json.optInt("quizDailyLimitCount", -1)
        val studyRewardCount = json.optInt("studyRewardDailyLimitCount", -1)
        val directCount = json.optInt("directDailyFreeCount", -1)
        if (quizCount !in 0..LocalRules.MAX_ROUTE_DAILY_COUNT ||
            studyRewardCount !in 0..LocalRules.MAX_ROUTE_DAILY_COUNT ||
            directCount !in 0..LocalRules.MAX_ROUTE_DAILY_COUNT
        ) {
            return ParsedRules.Invalid("RULES_INVALID_ROUTE_COUNTS")
        }
        val rest = json.optInt("restMinutes", -1)
        val buffer = json.optInt("sessionScreenOffBufferMinutes", -1)
        if (rest < 0 || buffer < 0) return ParsedRules.Invalid("RULES_INVALID")
        val allApps = json.optBoolean("allApps", false)
        val allowed = LinkedHashSet<String>()
        if (allApps) {
            allowed.add(ALL_VALID_APP_SENTINEL)
        } else if (json.has("allowedPackages")) {
            val array = json.optJSONArray("allowedPackages")
                ?: return ParsedRules.Invalid("RULES_INVALID_APPS")
            for (i in 0 until minOf(array.length(), 500)) {
                val value = array.optString(i, "").trim()
                if (value.isNotEmpty()) allowed.add(value)
            }
        }
        val study = LinkedHashSet<String>()
        if (json.has("studyPackages")) {
            val array = json.optJSONArray("studyPackages")
                ?: return ParsedRules.Invalid("RULES_INVALID_STUDY_APPS")
            for (i in 0 until minOf(array.length(), 500)) {
                val value = array.optString(i, "").trim()
                if (value.isNotEmpty()) study.add(value)
            }
        }
        // V42: a missing unlimited list means an empty category, never a fallback to the
        // entertainment list. The category also cannot use the all-apps sentinel.
        val unlimited = LinkedHashSet<String>()
        if (json.has("unlimitedPackages")) {
            val array = json.optJSONArray("unlimitedPackages")
                ?: return ParsedRules.Invalid("RULES_INVALID_UNLIMITED_APPS")
            for (i in 0 until minOf(array.length(), 500)) {
                val value = array.optString(i, "").trim()
                if (value.isNotEmpty()) unlimited.add(value)
            }
        }
        val studyEarn = if (json.has("studyEarnPackages")) {
            val array = json.optJSONArray("studyEarnPackages")
                ?: return ParsedRules.Invalid("RULES_INVALID_STUDY_EARN")
            LinkedHashSet<String>().apply {
                for (i in 0 until minOf(array.length(), 500)) {
                    val value = array.optString(i, "").trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        } else {
            previous?.effectiveStudyEarnPackages ?: emptySet()
        }
        val studyEarnStudy = if (json.has("studyEarnStudyMinutes")) {
            json.optInt("studyEarnStudyMinutes", -1)
        } else {
            previous?.studyEarnStudyMinutes ?: LocalRules.DEFAULT_STUDY_EARN_STUDY_MINUTES
        }
        val studyEarnReward = if (json.has("studyEarnRewardMinutes")) {
            json.optInt("studyEarnRewardMinutes", -1)
        } else {
            previous?.studyEarnRewardMinutes ?: LocalRules.DEFAULT_STUDY_EARN_REWARD_MINUTES
        }
        // New V5 writes are stricter than the legacy local document: X 1–180 and
        // Y 1..cap. X and Y are independent inputs; Y may exceed X. Old data keeps loading
        // through LocalPolicyStore, not this path.
        if (studyEarnStudy !in
            LocalRules.MIN_WRITE_STUDY_EARN_MINUTES..LocalRules.MAX_STUDY_EARN_MINUTES
        ) {
            return ParsedRules.Invalid("RULES_INVALID_STUDY_EARN")
        }
        if (studyEarnReward !in LocalRules.MIN_WRITE_STUDY_EARN_MINUTES..cap) {
            return ParsedRules.Invalid("RULES_INVALID_STUDY_EARN")
        }
        // A V3 page cannot express the route switches. Preserve the stored values rather than
        // resetting the parent's choices; V4 sends all three explicitly.
        val quizGate = if (json.has("quizGateEnabled")) {
            json.optBoolean("quizGateEnabled", true)
        } else {
            previous?.quizGateEnabled ?: LocalRules.DEFAULTS.quizGateEnabled
        }
        val studyEnabled = if (json.has("studyEarnEnabled")) {
            json.optBoolean("studyEarnEnabled", LocalRules.DEFAULTS.studyEarnEnabled)
        } else {
            previous?.studyEarnEnabled ?: LocalRules.DEFAULTS.studyEarnEnabled
        }
        val directStart = if (json.has("directStartEnabled")) {
            json.optBoolean("directStartEnabled", false)
        } else {
            previous?.directStartEnabled ?: LocalRules.DEFAULTS.directStartEnabled
        }
        val parsed = LocalRules(
            dayGroups = dayGroups,
            sessionLimitMinutes = direct,
            sessionCapMinutes = cap,
            quizDailyLimitCount = quizCount,
            studyRewardDailyLimitCount = studyRewardCount,
            directDailyFreeCount = directCount,
            restMinutes = rest,
            sessionScreenOffBufferMinutes = buffer,
            quizGateEnabled = quizGate,
            studyEarnEnabled = studyEnabled,
            directStartEnabled = directStart,
            allowedPackages = allowed,
            studyPackages = study,
            unlimitedPackages = unlimited,
            studyEarnPackages = studyEarn,
            studyEarnStudyMinutes = studyEarnStudy,
            studyEarnRewardMinutes = studyEarnReward,
        ).normalized()
        // V44: all three routes off is a valid choice. The parent confirms on the page and the
        // TV shows its empty state; the TV must not rewrite that decision.
        return ParsedRules.Valid(parsed)
    }

    /**
     * Strict schema-3 group parse. Coverage, duplicate days and category overlap are left to
     * [LocalRules.validationError]; malformed shapes fail here with the day-group error code.
     */
    private fun parseDayGroups(array: JSONArray?): List<LocalDayGroup>? {
        if (array == null || array.length() !in 1..LocalRules.MAX_DAY_GROUPS) return null
        val groups = ArrayList<LocalDayGroup>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: return null
            val daysArray = item.optJSONArray("days") ?: return null
            if (daysArray.length() !in 1..7) return null
            val days = ArrayList<Int>(daysArray.length())
            for (j in 0 until daysArray.length()) {
                val day = daysArray.optInt(j, -1)
                if (day !in 1..7) return null
                days.add(day)
            }
            val studyWindows = parseWindows(item.optJSONArray("studyWindows")) ?: return null
            val studyLimit = parseLimit(item, "studyDailyLimitMinutes") ?: return null
            val entertainmentWindows =
                parseWindows(item.optJSONArray("entertainmentWindows")) ?: return null
            val entertainmentLimit =
                parseLimit(item, "entertainmentDailyLimitMinutes") ?: return null
            groups.add(
                LocalDayGroup(
                    days = days,
                    studyWindows = studyWindows,
                    studyDailyLimitMinutes = studyLimit.minutes,
                    entertainmentWindows = entertainmentWindows,
                    entertainmentDailyLimitMinutes = entertainmentLimit.minutes,
                ),
            )
        }
        return groups
    }

    private data class ParsedLimit(val minutes: Int?)

    /** `null` JSON is the unlimited quota; zero stays a finite quota. Missing is malformed. */
    private fun parseLimit(json: JSONObject, name: String): ParsedLimit? {
        if (!json.has(name)) return null
        if (json.isNull(name)) return ParsedLimit(null)
        val value = json.optInt(name, -1)
        if (value !in 0..LocalRules.MAX_DAILY_MINUTES) return null
        return ParsedLimit(value)
    }

    private fun parseWindows(array: JSONArray?): List<LocalTimeWindow>? {
        if (array == null) return null
        if (array.length() > LocalRules.MAX_WINDOWS_PER_DAY) return null
        val windows = ArrayList<LocalTimeWindow>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: return null
            val start = item.optInt("start", -1)
            val end = item.optInt("end", -1)
            if (start !in 0 until 1440 || end !in 1..1440 || start >= end) return null
            windows.add(LocalTimeWindow(start, end))
        }
        return windows
    }

    private sealed class ParsedRules {
        data class Valid(val rules: LocalRules) : ParsedRules()
        data class Invalid(val code: String) : ParsedRules()
    }

    private const val ALL_VALID_APP_SENTINEL = "all_valid_app"
}
