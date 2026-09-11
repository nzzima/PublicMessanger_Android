package com.nzzima.secretmessanger.photo.data.impl

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.nzzima.secretmessanger.photo.domain.api.PhotoRepository
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.tasks.await

/**
 * [PhotoRepository] поверх Firestore.
 *
 * Байты едут `Blob` — так же их пишет iOS (`Data` там становится тем же типом документа).
 */
class PhotoRepositoryImpl(private val firestore: FirebaseFirestore) : PhotoRepository {

    override suspend fun sealed(convoId: String, messageId: String): Result<ByteArray?> = runCatching {
        images(convoId)
            .document(messageId)
            .get()
            .await()
            .getBlob(Constants.IMAGE_DATA_FIELD)
            ?.toBytes()
    }

    override suspend fun put(
        convoId: String,
        messageId: String,
        senderId: String,
        sealed: ByteArray,
    ): Result<Unit> = runCatching {
        images(convoId)
            .document(messageId)
            .set(
                mapOf(
                    Constants.SENDER_ID_FIELD to senderId,
                    Constants.IMAGE_DATA_FIELD to Blob.fromBytes(sealed),
                ),
            )
            .await()
    }

    private fun images(convoId: String) = firestore
        .collection(Constants.CONVERSATION_COLLECTION)
        .document(convoId)
        .collection(Constants.IMAGES_COLLECTION)
}
