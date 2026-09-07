package com.nzzima.secretmessanger.session.domain.api

import com.nzzima.secretmessanger.session.domain.models.Session
import kotlinx.coroutines.flow.StateFlow

/** Сессия для слоя представления. */
interface SessionInteractor {

    /** Текущее состояние сессии; обновляется при каждом входе и выходе. */
    fun observeSession(): StateFlow<Session>

    /**
     * Проверяет, действует ли сессия, и переводит [observeSession] в [Session.Expired],
     * если нет.
     *
     * Условия успеха и отказа — [SessionValidator.revalidate].
     */
    suspend fun revalidate(): Result<Unit>

    /** Завершает сессию. Локальные данные аккаунта не затрагивает. */
    fun signOut()
}
