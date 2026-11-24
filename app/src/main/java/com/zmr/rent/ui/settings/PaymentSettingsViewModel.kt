package com.zmr.rent.ui.settings

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zmr.rent.AppContainer
import com.zmr.rent.data.repository.PaymentRepository
import com.zmr.rent.data.repository.SaveSettingsResult
import com.zmr.rent.domain.Money
import com.zmr.rent.domain.PaymentSettings
import com.zmr.rent.domain.PaymentSettingsError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Payment details form, with amounts held as typed whole rupees. */
data class PaymentSettingsFormState(
    val isLoading: Boolean = true,
    val upiId: String = "",
    val payeeName: String = "",
    val bankAccountName: String = "",
    val bankAccountNumber: String = "",
    val bankIfsc: String = "",
    val bankName: String = "",
    val deposit: String = "",
    val advance: String = "",
    val errors: Set<PaymentSettingsError> = emptySet(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: Boolean = false,
)

/** Founder only: ZMR's UPI ID and bank account, and the deposit and advance amounts. */
class PaymentSettingsViewModel(private val paymentRepository: PaymentRepository) : ViewModel() {

    private val _state = MutableStateFlow(PaymentSettingsFormState())
    val state: StateFlow<PaymentSettingsFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = paymentRepository.observeSettings().first()
            _state.update {
                it.copy(
                    isLoading = false,
                    upiId = saved.upiId,
                    payeeName = saved.payeeName,
                    bankAccountName = saved.bankAccountName,
                    bankAccountNumber = saved.bankAccountNumber,
                    bankIfsc = saved.bankIfsc,
                    bankName = saved.bankName,
                    deposit = Money.toInputText(saved.depositPaise),
                    advance = Money.toInputText(saved.advancePaise),
                )
            }
        }
    }

    // Editing a field clears its error.

    fun onUpiIdChange(value: String) = _state.update {
        it.copy(upiId = value.trim().take(100), errors = it.errors - PaymentSettingsError.UPI_ID_INVALID)
    }

    fun onPayeeNameChange(value: String) = _state.update {
        it.copy(payeeName = value.take(60), errors = it.errors - PaymentSettingsError.PAYEE_NAME_REQUIRED)
    }

    fun onBankAccountNameChange(value: String) = _state.update {
        it.copy(bankAccountName = value.take(60), errors = it.errors - PaymentSettingsError.ACCOUNT_NAME_REQUIRED)
    }

    fun onBankAccountNumberChange(value: String) = _state.update {
        it.copy(
            bankAccountNumber = value.filter { char -> char in '0'..'9' }.take(18),
            errors = it.errors - PaymentSettingsError.ACCOUNT_NUMBER_INVALID,
        )
    }

    fun onBankIfscChange(value: String) = _state.update {
        it.copy(
            bankIfsc = value.uppercase().filter { char -> char in 'A'..'Z' || char in '0'..'9' }.take(11),
            errors = it.errors - PaymentSettingsError.IFSC_INVALID,
        )
    }

    fun onBankNameChange(value: String) = _state.update { it.copy(bankName = value.take(60)) }

    fun onDepositChange(value: String) = _state.update {
        it.copy(deposit = value.digitsOnly(), errors = it.errors - PaymentSettingsError.DEPOSIT_INVALID)
    }

    fun onAdvanceChange(value: String) = _state.update {
        it.copy(advance = value.digitsOnly(), errors = it.errors - PaymentSettingsError.ADVANCE_INVALID)
    }

    fun save() {
        val form = _state.value
        if (form.isSaving || form.isLoading) return
        val settings = PaymentSettings(
            upiId = form.upiId,
            payeeName = form.payeeName,
            bankAccountName = form.bankAccountName,
            bankAccountNumber = form.bankAccountNumber,
            bankIfsc = form.bankIfsc,
            bankName = form.bankName,
            // An empty or unreadable amount becomes -1, which the validator reports as missing.
            depositPaise = Money.parseRupees(form.deposit) ?: -1,
            advancePaise = Money.parseRupees(form.advance) ?: -1,
        )
        _state.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            val result = try {
                paymentRepository.saveSettings(settings)
            } catch (e: SQLiteException) {
                null
            }
            _state.update {
                when (result) {
                    SaveSettingsResult.Saved -> it.copy(isSaving = false, saved = true)
                    is SaveSettingsResult.Invalid -> it.copy(isSaving = false, errors = result.errors)
                    null -> it.copy(isSaving = false, saveFailed = true)
                }
            }
        }
    }

    private fun String.digitsOnly() = filter { it in '0'..'9' }.take(6)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PaymentSettingsViewModel(container.paymentRepository) }
        }
    }
}
