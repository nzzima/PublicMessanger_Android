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
     * @property avatars байты аватаров по идентификатору аккаунта; кого в карте нет — тот
     *   ещё не загрузился либо аватара не имеет.
     * @property online кто сейчас в сети. Пересчитывается сам по тику присутствия, поэтому
     *   точка гаснет без новых снимков — по одному лишь молчанию.
     */
    data class Content(
        val contacts: List<Contact>,
        val avatars: Map<String, ByteArray> = emptyMap(),
        val online: Set<String> = emptySet(),
    ) : ContactsUiState

    /** Подписка отказала. [message] показывается на экране, подписаться можно заново. */
    data class Failed(val message: String) : ContactsUiState
}
