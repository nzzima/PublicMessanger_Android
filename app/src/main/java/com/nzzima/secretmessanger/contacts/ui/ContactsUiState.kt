package com.nzzima.secretmessanger.contacts.ui

import com.nzzima.secretmessanger.contacts.domain.models.Contact

/** Состояние экрана контактов. */
sealed interface ContactsUiState {

    /** Первый снимок ещё не пришёл. */
    data object Loading : ContactsUiState

    /** Кроме владельца никто не зарегистрирован. */
    data object Empty : ContactsUiState

    /**
     * [contacts] — по алфавиту, пустым список здесь не бывает.
     *
     * @property isOpening идёт заведение диалога; на это время нажатия не принимаются.
     * @property opened диалог, который надо открыть. Разовое поручение экрану: тот
     *   переходит и снимает его [ContactsViewModel.onOpened].
     * @property error причина, по которой диалог не завёлся.
     */
    data class Content(
        val contacts: List<Contact>,
        val isOpening: Boolean = false,
        val opened: String? = null,
        val error: String? = null,
    ) : ContactsUiState

    /** Подписка отказала. [message] показывается на экране, подписаться можно заново. */
    data class Failed(val message: String) : ContactsUiState
}
