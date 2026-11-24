package com.zmr.rent.domain

import com.zmr.rent.data.entity.Setting

/**
 * Where renters send money, and how much a new rental pays up front. Set by the founder on the
 * Payment details screen. Empty text means "not set up yet".
 */
data class PaymentSettings(
    /** ZMR's UPI ID (VPA), for example "zmrmobility@okaxis". */
    val upiId: String = "",
    /** Name shown in the renter's UPI app when paying. */
    val payeeName: String = "",
    val bankAccountName: String = "",
    val bankAccountNumber: String = "",
    val bankIfsc: String = "",
    val bankName: String = "",
    val depositPaise: Long = DEFAULT_DEPOSIT_PAISE,
    val advancePaise: Long = DEFAULT_ADVANCE_PAISE,
) {
    val hasUpi: Boolean get() = upiId.isNotEmpty()
    val hasBankAccount: Boolean get() = bankAccountNumber.isNotEmpty()
    val initialTotalPaise: Long get() = depositPaise + advancePaise

    fun toSettings(): List<Setting> = listOf(
        Setting(KEY_UPI_ID, upiId),
        Setting(KEY_PAYEE_NAME, payeeName),
        Setting(KEY_BANK_ACCOUNT_NAME, bankAccountName),
        Setting(KEY_BANK_ACCOUNT_NUMBER, bankAccountNumber),
        Setting(KEY_BANK_IFSC, bankIfsc),
        Setting(KEY_BANK_NAME, bankName),
        Setting(KEY_DEPOSIT_PAISE, depositPaise.toString()),
        Setting(KEY_ADVANCE_PAISE, advancePaise.toString()),
    )

    companion object {
        // ZMR's verified Paytm merchant account, from its official QR code.
        const val ZMR_UPI_ID = "zmrmobility@ptaxis"
        const val ZMR_PAYEE_NAME = "ZMR MOBILITY PRIVATE LIMITED"

        /** Rs 2,000. */
        const val DEFAULT_DEPOSIT_PAISE = 200_000L

        /** Rs 1,400. */
        const val DEFAULT_ADVANCE_PAISE = 140_000L

        private const val KEY_UPI_ID = "payment.upiId"
        private const val KEY_PAYEE_NAME = "payment.payeeName"
        private const val KEY_BANK_ACCOUNT_NAME = "payment.bankAccountName"
        private const val KEY_BANK_ACCOUNT_NUMBER = "payment.bankAccountNumber"
        private const val KEY_BANK_IFSC = "payment.bankIfsc"
        private const val KEY_BANK_NAME = "payment.bankName"
        private const val KEY_DEPOSIT_PAISE = "amount.depositPaise"
        private const val KEY_ADVANCE_PAISE = "amount.advancePaise"

        /** Builds settings from stored rows; anything missing keeps its default, including ZMR's UPI account. */
        fun from(settings: List<Setting>): PaymentSettings {
            val values = settings.associate { it.key to it.value }
            val defaults = PaymentSettings()
            return PaymentSettings(
                upiId = values[KEY_UPI_ID].orEmpty().ifEmpty { ZMR_UPI_ID },
                payeeName = values[KEY_PAYEE_NAME].orEmpty().ifEmpty { ZMR_PAYEE_NAME },
                bankAccountName = values[KEY_BANK_ACCOUNT_NAME] ?: defaults.bankAccountName,
                bankAccountNumber = values[KEY_BANK_ACCOUNT_NUMBER] ?: defaults.bankAccountNumber,
                bankIfsc = values[KEY_BANK_IFSC] ?: defaults.bankIfsc,
                bankName = values[KEY_BANK_NAME] ?: defaults.bankName,
                depositPaise = values[KEY_DEPOSIT_PAISE]?.toLongOrNull() ?: defaults.depositPaise,
                advancePaise = values[KEY_ADVANCE_PAISE]?.toLongOrNull() ?: defaults.advancePaise,
            )
        }
    }
}

enum class PaymentSettingsError {
    UPI_ID_INVALID,
    PAYEE_NAME_REQUIRED,
    ACCOUNT_NAME_REQUIRED,
    ACCOUNT_NUMBER_INVALID,
    IFSC_INVALID,
    DEPOSIT_INVALID,
    ADVANCE_INVALID,
}

object PaymentSettingsValidator {

    /** Trims and canonicalises typed values. Run before [validate] and before saving. */
    fun clean(settings: PaymentSettings): PaymentSettings = settings.copy(
        upiId = settings.upiId.trim().lowercase(),
        payeeName = settings.payeeName.trim().replace(WHITESPACE, " "),
        bankAccountName = settings.bankAccountName.trim().replace(WHITESPACE, " "),
        bankAccountNumber = settings.bankAccountNumber.filter { it in '0'..'9' },
        bankIfsc = settings.bankIfsc.trim().uppercase().filter { it in 'A'..'Z' || it in '0'..'9' },
        bankName = settings.bankName.trim().replace(WHITESPACE, " "),
    )

    /**
     * UPI and bank details are each optional, but whichever is filled in must be complete,
     * because a renter will be sending real money to it.
     */
    fun validate(settings: PaymentSettings): Set<PaymentSettingsError> = buildSet {
        if (settings.upiId.isNotEmpty() || settings.payeeName.isNotEmpty()) {
            if (!Upi.isValidId(settings.upiId)) add(PaymentSettingsError.UPI_ID_INVALID)
            if (settings.payeeName.isEmpty()) add(PaymentSettingsError.PAYEE_NAME_REQUIRED)
        }
        val anyBankField = settings.bankAccountName.isNotEmpty() || settings.bankAccountNumber.isNotEmpty() ||
            settings.bankIfsc.isNotEmpty() || settings.bankName.isNotEmpty()
        if (anyBankField) {
            if (settings.bankAccountName.isEmpty()) add(PaymentSettingsError.ACCOUNT_NAME_REQUIRED)
            if (!BankAccount.isValidNumber(settings.bankAccountNumber)) add(PaymentSettingsError.ACCOUNT_NUMBER_INVALID)
            if (!BankAccount.isValidIfsc(settings.bankIfsc)) add(PaymentSettingsError.IFSC_INVALID)
        }
        if (settings.depositPaise !in 0..Money.MAX_AMOUNT_PAISE) add(PaymentSettingsError.DEPOSIT_INVALID)
        if (settings.advancePaise !in 0..Money.MAX_AMOUNT_PAISE) add(PaymentSettingsError.ADVANCE_INVALID)
    }

    private val WHITESPACE = Regex("\\s+")
}

/** Indian bank account details. */
object BankAccount {
    /** Indian bank account numbers are 9 to 18 digits long. */
    fun isValidNumber(number: String): Boolean = number.length in 9..18 && number.all { it in '0'..'9' }

    /** IFSC: 4 letters (the bank), a zero, then 6 letters or digits (the branch). */
    fun isValidIfsc(ifsc: String): Boolean = IFSC.matches(ifsc)

    private val IFSC = Regex("[A-Z]{4}0[A-Z0-9]{6}")
}
