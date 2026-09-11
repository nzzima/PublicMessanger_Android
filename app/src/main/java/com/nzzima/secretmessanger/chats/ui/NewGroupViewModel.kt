package com.nzzima.secretmessanger.chats.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzzima.secretmessanger.chats.domain.api.ConversationStarter
import com.nzzima.secretmessanger.contacts.domain.api.ContactsInteractor
import com.nzzima.secretmessanger.session.domain.api.SessionInteractor
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Выбор участников новой группы.
 *
 * Список тот же, что в «Контактах», — все зарегистрированные, кроме себя. Своего состояния
 * присутствия и аватаров здесь нет: экран живёт секунды, и грузить ради него лица незачем.
 */
class NewGroupViewModel(
    private val sessionInteractor: SessionInteractor,
    private val contactsInteractor: ContactsInteractor,
    private val conversationStarter: ConversationStarter,
) : ViewModel() {

    private val newGroupScreenState = MutableStateFlow<NewGroupUiState>(NewGroupUiState.Loading)
    private var subscription: Job? = null

    /** Текущее состояние экрана. */
    fun observeNewGroupScreenState(): StateFlow<NewGroupUiState> = newGroupScreenState.asStateFlow()

    init {
        subscribe()
    }

    /** Подписывается на список заново — нужна после отказа. */
    fun retry() = subscribe()

    /** Отмечает или снимает участника. */
    fun onToggle(uid: String) = newGroupScreenState.update { current ->
        if (current !is NewGroupUiState.Content || current.isCreating) return@update current

        val chosen = if (uid in current.chosen) current.chosen - uid else current.chosen + uid

        current.copy(chosen = chosen, error = null)
    }

    /**
     * Заводит группу из отмеченных.
     *
     * Срок тот же, что у отправки: заведение — три записи в базу, и висеть на них дольше
     * человеку не за что.
     */
    fun onCreate() {
        val state = newGroupScreenState.value as? NewGroupUiState.Content ?: return
        if (!state.canCreate) return

        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return
        val members = state.contacts.filter { it.id in state.chosen }.associate { it.id to it.login }

        newGroupScreenState.update { current ->
            if (current is NewGroupUiState.Content) current.copy(isCreating = true, error = null) else current
        }

        viewModelScope.launch {
            val result = withTimeoutOrNull(Constants.SUBMIT_TIMEOUT_MS) {
                conversationStarter.startGroup(uid, members)
            }

            newGroupScreenState.update { current ->
                if (current !is NewGroupUiState.Content) return@update current

                when {
                    result == null -> current.copy(isCreating = false, error = Constants.SERVER_SILENT)

                    result.isSuccess -> current.copy(isCreating = false, created = result.getOrNull())

                    else -> current.copy(
                        isCreating = false,
                        error = result.exceptionOrNull()?.message ?: Constants.SERVER_SILENT,
                    )
                }
            }
        }
    }

    /** Экран открыл группу — поручение снимается, чтобы не открыть её второй раз. */
    fun onOpened() = newGroupScreenState.update { current ->
        if (current is NewGroupUiState.Content) current.copy(created = null) else current
    }

    private fun subscribe() {
        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return

        subscription?.cancel()
        newGroupScreenState.value = NewGroupUiState.Loading

        subscription = viewModelScope.launch {
            contactsInteractor.observeContacts(uid).collect { snapshot ->
                snapshot
                    .onSuccess { contacts ->
                        newGroupScreenState.update { current ->
                            when {
                                contacts.isEmpty() -> NewGroupUiState.Empty
                                // Отметки переживают снимок: список перечитывается на любое
                                // чужое переименование, и терять из-за него выбор незачем.
                                current is NewGroupUiState.Content -> current.copy(contacts = contacts)
                                else -> NewGroupUiState.Content(contacts)
                            }
                        }
                    }
                    .onFailure {
                        newGroupScreenState.value =
                            NewGroupUiState.Failed(it.message ?: Constants.SERVER_SILENT)
                    }
            }
        }
    }
}
