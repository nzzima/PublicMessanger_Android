package com.nzzima.secretmessanger.auth.ui

import com.nzzima.secretmessanger.auth.domain.FakeAccountRepository
import com.nzzima.secretmessanger.auth.domain.FakeLoginRepository
import com.nzzima.secretmessanger.auth.domain.FakeProfileRepository
import com.nzzima.secretmessanger.auth.domain.FakeRegistrationMarker
import com.nzzima.secretmessanger.auth.domain.impl.AuthenticationInteractorImpl
import com.nzzima.secretmessanger.auth.domain.impl.RegistrationInteractorImpl
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        accounts: FakeAccountRepository = FakeAccountRepository(),
        logins: FakeLoginRepository = FakeLoginRepository(),
        profiles: FakeProfileRepository = FakeProfileRepository(),
    ) = AuthViewModel(
        RegistrationInteractorImpl(accounts, logins, profiles, FakeRegistrationMarker()),
        AuthenticationInteractorImpl(accounts),
    )

    private fun AuthViewModel.state() = observeAuthScreenState().value

    @Test
    fun `короткий пароль отбивается сообщением`() = runTest(dispatcher) {
        val model = viewModel()

        model.onEmailChange("a@b.co")
        model.onPasswordChange("12345")
        model.onSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Пароль должен быть не короче 6 символов", model.state().error)
    }

    @Test
    fun `битая почта отбивается первой`() = runTest(dispatcher) {
        val model = viewModel()

        model.onEmailChange("не-почта")
        model.onPasswordChange("12345")
        model.onSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Проверьте адрес почты", model.state().error)
    }

    @Test
    fun `несовпадающие пароли отбиваются при регистрации`() = runTest(dispatcher) {
        val profiles = FakeProfileRepository()
        val model = viewModel(profiles = profiles)

        model.onModeToggle()
        model.onEmailChange("a@b.co")
        model.onLoginChange("nzzima")
        model.onPasswordChange("123456")
        model.onPasswordRepeatChange("123457")
        model.onSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Пароли не совпадают", model.state().error)
        assertNull(profiles.created)
    }

    @Test
    fun `регистрация требует логин по правилам`() = runTest(dispatcher) {
        val model = viewModel()

        model.onModeToggle()
        model.onEmailChange("a@b.co")
        model.onPasswordChange("123456")
        model.onPasswordRepeatChange("123456")
        model.onSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Логин — от 3 до 20 символов: латиница, цифры, подчёркивание", model.state().error)
    }

    @Test
    fun `логин записывается без окружающих пробелов`() {
        val model = viewModel()

        model.onLoginChange("  nzzima  ")

        assertEquals("nzzima", model.state().login)
    }

    @Test
    fun `удачная регистрация проходит до профиля и не оставляет ошибки`() = runTest(dispatcher) {
        val logins = FakeLoginRepository()
        val profiles = FakeProfileRepository()
        val model = viewModel(logins = logins, profiles = profiles)

        model.onModeToggle()
        model.onEmailChange("a@b.co")
        model.onPasswordChange("123456")
        model.onPasswordRepeatChange("123456")
        model.onLoginChange("nzzima")
        model.onSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("nzzima"), logins.claimed)
        assertEquals(Triple("uid-1", "nzzima", "nzzima"), profiles.created)
        assertFalse(model.state().isSubmitting)
        assertNull(model.state().error)
    }

    @Test
    fun `занятый логин показывается ошибкой на экране`() = runTest(dispatcher) {
        val logins = FakeLoginRepository(mutableMapOf("nzzima" to "uid-другой"))
        val model = viewModel(logins = logins)

        model.onModeToggle()
        model.onEmailChange("a@b.co")
        model.onPasswordChange("123456")
        model.onPasswordRepeatChange("123456")
        model.onLoginChange("nzzima")
        model.onSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Логин уже занят — выберите другой", model.state().error)
        assertFalse(model.state().isSubmitting)
    }

    @Test
    fun `вход с верной парой проходит и не оставляет ошибки`() = runTest(dispatcher) {
        val profiles = FakeProfileRepository()
        val model = viewModel(
            accounts = FakeAccountRepository(knownCredentials = "a@b.co" to "123456"),
            profiles = profiles,
        )

        model.onEmailChange("a@b.co")
        model.onPasswordChange("123456")
        model.onSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(model.state().error)
        assertFalse(model.state().isSubmitting)
        assertNull("вход профиль не пишет", profiles.created)
    }

    @Test
    fun `неверная пара показывается одним отказом`() = runTest(dispatcher) {
        val model = viewModel(accounts = FakeAccountRepository(knownCredentials = "a@b.co" to "123456"))

        model.onEmailChange("a@b.co")
        model.onPasswordChange("неверный")
        model.onSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Неверная почта или пароль", model.state().error)
    }

    @Test
    fun `переключение режима очищает пароли`() {
        val model = viewModel()

        model.onPasswordChange("123456")
        model.onModeToggle()

        assertEquals("", model.state().password)
        assertEquals("", model.state().passwordRepeat)
        assertEquals("Регистрация", model.state().title)
    }

    @Test
    fun `зависшая регистрация снимается таймаутом`() = runTest(dispatcher) {
        val logins = FakeLoginRepository(hangs = true)
        val profiles = FakeProfileRepository()
        val model = viewModel(logins = logins, profiles = profiles)

        model.onModeToggle()
        model.onEmailChange("a@b.co")
        model.onLoginChange("nzzima")
        model.onPasswordChange("123456")
        model.onPasswordRepeatChange("123456")
        model.onSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.SERVER_SILENT, model.state().error)
        assertFalse("спиннер обязан погаснуть", model.state().isSubmitting)
        assertNull("до профиля дело не дошло", profiles.created)
    }

    @Test
    fun `зависший вход снимается таймаутом`() = runTest(dispatcher) {
        val model = viewModel(accounts = FakeAccountRepository(hangs = true))

        model.onEmailChange("a@b.co")
        model.onPasswordChange("123456")
        model.onSubmit()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.SERVER_SILENT, model.state().error)
        assertFalse(model.state().isSubmitting)
    }
}
