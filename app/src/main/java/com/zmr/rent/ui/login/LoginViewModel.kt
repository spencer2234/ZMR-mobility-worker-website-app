package com.zmr.rent.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zmr.rent.AppContainer
import com.zmr.rent.data.entity.UserSummary
import com.zmr.rent.data.repository.AuthRepository
import com.zmr.rent.data.repository.LoginResult
import com.zmr.rent.domain.Pin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginError {
    CHOOSE_USER,
    PIN_FORMAT,
    WRONG_PIN,
}

data class LoginFormState(
    val selectedUserId: Long? = null,
    val pin: String = "",
    val error: LoginError? = null,
    val isChecking: Boolean = false,
)

/**
 * Login: pick your name from the people registered on this phone, then type your PIN.
 * Navigation after a successful login is driven by the session, not by this screen.
 */
class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    /** Null while the list is loading. */
    val users: StateFlow<List<UserSummary>?> = authRepository.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether this phone already has a CEO; the "Set up as CEO" button is hidden once it does. */
    val hasCeo: StateFlow<Boolean> = authRepository.observeHasCeo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _form = MutableStateFlow(LoginFormState())
    val form: StateFlow<LoginFormState> = _form.asStateFlow()

    fun onUserSelected(userId: Long) = _form.update { it.copy(selectedUserId = userId, error = null) }

    fun onPinChange(value: String) = _form.update { it.copy(pin = value, error = null) }

    fun logIn() {
        val form = _form.value
        if (form.isChecking) return
        val userId = form.selectedUserId
        when {
            userId == null -> _form.update { it.copy(error = LoginError.CHOOSE_USER) }
            !Pin.isValid(form.pin) -> _form.update { it.copy(error = LoginError.PIN_FORMAT) }
            else -> {
                _form.update { it.copy(isChecking = true) }
                viewModelScope.launch {
                    val result = authRepository.logIn(userId, form.pin)
                    _form.update {
                        when (result) {
                            is LoginResult.LoggedIn -> LoginFormState()
                            LoginResult.WrongPin -> it.copy(isChecking = false, pin = "", error = LoginError.WRONG_PIN)
                            LoginResult.UnknownUser ->
                                it.copy(isChecking = false, selectedUserId = null, error = LoginError.CHOOSE_USER)
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(container.authRepository) }
        }
    }
}
