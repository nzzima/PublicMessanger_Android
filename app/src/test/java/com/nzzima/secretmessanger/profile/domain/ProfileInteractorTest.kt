package com.nzzima.secretmessanger.profile.domain

import com.nzzima.secretmessanger.profile.domain.impl.ProfileInteractorImpl
import com.nzzima.secretmessanger.profile.domain.models.Profile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Подстановка имени вместо пустого логина — единственное правило этого слоя. */
class ProfileInteractorTest {

    private val profiles = FakeProfileReader()
    private val interactor = ProfileInteractorImpl(profiles)

    private suspend fun profile(): Profile =
        interactor.observeProfile("uid-1").first().getOrThrow()

    @Test
    fun `пустой логин заменяется именем`() = runTest {
        profiles.send(Profile(id = "uid-1", login = "", name = "Никита", someInfo = ""))

        assertEquals("Никита", profile().login)
    }

    @Test
    fun `непустой логин остаётся как есть`() = runTest {
        profiles.send(Profile(id = "uid-1", login = "nzzima", name = "Никита", someInfo = ""))

        assertEquals("nzzima", profile().login)
    }

    @Test
    fun `имя и заметка не трогаются`() = runTest {
        profiles.send(Profile(id = "uid-1", login = "", name = "Никита", someInfo = "заметка"))

        val result = profile()

        assertEquals("Никита", result.name)
        assertEquals("заметка", result.someInfo)
    }

    @Test
    fun `отсутствие профиля доходит отказом`() = runTest {
        val error = IllegalStateException("профиля нет")
        profiles.fail(error)

        assertSame(error, interactor.observeProfile("uid-1").first().exceptionOrNull())
    }
}

/** Профиль в памяти; до первого [send] подписчик не получает ничего. */