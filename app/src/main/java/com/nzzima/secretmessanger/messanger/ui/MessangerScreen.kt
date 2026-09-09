package com.nzzima.secretmessanger.messanger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzzima.secretmessanger.messanger.domain.models.Reply
import com.nzzima.secretmessanger.ui.components.BackIcon
import com.nzzima.secretmessanger.ui.components.FailureNotice
import com.nzzima.secretmessanger.ui.components.Field
import com.nzzima.secretmessanger.ui.components.Notice
import com.nzzima.secretmessanger.ui.components.SendIcon
import com.nzzima.secretmessanger.ui.components.shortTime
import com.nzzima.secretmessanger.ui.theme.Accent
import com.nzzima.secretmessanger.ui.theme.ErrorColor
import com.nzzima.secretmessanger.ui.theme.Ink
import com.nzzima.secretmessanger.ui.theme.InkDim
import com.nzzima.secretmessanger.ui.theme.OwnBubble
import com.nzzima.secretmessanger.ui.theme.Raised
import com.nzzima.secretmessanger.utils.constants.Constants
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Экран переписки одного диалога.
 *
 * Нижний отступ держит сам: таб-бара здесь нет, а панель ввода обязана вставать над
 * клавиатурой. Из отступа клавиатуры вычтена навигационная полоса — её уже оплатил
 * корневой `Scaffold` в [com.nzzima.secretmessanger.main.ui.RootActivity], и целый
 * `imePadding` поднял бы панель над клавиатурой на высоту полосы. Закрытая клавиатура
 * даёт ноль: вычитание не уходит ниже нуля.
 *
 * @param convoId диалог; приходит аргументом назначения.
 * @param onBack возврат к списку диалогов.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessangerScreen(
    convoId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessangerViewModel = koinViewModel { parametersOf(convoId) },
) {
    val state by viewModel.observeMessangerScreenState().collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (state as? MessangerUiState.Content)?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(BackIcon, contentDescription = Constants.BACK, tint = Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
        ) {
            when (val current = state) {
                MessangerUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                is MessangerUiState.Failed ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (current.canRetry) {
                            FailureNotice(current.message, viewModel::retry)
                        } else {
                            Notice(current.message)
                        }
                    }

                is MessangerUiState.Content -> {
                    ReplyList(current.replies, Modifier.weight(1f))

                    current.error?.let { message ->
                        Text(
                            text = message,
                            color = ErrorColor,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE_PADDING),
                        )
                    }

                    InputBar(
                        draft = current.draft,
                        canSend = current.canSend,
                        onDraftChange = viewModel::onDraftChange,
                        onSend = viewModel::onSend,
                    )
                }
            }
        }
    }
}

/**
 * Лента реплик.
 *
 * `reverseLayout` вместо прокрутки к концу: лента прирастает снизу, и свежая реплика
 * попадает в кадр сама — как чужая, так и своя.
 */
@Composable
private fun ReplyList(replies: List<Reply>, modifier: Modifier = Modifier) {
    if (replies.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Notice(Constants.MESSAGES_EMPTY)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = SIDE_PADDING, vertical = LIST_PADDING),
        // Прижим к низу задаётся явно: своя расстановка перебивает ту, что идёт с
        // reverseLayout, и короткая переписка повисала вверху экрана вместо панели ввода.
        verticalArrangement = Arrangement.spacedBy(REPLY_GAP, Alignment.Bottom),
    ) {
        items(replies.asReversed(), key = { it.id }) { reply -> ReplyRow(reply) }
    }
}

/** Реплика: пузырь со своей стороны, под ним время. */
@Composable
private fun ReplyRow(reply: Reply) {
    if (reply.service) {
        Text(
            text = reply.text,
            color = InkDim,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = LIST_PADDING),
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (reply.outgoing) Alignment.End else Alignment.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = BUBBLE_MAX_WIDTH)
                // Оба пузыря весят одинаково, как на iOS: свой приглушённый синий,
                // чужой светлее фона. Насыщенный свой делал разговор монологом.
                .background(if (reply.outgoing) OwnBubble else Raised, RoundedCornerShape(BUBBLE_CORNER))
                .padding(horizontal = BUBBLE_PADDING, vertical = BUBBLE_INNER_PADDING),
        ) {
            if (reply.author.isNotEmpty()) {
                Text(
                    text = reply.author,
                    color = Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(text = reply.text, color = Ink, fontSize = 16.sp)
        }

        Text(
            text = shortTime(reply.date),
            color = InkDim,
            // Табличные цифры: без них время пляшет по горизонтали от реплики к реплике.
            style = TextStyle(fontSize = 10.sp, fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(horizontal = BUBBLE_PADDING, vertical = 2.dp),
        )
    }
}

/**
 * Панель ввода.
 *
 * Поле — то же, что на авторизации, но с заглавной после точки и автозаменой: здесь
 * пишут предложения, а не логины.
 */
@Composable
private fun InputBar(
    draft: String,
    canSend: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.surface)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE_PADDING, vertical = LIST_PADDING),
        verticalAlignment = Alignment.Bottom,
    ) {
        Field(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = Constants.MESSAGE_PLACEHOLDER,
            modifier = Modifier.weight(1f),
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrect = true,
            maxLines = INPUT_MAX_LINES,
        )

        IconButton(onClick = onSend, enabled = canSend) {
            Icon(
                imageVector = SendIcon,
                contentDescription = Constants.SEND_MESSAGE,
                tint = if (canSend) Accent else InkDim,
            )
        }
    }
}

private val SIDE_PADDING = 12.dp
private val LIST_PADDING = 8.dp
private val REPLY_GAP = 6.dp
private val BUBBLE_MAX_WIDTH = 300.dp
private val BUBBLE_CORNER = 15.dp
private val BUBBLE_PADDING = 12.dp
private val BUBBLE_INNER_PADDING = 8.dp
private const val INPUT_MAX_LINES = 5
