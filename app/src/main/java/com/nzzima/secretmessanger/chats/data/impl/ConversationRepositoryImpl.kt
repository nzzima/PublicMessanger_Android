package com.nzzima.secretmessanger.chats.data.impl

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.nzzima.secretmessanger.chats.domain.api.ConversationRepository
import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.ConversationGone
import com.nzzima.secretmessanger.chats.domain.models.ConversationHeader
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [ConversationRepository] поверх Firestore.
 *
 * Запрос `arrayContains(users, selfId)` совпадает с правилом чтения на `conversation/{id}`:
 * чужие диалоги база не отдаёт.
 */
class ConversationRepositoryImpl(private val firestore: FirebaseFirestore) : ConversationRepository {

    override fun observeHeaders(selfId: String): Flow<Result<List<ConversationHeader>>> = callbackFlow {
        val registration = firestore.collection(Constants.CONVERSATION_COLLECTION)
            .whereArrayContains(Constants.USERS_FIELD, selfId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    close()
                    return@addSnapshotListener
                }

                val documents = snapshot?.documents ?: return@addSnapshotListener
                trySend(Result.success(documents.mapNotNull { it.toHeader(selfId) }))
            }

        awaitClose(registration::remove)
    }

    override fun observeChat(convoId: String, selfId: String): Flow<Result<Chat>> = callbackFlow {
        val registration = firestore.collection(Constants.CONVERSATION_COLLECTION)
            .document(convoId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    close()
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                // Документа нет либо нас нет в составе — показывать нечего, и следующий
                // снимок этого не исправит: диалог стёрт.
                val chat = if (snapshot.exists()) snapshot.toChat(selfId) else null

                if (chat == null) {
                    trySend(Result.failure(ConversationGone()))
                    close()
                    return@addSnapshotListener
                }

                trySend(Result.success(chat))
            }

        awaitClose(registration::remove)
    }
}

/**
 * Шапка из документа; `null` — документ показать нечем.
 *
 * Состав проверяется ещё раз, хотя запрос его и гарантирует: диалог, в котором нас нет,
 * не имеет ни названия, ни ключа, и остальные поля читать смысла не было бы.
 */
private fun DocumentSnapshot.toHeader(selfId: String): ConversationHeader? {
    val chat = toChat(selfId) ?: return null

    return ConversationHeader(
        chat = chat,
        lastMessage = getString(Constants.LAST_MESSAGE_FIELD).orEmpty(),
        encrypted = getLong(Constants.LAST_ENCRYPTED_FIELD)?.toInt() == 1,
        version = getLong(Constants.LAST_VERSION_FIELD)?.toInt() ?: chat.keyVersion,
        date = getTimestamp(Constants.DATE_FIELD)?.toDate()?.time ?: System.currentTimeMillis(),
    )
}

/** Диалог из документа шапки; `null`, если [selfId] нет в составе. */
private fun DocumentSnapshot.toChat(selfId: String): Chat? {
    val members = get(Constants.USERS_FIELD).asStringList()
    if (selfId !in members) return null

    return Chat(
        id = id,
        members = members,
        logins = get(Constants.LOGINS_FIELD).asStringMap(),
        owner = getString(Constants.OWNER_FIELD).orEmpty(),
        selfId = selfId,
        convoKeys = get(Constants.CONVO_KEYS_FIELD).asStringMap(),
        keyVersion = getLong(Constants.KEY_VERSION_FIELD)?.toInt() ?: 0,
    )
}

/** Список строк из поля документа; чужие типы и отсутствие поля дают пустой список. */
private fun Any?.asStringList(): List<String> = (this as? List<*>)?.filterIsInstance<String>().orEmpty()

/** Карта строк из поля документа; записи чужих типов отбрасываются. */
private fun Any?.asStringMap(): Map<String, String> = (this as? Map<*, *>)
    ?.mapNotNull { (key, value) -> if (key is String && value is String) key to value else null }
    ?.toMap()
    .orEmpty()
