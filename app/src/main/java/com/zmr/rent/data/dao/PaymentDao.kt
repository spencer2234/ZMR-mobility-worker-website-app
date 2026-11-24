package com.zmr.rent.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.zmr.rent.data.entity.Payment
import kotlinx.coroutines.flow.Flow

/**
 * The payment ledger. Insert and read only: there is deliberately no update or delete, and
 * the database triggers in Triggers.kt refuse them even if someone adds one later.
 */
@Dao
interface PaymentDao {

    @Insert
    suspend fun insertAll(payments: List<Payment>): List<Long>

    @Query("SELECT * FROM payments WHERE renterId = :renterId ORDER BY receivedAt, id")
    fun observeForRenter(renterId: Long): Flow<List<Payment>>

    /** Every payment with the renter's name and vehicle number, oldest first, for reports. */
    @Query(
        """SELECT p.*, r.name AS renterName, v.vehicleNumber AS vehicleNumber
           FROM payments p
           INNER JOIN renters r ON r.id = p.renterId
           INNER JOIN vehicles v ON v.id = p.vehicleId
           ORDER BY p.receivedAt, p.id""",
    )
    suspend fun getAllWithRenter(): List<PaymentWithRenter>

    /** Each renter's total paid toward rent (advance and rent payments). */
    @Query("SELECT renterId, SUM(amountPaise) AS paise FROM payments WHERE type IN ('RENT', 'ADVANCE') GROUP BY renterId")
    fun observeRentCredits(): Flow<List<RentCredit>>

    @Query("SELECT COUNT(*) FROM payments WHERE renterId = :renterId AND type IN ('DEPOSIT', 'ADVANCE')")
    suspend fun countInitialPayments(renterId: Long): Int

    /**
     * Saves the deposit and advance together, unless they were already recorded for this
     * renter (for example from a double tap). Returns false when nothing was saved.
     */
    @Transaction
    suspend fun insertInitialPaymentsOnce(renterId: Long, payments: List<Payment>): Boolean {
        if (countInitialPayments(renterId) > 0) return false
        insertAll(payments)
        return true
    }
}

data class RentCredit(val renterId: Long, val paise: Long)

/** A payment together with who paid it, for reports. */
data class PaymentWithRenter(
    @Embedded val payment: Payment,
    val renterName: String,
    val vehicleNumber: String,
)
