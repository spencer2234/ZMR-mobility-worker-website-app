package com.zmr.rent.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zmr.rent.R
import com.zmr.rent.data.entity.UserRole
import com.zmr.rent.ui.components.BigButton
import com.zmr.rent.ui.components.BigOutlinedButton
import com.zmr.rent.ui.components.ZmrTopBar
import com.zmr.rent.ui.labelRes

/**
 * The first screen: Log in, or Continue as guest. A guest then picks which view to try
 * (Employee, Manager or Upper management). Guest mode is temporary, for testing the app.
 */
@Composable
fun WelcomeScreen(onLogIn: () -> Unit, onGuest: (UserRole) -> Unit) {
    var choosingView by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = choosingView) { choosingView = false }

    Scaffold(topBar = { ZmrTopBar(title = stringResource(R.string.app_name)) }) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp),
        ) {
            if (!choosingView) {
                BigButton(stringResource(R.string.action_log_in), painterResource(R.drawable.ic_check), onLogIn)
                BigOutlinedButton(
                    label = stringResource(R.string.action_continue_guest),
                    icon = painterResource(R.drawable.ic_person),
                    onClick = { choosingView = true },
                )
            } else {
                Text(stringResource(R.string.guest_choose_view), style = MaterialTheme.typography.titleMedium)
                UserRole.entries.forEach { role ->
                    BigOutlinedButton(
                        label = stringResource(role.labelRes()),
                        icon = painterResource(R.drawable.ic_person),
                        onClick = { onGuest(role) },
                    )
                }
            }
        }
    }
}
