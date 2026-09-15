package com.nzzima.secretmessanger.profile.data.impl

import com.google.firebase.firestore.FirebaseFirestore
import com.nzzima.secretmessanger.profile.domain.models.ProfileMissing
import com.nzzima.secretmessanger.profile.domain.api.ProfileReader
import com.nzzima.secretmessanger.profile.domain.models.Profile
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** [ProfileReader] поверх Firestore. */
class ProfileReaderImpl(private val firestore: FirebaseFirestore) : ProfileReader {

    override fun observe(uid: String): Flow<Result<Profile>> = callbackFlow {
        val registration = firestore.collection(Constants.USERS_COLLECTION)
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    close()
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                if (!snapshot.exists()) {
                    trySend(Result.failure(ProfileMissing()))
                    close()
                    return@addSnapshotListener
                }

                trySend(
                    Result.success(
                        Profile(
                            id = uid,
                            login = snapshot.getString(Constants.LOGIN_FIELD).orEmpty(),
                            name = snapshot.getString(Constants.NAME_FIELD).orEmpty(),
                            someInfo = snapshot.getString(Constants.SOME_INFO_FIELD).orEmpty(),
                            avatarVersion = snapshot.getLong(Constants.PROFILE_AVATAR_VERSION_FIELD)
                                ?.toInt() ?: 0,
                        ),
                    ),
                )
            }

        awaitClose(registration::remove)
    }
}
