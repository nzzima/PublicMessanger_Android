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
     * @property online кто из собеседников сейчас в сети. Пересчитывается сам по тику
     *   присутствия, поэтому точка гаснет без новых снимков — по одному молчанию.
     * @property asking диалог, про удаление которого спрашиваем; `null` — не спрашиваем.
     * @property isErasing идёт стирание: подтверждение уже нажато.
     * @property error что не так со стиранием. Показывается в том же вопросе: не удалось —
     *   вопрос остаётся открытым, и повтор здесь же.
     */
    data class Content(
        val conversations: List<Conversation>,
        val avatars: Map<String, ByteArray> = emptyMap(),
        val online: Set<String> = emptySet(),
        val asking: Conversation? = null,
        val isErasing: Boolean = false,
        val error: String? = null,
    ) : ChatsUiState

    /** Подписка отказала. [message] показывается на экране, подписаться можно заново. */
    data class Failed(val message: String) : ChatsUiState
}
