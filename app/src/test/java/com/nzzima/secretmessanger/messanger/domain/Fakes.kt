package com.nzzima.secretmessanger.messanger.domain

import com.nzzima.secretmessanger.chats.domain.models.Moment
import com.nzzima.secretmessanger.messanger.domain.api.MessageRepository
import com.nzzima.secretmessanger.messanger.domain.models.Message
import com.nzzima.secretmessanger.messanger.domain.models.MessageKind
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Реплики в памяти.
 *
 * Начального значения нет: до первого [send] подписчик не получает ничего — так же ведёт
 * себя Firestore, пока не пришёл первый снимок.
 */
class FakeMessageRepository : MessageRepository {

    private val snapshots = MutableSharedFlow<Result<List<Message>>>(
        replay = 1,
        extraBufferCapacity = BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Отправленное: диалог и сама реплика, в порядке отправки. */
    val sent = mutableListOf<Pair<String, Message>>()

    /** Чем отказывает отправка; `null` — отправка проходит. */
    var refusal: Throwable? = null

    override fun observeLast(convoId: String, limit: Long): Flow<Result<List<Message>>> = snapshots

    override suspend fun send(convoId: String, message: Message): Result<Unit> {
        refusal?.let { return Result.failure(it) }

        sent += convoId to message
        return Result.success(Unit)
    }

    /** Отдаёт подписчикам очередной снимок. */
    fun send(messages: List<Message>) = snapshots.tryEmit(Result.success(messages))

    /** Отдаёт подписчикам отказ. */
    fun fail(error: Throwable) = snapshots.tryEmit(Result.failure(error))

    private companion object {
        const val BUFFER = 8
    }
}

/** Реплика: минимум полей, за которые цепляются проверки. */
fun message(
    id: String = "m-1",
    senderId: String = "uid-2",
    body: String = "привет",
    encrypted: Boolean = false,
    version: Int = 1,
    date: Moment = Moment(0, 0),
    kind: MessageKind = MessageKind.Text,
) = Message(
    id = id,
    senderId = senderId,
    body = body,
    encrypted = encrypted,
    version = version,
    date = date,
    kind = kind,
)
