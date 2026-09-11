package com.nzzima.secretmessanger.main.ui

import com.nzzima.secretmessanger.auth.domain.FakeLoginRepository
import com.nzzima.secretmessanger.auth.domain.FakeProfileRepository
import com.nzzima.secretmessanger.auth.domain.api.RegistrationProgress
import com.nzzima.secretmessanger.auth.domain.impl.ProfileRepairInteractorImpl
import com.nzzima.secretmessanger.crypto.domain.api.IdentityInteractor
import com.nzzima.secretmessanger.presence.domain.FakePresenceInteractor
import com.nzzima.secretmessanger.crypto.domain.models.IdentityState
import com.nzzima.secretmessanger.session.domain.FakeSessionRepository
import com.nzzima.secretmessanger.session.domain.impl.SessionInteractorImpl
import com.nzzima.secretmessanger.session.domain.models.Session
import com.nzzima.secretmessanger.session.domain.models.SessionFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Оболочка приложения. Главная проверка — **в чаты не попасть мимо развилки**.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sessions = FakeSessionRepository()
    private val identity = FakeIdentityInteractor()

    // По умолчанию профиль у аккаунта есть: достройка — исключение, а не обычный путь.
    private var profiles = FakeProfileRepository(mutableSetOf("uid-1"))
    private var logins = FakeLoginRepository()
    private val progress = FakeRegistrationProgress()
    private val presence = FakePresenceInteractor()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = RootViewModel(
        SessionInteractorImpl(sessions, sessions, sessions),
        identity,
        ProfileRepairInteractorImpl(profiles, logins),
        progress,
        presence,
    )

    private fun RootViewModel.state() = observeRootState().value

    @Test
    fun `без сессии оболочка анонимна и ключ не проверяется`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(RootState.Anonymous, model.state())
        assertEquals("развилка не должна вызываться без сессии", 0, identity.prepares)
    }

    @Test
    fun `сессия с готовым ключом открывает чаты`() = runTest(dispatcher) {
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(RootState.Ready, model.state())
        assertEquals(1, identity.prepares)
    }

    @Test
    fun `чужая половина в профиле держит на развилке, а не пускает в чаты`() = runTest(dispatcher) {
        identity.state = IdentityState.NeedsConfirmation
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(RootState.NeedsConfirmation, model.state())
        assertNotEquals(RootState.Ready, model.state())
    }

    @Test
    fun `отказ проверки не пускает в чаты и показывает причину`() = runTest(dispatcher) {
        identity.prepareFails = IllegalStateException("client is offline")
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(RootState.Failed("client is offline"), model.state())
    }

    @Test
    fun `подтверждение публикует поверх и открывает чаты`() = runTest(dispatcher) {
        identity.state = IdentityState.NeedsConfirmation
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.confirmOverwrite()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("uid-1"), identity.overwritten)
        assertSame(RootState.Ready, model.state())
    }

    @Test
    fun `неудачная публикация оставляет на развилке`() = runTest(dispatcher) {
        identity.state = IdentityState.NeedsConfirmation
        identity.overwriteFails = IllegalStateException("PERMISSION_DENIED")
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.confirmOverwrite()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(RootState.Failed("PERMISSION_DENIED"), model.state())
    }

    @Test
    fun `повтор после отказа связи доводит до чатов`() = runTest(dispatcher) {
        identity.prepareFails = IllegalStateException("client is offline")
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        identity.prepareFails = null
        model.retry()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(RootState.Ready, model.state())
    }

    @Test
    fun `мёртвая сессия уводит на экран входа, не трогая профиль и ключ`() = runTest(dispatcher) {
        sessions.revalidateFails = SessionFailure.Expired
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(RootState.Expired, model.state())
        assertEquals("мёртвая сессия не должна доходить до ключа", 0, identity.prepares)
    }

    @Test
    fun `отказ связи при проверке сессии оставляет повтор осмысленным`() = runTest(dispatcher) {
        sessions.revalidateFails = IllegalStateException("client is offline")
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(RootState.Failed("client is offline"), model.state())

        sessions.revalidateFails = null
        model.retry()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(RootState.Ready, model.state())
    }

    @Test
    fun `живая сессия проверяется ровно один раз за вход`() = runTest(dispatcher) {
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(RootState.Ready, model.state())
        assertEquals(1, sessions.revalidations)
    }

    @Test
    fun `выход с экрана устаревшего входа открывает авторизацию`() = runTest(dispatcher) {
        sessions.revalidateFails = SessionFailure.Expired
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.signOut()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(RootState.Anonymous, model.state())
    }


    @Test
    fun `пульс бьётся, пока экран виден и вход пройден`() = runTest(dispatcher) {
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onVisible()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("uid-1", presence.beating)
    }

    @Test
    fun `до развилки ключа человек не числится в сети`() = runTest(dispatcher) {
        identity.state = IdentityState.NeedsConfirmation
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onVisible()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("отмечаться, ещё не войдя, — значит врать", presence.beating)
    }

    @Test
    fun `уход с экрана прекращает пульс`() = runTest(dispatcher) {
        sessions.signIn("uid-1")
        val model = viewModel()
        model.onVisible()
        dispatcher.scheduler.advanceUntilIdle()

        model.onHidden()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(presence.beating)
    }

    @Test
    fun `выход из аккаунта прекращает пульс`() = runTest(dispatcher) {
        sessions.signIn("uid-1")
        val model = viewModel()
        model.onVisible()
        dispatcher.scheduler.advanceUntilIdle()

        model.signOut()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(presence.beating)
    }

    @Test
    fun `выход возвращает в анонимное состояние`() = runTest(dispatcher) {
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.signOut()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(RootState.Anonymous, model.state())
        assertEquals(Session.Anonymous, sessions.session.value)
    }
}

