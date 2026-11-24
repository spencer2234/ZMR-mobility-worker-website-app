package com.zmr.rent.ui.addrenter

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zmr.rent.AppContainer
import com.zmr.rent.R
import com.zmr.rent.ui.renters.RenterFormScreen
import com.zmr.rent.ui.renters.RenterFormViewModel

/**
 * Add renter. Saving moves on to the First payment step (deposit and rent advance).
 */
@Composable
fun AddRenterScreen(
    container: AppContainer,
    onBack: () -> Unit,
    /** Called with the new renter's id, to go on to their first payment. */
    onSaved: (renterId: Long) -> Unit,
) {
    RenterFormScreen(
        title = stringResource(R.string.add_renter_title),
        saveLabel = stringResource(R.string.action_save_renter),
        viewModel = viewModel(factory = RenterFormViewModel.factory(container, renterId = null)),
        onBack = onBack,
        onSaved = onSaved,
    )
}
