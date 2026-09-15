package com.nzzima.secretmessanger.chats.domain.impl

import com.nzzima.secretmessanger.chats.domain.api.ChatsInteractor
import com.nzzima.secretmessanger.chats.domain.api.ConversationRepository
import com.nzzima.secretmessanger.chats.domain.models.Conversation
import com.nzzima.secretmessanger.chats.domain.models.ConversationHeader
import com.nzzima.secretmessanger.chats.domain.openText
import com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Список диалогов: расшифровка превью; порядок приходит из запроса и здесь не трогается.
 *
 * Сортировки на клиенте больше нет — с 15.09.2026 её делает база составным индексом. Шапка без
 * поля `date` в запрос не попадает вовсе, но такой шапки и не бывает: `date` пишется вместе с
 * `lastMessage`.
 */
class ChatsInteractorImpl(
    private val conversations: ConversationRepository,
    private val conversationKeys: ConversationKeys,
) : ChatsInteractor {

    override fun observeConversations(selfId: String): Flow<Result<List<Conversation>>> =
        conversations.observeHeaders(selfId).map { snapshot ->
            snapshot.map { headers ->
                headers.map { Conversation(chat = it.chat, preview = it.preview(), date = it.date) }
            }
        }

    /**
     * Открытый текст последней реплики.
     *
     * Нечитаемая реплика заменяется [Constants.UNREADABLE] и строку из списка не
     * убирает: диалог существует, и молчать о нём хуже, чем показать замок.
     *
     * **Диалог без единой реплики тоже остаётся в списке** — с 11.09.2026. До этого он
     * отбрасывался, и на диалоге на двоих это было безобидно: туда можно вернуться из профиля
     * собеседника. Заведённая группа так пропадала насовсем — другого входа в неё нет, и
     * человек терял то, что только что собрал.
     */
    private fun ConversationHeader.preview(): String {
        if (lastMessage.isEmpty()) return Constants.MESSAGES_EMPTY

        if (!encrypted) return lastMessage

        return conversationKeys.openText(chat, lastMessage, version) ?: Constants.UNREADABLE
    }
}
