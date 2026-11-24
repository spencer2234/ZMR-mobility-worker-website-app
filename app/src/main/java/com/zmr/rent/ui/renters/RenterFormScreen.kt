package com.zmr.rent.ui.renters

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zmr.rent.R
import com.zmr.rent.data.entity.CustomerType
import com.zmr.rent.data.entity.HoldReason
import com.zmr.rent.data.entity.RenterStatus
import com.zmr.rent.domain.IndianPhone
import com.zmr.rent.domain.Money
import com.zmr.rent.domain.RenterFieldError
import com.zmr.rent.ui.components.BigButton
import com.zmr.rent.ui.components.BigOutlinedButton
import com.zmr.rent.ui.components.ChoiceGrid
import com.zmr.rent.ui.components.DateField
import com.zmr.rent.ui.components.FormTextField
import com.zmr.rent.ui.components.PickerField
import com.zmr.rent.ui.components.ZmrTopBar
import com.zmr.rent.ui.labelRes
import java.time.format.TextStyle

/** The renter form with its top bar. Shared by the Add renter and Edit renter screens. */
@Composable
fun RenterFormScreen(
    title: String,
    saveLabel: String,
    viewModel: RenterFormViewModel,
    onBack: () -> Unit,
    onSaved: (renterId: Long) -> Unit,
    /** Shown when editing: opens the renter's first payment (deposit and advance). */
    onFirstPaymentClick: (() -> Unit)? = null,
    onRentPaymentClick: (() -> Unit)? = null,
    onDocumentsClick: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.savedRenterId) {
        state.savedRenterId?.let { renterId ->
            Toast.makeText(context, R.string.renter_saved, Toast.LENGTH_SHORT).show()
            onSaved(renterId)
        }
    }

    Scaffold(topBar = { ZmrTopBar(title = title, onBack = onBack) }) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding()
            .fillMaxSize()

        when {
            state.isLoading -> Box(contentModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.notFound -> Box(contentModifier.padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.renter_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            else -> RenterForm(state, saveLabel, viewModel, onFirstPaymentClick, onRentPaymentClick, onDocumentsClick, contentModifier)
        }
    }
}

