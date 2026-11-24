package com.zmr.rent.ui.renters

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zmr.rent.AppContainer
import com.zmr.rent.data.entity.RenterStatus
import com.zmr.rent.data.entity.RenterWithVehicle
import com.zmr.rent.data.repository.PaymentRepository
import com.zmr.rent.data.repository.RenterRepository
import com.zmr.rent.domain.IndiaTime
import com.zmr.rent.domain.RentLedger
import com.zmr.rent.domain.RenterSearch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

data class RentersUiState(
    val isLoading: Boolean = true,
    /** Renters matching the current search, sorted by name. */
    val renters: List<RenterWithVehicle> = emptyList(),
    /** False until the first renter has been added; used to pick the right empty message. */
    val hasAnyRenters: Boolean = false,
    /** Renter id to the amount past due today; renters who owe nothing are left out. */
    val pastDue: Map<Long, Long> = emptyMap(),
)

class RentersViewModel(repository: RenterRepository, paymentRepository: PaymentRepository, clock: Clock) : ViewModel() {

    /** Held as Compose state (not a Flow) so the search box updates on every keystroke. */
    var query by mutableStateOf("")
        private set

    val state: StateFlow<RentersUiState> =
        combine(repository.observeRenters(), paymentRepository.observeRentCredits(), snapshotFlow { query }) { renters, credits, query ->
            RentersUiState(
                isLoading = false,
                renters = RenterSearch.filter(renters.filter { it.renter.status == RenterStatus.ACTIVE }, query),
                hasAnyRenters = renters.isNotEmpty(),
                pastDue = renters.associate { (renter) ->
                    renter.id to RentLedger.status(
                        renter.startDate,
                        renter.endDate,
                        renter.weeklyRentPaise,
                        credits[renter.id] ?: 0,
                        renter.pendingPaise,
                        IndiaTime.today(clock),
                    ).pastDuePaise
                }.filterValues { it > 0 },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RentersUiState())

    fun onQueryChange(value: String) {
        query = value
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { RentersViewModel(container.renterRepository, container.paymentRepository, container.clock) }
        }
    }
}
