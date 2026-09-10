package com.nzzima.secretmessanger.auth.data.impl

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.nzzima.secretmessanger.auth.domain.api.LoginRepository
import com.nzzima.secretmessanger.auth.domain.models.LoginAvailability
import com.nzzima.secretmessanger.auth.domain.models.LoginTaken
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.tasks.await

/**
 * [LoginRepository] поверх Firestore.
 *
 * Отказ `PERMISSION_DENIED` на записи означает занятый логин: правило коллекции запрещает
 * `create` по существующему документу и `update` — всем.
 */
class LoginRepositoryImpl(private val firestore: FirebaseFirestore) : LoginRepository {

    override suspend fun check(login: String, uid: String?): Result<LoginAvailability> = runCatching {
        val snapshot = firestore.collection(Constants.LOGINS_COLLECTION)
            .document(LoginRepository.key(login))
            .get()
            .await()
        when {
            !snapshot.exists() -> LoginAvailability.FREE
            snapshot.getString(Constants.UID_FIELD) == uid -> LoginAvailability.MINE
            else -> LoginAvailability.TAKEN
        }
    }

    override suspend fun release(login: String): Result<Unit> = runCatching {
        firestore.collection(Constants.LOGINS_COLLECTION)
            .document(LoginRepository.key(login))
            .delete()
            .await()
    }

    override suspend fun claim(login: String, uid: String): Result<Unit> = runCatching {
        try {
            firestore.collection(Constants.LOGINS_COLLECTION)
                .document(LoginRepository.key(login))
                .set(mapOf(Constants.UID_FIELD to uid, Constants.LOGIN_FIELD to login))
                .await()
        } catch (error: FirebaseFirestoreException) {
            if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) throw LoginTaken()
            throw error
        }
    }
}
