package com.nzzima.secretmessanger.chats.domain.impl

import com.nzzima.secretmessanger.chats.domain.api.ChatEraser
import com.nzzima.secretmessanger.chats.domain.api.ConversationRepository
import com.nzzima.secretmessanger.chats.domain.models.CannotErase
import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys
import com.nzzima.secretmessanger.messanger.domain.api.MessageRepository
import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Удаление переписки каскадом с клиента.
 *
 * Каскад делает клиент, а не сервер: Cloud Functions требуют платного тарифа — того самого,
 * из-за которого здесь нет и Cloud Storage.
 */
class ChatEraserImpl(
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val conversationKeys: ConversationKeys,
) : ChatEraser {

    override suspend fun erase(chat: Chat): Result<Unit> {
        if (!chat.canErase) return Result.failure(CannotErase())

        // Страницами, пока не опустеет: одним запросом всю переписку не забрать, а батч
        // Firestore держит 500 операций — на реплику их уходит до двух.
        while (true) {
            val erased = messages.erasePage(chat.id, Constants.ERASE_PAGE)
                .getOrElse { return Result.failure(it) }

            if (erased == 0) break
        }

        conversations.erase(chat.id).onFailure { return Result.failure(it) }

        // Ключи забываются только теперь: оборвись удаление раньше, диалог остался бы в
        // списке, и превью в нём перестало бы читаться на ровном месте.
        conversationKeys.forget(chat.id)

        return Result.success(Unit)
    }
}
