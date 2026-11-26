package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchProtectionTest {
    @Test
    fun disabledPolicyNeverExempts() {
        val policy = SwitchProtection(
            required = false,
            exemptDays = listOf(1, 2),
            exemptStartMinute = 0,
            exemptEndMinute = 24 * 60,
        )

        assertFalse(policy.hasExemption)
        assertFalse(policy.isExemptAt(dayOfWeek = 1, minuteOfDay = 10 * 60))
        assertEquals(SwitchProtection(), policy.normalized())
    }

    @Test
    fun enabledPolicyUsesHalfOpenWindowOnSelectedDays() {
        val policy = SwitchProtection(
            required = true,
            exemptDays = listOf(3, 1, 3),
            exemptStartMinute = 9 * 60,
            exemptEndMinute = 15 * 60,
        ).normalized()!!

        assertEquals(listOf(1, 3), policy.exemptDays)
        assertTrue(policy.isExemptAt(dayOfWeek = 1, minuteOfDay = 9 * 60))
        assertTrue(policy.isExemptAt(dayOfWeek = 1, minuteOfDay = 14 * 60 + 59))
        assertFalse(policy.isExemptAt(dayOfWeek = 1, minuteOfDay = 8 * 60 + 59))
        assertFalse(policy.isExemptAt(dayOfWeek = 1, minuteOfDay = 15 * 60))
        assertFalse(policy.isExemptAt(dayOfWeek = 2, minuteOfDay = 10 * 60))
    }

    @Test
    fun enabledPolicyWithNoDaysAlwaysAsks() {
        val policy = SwitchProtection(required = true)

        assertFalse(policy.hasExemption)
        assertFalse(policy.isExemptAt(dayOfWeek = 1, minuteOfDay = 10 * 60))
    }

    @Test
    fun invalidPolicyIsRejected() {
        assertNull(
            SwitchProtection(
                required = true,
                exemptDays = listOf(0),
            ).normalized(),
        )
        assertNull(
            SwitchProtection(
                required = true,
                exemptDays = listOf(1),
                exemptStartMinute = 15 * 60,
                exemptEndMinute = 9 * 60,
            ).normalized(),
        )
        assertNull(
            SwitchProtection(
                required = true,
                exemptDays = listOf(1),
                exemptStartMinute = 9 * 60,
                exemptEndMinute = 24 * 60 + 1,
            ).normalized(),
        )
    }

    @Test
    fun commonExemptionRequiresEveryMemberToAgree() {
        val first = member("a", days = listOf(1, 2), start = 9 * 60, end = 15 * 60)
        val same = member("b", days = listOf(2, 1), start = 9 * 60, end = 15 * 60)
        val other = member("c", days = listOf(1), start = 9 * 60, end = 15 * 60)

        assertEquals(
            SwitchProtection(
                required = true,
                exemptDays = listOf(1, 2),
                exemptStartMinute = 9 * 60,
                exemptEndMinute = 15 * 60,
            ),
            SwitchProtection.commonExemption(listOf(first, same)),
        )
        assertNull(SwitchProtection.commonExemption(listOf(first, same, other)))
        assertNull(SwitchProtection.commonExemption(emptyList()))
    }

    @Test
    fun commonExemptionIgnoresAnEmptyWaiver() {
        val noWindow = member("a", days = emptyList())
        val withWindow = member("b", days = listOf(1))

        assertNull(SwitchProtection.commonExemption(listOf(noWindow, withWindow)))
        assertNull(SwitchProtection.commonExemption(listOf(noWindow)))
    }

    @Test
    fun commonExemptionRejectsInvalidStoredWindow() {
        val invalid = member(
            "a",
            days = listOf(1),
            start = 15 * 60,
            end = 9 * 60,
        )

        assertNull(SwitchProtection.commonExemption(listOf(invalid)))
    }

    private fun member(
        userId: String,
        days: List<Int>,
        start: Int = SwitchPinExempt.DEFAULT_START_MINUTE,
        end: Int = SwitchPinExempt.DEFAULT_END_MINUTE,
    ): FamilyMember = FamilyMember(
        familyId = "fam_test",
        userId = userId,
        displayName = userId,
        switchPinRequired = true,
        switchPinExemptDays = days,
        switchPinExemptStartMinute = start,
        switchPinExemptEndMinute = end,
        createdAt = 1L,
        updatedAt = 1L,
    )
}