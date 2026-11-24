package com.zmr.rent.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** What a payment was for. Stored in the database by name; do not rename entries. */
enum class PaymentType {
    RENT,
    DEPOSIT,
    ADVANCE,
}

/** How the money reached ZMR. Stored in the database by name; do not rename entries. */
enum class PaymentMethod {
    /** The renter scanned ZMR's UPI QR code (Google Pay, PhonePe, Paytm, BHIM...). */
    UPI,

    /** The renter sent money to ZMR's bank account (NEFT, IMPS or RTGS). */
    BANK_TRANSFER,
    CASH,
}

/**
 * One amount of money received. The payments table is an append-only ledger: rows are never
 * edited or deleted (database triggers refuse it). A mistake is fixed by adding a correcting
 * row that points at the original through [correctsPaymentId].
 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(entity = Renter::class, parentColumns = ["id"], childColumns = ["renterId"]),
        ForeignKey(entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"]),
    ],
    indices = [Index(value = ["renterId"]), Index(value = ["vehicleId"])],
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val renterId: Long,
    val vehicleId: Long,
    val type: PaymentType,
    val amountPaise: Long,
    val method: PaymentMethod,
    /** UPI transaction ID / bank UTR for UPI and bank transfers, receipt number for cash. */
    val reference: String,
    /** When the money was received, in milliseconds since the Unix epoch (UTC). */
    val receivedAt: Long,
    /** Name of the app user who recorded the payment. */
    val enteredBy: String,
    val enteredAt: Long,
    val correctsPaymentId: Long? = null,
    val correctionReason: String? = null,
)
