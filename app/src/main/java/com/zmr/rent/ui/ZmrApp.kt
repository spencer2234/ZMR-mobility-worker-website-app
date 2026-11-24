package com.zmr.rent.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.SoundEffectOnInteraction
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zmr.rent.AppContainer
import com.zmr.rent.R
import com.zmr.rent.data.entity.UserRole
import com.zmr.rent.ui.addrenter.AddRenterScreen
import com.zmr.rent.ui.home.HomeScreen
import com.zmr.rent.ui.home.LanguageScreen
import com.zmr.rent.ui.home.OtherScreen
import com.zmr.rent.ui.inventory.InventoryScreen
import com.zmr.rent.ui.login.CeoSetupScreen
import com.zmr.rent.ui.login.InviteSetupScreen
import com.zmr.rent.ui.login.LoginScreen
import com.zmr.rent.ui.login.WelcomeScreen
import com.zmr.rent.ui.payment.InitialPaymentScreen
import com.zmr.rent.ui.renters.DocumentsScreen
import com.zmr.rent.ui.renters.EditRenterScreen
import com.zmr.rent.ui.renters.RentersScreen
import com.zmr.rent.ui.reports.ReportScreen
import com.zmr.rent.ui.reports.WeeklyStatusScreen
import com.zmr.rent.ui.settings.MyEmailScreen
import com.zmr.rent.ui.settings.PaymentSettingsScreen
import com.zmr.rent.ui.team.AddMemberScreen
import com.zmr.rent.ui.theme.ZmrTheme

/** Screen addresses used by the navigation graph. */
object Routes {
    // Before logging in.
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val SETUP_INVITE = "setup/invite"
    const val SETUP_CEO = "setup/ceo"

    // The five main buttons.
    const val HOME = "home"
    const val ADD_RENTER = "renters/add"
    const val RENTERS = "renters"
    const val INVENTORY = "inventory"
    const val SEND_DATA = "report/own"
    const val OTHER = "other"

    // The Other page (managers and upper management).
    const val ADD_EMPLOYEE_LOGIN = "team/add-employee"
    const val SEND_MANAGER_DATA = "report/team"
    const val ADD_MANAGER_LOGIN = "team/add-manager"

    const val PAYMENT_SETTINGS = "settings/payment"
    const val ARG_RENTER_ID = "renterId"
    const val EDIT_RENTER = "renters/{$ARG_RENTER_ID}/edit"
    const val FIRST_PAYMENT = "renters/{$ARG_RENTER_ID}/payment"
    const val RENT_PAYMENT = "renters/{$ARG_RENTER_ID}/rent"
    const val DOCUMENTS = "renters/{$ARG_RENTER_ID}/documents"
    const val NEW_RENTER_DOCUMENTS = "renters/{$ARG_RENTER_ID}/documents/new"
    const val LANGUAGE = "settings/language"
    const val MY_EMAIL = "settings/my-email"
    const val WEEKLY_STATUS = "team/weekly-status"

    fun editRenter(renterId: Long) = "renters/$renterId/edit"

    fun firstPayment(renterId: Long) = "renters/$renterId/payment"

    fun rentPayment(renterId: Long) = "renters/$renterId/rent"

    fun documents(renterId: Long) = "renters/$renterId/documents"

    fun newRenterDocuments(renterId: Long) = "renters/$renterId/documents/new"

    /** Screens that can be used without anyone logged in. */
    val SIGNED_OUT = setOf(WELCOME, LOGIN, SETUP_INVITE, SETUP_CEO)
}

/** The whole app: theme plus the navigation graph. */
@Composable
fun ZmrApp(container: AppContainer) {
    ZmrTheme {
        // No click sound on any button. Covers dialogs too, such as the calendar.
        SoundEffectOnInteraction(false) {
            AppNavigation(container)
        }
    }
}

