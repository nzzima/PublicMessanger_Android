package com.nzzima.secretmessanger.session.domain

import com.nzzima.secretmessanger.session.domain.impl.SessionInteractorImpl
import com.nzzima.secretmessanger.session.domain.models.Session
import com.nzzima.secretmessanger.session.domain.models.SessionFailure
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionInteractorTest {

    @Test
    fun `состояние сессии видно наблюдателю сразу и после входа`() {
        val repository = FakeSessionRepository()
        val interactor = SessionInteractorImpl(repository, repository, repository)

        assertEquals(Session.Anonymous, interactor.observeSession().value)

        repository.signIn("uid-1")

        assertEquals(Session.Authenticated("uid-1"), interactor.observeSession().value)
    }

    @Test
    fun `проверка сессии уходит валидатору`() = runBlocking {
        val repository = FakeSessionRepository(Session.Authenticated("uid-1"))
        val interactor = SessionInteractorImpl(repository, repository, repository)

        assertTrue(interactor.revalidate().isSuccess)
        assertEquals(1, repository.revalidations)
    }

    @Test
    fun `мёртвая сессия отдаёт свой отказ и меняет состояние`() = runBlocking {
        val repository = FakeSessionRepository(Session.Authenticated("uid-1"))
        repository.revalidateFails = SessionFailure.Expired
        val interactor = SessionInteractorImpl(repository, repository, repository)

        assertEquals(SessionFailure.Expired, interactor.revalidate().exceptionOrNull())
        assertEquals(Session.Expired, interactor.observeSession().value)
    }

    @Test
    fun `отказ связи состояние сессии не трогает`() = runBlocking {
        val repository = FakeSessionRepository(Session.Authenticated("uid-1"))
        repository.revalidateFails = IllegalStateException("client is offline")
        val interactor = SessionInteractorImpl(repository, repository, repository)

        assertTrue(interactor.revalidate().isFailure)
        assertEquals(Session.Authenticated("uid-1"), interactor.observeSession().value)
    }

    @Test
    fun `выход переводит сессию в анонимную`() {
        val repository = FakeSessionRepository(Session.Authenticated("uid-1"))
        val interactor = SessionInteractorImpl(repository, repository, repository)

        interactor.signOut()

        assertEquals(Session.Anonymous, interactor.observeSession().value)
    }
}
