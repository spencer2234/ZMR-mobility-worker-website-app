package com.zmr.rent.domain.report

import com.zmr.rent.data.entity.CustomerType
import com.zmr.rent.data.entity.HoldReason
import com.zmr.rent.data.entity.PaymentMethod
import com.zmr.rent.data.entity.PaymentType
import com.zmr.rent.data.entity.RenterStatus
import com.zmr.rent.data.entity.UserRole
import com.zmr.rent.domain.IndianPhone
import com.zmr.rent.domain.xlsx.Cell
import com.zmr.rent.domain.xlsx.CellStyle
import com.zmr.rent.domain.xlsx.Sheet
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * The layout of a report in Excel, and reading it back.
 *
 * Sheets: Rental Data (ZMR's own sheet layout, with whose data it is on top and Submitted By on
 * the far right), Summary, Payments, and Sources and About (which let the next person's app
 * combine the file). The sheet and column names are fixed in English, because the
 * app finds the data by them; the people reading the file can be anywhere in the company.
 */
object ReportWorkbook {

    class NotAReportException(message: String) : Exception(message)

    private const val FORMAT = "zmr-report"
    private const val VERSION = "1"

    private const val SUMMARY = "Summary"
    private const val RENTERS = "Rental Data"
    private const val PAYMENTS = "Payments"
    private const val SOURCES = "Sources"
    private const val ABOUT = "About"

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)
    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm", Locale.ENGLISH)
    private val DATE_TIME_SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH)

    /** ZMR's sheet columns in ZMR's order, then the app's extra columns, with Submitted By last. */
    private val RENTER_COLUMNS = listOf(
        "Rider Name", "Rider Number", "Vehicle Number", "Vehicle Model", "Rental Start Date", "Security Deposit",
        "Security Deposit Date", "Expected Date", "Payment Received Date", "Rental Due Date", "Rental Delay (Days)",
        "Total Cash", "Pending Paisa", "Pending Challan",
        "Customer Type", "City", "RC Number", "Return / Lease End Date", "Residual Value", "Weekly Rent", "Rent Day",
        "Status", "Hold Reason", "Documents", "Added By", "Added On", "Area", "Manager", "Employee ID", "Submitted By",
    )
    private val PAYMENT_COLUMNS = listOf(
        "Area", "Manager", "Employee", "Employee ID", "Received (IST)", "Renter", "Vehicle number", "Type",
        "Amount (Rs)", "Method", "Reference", "Entered by",
    )
    private val SOURCE_COLUMNS = listOf("Employee ID", "Employee", "Role", "Area", "Manager", "Report date", "Generated at")
    private val SUMMARY_COLUMNS = listOf(
        "Area", "Manager", "Employee", "Employee ID", "Report date", "Renters", "Active rentals", "On hold",
        "Due on report day", "Rent due (Rs)", "Collected (Rs)", "Cash (Rs)", "UPI (Rs)", "Bank transfer (Rs)",
        "Deposits (Rs)", "Advances (Rs)", "Weekly rent (Rs)",
    )

    // ---------- Writing ----------

    fun sheets(report: Report): List<Sheet> = listOf(
        rentersSheet(report),
        summarySheet(report),
        paymentsSheet(report),
        sourcesSheet(report),
        aboutSheet(report),
    )

    private fun header(columns: List<String>) = columns.map { Cell.Text(it, CellStyle.BOLD) }

    private fun text(value: String) = Cell.Text(value)

    private fun summarySheet(report: Report): Sheet {
        val rows = mutableListOf<List<Cell>>()
        rows += listOf(Cell.Text("ZMR Mobility - ${report.kind.title}", CellStyle.TITLE))
        rows += listOf(text("Prepared by"), text("${report.preparedByName} (${report.preparedByCode}), ${roleLabel(report.preparedByRole)}"))
        if (report.area.isNotEmpty()) rows += listOf(text("Area"), text(report.area))
        rows += listOf(text("Generated (IST)"), text(report.generatedAt.format(DATE_TIME)))
        rows += listOf(text("Figures"), text("Money is what each person recorded on their report day."))
        rows += emptyList<Cell>()
        val headerRow = rows.size
        rows += header(SUMMARY_COLUMNS)

        val totalsBySource = report.sources.associateWith { SourceTotals.of(it, report.renters, report.payments) }
        val areas = report.sources.groupBy { it.area }
        for ((area, sources) in areas) {
            for (source in sources) rows += summaryRow(source, totalsBySource.getValue(source))
            if (areas.size > 1) {
                val subtotal = sources.fold(SourceTotals.ZERO) { sum, it -> sum + totalsBySource.getValue(it) }
                rows += totalsRow("Total ${area.ifEmpty { "(no area)" }}", subtotal)
            }
        }
        val total = totalsBySource.values.fold(SourceTotals.ZERO) { sum, it -> sum + it }
        rows += totalsRow("Total", total)

        if (report.missing.isNotEmpty()) {
            rows += emptyList<Cell>()
            rows += listOf(Cell.Text("No report received from", CellStyle.BOLD))
            rows += header(listOf("Area", "Name", "Employee ID", "Role"))
            for (member in report.missing) {
                rows += listOf(text(member.area), text(member.name), text(member.employeeCode), text(roleLabel(member.role)))
            }
        }
        return Sheet(SUMMARY, rows, columnWidths = listOf(16, 18, 18, 12, 12) + List(12) { 14 }, frozenRows = headerRow + 1)
    }

    private fun summaryRow(source: ReportSource, totals: SourceTotals) = listOf(
        text(source.area),
        text(source.managerName),
        text(source.employeeName),
        text(source.employeeCode),
        text(source.reportDate.format(DATE)),
    ) + totalsCells(totals, CellStyle.NORMAL, CellStyle.MONEY)

    private fun totalsRow(label: String, totals: SourceTotals) =
        listOf(Cell.Text(label, CellStyle.BOLD), Cell.Empty, Cell.Empty, Cell.Empty, Cell.Empty) +
            totalsCells(totals, CellStyle.BOLD, CellStyle.BOLD_MONEY)

    private fun totalsCells(t: SourceTotals, count: CellStyle, money: CellStyle) = listOf(
        Cell.Count(t.renters.toLong(), count),
        Cell.Count(t.active.toLong(), count),
        Cell.Count(t.onHold.toLong(), count),
        Cell.Count(t.dueOnReportDay.toLong(), count),
        Cell.Money(t.rentDueOnReportDayPaise, money),
        Cell.Money(t.collectedPaise, money),
        Cell.Money(t.cashPaise, money),
        Cell.Money(t.upiPaise, money),
        Cell.Money(t.bankPaise, money),
        Cell.Money(t.depositsPaise, money),
        Cell.Money(t.advancesPaise, money),
        Cell.Money(t.rentPaise, money),
    )

    private fun rentersSheet(report: Report): Sheet {
        val sources = report.sources.associateBy { it.employeeCode }
        val whose = report.sources.joinToString(", ") { "${it.employeeName} (${it.employeeCode})" }
        val title = "Data from: $whose  |  Sent: ${report.generatedAt.format(DATE_TIME)} IST"
        val rows = listOf(
            listOf(Cell.Text(title, CellStyle.BOLD)),
            RENTER_COLUMNS.map { Cell.Text(it, CellStyle.HEADER) },
        ) + report.renters.map { renter ->
            val source = sources[renter.employeeCode]
            listOf(
                text(renter.name),
                text(renter.phone),
                text(renter.vehicleNumber),
                text(renter.vehicleModel),
                text(renter.startDate.format(DATE)),
                Cell.Money(renter.depositPaise),
                date(renter.depositDate),
                date(renter.expectedDate),
                date(renter.lastPaymentDate),
                date(renter.dueDate),
                Cell.Count(renter.delayDays, if (renter.delayDays > 0) CellStyle.DELAY else CellStyle.NORMAL),
                Cell.Money(renter.totalCashPaise),
                Cell.Money(renter.pendingPaise),
                Cell.Count(renter.pendingChallans.toLong()),
                text(customerLabel(renter.customerType)),
                text(renter.city),
                text(renter.rcNumber),
                date(renter.endDate),
                renter.residualValuePaise?.let { Cell.Money(it) } ?: Cell.Empty,
                Cell.Money(renter.weeklyRentPaise),
                text(dayLabel(renter.rentDay)),
                text(statusLabel(renter.status)),
                text(renter.holdReason?.let(::holdLabel).orEmpty()),
                text(renter.documents),
                text(renter.addedBy),
                text(renter.addedOn.format(DATE)),
                text(source?.area.orEmpty()),
                text(source?.managerName.orEmpty()),
                text(renter.employeeCode),
                text("${source?.employeeName.orEmpty()} (${renter.employeeCode}), ${source?.generatedAt?.format(DATE).orEmpty()}"),
            )
        }
        return Sheet(RENTERS, rows, columnWidths = List(RENTER_COLUMNS.size) { 16 }, frozenRows = 2)
    }

    private fun date(value: LocalDate?) = text(value?.format(DATE).orEmpty())

    private fun paymentsSheet(report: Report): Sheet {
        val sources = report.sources.associateBy { it.employeeCode }
        val rows = listOf(header(PAYMENT_COLUMNS)) + report.payments.map { payment ->
            val source = sources[payment.employeeCode]
            listOf(
                text(source?.area.orEmpty()),
                text(source?.managerName.orEmpty()),
                text(source?.employeeName.orEmpty()),
                text(payment.employeeCode),
                text(payment.receivedAt.format(DATE_TIME)),
                text(payment.renterName),
                text(payment.vehicleNumber),
                text(typeLabel(payment.type)),
                Cell.Money(payment.amountPaise),
                text(methodLabel(payment.method)),
                text(payment.reference),
                text(payment.enteredBy),
            )
        }
        return Sheet(PAYMENTS, rows, columnWidths = listOf(14, 16, 16, 12, 17, 20, 15, 10, 13, 14, 18, 14), frozenRows = 1)
    }

    private fun sourcesSheet(report: Report): Sheet {
        val rows = listOf(header(SOURCE_COLUMNS)) + report.sources.map { source ->
            listOf(
                text(source.employeeCode),
                text(source.employeeName),
                text(source.role.name),
                text(source.area),
                text(source.managerName),
                text(source.reportDate.format(DATE)),
                text(source.generatedAt.format(DATE_TIME_SECONDS)),
            )
        }
        return Sheet(SOURCES, rows, columnWidths = listOf(12, 18, 10, 14, 18, 12, 20), frozenRows = 1)
    }

    private fun aboutSheet(report: Report): Sheet {
        val rows = listOf(
            listOf("Format", FORMAT),
            listOf("Version", VERSION),
            listOf("Kind", report.kind.name),
            listOf("Prepared by", report.preparedByName),
            listOf("Prepared by ID", report.preparedByCode),
            listOf("Prepared by role", report.preparedByRole.name),
            listOf("Area", report.area),
            listOf("Generated at", report.generatedAt.format(DATE_TIME_SECONDS)),
            listOf("Note", "Made by the ZMR Rent app. Please do not edit the Sources or About sheets."),
        ).map { (key, value) -> listOf(Cell.Text(key, CellStyle.BOLD), text(value)) }
        return Sheet(ABOUT, rows, columnWidths = listOf(18, 60))
    }

    // ---------- Reading ----------

    /** Reads a report made by this app. Throws [NotAReportException] for any other spreadsheet. */
    fun parse(sheets: Map<String, List<List<String>>>): Report {
        val about = sheets[ABOUT]?.associate { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
            ?: throw NotAReportException("No About sheet")
        if (about["Format"] != FORMAT) throw NotAReportException("Not a ZMR report")
        if (about["Version"] != VERSION) throw NotAReportException("Unsupported report version ${about["Version"]}")

        return try {
            Report(
                kind = ReportKind.valueOf(about.getValue("Kind")),
                preparedByName = about["Prepared by"].orEmpty(),
                preparedByCode = about["Prepared by ID"].orEmpty(),
                preparedByRole = UserRole.valueOf(about.getValue("Prepared by role")),
                area = about["Area"].orEmpty(),
                generatedAt = dateTime(about.getValue("Generated at")),
                sources = table(sheets, SOURCES).map { row ->
                    ReportSource(
                        employeeCode = row.getValue("Employee ID"),
                        employeeName = row.getValue("Employee"),
                        role = UserRole.valueOf(row.getValue("Role")),
                        area = row.getValue("Area"),
                        managerName = row.getValue("Manager"),
                        reportDate = LocalDate.parse(row.getValue("Report date"), DATE),
                        generatedAt = dateTime(row.getValue("Generated at")),
                    )
                },
                renters = table(sheets, RENTERS, headerRow = 1).map { row ->
                    RenterRow(
                        employeeCode = row.getValue("Employee ID"),
                        name = row.getValue("Rider Name"),
                        phone = IndianPhone.normalize(row.getValue("Rider Number")),
                        vehicleNumber = row.getValue("Vehicle Number"),
                        startDate = LocalDate.parse(row.getValue("Rental Start Date"), DATE),
                        rentDay = parseLabel(row.getValue("Rent Day"), DayOfWeek.entries, ::dayLabel),
                        weeklyRentPaise = paise(row.getValue("Weekly Rent")),
                        status = parseLabel(row.getValue("Status"), RenterStatus.entries, ::statusLabel),
                        holdReason = row.getValue("Hold Reason").takeIf { it.isNotEmpty() }
                            ?.let { parseLabel(it, HoldReason.entries, ::holdLabel) },
                        addedBy = row.getValue("Added By"),
                        addedOn = LocalDate.parse(row.getValue("Added On"), DATE),
                        customerType = parseLabel(row.getValue("Customer Type"), CustomerType.entries, ::customerLabel),
                        city = row.getValue("City"),
                        vehicleModel = row.getValue("Vehicle Model"),
                        rcNumber = row.getValue("RC Number"),
                        endDate = optionalDate(row.getValue("Return / Lease End Date")),
                        residualValuePaise = row.getValue("Residual Value").takeIf { it.isNotEmpty() }?.let(::paise),
                        depositPaise = paise(row.getValue("Security Deposit")),
                        depositDate = optionalDate(row.getValue("Security Deposit Date")),
                        expectedDate = optionalDate(row.getValue("Expected Date")),
                        lastPaymentDate = optionalDate(row.getValue("Payment Received Date")),
                        dueDate = optionalDate(row.getValue("Rental Due Date")),
                        delayDays = BigDecimal(row.getValue("Rental Delay (Days)")).toLong(),
                        totalCashPaise = paise(row.getValue("Total Cash")),
                        pendingPaise = paise(row.getValue("Pending Paisa")),
                        pendingChallans = BigDecimal(row.getValue("Pending Challan")).toInt(),
                        documents = row.getValue("Documents"),
                    )
                },
                payments = table(sheets, PAYMENTS).map { row ->
                    PaymentRow(
                        employeeCode = row.getValue("Employee ID"),
                        receivedAt = dateTime(row.getValue("Received (IST)")),
                        renterName = row.getValue("Renter"),
                        vehicleNumber = row.getValue("Vehicle number"),
                        type = parseLabel(row.getValue("Type"), PaymentType.entries, ::typeLabel),
                        amountPaise = paise(row.getValue("Amount (Rs)")),
                        method = parseLabel(row.getValue("Method"), PaymentMethod.entries, ::methodLabel),
                        reference = row.getValue("Reference"),
                        enteredBy = row.getValue("Entered by"),
                    )
                },
            )
        } catch (e: NotAReportException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw NotAReportException("Damaged report: ${e.message}")
        } catch (e: NoSuchElementException) {
            throw NotAReportException("Damaged report: ${e.message}")
        } catch (e: DateTimeParseException) {
            throw NotAReportException("Damaged report: ${e.message}")
        }
    }

    /** Rows of a sheet as column-name to value, using [headerRow] as the header. */
    private fun table(
        sheets: Map<String, List<List<String>>>,
        name: String,
        headerRow: Int = 0,
    ): List<Map<String, String>> {
        val rows = sheets[name] ?: throw NotAReportException("No $name sheet")
        val header = rows.getOrNull(headerRow) ?: return emptyList()
        return rows.drop(headerRow + 1)
            .filter { row -> row.any { it.isNotBlank() } }
            .map { row -> header.indices.associate { header[it].trim() to row.getOrElse(it) { "" }.trim() } }
    }

    private fun optionalDate(text: String) = text.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it, DATE) }

    private fun dateTime(text: String): LocalDateTime = try {
        LocalDateTime.parse(text, DATE_TIME_SECONDS)
    } catch (e: DateTimeParseException) {
        LocalDateTime.parse(text, DATE_TIME)
    }

    /** "1400.5" or "1400.50" to 140050 paise, exactly. */
    private fun paise(text: String): Long =
        BigDecimal(text.replace(",", "")).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()

    private fun <T> parseLabel(text: String, values: List<T>, label: (T) -> String): T =
        values.firstOrNull { label(it).equals(text, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown value \"$text\"")

    // English labels used in the spreadsheet.

    private fun roleLabel(role: UserRole) = when (role) {
        UserRole.EMPLOYEE -> "Employee"
        UserRole.MANAGER -> "Manager"
        UserRole.CEO -> "Upper management"
    }

    private fun dayLabel(day: DayOfWeek) = day.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)

    private fun customerLabel(type: CustomerType) = when (type) {
        CustomerType.RENTER -> "Renter"
        CustomerType.LESSEE -> "Lessee"
    }

    private fun statusLabel(status: RenterStatus) = when (status) {
        RenterStatus.ACTIVE -> "Active"
        RenterStatus.ON_HOLD -> "On hold"
    }

    private fun holdLabel(reason: HoldReason) = when (reason) {
        HoldReason.MISSED_PAYMENTS -> "Missed payments"
        HoldReason.REPAIR -> "Repair"
        HoldReason.RETURNED -> "Returned"
        HoldReason.OTHER -> "Other"
    }

    private fun typeLabel(type: PaymentType) = when (type) {
        PaymentType.RENT -> "Rent"
        PaymentType.DEPOSIT -> "Deposit"
        PaymentType.ADVANCE -> "Advance"
    }

    private fun methodLabel(method: PaymentMethod) = when (method) {
        PaymentMethod.UPI -> "UPI"
        PaymentMethod.BANK_TRANSFER -> "Bank transfer"
        PaymentMethod.CASH -> "Cash"
    }
}
