package com.zmr.rent.data.repository

import com.zmr.rent.data.dao.PaymentDao
import com.zmr.rent.data.dao.RenterDao
import com.zmr.rent.data.dao.SettingsDao
import com.zmr.rent.data.entity.Payment
import com.zmr.rent.data.entity.PaymentMethod
import com.zmr.rent.data.entity.PaymentType
import com.zmr.rent.data.session.Session
import com.zmr.rent.domain.Money
import com.zmr.rent.domain.PaymentSettings
import com.zmr.rent.domain.PaymentSettingsError
import com.zmr.rent.domain.PaymentSettingsValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

enum class PaymentError {
    METHOD_REQUIRED,
    REFERENCE_REQUIRED,
    AMOUNT_INVALID,
}

sealed interface RecordPaymentResult {
    data object Saved : RecordPaymentResult
    data class Invalid(val errors: Set<PaymentError>) : RecordPaymentResult

    /** The deposit and advance for this renter were saved before; nothing new was added. */
    data object AlreadyRecorded : RecordPaymentResult
    data object RenterNotFound : RecordPaymentResult
}

sealed interface SaveSettingsResult {
    data object Saved : SaveSettingsResult
    data class Invalid(val errors: Set<PaymentSettingsError>) : SaveSettingsResult
}

/** Recording money received, and the founder's payment details. */
class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val renterDao: RenterDao,
    private val settingsDao: SettingsDao,
    private val session: Session,
    private val clock: Clock,
) {
    fun observeSettings(): Flow<PaymentSettings> = settingsDao.observeAll().map(PaymentSettings::from)

    suspend fun saveSettings(settings: PaymentSettings): SaveSettingsResult {
        val clean = PaymentSettingsValidator.clean(settings)
        val errors = PaymentSettingsValidator.validate(clean)
        if (errors.isNotEmpty()) return SaveSettingsResult.Invalid(errors)
        settingsDao.upsertAll(clean.toSettings())
        return SaveSettingsResult.Saved
    }

    /** Renter id to the total paid toward rent. */
    fun observeRentCredits(): Flow<Map<Long, Long>> =
        paymentDao.observeRentCredits().map { credits -> credits.associate { it.renterId to it.paise } }

    fun observePayments(renterId: Long): Flow<List<Payment>> = paymentDao.observeForRenter(renterId)

    /**
     * Records the money a new renter pays up front: the deposit and the rent advance, as two
     * separate payment rows (never added together into one), both with the same method and
     * reference. A payment cannot be saved without a method and a reference.
     */
    suspend fun recordInitialPayment(
        renterId: Long,
        method: PaymentMethod?,
        reference: String,
        depositPaise: Long,
        advancePaise: Long,
    ): RecordPaymentResult {
        val cleanReference = cleanReference(reference)
        val errors = buildSet {
            if (method == null) add(PaymentError.METHOD_REQUIRED)
            if (cleanReference.isEmpty()) add(PaymentError.REFERENCE_REQUIRED)
            if (depositPaise !in 0..Money.MAX_AMOUNT_PAISE || advancePaise !in 0..Money.MAX_AMOUNT_PAISE ||
                depositPaise + advancePaise <= 0
            ) {
                add(PaymentError.AMOUNT_INVALID)
            }
        }
        if (errors.isNotEmpty() || method == null) return RecordPaymentResult.Invalid(errors)

        val renter = renterDao.findById(renterId) ?: return RecordPaymentResult.RenterNotFound
        val now = clock.millis()
        fun payment(type: PaymentType, amountPaise: Long) = Payment(
            renterId = renterId,
            vehicleId = renter.vehicle.id,
            type = type,
            amountPaise = amountPaise,
            method = method,
            reference = cleanReference,
            receivedAt = now,
            enteredBy = session.userName,
            enteredAt = now,
        )
        val payments = listOfNotNull(
            payment(PaymentType.DEPOSIT, depositPaise).takeIf { depositPaise > 0 },
            payment(PaymentType.ADVANCE, advancePaise).takeIf { advancePaise > 0 },
        )
        val saved = paymentDao.insertInitialPaymentsOnce(renterId, payments)
        return if (saved) RecordPaymentResult.Saved else RecordPaymentResult.AlreadyRecorded
    }

    /** Records one weekly-rent payment. Method and reference are required, as for every payment. */
    suspend fun recordRent(renterId: Long, method: PaymentMethod?, reference: String, amountPaise: Long): RecordPaymentResult {
        val cleanReference = cleanReference(reference)
        val errors = buildSet {
            if (method == null) add(PaymentError.METHOD_REQUIRED)
            if (cleanReference.isEmpty()) add(PaymentError.REFERENCE_REQUIRED)
            if (amountPaise !in 1..Money.MAX_AMOUNT_PAISE) add(PaymentError.AMOUNT_INVALID)
        }
        if (errors.isNotEmpty() || method == null) return RecordPaymentResult.Invalid(errors)
        val renter = renterDao.findById(renterId) ?: return RecordPaymentResult.RenterNotFound
        val now = clock.millis()
        paymentDao.insertAll(
            listOf(
                Payment(
                    renterId = renterId,
                    vehicleId = renter.vehicle.id,
                    type = PaymentType.RENT,
                    amountPaise = amountPaise,
                    method = method,
                    reference = cleanReference,
                    receivedAt = now,
                    enteredBy = session.userName,
                    enteredAt = now,
                ),
            ),
        )
        return RecordPaymentResult.Saved
    }

    private fun cleanReference(reference: String): String =
        reference.trim().replace(WHITESPACE, " ").uppercase().take(MAX_REFERENCE_LENGTH)

    companion object {
        const val MAX_REFERENCE_LENGTH = 40
        private val WHITESPACE = Regex("\\s+")
    }
}
