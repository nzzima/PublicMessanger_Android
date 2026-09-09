package com.nzzima.secretmessanger.contacts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzzima.secretmessanger.contacts.domain.models.Contact
import com.nzzima.secretmessanger.ui.components.FailureNotice
import com.nzzima.secretmessanger.ui.components.Notice
import com.nzzima.secretmessanger.ui.theme.ErrorColor
import com.nzzima.secretmessanger.ui.theme.Ink
import com.nzzima.secretmessanger.utils.constants.Constants
import org.koin.androidx.compose.koinViewModel

/**
 * Экран контактов.
 *
 * Нажатие по строке открывает переписку, заводя диалог, если его ещё не было. Переход
 * поручает модель: заведение — это чтение профиля и запись шапки, и до конца этой работы
 * открывать нечего.
 *
 * @param onOpen переход в переписку заведённого диалога.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactsViewModel = koinViewModel(),
) {
    val state by viewModel.observeContactsScreenState().collectAsStateWithLifecycle()
    val opened = (state as? ContactsUiState.Content)?.opened

    LaunchedEffect(opened) {
        if (opened != null) {
            onOpen(opened)
            viewModel.onOpened()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(Constants.CONTACTS_TITLE) },
                colors = TopAppBarDefaults.topAppBarColors(
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

                is ContactsUiState.Content -> ContactList(current, viewModel::onContactTap)

                is ContactsUiState.Failed -> FailureNotice(current.message, viewModel::retry)
            }
        }
    }
}

@Composable
private fun ContactList(state: ContactsUiState.Content, onTap: (Contact) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.contacts, key = { it.id }) { contact ->
                Text(
                    text = contact.login,
                    color = Ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTap(contact) }
                        .padding(horizontal = SIDE_PADDING, vertical = ROW_PADDING),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surface)
            }
        }

        state.error?.let { message ->
            Text(
                text = message,
                color = ErrorColor,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(SIDE_PADDING),
            )
        }
    }
}

private val SIDE_PADDING = 16.dp
private val ROW_PADDING = 14.dp
