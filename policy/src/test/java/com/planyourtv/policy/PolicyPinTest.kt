package com.planyourtv.policy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyPinTest {
    private val iterations = 1_000

    @Test
    fun acceptsTheSamePinAndRejectsAnother() {
        val salt = PolicyPin.newSalt()
        val hash = PolicyPin.hash("123456", salt, iterations)

        assertTrue(PolicyPin.verify("123456", salt, hash, iterations))
        assertFalse(PolicyPin.verify("123457", salt, hash, iterations))
    }

    @Test
    fun saltsMakeTheSamePinDifferent() {
        val a = PolicyPin.newSalt()
        val b = PolicyPin.newSalt()
        assertNotEquals(a, b)
        assertNotEquals(
            PolicyPin.hash("123456", a, iterations),
            PolicyPin.hash("123456", b, iterations),
        )
    }

    @Test
    fun emptyStoredPinNeverMatches() {
        assertFalse(PolicyPin.verify("123456", "", "", iterations))
    }
}
