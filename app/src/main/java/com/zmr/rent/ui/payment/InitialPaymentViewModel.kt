package com.zmr.rent.ui.payment

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zmr.rent.AppContainer
import com.zmr.rent.data.entity.Payment
import com.zmr.rent.data.entity.PaymentMethod
import com.zmr.rent.data.entity.PaymentType
import com.zmr.rent.data.entity.RenterWithVehicle
import com.zmr.rent.data.repository.PaymentError
import com.zmr.rent.data.repository.PaymentRepository
import com.zmr.rent.data.repository.RecordPaymentResult
import com.zmr.rent.data.repository.RenterRepository
import com.zmr.rent.domain.Money
import com.zmr.rent.domain.PaymentSettings
import com.zmr.rent.domain.Upi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InitialPaymentForm(
    val method: PaymentMethod? = null,
    val reference: String = "",
    /** Rent payments only: the rupees being paid, as typed. */
    val amount: String? = null,
    val errors: Set<PaymentError> = emptySet(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
)

data class InitialPaymentUiState(
    val isLoading: Boolean = true,
    val renter: RenterWithVehicle? = null,
    val settings: PaymentSettings = PaymentSettings(),
    /** Deposit and advance rows already saved for this renter; empty until collected. */
    val recorded: List<Payment> = emptyList(),
    val form: InitialPaymentForm = InitialPaymentForm(),
    /** True for a weekly-rent payment; false for the first payment (deposit and advance). */
    val forRent: Boolean = false,
) {
    /** What the renter should pay now. */
    val amountPaise: Long
        get() = if (forRent) {
            form.amount?.let(Money::parseRupees) ?: renter?.renter?.weeklyRentPaise ?: 0
        } else {
            settings.initialTotalPaise
        }

    val notFound: Boolean get() = !isLoading && renter == null

    /** The "upi://pay" link for ZMR's QR code. */
    val upiLink: String?
        get() = if (settings.hasUpi) Upi.qrLink(settings.upiId, settings.payeeName) else null
}

/**
 * The step after adding a renter: collect the deposit and rent advance by UPI QR code, bank
 * transfer or cash, then record it with the transaction reference.
 */
class InitialPaymentViewModel(
    private val renterId: Long,
    private val forRent: Boolean,
    renterRepository: RenterRepository,
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    private val form = MutableStateFlow(InitialPaymentForm())

    val state: StateFlow<InitialPaymentUiState> = combine(
        flow { emit(renterRepository.getRenter(renterId)) },
        paymentRepository.observeSettings(),
        paymentRepository.observePayments(renterId),
        form,
    ) { renter, settings, payments, form ->
        InitialPaymentUiState(
            isLoading = false,
            renter = renter,
            settings = settings,
            recorded = payments.filter { it.type == PaymentType.DEPOSIT || it.type == PaymentType.ADVANCE },
            form = form,
            forRent = forRent,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InitialPaymentUiState(forRent = forRent))

    fun onMethodChange(method: PaymentMethod) = form.update {
        // A UPI reference is not a cash receipt number, so start again when the method changes.
        it.copy(
            method = method,
            reference = if (method == it.method) it.reference else "",
            errors = it.errors - PaymentError.METHOD_REQUIRED - PaymentError.REFERENCE_REQUIRED,
        )
    }

    fun onReferenceChange(value: String) = form.update {
        it.copy(
            reference = value.take(PaymentRepository.MAX_REFERENCE_LENGTH),
            errors = it.errors - PaymentError.REFERENCE_REQUIRED,
        )
    }

    fun onAmountChange(value: String) = form.update { it.copy(amount = value.filter { char -> char in '0'..'9' }.take(6)) }

    fun save() {
        val current = form.value
        if (current.isSaving || current.saved) return
        val settings = state.value.settings
        form.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            val result = try {
                if (forRent) {
                    paymentRepository.recordRent(renterId, current.method, current.reference, state.value.amountPaise)
                } else {
                    paymentRepository.recordInitialPayment(
                        renterId = renterId,
                        method = current.method,
                        reference = current.reference,
                        depositPaise = settings.depositPaise,
                        advancePaise = settings.advancePaise,
                    )
                }
            } catch (e: SQLiteException) {
                null
            }
            form.update {
                when (result) {
                    RecordPaymentResult.Saved -> it.copy(isSaving = false, saved = true)
                    is RecordPaymentResult.Invalid -> it.copy(isSaving = false, errors = result.errors)
                    // Already saved or renter gone: the screen shows that from the data itself.
                    RecordPaymentResult.AlreadyRecorded, RecordPaymentResult.RenterNotFound ->
                        it.copy(isSaving = false)
                    null -> it.copy(isSaving = false, saveFailed = true)
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, renterId: Long, forRent: Boolean): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                InitialPaymentViewModel(renterId, forRent, container.renterRepository, container.paymentRepository)
            }
        }
    }
}
