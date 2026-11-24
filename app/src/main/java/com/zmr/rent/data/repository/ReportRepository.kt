package com.zmr.rent.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.zmr.rent.data.dao.DocumentDao
import com.zmr.rent.data.dao.PaymentDao
import com.zmr.rent.data.dao.RenterDao
import com.zmr.rent.data.dao.UserDao
import com.zmr.rent.data.entity.PaymentType
import com.zmr.rent.data.entity.ReportSubmission
import com.zmr.rent.data.entity.UserRole
import com.zmr.rent.data.session.SessionManager
import com.zmr.rent.domain.IndiaTime
import com.zmr.rent.domain.RentLedger
import com.zmr.rent.domain.report.ExpectedMember
import com.zmr.rent.domain.report.PaymentRow
import com.zmr.rent.domain.report.RenterRow
import com.zmr.rent.domain.report.Report
import com.zmr.rent.domain.report.ReportKind
import com.zmr.rent.domain.report.ReportMerger
import com.zmr.rent.domain.report.ReportSource
import com.zmr.rent.domain.report.ReportWorkbook
import com.zmr.rent.domain.xlsx.XlsxReader
import com.zmr.rent.domain.xlsx.XlsxWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed interface ReadReportResult {
    data class Read(val report: Report, val fileName: String) : ReadReportResult

    /** The file is not a report made by this app, or it is damaged. */
    data class NotAReport(val fileName: String) : ReadReportResult
}

/** A report saved as an Excel file, ready to send. */
data class ReportFile(
    val uri: Uri,
    val fileName: String,
    val report: Report,
    /** A zip of the sender's customers' attached documents; null when there are none. */
    val documentsUri: Uri? = null,
)

/**
 * Builds the reports that are sent up the company: an employee's own report, a manager's team
 * report (their own data plus their employees' files) and the CEO's company report (all the
 * managers' files). The app only collects and sends; the figures are read in Excel.
 */
