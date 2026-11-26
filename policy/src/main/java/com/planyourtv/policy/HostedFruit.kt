package com.planyourtv.policy

import org.json.JSONObject

/**
 * The cloud fruit account projected for the hosted rule guard.
 *
 * It is a read-only cache of the orchard's device context (`memberEnabled` / `deviceEnabled` /
 * `canUnlock` / exchange rate / balance / this-time max / daily remaining). The execution layer
 * reads it like any other HOSTED cache: the LOCAL ledger is never touched, and the cloud member
 * id scopes the document so one child's balance cannot appear on another child's lock screen.
 *
 * [routeEnabled] folds the remote verdict: when it is false the TV hides the fruit route instead
 * of offering an exchange the backend would reject. [maxFruits] and [dailyRemaining] are the
 * backend's own ceilings; the planner must not shrink them with unrelated LOCAL counters.
 */
data class HostedFruitFacts(
    val memberId: Long = 0L,
    val routeEnabled: Boolean = false,
    val exchangeRateMinutesPerFruit: Int = 0,
    val availableFruitHundredths: Long = 0L,
    val maxFruits: Int = 0,
    /** -1 = unlimited; otherwise successful exchanges still available today. */
    val dailyRemaining: Int = -1,
    val fetchedAtEpochMs: Long = 0L,
) {
    fun encode(): JSONObject = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("memberId", memberId)
        .put("routeEnabled", routeEnabled)
        .put("exchangeRateMinutesPerFruit", exchangeRateMinutesPerFruit)
        .put("availableFruitHundredths", availableFruitHundredths)
        .put("maxFruits", maxFruits)
        .put("dailyRemaining", dailyRemaining)
        .put("fetchedAtEpochMs", fetchedAtEpochMs)

    companion object {
        const val SCHEMA_VERSION = 1
        private const val MAX_FRUIT_HUNDREDTHS = 1_000_000L
        private const val MAX_WHOLE_FRUITS = 10_000

        fun decode(raw: String?): HostedFruitFacts? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val json = JSONObject(raw)
                if (json.optInt("schemaVersion", -1) != SCHEMA_VERSION) return@runCatching null
                val memberId = json.optLong("memberId", 0L)
                val rate = json.optInt("exchangeRateMinutesPerFruit", 0)
                val available = json.optLong("availableFruitHundredths", -1L)
                val maxFruits = json.optInt("maxFruits", -1)
                val dailyRemaining = json.optInt("dailyRemaining", -2)
                val fetchedAt = json.optLong("fetchedAtEpochMs", 0L)
                val routeEnabled = json.optBoolean("routeEnabled", false)
                if (memberId <= 0L ||
                    rate !in 0..FruitFacts.MAX_RATE ||
                    available !in 0L..MAX_FRUIT_HUNDREDTHS ||
                    maxFruits !in 0..MAX_WHOLE_FRUITS ||
                    dailyRemaining !in -1..MAX_WHOLE_FRUITS ||
                    fetchedAt <= 0L
                ) {
                    return@runCatching null
                }
                if (routeEnabled && (rate !in FruitFacts.MIN_RATE..FruitFacts.MAX_RATE ||
                        maxFruits < 1)
                ) {
                    return@runCatching null
                }
                HostedFruitFacts(
                    memberId = memberId,
                    routeEnabled = routeEnabled,
                    exchangeRateMinutesPerFruit = rate,
                    availableFruitHundredths = available,
                    maxFruits = maxFruits,
                    dailyRemaining = dailyRemaining,
                    fetchedAtEpochMs = fetchedAt,
                )
            }.getOrNull()
        }
    }
}