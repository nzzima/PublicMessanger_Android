package com.nzzima.secretmessanger.profile.ui

import com.nzzima.secretmessanger.avatar.domain.FakeAvatarInteractor
import com.nzzima.secretmessanger.chats.domain.api.ConversationStarter
import com.nzzima.secretmessanger.profile.domain.FakeProfileReader
import com.nzzima.secretmessanger.profile.domain.impl.ProfileInteractorImpl
import com.nzzima.secretmessanger.profile.domain.models.Profile
import com.nzzima.secretmessanger.session.domain.FakeSessionRepository
import com.nzzima.secretmessanger.session.domain.impl.SessionInteractorImpl
import com.nzzima.secretmessanger.session.domain.models.Session
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Чужой профиль: заголовок, публичные поля и кнопка «Написать». */
@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sessions = FakeSessionRepository(Session.Authenticated("uid-1"))
    private val profiles = FakeProfileReader()
    private val starter = FakeConversationStarter()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = UserProfileViewModel(
        "uid-2",
        "companion",
        SessionInteractorImpl(sessions, sessions, sessions),
        ProfileInteractorImpl(profiles),
        starter,
        FakeAvatarInteractor(),
    )

    private fun UserProfileViewModel.state() = observeUserProfileScreenState().value

    private fun UserProfileViewModel.content() = state() as UserProfileUiState.Content

    private fun profile(login: String = "companion", name: String = "Сергей", someInfo: String = "заметка") =
        Profile(id = "uid-2", login = login, name = name, someInfo = someInfo)

    /** Экран с прочитанным профилем. */
    private fun opened(): UserProfileViewModel {
        profiles.send(profile())

        return viewModel().also { dispatcher.scheduler.advanceUntilIdle() }
    }

    @Test
    fun `до профиля экран держит имя из списка контактов`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(model.state() is UserProfileUiState.Loading)
        assertEquals("companion", model.state().name)
    }

    @Test
    fun `профиль кладётся в состояние целиком`() = runTest(dispatcher) {
        val model = opened()

        assertEquals(profile(), model.content().profile)
    }

    @Test
    fun `переименование в профиле меняет имя на экране`() = runTest(dispatcher) {
        val model = opened()

        profiles.send(profile(login = "renamed"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("renamed", model.state().name)
    }

    @Test
    fun `безымянный профиль оставляет имя из списка`() = runTest(dispatcher) {
        profiles.send(profile(login = "", name = ""))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("companion", model.state().name)
    }

    @Test
    fun `кнопка заводит диалог и поручает переход`() = runTest(dispatcher) {
        val model = opened()

        model.onWrite()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("uid-1_uid-2", model.content().opened)
        assertFalse(model.content().isOpening)
    }

    @Test
    fun `в шапку диалога уходит имя из профиля, а не из списка`() = runTest(dispatcher) {
        profiles.send(profile(login = "renamed"))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onWrite()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(Triple("uid-1", "uid-2", "renamed")), starter.calls)
    }

    @Test
    fun `поручение снимается после перехода`() = runTest(dispatcher) {
        val model = opened()
        model.onWrite()
        dispatcher.scheduler.advanceUntilIdle()

        model.onOpened()

        assertNull(model.content().opened)
    }

    @Test
    fun `второе нажатие во время заведения не принимается`() = runTest(dispatcher) {
        val model = opened()
        val pending = starter.hang()

        model.onWrite()
        // Только текущие задачи: advanceUntilIdle прокрутил бы виртуальное время за
        // таймаут, и незавершённое заведение успело бы им оборваться.
        dispatcher.scheduler.runCurrent()
        model.onWrite()
        dispatcher.scheduler.runCurrent()

        assertTrue(model.content().isOpening)
        assertEquals(1, starter.calls.size)

        pending.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `молчащая сеть снимает заведение по таймауту`() = runTest(dispatcher) {
        val model = opened()
        starter.hang()

        model.onWrite()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.SERVER_SILENT, model.content().error)
        assertFalse(model.content().isOpening)
        assertNull(model.content().opened)
    }

    @Test
    fun `отказ заведения показывается строкой и никуда не ведёт`() = runTest(dispatcher) {
        val model = opened()
        starter.refusal = IllegalStateException(Constants.COMPANION_KEY_MISSING)

        model.onWrite()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.COMPANION_KEY_MISSING, model.content().error)
        assertNull(model.content().opened)
    }

    @Test
    fun `новый снимок профиля не отменяет начатого заведения`() = runTest(dispatcher) {
        val model = opened()
        val pending = starter.hang()

        model.onWrite()
        dispatcher.scheduler.runCurrent()
        profiles.send(profile(someInfo = "поправил заметку"))
        dispatcher.scheduler.runCurrent()

        assertTrue("заведение обязано пережить снимок", model.content().isOpening)
        assertEquals("поправил заметку", model.content().profile.someInfo)

        pending.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `отказ чтения профиля закрывает экран и проверяет сессию`() = runTest(dispatcher) {
        profiles.fail(IllegalStateException("профиля нет"))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state() as UserProfileUiState.Failed

        assertEquals("профиля нет", state.message)
        assertEquals("имя остаётся известным", "companion", state.name)
        assertEquals(1, sessions.revalidations)
    }

    @Test
    fun `без сессии кнопка ничего не делает`() = runTest(dispatcher) {
        val model = opened()
        sessions.signOut()

        model.onWrite()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(starter.calls.isEmpty())
    }
}

/** Заведение диалога в памяти: помнит вызовы и умеет зависнуть. */
private class FakeConversationStarter : ConversationStarter {

    /** Тройки «кто открыл — с кем — под каким именем», в порядке вызова. */
    val calls = mutableListOf<Triple<String, String, String>>()

    /** Чем отказывает заведение; `null` — проходит. */
    var refusal: Throwable? = null

    private var pending: CompletableDeferred<Unit>? = null

    /** Подвешивает следующее заведение; отпускается выполнением возвращённого. */
    fun hang(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { pending = it }

    override suspend fun start(selfId: String, companionId: String, companionLogin: String): Result<String> {
        calls += Triple(selfId, companionId, companionLogin)
        pending?.await()

        return refusal?.let { Result.failure(it) }
            ?: Result.success(listOf(selfId, companionId).sorted().joinToString("_"))
    }
}
