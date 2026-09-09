package com.nzzima.secretmessanger.contacts.ui

import com.nzzima.secretmessanger.contacts.domain.models.Contact

/** Состояние экрана контактов. */
sealed interface ContactsUiState {

    /** Первый снимок ещё не пришёл. */
    data object Loading : ContactsUiState

    /** Кроме владельца никто не зарегистрирован. */
    data object Empty : ContactsUiState

    /** [contacts] — по алфавиту, пустым список здесь не бывает. */
    data class Content(val contacts: List<Contact>) : ContactsUiState

    /** Подписка отказала. [message] показывается на экране, подписаться можно заново. */
    data class Failed(val message: String) : ContactsUiState
}
