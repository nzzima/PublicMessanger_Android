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
 * Список диалогов: отсев пустых, расшифровка превью, сортировка.
 *
 * Сортировка идёт здесь, а не в запросе: пара `arrayContains` + `orderBy` требует
 * составного индекса, которого в базе нет. На десятках диалогов разницы никакой.
 */
class ChatsInteractorImpl(
    private val conversations: ConversationRepository,
    private val conversationKeys: ConversationKeys,
) : ChatsInteractor {

    override fun observeConversations(selfId: String): Flow<Result<List<Conversation>>> =
        conversations.observeHeaders(selfId).map { snapshot ->
            snapshot.map { headers ->
                headers
                    .filter { it.lastMessage.isNotEmpty() }
                    .map { Conversation(chat = it.chat, preview = it.preview(), date = it.date) }
                    .sortedByDescending { it.date }
            }
        }

    /**
     * Открытый текст последней реплики.
     *
     * Нечитаемая реплика заменяется [Constants.UNREADABLE] и строку из списка не
     * убирает: диалог существует, и молчать о нём хуже, чем показать замок.
     */
    private fun ConversationHeader.preview(): String {
        if (!encrypted) return lastMessage

        return conversationKeys.openText(chat, lastMessage, version) ?: Constants.UNREADABLE
    }
}
