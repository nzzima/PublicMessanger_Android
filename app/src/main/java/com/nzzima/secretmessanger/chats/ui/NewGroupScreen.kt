package com.nzzima.secretmessanger.chats.ui

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
import androidx.compose.material3.TextButton
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
import com.nzzima.secretmessanger.ui.components.BackButton
import com.nzzima.secretmessanger.ui.components.FailureNotice
import com.nzzima.secretmessanger.ui.components.Notice
import com.nzzima.secretmessanger.ui.theme.Accent
import com.nzzima.secretmessanger.ui.theme.ErrorColor
import com.nzzima.secretmessanger.ui.theme.Ink
import com.nzzima.secretmessanger.ui.theme.InkDim
import com.nzzima.secretmessanger.utils.constants.Constants
import org.koin.androidx.compose.koinViewModel

/**
 * Экран новой группы: отметить участников и завести.
 *
 * @param onCreated открывает заведённую группу.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(
    backTitle: String,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewGroupViewModel = koinViewModel(),
) {
    val state by viewModel.observeNewGroupScreenState().collectAsStateWithLifecycle()
    val content = state as? NewGroupUiState.Content

    LaunchedEffect(content?.created) {
        content?.created?.let {
            viewModel.onOpened()
            onCreated(it)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(Constants.NEW_GROUP_TITLE) },
                navigationIcon = { BackButton(backTitle, onBack) },
                actions = {
                    // Кнопка живёт в шапке, а не внизу списка: список длинный, а решение
                    // принимают, не долистав до конца.
                    TextButton(onClick = viewModel::onCreate, enabled = content?.canCreate == true) {
                        Text(
                            text = Constants.CREATE_GROUP,
                            color = if (content?.canCreate == true) Accent else InkDim,
                            fontSize = 15.sp,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { insets ->
        Box(modifier = Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
            when (val current = state) {
                NewGroupUiState.Loading -> CircularProgressIndicator()

                NewGroupUiState.Empty -> Notice(Constants.CONTACTS_EMPTY)

                is NewGroupUiState.Content -> MemberList(current, viewModel::onToggle)

                is NewGroupUiState.Failed -> FailureNotice(current.message, viewModel::retry)
            }
        }
    }
}

@Composable
private fun MemberList(state: NewGroupUiState.Content, onToggle: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Подсказка вместо заблокированной кнопки без объяснения: с одним отмеченным
            // это диалог на двоих, и заводить его надо из «Контактов».
            Text(
                text = Constants.NEW_GROUP_HINT,
                color = InkDim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE_PADDING, vertical = 12.dp),
            )
        }

        state.error?.let { message ->
            item {
                Text(
                    text = message,
                    color = ErrorColor,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE_PADDING, vertical = 8.dp),
                )
            }
        }

        items(state.contacts, key = { it.id }) { contact ->
            MemberRow(contact, contact.id in state.chosen, onToggle)
            HorizontalDivider(color = MaterialTheme.colorScheme.surface)
        }
    }
}

/** Строка выбора: имя и галочка. */
@Composable
private fun MemberRow(contact: Contact, chosen: Boolean, onToggle: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(contact.id) }
            .padding(horizontal = SIDE_PADDING, vertical = ROW_PADDING),
    ) {
        Text(
            text = contact.login,
            color = Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (chosen) {
            Text(text = Constants.CHOSEN_MARK, color = Accent, fontSize = 17.sp)
        }
    }
}

private val SIDE_PADDING = 16.dp
private val ROW_PADDING = 14.dp
