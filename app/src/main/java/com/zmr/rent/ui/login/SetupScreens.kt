package com.zmr.rent.ui.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zmr.rent.AppContainer
import com.zmr.rent.R
import com.zmr.rent.data.repository.AccountError
import com.zmr.rent.domain.IndianPhone
import com.zmr.rent.ui.components.BigButton
import com.zmr.rent.ui.components.ChoiceGrid
import com.zmr.rent.ui.components.ZmrTopBar
import com.zmr.rent.ui.labelRes

/** Set up as upper management. Saving logs in, and the app moves on to the home screen by itself. */
@Composable
fun CeoSetupScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: CeoSetupViewModel = viewModel(factory = CeoSetupViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    SetupScaffold(title = stringResource(R.string.setup_ceo_title), onBack = onBack) {
        if (viewModel.leaders.isEmpty()) {
            Text(stringResource(R.string.setup_no_leaders), style = MaterialTheme.typography.bodyLarge)
            return@SetupScaffold
        }
        Text(stringResource(R.string.setup_ceo_intro), style = MaterialTheme.typography.bodyLarge)
        ChoiceGrid(
            options = viewModel.leaders,
            selected = viewModel.leaders.find { it.contact.employeeCode == state.employeeCode },
            onSelect = { viewModel.onPick(it.contact.employeeCode) },
            label = { it.contact.name },
            columns = 1,
        )
        if (state.employeeCode == null) return@SetupScaffold
        PinField(
            value = state.pin,
            onValueChange = viewModel::onPinChange,
            labelRes = R.string.field_pin,
            errorRes = state.errors.messageFor(AccountError.PIN_FORMAT, AccountError.PIN_WRONG),
        )
        listOf(AccountError.CEO_EXISTS, AccountError.NAME_TAKEN, AccountError.CODE_TAKEN)
            .filter { it in state.errors }
            .forEach { ErrorText(it.messageRes()) }
        if (state.saveFailed) ErrorText(R.string.error_save_failed)
        BigButton(
            label = stringResource(R.string.action_setup),
            icon = painterResource(R.drawable.ic_check),
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Set up with an invite code: paste the message, check whose account it is, choose a PIN. */
@Composable
fun InviteSetupScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: InviteSetupViewModel = viewModel(factory = InviteSetupViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    SetupScaffold(title = stringResource(R.string.setup_invite_title), onBack = onBack) {
        Text(stringResource(R.string.setup_invite_intro), style = MaterialTheme.typography.bodyLarge)
        val inviteError = AccountError.INVITE_INVALID in state.errors ||
            (state.inviteText.isNotBlank() && state.invite == null)
        OutlinedTextField(
            value = state.inviteText,
            onValueChange = viewModel::onInviteTextChange,
            label = { Text(stringResource(R.string.field_invite)) },
            isError = inviteError,
            supportingText = if (inviteError) {
                { Text(stringResource(R.string.error_invite_invalid)) }
            } else {
                null
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        )

        val invite = state.invite
        if (invite != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.invite_for, invite.member.name, invite.member.employeeCode),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.invite_role, stringResource(invite.role.labelRes())),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (invite.area.isNotEmpty()) {
                        Text(stringResource(R.string.invite_area, invite.area), style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        stringResource(
                            R.string.invite_reports_to,
                            "${invite.reportsTo.name}, ${IndianPhone.format(invite.reportsTo.phone)}",
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            listOf(AccountError.NAME_TAKEN, AccountError.CODE_TAKEN)
                .filter { it in state.errors }
                .forEach { ErrorText(it.messageRes()) }

            Text(stringResource(R.string.setup_choose_pin), style = MaterialTheme.typography.bodyLarge)
            PinFields(
                pin = state.pin,
                confirmPin = state.confirmPin,
                errors = state.errors,
                onPinChange = viewModel::onPinChange,
                onConfirmPinChange = viewModel::onConfirmPinChange,
            )
            if (state.saveFailed) ErrorText(R.string.error_save_failed)
            BigButton(
                label = stringResource(R.string.action_setup),
                icon = painterResource(R.drawable.ic_check),
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SetupScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = { ZmrTopBar(title = title, onBack = onBack) }) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun PinFields(
    pin: String,
    confirmPin: String,
    errors: Set<AccountError>,
    onPinChange: (String) -> Unit,
    onConfirmPinChange: (String) -> Unit,
) {
    PinField(
        value = pin,
        onValueChange = onPinChange,
        labelRes = R.string.field_pin,
        errorRes = errors.messageFor(AccountError.PIN_FORMAT),
        imeAction = ImeAction.Next,
    )
    PinField(
        value = confirmPin,
        onValueChange = onConfirmPinChange,
        labelRes = R.string.field_pin_confirm,
        errorRes = errors.messageFor(AccountError.PIN_MISMATCH),
    )
}

@Composable
private fun ErrorText(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
    )
}
