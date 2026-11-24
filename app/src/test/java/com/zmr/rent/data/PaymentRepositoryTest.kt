package com.zmr.rent.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zmr.rent.data.entity.PaymentMethod
import com.zmr.rent.data.entity.PaymentType
import com.zmr.rent.data.repository.PaymentError
import com.zmr.rent.data.repository.PaymentRepository
import com.zmr.rent.data.repository.RecordPaymentResult
import com.zmr.rent.data.repository.SaveSettingsResult
import com.zmr.rent.data.session.Session
import com.zmr.rent.domain.PaymentSettings
import com.zmr.rent.domain.PaymentSettingsError
import com.zmr.rent.renter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class PaymentRepositoryTest {

    private val now = Instant.parse("2026-10-18T05:00:00Z")
    private lateinit var database: AppDatabase
    private lateinit var payments: PaymentRepository
    private var renterId = 0L

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .withAppSetup()
            .allowMainThreadQueries()
            .build()
        payments = PaymentRepository(
            paymentDao = database.paymentDao(),
            renterDao = database.renterDao(),
            settingsDao = database.settingsDao(),
            session = object : Session {
                override val userName = "Imran"
            },
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
        renterId = database.renterDao().insertRenterWithVehicle(renter(), "DL1SAB1234")
    }

    @After
    fun closeDatabase() = database.close()

    private suspend fun recordUpi(reference: String = " 412345678901 ") =
        payments.recordInitialPayment(renterId, PaymentMethod.UPI, reference, 200_000, 140_000)

    @Test
    fun `the deposit and advance are saved as two separate payments`() = runTest {
        assertEquals(RecordPaymentResult.Saved, recordUpi())

        val saved = payments.observePayments(renterId).first()
        assertEquals(listOf(PaymentType.DEPOSIT, PaymentType.ADVANCE), saved.map { it.type })
        assertEquals(listOf(200_000L, 140_000L), saved.map { it.amountPaise })
        saved.forEach { payment ->
            assertEquals(PaymentMethod.UPI, payment.method)
            assertEquals("412345678901", payment.reference)
            assertEquals("Imran", payment.enteredBy)
            assertEquals(now.toEpochMilli(), payment.enteredAt)
            assertEquals(database.renterDao().findById(renterId)!!.vehicle.id, payment.vehicleId)
        }
    }

    @Test
    fun `a payment cannot be saved without a method and a reference`() = runTest {
        assertEquals(
            RecordPaymentResult.Invalid(setOf(PaymentError.METHOD_REQUIRED, PaymentError.REFERENCE_REQUIRED)),
            payments.recordInitialPayment(renterId, null, "   ", 200_000, 140_000),
        )
        assertTrue(payments.observePayments(renterId).first().isEmpty())
    }

    @Test
    fun `the first payment is only recorded once`() = runTest {
        recordUpi()
        assertEquals(RecordPaymentResult.AlreadyRecorded, recordUpi(reference = "999"))
        assertEquals(2, payments.observePayments(renterId).first().size)
    }

    @Test
    fun `an unknown renter is reported`() = runTest {
        assertEquals(
            RecordPaymentResult.RenterNotFound,
            payments.recordInitialPayment(42, PaymentMethod.CASH, "R-1", 200_000, 140_000),
        )
    }

    @Test
    fun `rent payments need a method and reference and count toward rent`() = runTest {
        assertEquals(
            RecordPaymentResult.Invalid(setOf(PaymentError.METHOD_REQUIRED, PaymentError.REFERENCE_REQUIRED)),
            payments.recordRent(renterId, null, "", 140_000),
        )
        assertEquals(RecordPaymentResult.Saved, payments.recordRent(renterId, PaymentMethod.CASH, "R-2", 140_000))
        assertEquals(140_000L, payments.observeRentCredits().first()[renterId])
    }

    @Test
    fun `the database itself refuses to edit or delete a payment`() = runTest {
        recordUpi()
        val db = database.openHelper.writableDatabase
        for (sql in listOf("UPDATE payments SET amountPaise = 1", "DELETE FROM payments")) {
            try {
                db.execSQL(sql)
                fail("Expected the database to refuse: $sql")
            } catch (expected: SQLiteConstraintException) {
                assertTrue(expected.message.orEmpty(), expected.message.orEmpty().contains("Payments cannot be"))
            }
        }
        assertEquals(2, payments.observePayments(renterId).first().size)
    }

    @Test
    fun `payment details are saved cleaned and read back`() = runTest {
        val result = payments.saveSettings(
            PaymentSettings(upiId = " ZMR@OKAXIS ", payeeName = "ZMR Mobility", depositPaise = 250_000),
        )

        assertEquals(SaveSettingsResult.Saved, result)
        val saved = payments.observeSettings().first()
        assertEquals("zmr@okaxis", saved.upiId)
        assertEquals(250_000L, saved.depositPaise)
        assertEquals(140_000L, saved.advancePaise)
    }

    @Test
    fun `invalid payment details are not saved`() = runTest {
        assertEquals(
            SaveSettingsResult.Invalid(setOf(PaymentSettingsError.IFSC_INVALID)),
            payments.saveSettings(
                PaymentSettings(bankAccountName = "ZMR", bankAccountNumber = "123456789012", bankIfsc = "BAD"),
            ),
        )
        assertEquals(PaymentSettings.from(emptyList()), payments.observeSettings().first())
    }
}
