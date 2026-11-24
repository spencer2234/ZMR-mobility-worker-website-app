package com.zmr.rent.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zmr.rent.data.entity.CustomerType
import com.zmr.rent.data.entity.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Builds a database exactly as an older app version left it (from the schema files in
 * app/schemas/), then opens it with the current app. Room runs the migrations and refuses to
 * open the database if the result does not match the current tables, so this proves an
 * upgrade loses nothing and ends in the right shape.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseFile: File

    @Before
    fun setUp() {
        databaseFile = context.getDatabasePath(DB_NAME)
        databaseFile.parentFile?.mkdirs()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `upgrading from version 1 keeps renters, adds start dates and protects payments`() = runTest {
        createDatabaseAtVersion(1) { db ->
            // Added 2026-10-11 20:00 UTC, which is already 12 October (a Monday) in India.
            db.execSQL(
                "INSERT INTO renters (id, name, phone, rentDay, weeklyRentPaise, status, holdReason, " +
                    "createdBy, createdAt) VALUES (1, 'Ramesh Kumar', '9876543210', 'MONDAY', 140000, " +
                    "'ACTIVE', NULL, 'Worker', 1791748800000)",
            )
            db.execSQL("INSERT INTO vehicles (id, vehicleNumber, renterId) VALUES (1, 'DL1SAB1234', 1)")
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .withAppSetup()
            .allowMainThreadQueries()
            .build()
        try {
            val renters = database.renterDao().observeAll().first()
            assertEquals(1, renters.size)
            val renter = renters.single()
            assertEquals("Ramesh Kumar", renter.renter.name)
            assertEquals("DL1SAB1234", renter.vehicle.vehicleNumber)
            assertEquals(LocalDate.of(2026, 10, 12), renter.renter.startDate)
            assertEquals(DayOfWeek.MONDAY, renter.renter.rentDay)
            assertEquals(emptyList<Any>(), database.userDao().observeAll().first())
            assertEquals(emptyList<Any>(), database.paymentDao().observeForRenter(1).first())

            // The upgrade also installed the triggers that stop payments being edited or deleted.
            val triggers = database.openHelper.readableDatabase
                .query("SELECT name FROM sqlite_master WHERE type = 'trigger' ORDER BY name")
                .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            assertEquals(listOf("payments_no_delete", "payments_no_update"), triggers)
        } finally {
            database.close()
        }
    }

    @Test
    fun `upgrading from version 2 turns Workers into Employees and the Founder into the CEO`() = runTest {
        createDatabaseAtVersion(2) { db ->
            db.execSQL(
                "INSERT INTO users (id, name, role, pinHash, pinSalt, createdAt) VALUES " +
                    "(1, 'Imran', 'WORKER', 'aa', 'bb', 0), (2, 'Ali', 'FOUNDER', 'cc', 'dd', 0)",
            )
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .withAppSetup()
            .allowMainThreadQueries()
            .build()
        try {
            val users = database.userDao().observeAll().first()
            assertEquals(listOf("Ali" to UserRole.CEO, "Imran" to UserRole.EMPLOYEE), users.map { it.name to it.role })
            assertEquals(listOf("LOCAL-2", "LOCAL-1"), users.map { it.employeeCode })
        } finally {
            database.close()
        }
    }

    @Test
    fun `upgrading from version 5 keeps customers as renters in Delhi`() = runTest {
        createDatabaseAtVersion(5) { db ->
            db.execSQL(
                "INSERT INTO renters (id, name, phone, startDate, rentDay, weeklyRentPaise, status, createdBy, createdAt) " +
                    "VALUES (1, 'Ramesh Kumar', '9876543210', 20738, 'MONDAY', 140000, 'ACTIVE', 'Worker', 0)",
            )
            db.execSQL("INSERT INTO vehicles (id, vehicleNumber, renterId, rcNumber) VALUES (7, 'DL1SAB1234', 1, 'RC1')")
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .withAppSetup()
            .allowMainThreadQueries()
            .build()
        try {
            val saved = database.renterDao().findById(1)!!
            assertEquals(CustomerType.RENTER, saved.renter.customerType)
            assertEquals(0L, saved.renter.pendingPaise)
            assertEquals("Delhi", saved.vehicle.city)
            assertEquals("RC1", saved.vehicle.rcNumber)
        } finally {
            database.close()
        }
    }

    /** Creates the tables of [version] from its exported schema and lets [fill] add rows. */
    private fun createDatabaseAtVersion(version: Int, fill: (SQLiteDatabase) -> Unit) {
        val schema = JSONObject(File("schemas/${AppDatabase::class.java.name}/$version.json").readText())
            .getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setupQueries = schema.getJSONArray("setupQueries")
            for (i in 0 until setupQueries.length()) db.execSQL(setupQueries.getString(i))
            fill(db)
            db.version = version
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }

    @Test
    fun `upgrading from version 4 keeps vehicles and their payments linked`() = runTest {
        createDatabaseAtVersion(4) { db ->
            db.execSQL(
                "INSERT INTO renters (id, name, phone, startDate, rentDay, weeklyRentPaise, status, holdReason, " +
                    "createdBy, createdAt) VALUES (1, 'Ramesh Kumar', '9876543210', 20738, 'MONDAY', 140000, " +
                    "'ACTIVE', NULL, 'Worker', 0)",
            )
            db.execSQL("INSERT INTO vehicles (id, vehicleNumber, renterId) VALUES (7, 'DL1SAB1234', 1)")
            db.execSQL(
                "INSERT INTO payments (renterId, vehicleId, type, amountPaise, method, reference, receivedAt, " +
                    "enteredBy, enteredAt) VALUES (1, 7, 'DEPOSIT', 200000, 'CASH', 'R-1', 0, 'Worker', 0)",
            )
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .withAppSetup()
            .allowMainThreadQueries()
            .build()
        try {
            val renter = database.renterDao().findById(1)!!
            assertEquals(7L, renter.vehicle.id)
            assertEquals("", renter.vehicle.rcNumber)
            assertEquals(null, renter.renter.endDate)
            assertEquals(7L, database.paymentDao().observeForRenter(1).first().single().vehicleId)
        } finally {
            database.close()
        }
    }
}
