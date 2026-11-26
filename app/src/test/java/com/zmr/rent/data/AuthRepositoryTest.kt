package com.zmr.rent.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zmr.rent.data.entity.UserRole
import com.zmr.rent.data.repository.AccountError
import com.zmr.rent.data.repository.AccountResult
import com.zmr.rent.data.repository.AuthRepository
import com.zmr.rent.data.repository.InviteResult
import com.zmr.rent.data.repository.LoginResult
import com.zmr.rent.data.repository.PersonDetails
import com.zmr.rent.data.session.SessionManager
import com.zmr.rent.domain.Contact
import com.zmr.rent.domain.Leader
import com.zmr.rent.domain.PaymentSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Two phones are simulated with two databases: the inviting person's phone and the new
 * person's phone. The invite code is the only thing that passes between them.
 */
@RunWith(AndroidJUnit4::class)
class AuthRepositoryTest {

    private val databases = mutableListOf<AppDatabase>()

    private inner class Phone {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .withAppSetup()
            .allowMainThreadQueries()
            .build()
            .also { databases += it }
        val session = SessionManager()
        val auth = AuthRepository(
            userDao = database.userDao(),
            settingsDao = database.settingsDao(),
            session = session,
            clock = Clock.fixed(Instant.parse("2026-11-02T04:30:00Z"), ZoneOffset.UTC),
            leaders = leaders,
            hashDispatcher = Dispatchers.Unconfined,
        )
    }

    // Upper management built into the app. Zara has no email yet.
    private val leaders = listOf(
        Leader.create(Contact("Ali", "CEO-1", "9876543210", "ali@example.com"), "1234"),
        Leader.create(Contact("Zara", "2", "9123456780", ""), "1111"),
    )

    private lateinit var ceoPhone: Phone

    private val payment = PaymentSettings(upiId = "zmrtest@okaxis", payeeName = "ZMR Test")

    @Before
    fun setUp() {
        ceoPhone = Phone()
    }

    @After
    fun closeDatabases() = databases.forEach { it.close() }

    private suspend fun setUpCeo(phone: Phone = ceoPhone) =
        phone.auth.setUpLeader("CEO-1", "1234")

    private suspend fun Phone.invite(role: UserRole, details: PersonDetails): String =
        (auth.createInvite(role, details, payment) as InviteResult.Created).code

    @Test
    fun `the CEO sets up the company and is logged in`() = runTest {
        val result = setUpCeo()

        val user = (result as AccountResult.SignedIn).user
        assertEquals(UserRole.CEO, user.role)
        assertEquals("CEO-1", user.employeeCode)
        assertEquals("9876543210", user.phone)
        assertEquals("ali@example.com", user.email)
        assertNull(user.reportsTo)
        assertEquals(user, ceoPhone.session.currentUser.value)
    }

    @Test
    fun `a phone can only have one upper management account`() = runTest {
        setUpCeo()
        assertEquals(AccountResult.Invalid(setOf(AccountError.CEO_EXISTS)), ceoPhone.auth.setUpLeader("2", "1111"))
    }

    @Test
    fun `upper management needs the PIN built into the app`() = runTest {
        assertEquals(AccountResult.Invalid(setOf(AccountError.PIN_WRONG)), ceoPhone.auth.setUpLeader("CEO-1", "1111"))
        assertEquals(AccountResult.Invalid(setOf(AccountError.PIN_FORMAT)), ceoPhone.auth.setUpLeader("CEO-1", "12"))
        assertEquals(AccountResult.Invalid(setOf(AccountError.NOT_ALLOWED)), ceoPhone.auth.setUpLeader("SOMEONE", "1234"))
        assertNull(ceoPhone.session.currentUser.value)
    }

    @Test
    fun `upper management can start with no email and add it later`() = runTest {
        val zara = (ceoPhone.auth.setUpLeader("2", "1111") as AccountResult.SignedIn).user
        assertEquals("", zara.email)

        assertEquals(AccountResult.Invalid(setOf(AccountError.EMAIL_INVALID)), ceoPhone.auth.updateMyEmail("zara@"))
        ceoPhone.auth.updateMyEmail(" Zara@Example.com ")
        assertEquals("zara@example.com", ceoPhone.session.currentUser.value?.email)
        ceoPhone.auth.logOut()
        ceoPhone.auth.logIn(zara.userId!!, "1111")
        assertEquals("zara@example.com", ceoPhone.session.currentUser.value?.email)
    }

