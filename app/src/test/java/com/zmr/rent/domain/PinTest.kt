package com.zmr.rent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinTest {

    @Test
    fun `a PIN is exactly four digits`() {
        assertTrue(Pin.isValid("0000"))
        assertTrue(Pin.isValid("4821"))
        assertFalse(Pin.isValid("482"))
        assertFalse(Pin.isValid("48210"))
        assertFalse(Pin.isValid("48a1"))
        assertFalse(Pin.isValid(""))
    }

    @Test
    fun `the right PIN matches and a wrong one does not`() {
        val salt = Pin.newSalt()
        val hash = Pin.hash("4821", salt)

        assertTrue(Pin.matches("4821", salt, hash))
        assertFalse(Pin.matches("4822", salt, hash))
    }

    @Test
    fun `the same PIN with different salts gives different hashes`() {
        assertNotEquals(Pin.hash("4821", Pin.newSalt()).toHex(), Pin.hash("4821", Pin.newSalt()).toHex())
    }

    @Test
    fun `hex round-trips`() {
        val bytes = byteArrayOf(0, 1, 127, -128, -1)
        assertEquals("00017f80ff", bytes.toHex())
        assertEquals(bytes.toList(), "00017f80ff".hexToBytes().toList())
    }
}
