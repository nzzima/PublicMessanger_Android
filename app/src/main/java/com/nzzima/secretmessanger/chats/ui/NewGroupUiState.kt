package com.nzzima.secretmessanger.chats.ui

import com.nzzima.secretmessanger.contacts.domain.models.Contact

/** Состояние экрана новой группы. */
sealed interface NewGroupUiState {

    /** Список контактов ещё не пришёл. */
    data object Loading : NewGroupUiState

    /** Кроме владельца никто не зарегистрирован — собирать группу не из кого. */
    data object Empty : NewGroupUiState

    /**
     * Выбор участников.
     *
     * @property chosen отмеченные; группа начинается с двух, потому что с одним это диалог
     *   на двоих, и заводить его надо из «Контактов» — там он попадёт в существующий.
     * @property isCreating идёт заведение; на это время список не принимает нажатий.
     * @property created идентификатор заведённой группы — поручение экрану открыть её.
     */
    data class Content(
        val contacts: List<Contact>,
        val chosen: Set<String> = emptySet(),
        val isCreating: Boolean = false,
        val created: String? = null,
        val error: String? = null,
    ) : NewGroupUiState {

        /** Есть ли из кого собирать группу и не идёт ли заведение уже. */
        val canCreate: Boolean get() = chosen.size >= MIN_MEMBERS && !isCreating

        private companion object {
            const val MIN_MEMBERS = 2
        }
    }

    /** Подписка на контакты отказала. */
    data class Failed(val message: String) : NewGroupUiState
}
