package com.zmr.rent.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Where a renter stands today. Uses the same terms as ZMR's rental sheet. */
data class RentStatus(
    /** The last date the rent paid so far was expected (the sheet's "Expected Date"). */
    val expectedDate: LocalDate,
    /** The first week not yet paid for (the sheet's "Rental Due Date"). */
    val dueDate: LocalDate,
    val delayDays: Long,
    val pastDuePaise: Long,
)

object RentLedger {

    /**
     * Past due = full weeks late x weekly rent, less any part-payment toward the next week, plus
     * the separately pending amount. Rent stops building up after the agreed end date.
     *
     * @param rentCreditPaise everything paid toward rent: the advance and rent payments.
     */
    fun status(
        startDate: LocalDate,
        endDate: LocalDate?,
        weeklyRentPaise: Long,
        rentCreditPaise: Long,
        pendingPaise: Long,
        today: LocalDate,
    ): RentStatus {
        val weeksPaid = rentCreditPaise / weeklyRentPaise
        val partPaid = rentCreditPaise % weeklyRentPaise
        val dueDate = startDate.plusWeeks(weeksPaid)
        val countUntil = if (endDate != null && endDate < today) endDate else today
        val delayDays = ChronoUnit.DAYS.between(dueDate, countUntil).coerceAtLeast(0)
        val owedForWeeks = (delayDays / 7 * weeklyRentPaise - partPaid).coerceAtLeast(0)
        return RentStatus(
            expectedDate = maxOf(startDate, dueDate.minusWeeks(1)),
            dueDate = dueDate,
            delayDays = delayDays,
            pastDuePaise = owedForWeeks + pendingPaise,
        )
    }
}
