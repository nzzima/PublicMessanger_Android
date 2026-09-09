package com.nzzima.secretmessanger.chats.domain

import com.nzzima.secretmessanger.chats.domain.api.ConversationRepository
import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.ConversationHeader
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart

/**
 * Шапки диалогов в памяти.
 *
 * Начального значения нет: до первого [send] подписчик не получает ничего — так же ведёт
 * себя Firestore, пока не пришёл первый снимок.
 */
class FakeConversationRepository : ConversationRepository {

    private val snapshots = MutableSharedFlow<Result<List<ConversationHeader>>>(
        replay = 1,
        extraBufferCapacity = BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Сколько раз на поток подписывались. */
    var subscriptions = 0
        private set

    /** Идентификатор, с которым запросили последнюю подписку. */
    var requestedFor: String? = null
        private set

    override fun observeHeaders(selfId: String): Flow<Result<List<ConversationHeader>>> =
        snapshots.onStart {
            subscriptions++
            requestedFor = selfId
        }

    /** Отдаёт подписчикам очередной снимок. */
    fun send(headers: List<ConversationHeader>) = snapshots.tryEmit(Result.success(headers))

    /** Отдаёт подписчикам отказ. */
    fun fail(error: Throwable) = snapshots.tryEmit(Result.failure(error))

    private val single = MutableSharedFlow<Result<Chat>>(
        replay = 1,
        extraBufferCapacity = BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Идентификатор диалога, с которым запросили последнюю подписку на шапку. */
    var requestedChat: String? = null
        private set

    override fun observeChat(convoId: String, selfId: String): Flow<Result<Chat>> =
        single.onStart { requestedChat = convoId }

    /** Отдаёт подписчикам очередную шапку. */
    fun sendChat(chat: Chat) = single.tryEmit(Result.success(chat))

    /** Отдаёт подписчикам отказ по шапке. */
    fun failChat(error: Throwable) = single.tryEmit(Result.failure(error))

    private companion object {
        const val BUFFER = 8
    }
}

/** Диалог на двоих: минимум полей, за которые цепляются проверки. */
fun chat(
    id: String = "uid-1_uid-2",
    selfId: String = "uid-1",
    members: List<String> = listOf("uid-1", "uid-2"),
    logins: Map<String, String> = mapOf("uid-1" to "self", "uid-2" to "companion"),
    convoKeys: Map<String, String> = emptyMap(),
    keyVersion: Int = 1,
) = Chat(
    id = id,
    members = members,
    logins = logins,
    owner = selfId,
    selfId = selfId,
    convoKeys = convoKeys,
    keyVersion = keyVersion,
)

/** Шапка с открытой последней репликой; шифрованные собираются в самих проверках. */
fun header(
    chat: Chat = chat(),
    lastMessage: String = "привет",
    encrypted: Boolean = false,
    version: Int = chat.keyVersion,
    date: Long = 0,
) = ConversationHeader(
    chat = chat,
    lastMessage = lastMessage,
    encrypted = encrypted,
    version = version,
    date = date,
)
