package com.nzzima.secretmessanger.auth.domain.api

/** Пометка идущей регистрации — для того, кто её выполняет. */
interface RegistrationMarker {

    /**
     * Выполняет [block], пока регистрация помечена идущей.
     *
     * Пометка снимается в любом исходе, включая отмену по таймауту отправки: незакрытая
     * оставила бы оболочку ждать вечно.
     */
    suspend fun <T> whileRegistering(block: suspend () -> T): T
}
