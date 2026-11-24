package com.zmr.rent.ui.payment

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zmr.rent.AppContainer
import com.zmr.rent.R
import com.zmr.rent.data.entity.Payment
import com.zmr.rent.data.entity.PaymentMethod
import com.zmr.rent.data.repository.PaymentError
import com.zmr.rent.domain.IndiaTime
import com.zmr.rent.domain.Money
import com.zmr.rent.domain.PaymentSettings
import com.zmr.rent.ui.components.BigButton
import com.zmr.rent.ui.components.BigOutlinedButton
import com.zmr.rent.ui.components.ChoiceGrid
import com.zmr.rent.ui.components.FormTextField
import com.zmr.rent.ui.components.ZmrTopBar
import com.zmr.rent.ui.labelRes
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * First payment for a renter: shows what to collect, a UPI QR code or ZMR's bank details for
 * the renter to pay to, and records the payment once the worker has the reference number.
 */
@Composable
fun InitialPaymentScreen(container: AppContainer, renterId: Long, onDone: () -> Unit, forRent: Boolean = false) {
    val viewModel: InitialPaymentViewModel =
        viewModel(factory = InitialPaymentViewModel.factory(container, renterId, forRent))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.form.saved) {
        if (state.form.saved) {
            Toast.makeText(context, R.string.payment_saved, Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    Scaffold(
        topBar = { ZmrTopBar(title = stringResource(if (forRent) R.string.action_record_rent else R.string.payment_title), onBack = onDone) },
    ) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding()
            .fillMaxSize()

        val renter = state.renter
        when {
            state.isLoading -> Box(contentModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            renter == null -> Box(contentModifier.padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.renter_not_found), style = MaterialTheme.typography.bodyLarge)
            }

            else -> Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(renter.renter.name, style = MaterialTheme.typography.titleLarge)
                Text(renter.vehicle.vehicleNumber, style = MaterialTheme.typography.titleMedium)

                if (!forRent && state.recorded.isNotEmpty()) {
                    AlreadyRecorded(state.recorded, onDone)
                } else {
                    if (forRent) {
                        FormTextField(
                            value = state.form.amount ?: Money.toInputText(renter.renter.weeklyRentPaise),
                            onValueChange = viewModel::onAmountChange,
                            labelRes = R.string.field_rent_amount,
                            errorRes = R.string.error_amount_invalid.takeIf { PaymentError.AMOUNT_INVALID in state.form.errors },
                            prefix = "₹ ",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    } else {
                        AmountsCard(state.settings)
                    }
                    PaymentForm(state, viewModel, onCollectLater = onDone)
                }
            }
        }
    }
}

@Composable
private fun AmountsCard(settings: PaymentSettings) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AmountRow(R.string.payment_deposit, settings.depositPaise)
            AmountRow(R.string.payment_advance, settings.advancePaise)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            AmountRow(R.string.payment_total, settings.initialTotalPaise, bold = true)
        }
    }
}

@Composable
private fun AmountRow(@StringRes labelRes: Int, amountPaise: Long, bold: Boolean = false) {
    val style = if (bold) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(labelRes), style = style, modifier = Modifier.weight(1f))
        Text(Money.format(amountPaise), style = style)
    }
}

@Composable
private fun PaymentForm(state: InitialPaymentUiState, viewModel: InitialPaymentViewModel, onCollectLater: () -> Unit) {
    val form = state.form
    Text(stringResource(R.string.payment_how), style = MaterialTheme.typography.titleMedium)
    ChoiceGrid(
        options = PaymentMethod.entries,
        selected = form.method,
        onSelect = viewModel::onMethodChange,
        label = { stringResource(it.labelRes()) },
        columns = 3,
    )
    if (PaymentError.METHOD_REQUIRED in form.errors) ErrorText(R.string.error_method_required)

    when (form.method) {
        PaymentMethod.UPI -> UpiPanel(state)
        PaymentMethod.BANK_TRANSFER -> BankPanel(state.settings, state.amountPaise)
        PaymentMethod.CASH -> InfoText(R.string.payment_cash_hint)
        null -> Unit
    }

    if (form.method != null) {
        FormTextField(
            value = form.reference,
            onValueChange = viewModel::onReferenceChange,
            labelRes = when (form.method) {
                PaymentMethod.UPI -> R.string.field_reference_upi
                PaymentMethod.BANK_TRANSFER -> R.string.field_reference_bank
                PaymentMethod.CASH -> R.string.field_reference_cash
            },
            errorRes = R.string.error_reference_required.takeIf { PaymentError.REFERENCE_REQUIRED in form.errors },
            hintRes = R.string.reference_hint_upi.takeIf { form.method == PaymentMethod.UPI },
            keyboardOptions = KeyboardOptions(
                // A UPI transaction ID is 12 digits; bank UTRs and receipt numbers can have letters.
                keyboardType = if (form.method == PaymentMethod.UPI) KeyboardType.Number else KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
        )
    }

    if (form.saveFailed) ErrorText(R.string.error_save_failed)

    BigButton(
        label = stringResource(R.string.action_payment_received),
        icon = painterResource(R.drawable.ic_check),
        onClick = viewModel::save,
        enabled = !form.isSaving,
        modifier = Modifier.padding(top = 8.dp),
    )
    BigOutlinedButton(
        label = stringResource(R.string.action_collect_later),
        icon = painterResource(R.drawable.ic_arrow_back),
        onClick = onCollectLater,
    )
}

@Composable
private fun UpiPanel(state: InitialPaymentUiState) {
    val link = state.upiLink
    if (link == null) {
        ErrorText(R.string.payment_upi_missing)
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        QrCodeImage(
            content = link,
            description = stringResource(R.string.method_upi),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 320.dp),
        )
        Text(
            text = stringResource(R.string.payment_upi_payee, state.settings.upiId, state.settings.payeeName),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    Text(
        stringResource(R.string.payment_scan_hint, Money.format(state.amountPaise)),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun BankPanel(settings: PaymentSettings, amountPaise: Long) {
    if (!settings.hasBankAccount) {
        ErrorText(R.string.payment_bank_missing)
        return
    }
    Text(
        text = stringResource(R.string.payment_bank_hint, Money.format(amountPaise)),
        style = MaterialTheme.typography.bodyLarge,
    )
    // Selectable, so the worker can copy the account number into a message for the renter.
    SelectionContainer {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BankLine(R.string.bank_account_name, settings.bankAccountName)
                BankLine(R.string.bank_account_number, settings.bankAccountNumber)
                BankLine(R.string.bank_ifsc, settings.bankIfsc)
                if (settings.bankName.isNotEmpty()) BankLine(R.string.bank_name, settings.bankName)
            }
        }
    }
}

@Composable
private fun BankLine(@StringRes labelRes: Int, value: String) {
    Column {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AlreadyRecorded(payments: List<Payment>, onDone: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale) }
    Text(stringResource(R.string.payment_already_recorded), style = MaterialTheme.typography.bodyLarge)
    payments.forEach { payment ->
        Surface(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(
                        R.string.payment_recorded_line,
                        stringResource(payment.type.labelRes()),
                        Money.format(payment.amountPaise),
                        stringResource(payment.method.labelRes()),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.payment_recorded_detail,
                        payment.reference,
                        payment.enteredBy,
                        Instant.ofEpochMilli(payment.enteredAt).atZone(IndiaTime.ZONE).format(formatter),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    BigButton(
        label = stringResource(R.string.action_done),
        icon = painterResource(R.drawable.ic_check),
        onClick = onDone,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun InfoText(@StringRes textRes: Int) {
    Text(stringResource(textRes), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun ErrorText(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
    )
}
