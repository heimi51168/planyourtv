package app.orchard.tv.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhonePagePortTest {
    @Test
    fun withoutSavedPortTriesPreferredThenFallbacksThenAnyFreePort() {
        assertEquals(
            listOf(8790, 8791, 8792, 8793, 8794, 8795, 8796, 8797, 8798, 8799, 0),
            PhonePagePort.candidates(null),
        )
    }

    @Test
    fun savedPortComesFirstAndIsNotTriedTwice() {
        val ports = PhonePagePort.candidates(8791)
        assertEquals(8791, ports.first())
        assertEquals(ports.size, ports.distinct().size)
        assertTrue(8790 in ports)
        assertEquals(0, ports.last())
    }

    @Test
    fun savedPortOutsideTheAppRangeIsIgnored() {
        val default = PhonePagePort.candidates(null)
        for (saved in listOf(-1, 0, 80, 65536)) {
            assertEquals("saved=$saved", default, PhonePagePort.candidates(saved))
        }
    }

    @Test
    fun everyCandidateIsBindableOrAsksTheSystemForAFreePort() {
        assertTrue(PhonePagePort.candidates(50000).all { it == 0 || it in 1024..65535 })
    }
}
