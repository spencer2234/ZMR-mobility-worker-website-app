package com.zmr.rent.domain.report

import com.zmr.rent.data.entity.CustomerType
import com.zmr.rent.data.entity.HoldReason
import com.zmr.rent.data.entity.PaymentMethod
import com.zmr.rent.data.entity.PaymentType
import com.zmr.rent.data.entity.RenterStatus
import com.zmr.rent.data.entity.UserRole
import com.zmr.rent.domain.xlsx.XlsxReader
import com.zmr.rent.domain.xlsx.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class ReportWorkbookTest {

    // Monday 2 November 2026.
    private val day = LocalDate.of(2026, 11, 2)

    private fun employeeReport(code: String, name: String, area: String, manager: String, madeAt: LocalDateTime) = Report(
        kind = ReportKind.EMPLOYEE,
        preparedByName = name,
        preparedByCode = code,
        preparedByRole = UserRole.EMPLOYEE,
        area = area,
        generatedAt = madeAt,
        sources = listOf(ReportSource(code, name, UserRole.EMPLOYEE, area, manager, madeAt.toLocalDate(), madeAt)),
        renters = listOf(
            RenterRow(code, "Renter of $name", "9876543210", "DL1SAB1234", day.minusWeeks(2), DayOfWeek.MONDAY,
                140_000, RenterStatus.ACTIVE, null, name, day.minusWeeks(2),
                customerType = CustomerType.LESSEE, city = area, vehicleModel = "BGauss", rcNumber = "RC123",
                endDate = day.plusWeeks(10), residualValuePaise = 2_500_000, depositPaise = 340_000,
                depositDate = day.minusWeeks(2), expectedDate = day.minusWeeks(1), lastPaymentDate = day,
                dueDate = day, delayDays = 3, totalCashPaise = 480_000, pendingPaise = 40_000,
                pendingChallans = 2, documents = "Photograph; Id proof: Pan card"),
            RenterRow(code, "Held renter of $name", "9123456780", "UP16ER0042", day.minusWeeks(3), DayOfWeek.FRIDAY,
                150_050, RenterStatus.ON_HOLD, HoldReason.REPAIR, name, day.minusWeeks(3)),
        ),
        payments = listOf(
            PaymentRow(code, day.atTime(10, 30), "Renter of $name", "DL1SAB1234", PaymentType.RENT, 140_000,
                PaymentMethod.UPI, "412345678901", name),
            PaymentRow(code, day.atTime(11, 0), "New renter", "HR26DK8337", PaymentType.DEPOSIT, 200_000,
                PaymentMethod.CASH, "R-17", name),
            PaymentRow(code, day.minusDays(1).atTime(9, 0), "Renter of $name", "DL1SAB1234", PaymentType.RENT, 140_000,
                PaymentMethod.BANK_TRANSFER, "UTR99", name),
        ),
    )

    private fun roundTrip(report: Report): Report {
        val bytes = XlsxWriter.toBytes(ReportWorkbook.sheets(report))
        return ReportWorkbook.parse(XlsxReader.read(bytes.inputStream()))
    }

    @Test
    fun `a report read back from Excel has the same data`() {
        val report = employeeReport("EMP-102", "Ravi Kumar", "Lucknow", "Suresh Yadav", day.atTime(18, 30, 15))
        assertEquals(report, roundTrip(report))
    }

    @Test
    fun `totals for a person count only their report day`() {
        val report = employeeReport("EMP-102", "Ravi Kumar", "Lucknow", "Suresh Yadav", day.atTime(18, 30))
        val totals = SourceTotals.of(report.sources.single(), report.renters, report.payments)

        assertEquals(2, totals.renters)
        assertEquals(1, totals.active)
        assertEquals(1, totals.onHold)
        // Only the active Monday renter is due on Monday 2 November.
        assertEquals(1, totals.dueOnReportDay)
        assertEquals(140_000L, totals.rentDueOnReportDayPaise)
        // The bank transfer was the day before, so it is not counted.
        assertEquals(340_000L, totals.collectedPaise)
        assertEquals(200_000L, totals.cashPaise)
        assertEquals(140_000L, totals.upiPaise)
        assertEquals(0L, totals.bankPaise)
        assertEquals(200_000L, totals.depositsPaise)
        assertEquals(140_000L, totals.rentPaise)
    }

    @Test
    fun `a manager's team report combines employees, keeps only the newest file each, and lists who is missing`() {
        val ravi = employeeReport("EMP-102", "Ravi Kumar", "Lucknow", "Suresh Yadav", day.atTime(18, 0))
        val raviOlder = employeeReport("EMP-102", "Ravi Kumar", "Lucknow", "Suresh Yadav", day.minusDays(1).atTime(18, 0))
        val asha = employeeReport("EMP-103", "Asha Singh", "Lucknow", "Suresh Yadav", day.atTime(17, 0))
        val manager = employeeReport("MGR-7", "Suresh Yadav", "Lucknow", "Suresh Yadav", day.atTime(19, 0))
            .let { it.copy(preparedByRole = UserRole.MANAGER, sources = it.sources.map { s -> s.copy(role = UserRole.MANAGER) }) }

        val team = ReportMerger.combine(
            kind = ReportKind.TEAM,
            own = manager,
            received = listOf(raviOlder, asha, ravi),
            expected = listOf(
                ExpectedMember("EMP-102", "Ravi Kumar", UserRole.EMPLOYEE, "Lucknow"),
                ExpectedMember("EMP-103", "Asha Singh", UserRole.EMPLOYEE, "Lucknow"),
                ExpectedMember("EMP-104", "Vikram Das", UserRole.EMPLOYEE, "Lucknow"),
            ),
        )

        assertEquals(listOf("Asha Singh", "Ravi Kumar", "Suresh Yadav"), team.sources.map { it.employeeName })
        assertEquals(day.atTime(18, 0), team.sources.single { it.employeeCode == "EMP-102" }.generatedAt)
        assertEquals(6, team.renters.size)
        assertEquals(9, team.payments.size)
        assertEquals(listOf("Vikram Das"), team.missing.map { it.name })
        assertEquals("Suresh Yadav", team.preparedByName)
    }

    @Test
    fun `the CEO combines team reports from different areas`() {
        val lucknowTeam = ReportMerger.combine(
            ReportKind.TEAM,
            employeeReport("MGR-7", "Suresh Yadav", "Lucknow", "Suresh Yadav", day.atTime(19, 0)),
            listOf(employeeReport("EMP-102", "Ravi Kumar", "Lucknow", "Suresh Yadav", day.atTime(18, 0))),
            emptyList(),
        )
        val delhiTeam = ReportMerger.combine(
            ReportKind.TEAM,
            employeeReport("MGR-9", "Neha Gupta", "Delhi", "Neha Gupta", day.atTime(19, 30)),
            emptyList(),
            emptyList(),
        )
        val ceo = Report(
            kind = ReportKind.EMPLOYEE,
            preparedByName = "Ali",
            preparedByCode = "CEO-1",
            preparedByRole = UserRole.CEO,
            area = "",
            generatedAt = day.atTime(20, 0),
            sources = listOf(ReportSource("CEO-1", "Ali", UserRole.CEO, "", "", day, day.atTime(20, 0))),
            renters = emptyList(),
            payments = emptyList(),
        )

        // The team files travel as Excel and are read back, exactly as on the CEO's phone.
        val company = ReportMerger.combine(ReportKind.COMPANY, ceo, listOf(roundTrip(lucknowTeam), roundTrip(delhiTeam)), emptyList())

        assertEquals(listOf("", "Delhi", "Lucknow", "Lucknow"), company.sources.map { it.area })
        assertEquals(6, company.renters.size)
        assertEquals(company, roundTrip(company))
    }

    @Test
    fun `the first sheet follows ZMR's columns and shows whose data it is and when it was sent`() {
        val report = employeeReport("EMP-102", "Ravi Kumar", "Delhi", "Suresh Yadav", day.atTime(18, 30))
        val sheet = XlsxReader.read(XlsxWriter.toBytes(ReportWorkbook.sheets(report)).inputStream()).getValue("Rental Data")

        assertEquals("Data from: Ravi Kumar (EMP-102)  |  Sent: 02-11-2026 18:30 IST", sheet[0][0])
        assertEquals(
            listOf(
                "Rider Name", "Rider Number", "Vehicle Number", "Vehicle Model", "Rental Start Date", "Security Deposit",
                "Security Deposit Date", "Expected Date", "Payment Received Date", "Rental Due Date",
                "Rental Delay (Days)", "Total Cash", "Pending Paisa", "Pending Challan",
            ),
            sheet[1].take(14),
        )
        assertEquals("Submitted By", sheet[1].last())
        assertEquals("Ravi Kumar (EMP-102), 02-11-2026", sheet[2].last())
    }

    @Test
    fun `any other spreadsheet is refused`() {
        val other = XlsxWriter.toBytes(
            listOf(com.zmr.rent.domain.xlsx.Sheet("Sheet1", listOf(listOf(com.zmr.rent.domain.xlsx.Cell.Text("hello"))))),
        )
        assertThrows(ReportWorkbook.NotAReportException::class.java) {
            ReportWorkbook.parse(XlsxReader.read(other.inputStream()))
        }
    }
}
