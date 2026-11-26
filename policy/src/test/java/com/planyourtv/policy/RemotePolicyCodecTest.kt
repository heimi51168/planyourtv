package com.planyourtv.policy

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePolicyCodecTest {
    private val policyJson = JSONObject(
        """
        {
          "deviceNo": "tv-1",
          "mode": "RULE",
          "handoffState": "DONE",
          "configVersion": 7,
          "appliedVersion": 7,
          "appliedMode": "RULE",
          "supportsUnifiedPolicy": true,
          "rules": {
            "schemaVersion": 1,
            "weekdayWindows": [{"startMinute": 1080, "endMinute": 1200}],
            "weekendWindows": [{"startMinute": 540, "endMinute": 1200}],
            "dailyLimitMinutes": 60,
            "sessionLimitMinutes": 20,
            "restMinutes": 10,
            "allowedPackages": ["all_valid_app"]
          },
          "updatedAt": "2026-10-30 12:00:00"
        }
        """.trimIndent(),
    )

    @Test
    fun parsesTheCloudPolicyNode() {
        val policy = RemotePolicyCodec.parse(policyJson)!!
        assertEquals(RemotePolicyMode.RULE, policy.mode)
        assertEquals(RemoteHandoffState.DONE, policy.handoffState)
        assertEquals(7L, policy.configVersion)
        assertEquals(7L, policy.appliedVersion)
        assertEquals(RemotePolicyMode.RULE, policy.appliedMode)
        assertTrue(policy.supportsUnifiedPolicy)
        assertTrue(policy.rulesUsable)
        assertTrue(policy.readyForRule)
        assertEquals(1080, policy.rules.weekdayWindows.single().startMinute)
        assertEquals(1200, policy.rules.weekendWindows.single().endMinute)
    }

    @Test
    fun missingWeekendQuotaSharesTheLegacyDailyLimit() {
        val policy = RemotePolicyCodec.parse(policyJson)!!
        assertEquals(60, policy.rules.autoDailyLimitMinutes)
        assertEquals(60, policy.rules.weekendDailyLimitMinutes)
    }

    @Test
    fun weekendQuotaRoundTripsSeparately() {
        val json = withRules(policyJson) { it.put("weekendDailyLimitMinutes", 120) }
        val policy = RemotePolicyCodec.parse(json)!!
        assertEquals(60, policy.rules.autoDailyLimitMinutes)
        assertEquals(120, policy.rules.weekendDailyLimitMinutes)
        val restored = RemotePolicyCodec.parse(RemotePolicyCodec.toJson(policy))!!
        assertEquals(120, restored.rules.weekendDailyLimitMinutes)
    }

    @Test
    fun missingWeekendRestAndBufferShareTheWeekdayValues() {
        val json = withRules(policyJson) {
            it.put("restMinutes", 12)
            it.put("sessionScreenOffBufferMinutes", 25)
        }
        val policy = RemotePolicyCodec.parse(json)!!
        assertEquals(12, policy.rules.restMinutes)
        assertEquals(12, policy.rules.weekendRestMinutes)
        assertEquals(25, policy.rules.sessionScreenOffBufferMinutes)
        assertEquals(25, policy.rules.weekendSessionScreenOffBufferMinutes)
    }

    @Test
    fun weekendRestAndBufferAreProjectionsOfTheSingleValue() {
        val json = withRules(policyJson) {
            it.put("restMinutes", 12)
            it.put("weekendRestMinutes", 30)
            it.put("sessionScreenOffBufferMinutes", 25)
            it.put("weekendSessionScreenOffBufferMinutes", 45)
        }
        val policy = RemotePolicyCodec.parse(json)!!
        // Rest and the screen-off grace are single values in schema 3. A backend still sending
        // weekend fields is projected onto the one stored value, not split into a second rule.
        assertEquals(12, policy.rules.restMinutes)
        assertEquals(12, policy.rules.weekendRestMinutes)
        assertEquals(25, policy.rules.sessionScreenOffBufferMinutes)
        assertEquals(25, policy.rules.weekendSessionScreenOffBufferMinutes)
        val restored = RemotePolicyCodec.parse(RemotePolicyCodec.toJson(policy))!!
        assertEquals(12, restored.rules.restMinutes)
        assertEquals(12, restored.rules.weekendRestMinutes)
        assertEquals(25, restored.rules.sessionScreenOffBufferMinutes)
        assertEquals(25, restored.rules.weekendSessionScreenOffBufferMinutes)
    }

    @Test
    fun missingAppliedModeMeansNotApplied() {
        val json = JSONObject(policyJson.toString()).apply { remove("appliedMode") }
        val policy = RemotePolicyCodec.parse(json)!!
        assertNull(policy.appliedMode)
        assertTrue(policy.pendingApply)
        assertFalse(policy.readyForRule)
    }

    @Test
    fun missingCapabilityDefaultsToFalse() {
        val json = JSONObject(policyJson.toString()).apply { remove("supportsUnifiedPolicy") }
        assertFalse(RemotePolicyCodec.parse(json)!!.supportsUnifiedPolicy)
    }

    @Test
    fun unknownModeIsRejected() {
        val json = JSONObject(policyJson.toString()).put("mode", "BOTH")
        assertNull(RemotePolicyCodec.parse(json))
    }

    @Test
    fun missingActivationDefaultsToManual() {
        assertEquals(Activation.MANUAL, RemotePolicyCodec.parse(policyJson)!!.activation)
    }

    @Test
    fun missingRestUsesTheManualDefault() {
        val manualJson = withRules(policyJson) { it.remove("restMinutes") }
        assertEquals(0, RemotePolicyCodec.parse(manualJson)!!.rules.restMinutes)
    }

    @Test
    fun automaticActivationIsRejected() {
        val autoJson = JSONObject(policyJson.toString()).put("activation", "AUTO")
        assertNull(RemotePolicyCodec.parse(autoJson))
    }

    @Test
    fun activationIsParsedCaseInsensitivelyAndRoundTrips() {
        val json = JSONObject(policyJson.toString()).put("activation", "manual")
        val policy = RemotePolicyCodec.parse(json)!!
        assertEquals(Activation.MANUAL, policy.activation)
        assertEquals(Activation.MANUAL, RemotePolicyCodec.parse(RemotePolicyCodec.toJson(policy))!!.activation)
    }

    @Test
    fun malformedActivationIsRejectedInsteadOfRevertingToAutomatic() {
        val json = JSONObject(policyJson.toString()).put("activation", "TIMED")
        assertNull(RemotePolicyCodec.parse(json))
    }

    @Test
    fun missingRulesAreRejected() {
        val json = JSONObject(policyJson.toString()).apply { remove("rules") }
        assertNull(RemotePolicyCodec.parse(json))
    }

    @Test
    fun malformedWindowIsRejectedInsteadOfSilentlyDropped() {
        val json = JSONObject(policyJson.toString())
            .put(
                "rules",
                JSONObject(policyJson.getJSONObject("rules").toString())
                    .put("weekdayWindows", org.json.JSONArray().put("18:00-20:00")),
            )
        assertNull(RemotePolicyCodec.parse(json))
    }

    @Test
    fun emptyWeekdayWindowsStayVisiblyUnusable() {
        val json = JSONObject(policyJson.toString())
            .put(
                "rules",
                JSONObject(policyJson.getJSONObject("rules").toString())
                    .put("weekdayWindows", org.json.JSONArray()),
            )
        val policy = RemotePolicyCodec.parse(json)!!
        assertFalse(policy.rulesUsable)
        assertFalse(policy.readyForRule)
    }

    @Test
    fun negativeVersionsAreClampedToZero() {
        val json = JSONObject(policyJson.toString())
            .put("configVersion", -3)
            .put("appliedVersion", -1)
        val policy = RemotePolicyCodec.parse(json)!!
        assertEquals(0L, policy.configVersion)
        assertEquals(0L, policy.appliedVersion)
    }

    @Test
    fun persistedJsonRoundTrips() {
        val policy = RemotePolicyCodec.parse(policyJson)!!
        val restored = RemotePolicyCodec.parse(RemotePolicyCodec.toJson(policy))!!
        assertEquals(policy, restored)
    }

    @Test
    fun missingEntryVisibilityMeansLegacyBackend() {
        val policy = RemotePolicyCodec.parse(policyJson)!!
        assertNull(policy.sourceEntryVisible)
        assertNull(policy.quizEntryVisible)
    }

    @Test
    fun entryVisibilityRoundTripsWhenProjected() {
        val json = JSONObject(policyJson.toString())
            .put("sourceEntryVisible", false)
            .put("quizEntryVisible", true)
        val policy = RemotePolicyCodec.parse(json)!!
        assertEquals(false, policy.sourceEntryVisible)
        assertEquals(true, policy.quizEntryVisible)
        val restored = RemotePolicyCodec.parse(RemotePolicyCodec.toJson(policy))!!
        assertFalse(restored.sourceEntryVisible!!)
        assertTrue(restored.quizEntryVisible!!)
        assertEquals(policy, restored)
    }

    @Test
    fun malformedEntryVisibilityIsTreatedAsLegacyWithoutDroppingTheSnapshot() {
        // 显示类开关的脏值不整包作废：策略快照保留，该字段按「旧后端未投影」处理，
        // 由 codec 写 trace（remote.invalidSourceEntryVisible / …QuizEntryVisible）。
        val json = JSONObject(policyJson.toString())
            .put("sourceEntryVisible", "no")
            .put("quizEntryVisible", 1)
        val policy = RemotePolicyCodec.parse(json)!!
        assertNull(policy.sourceEntryVisible)
        assertNull(policy.quizEntryVisible)
        assertTrue(policy.rulesUsable)
    }

    @Test
    fun missingAllowedPackagesIsRejectedInsteadOfWidened() {
        val json = withRules(policyJson) { it.remove("allowedPackages") }
        assertNull(RemotePolicyCodec.parse(json))
    }

    @Test
    fun explicitAllowedPackagesRoundTrip() {
        val json = withRules(policyJson) {
            it.put("allowedPackages", org.json.JSONArray().put("com.example.tv").put("com.example.kids"))
        }
        val policy = RemotePolicyCodec.parse(json)!!
        assertEquals(setOf("com.example.tv", "com.example.kids"), policy.rules.effectiveAllowedPackages)
        assertTrue(policy.rules.usesAppSelection)
        val restored = RemotePolicyCodec.parse(RemotePolicyCodec.toJson(policy))!!
        assertEquals(policy, restored)
        assertEquals(
            setOf("com.example.tv", "com.example.kids"),
            restored.rules.effectiveAllowedPackages,
        )
    }

    @Test
    fun emptyAllowedPackagesStayExplicitlyEmptyForEverySchema() {
        // V42 does not migrate old local documents; an empty entertainment scope is a real
        // "no entertainment apps" choice, not permission to allow everything.
        val json = withRules(policyJson) { it.put("allowedPackages", org.json.JSONArray()) }
        val rules = RemotePolicyCodec.parse(json)!!.rules
        assertTrue(rules.effectiveAllowedPackages.isEmpty())
        assertTrue(rules.usesAppSelection)
    }

    @Test
    fun schema2EmptyAllowedPackagesMeanNoEntertainmentApps() {
        val json = withRules(policyJson) {
            it.put("schemaVersion", 2)
            it.put("allowedPackages", org.json.JSONArray())
        }
        val rules = RemotePolicyCodec.parse(json)!!.rules
        assertTrue(rules.effectiveAllowedPackages.isEmpty())
        assertTrue(rules.usesAppSelection)
        val restored = RemotePolicyCodec.parse(
            RemotePolicyCodec.toJson(RemotePolicyCodec.parse(json)!!),
        )!!.rules
        assertTrue(restored.effectiveAllowedPackages.isEmpty())
    }

    @Test
    fun truncatedRulesWithoutAllowedPackagesAreRejectedForEverySchema() {
        for (schema in listOf(1, 2, 3)) {
            val json = withRules(policyJson) {
                it.put("schemaVersion", schema)
                it.remove("allowedPackages")
            }
            assertNull(RemotePolicyCodec.parse(json))
        }
    }

    @Test
    fun studyPackagesAreParsedAndRoundTrip() {
        val json = withRules(policyJson) {
            it.put("schemaVersion", 2)
            it.put("allowedPackages", org.json.JSONArray().put("com.example.tv"))
            it.put("studyPackages", org.json.JSONArray().put("com.example.study"))
        }
        val policy = RemotePolicyCodec.parse(json)!!
        assertEquals(setOf("com.example.study"), policy.rules.effectiveStudyPackages)
        assertTrue(policy.rules.hasStudyMode)
        val restored = RemotePolicyCodec.parse(RemotePolicyCodec.toJson(policy))!!
        assertEquals(policy, restored)
        assertEquals(setOf("com.example.study"), restored.rules.effectiveStudyPackages)
    }

    @Test
    fun studyPackagesCannotContainTheAllAppsSentinel() {
        val json = withRules(policyJson) {
            it.put("studyPackages", org.json.JSONArray().put(ALL_VALID_APP))
        }
        assertNull(RemotePolicyCodec.parse(json))
    }

    @Test
    fun malformedStudyPackagesAreRejectedInsteadOfDropped() {
        val notAnArray = withRules(policyJson) { it.put("studyPackages", "com.example.study") }
        assertNull(RemotePolicyCodec.parse(notAnArray))
        val nonString = withRules(policyJson) {
            it.put("studyPackages", org.json.JSONArray().put(1))
        }
        assertNull(RemotePolicyCodec.parse(nonString))
    }

    @Test
    fun autoDailyLimitPrefersTheNewKeyAndFallsBackToTheLegacyKey() {
        val legacy = RemotePolicyCodec.parse(policyJson)!!.rules
        assertEquals(60, legacy.autoDailyLimitMinutes)

        val both = withRules(policyJson) {
            it.put("autoDailyLimitMinutes", 30)
            it.put("dailyLimitMinutes", 99)
        }
        assertEquals(30, RemotePolicyCodec.parse(both)!!.rules.autoDailyLimitMinutes)

        val serialized = RemotePolicyCodec.rulesToJson(legacy)
        assertEquals(60, serialized.getInt("autoDailyLimitMinutes"))
        assertEquals(60, serialized.getInt("dailyLimitMinutes"))
    }

    @Test
    fun missingSessionBufferDefaultsToTwentyMinutes() {
        val rules = RemotePolicyCodec.parse(policyJson)!!.rules
        assertEquals(20, rules.sessionScreenOffBufferMinutes)
        val serialized = RemotePolicyCodec.rulesToJson(rules)
        assertEquals(20, serialized.getInt("sessionScreenOffBufferMinutes"))
    }

    @Test
    fun sessionBufferIsParsedAndRoundTrips() {
        val json = withRules(policyJson) { it.put("sessionScreenOffBufferMinutes", 25) }
        val policy = RemotePolicyCodec.parse(json)!!
        assertEquals(25, policy.rules.sessionScreenOffBufferMinutes)
        assertEquals(
            25,
            RemotePolicyCodec.parse(RemotePolicyCodec.toJson(policy))!!
                .rules.sessionScreenOffBufferMinutes,
        )
    }

    @Test
    fun sessionBufferIsClampedForLegacyDeviceValues() {
        val tooLarge = withRules(policyJson) { it.put("sessionScreenOffBufferMinutes", 1470) }
        assertEquals(
            LocalRules.MAX_SESSION_BUFFER_MINUTES,
            RemotePolicyCodec.parse(tooLarge)!!.rules.sessionScreenOffBufferMinutes,
        )
        val zero = withRules(policyJson) { it.put("sessionScreenOffBufferMinutes", 0) }
        assertEquals(
            0,
            RemotePolicyCodec.parse(zero)!!.rules.sessionScreenOffBufferMinutes,
        )
        // Negative is malformed and falls back to the default; it is not a zero clamp.
        val negative = withRules(policyJson) { it.put("sessionScreenOffBufferMinutes", -1) }
        assertEquals(
            LocalRules.DEFAULT_SESSION_BUFFER_MINUTES,
            RemotePolicyCodec.parse(negative)!!.rules.sessionScreenOffBufferMinutes,
        )
    }

    @Test
    fun allValidAppSentinelWinsOverOtherNames() {
        val json = withRules(policyJson) {
            it.put(
                "allowedPackages",
                org.json.JSONArray().put("com.example.tv").put(ALL_VALID_APP),
            )
        }
        val rules = RemotePolicyCodec.parse(json)!!.rules
        assertEquals(setOf(ALL_VALID_APP), rules.effectiveAllowedPackages)
        assertFalse(rules.usesAppSelection)
    }

    @Test
    fun malformedAllowedPackagesAreRejectedInsteadOfWidened() {
        val notAnArray = withRules(policyJson) { it.put("allowedPackages", "com.example.tv") }
        assertNull(RemotePolicyCodec.parse(notAnArray))

        val nonString = withRules(policyJson) {
            it.put("allowedPackages", org.json.JSONArray().put(1))
        }
        assertNull(RemotePolicyCodec.parse(nonString))

        val blank = withRules(policyJson) {
            it.put("allowedPackages", org.json.JSONArray().put("  "))
        }
        assertNull(RemotePolicyCodec.parse(blank))
    }

    @Test
    fun oldBackendWithoutRuleSetsRunsSingleSetMode() {
        val policy = RemotePolicyCodec.parse(policyJson)!!
        assertFalse(policy.supportsRuleSets)
        assertNull(policy.ruleSets)
    }

    @Test
    fun manualRuleSetIsParsedAndRoundTrips() {
        val policy = RemotePolicyCodec.parse(withRuleSets(policyJson))!!

        assertTrue(policy.supportsRuleSets)
        val sets = policy.ruleSets!!
        assertEquals(120, sets.manual.autoDailyLimitMinutes)
        assertEquals(0, sets.manual.restMinutes)
        assertEquals(35, sets.manual.sessionScreenOffBufferMinutes)
        // Shared app scope comes from the rules snapshot, not from the time set.
        assertEquals(policy.rules.effectiveAllowedPackages, sets.shared.allowedPackages)

        val restored = RemotePolicyCodec.parse(RemotePolicyCodec.toJson(policy))!!
        assertEquals(policy, restored)
    }

    @Test
    fun ruleSetWithoutItsOwnBufferFallsBackToTheFlatSnapshot() {
        val json = withRuleSets(policyJson).apply {
            getJSONObject("manualRules").remove("sessionScreenOffBufferMinutes")
        }.let { withRules(it) { rules -> rules.put("sessionScreenOffBufferMinutes", 40) } }

        val sets = RemotePolicyCodec.parse(json)!!.ruleSets!!
        assertEquals(40, sets.manual.sessionScreenOffBufferMinutes)
    }

    @Test
    fun outOfRangeRuleSetBufferIsRejected() {
        val json = withRuleSets(policyJson)
        json.getJSONObject("manualRules").put("sessionScreenOffBufferMinutes", 120)
        assertNull(RemotePolicyCodec.parse(json))
    }

    @Test
    fun missingManualRuleSetFallsBackToSingleSetMode() {
        val json = withRuleSets(policyJson).apply { remove("manualRules") }
        val policy = RemotePolicyCodec.parse(json)!!
        assertFalse(policy.supportsRuleSets)
        assertNull(policy.ruleSets)
    }

    @Test
    fun autoRulesOnlyFallsBackToSingleSetMode() {
        val json = withRuleSets(policyJson).apply {
            remove("manualRules")
            put(
                "autoRules",
                JSONObject()
                    .put("weekdayWindows", windowArray(1080, 1200))
                    .put("weekendWindows", windowArray(540, 1200))
                    .put("dailyLimitMinutes", 60)
                    .put("sessionLimitMinutes", 20)
                    .put("restMinutes", 5),
            )
        }
        val policy = RemotePolicyCodec.parse(json)!!
        assertFalse(policy.supportsRuleSets)
        assertNull(policy.ruleSets)
    }

    @Test
    fun ruleSetMissingAFieldIsRejected() {
        val json = withRuleSets(policyJson)
        json.getJSONObject("manualRules").remove("restMinutes")
        assertNull(RemotePolicyCodec.parse(json))
    }

    @Test
    fun memberScopedFieldsMaskIsParsedAndRoundTrips() {
        val json = JSONObject(policyJson.toString()).put(
            "memberScopedFields",
            org.json.JSONArray().put("allowedPackages").put("dayGroups"),
        )

        val policy = RemotePolicyCodec.parse(json)!!

        assertEquals(setOf("allowedPackages", "dayGroups"), policy.memberScopedFields)
        assertEquals(policy, RemotePolicyCodec.parse(RemotePolicyCodec.toJson(policy))!!)
    }

    @Test
    fun aMissingMaskIsALegacyBackendAndAnEmptyMaskStaysExplicit() {
        // Absent = backend predates member projection: the TV must keep every local field.
        assertNull(RemotePolicyCodec.parse(policyJson)!!.memberScopedFields)

        // An explicit JSON null is how the backend serializes "not projected for a member";
        // like an absent key it must keep every local field, not invalidate the snapshot.
        val explicitNull = JSONObject(policyJson.toString())
            .put("memberScopedFields", JSONObject.NULL)
        assertNull(RemotePolicyCodec.parse(explicitNull)!!.memberScopedFields)

        // An empty array is a real mask (no field projected) and must not be confused with
        // "legacy backend"; conflating them would widen a child's scope.
        val emptyMask = JSONObject(policyJson.toString())
            .put("memberScopedFields", org.json.JSONArray())
        assertEquals(
            emptySet<String>(),
            RemotePolicyCodec.parse(emptyMask)!!.memberScopedFields,
        )
    }

    @Test
    fun malformedMaskInvalidatesTheSnapshotInsteadOfWidening() {
        val notAnArray = JSONObject(policyJson.toString()).put("memberScopedFields", "dayGroups")
        assertNull(RemotePolicyCodec.parse(notAnArray))

        val nonString = JSONObject(policyJson.toString())
            .put("memberScopedFields", org.json.JSONArray().put(1))
        assertNull(RemotePolicyCodec.parse(nonString))

        val blank = JSONObject(policyJson.toString())
            .put("memberScopedFields", org.json.JSONArray().put("  "))
        assertNull(RemotePolicyCodec.parse(blank))
    }

    @Test
    fun theMaskIsAlsoReadFromTheManualRuleSet() {
        val json = withRuleSets(policyJson).apply {
            getJSONObject("manualRules")
                .put("memberScopedFields", org.json.JSONArray().put("sessionLimitMinutes"))
        }

        assertEquals(
            setOf("sessionLimitMinutes"),
            RemotePolicyCodec.parse(json)!!.memberScopedFields,
        )
    }

    @Test
    fun unlimitedMemberQuotaArrivesAsJsonNull() {
        // 2026-11-21 额度口径：后端只对「不限」下发显式 JSON null，0 / 1440 都是字面分钟数。
        // 收到 null 必须当成「不限」，不能读成被截断或 0 分钟。旧扁平 getter 仍把 null
        // 投影成 MAX_DAILY_MINUTES（托管交接契约，见 LocalDayGroupsTest）。
        val json = withRules(policyJson) {
            it.put("autoDailyLimitMinutes", JSONObject.NULL)
            it.remove("dailyLimitMinutes")
            it.put("weekendDailyLimitMinutes", JSONObject.NULL)
        }

        val rules = RemotePolicyCodec.parse(json)!!.rules

        assertNull(rules.entertainmentDailyLimitFor(1))
        assertNull(rules.entertainmentDailyLimitFor(6))
        assertEquals(1440, rules.autoDailyLimitMinutes)
        val restored = RemotePolicyCodec.parse(
            RemotePolicyCodec.toJson(RemotePolicyCodec.parse(json)!!),
        )!!.rules
        assertNull(restored.entertainmentDailyLimitFor(1))
        assertNull(restored.entertainmentDailyLimitFor(6))
    }

    @Test
    fun explicitNullSessionCapUsesTheLocalDefault() {
        // 设备级策略没有「单次娱乐上限」投影时后端会下发显式 null，不能整份作废。
        val json = withRules(policyJson) { it.put("sessionCapMinutes", JSONObject.NULL) }
        val policy = RemotePolicyCodec.parse(json)!!
        assertTrue(policy.rulesUsable)
        assertEquals(LocalRules.DEFAULTS.sessionCapMinutes, policy.rules.sessionCapMinutes)
    }

    @Test
    fun literalZeroAndFullDayQuotasAreNotFoldedIntoUnlimited() {
        // 2026-11-21：0 = 0 分钟（当天禁止娱乐）、1440 = 字面 24 小时，都不能被折叠成 null。
        // 旧实现把 1440 当「不限」，电视会显示成「不限」，与家长所配不一致。
        val zero = RemotePolicyCodec.parse(
            withRules(policyJson) {
                it.put("autoDailyLimitMinutes", 0)
                it.remove("dailyLimitMinutes")
                it.put("weekendDailyLimitMinutes", 0)
            },
        )!!.rules
        assertEquals(0, zero.entertainmentDailyLimitFor(1))
        assertEquals(0, zero.entertainmentDailyLimitFor(6))

        val fullDay = RemotePolicyCodec.parse(
            withRules(policyJson) {
                it.put("autoDailyLimitMinutes", 1440)
                it.remove("dailyLimitMinutes")
                it.put("weekendDailyLimitMinutes", 1440)
            },
        )!!
        assertEquals(1440, fullDay.rules.entertainmentDailyLimitFor(1))
        assertEquals(1440, fullDay.rules.entertainmentDailyLimitFor(6))
        val restored = RemotePolicyCodec.parse(RemotePolicyCodec.toJson(fullDay))!!.rules
        assertEquals(1440, restored.entertainmentDailyLimitFor(1))
        assertEquals(1440, restored.entertainmentDailyLimitFor(6))
    }

    @Test
    fun dayGroupsOverrideTheLegacyWeekdayAndWeekendWindows() {
        val json = withRules(policyJson) {
            it.put(
                "dayGroups",
                org.json.JSONArray()
                    .put(dayGroup(listOf(1, 2, 3, 4, 5), 600, 660, studyLimit = null, entLimit = 120))
                    .put(dayGroup(listOf(6, 7), 540, 720, studyLimit = null, entLimit = null)),
            )
        }

        val policy = RemotePolicyCodec.parse(json)!!
        val rules = policy.rules

        assertEquals(2, rules.dayGroups.size)
        assertEquals(120, rules.entertainmentDailyLimitFor(1))
        assertNull(rules.entertainmentDailyLimitFor(6))
        assertEquals(600, rules.entertainmentWindowsFor(1).single().startMinute)
        assertEquals(720, rules.entertainmentWindowsFor(6).single().endMinute)
        // The legacy flat arrays from policyJson no longer describe the schedule.
        assertEquals(120, rules.autoDailyLimitMinutes)
        assertEquals(1440, rules.weekendDailyLimitMinutes)
        assertEquals(policy, RemotePolicyCodec.parse(RemotePolicyCodec.toJson(policy))!!)
    }

    @Test
    fun anIncompleteDayGroupsScheduleInvalidatesTheSnapshot() {
        // Every day must belong to exactly one group; leaving Sunday out is truncated data,
        // not permission to keep the legacy weekday/weekend projection.
        val missingDay = withRules(policyJson) {
            it.put(
                "dayGroups",
                org.json.JSONArray()
                    .put(dayGroup(listOf(1, 2, 3, 4, 5, 6), 600, 660, studyLimit = null, entLimit = 120)),
            )
        }
        assertNull(RemotePolicyCodec.parse(missingDay))

        val duplicateDay = withRules(policyJson) {
            it.put(
                "dayGroups",
                org.json.JSONArray()
                    .put(dayGroup(listOf(1, 2, 3, 4, 5), 600, 660, studyLimit = null, entLimit = 120))
                    .put(dayGroup(listOf(5, 6, 7), 600, 660, studyLimit = null, entLimit = 120)),
            )
        }
        assertNull(RemotePolicyCodec.parse(duplicateDay))

        val notAnArray = withRules(policyJson) { it.put("dayGroups", "1-5") }
        assertNull(RemotePolicyCodec.parse(notAnArray))
    }

    @Test
    fun negativeRouteCountsSurviveAsUnlimited() {
        val json = withRules(policyJson) {
            it.put("quizDailyLimitCount", -1)
            it.put("studyRewardDailyLimitCount", 0)
            it.put("directDailyFreeCount", 99)
        }

        val rules = RemotePolicyCodec.parse(json)!!.rules

        assertEquals(-1, rules.quizDailyLimitCount)
        assertEquals(0, rules.studyRewardDailyLimitCount)
        assertEquals(99, rules.directDailyFreeCount)
    }

    @Test
    fun largeRouteCountsSurviveAndOnlyValuesPastTheIntBoundAreRejected() {
        val large = withRules(policyJson) { it.put("quizDailyLimitCount", 100000) }
        assertEquals(100000, RemotePolicyCodec.parse(large)!!.rules.quizDailyLimitCount)

        val tooLarge = withRules(policyJson) { it.put("quizDailyLimitCount", 2147483648L) }
        assertNull(RemotePolicyCodec.parse(tooLarge))

        val tooSmall = withRules(policyJson) { it.put("directDailyFreeCount", -2) }
        assertNull(RemotePolicyCodec.parse(tooSmall))
    }

    private fun dayGroup(
        days: List<Int>,
        entStart: Int,
        entEnd: Int,
        studyLimit: Int?,
        entLimit: Int?,
    ): JSONObject {
        val daysArray = org.json.JSONArray()
        days.forEach(daysArray::put)
        return JSONObject()
            .put("days", daysArray)
            .put("studyWindows", windowArray(0, 1440))
            .put("studyDailyLimitMinutes", studyLimit ?: JSONObject.NULL)
            .put("entertainmentWindows", windowArray(entStart, entEnd))
            .put("entertainmentLimitMinutes", entLimit ?: JSONObject.NULL)
    }

    private fun withRuleSets(source: JSONObject): JSONObject = JSONObject(source.toString())
        .put(
            "manualRules",
            JSONObject()
                .put("weekdayWindows", windowArray(1080, 1200))
                .put("weekendWindows", windowArray(540, 1200))
                .put("dailyLimitMinutes", 120)
                .put("sessionLimitMinutes", 30)
                .put("restMinutes", 0)
                .put("sessionScreenOffBufferMinutes", 35),
        )

    private fun windowArray(start: Int, end: Int): org.json.JSONArray =
        org.json.JSONArray().put(
            JSONObject().put("startMinute", start).put("endMinute", end),
        )

    private fun withRules(
        source: JSONObject,
        change: (JSONObject) -> Unit,
    ): JSONObject {
        val rules = JSONObject(source.getJSONObject("rules").toString())
        change(rules)
        return JSONObject(source.toString()).put("rules", rules)
    }

    @Test
    fun emptyOrNullNodeIsIgnored() {
        assertNull(RemotePolicyCodec.parse(null))
        assertNull(RemotePolicyCodec.parse(JSONObject()))
    }

    @Test
    fun explicitNullOptionalPackagesDoNotInvalidateTheSnapshot() {
        // 后端 Jackson 默认把未投影的名单字段序列化成显式 null；旧 codec 会因此整份作废。
        val json = withRules(policyJson) {
            it.put("studyPackages", JSONObject.NULL)
            it.put("unlimitedPackages", JSONObject.NULL)
            it.put("studyEarnPackages", JSONObject.NULL)
        }
        val policy = RemotePolicyCodec.parse(json)!!
        assertTrue(policy.rulesUsable)
        assertEquals(emptySet<String>(), policy.rules.studyPackages)
        assertEquals(emptySet<String>(), policy.rules.unlimitedPackages)
        assertEquals(emptySet<String>(), policy.rules.studyEarnPackages)
    }

    @Test
    fun explicitNullMemberMaskMeansLegacySnapshot() {
        // 未按小果农投影时后端会输出 "memberScopedFields": null；它表示「没有 mask」，
        // 不是脏数据——整份快照仍要可用，本地字段全部保留。
        val manualRules = JSONObject(
            """
            {
              "weekdayWindows": [{"startMinute": 1080, "endMinute": 1200}],
              "weekendWindows": [{"startMinute": 540, "endMinute": 1200}],
              "dailyLimitMinutes": 60,
              "sessionLimitMinutes": 20,
              "restMinutes": 0,
              "sessionScreenOffBufferMinutes": 20,
              "dayGroups": null,
              "sessionCapMinutes": null,
              "quizGateEnabled": null,
              "studyEarnEnabled": null,
              "directStartEnabled": null,
              "quizDailyLimitCount": null,
              "studyRewardDailyLimitCount": null,
              "directDailyFreeCount": null,
              "memberScopedFields": null
            }
            """.trimIndent(),
        )
        val json = withRules(policyJson) {
            it.put("unlimitedPackages", JSONObject.NULL)
            it.put("studyEarnPackages", JSONObject.NULL)
        }.put("manualRules", manualRules)
        val policy = RemotePolicyCodec.parse(json)!!
        assertTrue(policy.rulesUsable)
        assertNull(policy.memberScopedFields)
        assertTrue(policy.ruleSets != null)
    }
}
