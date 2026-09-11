package com.nzzima.secretmessanger.chats.domain.impl

import com.nzzima.secretmessanger.chats.domain.api.ConversationRepository
import com.nzzima.secretmessanger.chats.domain.api.ConversationStarter
import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.CompanionKeyMissing
import com.nzzima.secretmessanger.crypto.domain.CryptoBox
import com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys
import com.nzzima.secretmessanger.crypto.domain.api.IdentityKeyStore
import com.nzzima.secretmessanger.crypto.domain.api.PublicKeyRepository
import com.nzzima.secretmessanger.crypto.domain.models.CryptoFailure
import com.nzzima.secretmessanger.profile.domain.api.ProfileReader
import com.nzzima.secretmessanger.utils.constants.Constants
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * Заведение диалога: на двоих по детерминированному идентификатору, группы — по случайному.
 *
 * Правка состава и ротация ключа по-прежнему не написаны: это работа создателя, и она
 * начинается там, где кого-то добавляют или удаляют. Завести группу можно и без неё.
 */
class ConversationStarterImpl(
    private val conversations: ConversationRepository,
    private val conversationKeys: ConversationKeys,
    private val publicKeys: PublicKeyRepository,
    private val identityKeys: IdentityKeyStore,
    private val profiles: ProfileReader,
) : ConversationStarter {

    override suspend fun start(selfId: String, companionId: String, companionLogin: String): Result<String> {
        val convoId = Chat.conversationId(selfId, companionId)

        conversations.existing(convoId, selfId)
            .onFailure { return Result.failure(it) }
            .getOrNull()
            ?.let { return Result.success(convoId) }

        val chat = newChat(convoId, selfId, companionId, companionLogin)
            .getOrElse { return Result.failure(it) }

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
    private suspend fun newChat(
        convoId: String,
        selfId: String,
        companionId: String,
        companionLogin: String,
    ): Result<Chat> {
        val identityPrivate = identityKeys.existing(selfId) ?: return Result.failure(CryptoFailure.NoKey)

        val companionPublic = publicKeys.published(companionId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(CompanionKeyMissing())

        // Своя половина берётся из хранилища, а не из профиля: там она и рождается, а в
        // профиле лишь опубликована. Читать её из `users` значило бы зависеть от того,
        // что публикация уже прошла.
        val entries = conversationKeys.sealNew(
            convoId = convoId,
            publicKeys = mapOf(
                selfId to encode(CryptoBox.publicKey(identityPrivate)),
                companionId to companionPublic,
            ),
        ) ?: return Result.failure(CompanionKeyMissing())

        val selfLogin = profiles.observe(selfId).first().getOrElse { return Result.failure(it) }.login

        return Result.success(
            Chat(
                id = convoId,
                members = listOf(selfId, companionId),
                logins = mapOf(selfId to selfLogin, companionId to companionLogin),
                owner = selfId,
                selfId = selfId,
                convoKeys = entries,
                keyVersion = Constants.FIRST_KEY_VERSION,
            ),
        )
    }

    private fun encode(publicKey: ByteArray): String = Base64.getEncoder().encodeToString(publicKey)

    override suspend fun startGroup(selfId: String, members: Map<String, String>): Result<String> {
        // Случайный идентификатор, и повторное заведение той же компании даёт новую группу.
        // Это не недосмотр: «та же компания» — не то же самое, что «тот же разговор».
        val convoId = UUID.randomUUID().toString()

        val identityPrivate = identityKeys.existing(selfId) ?: return Result.failure(CryptoFailure.NoKey)

        val published = mutableMapOf(selfId to encode(CryptoBox.publicKey(identityPrivate)))

        for (uid in members.keys) {
            val key = publicKeys.published(uid).getOrElse { return Result.failure(it) }
                ?: return Result.failure(CompanionKeyMissing())

            published[uid] = key
        }

        val entries = conversationKeys.sealNew(convoId, published) ?: return Result.failure(CompanionKeyMissing())

        val selfLogin = profiles.observe(selfId).first().getOrElse { return Result.failure(it) }.login

        val chat = Chat(
            id = convoId,
            members = listOf(selfId) + members.keys,
            logins = mapOf(selfId to selfLogin) + members,
            owner = selfId,
            selfId = selfId,
            convoKeys = entries,
            keyVersion = Constants.FIRST_KEY_VERSION,
        )

        return conversations.create(chat).map { convoId }
    }
}
