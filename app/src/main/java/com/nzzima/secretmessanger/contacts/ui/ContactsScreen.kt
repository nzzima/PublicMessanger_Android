package com.nzzima.secretmessanger.contacts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzzima.secretmessanger.contacts.domain.models.Contact
import com.nzzima.secretmessanger.ui.components.Avatar
import com.nzzima.secretmessanger.ui.components.FailureNotice
import com.nzzima.secretmessanger.ui.components.Notice
import com.nzzima.secretmessanger.ui.theme.Ink
import com.nzzima.secretmessanger.utils.constants.Constants
import org.koin.androidx.compose.koinViewModel

/**
 * Экран контактов.
 *
 * Нажатие по строке открывает профиль человека, а не переписку: диалог заводится оттуда,
 * кнопкой, — как на iOS. Работы у нажатия поэтому нет никакой, и модель о нём не знает.
 *
 * @param onOpen переход в профиль: идентификатор аккаунта и имя из списка. Имя нужно
 *   заголовку профиля до того, как тот подгрузится: экран не должен открываться безымянным.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onOpen: (Contact) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactsViewModel = koinViewModel(),
) {
    val state by viewModel.observeContactsScreenState().collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(Constants.CONTACTS_TITLE) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { insets ->
        Box(modifier = Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
            when (val current = state) {
                ContactsUiState.Loading -> CircularProgressIndicator()

                ContactsUiState.Empty -> Notice(Constants.CONTACTS_EMPTY)

                is ContactsUiState.Content -> ContactList(current, onOpen)

                is ContactsUiState.Failed -> FailureNotice(current.message, viewModel::retry)
            }
        }
    }
}

@Composable
private fun ContactList(state: ContactsUiState.Content, onOpen: (Contact) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.contacts, key = { it.id }) { contact ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(contact) }
                    .padding(horizontal = SIDE_PADDING, vertical = ROW_PADDING),
            ) {
                Avatar(
                    name = contact.login,
                    image = state.avatars[contact.id],
                    size = ROW_AVATAR,
                    modifier = Modifier.padding(end = AVATAR_GAP),
                )

                Text(
                    text = contact.login,
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surface)
        }
    }
}

private val SIDE_PADDING = 16.dp
private val ROW_PADDING = 10.dp
private val ROW_AVATAR = 44.dp
private val AVATAR_GAP = 12.dp
