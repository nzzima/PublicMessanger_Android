package com.nzzima.secretmessanger.contacts.data.impl

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.nzzima.secretmessanger.contacts.domain.api.ContactsRepository
import com.nzzima.secretmessanger.contacts.domain.models.Contact
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * [ContactsRepository] поверх Firestore.
 *
 * Читать профили может любой вошедший — на этом правиле и держится список контактов.
 */
class ContactsRepositoryImpl(private val firestore: FirebaseFirestore) : ContactsRepository {

    override fun observeAll(): Flow<Result<List<Contact>>> = callbackFlow {
        val registration = firestore.collection(Constants.USERS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    close()
                    return@addSnapshotListener
                }

                val documents = snapshot?.documents ?: return@addSnapshotListener
                trySend(Result.success(documents.map { it.toContact() }))
            }

        awaitClose(registration::remove)
    }
}

/**
 * Контакт из документа профиля.
 *
 * Логин берётся из одноимённого поля, а при пустом — из имени: так же читает iOS.
 */
private fun DocumentSnapshot.toContact() = Contact(
    id = id,
    login = getString(Constants.LOGIN_FIELD)
        ?.takeIf { it.isNotEmpty() }
        ?: getString(Constants.NAME_FIELD).orEmpty(),
    avatarVersion = getLong(Constants.PROFILE_AVATAR_VERSION_FIELD)?.toInt() ?: 0,
)
