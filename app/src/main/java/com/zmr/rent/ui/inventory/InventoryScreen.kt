package com.zmr.rent.ui.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import com.zmr.rent.data.entity.Vehicle
import com.zmr.rent.data.entity.VehicleWithRenter
import com.zmr.rent.data.repository.CityRepository
import com.zmr.rent.data.repository.RenterRepository
import com.zmr.rent.domain.VehicleNumber
import com.zmr.rent.ui.components.BigOutlinedButton
import com.zmr.rent.ui.components.FormTextField
import com.zmr.rent.ui.components.PickerField
import com.zmr.rent.ui.components.ZmrTopBar
import com.zmr.rent.ui.theme.TileToday
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Inventory: every vehicle, free or rented, filtered by city, and a form to add new vehicles. */
class InventoryViewModel(
    private val repository: RenterRepository,
    private val cityRepository: CityRepository,
) : ViewModel() {

    val vehicles = repository.observeInventory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val cities = cityRepository.observeCities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(Vehicle.DEFAULT_CITY))

    // Compose state, so the text boxes update on every keystroke.
    /** The city being viewed; null shows all cities. */
    var viewedCity by mutableStateOf<String?>(null)
    var newCity by mutableStateOf("")
    var vehicleCity by mutableStateOf(Vehicle.DEFAULT_CITY)
    var vehicleNumber by mutableStateOf("")
    var vehicleModel by mutableStateOf("")
    var rcNumber by mutableStateOf("")
    var addFailed by mutableStateOf(false)

    fun addVehicle() = viewModelScope.launch {
        addFailed = !repository.addVehicle(vehicleNumber, rcNumber, vehicleModel, vehicleCity)
        if (!addFailed) {
            vehicleNumber = ""
            vehicleModel = ""
            rcNumber = ""
        }
    }

    fun addCity() = viewModelScope.launch {
        cityRepository.addCity(newCity)
        newCity = ""
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { InventoryViewModel(container.renterRepository, container.cityRepository) }
        }
    }
}

@Composable
fun InventoryScreen(container: AppContainer, onBack: () -> Unit, onRenterClick: (Long) -> Unit) {
    val viewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.factory(container))
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val cities by viewModel.cities.collectAsStateWithLifecycle()
    val user by container.session.currentUser.collectAsStateWithLifecycle()
    val canAddCity = user?.canEditPaymentDetails == true
    val shown = vehicles.filter { viewModel.viewedCity == null || it.vehicle.city == viewModel.viewedCity }
    val caps = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false)
    val allCities = stringResource(R.string.inventory_all_cities)

    Scaffold(topBar = { ZmrTopBar(title = stringResource(R.string.main_inventory), onBack = onBack) }) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .fillMaxSize(),
        ) {
            item {
                PickerField(
                    label = stringResource(R.string.field_city),
                    selected = viewModel.viewedCity,
                    options = listOf<String?>(null) + cities,
                    optionLabel = { it ?: allCities },
                    onPick = { viewModel.viewedCity = it },
                )
            }
            if (shown.isEmpty()) {
                item { Text(stringResource(R.string.inventory_empty), style = MaterialTheme.typography.bodyLarge) }
            }
            items(shown, key = { it.vehicle.id }) { item -> VehicleCard(item, onRenterClick) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.action_add_vehicle), style = MaterialTheme.typography.titleMedium)
                    FormTextField(
                        value = viewModel.vehicleNumber,
                        onValueChange = { viewModel.vehicleNumber = VehicleNumber.normalize(it); viewModel.addFailed = false },
                        labelRes = R.string.field_vehicle,
                        errorRes = R.string.error_vehicle_exists.takeIf { viewModel.addFailed },
                        keyboardOptions = caps,
                    )
                    FormTextField(
                        value = viewModel.vehicleModel,
                        onValueChange = { viewModel.vehicleModel = it.take(40) },
                        labelRes = R.string.field_vehicle_model,
                        errorRes = null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    FormTextField(
                        value = viewModel.rcNumber,
                        onValueChange = { viewModel.rcNumber = VehicleNumber.normalize(it) },
                        labelRes = R.string.field_rc_number,
                        errorRes = null,
                        keyboardOptions = caps,
                    )
                    PickerField(
                        label = stringResource(R.string.field_city),
                        selected = viewModel.vehicleCity,
                        options = cities,
                        optionLabel = { it },
                        onPick = { viewModel.vehicleCity = it },
                    )
                    BigOutlinedButton(
                        label = stringResource(R.string.action_add_vehicle),
                        icon = painterResource(R.drawable.ic_check),
                        onClick = { viewModel.addVehicle() },
                    )
                    if (canAddCity) {
                        FormTextField(
                            value = viewModel.newCity,
                            onValueChange = { viewModel.newCity = it.take(40) },
                            labelRes = R.string.field_new_city,
                            errorRes = null,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        )
                        BigOutlinedButton(
                            label = stringResource(R.string.action_add_city),
                            icon = painterResource(R.drawable.ic_check),
                            onClick = { viewModel.addCity() },
                        )
                    }
                }
            }
        }
    }
}

/** One vehicle. A rented vehicle opens its renter when tapped. */
@Composable
private fun VehicleCard(item: VehicleWithRenter, onRenterClick: (Long) -> Unit) {
    val renterId = item.vehicle.renterId
    Surface(
        onClick = { renterId?.let(onRenterClick) },
        enabled = renterId != null,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.vehicle.vehicleNumber, style = MaterialTheme.typography.titleMedium)
            Text(
                listOf(item.vehicle.model, item.vehicle.city).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (item.vehicle.rcNumber.isNotEmpty()) {
                Text(stringResource(R.string.inventory_rc, item.vehicle.rcNumber), style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                text = item.renterName?.let { stringResource(R.string.inventory_rented_to, it) }
                    ?: stringResource(R.string.inventory_free),
                style = MaterialTheme.typography.bodyLarge,
                color = if (renterId == null) TileToday else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
