package com.zmr.rent.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zmr.rent.AppContainer
import com.zmr.rent.R
import com.zmr.rent.data.entity.UserRole
import com.zmr.rent.domain.InviteLink
import com.zmr.rent.ui.components.BigButton
import com.zmr.rent.ui.components.BigOutlinedButton
import com.zmr.rent.ui.components.ZmrTopBar
import com.zmr.rent.ui.labelRes
import com.zmr.rent.ui.login.PersonFields
import com.zmr.rent.util.Sending

/**
 * Add employee login info, or Add manager login (upper management only). Creates an invite code
 * and sends it to the new person by WhatsApp or email; they use it to set up their own phone.
 */
@Composable
fun AddMemberScreen(container: AppContainer, role: UserRole, onDone: () -> Unit) {
    val viewModel: AddMemberViewModel = viewModel(factory = AddMemberViewModel.factory(container, role))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val title = stringResource(
        if (state.role == UserRole.MANAGER) R.string.action_add_manager_login else R.string.action_add_employee_login,
    )
    Scaffold(topBar = { ZmrTopBar(title = title, onBack = onDone) }) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            val invite = state.created
            if (invite == null) {
                Text(stringResource(R.string.team_add_intro), style = MaterialTheme.typography.bodyLarge)
                PersonFields(
                    form = state.person,
                    errors = state.errors,
                    onChange = viewModel::onPersonChange,
                    showArea = state.fixedArea.isEmpty(),
                )
                if (state.fixedArea.isNotEmpty()) {
                    Text(stringResource(R.string.team_area_fixed, state.fixedArea), style = MaterialTheme.typography.bodyLarge)
                }
                if (state.saveFailed) {
                    Text(
                        stringResource(R.string.error_save_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                BigButton(
                    label = stringResource(R.string.action_create_invite),
                    icon = painterResource(R.drawable.ic_person_add),
                    onClick = viewModel::createInvite,
                    enabled = !state.isSaving,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                val name = invite.member.name
                val role = stringResource(invite.role.labelRes())
                val links = invite.payment
                val message = if (links.invitePageLink.isNotEmpty()) {
                    val link = InviteLink.build(links.invitePageLink, state.code, links.appDownloadLink)
                    stringResource(R.string.invite_message_link, name, role, link)
                } else {
                    val download = if (links.appDownloadLink.isEmpty()) "" else stringResource(R.string.invite_download_line, links.appDownloadLink)
                    stringResource(R.string.invite_message, name, role, download + state.code)
                }
                val subject = stringResource(R.string.invite_subject)
                val chooser = stringResource(R.string.send_with)
                Text(stringResource(R.string.invite_ready, invite.member.name), style = MaterialTheme.typography.titleMedium)
                // The code itself can be copied, for sending some other way.
                SelectionContainer {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(state.code, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                    }
                }
                BigButton(
                    label = stringResource(R.string.action_send_whatsapp),
                    icon = painterResource(R.drawable.ic_check),
                    onClick = { Sending.textByWhatsApp(context, invite.member.phone, message) },
                )
                BigOutlinedButton(
                    label = stringResource(R.string.action_send_email),
                    icon = painterResource(R.drawable.ic_check),
                    onClick = { Sending.textByEmail(context, invite.member.email, subject, message, chooser) },
                )
                BigOutlinedButton(
                    label = stringResource(R.string.action_done),
                    icon = painterResource(R.drawable.ic_arrow_back),
                    onClick = onDone,
                )
            }
        }
    }
}
