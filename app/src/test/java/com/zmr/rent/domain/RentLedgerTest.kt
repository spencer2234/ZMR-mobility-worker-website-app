package com.zmr.rent.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RentLedgerTest {

    private val start = LocalDate.of(2026, 10, 5)
    private val weekly = 140_000L

    private fun status(credit: Long, today: LocalDate, pending: Long = 0, end: LocalDate? = null) =
        RentLedger.status(start, end, weekly, credit, pending, today)

    @Test
    fun `the due date follows the weeks paid, and expected is one week before it`() {
        val rent = status(credit = weekly * 2, today = start)
        assertEquals(start.plusWeeks(2), rent.dueDate)
        assertEquals(start.plusWeeks(1), rent.expectedDate)
        assertEquals(0L, rent.pastDuePaise)
    }

    @Test
    fun `only full weeks late are charged`() {
        assertEquals(0L, status(weekly, start.plusDays(13)).pastDuePaise)
        val rent = status(weekly, start.plusDays(21))
        assertEquals(14L, rent.delayDays)
        assertEquals(weekly * 2, rent.pastDuePaise)
    }

    @Test
    fun `a part payment and the pending amount are counted`() {
        val rent = status(credit = weekly + 40_000, today = start.plusDays(21), pending = 40_000)
        assertEquals(weekly * 2 - 40_000 + 40_000, rent.pastDuePaise)
    }

    @Test
    fun `rent stops building up after the end date`() {
        val rent = status(credit = 0, today = start.plusWeeks(10), end = start.plusWeeks(2))
        assertEquals(14L, rent.delayDays)
        assertEquals(weekly * 2, rent.pastDuePaise)
    }
}
