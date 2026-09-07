package com.nzzima.secretmessanger.session.domain.impl

import com.nzzima.secretmessanger.session.domain.api.SessionCloser
import com.nzzima.secretmessanger.session.domain.api.SessionInteractor
import com.nzzima.secretmessanger.session.domain.api.SessionReader
import com.nzzima.secretmessanger.session.domain.api.SessionValidator
import com.nzzima.secretmessanger.session.domain.models.Session
import kotlinx.coroutines.flow.StateFlow

/** [SessionInteractor] поверх [SessionReader], [SessionValidator] и [SessionCloser]. */
class SessionInteractorImpl(
    private val reader: SessionReader,
    private val validator: SessionValidator,
    private val closer: SessionCloser,
) : SessionInteractor {

    override fun observeSession(): StateFlow<Session> = reader.session

    override suspend fun revalidate(): Result<Unit> = validator.revalidate()

    override fun signOut() = closer.signOut()
}
