package com.zmr.rent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadersTest {

    @Test
    fun `the built-in list is read line by line, skipping notes and broken lines`() {
        val made = Leader.create(Contact("Test", "7", "9000000000", ""), "4321")
        val text = """
            # employee ID | name | mobile | email | PIN salt | PIN hash
            7 | Test Person | +91 90000 00000 |  | ${made.pinSalt} | ${made.pinHash}
            not | enough
        """.trimIndent()

        val leader = Leaders.parse(text).single()

        assertEquals(Contact("Test Person", "7", "9000000000", ""), leader.contact)
        assertTrue(leader.pinMatches("4321"))
        assertFalse(leader.pinMatches("1234"))
    }
}
