package com.zmr.rent.ui.renters

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zmr.rent.AppContainer
import com.zmr.rent.data.entity.CustomerType
import com.zmr.rent.data.entity.HoldReason
import com.zmr.rent.data.entity.Renter
import com.zmr.rent.data.entity.RenterStatus
import com.zmr.rent.data.entity.Vehicle
import com.zmr.rent.data.repository.CityRepository
import com.zmr.rent.data.repository.PaymentRepository
import com.zmr.rent.data.repository.RenterRepository
import com.zmr.rent.data.repository.SaveRenterResult
import com.zmr.rent.domain.IndiaTime
import com.zmr.rent.domain.IndianPhone
import com.zmr.rent.domain.Money
import com.zmr.rent.domain.RentLedger
import com.zmr.rent.domain.RentStatus
import com.zmr.rent.domain.RenterDetails
import com.zmr.rent.domain.RenterFieldError
import com.zmr.rent.domain.RenterValidator
import com.zmr.rent.domain.VehicleNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class RenterFormState(
    val startDate: LocalDate,
    /** Whole rupees as typed, digits only. */
    val weeklyRent: String,
    val name: String = "",
    val phone: String = "",
    val vehicleNumber: String = "",
    /** Registration certificate number; optional. */
    val rcNumber: String = "",
    /** Agreed return date; null means open-ended. */
    val endDate: LocalDate? = null,
    val customerType: CustomerType = CustomerType.RENTER,
    val vehicleModel: String = "",
    val city: String = Vehicle.DEFAULT_CITY,
    /** Whole rupees as typed. */
    val residualValue: String = "",
    val pending: String = "",
    val challans: String = "",
    /** Past-due figures of a saved renter, shown beside their name. */
    val rentStatus: RentStatus? = null,
    val status: RenterStatus = RenterStatus.ACTIVE,
    val holdReason: HoldReason? = null,
    val errors: Set<RenterFieldError> = emptySet(),
    /** True while an existing renter is being read from the database. */
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Set once the renter is saved; the screen then moves on. */
    val savedRenterId: Long? = null,
)

/**
 * Drives the renter form for both adding ([renterId] is null) and editing.
 *
 * A new renter starts with today (in India) as the start date and the standard weekly rent,
 * so in the usual case only name, phone and vehicle number are typed.
 */