@Composable
private fun RenterForm(
    state: RenterFormState,
    saveLabel: String,
    viewModel: RenterFormViewModel,
    onFirstPaymentClick: (() -> Unit)?,
    onRentPaymentClick: (() -> Unit)?,
    onDocumentsClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cities by viewModel.cities.collectAsStateWithLifecycle()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        state.rentStatus?.takeIf { it.pastDuePaise > 0 }?.let { rent ->
            Text(
                stringResource(R.string.past_due_detail, Money.format(rent.pastDuePaise), rent.delayDays),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        SectionLabel(R.string.field_customer_type)
        ChoiceGrid(
            options = CustomerType.entries,
            selected = state.customerType,
            onSelect = { type -> viewModel.edit { copy(customerType = type) } },
            label = { stringResource(it.labelRes()) },
            columns = 2,
        )
        FormTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            labelRes = R.string.field_name,
            errorRes = R.string.error_name_required.takeIf { RenterFieldError.NAME_REQUIRED in state.errors },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        FormTextField(
            value = state.phone,
            onValueChange = viewModel::onPhoneChange,
            labelRes = R.string.field_phone,
            errorRes = R.string.error_phone_invalid.takeIf { RenterFieldError.PHONE_INVALID in state.errors },
            prefix = IndianPhone.COUNTRY_CODE + " ",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
        )
        FormTextField(
            value = state.vehicleNumber,
            onValueChange = viewModel::onVehicleNumberChange,
            labelRes = R.string.field_vehicle,
            errorRes = when {
                RenterFieldError.VEHICLE_TAKEN in state.errors -> R.string.error_vehicle_taken
                RenterFieldError.VEHICLE_INVALID in state.errors -> R.string.error_vehicle_invalid
                else -> null
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )
        FormTextField(
            value = state.rcNumber,
            onValueChange = viewModel::onRcNumberChange,
            labelRes = R.string.field_rc_number,
            errorRes = null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
        )
        FormTextField(
            value = state.vehicleModel,
            onValueChange = { text -> viewModel.edit { copy(vehicleModel = text.take(40)) } },
            labelRes = R.string.field_vehicle_model,
            errorRes = null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        )
        PickerField(
            label = stringResource(R.string.field_city),
            selected = state.city,
            options = cities,
            optionLabel = { it },
            onPick = { city -> viewModel.edit { copy(city = city) } },
        )

        DateField(
            label = stringResource(R.string.field_start_date),
            date = state.startDate,
            onDateChange = { date -> date?.let(viewModel::onStartDateChange) },
        )
        Text(
            text = stringResource(
                R.string.rent_due_every,
                state.startDate.dayOfWeek.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0]),
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DateField(
            label = stringResource(if (state.customerType == CustomerType.LESSEE) R.string.field_lease_end else R.string.field_end_date),
            date = state.endDate,
            onDateChange = viewModel::onEndDateChange,
            noDateLabel = stringResource(R.string.no_end_date),
        )
        if (RenterFieldError.END_BEFORE_START in state.errors) ErrorText(R.string.error_end_before_start)

        FormTextField(
            value = state.weeklyRent,
            onValueChange = viewModel::onWeeklyRentChange,
            labelRes = R.string.field_weekly_rent,
            errorRes = R.string.error_rent_invalid.takeIf { RenterFieldError.RENT_INVALID in state.errors },
            prefix = "₹ ",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        )

        if (state.customerType == CustomerType.LESSEE) {
            RupeesField(state.residualValue, R.string.field_residual) { text -> viewModel.edit { copy(residualValue = text) } }
        }
        RupeesField(state.pending, R.string.field_pending) { text -> viewModel.edit { copy(pending = text) } }
        FormTextField(
            value = state.challans,
            onValueChange = { text -> viewModel.edit { copy(challans = text.filter { it in '0'..'9' }.take(3)) } },
            labelRes = R.string.field_challans,
            errorRes = null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        SectionLabel(R.string.field_status)
        ChoiceGrid(
            options = RenterStatus.entries,
            selected = state.status,
            onSelect = viewModel::onStatusChange,
            label = { stringResource(it.labelRes()) },
            columns = 2,
        )

        if (state.status == RenterStatus.ON_HOLD) {
            SectionLabel(R.string.field_hold_reason)
            ChoiceGrid(
                options = HoldReason.entries,
                selected = state.holdReason,
                onSelect = viewModel::onHoldReasonChange,
                label = { stringResource(it.labelRes()) },
                columns = 2,
            )
            if (RenterFieldError.HOLD_REASON_REQUIRED in state.errors) {
                ErrorText(R.string.error_hold_reason_required)
            }
        }

        if (state.saveFailed) ErrorText(R.string.error_save_failed)

        BigButton(
            label = saveLabel,
            icon = painterResource(R.drawable.ic_check),
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (onFirstPaymentClick != null) {
            BigOutlinedButton(
                label = stringResource(R.string.payment_title),
                icon = painterResource(R.drawable.ic_check),
                onClick = onFirstPaymentClick,
            )
        }
        onRentPaymentClick?.let {
            BigOutlinedButton(stringResource(R.string.action_record_rent), painterResource(R.drawable.ic_check), it)
        }
        onDocumentsClick?.let {
            BigOutlinedButton(stringResource(R.string.documents_title), painterResource(R.drawable.ic_report), it)
        }
    }
}

@Composable
private fun RupeesField(value: String, @StringRes labelRes: Int, onChange: (String) -> Unit) {
    FormTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it in '0'..'9' }.take(6)) },
        labelRes = labelRes,
        errorRes = null,
        prefix = "₹ ",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}


@Composable
private fun SectionLabel(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ErrorText(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
