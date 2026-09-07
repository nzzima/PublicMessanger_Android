package com.nzzima.secretmessanger.session.domain

import com.nzzima.secretmessanger.session.domain.api.SessionCloser
import com.nzzima.secretmessanger.session.domain.api.SessionReader
import com.nzzima.secretmessanger.session.domain.api.SessionValidator
import com.nzzima.secretmessanger.session.domain.models.Session
import com.nzzima.secretmessanger.session.domain.models.SessionFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Сессия в памяти. Состояние задаётся тестом и не переживает создание нового экземпляра. */
class FakeSessionRepository(initial: Session = Session.Anonymous) : SessionReader, SessionCloser, SessionValidator {

    private val state = MutableStateFlow(initial)

    /** Отказ, который вернёт [revalidate]; `null` — сессия действует. */
    var revalidateFails: Throwable? = null

    /** Сколько раз спрашивали о живости сессии. */
    var revalidations = 0
        private set

    override val session: StateFlow<Session> = state.asStateFlow()

    override fun signOut() {
        state.value = Session.Anonymous
    }

    /** Повторяет договор настоящего репозитория: мёртвая сессия переводит состояние сама. */
    override suspend fun revalidate(): Result<Unit> {
        revalidations++

        val failure = revalidateFails ?: return Result.success(Unit)
        if (failure is SessionFailure.Expired) state.value = Session.Expired
        return Result.failure(failure)
    }

    /** Устанавливает [Session.Authenticated] с идентификатором [uid]. */
    fun signIn(uid: String) {
        state.value = Session.Authenticated(uid)
    }
}
