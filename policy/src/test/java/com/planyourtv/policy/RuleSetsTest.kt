package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleSetsTest {
    private val rules = LocalRules(
        dayGroups = LocalRules.fromLegacyWeekdayWeekend(
            weekdayWindows = listOf(LocalTimeWindow(1080, 1200)),
            weekendWindows = listOf(LocalTimeWindow(540, 1200)),
            weekdayLimitMinutes = 90,
            weekendLimitMinutes = 90,
        ),
        sessionLimitMinutes = 25,
        restMinutes = 10,
        sessionScreenOffBufferMinutes = 30,
        allowedPackages = setOf("com.example.tv"),
        studyPackages = setOf("com.example.study"),
        // V45 keeps the legacy field as a projection of the study scope; canonical rules spell
        // out the whole list so a from().rules() round trip is lossless.
        studyEarnPackages = setOf("com.example.study"),
    )

    @Test
    fun migrationStoresOneManualSetAndTheSharedScope() {
        val sets = LocalRuleSets.from(rules)

        assertEquals(LocalRuleSet.of(rules), sets.manual)
        assertEquals(10, sets.manual.restMinutes)
        assertEquals(30, sets.manual.sessionScreenOffBufferMinutes)
        assertEquals(LocalSharedAppScope.of(rules), sets.shared)
        assertEquals(rules, sets.rules())
        assertTrue(sets.isUsable)
    }

    @Test
    fun materializationCombinesTheSetWithTheSharedApps() {
        val sets = LocalRuleSets.from(rules).copy(
            manual = LocalRuleSet(
                dayGroups = LocalRules.fromLegacyWeekdayWeekend(
                    weekdayWindows = listOf(LocalTimeWindow(600, 660)),
                    weekendWindows = listOf(LocalTimeWindow(600, 660)),
                    weekdayLimitMinutes = 40,
                    weekendLimitMinutes = 40,
                ),
                sessionLimitMinutes = 15,
                restMinutes = 0,
                sessionScreenOffBufferMinutes = 25,
            ),
        )

        val manual = sets.rules()
        assertEquals(40, manual.autoDailyLimitMinutes)
        assertEquals(0, manual.restMinutes)
        assertEquals(setOf("com.example.tv"), manual.allowedPackages)
        assertEquals(setOf("com.example.study"), manual.studyPackages)
        assertEquals(25, manual.sessionScreenOffBufferMinutes)
    }

    @Test
    fun theManualSetCarriesItsSingleBuffer() {
        val manual = LocalRuleSet(
            dayGroups = LocalRules.fromLegacyWeekdayWeekend(
                weekdayWindows = listOf(LocalTimeWindow(600, 660)),
                weekendWindows = listOf(LocalTimeWindow(600, 660)),
                weekdayLimitMinutes = 40,
                weekendLimitMinutes = 40,
            ),
            sessionLimitMinutes = 15,
            restMinutes = 0,
            sessionScreenOffBufferMinutes = 25,
        )

        val sets = LocalRuleSets.from(rules).copy(manual = manual)

        assertEquals(25, sets.rules().sessionScreenOffBufferMinutes)
        assertEquals(25, sets.manual.sessionScreenOffBufferMinutes)
    }

    @Test
    fun editingRulesWritesBothTheSetAndTheSharedScope() {
        val sets = LocalRuleSets.from(rules)
        val edited = rules.copy(
            dayGroups = rules.dayGroups.map { it.copy(entertainmentDailyLimitMinutes = 45) },
            restMinutes = 5,
            sessionScreenOffBufferMinutes = 15,
            allowedPackages = setOf("com.example.other"),
            studyPackages = emptySet(),
            // V45: clearing the study scope also clears the legacy study-earn projection.
            studyEarnPackages = emptySet(),
            unlimitedPackages = setOf("com.example.free"),
        )

        val updated = sets.withRules(edited)

        assertEquals(45, updated.manual.autoDailyLimitMinutes)
        assertEquals(5, updated.manual.restMinutes)
        assertEquals(15, updated.manual.sessionScreenOffBufferMinutes)
        assertEquals(setOf("com.example.other"), updated.shared.allowedPackages)
        assertEquals(setOf("com.example.free"), updated.shared.unlimitedPackages)
        assertEquals(edited, updated.rules())
    }

    @Test
    fun theMemberMaskSelectsCloudFieldsAndKeepsEveryOtherLocalValue() {
        val previous = LocalRuleSets.from(
            rules.copy(
                sessionLimitMinutes = 25,
                restMinutes = 10,
                sessionScreenOffBufferMinutes = 30,
                quizGateEnabled = true,
                quizDailyLimitCount = 2,
                // 掩码外的次数各给一个不同于云端的本机值（云端保持默认 3），
                // 才能真正证明「云没投影的字段保留本地值」，而不是碰巧撞上同一个默认值。
                studyRewardDailyLimitCount = 7,
                directDailyFreeCount = 4,
            ),
        )
        val cloud = LocalRuleSets.from(
            rules.copy(
                dayGroups = rules.dayGroups.map { it.copy(entertainmentDailyLimitMinutes = 45) },
                sessionLimitMinutes = 90,
                restMinutes = 0,
                sessionScreenOffBufferMinutes = 15,
                quizGateEnabled = false,
                quizDailyLimitCount = -1,
                allowedPackages = setOf("com.example.cloud"),
            ),
        )

        val merged = cloud.preservingDeviceFieldsFrom(
            previous,
            setOf("dayGroups", "allowedPackages", "quizDailyLimitCount"),
        )

        // Inside the mask the cloud wins.
        assertEquals(cloud.manual.dayGroups, merged.manual.dayGroups)
        assertEquals(setOf("com.example.cloud"), merged.shared.allowedPackages)
        assertEquals(-1, merged.manual.quizDailyLimitCount)
        // Outside the mask the local value stays: the backend never projected these fields.
        assertEquals(25, merged.manual.sessionLimitMinutes)
        assertEquals(10, merged.manual.restMinutes)
        assertEquals(30, merged.manual.sessionScreenOffBufferMinutes)
        assertTrue(merged.manual.quizGateEnabled)
        assertEquals(7, merged.manual.studyRewardDailyLimitCount)
        assertEquals(4, merged.manual.directDailyFreeCount)
    }

    @Test
    fun anEmptyMemberMaskKeepsTheWholeLocalSnapshot() {
        val previous = LocalRuleSets.from(
            rules.copy(sessionLimitMinutes = 25, quizDailyLimitCount = 2),
        )
        val cloud = LocalRuleSets.from(
            rules.copy(
                sessionLimitMinutes = 90,
                quizDailyLimitCount = -1,
                allowedPackages = setOf("com.example.cloud"),
            ),
        )

        val merged = cloud.preservingDeviceFieldsFrom(previous, emptySet())

        assertEquals(previous, merged)
    }

    @Test
    fun aSetWithoutAnyWindowIsNotUsable() {
        val closed = rules.copy(
            dayGroups = rules.dayGroups.map {
                it.copy(studyWindows = emptyList(), entertainmentWindows = emptyList())
            },
        )
        val empty = LocalRuleSets.from(rules).copy(
            manual = LocalRuleSet.of(closed),
        )

        assertFalse(empty.manual.isUsableWith(empty.shared))
        assertFalse(empty.isUsable)
    }
}