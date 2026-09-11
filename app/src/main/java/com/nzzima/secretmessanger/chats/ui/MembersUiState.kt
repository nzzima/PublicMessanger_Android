package com.nzzima.secretmessanger.chats.ui

import com.nzzima.secretmessanger.contacts.domain.models.Contact

/** Состояние экрана участников группы. */
sealed interface MembersUiState {

    /** Шапка диалога ещё не пришла. */
    data object Loading : MembersUiState

    /**
     * Состав группы.
     *
     * @property members участники: создатель первым, дальше по алфавиту.
     * @property canManage правим ли мы состав — то есть мы ли создатель.
     * @property candidates кого можно добавить: контакты, которых в группе ещё нет.
     * @property chosen отмеченные для добавления.
     * @property adding показан ли список добавления.
     * @property isWorking идёт запись состава; на это время список не принимает нажатий.
     */
    data class Content(
        val members: List<Member>,
        val canManage: Boolean,
        val candidates: List<Contact> = emptyList(),
        val chosen: Set<String> = emptySet(),
        val adding: Boolean = false,
        val isWorking: Boolean = false,
        val error: String? = null,
    ) : MembersUiState

    /** Шапку прочитать не вышло либо диалога больше нет. */
    data class Failed(val message: String) : MembersUiState

    /**
     * Участник в списке.
     *
     * @property isOwner создатель: его не убрать никому.
     * @property isSelf мы сами: себя из состава не убирают — для этого есть выход, и у него
     *   своё правило в базе.
     */
    data class Member(val id: String, val login: String, val isOwner: Boolean, val isSelf: Boolean)
}
