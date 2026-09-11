package com.nzzima.secretmessanger.auth.domain

import com.nzzima.secretmessanger.auth.domain.impl.RegistrationInteractorImpl
import com.nzzima.secretmessanger.auth.domain.models.LoginTaken
import com.nzzima.secretmessanger.auth.domain.models.RegistrationFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationInteractorTest {

    private val marker = FakeRegistrationMarker()

    private fun interactor(
        accounts: FakeAccountRepository = FakeAccountRepository(),
        logins: FakeLoginRepository = FakeLoginRepository(),
        profiles: FakeProfileRepository = FakeProfileRepository(),
    ) = RegistrationInteractorImpl(accounts, logins, profiles, marker)

    @Test
    fun `логин не по правилам отбивается до обращения к сети`() = runTest {
        val accounts = FakeAccountRepository()
        val profiles = FakeProfileRepository()

        for (bad in listOf("ab", "с-кириллицей", "слишком_длинный_логин_совсем", "с пробелом", "точка.точка")) {
            val result = interactor(accounts = accounts, profiles = profiles)
                .register("a@b.c", "123456", bad)

            assertTrue(bad, result.exceptionOrNull() is RegistrationFailure.InvalidLogin)
        }
        assertNull("профиль не должен создаваться", profiles.created)
        assertFalse("аккаунт не должен удаляться", accounts.deleted)
    }

    @Test
    fun `занятый логин отбивается до создания аккаунта`() = runTest {
        val accounts = FakeAccountRepository()
        val logins = FakeLoginRepository(mutableMapOf("nzzima" to "uid-другой"))

        val result = interactor(accounts = accounts, logins = logins)
            .register("a@b.c", "123456", "nzzima")

        assertTrue(result.exceptionOrNull() is RegistrationFailure.LoginTaken)
        assertFalse(accounts.deleted)
    }

    @Test
    fun `удачная регистрация занимает логин и пишет профиль`() = runTest {
        val logins = FakeLoginRepository()
        val profiles = FakeProfileRepository()

        val result = interactor(logins = logins, profiles = profiles)
            .register("a@b.c", "123456", "nzzima")

        assertEquals("uid-1", result.getOrNull())
        assertEquals(listOf("nzzima"), logins.claimed)
        assertEquals(Triple("uid-1", "nzzima", "nzzima"), profiles.created)
    }

    @Test
    fun `проигранная гонка за логин откатывает аккаунт`() = runTest {
        val accounts = FakeAccountRepository()
        val logins = FakeLoginRepository(claimFails = LoginTaken())
        val profiles = FakeProfileRepository()

        val result = interactor(accounts, logins, profiles).register("a@b.c", "123456", "nzzima")

        assertTrue(result.exceptionOrNull() is RegistrationFailure.LoginTaken)
        assertTrue("аккаунт обязан откатиться", accounts.deleted)
        assertNull("профиль писать нечему", profiles.created)
    }

    @Test
    fun `сетевой отказ при занятии логина аккаунт не удаляет`() = runTest {
        val accounts = FakeAccountRepository()
        val logins = FakeLoginRepository(claimFails = IllegalStateException("client is offline"))

        val result = interactor(accounts = accounts, logins = logins)
            .register("a@b.c", "123456", "nzzima")

        assertTrue(result.isFailure)
        assertFalse("на сетевой ошибке аккаунт остаётся", accounts.deleted)
    }

    @Test
    fun `отказ создания аккаунта возвращается как есть`() = runTest {
        val accounts = FakeAccountRepository(registerFails = IllegalStateException("email занят"))
        val profiles = FakeProfileRepository()

        val result = interactor(accounts = accounts, profiles = profiles)
            .register("a@b.c", "123456", "nzzima")

        assertEquals("email занят", result.exceptionOrNull()?.message)
        assertNull(profiles.created)
    }

    @Test
    fun `регистрация идёт под пометкой, чтобы оболочка её переждала`() = runTest {
        interactor().register("a@b.c", "123456", "nzzima")

        assertEquals("сессию открывает первый шаг, а профиль пишет третий", 1, marker.wraps)
    }
}
