package com.zmr.rent.data.session

import com.zmr.rent.data.entity.UserRole
import com.zmr.rent.domain.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Who is using the app right now. Their name is stamped on every record they create. */
interface Session {
    val userName: String
}

/** The person logged in, or a guest. */
data class CurrentUser(
    val name: String,
    /** Null for a guest. */
    val role: UserRole?,
    /** Null for a guest. */
    val userId: Long?,
    val employeeCode: String = "",
    val phone: String = "",
    val email: String = "",
    val area: String = "",
    /** Who this person's reports go to: their manager, or the CEO for a manager. Null for the CEO and guests. */
    val reportsTo: Contact? = null,
) {
    val isGuest: Boolean get() = userId == null

    val contact: Contact get() = Contact(name, employeeCode, phone, email)

    /** Payment details decide where renters' money goes, so only managers and upper management may change them. */
    val canEditPaymentDetails: Boolean get() = role == UserRole.MANAGER || role == UserRole.CEO

    /** Managers and upper management may add employee logins; only upper management may add manager logins. */
    fun canAdd(newRole: UserRole): Boolean = when (newRole) {
        UserRole.EMPLOYEE -> role == UserRole.MANAGER || role == UserRole.CEO
        UserRole.MANAGER -> role == UserRole.CEO
        UserRole.CEO -> false
    }
}

/**
 * Holds the logged-in user while the app is running. Nothing is remembered after the app is
 * closed, so the login screen is shown every time the app starts.
 */
class SessionManager : Session {

    private val _currentUser = MutableStateFlow<CurrentUser?>(null)

    /** Null until someone logs in or continues as a guest. */
    val currentUser: StateFlow<CurrentUser?> = _currentUser.asStateFlow()

    override val userName: String get() = _currentUser.value?.name ?: GUEST_NAME

    fun signIn(user: CurrentUser) {
        _currentUser.value = user
    }

    fun signOut() {
        _currentUser.value = null
    }

    companion object {
        /** Stored as "entered by" on records made without logging in. Reserved: no user may take it. */
        const val GUEST_NAME = "Guest"

        /** A guest trying out one of the three views. Temporary, until every account is set up by invite. */
        fun guest(role: UserRole) = CurrentUser(name = GUEST_NAME, role = role, userId = null, employeeCode = "GUEST")
    }
}
