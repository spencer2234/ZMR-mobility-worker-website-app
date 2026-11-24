package com.zmr.rent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zmr.rent.AppContainer
import com.zmr.rent.R
import com.zmr.rent.data.repository.AccountResult
import com.zmr.rent.ui.components.BigButton
import com.zmr.rent.ui.components.FormTextField
import com.zmr.rent.ui.components.ZmrTopBar
import kotlinx.coroutines.launch

/** Lets the logged-in person add or change their own email (upper management may start with none). */
@Composable
fun MyEmailScreen(container: AppContainer, onBack: () -> Unit) {
    val auth = container.authRepository
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf(container.session.currentUser.value?.email.orEmpty()) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { ZmrTopBar(stringResource(R.string.settings_my_email), onBack) }) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(innerPadding).padding(16.dp),
        ) {
            Text(stringResource(R.string.my_email_intro), style = MaterialTheme.typography.bodyLarge)
            FormTextField(
                value = email,
                onValueChange = { email = it; invalid = false; saved = false },
                labelRes = R.string.field_email,
                errorRes = R.string.error_email_invalid.takeIf { invalid },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            if (saved) Text(stringResource(R.string.my_email_saved), style = MaterialTheme.typography.bodyLarge)
            BigButton(
                label = stringResource(R.string.action_save_email),
                icon = painterResource(R.drawable.ic_check),
                onClick = {
                    scope.launch {
                        val ok = auth.updateMyEmail(email) is AccountResult.SignedIn
                        invalid = !ok
                        saved = ok
                    }
                },
            )
        }
    }
}
