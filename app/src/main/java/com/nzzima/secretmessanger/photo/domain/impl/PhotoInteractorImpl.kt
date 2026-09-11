package com.nzzima.secretmessanger.photo.domain.impl

import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.openBytes
import com.nzzima.secretmessanger.chats.domain.sealBytes
import com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys
import com.nzzima.secretmessanger.crypto.domain.models.CryptoFailure
import com.nzzima.secretmessanger.photo.domain.api.PhotoEncoder
import com.nzzima.secretmessanger.photo.domain.api.PhotoInteractor
import com.nzzima.secretmessanger.photo.domain.api.PhotoRepository
import com.nzzima.secretmessanger.photo.domain.models.PhotoTooLarge
import com.nzzima.secretmessanger.photo.domain.models.PhotoSize
import com.nzzima.secretmessanger.utils.constants.Constants
import com.nzzima.secretmessanger.utils.media.LruBytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Снимки: кэш в памяти, расшифровка и порядок записи.
 *
 * Кэш только в памяти, как у аватаров, но по другой причине: класть расшифрованный снимок на
 * диск значило бы вынести наружу то самое, ради чего он шифруется в базе.
 *
 * Одновременные запросы одного снимка схлопываются в один поход в базу: лента
 * перерисовывается на каждую реплику, и без этого один и тот же кадр качался бы по нескольку
 * раз.
 */
class PhotoInteractorImpl(
    private val photos: PhotoRepository,
    private val encoder: PhotoEncoder,
    private val conversationKeys: ConversationKeys,
) : PhotoInteractor {

    private val cache = LruBytes(Constants.PHOTO_CACHE_SIZE)
    private val loading = mutableMapOf<String, Mutex>()
    private val guard = Mutex()

    override suspend fun photo(chat: Chat, messageId: String, version: Int): ByteArray? {
        cached(messageId)?.let { return it }

        // Замок на снимок, а не на весь кэш: пока грузится один, остальные пузыри не должны
        // ждать своей очереди.
        val lock = guard.withLock { loading.getOrPut(messageId) { Mutex() } }

        return lock.withLock {
            cached(messageId) ?: download(chat, messageId, version)?.also { remember(messageId, it) }
        }
    }

    override suspend fun attach(chat: Chat, messageId: String, source: String): Result<PhotoSize> {
        val encoded = encoder.encode(source) ?: return Result.failure(PhotoTooLarge())
        val sealed = conversationKeys.sealBytes(chat, encoded.bytes)
            ?: return Result.failure(CryptoFailure.NoKey)

        photos.put(chat.id, messageId, chat.selfId, sealed).getOrElse { return Result.failure(it) }
        remember(messageId, encoded.bytes)

        return Result.success(encoded.size)
    }

    private suspend fun download(chat: Chat, messageId: String, version: Int): ByteArray? {
        val sealed = photos.sealed(chat.id, messageId).getOrNull() ?: return null

        return conversationKeys.openBytes(chat, sealed, version)
    }

    private suspend fun cached(messageId: String): ByteArray? = guard.withLock { cache[messageId] }

    private suspend fun remember(messageId: String, image: ByteArray) {
        guard.withLock { cache[messageId] = image }
    }
}
