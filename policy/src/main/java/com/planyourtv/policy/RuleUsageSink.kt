package com.planyourtv.policy

/**
 * Receives rule session lifecycle events. The local engine never waits for delivery: reporting
 * must not affect the countdown or the local day ledger.
 */
fun interface RuleUsageSink {
    fun onRuleUsage(event: RuleUsageEvent)
}
