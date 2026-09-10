package com.nzzima.secretmessanger.auth.domain.api

/** Запись профиля `users/{uid}`. */
interface ProfileRepository {

    /**
     * Создаёт профиль аккаунта [uid].
     *
     * Запись проходит только тогда, когда [login] уже занят этим же аккаунтом в реестре
     * `logins`: правило Firestore на `users/{uid}` сверяет их между собой.
     */
    suspend fun createProfile(uid: String, login: String, name: String): Result<Unit>

    /**
     * Есть ли профиль у аккаунта [uid].
     *
     * Отсутствие означает оборванную регистрацию: аккаунт в Firebase Auth создан, а логин и
     * профиль дописаны не были.
     */
    suspend fun exists(uid: String): Result<Boolean>

    /**
     * Переписывает поля профиля аккаунта [uid].
     *
     * Слияние обязательно: правило смотрит на документ целиком, каким он станет после
     * записи, а открытая половина ключа лежит в том же документе и переписывать её нечем.
     *
     * Требует, чтобы [login] был уже занят этим же аккаунтом в реестре: правило сверяет их
     * между собой. Значит при переименовании захват идёт первым, а эта запись второй.
     */
    suspend fun updateProfile(uid: String, login: String, name: String, someInfo: String): Result<Unit>
}
