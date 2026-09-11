package com.nzzima.secretmessanger.presence.data.impl

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nzzima.secretmessanger.presence.domain.api.PresenceRepository
import com.nzzima.secretmessanger.presence.domain.models.Presence
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** [PresenceRepository] поверх Firestore. */
class PresenceRepositoryImpl(private val firestore: FirebaseFirestore) : PresenceRepository {

    override fun observeEveryone(): Flow<Result<Map<String, Presence>>> = callbackFlow {
        val registration = presence().addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                close()
                return@addSnapshotListener
            }

            val documents = snapshot?.documents ?: return@addSnapshotListener

            trySend(Result.success(documents.mapNotNull { it.presence()?.let { p -> it.id to p } }.toMap()))
        }

        awaitClose(registration::remove)
    }

    override fun observe(uid: String): Flow<Result<Presence?>> = callbackFlow {
        val registration = presence().document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.failure(error))
                close()
                return@addSnapshotListener
            }

            if (snapshot == null) return@addSnapshotListener

            trySend(Result.success(snapshot.presence()))
        }

        awaitClose(registration::remove)
    }

    override suspend fun beat(uid: String): Result<Unit> = runCatching {
        presence()
            .document(uid)
            .set(mapOf(Constants.LAST_SEEN_FIELD to FieldValue.serverTimestamp()))
            .await()
    }

    private fun presence() = firestore.collection(Constants.PRESENCE_COLLECTION)
}

/**
 * Присутствие из документа; `null` — поля нет.
 *
 * Отметка приходит пустой у своего же удара, пока сервер не проставил время: Firestore
 * отдаёт локальный снимок сразу, а `serverTimestamp` в нём ещё `null`. Такой снимок
 * пропускается — секунду спустя приедет настоящий.
 */
private fun com.google.firebase.firestore.DocumentSnapshot.presence(): Presence? =
    getTimestamp(Constants.LAST_SEEN_FIELD)?.let { Presence(it.toDate().time) }
