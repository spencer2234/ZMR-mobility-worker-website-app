package com.zmr.rent.ui.renters

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zmr.rent.AppContainer
import com.zmr.rent.R
import com.zmr.rent.data.entity.RenterWithVehicle
import com.zmr.rent.domain.IndianPhone
import com.zmr.rent.domain.Money
import com.zmr.rent.ui.components.BigButton
import com.zmr.rent.ui.components.ZmrTopBar
import com.zmr.rent.ui.labelRes
import com.zmr.rent.ui.shortLabelRes
import java.time.format.DateTimeFormatter

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

/**
 * Renters: everyone currently using a vehicle, with their dates, plate and RC number. Searchable;
 * tapping a renter opens them for editing. Renters on hold are reached from the Inventory.
 */
@Composable
fun RentersScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onRenterClick: (Long) -> Unit,
    onAddRenterClick: () -> Unit,
) {
    val viewModel: RentersViewModel = viewModel(factory = RentersViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { ZmrTopBar(title = stringResource(R.string.renters_title), onBack = onBack) },
    ) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding()
            .fillMaxSize()

        when {
            state.isLoading -> Box(contentModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            !state.hasAnyRenters -> NoRentersYet(onAddRenterClick, contentModifier)

            else -> Column(contentModifier) {
                SearchField(
                    query = viewModel.query,
                    onQueryChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
                )
                if (state.renters.isEmpty()) {
                    Text(
                        text = stringResource(R.string.renters_no_match, viewModel.query.trim()),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.renters, key = { it.renter.id }) { item ->
                            RenterCard(item, state.pastDue[item.renter.id], onClick = { onRenterClick(item.renter.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.renters_search_hint)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_clear_search),
                    )
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier,
    )
}

@Composable
private fun RenterCard(item: RenterWithVehicle, pastDuePaise: Long?, onClick: () -> Unit) {
    val renter = item.renter
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(renter.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                pastDuePaise?.let {
                    Text(
                        stringResource(R.string.past_due_short, Money.format(it)),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(stringResource(renter.customerType.labelRes()), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            val vehicle = item.vehicle
            Text(
                text = if (vehicle.rcNumber.isEmpty()) vehicle.vehicleNumber
                else stringResource(R.string.renter_vehicle_line, vehicle.vehicleNumber, vehicle.rcNumber),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(IndianPhone.format(renter.phone), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    R.string.renter_dates_line,
                    renter.startDate.format(DATE),
                    renter.endDate?.format(DATE) ?: stringResource(R.string.no_end_date),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    R.string.renter_rent_line,
                    stringResource(renter.rentDay.shortLabelRes()),
                    Money.format(renter.weeklyRentPaise),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoRentersYet(onAddRenterClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.renters_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.renters_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        BigButton(
            label = stringResource(R.string.main_register_customer),
            icon = painterResource(R.drawable.ic_person_add),
            onClick = onAddRenterClick,
        )
    }
}
