package com.nzzima.secretmessanger.session.data.impl

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.nzzima.secretmessanger.session.domain.api.SessionCloser
import com.nzzima.secretmessanger.session.domain.api.SessionReader
import com.nzzima.secretmessanger.session.domain.api.SessionValidator
import com.nzzima.secretmessanger.session.domain.models.Session
import com.nzzima.secretmessanger.session.domain.models.SessionFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Сессия поверх Firebase Authentication.
 *
 * Начальное значение [session] берётся из [FirebaseAuth.getCurrentUser] при создании,
 * дальнейшие — из слушателя состояния авторизации. Слушатель снимается только вместе с
 * процессом.
 *
 * Сессия Firebase сохраняется на устройстве и переживает перезапуск приложения. Пережить
 * она может и собственную смерть: `currentUser` остаётся на месте после смены пароля,
 * отключения аккаунта и отзыва токена обновления, поэтому [Session.Expired] выставляет
 * только [revalidate], а не слушатель.
 */
class SessionRepositoryImpl(private val auth: FirebaseAuth) : SessionReader, SessionCloser, SessionValidator {

    private val state = MutableStateFlow(auth.currentUser.toSession())

    override val session: StateFlow<Session> = state.asStateFlow()

    init {
        auth.addAuthStateListener { updated -> state.value = updated.currentUser.toSession() }
    }

    override fun signOut() = auth.signOut()

    /**
     * Запрашивает токен без принудительного обновления.
     *
     * Живой токен отдаётся из памяти и не стоит ничего; просроченный обновляется по токену
     * обновления — на этом и ловится мёртвая сессия. Принудительное обновление добавило бы
     * сетевой запрос на каждый запуск, ничего не проверив сверх этого: база принимает токен
     * до конца его срока в любом случае.
     */
    override suspend fun revalidate(): Result<Unit> {
        val user = auth.currentUser ?: return Result.success(Unit)

        return runCatching { user.getIdToken(false).await() }
            .map { }
            .recoverCatching { error ->
                if (error !is FirebaseAuthInvalidUserException && error !is FirebaseAuthInvalidCredentialsException) {
                    throw error
                }
                state.value = Session.Expired
                throw SessionFailure.Expired
            }
    }
}

private fun FirebaseUser?.toSession(): Session =
    if (this == null) Session.Anonymous else Session.Authenticated(uid)
