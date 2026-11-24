package com.zmr.rent.ui.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zmr.rent.AppContainer
import com.zmr.rent.R
import com.zmr.rent.data.entity.UserRole
import com.zmr.rent.data.repository.ReportFile
import com.zmr.rent.data.repository.ReportRepository
import com.zmr.rent.domain.IndianPhone
import com.zmr.rent.ui.components.BigButton
import com.zmr.rent.ui.components.BigOutlinedButton
import com.zmr.rent.ui.components.ZmrTopBar
import com.zmr.rent.ui.labelRes
import com.zmr.rent.util.Sending
import java.time.format.DateTimeFormatter

private val SHORT_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

/**
 * Send report (employee), Team report (manager) or Company report (CEO). The figures are never
 * shown here: the app builds the Excel file and sends it by WhatsApp or email.
 */
@Composable
fun ReportScreen(container: AppContainer, combine: Boolean, onBack: () -> Unit) {
    val viewModel: ReportViewModel = viewModel(factory = ReportViewModel.factory(container, combine))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addFiles(uris)
    }

    val title = stringResource(if (combine) R.string.action_send_manager_data else R.string.main_send_data)
    Scaffold(topBar = { ZmrTopBar(title = title, onBack = onBack) }) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (state.combines) {
                Text(
                    stringResource(
                        if (state.me?.role == UserRole.CEO) R.string.report_add_intro_ceo else R.string.report_add_intro_manager,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                BigOutlinedButton(
                    label = stringResource(R.string.action_add_files),
                    icon = painterResource(R.drawable.ic_report),
                    onClick = {
                        // WhatsApp and email apps do not always label Excel files, so allow both.
                        pickFiles.launch(arrayOf(ReportRepository.XLSX_MIME, "application/octet-stream"))
                    },
                )
                Text(
                    stringResource(R.string.report_files_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.skipped.forEach { name ->
                    Text(
                        stringResource(R.string.report_skipped, name),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            val file = state.file
            when {
                state.isWorking -> Working()
                file == null -> Text(
                    stringResource(R.string.error_save_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    if (state.combines) WhoIsIncluded(file, state.me?.employeeCode.orEmpty())
                    SendButtons(state, file)
                }
            }
        }
    }
}

@Composable
private fun Working() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.report_making), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Who is in the combined report and who has not sent theirs: names only, no figures. */
@Composable
private fun WhoIsIncluded(file: ReportFile, myCode: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.report_included_header), style = MaterialTheme.typography.titleMedium)
            val you = stringResource(R.string.report_you)
            file.report.sources.forEach { source ->
                val code = if (source.employeeCode == myCode) "${source.employeeCode}, $you" else source.employeeCode
                Text(
                    stringResource(R.string.report_included_line, source.employeeName, code, source.generatedAt.format(SHORT_DATE_TIME)),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (file.report.missing.isNotEmpty()) {
                Text(
                    stringResource(R.string.report_missing_header),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                file.report.missing.forEach { member ->
                    Text(
                        stringResource(R.string.report_missing_line, member.name, member.employeeCode),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun SendButtons(state: ReportUiState, file: ReportFile) {
    val context = LocalContext.current
    val me = state.me
    // Employees send to their manager and managers to the CEO. The CEO sends wherever they choose,
    // with their own email filled in so the report is kept in their inbox.
    val to = me?.reportsTo
    val emailTo = to?.email ?: me?.email
    val report = file.report
    val message = stringResource(
        R.string.report_message,
        report.kind.title,
        report.preparedByName,
        report.preparedByCode,
        report.generatedAt.format(SHORT_DATE),
    )
    val chooser = stringResource(R.string.send_with)
    val attachments = listOfNotNull(file.uri, file.documentsUri)

    Text(stringResource(R.string.report_ready, file.fileName), style = MaterialTheme.typography.bodyLarge)
    if (to != null) {
        val role = stringResource((if (me.role == UserRole.EMPLOYEE) UserRole.MANAGER else UserRole.CEO).labelRes())
        Text(
            stringResource(
                R.string.report_goes_to,
                "${to.name} ($role)",
                listOf(IndianPhone.format(to.phone), to.email).filter { it.isNotEmpty() }.joinToString(", "),
            ),
            style = MaterialTheme.typography.titleSmall,
        )
    } else if (me?.role != UserRole.CEO) {
        Text(stringResource(R.string.report_no_recipient), style = MaterialTheme.typography.bodyLarge)
    }

    BigButton(
        label = stringResource(R.string.action_send_whatsapp),
        icon = painterResource(R.drawable.ic_check),
        onClick = { Sending.fileByWhatsApp(context, attachments, to?.phone, message, chooser) },
        modifier = Modifier.padding(top = 8.dp),
    )
    BigOutlinedButton(
        label = stringResource(R.string.action_send_email),
        icon = painterResource(R.drawable.ic_check),
        onClick = { Sending.fileByEmail(context, attachments, emailTo, message, message, chooser) },
    )
}
