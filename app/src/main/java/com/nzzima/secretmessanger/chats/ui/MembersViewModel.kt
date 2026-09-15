package com.nzzima.secretmessanger.chats.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzzima.secretmessanger.chats.domain.api.GroupEditor
import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.contacts.domain.api.ContactsInteractor
import com.nzzima.secretmessanger.session.domain.api.SessionInteractor
import com.nzzima.secretmessanger.utils.constants.Constants
import com.nzzima.secretmessanger.utils.errors.ErrorText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Состав группы: показать, добавить, убрать.
 *
 * Слушает ту же шапку, что и переписка, своей подпиской: состав может поменять создатель,
 * пока этот экран открыт у кого-то другого.
 */
class MembersViewModel(
    private val convoId: String,
    private val sessionInteractor: SessionInteractor,
    private val groupEditor: GroupEditor,
    private val contactsInteractor: ContactsInteractor,
) : ViewModel() {

    private val membersScreenState = MutableStateFlow<MembersUiState>(MembersUiState.Loading)
    private var subscription: Job? = null
    private var contacts: Job? = null

    /** Последняя шапка: ею правится состав. */
    private var chat: Chat? = null

    /** Текущее состояние экрана. */
    fun observeMembersScreenState(): StateFlow<MembersUiState> = membersScreenState.asStateFlow()

    init {
        subscribe()
    }

    /** Читает состав заново — нужна после отказа. */
    fun retry() = subscribe()

    /** Открывает список добавления. */
    fun onAddAsked() = update { it.copy(adding = true, chosen = emptySet(), error = null) }

    /** Закрывает список добавления, не добавив никого. */
    fun onAddDismissed() = update { it.copy(adding = false, chosen = emptySet()) }

    /** Отмечает или снимает кандидата. */
    fun onToggle(uid: String) = update { current ->
        if (current.isWorking) return@update current

        val chosen = if (uid in current.chosen) current.chosen - uid else current.chosen + uid

        current.copy(chosen = chosen, error = null)
    }

    /** Добавляет отмеченных. */
    fun onAddConfirmed() {
        val state = membersScreenState.value as? MembersUiState.Content ?: return
        val chat = chat ?: return
        if (state.chosen.isEmpty() || state.isWorking) return

        val added = state.candidates.filter { it.id in state.chosen }.associate { it.id to it.login }

        working { groupEditor.add(chat, added) }
    }

    /** Убирает участника. */
    fun onRemove(uid: String) {
        val chat = chat ?: return
        if ((membersScreenState.value as? MembersUiState.Content)?.isWorking == true) return

        working { groupEditor.remove(chat, uid) }
    }

    /**
     * Общая часть обеих правок: признак работы, срок и разбор итога.
     *
     * Новый состав приезжает подпиской, а не ответом: шапку слушают все, и экран обновится
     * тем же путём, что у остальных.
     */
    private fun working(change: suspend () -> Result<Unit>) {
        update { it.copy(isWorking = true, error = null) }

        viewModelScope.launch {
            val result = withTimeoutOrNull(Constants.SUBMIT_TIMEOUT_MS) { change() }

            update { current ->
                when {
                    result == null -> current.copy(isWorking = false, error = Constants.SERVER_SILENT)

                    result.isSuccess -> current.copy(isWorking = false, adding = false, chosen = emptySet())

                    else -> current.copy(
                        isWorking = false,
                        error = ErrorText.of(result.exceptionOrNull()),
                    )
                }
            }
        }
    }

    private fun subscribe() {
        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return

        subscription?.cancel()
        membersScreenState.value = MembersUiState.Loading

        subscription = viewModelScope.launch {
            groupEditor.observe(convoId, uid).collect { snapshot ->
                snapshot
                    .onSuccess { updated ->
                        chat = updated
                        show(updated)
                    }
                    .onFailure {
                        membersScreenState.value =
                            MembersUiState.Failed(ErrorText.of(it))
                    }
            }
        }

        contacts?.cancel()
        contacts = viewModelScope.launch {
            contactsInteractor.observeContacts(uid).collect { snapshot ->
                val all = snapshot.getOrNull() ?: return@collect

                update { current ->
                    current.copy(candidates = all.filterNot { it.id in (chat?.members ?: emptyList()) })
                }
            }
        }
    }

    /** Складывает состав в список: создатель первым, остальные по алфавиту. */
    private fun show(chat: Chat) {
        val members = chat.members
            .map {
                MembersUiState.Member(
                    id = it,
                    login = chat.logins[it].orEmpty(),
                    isOwner = it == chat.owner,
                    isSelf = it == chat.selfId,
                )
            }
            .sortedWith(compareByDescending<MembersUiState.Member> { it.isOwner }.thenBy { it.login })

        membersScreenState.update { current ->
            val candidates = (current as? MembersUiState.Content)?.candidates.orEmpty()
                .filterNot { it.id in chat.members }

            if (current is MembersUiState.Content) {
                current.copy(members = members, canManage = chat.owner == chat.selfId, candidates = candidates)
            } else {
                MembersUiState.Content(members, canManage = chat.owner == chat.selfId, candidates = candidates)
            }
        }
    }

    private fun update(change: (MembersUiState.Content) -> MembersUiState.Content) =
        membersScreenState.update { current ->
            if (current is MembersUiState.Content) change(current) else current
        }
}
