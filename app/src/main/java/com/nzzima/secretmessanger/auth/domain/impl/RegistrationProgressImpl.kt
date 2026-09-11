package com.nzzima.secretmessanger.auth.domain.impl

import com.nzzima.secretmessanger.auth.domain.api.RegistrationMarker
import com.nzzima.secretmessanger.auth.domain.api.RegistrationProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Признак идущей регистрации в памяти процесса.
 *
 * Одиночка на два интерфейса: помечает регистрация, ждёт оболочка — объекты разные, общего у
 * них только этот признак. Хранить его дольше жизни процесса незачем: регистрацию, убитую
 * вместе с приложением, ждать уже некому.
 */
class RegistrationProgressImpl : RegistrationProgress, RegistrationMarker {

    private val inProgress = MutableStateFlow(false)

    override suspend fun awaitIdle() {
        inProgress.first { !it }
    }

    override suspend fun <T> whileRegistering(block: suspend () -> T): T {
        inProgress.value = true

        return try {
            block()
        } finally {
            inProgress.value = false
        }
    }
}
