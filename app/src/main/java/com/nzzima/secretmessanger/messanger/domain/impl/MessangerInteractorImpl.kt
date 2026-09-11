package com.nzzima.secretmessanger.messanger.domain.impl

import com.nzzima.secretmessanger.chats.domain.api.ConversationRepository
import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.Moment
import com.nzzima.secretmessanger.chats.domain.openText
import com.nzzima.secretmessanger.chats.domain.sealText
import com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys
import com.nzzima.secretmessanger.crypto.domain.models.CryptoFailure
import com.nzzima.secretmessanger.messanger.domain.api.MessageRepository
import com.nzzima.secretmessanger.messanger.domain.api.MessangerInteractor
import com.nzzima.secretmessanger.messanger.domain.models.Dialogue
import com.nzzima.secretmessanger.messanger.domain.models.Message
import com.nzzima.secretmessanger.messanger.domain.models.MessageKind
import com.nzzima.secretmessanger.messanger.domain.models.Reply
import com.nzzima.secretmessanger.utils.constants.Constants
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Переписка: сборка ленты из двух подписок и отправка текста. */
class MessangerInteractorImpl(
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val conversationKeys: ConversationKeys,
) : MessangerInteractor {

    /**
     * Шапка и реплики складываются здесь, а не в двух состояниях экрана: расшифровка
     * требует обоих, и приход нового ключа обязан перечитать уже показанные реплики.
     */
    override fun observeDialogue(convoId: String, selfId: String): Flow<Result<Dialogue>> = combine(
        conversations.observeChat(convoId, selfId),
        messages.observeLast(convoId, Constants.MESSAGE_WINDOW),
    ) { header, batch ->
        // Отказ любой из двух подписок становится отказом всего экрана: getOrThrow
        // внутри mapCatching возвращает его сюда, не роняя поток.
        header.mapCatching { chat ->
            val messages = batch.getOrThrow()

            Dialogue(
                chat = chat,
                replies = messages.map { it.reply(chat) },
                lastIncoming = messages.lastOrNull { it.senderId != chat.selfId }?.date,
            )
        }
    }

    override suspend fun send(chat: Chat, text: String): Result<Unit> {
        val payload = if (chat.isEncrypted) {
            conversationKeys.sealText(chat, text) ?: return Result.failure(CryptoFailure.NoKey)
        } else {
            text
        }

        return messages.send(
            convoId = chat.id,
            message = Message(
                // Идентификатор задаётся здесь, а не базой: тот же формат, что на iOS.
                id = UUID.randomUUID().toString(),
                senderId = chat.selfId,
                body = payload,
                encrypted = chat.isEncrypted,
                version = chat.keyVersion,
                date = Moment.of(System.currentTimeMillis()),
                kind = MessageKind.Text,
            ),
        )
    }

    override suspend fun markRead(chat: Chat, upTo: Moment): Result<Unit> {
        // Отметка только растёт: повторная запись того же разбудила бы слушателя шапки у
        // собеседника впустую.
        if (chat.readUpTo[chat.selfId]?.let { it >= upTo } == true) return Result.success(Unit)

        return conversations.markRead(chat.id, chat.selfId, upTo)
    }

    private fun Message.reply(chat: Chat) = Reply(
        id = id,
        text = text(chat),
        // Подпись нужна только чужим репликам в группе: в диалоге на двоих автор
        // очевиден из стороны пузыря.
        author = if (chat.isGroup && senderId != chat.selfId) chat.logins[senderId].orEmpty() else "",
        authorId = senderId,
        authorName = chat.logins[senderId].orEmpty(),
        outgoing = senderId == chat.selfId,
        date = date.millis,
        // Галочки только на своих: чужой реплике «прочитано» ничего не сообщает.
        read = senderId == chat.selfId && chat.isRead(date, chat.selfId),
        service = kind == MessageKind.KeyNotice,
    )

    /**
     * Текст реплики для ленты.
     *
     * Вложения подменяются пометкой вида и не расшифровываются вовсе: показать снимок или
     * проиграть запись Android пока нечем, а замок на месте фотографии врал бы — ключ у
     * нас есть, дела с ним ещё нет. Геопозиция по той же причине: без карты две координаты
     * ничего не сообщают.
     */
    private fun Message.text(chat: Chat): String = when (kind) {
        MessageKind.Voice -> Constants.VOICE_MESSAGE
        MessageKind.Photo -> Constants.PHOTO_MESSAGE
        MessageKind.Location -> Constants.LOCATION_MESSAGE
        MessageKind.KeyNotice -> Constants.KEY_ROTATED
        MessageKind.Text ->
            if (encrypted) conversationKeys.openText(chat, body, version) ?: Constants.UNREADABLE else body
    }
}
