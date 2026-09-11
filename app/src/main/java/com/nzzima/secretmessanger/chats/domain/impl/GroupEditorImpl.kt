package com.nzzima.secretmessanger.chats.domain.impl

import com.nzzima.secretmessanger.chats.domain.api.ConversationRepository
import com.nzzima.secretmessanger.chats.domain.api.GroupEditor
import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.CompanionKeyMissing
import com.nzzima.secretmessanger.chats.domain.models.NotAGroup
import com.nzzima.secretmessanger.chats.domain.models.NotTheOwner
import com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys
import com.nzzima.secretmessanger.crypto.domain.api.PublicKeyRepository
import com.nzzima.secretmessanger.crypto.domain.CryptoBox
import com.nzzima.secretmessanger.crypto.domain.api.IdentityKeyStore
import java.util.Base64
import com.nzzima.secretmessanger.messanger.domain.api.MessageRepository
import kotlinx.coroutines.flow.Flow

/**
 * Правка состава группы.
 *
 * Проверки «группа» и «мы создатель» стоят здесь, а не только в разметке: правила базы
 * откажут в любом случае, но отказ по правам человеку ничего не объясняет, а кнопка может
 * появиться там, где её не ждали.
 */
class GroupEditorImpl(
    private val conversations: ConversationRepository,
    private val conversationKeys: ConversationKeys,
    private val publicKeys: PublicKeyRepository,
    private val identityKeys: IdentityKeyStore,
    private val messages: MessageRepository,
) : GroupEditor {

    override fun observe(convoId: String, selfId: String): Flow<Result<Chat>> =
        conversations.observeChat(convoId, selfId)

    override suspend fun add(chat: Chat, members: Map<String, String>): Result<Unit> {
        guard(chat)?.let { return Result.failure(it) }

        val newcomers = members.filterKeys { it !in chat.members }
        if (newcomers.isEmpty()) return Result.success(Unit)

        val published = mutableMapOf<String, String>()

        for (uid in newcomers.keys) {
            val key = publicKeys.published(uid).getOrElse { return Result.failure(it) }
                ?: return Result.failure(CompanionKeyMissing())

            published[uid] = key
        }

        val entries = if (chat.isEncrypted) {
            conversationKeys.sealExisting(chat, published) ?: return Result.failure(CompanionKeyMissing())
        } else {
            emptyMap()
        }

        return conversations.updateMembers(
            convoId = chat.id,
            members = chat.members + newcomers.keys,
            logins = chat.logins + newcomers,
            keys = entries,
        )
    }

    override suspend fun remove(chat: Chat, uid: String): Result<Unit> {
        guard(chat)?.let { return Result.failure(it) }
        if (uid == chat.selfId) return Result.failure(NotTheOwner())

        val remaining = chat.members - uid

        val rotated = if (chat.isEncrypted) rotated(chat, remaining) else null

        conversations.updateMembers(
            convoId = chat.id,
            members = remaining,
            logins = chat.logins,
            keys = rotated?.first.orEmpty(),
            keyVersion = rotated?.second,
        ).onFailure { return Result.failure(it) }

        // Отметка пишется после удачной смены состава и молча: легшая в ленту при отклонённой
        // записи, она соврала бы о том, чего не было, а её собственная неудача ничего не
        // отменяет — ключ уже сменён.
        if (rotated != null) messages.note(chat.id, chat.selfId)

        return Result.success(Unit)
    }

    /**
     * Новый ключ для оставшихся; `null` — запечатать некому, и менять нечего.
     *
     * **Своя половина берётся из хранилища, а не из реестра** — там она и рождается, а в
     * профиле лишь опубликована. Через реестр мы зависели бы от того, что публикация прошла,
     * и на первом же промахе выдали бы новый ключ всем, кроме себя: своя же группа стала бы
     * нечитаемой с этого места.
     */
    private suspend fun rotated(chat: Chat, remaining: List<String>): Pair<Map<String, String>, Int>? {
        val identityPrivate = identityKeys.existing(chat.selfId) ?: return null
        val published = mutableMapOf(chat.selfId to encode(CryptoBox.publicKey(identityPrivate)))

        for (member in remaining.filterNot { it == chat.selfId }) {
            val key = publicKeys.published(member).getOrNull() ?: continue

            published[member] = key
        }

        return conversationKeys.rotate(chat, published)
    }

    private fun encode(publicKey: ByteArray): String = Base64.getEncoder().encodeToString(publicKey)

    /** Общая проверка обеих правок; `null` — можно. */
    private fun guard(chat: Chat): Throwable? = when {
        !chat.isGroup -> NotAGroup()
        chat.owner != chat.selfId -> NotTheOwner()
        else -> null
    }
}
