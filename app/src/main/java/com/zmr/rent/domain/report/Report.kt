package com.zmr.rent.domain.report

import com.zmr.rent.data.entity.CustomerType
import com.zmr.rent.data.entity.HoldReason
import com.zmr.rent.data.entity.PaymentMethod
import com.zmr.rent.data.entity.PaymentType
import com.zmr.rent.data.entity.RenterStatus
import com.zmr.rent.data.entity.UserRole
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** Who made a report file. Each level combines the files from the level below. */
enum class ReportKind(val title: String) {
    /** One employee's own data, sent to their manager. */
    EMPLOYEE("Employee report"),

    /** A manager's own data plus their employees' reports, sent to the CEO. */
    TEAM("Team report"),

    /** The CEO's combination of all managers' team reports. */
    COMPANY("Company report"),
}

/** One person whose data is in a report, and when they made their part of it. */
data class ReportSource(
    val employeeCode: String,
    val employeeName: String,
    val role: UserRole,
    val area: String,
    val managerName: String,
    /** The day the figures in the summary are for (in India). */
    val reportDate: LocalDate,
    val generatedAt: LocalDateTime,
)

/** A renter as it appears in a report, under the employee who added them. */
data class RenterRow(
    val employeeCode: String,
    val name: String,
    val phone: String,
    val vehicleNumber: String,
    val startDate: LocalDate,
    val rentDay: DayOfWeek,
    val weeklyRentPaise: Long,
    val status: RenterStatus,
    val holdReason: HoldReason?,
    val addedBy: String,
    val addedOn: LocalDate,
    val customerType: CustomerType = CustomerType.RENTER,
    val city: String = "",
    val vehicleModel: String = "",
    val rcNumber: String = "",
    val endDate: LocalDate? = null,
    val residualValuePaise: Long? = null,
    val depositPaise: Long = 0,
    val depositDate: LocalDate? = null,
    val expectedDate: LocalDate? = null,
    val lastPaymentDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val delayDays: Long = 0,
    val totalCashPaise: Long = 0,
    val pendingPaise: Long = 0,
    val pendingChallans: Int = 0,
    /** Which documents are attached, for example "Photograph; ID proof: PAN card". */
    val documents: String = "",
)

/** A payment as it appears in a report, under the employee who recorded it. */
data class PaymentRow(
    val employeeCode: String,
    /** Indian Standard Time. */
    val receivedAt: LocalDateTime,
    val renterName: String,
    val vehicleNumber: String,
    val type: PaymentType,
    val amountPaise: Long,
    val method: PaymentMethod,
    val reference: String,
    val enteredBy: String,
)

/** A team member who should have sent a report; listed as missing if they did not. */
data class ExpectedMember(
    val employeeCode: String,
    val name: String,
    val role: UserRole,
    val area: String,
)

/** Everything in one report file. */
data class Report(
    val kind: ReportKind,
    val preparedByName: String,
    val preparedByCode: String,
    val preparedByRole: UserRole,
    val area: String,
    val generatedAt: LocalDateTime,
    val sources: List<ReportSource>,
    val renters: List<RenterRow>,
    val payments: List<PaymentRow>,
    /** Members of the sender's team who sent nothing. Shown in the summary, not read back. */
    val missing: List<ExpectedMember> = emptyList(),
)

/** The figures in one summary line. */
data class SourceTotals(
    val renters: Int,
    val active: Int,
    val onHold: Int,
    val dueOnReportDay: Int,
    val rentDueOnReportDayPaise: Long,
    val collectedPaise: Long,
    val cashPaise: Long,
    val upiPaise: Long,
    val bankPaise: Long,
    val depositsPaise: Long,
    val advancesPaise: Long,
    val rentPaise: Long,
) {
    operator fun plus(other: SourceTotals) = SourceTotals(
        renters + other.renters,
        active + other.active,
        onHold + other.onHold,
        dueOnReportDay + other.dueOnReportDay,
        rentDueOnReportDayPaise + other.rentDueOnReportDayPaise,
        collectedPaise + other.collectedPaise,
        cashPaise + other.cashPaise,
        upiPaise + other.upiPaise,
        bankPaise + other.bankPaise,
        depositsPaise + other.depositsPaise,
        advancesPaise + other.advancesPaise,
        rentPaise + other.rentPaise,
    )

    companion object {
        val ZERO = SourceTotals(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

        /** Totals for one person: renters they added, and money they recorded on their report day. */
        fun of(source: ReportSource, renters: List<RenterRow>, payments: List<PaymentRow>): SourceTotals {
            val own = renters.filter { it.employeeCode == source.employeeCode }
            val due = own.filter { it.status == RenterStatus.ACTIVE && it.rentDay == source.reportDate.dayOfWeek }
            val paid = payments.filter {
                it.employeeCode == source.employeeCode && it.receivedAt.toLocalDate() == source.reportDate
            }
            fun sum(rows: List<PaymentRow>) = rows.sumOf { it.amountPaise }
            return SourceTotals(
                renters = own.size,
                active = own.count { it.status == RenterStatus.ACTIVE },
                onHold = own.count { it.status == RenterStatus.ON_HOLD },
                dueOnReportDay = due.size,
                rentDueOnReportDayPaise = due.sumOf { it.weeklyRentPaise },
                collectedPaise = sum(paid),
                cashPaise = sum(paid.filter { it.method == PaymentMethod.CASH }),
                upiPaise = sum(paid.filter { it.method == PaymentMethod.UPI }),
                bankPaise = sum(paid.filter { it.method == PaymentMethod.BANK_TRANSFER }),
                depositsPaise = sum(paid.filter { it.type == PaymentType.DEPOSIT }),
                advancesPaise = sum(paid.filter { it.type == PaymentType.ADVANCE }),
                rentPaise = sum(paid.filter { it.type == PaymentType.RENT }),
            )
        }
    }
}

/** Combines a person's own report with the reports they received from their team. */
object ReportMerger {

    /**
     * When the same employee's report arrives more than once (for example yesterday's and
     * today's), only the newest is kept, so nothing is counted twice.
     */
    fun combine(
        kind: ReportKind,
        own: Report,
        received: List<Report>,
        expected: List<ExpectedMember>,
    ): Report {
        val newest = mutableMapOf<String, Pair<ReportSource, Report>>()
        for (report in listOf(own) + received) {
            for (source in report.sources) {
                val current = newest[source.employeeCode]
                if (current == null || source.generatedAt > current.first.generatedAt) {
                    newest[source.employeeCode] = source to report
                }
            }
        }
        val chosen = newest.values.sortedWith(
            compareBy({ it.first.area.lowercase() }, { it.first.managerName.lowercase() }, { it.first.employeeName.lowercase() }),
        )
        return own.copy(
            kind = kind,
            sources = chosen.map { it.first },
            renters = chosen.flatMap { (source, report) -> report.renters.filter { it.employeeCode == source.employeeCode } },
            payments = chosen.flatMap { (source, report) -> report.payments.filter { it.employeeCode == source.employeeCode } },
            missing = expected.filter { member -> member.employeeCode !in newest }.sortedBy { it.name.lowercase() },
        )
    }
}
