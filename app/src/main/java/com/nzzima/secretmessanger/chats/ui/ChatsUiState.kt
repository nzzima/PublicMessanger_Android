package com.nzzima.secretmessanger.chats.ui

import com.nzzima.secretmessanger.chats.domain.models.Conversation

/** Состояние экрана списка диалогов. */
sealed interface ChatsUiState {

    /** Первый снимок ещё не пришёл. */
    data object Loading : ChatsUiState

    /** Диалогов нет ни одного. */
    data object Empty : ChatsUiState

    /**
     * [conversations] — свежие сверху, пустым список здесь не бывает.
     *
     * @property avatars байты аватаров по идентификатору собеседника; кого в карте нет — тот
     *   ещё не загрузился, аватара не имеет либо это группа, у которой его и не бывает.
     */
    data class Content(
        val conversations: List<Conversation>,
        val avatars: Map<String, ByteArray> = emptyMap(),
    ) : ChatsUiState

    /** Подписка отказала. [message] показывается на экране, подписаться можно заново. */
    data class Failed(val message: String) : ChatsUiState
}
