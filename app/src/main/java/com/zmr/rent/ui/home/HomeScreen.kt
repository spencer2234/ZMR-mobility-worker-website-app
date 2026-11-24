package com.zmr.rent.ui.home

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zmr.rent.R
import com.zmr.rent.ui.components.BigButton
import com.zmr.rent.ui.components.BigOutlinedButton
import com.zmr.rent.ui.components.ChoiceGrid
import com.zmr.rent.ui.components.ZmrTopBar
import com.zmr.rent.ui.theme.TileAddRenter
import com.zmr.rent.ui.theme.TileRenters
import com.zmr.rent.ui.theme.TileReport
import com.zmr.rent.ui.theme.TileToday
import com.zmr.rent.util.AppLanguage

/** The main screen after logging in: the same five buttons for every role. */
@Composable
fun HomeScreen(
    /** "Logged in as Ravi (Employee)". */
    userLine: String,
    onLogOut: () -> Unit,
    /** Null hides the Payment details button (only managers and upper management may change where money goes). */
    onSettingsClick: (() -> Unit)?,
    onRegisterCustomer: () -> Unit,
    onRenters: () -> Unit,
    onInventory: () -> Unit,
    onSendData: () -> Unit,
    onOther: () -> Unit,
) {
    MenuScaffold(
        title = stringResource(R.string.app_name),
        actions = {
            if (onSettingsClick != null) {
                TopBarIcon(R.drawable.ic_settings, R.string.action_settings, onSettingsClick)
            }
            TopBarIcon(R.drawable.ic_logout, R.string.action_log_out, onLogOut)
        },
    ) {
        Text(userLine, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MenuButton(R.string.main_register_customer, R.drawable.ic_person_add, TileAddRenter, onRegisterCustomer)
        MenuButton(R.string.main_renters, R.drawable.ic_group, TileRenters, onRenters)
        MenuButton(R.string.main_inventory, R.drawable.ic_vehicle, TileToday, onInventory)
        MenuButton(R.string.main_send_data, R.drawable.ic_report, TileReport, onSendData)
        BigOutlinedButton(stringResource(R.string.main_other), painterResource(R.drawable.ic_more), onOther)
    }
}

/**
 * The page behind "Other": Home, Language and My email for everyone, plus the management features. A null
 * callback hides its button, so employees see no management buttons and managers never see the
 * upper-management button.
 */
@Composable
fun OtherScreen(
    onHome: () -> Unit,
    onAddEmployeeLogin: (() -> Unit)?,
    onSendManagerData: (() -> Unit)?,
    onAddManagerLogin: (() -> Unit)?,
    onWeeklyStatus: (() -> Unit)?,
    onLanguage: () -> Unit,
    onMyEmail: (() -> Unit)?,
) {
    MenuScaffold(title = stringResource(R.string.main_other)) {
        BigButton(stringResource(R.string.action_home), painterResource(R.drawable.ic_arrow_back), onHome)
        BigOutlinedButton(stringResource(R.string.settings_language), painterResource(R.drawable.ic_settings), onLanguage)
        onMyEmail?.let { BigOutlinedButton(stringResource(R.string.settings_my_email), painterResource(R.drawable.ic_person_add), it) }
        onWeeklyStatus?.let { BigOutlinedButton(stringResource(R.string.action_weekly_status), painterResource(R.drawable.ic_today), it) }
        onAddEmployeeLogin?.let { BigOutlinedButton(stringResource(R.string.action_add_employee_login), painterResource(R.drawable.ic_person_add), it) }
        onSendManagerData?.let { BigOutlinedButton(stringResource(R.string.action_send_manager_data), painterResource(R.drawable.ic_report), it) }
        onAddManagerLogin?.let { BigOutlinedButton(stringResource(R.string.action_add_manager_login), painterResource(R.drawable.ic_group), it) }
    }
}

/** Language setting: the whole app switches straight away. */
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = { ZmrTopBar(stringResource(R.string.settings_language), onBack) }) { innerPadding ->
        ChoiceGrid(
            options = AppLanguage.OPTIONS,
            selected = AppLanguage.OPTIONS.first { it.first == AppLanguage.current(context) },
            onSelect = { (code, _) ->
                AppLanguage.save(context, code)
                (context as? Activity)?.recreate()
            },
            label = { it.second },
            columns = 2,
            modifier = Modifier.padding(innerPadding).padding(16.dp),
        )
    }
}

@Composable
private fun MenuScaffold(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(topBar = { ZmrTopBar(title = title, actions = actions) }) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun MenuButton(@StringRes labelRes: Int, @DrawableRes iconRes: Int, color: Color, onClick: () -> Unit) =
    BigButton(stringResource(labelRes), painterResource(iconRes), onClick, color = color)

@Composable
private fun TopBarIcon(@DrawableRes iconRes: Int, @StringRes descriptionRes: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(iconRes), contentDescription = stringResource(descriptionRes))
    }
}
