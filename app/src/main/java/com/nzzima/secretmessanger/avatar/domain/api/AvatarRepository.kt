package com.nzzima.secretmessanger.avatar.domain.api

/**
 * Байты аватаров — коллекция `avatars`, документ на человека.
 *
 * ```
 * avatars/{uid}   data: bytes (JPEG), version: Int
 * users/{uid}     avatarVersion: Int   — 0 или нет поля: аватара нет
 * ```
 *
 * Байты живут отдельно от профиля не для красоты: «Контакты» держат слушатель на **всю**
 * коллекцию профилей и перечитывают её при любом изменении любого из них. Аватар внутри
 * профиля означал бы десятки картинок на каждое чужое переименование.
 */
interface AvatarRepository {

    /** Байты аватара аккаунта [uid]; `null` — документа нет. */
    suspend fun bytes(uid: String): Result<ByteArray?>

    /**
     * Кладёт [image] аккаунту [uid] под версией [version].
     *
     * Правило Firestore проверяет размер числом — не больше
     * [com.nzzima.secretmessanger.utils.constants.Constants.AVATAR_BUDGET] байт.
     */
    suspend fun put(uid: String, image: ByteArray, version: Int): Result<Unit>

    /** Удаляет аватар аккаунта [uid]. Маркер в профиле обнуляет вызывающий — следом. */
    suspend fun delete(uid: String): Result<Unit>
}