class RenterFormViewModel(
    private val renterId: Long?,
    private val repository: RenterRepository,
    private val paymentRepository: PaymentRepository,
    cityRepository: CityRepository,
    private val clock: Clock,
) : ViewModel() {

    val cities = cityRepository.observeCities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(Vehicle.DEFAULT_CITY))

    private val _state = MutableStateFlow(
        RenterFormState(
            startDate = IndiaTime.today(clock),
            weeklyRent = Money.toInputText(Renter.DEFAULT_WEEKLY_RENT_PAISE),
            isLoading = renterId != null,
        ),
    )
    val state: StateFlow<RenterFormState> = _state.asStateFlow()

    init {
        if (renterId != null) viewModelScope.launch { load(renterId) }
    }

    private suspend fun load(renterId: Long) {
        val existing = repository.getRenter(renterId)
        val rentCredit = paymentRepository.observeRentCredits().first()[renterId] ?: 0
        _state.update { current ->
            if (existing == null) {
                current.copy(isLoading = false, notFound = true)
            } else {
                val renter = existing.renter
                current.copy(
                    isLoading = false,
                    name = renter.name,
                    phone = renter.phone,
                    vehicleNumber = existing.vehicle.vehicleNumber,
                    rcNumber = existing.vehicle.rcNumber,
                    startDate = renter.startDate,
                    endDate = renter.endDate,
                    weeklyRent = Money.toInputText(renter.weeklyRentPaise),
                    status = renter.status,
                    holdReason = renter.holdReason,
                    customerType = renter.customerType,
                    vehicleModel = existing.vehicle.model,
                    city = existing.vehicle.city,
                    residualValue = renter.residualValuePaise?.let(Money::toInputText).orEmpty(),
                    pending = renter.pendingPaise.takeIf { it > 0 }?.let(Money::toInputText).orEmpty(),
                    challans = renter.pendingChallans.takeIf { it > 0 }?.toString().orEmpty(),
                    rentStatus = RentLedger.status(
                        renter.startDate,
                        renter.endDate,
                        renter.weeklyRentPaise,
                        rentCredit,
                        renter.pendingPaise,
                        IndiaTime.today(clock),
                    ),
                )
            }
        }
    }

    // Editing a field clears that field's error, so the red text goes away as soon as the
    // worker starts fixing it.

    fun onNameChange(value: String) = _state.update {
        it.copy(
            name = value.take(RenterValidator.MAX_NAME_LENGTH),
            errors = it.errors - RenterFieldError.NAME_REQUIRED,
        )
    }

    fun onPhoneChange(value: String) = _state.update {
        it.copy(
            phone = IndianPhone.normalize(value),
            errors = it.errors - RenterFieldError.PHONE_INVALID,
        )
    }

    fun onVehicleNumberChange(value: String) = _state.update {
        it.copy(
            vehicleNumber = VehicleNumber.normalize(value),
            errors = it.errors - RenterFieldError.VEHICLE_INVALID - RenterFieldError.VEHICLE_TAKEN,
        )
    }

    fun onStartDateChange(value: LocalDate) = _state.update {
        it.copy(startDate = value, errors = it.errors - RenterFieldError.END_BEFORE_START)
    }

    fun onEndDateChange(value: LocalDate?) = _state.update {
        it.copy(endDate = value, errors = it.errors - RenterFieldError.END_BEFORE_START)
    }

    fun onRcNumberChange(value: String) = _state.update { it.copy(rcNumber = VehicleNumber.normalize(value)) }

    fun onWeeklyRentChange(value: String) = _state.update {
        it.copy(
            weeklyRent = value.filter { char -> char in '0'..'9' }.take(MAX_RENT_DIGITS),
            errors = it.errors - RenterFieldError.RENT_INVALID,
        )
    }

    fun onStatusChange(value: RenterStatus) = _state.update {
        it.copy(
            status = value,
            holdReason = it.holdReason.takeIf { value == RenterStatus.ON_HOLD },
            errors = it.errors - RenterFieldError.HOLD_REASON_REQUIRED,
        )
    }

    fun onHoldReasonChange(value: HoldReason) = _state.update {
        it.copy(holdReason = value, errors = it.errors - RenterFieldError.HOLD_REASON_REQUIRED)
    }

    /** For the simple boxes that have no error of their own. */
    fun edit(change: RenterFormState.() -> RenterFormState) = _state.update(change)

    fun save() {
        val form = _state.value
        if (form.isSaving || form.savedRenterId != null) return

        val details = RenterValidator.clean(
            RenterDetails(
                name = form.name,
                phone = form.phone,
                vehicleNumber = form.vehicleNumber,
                rcNumber = form.rcNumber,
                startDate = form.startDate,
                endDate = form.endDate,
                weeklyRentPaise = Money.parseRupees(form.weeklyRent),
                status = form.status,
                holdReason = form.holdReason,
                customerType = form.customerType,
                residualValuePaise = Money.parseRupees(form.residualValue),
                pendingPaise = Money.parseRupees(form.pending) ?: 0,
                pendingChallans = form.challans.toIntOrNull() ?: 0,
                vehicleModel = form.vehicleModel,
                city = form.city,
            ),
        )
        val errors = RenterValidator.validate(details)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }

        _state.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            val result = try {
                if (renterId == null) {
                    repository.addRenter(details)
                } else {
                    repository.updateRenter(renterId, details)
                }
            } catch (e: SQLiteException) {
                // For example the phone's storage is full. Keep the form so nothing typed is lost.
                null
            }
            _state.update {
                when (result) {
                    is SaveRenterResult.Saved -> it.copy(isSaving = false, savedRenterId = result.renterId)
                    is SaveRenterResult.Invalid -> it.copy(isSaving = false, errors = result.errors)
                    SaveRenterResult.NotFound -> it.copy(isSaving = false, notFound = true)
                    null -> it.copy(isSaving = false, saveFailed = true)
                }
            }
        }
    }

    companion object {
        /** Up to Rs 9,99,999, matching Money.MAX_AMOUNT_PAISE. */
        private const val MAX_RENT_DIGITS = 6

        fun factory(container: AppContainer, renterId: Long?): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    RenterFormViewModel(
                        renterId,
                        container.renterRepository,
                        container.paymentRepository,
                        container.cityRepository,
                        container.clock,
                    )
                }
            }
    }
}
