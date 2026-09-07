package com.nzzima.secretmessanger.session.domain.api

/** Проверка того, что сессия ещё действует. */
interface SessionValidator {

    /**
     * Спрашивает у сервиса, признаёт ли он текущую сессию.
     *
     * Успех — сессия действует. [com.nzzima.secretmessanger.session.domain.models.SessionFailure.Expired]
     * — не действует, и [SessionReader.session] переведён в
     * [com.nzzima.secretmessanger.session.domain.models.Session.Expired]. Любой другой отказ —
     * связь: состояние сессии не меняется, повтор имеет смысл.
     *
     * Без сессии возвращает успех: проверять нечего.
     */
    suspend fun revalidate(): Result<Unit>
}
