package com.zmr.rent.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.zmr.rent.data.dao.SettingsDao
import com.zmr.rent.data.dao.UserDao
import com.zmr.rent.data.entity.TeamMember
import com.zmr.rent.data.entity.User
import com.zmr.rent.data.entity.UserRole
import com.zmr.rent.data.entity.UserSummary
import com.zmr.rent.data.session.CurrentUser
import com.zmr.rent.data.session.SessionManager
import com.zmr.rent.domain.Contact
import com.zmr.rent.domain.Email
import com.zmr.rent.domain.EmployeeCode
import com.zmr.rent.domain.IndianPhone
import com.zmr.rent.domain.Invite
import com.zmr.rent.domain.InviteCodec
import com.zmr.rent.domain.Leader
import com.zmr.rent.domain.PaymentSettings
import com.zmr.rent.domain.PaymentSettingsValidator
import com.zmr.rent.domain.Pin
import com.zmr.rent.domain.hexToBytes
import com.zmr.rent.domain.toHex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Clock

enum class AccountError {
    NAME_REQUIRED,
    NAME_TAKEN,
    CODE_INVALID,
    CODE_TAKEN,
    PHONE_INVALID,
    EMAIL_INVALID,
    AREA_REQUIRED,
    PIN_FORMAT,
    PIN_MISMATCH,
    PIN_WRONG,
    INVITE_INVALID,
    CEO_EXISTS,
    NOT_ALLOWED,
}

sealed interface AccountResult {
    data class SignedIn(val user: CurrentUser) : AccountResult
    data class Invalid(val errors: Set<AccountError>) : AccountResult
}

sealed interface InviteResult {
    /** [code] is the text to send to the new person. */
    data class Created(val invite: Invite, val code: String) : InviteResult
    data class Invalid(val errors: Set<AccountError>) : InviteResult
}

sealed interface LoginResult {
    data class LoggedIn(val user: CurrentUser) : LoginResult
    data object WrongPin : LoginResult
    data object UnknownUser : LoginResult
}

/** The details typed for a new person. */
data class PersonDetails(
    val name: String,
    val employeeCode: String,
    val phone: String,
    val email: String,
    val area: String = "",
)

/**
 * Accounts. There is no server: the CEO sets up their own account on their phone; the CEO adds
 * managers and managers add employees by creating an invite code, which the new person enters
 * on their own phone to set up their account with their own PIN.
 */
