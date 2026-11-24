package com.zmr.rent.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zmr.rent.AppContainer
import com.zmr.rent.R
import com.zmr.rent.ui.components.BigButton
import com.zmr.rent.ui.components.BigOutlinedButton
import com.zmr.rent.ui.components.ChoiceGrid
import com.zmr.rent.ui.components.ZmrTopBar

/**
 * Log in: pick an account on this phone and type its PIN. Also where a new person sets up their
 * account from an invite code, or upper management sets up the first account.
 */
@Composable
fun LoginScreen(container: AppContainer, onBack: () -> Unit, onSetUpWithInvite: () -> Unit, onSetUpAsCeo: () -> Unit) {
    val viewModel: LoginViewModel = viewModel(factory = LoginViewModel.factory(container))
    val users by viewModel.users.collectAsStateWithLifecycle()
    val hasCeo by viewModel.hasCeo.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()

    Scaffold(topBar = { ZmrTopBar(title = stringResource(R.string.action_log_in), onBack = onBack) }) { innerPadding ->
        val contentModifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding()
            .fillMaxSize()

        val loadedUsers = users
        if (loadedUsers == null) {
            Box(contentModifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = contentModifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (loadedUsers.isEmpty()) {
                Text(stringResource(R.string.login_no_users), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                BigButton(
                    label = stringResource(R.string.action_setup_invite),
                    icon = painterResource(R.drawable.ic_person_add),
                    onClick = onSetUpWithInvite,
                )
            } else {
                Text(stringResource(R.string.login_who), style = MaterialTheme.typography.titleMedium)
                ChoiceGrid(
                    options = loadedUsers,
                    selected = loadedUsers.firstOrNull { it.id == form.selectedUserId },
                    onSelect = { viewModel.onUserSelected(it.id) },
                    label = { it.name },
                    columns = 2,
                )
                if (form.error == LoginError.CHOOSE_USER) {
                    Text(
                        text = stringResource(R.string.error_choose_user),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                PinField(
                    value = form.pin,
                    onValueChange = viewModel::onPinChange,
                    labelRes = R.string.field_pin,
                    errorRes = when (form.error) {
                        LoginError.PIN_FORMAT -> R.string.error_pin_format
                        LoginError.WRONG_PIN -> R.string.error_pin_wrong
                        else -> null
                    },
                )
                BigButton(
                    label = stringResource(R.string.action_log_in),
                    icon = painterResource(R.drawable.ic_check),
                    onClick = viewModel::logIn,
                    enabled = !form.isChecking,
                )
                Spacer(Modifier.height(8.dp))
                BigOutlinedButton(
                    label = stringResource(R.string.action_setup_invite),
                    icon = painterResource(R.drawable.ic_person_add),
                    onClick = onSetUpWithInvite,
                )
            }
            if (!hasCeo) {
                BigOutlinedButton(
                    label = stringResource(R.string.action_setup_ceo),
                    icon = painterResource(R.drawable.ic_group),
                    onClick = onSetUpAsCeo,
                )
            }
        }
    }
}
