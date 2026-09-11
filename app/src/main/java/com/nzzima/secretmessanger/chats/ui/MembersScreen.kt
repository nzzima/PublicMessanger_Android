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
import com.nzzima.secretmessanger.ui.theme.Accent
import com.nzzima.secretmessanger.ui.theme.ErrorColor
import com.nzzima.secretmessanger.ui.theme.Ink
import com.nzzima.secretmessanger.ui.theme.InkDim
import com.nzzima.secretmessanger.utils.constants.Constants
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Экран участников группы.
 *
 * Правки видны сразу всем, кто смотрит: экран слушает ту же шапку, что и переписка, — состав
 * может поменять создатель, пока список открыт у другого.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    convoId: String,
    backTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MembersViewModel = koinViewModel { parametersOf(convoId) },
) {
    val state by viewModel.observeMembersScreenState().collectAsStateWithLifecycle()
    val content = state as? MembersUiState.Content

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(Constants.MEMBERS_TITLE) },
                navigationIcon = { BackButton(backTitle, onBack) },
                actions = {
                    // Кнопка только у создателя: состав правит он, и правила согласны только
                    // с ним. Показывать её остальным значило бы обещать отказ по правам.
                    if (content?.canManage == true) {
                        TextButton(
                            onClick = if (content.adding) viewModel::onAddConfirmed else viewModel::onAddAsked,
                            enabled = !content.adding || content.chosen.isNotEmpty(),
                        ) {
                            Text(
                                text = if (content.adding) Constants.CREATE_GROUP else Constants.ADD_MEMBERS,
                                color = if (!content.adding || content.chosen.isNotEmpty()) Accent else InkDim,
                                fontSize = 15.sp,
                            )
                        }
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
                MembersUiState.Loading -> CircularProgressIndicator()

                is MembersUiState.Content -> MemberList(current, viewModel)

                is MembersUiState.Failed -> FailureNotice(current.message, viewModel::retry)
            }
        }
    }
}

@Composable
private fun MemberList(state: MembersUiState.Content, viewModel: MembersViewModel) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        state.error?.let { message ->
            item {
                Text(
                    text = message,
                    color = ErrorColor,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE_PADDING, vertical = 10.dp),
                )
            }
        }

        if (state.adding) {
            item { Caption(Constants.ADD_MEMBERS) }

            items(state.candidates, key = { it.id }) { contact ->
                CandidateRow(contact, contact.id in state.chosen, viewModel::onToggle)
                HorizontalDivider(color = MaterialTheme.colorScheme.surface)
            }

            item {
                TextButton(
                    onClick = viewModel::onAddDismissed,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Text(Constants.CANCEL, color = InkDim, fontSize = 15.sp)
                }
            }

            return@LazyColumn
        }

        items(state.members, key = { it.id }) { member ->
            MemberRow(member, state.canManage && !member.isSelf && !member.isOwner, viewModel::onRemove)
            HorizontalDivider(color = MaterialTheme.colorScheme.surface)
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        color = InkDim,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE_PADDING, vertical = 10.dp),
    )
}

/** Строка участника: имя, пометка и «убрать» — там, где убирать вправе. */
@Composable
private fun MemberRow(member: MembersUiState.Member, removable: Boolean, onRemove: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE_PADDING, vertical = ROW_PADDING),
    ) {
        Text(
            text = member.login,
            color = Ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Пометка вместо отдельного столбца: создателя и себя в списке из пяти человек надо
        // узнавать с одного взгляда, а не вычислять.
        member.mark()?.let {
            Text(text = " · $it", color = InkDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        } ?: Box(Modifier.weight(1f))

        if (removable) {
            TextButton(onClick = { onRemove(member.id) }) {
                Text(Constants.REMOVE_MEMBER, color = ErrorColor, fontSize = 14.sp)
            }
        }
    }
}

private fun MembersUiState.Member.mark(): String? = when {
    isSelf -> Constants.YOU_MARK
    isOwner -> Constants.OWNER_MARK
    else -> null
}

/** Строка кандидата: имя и галочка. */
@Composable
private fun CandidateRow(contact: Contact, chosen: Boolean, onToggle: (String) -> Unit) {
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
