package com.nzzima.secretmessanger.messanger.data.impl

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.nzzima.secretmessanger.messanger.domain.api.MessageRepository
import com.nzzima.secretmessanger.messanger.domain.models.Message
import com.nzzima.secretmessanger.messanger.domain.models.MessageKind
import com.nzzima.secretmessanger.utils.constants.Constants
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * [MessageRepository] поверх Firestore.
 *
 * Участие проверяется правилами по составу из шапки диалога, а не по пути документа:
 * состав группы в идентификатор не закодируешь, тем более что он меняется.
 */
class MessageRepositoryImpl(private val firestore: FirebaseFirestore) : MessageRepository {

    override fun observeLast(convoId: String, limit: Long): Flow<Result<List<Message>>> = callbackFlow {
        val registration = firestore.messages(convoId)
            .orderBy(Constants.DATE_FIELD)
            .limitToLast(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    close()
                    return@addSnapshotListener
                }

                val documents = snapshot?.documents ?: return@addSnapshotListener
                trySend(Result.success(documents.map { it.toMessage() }))
            }

        awaitClose(registration::remove)
    }

    override suspend fun send(convoId: String, message: Message): Result<Unit> = runCatching {
        val date = Timestamp(Date(message.date))

        // Время у шапки и у реплики одно и то же значение: список диалогов сортируется
        // по шапке, лента — по реплике, и разъехаться им нельзя.
        firestore.collection(Constants.CONVERSATION_COLLECTION)
            .document(convoId)
            .set(message.header(date), SetOptions.merge())
            .await()

        firestore.messages(convoId)
            .document(message.id)
            .set(message.payload(date))
            .await()
    }

    private fun FirebaseFirestore.messages(convoId: String) =
        collection(Constants.CONVERSATION_COLLECTION).document(convoId).collection(Constants.MESSAGES_COLLECTION)
}

/**
 * Поля шапки, которые обновляет отправка.
 *
 * Карта `logins` сюда не попадает, хотя iOS вписывает в неё своё имя при каждой отправке:
 * там логин меняется в профиле, а на Android его сменить нечем — переписывать было бы
 * нечего. Появится смена логина — появится и запись.
 */
private fun Message.header(date: Timestamp): Map<String, Any> = buildMap {
    put(Constants.LAST_MESSAGE_FIELD, body)
    put(Constants.DATE_FIELD, date)

    if (encrypted) {
        put(Constants.LAST_ENCRYPTED_FIELD, 1)
        put(Constants.LAST_VERSION_FIELD, version)
    }
}

/**
 * Документ реплики.
 *
 * Поле `type` не пишется: Android отправляет только текст, а текстовая реплика вида не
 * имеет — так же её пишет iOS.
 */
private fun Message.payload(date: Timestamp): Map<String, Any> = buildMap {
    put(Constants.SENDER_ID_FIELD, senderId)
    put(Constants.MESSAGE_FIELD, body)
    put(Constants.DATE_FIELD, date)

    if (encrypted) {
        put(Constants.ENCRYPTED_FIELD, 1)
        put(Constants.VERSION_FIELD, version)
    }
}

/**
 * Реплика из документа.
 *
 * Отсутствующее время подменяется текущим: без него реплика провалилась бы в начало
 * ленты. На практике его не бывает только у записи, снимок которой пришёл до того, как
 * сервер проставил своё время, — а его отправитель пишет сам.
 */
private fun DocumentSnapshot.toMessage() = Message(
    id = id,
    senderId = getString(Constants.SENDER_ID_FIELD).orEmpty(),
    body = getString(Constants.MESSAGE_FIELD).orEmpty(),
    encrypted = getLong(Constants.ENCRYPTED_FIELD)?.toInt() == 1,
    version = getLong(Constants.VERSION_FIELD)?.toInt() ?: 0,
    date = getTimestamp(Constants.DATE_FIELD)?.toDate()?.time ?: System.currentTimeMillis(),
    kind = getString(Constants.TYPE_FIELD).toKind(),
)

/** Вид реплики из поля `type`; незнакомое значение читается как текст. */
private fun String?.toKind(): MessageKind = when (this) {
    Constants.VOICE_TYPE -> MessageKind.Voice
    Constants.PHOTO_TYPE -> MessageKind.Photo
    Constants.LOCATION_TYPE -> MessageKind.Location
    Constants.KEY_NOTICE_TYPE -> MessageKind.KeyNotice
    else -> MessageKind.Text
}