    @Test
    fun `CEO adds a manager, who sets up their own phone and adds an employee, who sets up theirs`() = runTest {
        setUpCeo()
        val managerCode = ceoPhone.invite(UserRole.MANAGER, PersonDetails("Suresh Yadav", "MGR-7", "9123456780", "suresh@example.com", "Lucknow"))
        assertEquals(listOf("Suresh Yadav"), ceoPhone.database.userDao().getTeam("CEO-1").map { it.name })

        val managerPhone = Phone()
        val manager = (managerPhone.auth.setUpFromInvite(managerCode, "4321", "4321") as AccountResult.SignedIn).user
        assertEquals(UserRole.MANAGER, manager.role)
        assertEquals("Lucknow", manager.area)
        assertEquals(Contact("Ali", "CEO-1", "9876543210", "ali@example.com"), manager.reportsTo)
        // The CEO's payment details came with the invite.
        assertEquals("zmrtest@okaxis", PaymentSettings.from(managerPhone.database.settingsDao().observeAll().first()).upiId)

        // A manager's employees are always in the manager's own area.
        val employeeCode = managerPhone.invite(UserRole.EMPLOYEE, PersonDetails("Ravi Kumar", "EMP-102", "9988776655", "ravi@example.com", "Delhi"))
        val employeePhone = Phone()
        val employee = (employeePhone.auth.setUpFromInvite(employeeCode, "5555", "5555") as AccountResult.SignedIn).user
        assertEquals(UserRole.EMPLOYEE, employee.role)
        assertEquals("Lucknow", employee.area)
        assertEquals("Suresh Yadav", employee.reportsTo?.name)
        assertEquals("MGR-7", employee.reportsTo?.employeeCode)
        assertFalse(employee.canAdd(UserRole.EMPLOYEE))
        assertFalse(employee.canAdd(UserRole.MANAGER))
        assertTrue(manager.canAdd(UserRole.EMPLOYEE))
        assertFalse(manager.canAdd(UserRole.MANAGER))
        assertFalse(employee.canEditPaymentDetails)
    }

    @Test
    fun `employees cannot add logins, managers cannot add managers, and employee IDs cannot repeat`() = runTest {
        val someone = PersonDetails("X", "X-1", "9123456780", "x@example.com", "Pune")
        ceoPhone.auth.continueAsGuest(UserRole.EMPLOYEE)
        assertEquals(InviteResult.Invalid(setOf(AccountError.NOT_ALLOWED)), ceoPhone.auth.createInvite(UserRole.EMPLOYEE, someone, payment))
        ceoPhone.auth.continueAsGuest(UserRole.MANAGER)
        assertEquals(InviteResult.Invalid(setOf(AccountError.NOT_ALLOWED)), ceoPhone.auth.createInvite(UserRole.MANAGER, someone, payment))

        setUpCeo()
        ceoPhone.invite(UserRole.MANAGER, PersonDetails("Suresh Yadav", "MGR-7", "9123456780", "suresh@example.com", "Lucknow"))
        assertEquals(
            InviteResult.Invalid(setOf(AccountError.CODE_TAKEN)),
            ceoPhone.auth.createInvite(UserRole.MANAGER, PersonDetails("Other", "mgr-7", "9123456781", "o@example.com", "Pune"), payment),
        )
        // Upper management has no area of their own, so the area must be typed.
        assertEquals(
            InviteResult.Invalid(setOf(AccountError.AREA_REQUIRED)),
            ceoPhone.auth.createInvite(UserRole.EMPLOYEE, PersonDetails("Neha", "EMP-9", "9123456782", "n@example.com", ""), payment),
        )
    }

    @Test
    fun `a new invite for the same person replaces the old one and resets their PIN`() = runTest {
        setUpCeo()
        val ravi = PersonDetails("Ravi Kumar", "EMP-102", "9988776655", "ravi@example.com", "Delhi")
        val first = ceoPhone.invite(UserRole.EMPLOYEE, ravi)
        val raviPhone = Phone()
        val userId = (raviPhone.auth.setUpFromInvite(first, "1111", "1111") as AccountResult.SignedIn).user.userId!!
        raviPhone.auth.logOut()

        // Ravi forgot his PIN: his manager sends a new invite with his new number.
        val second = ceoPhone.invite(UserRole.EMPLOYEE, ravi.copy(phone = "9988776600"))
        assertEquals(1, ceoPhone.database.userDao().getTeam("CEO-1").size)
        val again = (raviPhone.auth.setUpFromInvite(second, "2222", "2222") as AccountResult.SignedIn).user
        assertEquals(userId, again.userId)
        assertEquals("9988776600", again.phone)
        raviPhone.auth.logOut()
        assertEquals(LoginResult.WrongPin, raviPhone.auth.logIn(userId, "1111"))
        assertTrue(raviPhone.auth.logIn(userId, "2222") is LoginResult.LoggedIn)
    }

    @Test
    fun `a broken invite code is refused`() = runTest {
        assertEquals(
            AccountResult.Invalid(setOf(AccountError.INVITE_INVALID)),
            ceoPhone.auth.setUpFromInvite("ZMR1.abc.00000000", "1234", "1234"),
        )
    }

    @Test
    fun `logging in needs the right PIN`() = runTest {
        val userId = (setUpCeo() as AccountResult.SignedIn).user.userId!!
        ceoPhone.auth.logOut()

        assertEquals(LoginResult.WrongPin, ceoPhone.auth.logIn(userId, "0000"))
        assertNull(ceoPhone.session.currentUser.value)
        assertTrue(ceoPhone.auth.logIn(userId, "1234") is LoginResult.LoggedIn)
        assertEquals("CEO-1", ceoPhone.session.currentUser.value?.employeeCode)
    }

    @Test
    fun `a guest picks a view and is recorded as Guest`() {
        ceoPhone.auth.continueAsGuest(UserRole.EMPLOYEE)

        val guest = ceoPhone.session.currentUser.value!!
        assertTrue(guest.isGuest)
        assertEquals(UserRole.EMPLOYEE, guest.role)
        assertFalse(guest.canAdd(UserRole.EMPLOYEE))
        assertEquals(SessionManager.GUEST_NAME, ceoPhone.session.userName)

        ceoPhone.auth.continueAsGuest(UserRole.CEO)
        assertTrue(ceoPhone.session.currentUser.value!!.canAdd(UserRole.MANAGER))
    }
}
