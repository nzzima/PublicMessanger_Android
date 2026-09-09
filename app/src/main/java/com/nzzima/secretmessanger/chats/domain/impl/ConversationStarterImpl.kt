package com.nzzima.secretmessanger.chats.domain.impl

import com.nzzima.secretmessanger.chats.domain.api.ConversationRepository
import com.nzzima.secretmessanger.chats.domain.api.ConversationStarter
import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.CompanionKeyMissing
import com.nzzima.secretmessanger.contacts.domain.models.Contact
import com.nzzima.secretmessanger.crypto.domain.CryptoBox
import com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys
import com.nzzima.secretmessanger.crypto.domain.api.IdentityKeyStore
import com.nzzima.secretmessanger.crypto.domain.api.PublicKeyRepository
import com.nzzima.secretmessanger.crypto.domain.models.CryptoFailure
import com.nzzima.secretmessanger.profile.domain.api.ProfileReader
import com.nzzima.secretmessanger.utils.constants.Constants
import java.util.Base64
import kotlinx.coroutines.flow.first

/**
 * Заведение диалога на двоих.
 *
 * Групп Android не заводит: состав в идентификатор не закодируешь, а список участников,
 * ротация ключа и удаление из группы — работа создателя, которой в приложении нет.
 */
class ConversationStarterImpl(
    private val conversations: ConversationRepository,
    private val conversationKeys: ConversationKeys,
    private val publicKeys: PublicKeyRepository,
    private val identityKeys: IdentityKeyStore,
    private val profiles: ProfileReader,
) : ConversationStarter {

    override suspend fun start(selfId: String, contact: Contact): Result<String> {
        val convoId = Chat.conversationId(selfId, contact.id)

        conversations.existing(convoId, selfId)
            .onFailure { return Result.failure(it) }
            .getOrNull()
            ?.let { return Result.success(convoId) }

        val chat = newChat(convoId, selfId, contact).getOrElse { return Result.failure(it) }

        return conversations.create(chat).fold(
            onSuccess = { Result.success(convoId) },
            // Собеседник мог завести тот же диалог первым — идентификатор у нас общий, и
            // запись поверх его шапки правила отклонят. Проигранная гонка не отказ:
            // диалог, который мы собирались завести, уже есть.
            onFailure = { error ->
                if (conversations.existing(convoId, selfId).getOrNull() != null) {
                    Result.success(convoId)
                } else {
                    Result.failure(error)
                }
            },
        )
    }

    /** Шапка нового диалога: состав, имена обоих и ключ, запечатанный каждому. */
    private suspend fun newChat(convoId: String, selfId: String, contact: Contact): Result<Chat> {
        val identityPrivate = identityKeys.existing(selfId) ?: return Result.failure(CryptoFailure.NoKey)

        val companionPublic = publicKeys.published(contact.id).getOrElse { return Result.failure(it) }
            ?: return Result.failure(CompanionKeyMissing())

        // Своя половина берётся из хранилища, а не из профиля: там она и рождается, а в
        // профиле лишь опубликована. Читать её из `users` значило бы зависеть от того,
        // что публикация уже прошла.
        val entries = conversationKeys.sealNew(
            convoId = convoId,
            publicKeys = mapOf(
                selfId to encode(CryptoBox.publicKey(identityPrivate)),
                contact.id to companionPublic,
            ),
        ) ?: return Result.failure(CompanionKeyMissing())

        val selfLogin = profiles.observe(selfId).first().getOrElse { return Result.failure(it) }.login

        return Result.success(
            Chat(
                id = convoId,
                members = listOf(selfId, contact.id),
                logins = mapOf(selfId to selfLogin, contact.id to contact.login),
                owner = selfId,
                selfId = selfId,
                convoKeys = entries,
                keyVersion = Constants.FIRST_KEY_VERSION,
            ),
        )
    }

    private fun encode(publicKey: ByteArray): String = Base64.getEncoder().encodeToString(publicKey)
}
