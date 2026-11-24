package com.zmr.rent.ui.renters

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zmr.rent.AppContainer
import com.zmr.rent.R
import com.zmr.rent.data.entity.RenterDocument
import com.zmr.rent.data.repository.DocumentRepository
import com.zmr.rent.domain.DocumentSlot
import com.zmr.rent.domain.DocumentType
import com.zmr.rent.ui.components.BigButton
import com.zmr.rent.ui.components.BigOutlinedButton
import com.zmr.rent.ui.components.PickerField
import com.zmr.rent.ui.components.ZmrTopBar
import com.zmr.rent.ui.labelRes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DocumentsViewModel(private val renterId: Long, private val repository: DocumentRepository) : ViewModel() {

    val documents = repository.observe(renterId).map { list -> list.associateBy { it.slot } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The document type picked for each slot before its file is attached. */
    val chosenTypes = mutableStateMapOf<DocumentSlot, DocumentType>()
    var attachFailed by mutableStateOf(false)
        private set

    fun attach(slot: DocumentSlot, uri: Uri) = viewModelScope.launch {
        val type = chosenTypes[slot] ?: documents.value[slot]?.type ?: slot.choices.firstOrNull()
        attachFailed = !repository.attach(renterId, slot, type, uri)
    }

    companion object {
        fun factory(container: AppContainer, renterId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { DocumentsViewModel(renterId, container.documentRepository) }
        }
    }
}

/** The six optional customer documents. Each can be an image or a PDF. */
@Composable
fun DocumentsScreen(container: AppContainer, renterId: Long, onBack: () -> Unit, onContinue: () -> Unit) {
    val viewModel: DocumentsViewModel = viewModel(factory = DocumentsViewModel.factory(container, renterId))
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    var attachingSlot by rememberSaveable { mutableStateOf<DocumentSlot?>(null) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = attachingSlot
        if (uri != null && slot != null) viewModel.attach(slot, uri)
    }

    Scaffold(topBar = { ZmrTopBar(stringResource(R.string.documents_title), onBack) }) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.documents_intro), style = MaterialTheme.typography.bodyLarge)
            if (viewModel.attachFailed) {
                Text(
                    stringResource(R.string.error_document_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            DocumentSlot.entries.forEach { slot ->
                val saved = documents[slot]
                val type = viewModel.chosenTypes[slot] ?: saved?.type ?: slot.choices.firstOrNull()
                DocumentCard(slot, type, saved, onTypeChange = { viewModel.chosenTypes[slot] = it }) {
                    attachingSlot = slot
                    pickFile.launch(arrayOf("image/*", "application/pdf"))
                }
            }
            BigButton(stringResource(R.string.action_continue), painterResource(R.drawable.ic_check), onContinue)
        }
    }
}

@Composable
private fun DocumentCard(
    slot: DocumentSlot,
    type: DocumentType?,
    saved: RenterDocument?,
    onTypeChange: (DocumentType) -> Unit,
    onAttach: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(slot.labelRes()), style = MaterialTheme.typography.titleMedium)
            if (type != null) {
                PickerField(
                    label = stringResource(R.string.document_type),
                    selected = type,
                    options = slot.choices,
                    optionLabel = { stringResource(it.labelRes()) },
                    onPick = onTypeChange,
                )
            }
            if (type == DocumentType.AADHAAR_CARD) {
                Text(
                    stringResource(R.string.document_aadhaar_masked),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            saved?.let { Text(stringResource(R.string.document_attached, it.fileName), style = MaterialTheme.typography.bodyLarge) }
            BigOutlinedButton(
                label = stringResource(if (saved == null) R.string.action_add_file else R.string.action_replace_file),
                icon = painterResource(R.drawable.ic_report),
                onClick = onAttach,
            )
        }
    }
}
