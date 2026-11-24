package com.zmr.rent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndianPhoneTest {

    @Test
    fun `normalize strips spacing, country code and trunk zero`() {
        assertEquals("9876543210", IndianPhone.normalize("9876543210"))
        assertEquals("9876543210", IndianPhone.normalize("98765 43210"))
        assertEquals("9876543210", IndianPhone.normalize("+91 98765-43210"))
        assertEquals("9876543210", IndianPhone.normalize("919876543210"))
        assertEquals("9876543210", IndianPhone.normalize("09876543210"))
    }

    @Test
    fun `normalize keeps a number that itself starts with 91`() {
        assertEquals("9198765432", IndianPhone.normalize("9198765432"))
    }

    @Test
    fun `normalize leaves partly typed numbers alone`() {
        assertEquals("", IndianPhone.normalize(""))
        assertEquals("98765", IndianPhone.normalize("98765"))
    }

    @Test
    fun `valid numbers have 10 digits and start with 6 to 9`() {
        assertTrue(IndianPhone.isValid("9876543210"))
        assertTrue(IndianPhone.isValid("6000000000"))
        assertFalse(IndianPhone.isValid("5876543210"))
        assertFalse(IndianPhone.isValid("987654321"))
        assertFalse(IndianPhone.isValid("98765432100"))
        assertFalse(IndianPhone.isValid(""))
    }

    @Test
    fun `format adds the country code`() {
        assertEquals("+91 98765 43210", IndianPhone.format("9876543210"))
        assertEquals("98765", IndianPhone.format("98765"))
    }
}