class AuthRepository(
    private val userDao: UserDao,
    private val settingsDao: SettingsDao,
    private val session: SessionManager,
    private val clock: Clock,
    /** The upper-management people built into this APK. */
    val leaders: List<Leader> = emptyList(),
    /** PIN hashing is deliberately slow, so it runs off the main thread. */
    private val hashDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    fun observeUsers(): Flow<List<UserSummary>> = userDao.observeAll()

    fun observeHasCeo(): Flow<Boolean> = userDao.observeHasCeo()

    /** Everyone the logged-in manager or CEO has added. */
    fun observeTeam(): Flow<List<TeamMember>> {
        val code = session.currentUser.value?.employeeCode.orEmpty()
        return userDao.observeTeam(code)
    }

    /** Upper management sets up their own phone: pick their name and type the PIN ZMR gave them. One per phone. */
    suspend fun setUpLeader(employeeCode: String, pin: String): AccountResult {
        val leader = leaders.find { it.contact.employeeCode == employeeCode }
            ?: return AccountResult.Invalid(setOf(AccountError.NOT_ALLOWED))
        if (!Pin.isValid(pin)) return AccountResult.Invalid(setOf(AccountError.PIN_FORMAT))
        if (!withContext(hashDispatcher) { leader.pinMatches(pin) }) return AccountResult.Invalid(setOf(AccountError.PIN_WRONG))
        val c = leader.contact
        val person = PersonDetails(c.name, c.employeeCode, c.phone, c.email)
        val errors = takenErrors(person).toMutableSet()
        if (userDao.observeHasCeo().first()) errors += AccountError.CEO_EXISTS
        if (errors.isNotEmpty()) return AccountResult.Invalid(errors)
        return createAndSignIn(person, UserRole.CEO, reportsTo = null, pin = pin)
    }

    /** Changes the logged-in person's email. Invites they already sent keep the old one. */
    suspend fun updateMyEmail(email: String): AccountResult {
        val me = session.currentUser.value
        val userId = me?.userId ?: return AccountResult.Invalid(setOf(AccountError.NOT_ALLOWED))
        val clean = Email.normalize(email)
        if (!Email.isValid(clean)) return AccountResult.Invalid(setOf(AccountError.EMAIL_INVALID))
        userDao.updateEmail(userId, clean)
        val updated = me.copy(email = clean)
        session.signIn(updated)
        return AccountResult.SignedIn(updated)
    }

    /** Reads an invite from pasted text (the whole message is fine). Null if there is none. */
    fun readInvite(text: String): Invite? = InviteCodec.decode(text)

    /** Sets up this phone for the person an invite was made for, with the PIN they choose. */
    suspend fun setUpFromInvite(inviteText: String, pin: String, confirmPin: String): AccountResult {
        val invite = InviteCodec.decode(inviteText)
            ?: return AccountResult.Invalid(setOf(AccountError.INVITE_INVALID))
        if (invite.role == UserRole.CEO) return AccountResult.Invalid(setOf(AccountError.INVITE_INVALID))
        val person = clean(
            PersonDetails(invite.member.name, invite.member.employeeCode, invite.member.phone, invite.member.email, invite.area),
        )
        val errors = pinErrors(pin, confirmPin) + takenErrors(person)
        if (errors.isNotEmpty()) return AccountResult.Invalid(errors)

        // The phone takes the payment details of the person who sent the invite, so this
        // person's UPI QR codes and bank details point at the right ZMR account.
        val payment = PaymentSettingsValidator.clean(invite.payment)
        if (PaymentSettingsValidator.validate(payment).isEmpty()) settingsDao.upsertAll(payment.toSettings())

        return createAndSignIn(person, invite.role, invite.reportsTo, pin)
    }

    /**
     * Adds a team member with the given [role]: managers and upper management add employees,
     * upper management also adds managers. Returns the invite code to send them.
     */
    suspend fun createInvite(role: UserRole, details: PersonDetails, payment: PaymentSettings): InviteResult {
        val me = session.currentUser.value
        if (me == null || !me.canAdd(role)) return InviteResult.Invalid(setOf(AccountError.NOT_ALLOWED))
        // A manager's employees work in the manager's area; upper management types the area.
        val person = clean(if (me.area.isNotEmpty() && role == UserRole.EMPLOYEE) details.copy(area = me.area) else details)
        val errors = personErrors(person, needsArea = true).toMutableSet()
        if (person.employeeCode.isNotEmpty() &&
            (userDao.countTeamMembersWithCode(person.employeeCode) > 0 || person.employeeCode == me.employeeCode)
        ) {
            errors += AccountError.CODE_TAKEN
        }
        if (errors.isNotEmpty()) return InviteResult.Invalid(errors)

        val invite = Invite(
            role = role,
            member = Contact(person.name, person.employeeCode, person.phone, person.email),
            area = person.area,
            reportsTo = me.contact,
            payment = payment,
        )
        userDao.insertTeamMember(
            TeamMember(
                employeeCode = person.employeeCode,
                name = person.name,
                role = role,
                phone = person.phone,
                email = person.email,
                area = person.area,
                addedByCode = me.employeeCode,
                addedAt = clock.millis(),
            ),
        )
        return InviteResult.Created(invite, InviteCodec.encode(invite))
    }

    suspend fun logIn(userId: Long, pin: String): LoginResult {
        val user = userDao.findById(userId) ?: return LoginResult.UnknownUser
        val correct = Pin.isValid(pin) && withContext(hashDispatcher) {
            Pin.matches(pin, user.pinSalt.hexToBytes(), user.pinHash.hexToBytes())
        }
        if (!correct) return LoginResult.WrongPin

        val current = user.toCurrentUser()
        session.signIn(current)
        return LoginResult.LoggedIn(current)
    }

    fun continueAsGuest(role: UserRole) = session.signIn(SessionManager.guest(role))

    fun logOut() = session.signOut()

    private suspend fun createAndSignIn(person: PersonDetails, role: UserRole, reportsTo: Contact?, pin: String): AccountResult {
        val salt = Pin.newSalt()
        val hash = withContext(hashDispatcher) { Pin.hash(pin, salt) }
        val user = User(
            name = person.name,
            role = role,
            pinHash = hash.toHex(),
            pinSalt = salt.toHex(),
            createdAt = clock.millis(),
            employeeCode = person.employeeCode,
            phone = person.phone,
            email = person.email,
            area = person.area,
            reportsToName = reportsTo?.name.orEmpty(),
            reportsToCode = reportsTo?.employeeCode.orEmpty(),
            reportsToPhone = reportsTo?.phone.orEmpty(),
            reportsToEmail = reportsTo?.email.orEmpty(),
        )
        val id = try {
            userDao.insert(user)
        } catch (e: SQLiteConstraintException) {
            return AccountResult.Invalid(setOf(AccountError.CODE_TAKEN))
        }
        val current = user.copy(id = id).toCurrentUser()
        session.signIn(current)
        return AccountResult.SignedIn(current)
    }

    private fun clean(details: PersonDetails) = details.copy(
        name = details.name.trim().replace(WHITESPACE, " "),
        employeeCode = EmployeeCode.normalize(details.employeeCode),
        phone = IndianPhone.normalize(details.phone),
        email = Email.normalize(details.email),
        area = details.area.trim().replace(WHITESPACE, " "),
    )

    private fun personErrors(person: PersonDetails, needsArea: Boolean) = buildSet {
        when {
            person.name.isEmpty() || person.name.length > MAX_NAME_LENGTH -> add(AccountError.NAME_REQUIRED)
            person.name.equals(SessionManager.GUEST_NAME, ignoreCase = true) -> add(AccountError.NAME_TAKEN)
        }
        if (!EmployeeCode.isValid(person.employeeCode)) add(AccountError.CODE_INVALID)
        if (!IndianPhone.isValid(person.phone)) add(AccountError.PHONE_INVALID)
        if (!Email.isValid(person.email)) add(AccountError.EMAIL_INVALID)
        if (needsArea && person.area.isEmpty()) add(AccountError.AREA_REQUIRED)
    }

    private fun pinErrors(pin: String, confirmPin: String) = buildSet {
        when {
            !Pin.isValid(pin) -> add(AccountError.PIN_FORMAT)
            pin != confirmPin -> add(AccountError.PIN_MISMATCH)
        }
    }

    /** Names and employee IDs must be unique among the accounts on this phone. */
    private suspend fun takenErrors(person: PersonDetails) = buildSet {
        if (person.name.isNotEmpty() && userDao.countWithName(person.name) > 0) add(AccountError.NAME_TAKEN)
        if (person.employeeCode.isNotEmpty() && userDao.countWithCode(person.employeeCode) > 0) add(AccountError.CODE_TAKEN)
    }

    private fun User.toCurrentUser() = CurrentUser(
        name = name,
        role = role,
        userId = id,
        employeeCode = employeeCode,
        phone = phone,
        email = email,
        area = area,
        reportsTo = Contact(reportsToName, reportsToCode, reportsToPhone, reportsToEmail)
            .takeIf { reportsToName.isNotEmpty() },
    )

    companion object {
        const val MAX_NAME_LENGTH = 40
        private val WHITESPACE = Regex("\\s+")
    }
}
