package com.nzzima.secretmessanger.avatar.data.impl

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.nzzima.secretmessanger.avatar.domain.api.AvatarRepository
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.tasks.await

/**
 * [AvatarRepository] поверх Firestore.
 *
 * Байты едут `Blob` — так же их пишет iOS (`Data` там становится тем же типом документа).
 */
class AvatarRepositoryImpl(private val firestore: FirebaseFirestore) : AvatarRepository {

    override suspend fun bytes(uid: String): Result<ByteArray?> = runCatching {
        firestore.collection(Constants.AVATARS_COLLECTION)
            .document(uid)
            .get()
            .await()
            .getBlob(Constants.AVATAR_DATA_FIELD)
            ?.toBytes()
    }

    override suspend fun put(uid: String, image: ByteArray, version: Int): Result<Unit> = runCatching {
        firestore.collection(Constants.AVATARS_COLLECTION)
            .document(uid)
            .set(
                mapOf(
                    Constants.AVATAR_DATA_FIELD to Blob.fromBytes(image),
                    Constants.AVATAR_VERSION_FIELD to version,
                ),
            )
            .await()
    }

    override suspend fun delete(uid: String): Result<Unit> = runCatching {
        firestore.collection(Constants.AVATARS_COLLECTION)
            .document(uid)
            .delete()
            .await()
    }
}
