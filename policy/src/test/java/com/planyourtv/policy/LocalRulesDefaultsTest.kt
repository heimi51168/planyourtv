package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalRulesDefaultsTest {
    @Test
    fun manualAndAutomaticDefaultsDifferOnlyInRest() {
        val manual = LocalRules.defaultsFor(Activation.MANUAL)
        val automatic = LocalRules.defaultsFor(Activation.AUTO)
        assertEquals(LocalRules.DEFAULTS, manual)
        assertEquals(0, manual.restMinutes)
        assertEquals(10, automatic.restMinutes)
        assertEquals(manual.copy(restMinutes = 10), automatic)
    }

    @Test
    fun modeSwitchFollowsTheNewRestDefaultOnlyWhenTheValueIsUntouched() {
        val manual = LocalRules.defaultsFor(Activation.MANUAL)
        val automatic = LocalRules.defaultsFor(Activation.AUTO)

        assertEquals(
            10,
            manual.withModeDefaultRest(Activation.MANUAL, Activation.AUTO).restMinutes,
        )
        assertEquals(
            0,
            automatic.withModeDefaultRest(Activation.AUTO, Activation.MANUAL).restMinutes,
        )

        // A parent's own value survives both directions.
        val customManual = manual.copy(restMinutes = 12)
        assertEquals(
            12,
            customManual.withModeDefaultRest(Activation.MANUAL, Activation.AUTO).restMinutes,
        )
        val customAutomatic = automatic.copy(restMinutes = 12)
        assertEquals(
            12,
            customAutomatic.withModeDefaultRest(Activation.AUTO, Activation.MANUAL).restMinutes,
        )
    }

    @Test
    fun switchingToTheSameActivationKeepsTheRules() {
        val rules = LocalRules.DEFAULTS.copy(restMinutes = 12)
        assertEquals(rules, rules.withModeDefaultRest(Activation.MANUAL, Activation.MANUAL))
        assertEquals(
            rules,
            rules.withModeDefaultRest(Activation.AUTO, Activation.AUTO),
        )
    }

    @Test
    fun firstRunTimesAllowTheWholeDay() {
        assertEquals(
            listOf(LocalTimeWindow(1, 23 * 60 + 59)),
            LocalRules.DEFAULTS.weekdayWindows,
        )
        assertEquals(
            listOf(LocalTimeWindow(1, 23 * 60 + 59)),
            LocalRules.DEFAULTS.weekendWindows,
        )
    }
}
