package com.zmr.rent.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A person renting a vehicle from ZMR.
 *
 * Enum columns ([rentDay], [status], [holdReason]) are stored by Room as their names
 * ("MONDAY", "ON_HOLD"), which keeps the database and backup files readable.
 */
@Entity(tableName = "renters")
data class Renter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** 10-digit Indian mobile number without the +91 country code. */
    val phone: String,
    /** The day the rental began, picked on a calendar. Stored as an epoch day number. */
    @ColumnInfo(defaultValue = "0")
    val startDate: LocalDate,
    /** The agreed day the vehicle comes back, or null for an open-ended rental. */
    val endDate: LocalDate? = null,
    /** The day of the week on which this renter's weekly rent is due: the weekday of [startDate]. */
    val rentDay: DayOfWeek,
    val weeklyRentPaise: Long = DEFAULT_WEEKLY_RENT_PAISE,
    val status: RenterStatus = RenterStatus.ACTIVE,
    /** Set only while [status] is [RenterStatus.ON_HOLD]. */
    val holdReason: HoldReason? = null,
    /** Name of the app user who added the renter. */
    val createdBy: String,
    /** When the renter was added, in milliseconds since the Unix epoch (UTC). */
    val createdAt: Long,
    @ColumnInfo(defaultValue = "RENTER") val customerType: CustomerType = CustomerType.RENTER,
    /** For a lessee: the price to buy the vehicle at the end of the lease. */
    val residualValuePaise: Long? = null,
    /** Money owed besides weekly rent (the sheet's "Pending Paisa"). */
    @ColumnInfo(defaultValue = "0") val pendingPaise: Long = 0,
    /** Unpaid traffic challans (the sheet's "Pending Challan"). */
    @ColumnInfo(defaultValue = "0") val pendingChallans: Int = 0,
) {
    companion object {
        /** Rs 1,400. Moves to the founder-editable settings table with Feature 3. */
        const val DEFAULT_WEEKLY_RENT_PAISE = 140_000L
    }
}
