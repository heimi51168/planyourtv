package com.planyourtv.policy

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostedFruitFactsTest {
    private val now = 1_760_000_000_000L

    private fun facts(
        memberId: Long = 42L,
        route: Boolean = true,
        rate: Int = 2,
        available: Long = 1234L,
        maxFruits: Int = 5,
        dailyRemaining: Int = 3,
    ) = HostedFruitFacts(
        memberId = memberId,
        routeEnabled = route,
        exchangeRateMinutesPerFruit = rate,
        availableFruitHundredths = available,
        maxFruits = maxFruits,
        dailyRemaining = dailyRemaining,
        fetchedAtEpochMs = now,
    )

    /** The cached document is written by the TV and read by the plane planner after a restart. */
    @Test
    fun theDocumentRoundTripsThroughJson() {
        val original = facts()
        val decoded = HostedFruitFacts.decode(original.encode().toString())

        assertEquals(original, decoded)
    }

    @Test
    fun unlimitedAndZeroDailyRemainingAreDistinct() {
        assertEquals(
            -1,
            HostedFruitFacts.decode(facts(dailyRemaining = -1).encode().toString())
                ?.dailyRemaining,
        )
        assertEquals(
            0,
            HostedFruitFacts.decode(facts(dailyRemaining = 0).encode().toString())
                ?.dailyRemaining,
        )
    }

    /** The orchard disabled the route: the cache is still valid, it just hides the card. */
    @Test
    fun aDisabledRouteMayCarryZeroRateAndZeroCeiling() {
        val decoded = HostedFruitFacts.decode(
            facts(route = false, rate = 0, maxFruits = 0, available = 0L).encode().toString(),
        )

        assertNotNull(decoded)
        assertFalse(decoded!!.routeEnabled)
    }

    @Test
    fun anEnabledRouteMustHaveAUsableRateAndAtLeastOneWholeFruit() {
        assertNull(
            HostedFruitFacts.decode(facts(rate = 0).encode().toString()),
        )
        assertNull(
            HostedFruitFacts.decode(facts(rate = 61).encode().toString()),
        )
        assertNull(
            HostedFruitFacts.decode(facts(maxFruits = 0).encode().toString()),
        )
    }

    @Test
    fun memberBalanceAndFetchTimeAreMandatory() {
        assertNull(HostedFruitFacts.decode(facts(memberId = 0L).encode().toString()))
        assertNull(HostedFruitFacts.decode(facts(available = -1L).encode().toString()))
        assertNull(HostedFruitFacts.decode(facts(dailyRemaining = -2).encode().toString()))

        val noTime = JSONObject(facts().encode().toString()).put("fetchedAtEpochMs", 0L)
        assertNull(HostedFruitFacts.decode(noTime.toString()))
    }

    @Test
    fun anotherSchemaAndGarbageAreRejectedInsteadOfGuessed() {
        val otherSchema = JSONObject(facts().encode().toString()).put("schemaVersion", 2)
        assertNull(HostedFruitFacts.decode(otherSchema.toString()))
        assertNull(HostedFruitFacts.decode("not json"))
        assertNull(HostedFruitFacts.decode(""))
        assertNull(HostedFruitFacts.decode(null))
    }

    @Test
    fun unknownFieldsFromANewerBackendAreIgnored() {
        val json = JSONObject(facts().encode().toString())
            .put("blockers", "ignored")
            .put("serverTime", 123L)
        val decoded = HostedFruitFacts.decode(json.toString())

        assertNotNull(decoded)
        assertTrue(decoded!!.routeEnabled)
        assertEquals(1234L, decoded.availableFruitHundredths)
    }
}