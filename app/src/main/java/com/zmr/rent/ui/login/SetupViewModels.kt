package com.zmr.rent.ui.login

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zmr.rent.AppContainer
import com.zmr.rent.data.repository.AccountError
import com.zmr.rent.data.repository.AccountResult
import com.zmr.rent.data.repository.AuthRepository
import com.zmr.rent.domain.Invite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CeoSetupState(
    /** Employee ID of the upper-management person picked. */
    val employeeCode: String? = null,
    val pin: String = "",
    val errors: Set<AccountError> = emptySet(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

/** Set up as upper management: pick a name built into the app and type its PIN. Success logs in. */
class CeoSetupViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val leaders = authRepository.leaders

    private val _state = MutableStateFlow(CeoSetupState())
    val state: StateFlow<CeoSetupState> = _state.asStateFlow()

    fun onPick(employeeCode: String) = _state.update { it.copy(employeeCode = employeeCode, errors = emptySet()) }

    fun onPinChange(value: String) = _state.update { it.copy(pin = value, errors = emptySet()) }

    fun save() {
        val form = _state.value
        val code = form.employeeCode ?: return
        if (form.isSaving) return
        _state.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            val result = try {
                authRepository.setUpLeader(code, form.pin)
            } catch (e: SQLiteException) {
                null
            }
            _state.update {
                when (result) {
                    is AccountResult.Invalid -> it.copy(isSaving = false, errors = result.errors)
                    is AccountResult.SignedIn -> it.copy(isSaving = false)
                    null -> it.copy(isSaving = false, saveFailed = true)
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CeoSetupViewModel(container.authRepository) }
        }
    }
}

data class InviteSetupState(
    val inviteText: String = "",
    /** The invite found in [inviteText], shown so the person can check it is theirs. */
    val invite: Invite? = null,
    val pin: String = "",
    val confirmPin: String = "",
    val errors: Set<AccountError> = emptySet(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

/** Set up this phone from an invite code sent by a manager or the CEO. */
class InviteSetupViewModel(
    private val authRepository: AuthRepository,
    pendingInvite: MutableStateFlow<String?>,
) : ViewModel() {

    private val _state = MutableStateFlow(InviteSetupState())
    val state: StateFlow<InviteSetupState> = _state.asStateFlow()

    init {
        // Opened from an invite link: the code is filled in already.
        pendingInvite.getAndUpdate { null }?.let(::onInviteTextChange)
    }

    fun onInviteTextChange(value: String) = _state.update {
        val text = value.take(MAX_INVITE_TEXT)
        it.copy(inviteText = text, invite = authRepository.readInvite(text), errors = emptySet())
    }

    fun onPinChange(value: String) = _state.update { it.copy(pin = value, errors = emptySet()) }

    fun onConfirmPinChange(value: String) = _state.update { it.copy(confirmPin = value, errors = emptySet()) }

    fun save() {
        val form = _state.value
        if (form.isSaving) return
        _state.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            val result = try {
                authRepository.setUpFromInvite(form.inviteText, form.pin, form.confirmPin)
            } catch (e: SQLiteException) {
                null
            }
            _state.update {
                when (result) {
                    is AccountResult.Invalid -> it.copy(isSaving = false, errors = result.errors)
                    is AccountResult.SignedIn -> it.copy(isSaving = false)
                    null -> it.copy(isSaving = false, saveFailed = true)
                }
            }
        }
    }

    companion object {
        private const val MAX_INVITE_TEXT = 4_000

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { InviteSetupViewModel(container.authRepository, container.pendingInvite) }
        }
    }
}
