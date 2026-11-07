package com.planyourtv.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPinTest {
    @Test
    fun localPinIsSixDigitsOnly() {
        assertTrue(LocalPin.isValid("123456"))
        assertFalse(LocalPin.isValid("12345"))
        assertFalse(LocalPin.isValid("12345a"))
        assertFalse(LocalPin.isValid("abcdef"))
    }

    @Test
    fun localPinHashAcceptsOnlyTheSamePin() {
        val salt = LocalPin.newSalt()
        val hash = LocalPin.hash("123456", salt)
        assertTrue(LocalPin.verify("123456", salt, hash))
        assertFalse(LocalPin.verify("654321", salt, hash))
    }

    @Test
    fun recoveryCodeIsFormattedAndNormalized() {
        val code = LocalRecovery.newCode()
        assertEquals(14, code.length) // 12 chars + two hyphens
        assertEquals(code, LocalRecovery.format(code))
        assertNotNull(LocalRecovery.normalize(code))
        assertNotNull(LocalRecovery.normalize(code.replace("-", "").lowercase()))
        assertNull(LocalRecovery.normalize("too-short"))
        assertNull(LocalRecovery.normalize("OOOO-OOOO-OOOO"))
    }

    @Test
    fun recoveryHashIsCaseAndSeparatorInsensitive() {
        val code = LocalRecovery.newCode()
        val salt = LocalRecovery.newSalt()
        val hash = LocalRecovery.hash(code, salt)
        assertNotNull(hash)
        assertTrue(LocalRecovery.verify(code.lowercase().replace("-", " "), salt, hash!!))
        assertFalse(LocalRecovery.verify("AAAA-AAAA-AAAA", salt, hash))
    }
}
