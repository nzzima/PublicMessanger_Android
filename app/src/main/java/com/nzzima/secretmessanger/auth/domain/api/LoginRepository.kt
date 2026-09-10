package com.nzzima.secretmessanger.auth.domain.api

import com.nzzima.secretmessanger.auth.domain.models.LoginAvailability
import com.nzzima.secretmessanger.auth.domain.models.LoginTaken

/**
 * Реестр занятых логинов: коллекция `logins`, документ на логин.
 *
 * Уникальность обеспечивает правило Firestore, а не этот интерфейс: `create` по
 * существующему документу запрещён, `update` запрещён всем. [check] описывает состояние на
 * момент вызова и не резервирует имя; занимает его только [claim].
 */
interface LoginRepository {

    /** Состояние логина [login] с точки зрения аккаунта [uid]. */
    suspend fun check(login: String, uid: String?): Result<LoginAvailability>

    /**
     * Занимает [login] за аккаунтом [uid].
     *
     * Проваливается с [LoginTaken], если логин уже занят, и с исходной ошибкой при любом
     * другом отказе.
     */
    suspend fun claim(login: String, uid: String): Result<Unit>

    /**
     * Отпускает [login] — удаляет запись реестра.
     *
     * Правило пускает удаление только владельцу записи, поэтому чужое имя этим не
     * освободить. Отпускать нечего — тоже успех: удаление несуществующего документа
     * Firestore считает выполненным.
     */
    suspend fun release(login: String): Result<Unit>

    companion object {
        /** Идентификатор документа реестра: логин в нижнем регистре. */
        fun key(login: String): String = login.lowercase()
    }
}
