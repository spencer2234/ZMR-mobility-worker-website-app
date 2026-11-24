package com.zmr.rent.ui.reports

import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zmr.rent.AppContainer
import com.zmr.rent.data.repository.ReadReportResult
import com.zmr.rent.data.repository.ReportFile
import com.zmr.rent.data.repository.ReportRepository
import com.zmr.rent.data.session.CurrentUser
import com.zmr.rent.data.session.SessionManager
import com.zmr.rent.domain.report.Report
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class ReportUiState(
    val me: CurrentUser?,
    /** True for "Send manager data": the person's own data plus the report files they add. */
    val combines: Boolean,
    val isWorking: Boolean = true,
    /** The Excel file to send; rebuilt each time more received files are added. */
    val file: ReportFile? = null,
    /** Report files added by a manager or the CEO. Only who sent them is shown, never the figures. */
    val received: List<Report> = emptyList(),
    /** Names of picked files that were not ZMR reports. */
    val skipped: List<String> = emptyList(),
    val failed: Boolean = false,
)

/**
 * Builds the Excel report and keeps it ready to send. An employee's report holds their own
 * renters and payments; a manager's and the CEO's also hold the report files they add.
 */
class ReportViewModel(
    combine: Boolean,
    session: SessionManager,
    private val reportRepository: ReportRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportUiState(me = session.currentUser.value, combines = combine))
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { rebuild() }
    }

    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(isWorking = true, failed = false) }
        viewModelScope.launch {
            val added = mutableListOf<Report>()
            val skipped = mutableListOf<String>()
            for (uri in uris) {
                when (val result = reportRepository.read(uri)) {
                    is ReadReportResult.Read -> added += result.report
                    is ReadReportResult.NotAReport -> skipped += result.fileName
                }
            }
            reportRepository.recordSubmissions(added)
            _state.update { it.copy(received = it.received + added, skipped = skipped) }
            rebuild()
        }
    }

    private suspend fun rebuild() {
        _state.update { it.copy(isWorking = true, failed = false) }
        val current = _state.value
        val file = try {
            val report = if (current.combines) {
                reportRepository.combinedReport(current.received)
            } else {
                reportRepository.ownReport()
            }
            reportRepository.save(report)
        } catch (e: SQLiteException) {
            null
        } catch (e: IOException) {
            null
        }
        _state.update { it.copy(isWorking = false, file = file, failed = file == null) }
    }

    companion object {
        fun factory(container: AppContainer, combine: Boolean): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportViewModel(combine, container.session, container.reportRepository) }
        }
    }
}
