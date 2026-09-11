package com.nzzima.secretmessanger.voice.data.impl

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.nzzima.secretmessanger.utils.constants.Constants
import com.nzzima.secretmessanger.voice.domain.api.VoiceRepository
import kotlinx.coroutines.tasks.await

/** [VoiceRepository] поверх Firestore. Байты едут `Blob` — так же их пишет iOS. */
class VoiceRepositoryImpl(private val firestore: FirebaseFirestore) : VoiceRepository {

    override suspend fun sealed(convoId: String, messageId: String): Result<ByteArray?> = runCatching {
        audio(convoId)
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
        audio(convoId)
            .document(messageId)
            .set(
                mapOf(
                    Constants.SENDER_ID_FIELD to senderId,
                    Constants.IMAGE_DATA_FIELD to Blob.fromBytes(sealed),
                ),
            )
            .await()
    }

    private fun audio(convoId: String) = firestore
        .collection(Constants.CONVERSATION_COLLECTION)
        .document(convoId)
        .collection(Constants.AUDIO_COLLECTION)
}
