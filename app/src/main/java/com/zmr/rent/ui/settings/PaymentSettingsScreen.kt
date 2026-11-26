package com.zmr.rent.ui.settings

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zmr.rent.AppContainer
import com.zmr.rent.R
import com.zmr.rent.domain.PaymentSettingsError
import com.zmr.rent.ui.components.BigButton
import com.zmr.rent.ui.components.FormTextField
import com.zmr.rent.ui.components.ZmrTopBar

/** Founder only: where renters send money, and the first payment amounts. */
@Composable
fun PaymentSettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: PaymentSettingsViewModel = viewModel(factory = PaymentSettingsViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.saved) {
        if (state.saved) {
            Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    Scaffold(
        topBar = { ZmrTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
    ) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding()
            .fillMaxSize()

        if (state.isLoading) {
            Box(contentModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        fun error(error: PaymentSettingsError, @StringRes message: Int): Int? = message.takeIf { error in state.errors }
        val text = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
        val plain = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        )
        val number = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = contentModifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.settings_intro), style = MaterialTheme.typography.bodyLarge)

            SectionTitle(R.string.settings_upi_section)
            FormTextField(
                value = state.upiId,
                onValueChange = viewModel::onUpiIdChange,
                labelRes = R.string.field_upi_id,
                errorRes = error(PaymentSettingsError.UPI_ID_INVALID, R.string.error_upi_invalid),
                keyboardOptions = plain,
            )
            FormTextField(
                value = state.payeeName,
                onValueChange = viewModel::onPayeeNameChange,
                labelRes = R.string.field_payee_name,
                errorRes = error(PaymentSettingsError.PAYEE_NAME_REQUIRED, R.string.error_payee_name_required),
                keyboardOptions = text,
            )

            SectionTitle(R.string.settings_bank_section)
            FormTextField(
                value = state.bankAccountName,
                onValueChange = viewModel::onBankAccountNameChange,
                labelRes = R.string.bank_account_name,
                errorRes = error(PaymentSettingsError.ACCOUNT_NAME_REQUIRED, R.string.error_account_name_required),
                keyboardOptions = text,
            )
            FormTextField(
                value = state.bankAccountNumber,
                onValueChange = viewModel::onBankAccountNumberChange,
                labelRes = R.string.bank_account_number,
                errorRes = error(PaymentSettingsError.ACCOUNT_NUMBER_INVALID, R.string.error_account_invalid),
                keyboardOptions = number,
            )
            FormTextField(
                value = state.bankIfsc,
                onValueChange = viewModel::onBankIfscChange,
                labelRes = R.string.bank_ifsc,
                errorRes = error(PaymentSettingsError.IFSC_INVALID, R.string.error_ifsc_invalid),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
            )
            FormTextField(
                value = state.bankName,
                onValueChange = viewModel::onBankNameChange,
                labelRes = R.string.bank_name,
                errorRes = null,
                keyboardOptions = text,
            )

            SectionTitle(R.string.settings_amounts_section)
            FormTextField(
                value = state.deposit,
                onValueChange = viewModel::onDepositChange,
                labelRes = R.string.payment_deposit,
                errorRes = error(PaymentSettingsError.DEPOSIT_INVALID, R.string.error_amount_invalid),
                prefix = "₹ ",
                keyboardOptions = number,
            )
            FormTextField(
                value = state.advance,
                onValueChange = viewModel::onAdvanceChange,
                labelRes = R.string.payment_advance,
                errorRes = error(PaymentSettingsError.ADVANCE_INVALID, R.string.error_amount_invalid),
                prefix = "₹ ",
                keyboardOptions = number,
            )

            SectionTitle(R.string.settings_links_section)
            val link = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next)
            FormTextField(
                value = state.appDownloadLink,
                onValueChange = viewModel::onAppDownloadLinkChange,
                labelRes = R.string.field_app_download_link,
                errorRes = error(PaymentSettingsError.DOWNLOAD_LINK_INVALID, R.string.error_link_invalid),
                keyboardOptions = link,
                hintRes = R.string.hint_app_download_link,
            )
            FormTextField(
                value = state.invitePageLink,
                onValueChange = viewModel::onInvitePageLinkChange,
                labelRes = R.string.field_invite_page_link,
                errorRes = error(PaymentSettingsError.INVITE_PAGE_INVALID, R.string.error_link_invalid),
                keyboardOptions = link.copy(imeAction = ImeAction.Done),
            )

            if (state.saveFailed) {
                Text(
                    stringResource(R.string.error_save_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            BigButton(
                label = stringResource(R.string.action_save_settings),
                icon = painterResource(R.drawable.ic_check),
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}
