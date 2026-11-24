package com.zmr.rent.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zmr.rent.data.dao.RenterDao
import com.zmr.rent.data.entity.Vehicle
import com.zmr.rent.renter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real Room database in memory (through Robolectric, so no phone is needed). */
@RunWith(AndroidJUnit4::class)
class RenterDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: RenterDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.renterDao()
    }

    @After
    fun closeDatabase() = database.close()

    /** Passes only when the database itself rejects [save] with a constraint error. */
    private suspend fun assertRefused(save: suspend () -> Unit) {
        try {
            save()
        } catch (expected: SQLiteConstraintException) {
            return
        }
        fail("Expected the database to refuse this save")
    }

    @Test
    fun `a renter is saved together with their vehicle`() = runTest {
        val id = dao.insertRenterWithVehicle(renter(name = "Ramesh Kumar"), "DL1SAB1234")

        val saved = dao.findById(id)!!
        assertEquals("Ramesh Kumar", saved.renter.name)
        assertEquals("DL1SAB1234", saved.vehicle.vehicleNumber)
        assertEquals(id, saved.vehicle.renterId)
        assertEquals(id, dao.findRenterIdByVehicleNumber("DL1SAB1234"))
    }

    @Test
    fun `one vehicle cannot be given to two renters`() = runTest {
        dao.insertRenterWithVehicle(renter(name = "Ramesh Kumar"), "DL1SAB1234")

        assertRefused {
            dao.insertRenterWithVehicle(renter(name = "Suresh Yadav"), "DL1SAB1234")
        }
        // The failed save left nothing behind: no second renter without a vehicle.
        assertEquals(listOf("Ramesh Kumar"), dao.observeAll().first().map { it.renter.name })
    }

    @Test
    fun `one renter cannot hold two vehicles`() = runTest {
        val id = dao.insertRenterWithVehicle(renter(), "DL1SAB1234")

        assertRefused {
            dao.insertVehicle(Vehicle(vehicleNumber = "UP16ER0042", renterId = id))
        }
    }

    @Test
    fun `changing a vehicle number to one in use is refused and changes nothing`() = runTest {
        dao.insertRenterWithVehicle(renter(name = "Ramesh Kumar"), "DL1SAB1234")
        val sureshId = dao.insertRenterWithVehicle(renter(name = "Suresh Yadav"), "UP16ER0042")
        val suresh = dao.findById(sureshId)!!.renter

        assertRefused {
            dao.updateRenterWithVehicle(suresh.copy(name = "Changed"), "DL1SAB1234")
        }
        val after = dao.findById(sureshId)!!
        assertEquals("Suresh Yadav", after.renter.name)
        assertEquals("UP16ER0042", after.vehicle.vehicleNumber)
    }

    @Test
    fun `renters are listed by name, ignoring letter case`() = runTest {
        dao.insertRenterWithVehicle(renter(name = "suresh"), "UP16ER0042")
        dao.insertRenterWithVehicle(renter(name = "Anil"), "DL1SAB1234")
        dao.insertRenterWithVehicle(renter(name = "Ramesh"), "HR26DK8337")

        assertEquals(listOf("Anil", "Ramesh", "suresh"), dao.observeAll().first().map { it.renter.name })
    }

    @Test
    fun `an unknown renter or vehicle is not found`() = runTest {
        assertNull(dao.findById(42))
        assertNull(dao.findRenterIdByVehicleNumber("DL1SAB1234"))
    }
}
