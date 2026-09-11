package com.nzzima.secretmessanger.contacts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzzima.secretmessanger.avatar.domain.api.AvatarInteractor
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

/**
 * Состояние экрана контактов.
 *
 * Идентификатор аккаунта берётся из сессии в момент подписки: вкладка достижима только при
 * живой сессии, а выход уводит с неё целиком.
 */
class ContactsViewModel(
    private val sessionInteractor: SessionInteractor,
    private val contactsInteractor: ContactsInteractor,
    private val avatarInteractor: AvatarInteractor,
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
     * Догружает аватары к показанным контактам.
     *
     * Грузятся сразу все, у кого аватар есть, а не по мере появления строк на экране, как на
     * iOS. Разница честная: там список рассчитан на длинный, здесь в нём десяток человек, а
     * повторные загрузки всё равно снимает кэш интерактора. Появятся сотни — придётся
     * грузить по строкам.
     */
    private fun loadAvatars(contacts: List<Contact>) {
        viewModelScope.launch {
            contacts.filter { it.avatarVersion > 0 }.forEach { contact ->
                val image = avatarInteractor.avatar(contact.id, contact.avatarVersion) ?: return@forEach

                contactsScreenState.update { current ->
                    if (current is ContactsUiState.Content) {
                        current.copy(avatars = current.avatars + (contact.id to image))
                    } else {
                        current
                    }
                }
            }
        }
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
                                // Уже загруженные аватары переживают снимок: список
                                // перерисовывается на любое изменение любого профиля, и
                                // ронять картинки на каждое чужое переименование незачем.
                                current is ContactsUiState.Content -> current.copy(contacts = contacts)
                                else -> ContactsUiState.Content(contacts)
                            }
                        }
                        loadAvatars(contacts)
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
