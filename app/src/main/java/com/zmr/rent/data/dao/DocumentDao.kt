package com.zmr.rent.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zmr.rent.data.entity.RenterDocument
import com.zmr.rent.data.entity.ReportSubmission
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM renter_documents WHERE renterId = :renterId")
    fun observeForRenter(renterId: Long): Flow<List<RenterDocument>>

    @Query("SELECT * FROM renter_documents")
    suspend fun getAll(): List<RenterDocument>

    @Upsert
    suspend fun save(document: RenterDocument)

    @Query("SELECT employeeCode FROM report_submissions WHERE weekStart = :weekStart")
    fun observeSubmitted(weekStart: java.time.LocalDate): Flow<List<String>>

    @Upsert
    suspend fun saveSubmissions(submissions: List<ReportSubmission>)
}
