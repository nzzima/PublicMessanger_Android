package com.nzzima.secretmessanger.chats.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzzima.secretmessanger.chats.domain.models.Conversation
import com.nzzima.secretmessanger.ui.components.Avatar
import com.nzzima.secretmessanger.ui.components.FailureNotice
import com.nzzima.secretmessanger.ui.components.GroupAvatar
import com.nzzima.secretmessanger.ui.components.Notice
import com.nzzima.secretmessanger.ui.components.shortTime
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.nzzima.secretmessanger.ui.components.NewGroupIcon
import com.nzzima.secretmessanger.ui.theme.Accent
import com.nzzima.secretmessanger.ui.theme.ErrorColor
import com.nzzima.secretmessanger.ui.theme.Ink
import com.nzzima.secretmessanger.ui.theme.InkDim
import com.nzzima.secretmessanger.utils.constants.Constants
import org.koin.androidx.compose.koinViewModel

/**
 * Экран списка диалогов.
 *
 * @param onOpen нажатие по строке; ведёт в переписку выбранного диалога.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onOpen: (String) -> Unit,
    onNewGroup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsViewModel = koinViewModel(),
) {
    val state by viewModel.observeChatsScreenState().collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(Constants.CHATS_TITLE) },
                actions = {
                    IconButton(onClick = onNewGroup) {
                        Icon(imageVector = NewGroupIcon, contentDescription = Constants.NEW_GROUP, tint = Accent)
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
                ChatsUiState.Loading -> CircularProgressIndicator()

                ChatsUiState.Empty -> Notice(Constants.CHATS_EMPTY)

                is ChatsUiState.Content -> ConversationList(current, onOpen, viewModel::onEraseAsked)

                is ChatsUiState.Failed -> FailureNotice(current.message, viewModel::retry)
            }
        }
    }

    (state as? ChatsUiState.Content)?.asking?.let { asking ->
        EraseQuestion(
            isGroup = asking.chat.isGroup,
            isErasing = (state as ChatsUiState.Content).isErasing,
            error = (state as ChatsUiState.Content).error,
            onConfirm = viewModel::onEraseConfirmed,
            onDismiss = viewModel::onEraseDismissed,
        )
    }
}

/**
 * Вопрос перед стиранием.
 *
 * Красной здесь только кнопка подтверждения: знак необратимости перестаёт читаться, если
 * ставить его на всё подряд.
 */
@Composable
private fun EraseQuestion(
    isGroup: Boolean,
    isErasing: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isGroup) Constants.ERASE_GROUP_QUESTION else Constants.ERASE_CHAT_QUESTION) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                Text(Constants.ERASE_CHAT_EXPLANATION)
                error?.let { Text(it, color = ErrorColor, fontSize = 14.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isErasing) {
                Text(Constants.ERASE_CHAT, color = ErrorColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isErasing) {
                Text(Constants.CANCEL, color = Accent)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun ConversationList(
    state: ChatsUiState.Content,
    onOpen: (String) -> Unit,
    onErase: (Conversation) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.conversations, key = { it.chat.id }) { conversation ->
            ConversationRow(
                conversation = conversation,
                avatar = conversation.chat.companionId?.let(state.avatars::get),
                online = conversation.chat.companionId?.let(state.online::contains) == true,
                onOpen = onOpen,
                onErase = onErase,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surface)
        }
    }
}

/**
 * Строка списка: лицо собеседника, название диалога, превью последней реплики и её время.
 *
 * Удаление висит на долгом нажатии — привычном на Android жесте для действий над строкой
 * списка. Свайпа, как на iOS, здесь нет: там он подсказан системой, а в Compose это своя
 * механика, которая на строке с картинкой и тремя текстами стоила бы дороже, чем даёт.
 *
 * Присутствие показано точкой без подписи словами: вторая строка занята превью реплики.
 * [online] у группы всегда `false` — собеседник там не один.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    avatar: ByteArray?,
    online: Boolean,
    onOpen: (String) -> Unit,
    onErase: (Conversation) -> Unit,
) {
    Row(
        modifier = Modifier
            .combinedClickable(
                onClick = { onOpen(conversation.chat.id) },
                onLongClick = { onErase(conversation) },
            )
            .padding(horizontal = SIDE_PADDING, vertical = ROW_PADDING),
        verticalAlignment = Alignment.Top,
    ) {
        if (conversation.chat.isGroup) {
            GroupAvatar(size = ROW_AVATAR, modifier = Modifier.padding(end = AVATAR_GAP))
        } else {
            Avatar(
                name = conversation.chat.title,
                image = avatar,
                size = ROW_AVATAR,
                modifier = Modifier.padding(end = AVATAR_GAP),
                online = online,
            )
        }

        Column(
            modifier = Modifier.weight(1f).padding(end = GAP),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = conversation.chat.title,
                color = Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = conversation.preview,
                color = InkDim,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = shortTime(conversation.date),
            color = InkDim,
            // Табличные цифры: без них время пляшет по горизонтали от строки к строке.
            style = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum"),
        )
    }
}

private val SIDE_PADDING = 16.dp
private val ROW_PADDING = 12.dp
private val GAP = 10.dp
private val ROW_AVATAR = 44.dp
private val AVATAR_GAP = 12.dp
