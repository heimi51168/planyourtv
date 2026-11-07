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
            "restMinutes": 10
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
    fun missingRestUsesTheActivationDefault() {
        val manualJson = withRules(policyJson) { it.remove("restMinutes") }
        assertEquals(0, RemotePolicyCodec.parse(manualJson)!!.rules.restMinutes)

        val autoJson = JSONObject(manualJson.toString()).put("activation", "AUTO")
        assertEquals(10, RemotePolicyCodec.parse(autoJson)!!.rules.restMinutes)
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
    fun missingAllowedPackagesMeansAllApps() {
        val policy = RemotePolicyCodec.parse(policyJson)!!
        assertEquals(setOf(ALL_VALID_APP), policy.rules.effectiveAllowedPackages)
        assertFalse(policy.rules.usesAppSelection)
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
    fun schema1EmptyAllowedPackagesNormalizeToAllApps() {
        val json = withRules(policyJson) { it.put("allowedPackages", org.json.JSONArray()) }
        val rules = RemotePolicyCodec.parse(json)!!.rules
        assertEquals(setOf(ALL_VALID_APP), rules.effectiveAllowedPackages)
        assertFalse(rules.usesAppSelection)
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
    fun schema2MustCarryTheAllowedPackagesField() {
        val json = withRules(policyJson) { it.put("schemaVersion", 2) }
        assertNull(RemotePolicyCodec.parse(json))
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
        val tooSmall = withRules(policyJson) { it.put("sessionScreenOffBufferMinutes", 1) }
        assertEquals(
            LocalRules.MIN_SESSION_BUFFER_MINUTES,
            RemotePolicyCodec.parse(tooSmall)!!.rules.sessionScreenOffBufferMinutes,
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
    fun twoRuleSetsAreParsedAndRoundTrip() {
        val policy = RemotePolicyCodec.parse(withRuleSets(policyJson))!!

        assertTrue(policy.supportsRuleSets)
        val sets = policy.ruleSets!!
        assertEquals(60, sets.auto.autoDailyLimitMinutes)
        assertEquals(5, sets.auto.restMinutes)
        assertEquals(25, sets.auto.sessionScreenOffBufferMinutes)
        assertEquals(120, sets.manual.autoDailyLimitMinutes)
        assertEquals(0, sets.manual.restMinutes)
        assertEquals(35, sets.manual.sessionScreenOffBufferMinutes)
        // Shared app scope comes from the rules snapshot, not from either time set.
        assertEquals(policy.rules.effectiveAllowedPackages, sets.shared.allowedPackages)

        val restored = RemotePolicyCodec.parse(RemotePolicyCodec.toJson(policy))!!
        assertEquals(policy, restored)
    }

    @Test
    fun ruleSetsWithoutTheirOwnBufferFallBackToTheFlatSnapshot() {
        val json = withRuleSets(policyJson).apply {
            getJSONObject("autoRules").remove("sessionScreenOffBufferMinutes")
            getJSONObject("manualRules").remove("sessionScreenOffBufferMinutes")
        }.let { withRules(it) { rules -> rules.put("sessionScreenOffBufferMinutes", 40) } }

        val sets = RemotePolicyCodec.parse(json)!!.ruleSets!!
        assertEquals(40, sets.auto.sessionScreenOffBufferMinutes)
        assertEquals(40, sets.manual.sessionScreenOffBufferMinutes)
    }

    @Test
    fun outOfRangeRuleSetBufferIsRejected() {
        val json = withRuleSets(policyJson)
        json.getJSONObject("autoRules").put("sessionScreenOffBufferMinutes", 120)
        assertNull(RemotePolicyCodec.parse(json))
    }

    @Test
    fun oneMissingRuleSetIsRejectedInsteadOfGuessed() {
        val json = withRuleSets(policyJson).apply { remove("manualRules") }
        assertNull(RemotePolicyCodec.parse(json))
    }

    @Test
    fun ruleSetMissingAFieldIsRejected() {
        val json = withRuleSets(policyJson)
        json.getJSONObject("autoRules").remove("restMinutes")
        assertNull(RemotePolicyCodec.parse(json))
    }

    private fun withRuleSets(source: JSONObject): JSONObject = JSONObject(source.toString())
        .put(
            "autoRules",
            JSONObject()
                .put("weekdayWindows", windowArray(1080, 1200))
                .put("weekendWindows", windowArray(540, 1200))
                .put("dailyLimitMinutes", 60)
                .put("sessionLimitMinutes", 20)
                .put("restMinutes", 5)
                .put("sessionScreenOffBufferMinutes", 25),
        )
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
}
