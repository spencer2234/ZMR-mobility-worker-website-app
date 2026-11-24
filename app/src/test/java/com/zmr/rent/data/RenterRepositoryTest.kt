package com.zmr.rent.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zmr.rent.data.entity.HoldReason
import com.zmr.rent.data.entity.RenterStatus
import com.zmr.rent.data.repository.RenterRepository
import com.zmr.rent.data.repository.SaveRenterResult
import com.zmr.rent.data.session.Session
import com.zmr.rent.domain.RenterFieldError
import com.zmr.rent.renterDetails
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RenterRepositoryTest {

    private val savedAt = Instant.parse("2026-10-12T04:30:00Z")
    private var currentUser = "Imran"

    private lateinit var database: AppDatabase
    private lateinit var repository: RenterRepository

    @Before
    fun createRepository() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val session = object : Session {
            override val userName: String get() = currentUser
        }
        repository = RenterRepository(database.renterDao(), session, Clock.fixed(savedAt, ZoneOffset.UTC))
    }

    @After
    fun closeDatabase() = database.close()

    private suspend fun add(
        name: String = "Ramesh Kumar",
        vehicleNumber: String = "DL1SAB1234",
    ): Long = (repository.addRenter(renterDetails(name = name, vehicleNumber = vehicleNumber)) as SaveRenterResult.Saved)
        .renterId

    private val vehicleTaken = SaveRenterResult.Invalid(setOf(RenterFieldError.VEHICLE_TAKEN))

    @Test
    fun `adding a renter cleans the details and records who entered them and when`() = runTest {
        val result = repository.addRenter(
            renterDetails(name = " Ramesh  Kumar ", phone = "+91 98765 43210", vehicleNumber = "dl 1s ab 1234"),
        )

        val saved = repository.getRenter((result as SaveRenterResult.Saved).renterId)!!
        assertEquals("Ramesh Kumar", saved.renter.name)
        assertEquals("9876543210", saved.renter.phone)
        assertEquals("DL1SAB1234", saved.vehicle.vehicleNumber)
        assertEquals(140_000L, saved.renter.weeklyRentPaise)
        assertEquals(LocalDate.of(2026, 10, 12), saved.renter.startDate)
        assertEquals(DayOfWeek.MONDAY, saved.renter.rentDay)
        assertEquals("Imran", saved.renter.createdBy)
        assertEquals(savedAt.toEpochMilli(), saved.renter.createdAt)
    }

    @Test
    fun `invalid details are refused and nothing is saved`() = runTest {
        val result = repository.addRenter(renterDetails(phone = "12345"))

        assertEquals(SaveRenterResult.Invalid(setOf(RenterFieldError.PHONE_INVALID)), result)
        assertTrue(repository.observeRenters().first().isEmpty())
    }

    @Test
    fun `a vehicle already linked to a renter cannot be given to another`() = runTest {
        add(name = "Ramesh Kumar", vehicleNumber = "DL1SAB1234")

        // Written differently, but the same vehicle.
        val result = repository.addRenter(renterDetails(name = "Suresh Yadav", vehicleNumber = "dl-1s-ab-1234"))

        assertEquals(vehicleTaken, result)
        assertEquals(1, repository.observeRenters().first().size)
    }

    @Test
    fun `editing keeps the renter's own vehicle number without complaint`() = runTest {
        val id = add(vehicleNumber = "DL1SAB1234")

        val result = repository.updateRenter(
            id,
            renterDetails(name = "Ramesh K", vehicleNumber = "DL1SAB1234", startDate = LocalDate.of(2026, 10, 16)),
        )

        assertEquals(SaveRenterResult.Saved(id), result)
        val saved = repository.getRenter(id)!!
        assertEquals("Ramesh K", saved.renter.name)
        assertEquals(LocalDate.of(2026, 10, 16), saved.renter.startDate)
        // The rent day follows the start date: 16 October 2026 is a Friday.
        assertEquals(DayOfWeek.FRIDAY, saved.renter.rentDay)
    }

    @Test
    fun `editing cannot take another renter's vehicle`() = runTest {
        add(name = "Ramesh Kumar", vehicleNumber = "DL1SAB1234")
        val sureshId = add(name = "Suresh Yadav", vehicleNumber = "UP16ER0042")

        val result = repository.updateRenter(
            sureshId,
            renterDetails(name = "Suresh Yadav", vehicleNumber = "DL1SAB1234"),
        )

        assertEquals(vehicleTaken, result)
        assertEquals("UP16ER0042", repository.getRenter(sureshId)!!.vehicle.vehicleNumber)
    }

    @Test
    fun `editing never changes who first added the renter`() = runTest {
        val id = add()
        currentUser = "Someone else"

        repository.updateRenter(id, renterDetails(name = "Ramesh K"))

        assertEquals("Imran", repository.getRenter(id)!!.renter.createdBy)
    }

    @Test
    fun `putting a renter on hold stores the reason and making them active clears it`() = runTest {
        val id = add()
        val onHold = renterDetails().copy(status = RenterStatus.ON_HOLD, holdReason = HoldReason.MISSED_PAYMENTS)

        repository.updateRenter(id, onHold)
        assertEquals(HoldReason.MISSED_PAYMENTS, repository.getRenter(id)!!.renter.holdReason)

        repository.updateRenter(id, onHold.copy(status = RenterStatus.ACTIVE))
        val active = repository.getRenter(id)!!.renter
        assertEquals(RenterStatus.ACTIVE, active.status)
        assertNull(active.holdReason)
    }

    @Test
    fun `editing a renter that does not exist reports not found`() = runTest {
        assertEquals(SaveRenterResult.NotFound, repository.updateRenter(42, renterDetails()))
    }

    @Test
    fun `the RC number and return date are saved with the renter`() = runTest {
        val end = LocalDate.of(2026, 12, 31)
        val id = (repository.addRenter(renterDetails().copy(rcNumber = "dl 01 rc 2020", endDate = end)) as SaveRenterResult.Saved).renterId

        val saved = repository.getRenter(id)!!
        assertEquals("DL01RC2020", saved.vehicle.rcNumber)
        assertEquals(end, saved.renter.endDate)
    }

    @Test
    fun `a free inventory vehicle is handed to a new renter, and goes back when they switch vehicles`() = runTest {
        assertTrue(repository.addVehicle("DL1SAB1234", "RC-1"))
        assertTrue(repository.addVehicle("UP16ER0042", ""))
        assertFalse("the same vehicle cannot be added twice", repository.addVehicle("dl 1s ab 1234", ""))

        val id = add(vehicleNumber = "DL1SAB1234")
        assertEquals("RC1", repository.getRenter(id)!!.vehicle.rcNumber)

        repository.updateRenter(id, renterDetails(vehicleNumber = "UP16ER0042"))

        val inventory = repository.observeInventory().first().associate { it.vehicle.vehicleNumber to it.renterName }
        assertEquals(mapOf("DL1SAB1234" to null, "UP16ER0042" to "Ramesh Kumar"), inventory)
    }
}