/**
 * [RegistrationProgress], которым распоряжается тест.
 *
 * Ожидание устроено так же, как в бою, — на `StateFlow`: подделывать нечего, вся суть в том,
 * что оболочка на нём останавливается.
 */
private class FakeRegistrationProgress : RegistrationProgress {

    private val inProgress = MutableStateFlow(false)

    fun begin() {
        inProgress.value = true
    }

    fun end() {
        inProgress.value = false
    }

    override suspend fun awaitIdle() {
        inProgress.first { !it }
    }
}

/** [IdentityInteractor] в памяти. Считает вызовы, чтобы ловить лишние проверки. */
private class FakeIdentityInteractor : IdentityInteractor {

    var state: IdentityState = IdentityState.Ready
    var prepareFails: Throwable? = null
    var overwriteFails: Throwable? = null
    var prepares = 0
    val overwritten = mutableListOf<String>()

    override suspend fun prepare(uid: String): Result<IdentityState> {
        prepares++
        return prepareFails?.let { Result.failure(it) } ?: Result.success(state)
    }

    override suspend fun publishOverwriting(uid: String): Result<Unit> {
        overwriteFails?.let { return Result.failure(it) }
        overwritten += uid
        return Result.success(Unit)
    }
}

/**
 * Достройка оборванной регистрации — отдельным набором, потому что проверяет **порядок**:
 * профиль раньше ключа. Порядок здесь не вкусовщина: публикация открытой половины пишет
 * `users/{uid}` слиянием, и на аккаунте без профиля правило её не пропускает.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelRepairTest {

    private val dispatcher = StandardTestDispatcher()
    private val sessions = FakeSessionRepository()
    private val identity = FakeIdentityInteractor()
    private val profiles = FakeProfileRepository()
    private val logins = FakeLoginRepository()
    private val progress = FakeRegistrationProgress()
    private val presence = FakePresenceInteractor()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = RootViewModel(
        SessionInteractorImpl(sessions, sessions, sessions),
        identity,
        ProfileRepairInteractorImpl(profiles, logins),
        progress,
        presence,
    )

    private fun RootViewModel.state() = observeRootState().value

    @Test
    fun `аккаунт без профиля уводит на достройку и ключ не трогает`() = runTest(dispatcher) {
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(RootState.NeedsProfile(), model.state())
        assertEquals("ключ не должен проверяться без профиля", 0, identity.prepares)
    }

    @Test
    fun `идущая регистрация пережидается, а не читается как оборванная`() = runTest(dispatcher) {
        // Первый шаг регистрации: аккаунт создан, сессия открыта, профиля ещё нет.
        progress.begin()
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame("профиль ещё пишется — судить не о чем", RootState.Checking, model.state())
        assertEquals("ключ тем более не трогаем", 0, identity.prepares)

        // Третий шаг и конец регистрации.
        profiles.createProfile("uid-1", login = "nzzima", name = "nzzima")
        progress.end()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(RootState.Ready, model.state())
    }

    @Test
    fun `успешная достройка занимает логин, пишет профиль и продолжает вход`() = runTest(dispatcher) {
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.repairProfile("nzzima")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("nzzima"), logins.claimed)
        assertSame(RootState.Ready, model.state())
        assertEquals("после достройки ключ обязан проверяться", 1, identity.prepares)
    }

    @Test
    fun `занятый логин возвращает на тот же экран с причиной`() = runTest(dispatcher) {
        logins.claim("nzzima", "uid-2")
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.repairProfile("nzzima")
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state()
        assertTrue("должны остаться на достройке", state is RootState.NeedsProfile)
        assertNotNull((state as RootState.NeedsProfile).error)
        assertEquals(0, identity.prepares)
    }

    @Test
    fun `аккаунт с профилем на достройку не заходит`() = runTest(dispatcher) {
        profiles.createProfile("uid-1", "nzzima", "nzzima")
        sessions.signIn("uid-1")
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(RootState.Ready, model.state())
        assertEquals(1, identity.prepares)
    }
}
