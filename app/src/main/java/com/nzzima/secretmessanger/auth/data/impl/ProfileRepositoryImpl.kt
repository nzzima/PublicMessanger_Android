package com.nzzima.secretmessanger.auth.data.impl

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.nzzima.secretmessanger.auth.domain.api.ProfileRepository
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.tasks.await

/** [ProfileRepository] поверх Firestore. */
class ProfileRepositoryImpl(private val firestore: FirebaseFirestore) : ProfileRepository {

    override suspend fun createProfile(uid: String, login: String, name: String): Result<Unit> = runCatching {
        firestore.collection(Constants.USERS_COLLECTION)
            .document(uid)
            .set(
                mapOf(
                    Constants.LOGIN_FIELD to login,
                    Constants.NAME_FIELD to name,
                    Constants.SOME_INFO_FIELD to "",
                ),
                SetOptions.merge(),
            )
            .await()
    }

    override suspend fun updateProfile(
        uid: String,
        login: String,
        name: String,
        someInfo: String,
    ): Result<Unit> = runCatching {
        firestore.collection(Constants.USERS_COLLECTION)
            .document(uid)
            .set(
                mapOf(
                    Constants.LOGIN_FIELD to login,
                    Constants.NAME_FIELD to name,
                    Constants.SOME_INFO_FIELD to someInfo,
                ),
                SetOptions.merge(),
            )
            .await()
    }

    override suspend fun exists(uid: String): Result<Boolean> = runCatching {
        firestore.collection(Constants.USERS_COLLECTION)
            .document(uid)
            .get()
            .await()
            .exists()
    }
}
