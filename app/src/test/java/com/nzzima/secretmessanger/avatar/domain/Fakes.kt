package com.nzzima.secretmessanger.avatar.domain

import com.nzzima.secretmessanger.avatar.domain.api.AvatarEncoder
import com.nzzima.secretmessanger.avatar.domain.api.AvatarInteractor
import com.nzzima.secretmessanger.avatar.domain.api.AvatarRepository

/** Байты аватаров в памяти. */
class FakeAvatarRepository(
    private val journal: MutableList<String> = mutableListOf(),
) : AvatarRepository {

    /** Что лежит в базе: uid → байты. */
    val stored = mutableMapOf<String, ByteArray>()

    /** Сколько раз ходили в базу за картинкой. */
    var reads = 0
        private set

    /** Чем отказывает запись; `null` — проходит. */
    var putFails: Throwable? = null

    /** Чем отказывает удаление; `null` — проходит. */
    var deleteFails: Throwable? = null

    override suspend fun bytes(uid: String): Result<ByteArray?> {
        reads++
        return Result.success(stored[uid])
    }

    override suspend fun put(uid: String, image: ByteArray, version: Int): Result<Unit> {
        putFails?.let { return Result.failure(it) }

        stored[uid] = image
        journal += "picture:$version"
        return Result.success(Unit)
    }

    override suspend fun delete(uid: String): Result<Unit> {
        deleteFails?.let { return Result.failure(it) }

        stored.remove(uid)
        journal += "delete"
        return Result.success(Unit)
    }
}

/** Кодировщик, который отдаёт заранее заданные байты. */
class FakeAvatarEncoder(private val encoded: ByteArray? = byteArrayOf(1, 2, 3)) : AvatarEncoder {

    /** Источники, которые просили закодировать. */
    val sources = mutableListOf<String>()

    override suspend fun encode(source: String): ByteArray? {
        sources += source
        return encoded
    }
}

/** Аватары в памяти — для моделей экранов, которым сам крипто- и сетевой путь не нужен. */
class FakeAvatarInteractor(private val image: ByteArray? = null) : AvatarInteractor {

    /** Пары «кто + версия», которые просили показать. */
    val requested = mutableListOf<Pair<String, Int>>()

    /** Источники, поставленные аватаром. */
    val changed = mutableListOf<String>()

    /** Сколько раз аватар убирали. */
    var removals = 0
        private set

    /** Чем отказывает запись; `null` — проходит. */
    var refusal: Throwable? = null

    override suspend fun avatar(uid: String, version: Int): ByteArray? {
        requested += uid to version
        return image.takeIf { version > 0 }
    }

    override suspend fun change(uid: String, source: String, currentVersion: Int): Result<Unit> {
        refusal?.let { return Result.failure(it) }

        changed += source
        return Result.success(Unit)
    }

    override suspend fun remove(uid: String): Result<Unit> {
        refusal?.let { return Result.failure(it) }

        removals++
        return Result.success(Unit)
    }
}
