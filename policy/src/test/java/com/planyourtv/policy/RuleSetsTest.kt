package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleSetsTest {
    private val rules = LocalRules(
        weekdayWindows = listOf(LocalTimeWindow(1080, 1200)),
        weekendWindows = listOf(LocalTimeWindow(540, 1200)),
        autoDailyLimitMinutes = 90,
        sessionLimitMinutes = 25,
        restMinutes = 10,
        sessionScreenOffBufferMinutes = 30,
        allowedPackages = setOf("com.example.tv"),
        studyPackages = setOf("com.example.study"),
    )

    @Test
    fun migrationCopiesTheSingleSetIntoBothAndKeepsRest() {
        val sets = LocalRuleSets.from(rules)

        assertEquals(LocalRuleSet.of(rules), sets.auto)
        assertEquals(LocalRuleSet.of(rules), sets.manual)
        assertEquals(10, sets.auto.restMinutes)
        assertEquals(10, sets.manual.restMinutes)
        assertEquals(30, sets.auto.sessionScreenOffBufferMinutes)
        assertEquals(30, sets.manual.sessionScreenOffBufferMinutes)
        assertEquals(LocalSharedAppScope.of(rules), sets.shared)
        assertTrue(sets.isUsable)
    }

    @Test
    fun freshSetupGivesTheInactiveActivationItsOwnRestDefault() {
        val manualStart = LocalRuleSets.from(rules.copy(restMinutes = 0), Activation.MANUAL)
        assertEquals(0, manualStart.manual.restMinutes)
        assertEquals(10, manualStart.auto.restMinutes)
        assertEquals(30, manualStart.auto.sessionScreenOffBufferMinutes)
        assertEquals(30, manualStart.manual.sessionScreenOffBufferMinutes)

        val autoStart = LocalRuleSets.from(rules.copy(restMinutes = 10), Activation.AUTO)
        assertEquals(10, autoStart.auto.restMinutes)
        assertEquals(0, autoStart.manual.restMinutes)
        assertEquals(30, autoStart.manual.sessionScreenOffBufferMinutes)
    }

    @Test
    fun materializationCombinesTheSetWithTheSharedApps() {
        val sets = LocalRuleSets.from(rules).copy(
            manual = LocalRuleSet(
                weekdayWindows = listOf(LocalTimeWindow(600, 660)),
                weekendWindows = listOf(LocalTimeWindow(600, 660)),
                autoDailyLimitMinutes = 40,
                sessionLimitMinutes = 15,
                restMinutes = 0,
                sessionScreenOffBufferMinutes = 25,
            ),
        )

        val manual = sets.rulesFor(Activation.MANUAL)
        assertEquals(40, manual.autoDailyLimitMinutes)
        assertEquals(0, manual.restMinutes)
        assertEquals(setOf("com.example.tv"), manual.allowedPackages)
        assertEquals(setOf("com.example.study"), manual.studyPackages)
        // The set's own buffer wins over the legacy shared value.
        assertEquals(25, manual.sessionScreenOffBufferMinutes)

        val auto = sets.rulesFor(Activation.AUTO)
        assertEquals(90, auto.autoDailyLimitMinutes)
        assertEquals(10, auto.restMinutes)
        assertEquals(30, auto.sessionScreenOffBufferMinutes)
    }

    @Test
    fun aSetWithoutItsOwnBufferFallsBackToTheSharedValue() {
        val legacyManual = LocalRuleSet(
            weekdayWindows = listOf(LocalTimeWindow(600, 660)),
            weekendWindows = listOf(LocalTimeWindow(600, 660)),
            autoDailyLimitMinutes = 40,
            sessionLimitMinutes = 15,
            restMinutes = 0,
        )

        val sets = LocalRuleSets.from(rules).copy(manual = legacyManual)

        assertEquals(30, sets.rulesFor(Activation.MANUAL).sessionScreenOffBufferMinutes)
        assertNull(sets.manual.sessionScreenOffBufferMinutes)
    }

    @Test
    fun editingTheActiveSetKeepsTheInactiveSetUntouched() {
        val sets = LocalRuleSets.from(rules).copy(
            manual = LocalRuleSet.of(
                rules.copy(
                    autoDailyLimitMinutes = 30,
                    restMinutes = 0,
                    sessionScreenOffBufferMinutes = 25,
                ),
            ),
        )

        val updated = sets.withActiveRules(
            rules.copy(autoDailyLimitMinutes = 45, sessionScreenOffBufferMinutes = 15),
            Activation.AUTO,
        )

        assertEquals(45, updated.auto.autoDailyLimitMinutes)
        assertEquals(45, updated.rulesFor(Activation.AUTO).autoDailyLimitMinutes)
        // The manual set keeps its own quota, rest and buffer; a switch must not adopt AUTO's.
        assertEquals(30, updated.manual.autoDailyLimitMinutes)
        assertEquals(0, updated.manual.restMinutes)
        assertEquals(25, updated.manual.sessionScreenOffBufferMinutes)
        assertEquals(15, updated.auto.sessionScreenOffBufferMinutes)
        // The legacy shared mirror follows the active activation.
        assertEquals(15, updated.shared.sessionScreenOffBufferMinutes)
    }

    @Test
    fun sharedAppEditsApplyToBothActivationsButBuffersStayPerSet() {
        val sets = LocalRuleSets.from(rules)
        val edited = rules.copy(
            allowedPackages = setOf("com.example.other"),
            studyPackages = emptySet(),
            sessionScreenOffBufferMinutes = 15,
        )

        val updated = sets.withActiveRules(edited, Activation.MANUAL)

        assertEquals(setOf("com.example.other"), updated.shared.allowedPackages)
        assertEquals(15, updated.shared.sessionScreenOffBufferMinutes)
        // App scope is device-wide; the buffer is not. AUTO keeps the value it was seeded with.
        assertEquals(15, updated.rulesFor(Activation.MANUAL).sessionScreenOffBufferMinutes)
        assertEquals(30, updated.rulesFor(Activation.AUTO).sessionScreenOffBufferMinutes)
        assertEquals(setOf("com.example.other"), updated.rulesFor(Activation.AUTO).allowedPackages)
    }

    @Test
    fun aSetWithoutAnyWindowIsNotUsable() {
        val empty = LocalRuleSets.from(rules).copy(
            auto = LocalRuleSet.of(
                rules.copy(weekdayWindows = emptyList(), weekendWindows = emptyList()),
            ),
        )

        assertFalse(empty.auto.isUsableWith(empty.shared))
        assertFalse(empty.isUsable)
        assertTrue(empty.manual.isUsableWith(empty.shared))
    }
}