class ReportRepository(
    private val context: Context,
    private val renterDao: RenterDao,
    private val paymentDao: PaymentDao,
    private val userDao: UserDao,
    private val documentDao: DocumentDao,
    private val session: SessionManager,
    private val clock: Clock,
) {
    /** The logged-in person's own data: renters they added and payments they recorded. */
    suspend fun ownReport(): Report {
        val me = requireNotNull(session.currentUser.value) { "Not logged in" }
        val role = requireNotNull(me.role)
        val now = LocalDateTime.now(clock.withZone(IndiaTime.ZONE)).truncatedTo(ChronoUnit.SECONDS)
        val source = ReportSource(
            employeeCode = me.employeeCode,
            employeeName = me.name,
            role = role,
            area = me.area,
            managerName = when (role) {
                UserRole.EMPLOYEE -> me.reportsTo?.name.orEmpty()
                UserRole.MANAGER -> me.name
                UserRole.CEO -> ""
            },
            reportDate = now.toLocalDate(),
            generatedAt = now,
        )
        val allPayments = paymentDao.getAllWithRenter().map { it.payment }.groupBy { it.renterId }
        val documents = documentDao.getAll().groupBy { it.renterId }
        fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(IndiaTime.ZONE).toLocalDate()
        val renters = renterDao.getAll().filter { it.renter.createdBy == me.name }.map { item ->
            val paid = allPayments[item.renter.id].orEmpty()
            val deposits = paid.filter { it.type == PaymentType.DEPOSIT }
            val rent = RentLedger.status(
                startDate = item.renter.startDate,
                endDate = item.renter.endDate,
                weeklyRentPaise = item.renter.weeklyRentPaise,
                rentCreditPaise = paid.filter { it.type != PaymentType.DEPOSIT }.sumOf { it.amountPaise },
                pendingPaise = item.renter.pendingPaise,
                today = now.toLocalDate(),
            )
            RenterRow(
                employeeCode = me.employeeCode,
                name = item.renter.name,
                phone = item.renter.phone,
                vehicleNumber = item.vehicle.vehicleNumber,
                startDate = item.renter.startDate,
                rentDay = item.renter.rentDay,
                weeklyRentPaise = item.renter.weeklyRentPaise,
                status = item.renter.status,
                holdReason = item.renter.holdReason,
                addedBy = item.renter.createdBy,
                addedOn = day(item.renter.createdAt),
                customerType = item.renter.customerType,
                city = item.vehicle.city,
                vehicleModel = item.vehicle.model,
                rcNumber = item.vehicle.rcNumber,
                endDate = item.renter.endDate,
                residualValuePaise = item.renter.residualValuePaise,
                depositPaise = deposits.sumOf { it.amountPaise },
                depositDate = deposits.minOfOrNull { it.receivedAt }?.let(::day),
                expectedDate = rent.expectedDate,
                lastPaymentDate = paid.maxOfOrNull { it.receivedAt }?.let(::day),
                dueDate = rent.dueDate,
                delayDays = rent.delayDays,
                totalCashPaise = paid.sumOf { it.amountPaise },
                pendingPaise = item.renter.pendingPaise,
                pendingChallans = item.renter.pendingChallans,
                documents = documents[item.renter.id].orEmpty().joinToString("; ") { document ->
                    listOfNotNull(document.slot.name, document.type?.name).joinToString(": ") { label ->
                        label.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
                    }
                },
            )
        }
        val payments = paymentDao.getAllWithRenter().filter { it.payment.enteredBy == me.name }.map { item ->
            PaymentRow(
                employeeCode = me.employeeCode,
                receivedAt = Instant.ofEpochMilli(item.payment.receivedAt).atZone(IndiaTime.ZONE).toLocalDateTime()
                    .truncatedTo(ChronoUnit.MINUTES),
                renterName = item.renterName,
                vehicleNumber = item.vehicleNumber,
                type = item.payment.type,
                amountPaise = item.payment.amountPaise,
                method = item.payment.method,
                reference = item.payment.reference,
                enteredBy = item.payment.enteredBy,
            )
        }
        return Report(
            kind = ReportKind.EMPLOYEE,
            preparedByName = me.name,
            preparedByCode = me.employeeCode,
            preparedByRole = role,
            area = me.area,
            generatedAt = now,
            sources = listOf(source),
            renters = renters,
            payments = payments,
        )
    }

    /**
     * A manager's team report or the CEO's company report: their own data plus the [received]
     * files, listing anyone they added who has not sent a report.
     */
    suspend fun combinedReport(received: List<Report>): Report {
        val me = requireNotNull(session.currentUser.value)
        val kind = if (me.role == UserRole.CEO) ReportKind.COMPANY else ReportKind.TEAM
        val expected = userDao.getTeam(me.employeeCode).map { ExpectedMember(it.employeeCode, it.name, it.role, it.area) }
        // A manager's file stands for their whole team, so the CEO only expects the managers themselves.
        val own = ownReport().let { report ->
            // The CEO usually enters no renters; an empty CEO line would only clutter the company report.
            val empty = report.renters.isEmpty() && report.payments.isEmpty()
            if (me.role == UserRole.CEO && empty) report.copy(sources = emptyList()) else report
        }
        return ReportMerger.combine(kind, own, received, expected)
    }

    /** Reads a report file someone sent (picked from WhatsApp downloads, email attachments...). */
    suspend fun read(uri: Uri): ReadReportResult = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        try {
            val sheets = context.contentResolver.openInputStream(uri)?.use(XlsxReader::read)
                ?: return@withContext ReadReportResult.NotAReport(name)
            ReadReportResult.Read(ReportWorkbook.parse(sheets), name)
        } catch (e: XlsxReader.NotASpreadsheetException) {
            ReadReportResult.NotAReport(name)
        } catch (e: ReportWorkbook.NotAReportException) {
            ReadReportResult.NotAReport(name)
        } catch (e: IOException) {
            ReadReportResult.NotAReport(name)
        } catch (e: SecurityException) {
            ReadReportResult.NotAReport(name)
        }
    }

    /** Writes [report] to an Excel file and makes a link WhatsApp or an email app can read it through. */
    suspend fun save(report: Report): ReportFile {
        val file = writeFile(report)
        fun shareable(target: File) = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, target)
        return ReportFile(shareable(file), file.name, report, writeDocumentsZip(file)?.let(::shareable))
    }

    /** Zips the documents of the customers this person added, in one folder per customer. */
    private suspend fun writeDocumentsZip(reportFile: File): File? = withContext(Dispatchers.IO) {
        val me = session.currentUser.value?.name
        val mine = renterDao.getAll().filter { it.renter.createdBy == me }.associateBy { it.renter.id }
        val documents = documentDao.getAll().filter { it.renterId in mine && File(it.storedPath).exists() }
        if (documents.isEmpty()) return@withContext null
        File(reportFile.parentFile, reportFile.nameWithoutExtension + "-documents.zip").also { zipFile ->
            ZipOutputStream(zipFile.outputStream()).use { zip ->
                for (document in documents) {
                    val item = mine.getValue(document.renterId)
                    val folder = "${item.renter.name}-${item.vehicle.vehicleNumber}".replace(Regex("[^A-Za-z0-9-]+"), "-")
                    val stored = File(document.storedPath)
                    zip.putNextEntry(ZipEntry("$folder/${document.slot}.${stored.extension}"))
                    stored.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Remembers whose reports arrived, for the weekly submitted / pending view. */
    suspend fun recordSubmissions(received: List<Report>) {
        documentDao.saveSubmissions(
            received.flatMap { it.sources }.map {
                ReportSubmission(it.employeeCode, weekStart(it.reportDate), clock.millis())
            },
        )
    }

    fun observeSubmitted(weekStart: LocalDate) = documentDao.observeSubmitted(weekStart)

    fun currentWeekStart(): LocalDate = weekStart(IndiaTime.today(clock))

    private fun weekStart(date: LocalDate) = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Writes [report] to an Excel file in the app's private cache folder. */
    suspend fun writeFile(report: Report): File = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, FOLDER).apply { mkdirs() }
        // Old report files are not needed once sent; keep the cache small on low-storage phones.
        folder.listFiles()?.forEach { it.delete() }
        File(folder, fileName(report)).also { file ->
            file.outputStream().use { XlsxWriter.write(ReportWorkbook.sheets(report), it) }
        }
    }

    private fun fileName(report: Report): String {
        val who = "${report.preparedByCode}-${report.preparedByName}".replace(Regex("[^A-Za-z0-9-]+"), "-").trim('-')
        val kind = report.kind.title.replace(' ', '-')
        return "ZMR-$kind-$who-${report.generatedAt.toLocalDate()}.xlsx"
    }

    private fun displayName(uri: Uri): String = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (e: Exception) {
        null
    } ?: uri.lastPathSegment.orEmpty()

    companion object {
        const val FOLDER = "reports"
        const val AUTHORITY_SUFFIX = ".reports"
        const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
