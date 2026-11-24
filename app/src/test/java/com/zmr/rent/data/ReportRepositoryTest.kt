package com.zmr.rent.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zmr.rent.data.entity.PaymentMethod
import com.zmr.rent.data.entity.TeamMember
import com.zmr.rent.data.entity.UserRole
import com.zmr.rent.data.repository.PaymentRepository
import com.zmr.rent.data.repository.ReadReportResult
import com.zmr.rent.data.repository.RenterRepository
import com.zmr.rent.data.repository.ReportRepository
import com.zmr.rent.data.session.CurrentUser
import com.zmr.rent.data.session.SessionManager
import com.zmr.rent.domain.Contact
import com.zmr.rent.domain.report.ReportKind
import com.zmr.rent.renterDetails
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ReportRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // 2 November 2026, 10:30 in India.
    private val clock = Clock.fixed(Instant.parse("2026-11-02T05:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: AppDatabase
    private val session = SessionManager()
    private lateinit var renters: RenterRepository
    private lateinit var payments: PaymentRepository
    private lateinit var reports: ReportRepository

    private val ravi = CurrentUser(
        name = "Ravi Kumar",
        role = UserRole.EMPLOYEE,
        userId = 1,
        employeeCode = "EMP-102",
        area = "Lucknow",
        reportsTo = Contact("Suresh Yadav", "MGR-7", "9123456780", "suresh@example.com"),
    )
    private val suresh = CurrentUser(
        name = "Suresh Yadav",
        role = UserRole.MANAGER,
        userId = 2,
        employeeCode = "MGR-7",
        area = "Lucknow",
        reportsTo = Contact("Ali", "CEO-1", "9876543210", "ali@example.com"),
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .withAppSetup()
            .allowMainThreadQueries()
            .build()
        renters = RenterRepository(database.renterDao(), session, clock)
        payments = PaymentRepository(database.paymentDao(), database.renterDao(), database.settingsDao(), session, clock)
        reports = ReportRepository(context, database.renterDao(), database.paymentDao(), database.userDao(), database.documentDao(), session, clock)
    }

    @After
    fun closeDatabase() = database.close()

    private suspend fun addRenterWithFirstPayment(name: String, vehicle: String, phone: String) {
        val id = (renters.addRenter(renterDetails(name = name, vehicleNumber = vehicle, phone = phone))
            as com.zmr.rent.data.repository.SaveRenterResult.Saved).renterId
        payments.recordInitialPayment(id, PaymentMethod.UPI, "4123", 200_000, 140_000)
    }

    @Test
    fun `an employee's report holds only what they entered`() = runTest {
        session.signIn(ravi)
        addRenterWithFirstPayment("Ramesh", "DL1SAB1234", "9876543210")
        session.signIn(suresh)
        addRenterWithFirstPayment("Mohan", "UP16ER0042", "9123456780")
        session.signIn(ravi)

        val report = reports.ownReport()

        assertEquals(ReportKind.EMPLOYEE, report.kind)
        assertEquals(listOf("Ramesh"), report.renters.map { it.name })
        assertEquals(2, report.payments.size)
        assertTrue(report.payments.all { it.employeeCode == "EMP-102" })
        val source = report.sources.single()
        assertEquals("Suresh Yadav", source.managerName)
        assertEquals(LocalDate.of(2026, 11, 2), source.reportDate)
        assertEquals(10, source.generatedAt.hour)
    }

    @Test
    fun `the Excel file can be read back by the manager's app`() = runTest {
        session.signIn(ravi)
        addRenterWithFirstPayment("Ramesh", "DL1SAB1234", "9876543210")
        val report = reports.ownReport()
        // The FileProvider link is not tested here: Robolectric on Windows cannot make one.
        val file = reports.writeFile(report)

        assertEquals("ZMR-Employee-report-EMP-102-Ravi-Kumar-2026-11-02.xlsx", file.name)
        val read = reports.read(android.net.Uri.fromFile(file)) as ReadReportResult.Read
        assertEquals(report, read.report)
    }

    @Test
    fun `a file that is not a report is skipped`() = runTest {
        val notes = File(context.cacheDir, "notes.txt").apply { writeText("hello") }
        assertTrue(reports.read(android.net.Uri.fromFile(notes)) is ReadReportResult.NotAReport)
    }

    @Test
    fun `a manager's team report includes their own data, received reports, and who is missing`() = runTest {
        session.signIn(ravi)
        addRenterWithFirstPayment("Ramesh", "DL1SAB1234", "9876543210")
        val raviReport = reports.ownReport()

        session.signIn(suresh)
        addRenterWithFirstPayment("Mohan", "UP16ER0042", "9123456780")
        for ((code, name) in listOf("EMP-102" to "Ravi Kumar", "EMP-104" to "Vikram Das")) {
            database.userDao().insertTeamMember(
                TeamMember(code, name, UserRole.EMPLOYEE, "9000000000", "x@example.com", "Lucknow", "MGR-7", 0),
            )
        }

        val team = reports.combinedReport(listOf(raviReport))

        assertEquals(ReportKind.TEAM, team.kind)
        assertEquals(listOf("Ravi Kumar", "Suresh Yadav"), team.sources.map { it.employeeName })
        assertEquals(setOf("Ramesh", "Mohan"), team.renters.map { it.name }.toSet())
        assertEquals(listOf("Vikram Das"), team.missing.map { it.name })
    }

    @Test
    fun `the CEO's company report leaves out the CEO when they entered nothing`() = runTest {
        session.signIn(ravi)
        addRenterWithFirstPayment("Ramesh", "DL1SAB1234", "9876543210")
        val raviReport = reports.ownReport()
        session.signIn(CurrentUser(name = "Ali", role = UserRole.CEO, userId = 3, employeeCode = "CEO-1"))

        val company = reports.combinedReport(listOf(raviReport))

        assertEquals(ReportKind.COMPANY, company.kind)
        assertEquals(listOf("EMP-102"), company.sources.map { it.employeeCode })
        assertEquals("Ali", company.preparedByName)
    }

    @Test
    fun `a received report counts for the Monday-to-Sunday week it was made in`() = runTest {
        session.signIn(ravi)
        reports.recordSubmissions(listOf(reports.ownReport()))

        // 2 November 2026 is a Monday.
        assertEquals(LocalDate.of(2026, 11, 2), reports.currentWeekStart())
        assertEquals(listOf("EMP-102"), reports.observeSubmitted(LocalDate.of(2026, 11, 2)).first())
        assertTrue(reports.observeSubmitted(LocalDate.of(2026, 11, 9)).first().isEmpty())
    }
}
