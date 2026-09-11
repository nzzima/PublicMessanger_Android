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
import com.nzzima.secretmessanger.messanger.domain.models.Place
import com.nzzima.secretmessanger.messanger.domain.models.PhotoAttachment
import com.nzzima.secretmessanger.messanger.domain.models.Reply
import com.nzzima.secretmessanger.photo.domain.api.PhotoInteractor
import com.nzzima.secretmessanger.photo.domain.models.PhotoSize
import com.nzzima.secretmessanger.utils.constants.Constants
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Переписка: сборка ленты из двух подписок и отправка текста, снимка или точки. */
class MessangerInteractorImpl(
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val conversationKeys: ConversationKeys,
    private val photos: PhotoInteractor,
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
        val payload = sealed(chat, text) ?: return Result.failure(CryptoFailure.NoKey)

        return write(chat, MessageKind.Text, payload, preview = payload)
    }

    override suspend fun sendPhoto(chat: Chat, source: String): Result<Unit> {
        // Идентификатор нужен раньше записи: под ним лягут и байты, и сообщение о них.
        val messageId = UUID.randomUUID().toString()
        val size = photos.attach(chat, messageId, source).getOrElse { return Result.failure(it) }
        val payload = sealed(chat, Constants.PHOTO_MESSAGE) ?: return Result.failure(CryptoFailure.NoKey)

        return write(chat, MessageKind.Photo, payload, preview = payload, id = messageId, size = size)
    }

    override suspend fun sendLocation(chat: Chat, place: Place): Result<Unit> {
        val payload = sealed(chat, Place.payload(place.latitude, place.longitude))
            ?: return Result.failure(CryptoFailure.NoKey)
        val preview = sealed(chat, Constants.LOCATION_MESSAGE) ?: return Result.failure(CryptoFailure.NoKey)

        return write(chat, MessageKind.Location, payload, preview)
    }

    /**
     * Запечатывает [text] текущим ключом; у диалога без шифрования отдаёт как есть.
     *
     * Диалог без ключей — начатый до появления шифрования: его история и так лежит в базе
     * читаемой, а задним числом её не зашифровать.
     */
    private fun sealed(chat: Chat, text: String): String? =
        if (chat.isEncrypted) conversationKeys.sealText(chat, text) else text

    private suspend fun write(
        chat: Chat,
        kind: MessageKind,
        payload: String,
        preview: String,
        // Идентификатор задаётся здесь, а не базой: тот же формат, что на iOS.
        id: String = UUID.randomUUID().toString(),
        size: PhotoSize? = null,
    ): Result<Unit> = messages.send(
        convoId = chat.id,
        message = Message(
            id = id,
            senderId = chat.selfId,
            body = payload,
            encrypted = chat.isEncrypted,
            version = chat.keyVersion,
            date = Moment.of(System.currentTimeMillis()),
            kind = kind,
            size = size,
        ),
        preview = preview,
    )

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
        photo = size?.takeIf { kind == MessageKind.Photo }?.let { PhotoAttachment(it, version) },
        place = place(chat),
    )

    /**
     * Точка из тела реплики; `null` — реплика не точка либо координаты не разобрались.
     *
     * Не разобраться они могут по двум причинам: ключа этой версии у нас нет или прислано
     * что-то другое. Обе показываются одинаково — подписью «📍 Геопозиция» без карты: врать
     * точкой посреди океана хуже, чем не показать её вовсе.
     */
    private fun Message.place(chat: Chat): Place? {
        if (kind != MessageKind.Location) return null

        val payload = if (encrypted) conversationKeys.openText(chat, body, version) ?: return null else body

        return Place.parse(payload)
    }

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
