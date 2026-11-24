package com.zmr.rent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `formats with the rupee sign and Indian digit grouping`() {
        assertEquals("₹0", Money.format(0))
        assertEquals("₹999", Money.format(99_900))
        assertEquals("₹1,400", Money.format(140_000))
        assertEquals("₹2,000", Money.format(200_000))
        assertEquals("₹1,00,000", Money.format(10_000_000))
        assertEquals("₹12,34,567", Money.format(123_456_700))
        assertEquals("₹1,23,45,678", Money.format(1_234_567_800))
    }

    @Test
    fun `shows paise only when there are some`() {
        assertEquals("₹1,400.50", Money.format(140_050))
        assertEquals("₹0.05", Money.format(5))
        assertEquals("-₹400", Money.format(-40_000))
    }

    @Test
    fun `parses typed rupees into paise`() {
        assertEquals(140_000L, Money.parseRupees("1400"))
        assertEquals(140_000L, Money.parseRupees(" 1,400 "))
        assertEquals(140_000L, Money.parseRupees("₹1,400"))
        assertEquals(140_050L, Money.parseRupees("1400.5"))
        assertEquals(140_005L, Money.parseRupees("1400.05"))
        assertEquals(0L, Money.parseRupees("0"))
    }

    @Test
    fun `rejects text that is not an amount`() {
        assertNull(Money.parseRupees(""))
        assertNull(Money.parseRupees("abc"))
        assertNull(Money.parseRupees("-1400"))
        assertNull(Money.parseRupees("14.005"))
        assertNull(Money.parseRupees("1400."))
        assertNull(Money.parseRupees("9999999999999"))
    }

    @Test
    fun `input text round-trips through parse`() {
        assertEquals("1400", Money.toInputText(140_000))
        assertEquals("1400.50", Money.toInputText(140_050))
        assertEquals(140_050L, Money.parseRupees(Money.toInputText(140_050)))
    }
}
