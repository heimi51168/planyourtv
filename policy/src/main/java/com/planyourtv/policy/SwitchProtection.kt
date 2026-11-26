package com.planyourtv.policy

/**
 * Device-wide policy for asking the target member's switch password on the TV lock screen.
 *
 * Every active member shares the switch and the optional same-day waiver window. Member
 * credentials stay per-member, so the parent can still verify who is being entered instead of
 * using one shared family password. The waiver only decides whether the prompt is shown at the
 * moment the subject is switched; it never changes the password itself.
 *
 * [exemptDays] is empty when no waiver is configured. Invalid stored values must fail closed:
 * [normalized] returns null instead of silently widening or inventing an exemption.
 */
data class SwitchProtection(
    val required: Boolean = false,
    val exemptDays: List<Int> = emptyList(),
    val exemptStartMinute: Int = SwitchPinExempt.DEFAULT_START_MINUTE,
    val exemptEndMinute: Int = SwitchPinExempt.DEFAULT_END_MINUTE,
) {
    val hasExemption: Boolean
        get() = required && exemptDays.isNotEmpty()

    /** Distinct, sorted days and a valid half-open window, or null for invalid input. */
    fun normalized(): SwitchProtection? {
        if (!required) return SwitchProtection()
        val days = SwitchPinExempt.normalizeDays(exemptDays) ?: return null
        if (!SwitchPinExempt.isValidWindow(exemptStartMinute, exemptEndMinute)) return null
        return copy(exemptDays = days)
    }

    /**
     * True when [this] policy currently waives the password. Protection must be on, the day must
     * be selected, the window must still be valid, and [minuteOfDay] must fall in `[start, end)`.
     * Anything unexpected asks for the password.
     */
    fun isExemptAt(dayOfWeek: Int, minuteOfDay: Int): Boolean =
        required &&
            dayOfWeek in 1..7 &&
            dayOfWeek in exemptDays &&
            SwitchPinExempt.isValidWindow(exemptStartMinute, exemptEndMinute) &&
            minuteOfDay >= exemptStartMinute &&
            minuteOfDay < exemptEndMinute

    companion object {
        /**
         * Returns the one waiver shared by every [members] entry, or null when they are empty,
         * disagree, or do not configure a waiver. Used only to migrate the old per-member window
         * without silently picking one member's schedule for the whole family.
         */
        fun commonExemption(members: Collection<FamilyMember>): SwitchProtection? {
            if (members.isEmpty()) return null
            val policies = members.map { member ->
                SwitchProtection(
                    required = true,
                    exemptDays = member.switchPinExemptDays,
                    exemptStartMinute = member.switchPinExemptStartMinute,
                    exemptEndMinute = member.switchPinExemptEndMinute,
                ).normalized() ?: return null
            }.distinct()
            val policy = policies.singleOrNull() ?: return null
            return policy.takeIf { it.hasExemption }
        }
    }
}