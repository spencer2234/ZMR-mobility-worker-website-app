package com.zmr.rent.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import com.zmr.rent.domain.DocumentSlot
import com.zmr.rent.domain.DocumentType
import java.time.LocalDate

/** A file attached to a customer. Attaching to the same slot again replaces it. */
@Entity(
    tableName = "renter_documents",
    primaryKeys = ["renterId", "slot"],
    foreignKeys = [ForeignKey(entity = Renter::class, parentColumns = ["id"], childColumns = ["renterId"])],
)
data class RenterDocument(
    val renterId: Long,
    val slot: DocumentSlot,
    /** Which accepted document it is; null for the photograph and driving licence. */
    val type: DocumentType?,
    val fileName: String,
    /** Path inside the app's private storage. */
    val storedPath: String,
    val addedBy: String,
    val addedAt: Long,
)

/** Records that a person's report arrived for the week starting on Monday [weekStart]. */
@Entity(tableName = "report_submissions", primaryKeys = ["employeeCode", "weekStart"])
data class ReportSubmission(
    val employeeCode: String,
    val weekStart: LocalDate,
    val submittedAt: Long,
)
