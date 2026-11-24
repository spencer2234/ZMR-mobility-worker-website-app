package com.zmr.rent.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentSettingsTest {

    private fun errorsFor(settings: PaymentSettings) =
        PaymentSettingsValidator.validate(PaymentSettingsValidator.clean(settings))

    private val complete = PaymentSettings(
        upiId = "zmrmobility@okaxis",
        payeeName = "ZMR Mobility",
        bankAccountName = "ZMR Mobility Pvt Ltd",
        bankAccountNumber = "123456789012",
        bankIfsc = "SBIN0001234",
        bankName = "State Bank of India",
    )

    @Test
    fun `nothing filled in is allowed, so the app works before setup`() {
        assertEquals(emptySet<PaymentSettingsError>(), errorsFor(PaymentSettings()))
    }

    @Test
    fun `complete UPI and bank details are accepted`() {
        assertEquals(emptySet<PaymentSettingsError>(), errorsFor(complete))
    }

    @Test
    fun `clean tidies typed values`() {
        val clean = PaymentSettingsValidator.clean(
            complete.copy(upiId = " ZMRMobility@OKAXIS ", bankAccountNumber = "1234 5678 9012", bankIfsc = "sbin 0001234"),
        )
        assertEquals("zmrmobility@okaxis", clean.upiId)
        assertEquals("123456789012", clean.bankAccountNumber)
        assertEquals("SBIN0001234", clean.bankIfsc)
    }

    @Test
    fun `half-filled UPI details are reported`() {
        assertEquals(
            setOf(PaymentSettingsError.PAYEE_NAME_REQUIRED),
            errorsFor(PaymentSettings(upiId = "zmr@okaxis")),
        )
        assertEquals(
            setOf(PaymentSettingsError.UPI_ID_INVALID),
            errorsFor(PaymentSettings(upiId = "zmr", payeeName = "ZMR")),
        )
    }

    @Test
    fun `half-filled bank details are reported`() {
        assertEquals(
            setOf(
                PaymentSettingsError.ACCOUNT_NAME_REQUIRED,
                PaymentSettingsError.ACCOUNT_NUMBER_INVALID,
                PaymentSettingsError.IFSC_INVALID,
            ),
            errorsFor(PaymentSettings(bankName = "SBI")),
        )
        assertEquals(
            setOf(PaymentSettingsError.IFSC_INVALID),
            errorsFor(complete.copy(bankIfsc = "SBIN1001234")),
        )
    }

    @Test
    fun `bank account numbers are 9 to 18 digits and IFSC codes 11 characters`() {
        assertTrue(BankAccount.isValidNumber("123456789"))
        assertTrue(BankAccount.isValidNumber("123456789012345678"))
        assertFalse(BankAccount.isValidNumber("12345678"))
        assertFalse(BankAccount.isValidNumber("1234567890123456789"))
        assertTrue(BankAccount.isValidIfsc("HDFC0000123"))
        assertTrue(BankAccount.isValidIfsc("SBIN000ABCD"))
        assertFalse(BankAccount.isValidIfsc("HDFC000012"))
    }

    @Test
    fun `missing amounts are reported`() {
        assertEquals(
            setOf(PaymentSettingsError.DEPOSIT_INVALID, PaymentSettingsError.ADVANCE_INVALID),
            errorsFor(PaymentSettings(depositPaise = -1, advancePaise = -1)),
        )
    }

    @Test
    fun `settings survive being stored and read back, and missing ones use defaults`() {
        assertEquals(complete, PaymentSettings.from(complete.toSettings()))
        val defaults = PaymentSettings.from(emptyList())
        assertEquals(200_000L, defaults.depositPaise)
        assertEquals(140_000L, defaults.advancePaise)
        assertEquals(340_000L, defaults.initialTotalPaise)
        assertEquals(PaymentSettings.ZMR_UPI_ID, defaults.upiId)
        assertEquals(PaymentSettings.ZMR_PAYEE_NAME, defaults.payeeName)
    }
}