@Composable
private fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()
    val user by container.session.currentUser.collectAsStateWithLifecycle()

    // Logging in, continuing as a guest and logging out all just change the session; this
    // moves to the right screen and clears Back history either way.
    LaunchedEffect(user == null) {
        val onSignedOutScreen = navController.currentDestination?.route in Routes.SIGNED_OUT
        when {
            user != null && onSignedOutScreen -> navController.navigateClearingHistory(Routes.HOME)
            user == null && !onSignedOutScreen -> navController.navigateClearingHistory(Routes.WELCOME)
        }
    }

    // Ignore Back when there is nowhere to go back to (for example a fast double tap).
    val goBack: () -> Unit = {
        if (navController.previousBackStackEntry != null) navController.popBackStack()
    }
    val go: (String) -> Unit = { route -> navController.navigate(route) }
    val renterIdArgument = listOf(navArgument(Routes.ARG_RENTER_ID) { type = NavType.LongType })

    NavHost(navController = navController, startDestination = Routes.WELCOME) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onLogIn = { go(Routes.LOGIN) }, onGuest = container.authRepository::continueAsGuest)
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                container = container,
                onBack = goBack,
                onSetUpWithInvite = { go(Routes.SETUP_INVITE) },
                onSetUpAsCeo = { go(Routes.SETUP_CEO) },
            )
        }
        composable(Routes.SETUP_INVITE) { InviteSetupScreen(container = container, onBack = goBack) }
        composable(Routes.SETUP_CEO) { CeoSetupScreen(container = container, onBack = goBack) }

        composable(Routes.HOME) {
            val current = user
            HomeScreen(
                userLine = if (current?.role == null) {
                    stringResource(R.string.home_logged_in_as, stringResource(R.string.user_guest))
                } else {
                    stringResource(R.string.home_logged_in_as_role, current.name, stringResource(current.role.labelRes()))
                },
                onLogOut = container.authRepository::logOut,
                onSettingsClick = { go(Routes.PAYMENT_SETTINGS) }.takeIf { current?.canEditPaymentDetails == true },
                onRegisterCustomer = { go(Routes.ADD_RENTER) },
                onRenters = { go(Routes.RENTERS) },
                onInventory = { go(Routes.INVENTORY) },
                onSendData = { go(Routes.SEND_DATA) },
                onOther = { go(Routes.OTHER) },
            )
        }
        composable(Routes.OTHER) {
            val isManagement = user?.canAdd(UserRole.EMPLOYEE) == true
            OtherScreen(
                onHome = goBack,
                onAddEmployeeLogin = { go(Routes.ADD_EMPLOYEE_LOGIN) }.takeIf { isManagement },
                onSendManagerData = { go(Routes.SEND_MANAGER_DATA) }.takeIf { isManagement },
                onAddManagerLogin = { go(Routes.ADD_MANAGER_LOGIN) }.takeIf { user?.canAdd(UserRole.MANAGER) == true },
                onWeeklyStatus = { go(Routes.WEEKLY_STATUS) }.takeIf { isManagement },
                onLanguage = { go(Routes.LANGUAGE) },
                onMyEmail = { go(Routes.MY_EMAIL) }.takeIf { user?.isGuest == false },
            )
        }

        composable(Routes.ADD_RENTER) {
            AddRenterScreen(
                container = container,
                onBack = goBack,
                // Straight on to documents, then the first payment; Back skips the finished form.
                onSaved = { renterId ->
                    navController.navigate(Routes.newRenterDocuments(renterId)) {
                        popUpTo(Routes.ADD_RENTER) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.RENTERS) {
            RentersScreen(
                container = container,
                onBack = goBack,
                onRenterClick = { renterId -> go(Routes.editRenter(renterId)) },
                onAddRenterClick = { go(Routes.ADD_RENTER) },
            )
        }
        composable(Routes.INVENTORY) {
            InventoryScreen(container = container, onBack = goBack, onRenterClick = { renterId -> go(Routes.editRenter(renterId)) })
        }
        composable(Routes.SEND_DATA) { ReportScreen(container = container, combine = false, onBack = goBack) }

        composable(Routes.EDIT_RENTER, arguments = renterIdArgument) { entry ->
            val renterId = requireNotNull(entry.arguments).getLong(Routes.ARG_RENTER_ID)
            EditRenterScreen(
                container = container,
                renterId = renterId,
                onBack = goBack,
                onSaved = goBack,
                onFirstPaymentClick = { go(Routes.firstPayment(renterId)) },
                onRentPaymentClick = { go(Routes.rentPayment(renterId)) },
                onDocumentsClick = { go(Routes.documents(renterId)) },
            )
        }
        composable(Routes.FIRST_PAYMENT, arguments = renterIdArgument) { entry ->
            InitialPaymentScreen(
                container = container,
                renterId = requireNotNull(entry.arguments).getLong(Routes.ARG_RENTER_ID),
                onDone = goBack,
            )
        }

        composable(Routes.RENT_PAYMENT, arguments = renterIdArgument) { entry ->
            InitialPaymentScreen(
                container = container,
                renterId = requireNotNull(entry.arguments).getLong(Routes.ARG_RENTER_ID),
                onDone = goBack,
                forRent = true,
            )
        }
        composable(Routes.DOCUMENTS, arguments = renterIdArgument) { entry ->
            val renterId = requireNotNull(entry.arguments).getLong(Routes.ARG_RENTER_ID)
            DocumentsScreen(container, renterId, onBack = goBack, onContinue = goBack)
        }
        composable(Routes.NEW_RENTER_DOCUMENTS, arguments = renterIdArgument) { entry ->
            val renterId = requireNotNull(entry.arguments).getLong(Routes.ARG_RENTER_ID)
            val toFirstPayment = {
                navController.navigate(Routes.firstPayment(renterId)) {
                    popUpTo(Routes.NEW_RENTER_DOCUMENTS) { inclusive = true }
                }
            }
            DocumentsScreen(container, renterId, onBack = toFirstPayment, onContinue = toFirstPayment)
        }
        composable(Routes.LANGUAGE) { LanguageScreen(onBack = goBack) }
        composable(Routes.MY_EMAIL) { MyEmailScreen(container = container, onBack = goBack) }

        // Management only. Each screen checks the role again, in case it is reached some other way.
        composable(Routes.PAYMENT_SETTINGS) {
            OnlyIf(user?.canEditPaymentDetails == true, goBack) { PaymentSettingsScreen(container = container, onBack = goBack) }
        }
        composable(Routes.ADD_EMPLOYEE_LOGIN) {
            OnlyIf(user?.canAdd(UserRole.EMPLOYEE) == true, goBack) { AddMemberScreen(container, UserRole.EMPLOYEE, goBack) }
        }
        composable(Routes.SEND_MANAGER_DATA) {
            OnlyIf(user?.canAdd(UserRole.EMPLOYEE) == true, goBack) { ReportScreen(container, combine = true, onBack = goBack) }
        }
        composable(Routes.WEEKLY_STATUS) {
            OnlyIf(user?.canAdd(UserRole.EMPLOYEE) == true, goBack) { WeeklyStatusScreen(container, goBack) }
        }
        composable(Routes.ADD_MANAGER_LOGIN) {
            OnlyIf(user?.canAdd(UserRole.MANAGER) == true, goBack) { AddMemberScreen(container, UserRole.MANAGER, goBack) }
        }
    }
}

/** Shows [content] only when [allowed]; otherwise goes straight back. */
@Composable
private fun OnlyIf(allowed: Boolean, goBack: () -> Unit, content: @Composable () -> Unit) {
    if (allowed) content() else LaunchedEffect(Unit) { goBack() }
}

private fun NavHostController.navigateClearingHistory(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
