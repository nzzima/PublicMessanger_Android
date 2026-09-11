package com.nzzima.secretmessanger.chats.domain

import com.nzzima.secretmessanger.chats.domain.api.ConversationRepository
import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.ConversationHeader
import com.nzzima.secretmessanger.chats.domain.models.Moment
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
class FakeConversationRepository(
    private val journal: MutableList<String> = mutableListOf(),
) : ConversationRepository {

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

    /** Шапки, лежащие в базе: идентификатор диалога → сам диалог. */
    val stored = mutableMapOf<String, Chat>()

    /** Заведённое через [create], в порядке записи. */
    val created = mutableListOf<Chat>()

    /** Чем отказывает чтение шапки; `null` — читается. */
    var readFails: Throwable? = null

    /** Чем отказывает заведение; `null` — заводится. */
    var createFails: Throwable? = null

    override suspend fun existing(convoId: String, selfId: String): Result<Chat?> =
        readFails?.let { Result.failure(it) } ?: Result.success(stored[convoId])

    /** Отметки прочтения: диалог, чья отметка и докуда — в порядке записи. */
    val receipts = mutableListOf<Triple<String, String, Moment>>()

    override suspend fun markRead(convoId: String, uid: String, upTo: Moment): Result<Unit> {
        receipts += Triple(convoId, uid, upTo)
        return Result.success(Unit)
    }

    /** Разосланные по диалогам имена: чьё и какое. */
    val renames = mutableListOf<Pair<String, String>>()

    /** Чем отказывает рассылка имени; `null` — проходит. */
    var renameFails: Throwable? = null

    override suspend fun renameInConversations(uid: String, login: String): Result<Unit> {
        renameFails?.let { return Result.failure(it) }

        renames += uid to login
        journal += "rename:$login"
        return Result.success(Unit)
    }

    /** Составы, оставшиеся после выхода: диалог → кто в нём остался. */
    val left = mutableMapOf<String, List<String>>()

    /** Чем отказывает выход; `null` — проходит. */
    var leaveFails: Throwable? = null

    override suspend fun leave(convoId: String, uid: String, members: List<String>): Result<Unit> {
        leaveFails?.let { return Result.failure(it) }

        left[convoId] = members - uid
        return Result.success(Unit)
    }

    override suspend fun create(chat: Chat): Result<Unit> {
        createFails?.let { return Result.failure(it) }

        created += chat
        stored[chat.id] = chat
        return Result.success(Unit)
    }

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
    readUpTo: Map<String, Moment> = emptyMap(),
    owner: String = selfId,
) = Chat(
    id = id,
    members = members,
    logins = logins,
    owner = owner,
    selfId = selfId,
    convoKeys = convoKeys,
    keyVersion = keyVersion,
    readUpTo = readUpTo,
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
