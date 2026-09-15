package com.nzzima.secretmessanger.crypto.domain.impl

import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.crypto.domain.CryptoBox
import com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys
import com.nzzima.secretmessanger.crypto.domain.api.IdentityKeyStore
import com.nzzima.secretmessanger.utils.constants.Constants
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * [ConversationKeys] поверх постоянного ключа аккаунта из [IdentityKeyStore].
 *
 * Открытые ключи кэшируются по диалогу, версии и аккаунту: распечатывание асимметричное,
 * а один ключ обслуживает и превью в списке, и все сообщения диалога.
 *
 * Кэш живёт, пока жив экземпляр, и растёт вместе с числом открытых диалогов; записи из
 * него не вытесняются. Стёртый диалог убирается из кэша явно — [forget].
 */
class ConversationKeysImpl(private val identityKeys: IdentityKeyStore) : ConversationKeys {

    private val cache = ConcurrentHashMap<String, ByteArray>()

    override fun open(
        convoId: String,
        uid: String,
        version: Int,
        entries: Map<String, String>,
    ): ByteArray? {
        val cacheKey = "$convoId/$version/$uid"
        cache[cacheKey]?.let { return it }

        val payload = entries[entryKey(uid, version)] ?: return null
        val identityPrivate = identityKeys.existing(uid) ?: return null

        return runCatching { CryptoBox.openKey(payload, identityPrivate, context(convoId, version)) }
            .getOrNull()
            ?.also { cache[cacheKey] = it }
    }

    override fun sealNew(convoId: String, publicKeys: Map<String, String>): Map<String, String>? {
        val key = CryptoBox.newConversationKey()
        val version = Constants.FIRST_KEY_VERSION

        return publicKeys.entries.associate { (uid, encoded) ->
            val recipient = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
                ?: return null
            val payload = runCatching { CryptoBox.sealKey(key, recipient, context(convoId, version)) }
                .getOrNull()
                ?: return null

            entryKey(uid, version) to payload
        }
    }

    override fun sealExisting(chat: Chat, publicKeys: Map<String, String>): Map<String, String>? {
        val entries = mutableMapOf<String, String>()

        // Версии перебираются от первой до текущей: карта шапки хранит их все, и какие из
        // них нам доступны, знает только наш собственный ключ.
        for (version in Constants.FIRST_KEY_VERSION..chat.keyVersion) {
            val key = open(chat.id, chat.selfId, version, chat.convoKeys) ?: continue

            for ((uid, encoded) in publicKeys) {
                val recipient = decode(encoded) ?: return null
                val payload = runCatching { CryptoBox.sealKey(key, recipient, context(chat.id, version)) }
                    .getOrNull()
                    ?: return null

                entries[entryKey(uid, version)] = payload
            }
        }

        return entries.takeIf { it.isNotEmpty() }
    }

    override fun rotate(chat: Chat, publicKeys: Map<String, String>): Pair<Map<String, String>, Int>? {
        if (publicKeys.isEmpty()) return null

        val key = CryptoBox.newConversationKey()
        val version = chat.keyVersion + 1

        val entries = publicKeys.entries.associate { (uid, encoded) ->
            val recipient = decode(encoded) ?: return null
            val payload = runCatching { CryptoBox.sealKey(key, recipient, context(chat.id, version)) }
                .getOrNull()
                ?: return null

            entryKey(uid, version) to payload
        }

        return entries to version
    }

    override fun forget(convoId: String) {
        // Ключ кэша начинается с идентификатора диалога, а версий и аккаунтов в нём может
        // быть несколько — убирать надо все.
        cache.keys.removeAll { it.startsWith("$convoId/") }
    }

    private fun decode(encoded: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()

    /** Ключ записи в карте `convoKeys`: чей ключ и какой версии. */
    private fun entryKey(uid: String, version: Int) = "${uid}_$version"

    /**
     * Контекст HKDF: привязывает запечатанный ключ к диалогу и версии, поэтому запись,
     * переставленная в другой диалог, не открывается.
     */
    private fun context(convoId: String, version: Int) = "$convoId/v$version"
}
