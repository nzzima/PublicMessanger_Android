package com.nzzima.secretmessanger.contacts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzzima.secretmessanger.chats.domain.api.ConversationStarter
import com.nzzima.secretmessanger.contacts.domain.api.ContactsInteractor
import com.nzzima.secretmessanger.contacts.domain.models.Contact
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
 * Состояние экрана контактов.
 *
 * Идентификатор аккаунта берётся из сессии в момент подписки: вкладка достижима только при
 * живой сессии, а выход уводит с неё целиком.
 */
class ContactsViewModel(
    private val sessionInteractor: SessionInteractor,
    private val contactsInteractor: ContactsInteractor,
    private val conversationStarter: ConversationStarter,
) : ViewModel() {

    private val contactsScreenState = MutableStateFlow<ContactsUiState>(ContactsUiState.Loading)
    private var subscription: Job? = null

    /** Текущее состояние экрана. */
    fun observeContactsScreenState(): StateFlow<ContactsUiState> = contactsScreenState.asStateFlow()

    init {
        subscribe()
    }

    /** Подписывается на список заново — нужна после отказа. */
    fun retry() = subscribe()

    /**
     * Открывает переписку с [contact], заводя диалог, если его ещё нет.
     *
     * Ограничено [Constants.SUBMIT_TIMEOUT_MS]: за строкой стоит чтение профиля собеседника
     * и запись шапки, и молчащая сеть подвесила бы список навсегда.
     */
    fun onContactTap(contact: Contact) {
        val state = contactsScreenState.value as? ContactsUiState.Content ?: return
        if (state.isOpening) return

        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return

        contactsScreenState.update { current ->
            if (current is ContactsUiState.Content) current.copy(isOpening = true, error = null) else current
        }

        viewModelScope.launch {
            val result = withTimeoutOrNull(Constants.SUBMIT_TIMEOUT_MS) {
                conversationStarter.start(uid, contact)
            }

            contactsScreenState.update { current ->
                if (current !is ContactsUiState.Content) return@update current

                when {
                    result == null -> current.copy(isOpening = false, error = Constants.SERVER_SILENT)
                    result.isSuccess -> current.copy(isOpening = false, opened = result.getOrNull())
                    else -> current.copy(
                        isOpening = false,
                        error = result.exceptionOrNull()?.message ?: Constants.SERVER_SILENT,
                    )
                }
            }
        }
    }

    /** Снимает поручение открыть диалог — экран уже перешёл. */
    fun onOpened() = contactsScreenState.update { current ->
        if (current is ContactsUiState.Content) current.copy(opened = null) else current
    }

    private fun subscribe() {
        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return

        subscription?.cancel()
        contactsScreenState.value = ContactsUiState.Loading

        subscription = viewModelScope.launch {
            contactsInteractor.observeContacts(uid).collect { snapshot ->
                snapshot
                    .onSuccess { contacts ->
                        contactsScreenState.update { current ->
                            when {
                                contacts.isEmpty() -> ContactsUiState.Empty
                                // Новый снимок не отменяет начатого открытия и не стирает
                                // показанный отказ: список меняется сам по себе, от входа
                                // любого нового человека.
                                current is ContactsUiState.Content -> current.copy(contacts = contacts)
                                else -> ContactsUiState.Content(contacts)
                            }
                        }
                    }
                    .onFailure {
                        contactsScreenState.value = ContactsUiState.Failed(it.message ?: Constants.SERVER_SILENT)
                        // Отказ Firestore не отличает мёртвую сессию от обрыва связи, а
                        // «Повторить» лечит только второе. Проверка разводит эти два случая:
                        // мёртвая сессия уводит с вкладок целиком.
                        sessionInteractor.revalidate()
                    }
            }
        }
    }
}
