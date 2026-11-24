package com.zmr.rent.ui.team

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zmr.rent.AppContainer
import com.zmr.rent.data.entity.UserRole
import com.zmr.rent.data.repository.AccountError
import com.zmr.rent.data.repository.AuthRepository
import com.zmr.rent.data.repository.InviteResult
import com.zmr.rent.data.repository.PaymentRepository
import com.zmr.rent.data.repository.PersonDetails
import com.zmr.rent.data.session.SessionManager
import com.zmr.rent.domain.Invite
import com.zmr.rent.ui.login.PersonForm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddMemberState(
    /** The kind of account being added. */
    val role: UserRole,
    /** A manager's employees work in the manager's area; shown, not typed. */
    val fixedArea: String,
    val person: PersonForm = PersonForm(),
    val errors: Set<AccountError> = emptySet(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Set once the invite is made; the screen then offers to send it. */
    val created: Invite? = null,
    val code: String = "",
)

/** Adds an employee or manager login and sends them an invite code. */
class AddMemberViewModel(
    role: UserRole,
    session: SessionManager,
    private val authRepository: AuthRepository,
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    private val me = session.currentUser.value

    private val _state = MutableStateFlow(
        AddMemberState(role = role, fixedArea = if (role == UserRole.EMPLOYEE) me?.area.orEmpty() else ""),
    )
    val state: StateFlow<AddMemberState> = _state.asStateFlow()

    fun onPersonChange(person: PersonForm) = _state.update { it.copy(person = person, errors = emptySet()) }

    fun createInvite() {
        val form = _state.value
        if (form.isSaving || form.created != null) return
        _state.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            val person = form.person
            val result = try {
                authRepository.createInvite(
                    form.role,
                    PersonDetails(person.name, person.employeeCode, person.phone, person.email, person.area),
                    payment = paymentRepository.observeSettings().first(),
                )
            } catch (e: SQLiteException) {
                null
            }
            _state.update {
                when (result) {
                    is InviteResult.Created -> it.copy(isSaving = false, created = result.invite, code = result.code)
                    is InviteResult.Invalid -> it.copy(isSaving = false, errors = result.errors)
                    null -> it.copy(isSaving = false, saveFailed = true)
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, role: UserRole): ViewModelProvider.Factory = viewModelFactory {
            initializer { AddMemberViewModel(role, container.session, container.authRepository, container.paymentRepository) }
        }
    }
}
