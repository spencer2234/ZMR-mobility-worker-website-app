package com.zmr.rent.ui.renters

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zmr.rent.AppContainer
import com.zmr.rent.R

/** Edit renter: the same form as Add renter, pre-filled with the saved details. */
@Composable
fun EditRenterScreen(
    container: AppContainer,
    renterId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onFirstPaymentClick: () -> Unit,
    onRentPaymentClick: () -> Unit,
    onDocumentsClick: () -> Unit,
) {
    RenterFormScreen(
        title = stringResource(R.string.edit_renter_title),
        saveLabel = stringResource(R.string.action_save_changes),
        viewModel = viewModel(factory = RenterFormViewModel.factory(container, renterId)),
        onBack = onBack,
        onSaved = { onSaved() },
        onFirstPaymentClick = onFirstPaymentClick,
        onRentPaymentClick = onRentPaymentClick,
        onDocumentsClick = onDocumentsClick,
    )
}
