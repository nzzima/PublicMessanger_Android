package com.nzzima.secretmessanger.avatar.domain.impl

import com.nzzima.secretmessanger.auth.domain.api.ProfileRepository
import com.nzzima.secretmessanger.avatar.domain.api.AvatarEncoder
import com.nzzima.secretmessanger.avatar.domain.api.AvatarInteractor
import com.nzzima.secretmessanger.avatar.domain.api.AvatarRepository
import com.nzzima.secretmessanger.avatar.domain.models.AvatarTooLarge
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Аватары: кэш в памяти, кодирование и порядок записи.
 *
 * Кэш только в памяти. На диск класть было бы можно — аватар публичен, в отличие от
 * расшифрованных реплик, — но незачем: картинки маленькие, а лишнее хранилище пришлось бы
 * чистить при смене аватара и при выходе из аккаунта.
 *
 * Одновременные запросы одной картинки схлопываются в один поход в базу: список контактов
 * перерисовывается на любое изменение любого профиля, и без этого он заказывал бы одно и то
 * же скачивание по нескольку раз.
 */
class AvatarInteractorImpl(
    private val avatars: AvatarRepository,
    private val encoder: AvatarEncoder,
    private val profiles: ProfileRepository,
) : AvatarInteractor {

    private val cache = LruBytes(Constants.AVATAR_CACHE_SIZE)
    private val loading = mutableMapOf<String, Mutex>()
    private val guard = Mutex()

    override suspend fun avatar(uid: String, version: Int): ByteArray? {
        if (version <= 0) return null

        val key = key(uid, version)
        cached(key)?.let { return it }

        // Замок на ключ, а не на весь кэш: пока грузится один аватар, остальные строки
        // списка не должны ждать своей очереди.
        val lock = guard.withLock { loading.getOrPut(key) { Mutex() } }

        return lock.withLock {
            cached(key) ?: avatars.bytes(uid).getOrNull()?.also { remember(key, it) }
        }
    }

    override suspend fun change(uid: String, source: String, currentVersion: Int): Result<Unit> {
        val image = encoder.encode(source) ?: return Result.failure(AvatarTooLarge())
        val version = currentVersion + 1

        avatars.put(uid, image, version).getOrElse { return Result.failure(it) }

        // Своя же картинка кладётся в кэш сразу: показывать надо ровно то, что увидят
        // остальные, — сжатую версию, а не исходник.
        remember(key(uid, version), image)

        return profiles.updateAvatarVersion(uid, version)
    }

    override suspend fun remove(uid: String): Result<Unit> {
        avatars.delete(uid).getOrElse { return Result.failure(it) }

        return profiles.updateAvatarVersion(uid, NO_AVATAR)
    }

    /** Ключ кэша: сменившийся аватар получает новую версию, поэтому устареть он не может. */
    private fun key(uid: String, version: Int) = "${uid}_$version"

    private suspend fun cached(key: String): ByteArray? = guard.withLock { cache[key] }

    private suspend fun remember(key: String, image: ByteArray) {
        guard.withLock { cache[key] = image }
    }

    private companion object {
        const val NO_AVATAR = 0
    }
}

/**
 * Кэш картинок с вытеснением самой давней.
 *
 * `LinkedHashMap` в порядке обращения вместо `android.util.LruCache`: домен обходится без
 * платформенных классов, а заодно проверяется обычными JVM-тестами, без заглушек Android.
 */
private class LruBytes(private val limit: Int) : LinkedHashMap<String, ByteArray>(CAPACITY, LOAD, true) {

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>): Boolean = size > limit

    private companion object {
        const val CAPACITY = 16
        const val LOAD = 0.75f
    }
}
