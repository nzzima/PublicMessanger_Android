package com.nzzima.secretmessanger.chats.ui

import com.nzzima.secretmessanger.chats.domain.FakeConversationRepository
import com.nzzima.secretmessanger.chats.domain.chat
import com.nzzima.secretmessanger.chats.domain.header
import com.nzzima.secretmessanger.chats.domain.impl.ChatsInteractorImpl
import com.nzzima.secretmessanger.crypto.domain.FakeConversationKeys
import com.nzzima.secretmessanger.session.domain.FakeSessionRepository
import com.nzzima.secretmessanger.session.domain.impl.SessionInteractorImpl
import com.nzzima.secretmessanger.session.domain.models.Session
import com.nzzima.secretmessanger.session.domain.models.SessionFailure
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/** Состояния экрана списка: пусто, список, отказ и повторная подписка после отказа. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sessions = FakeSessionRepository(Session.Authenticated("uid-1"))
    private val conversations = FakeConversationRepository()

    /** Ключей диалогов ни у кого нет: превью здесь не проверяется, это дело интерактора. */
    private val noKeys = FakeConversationKeys()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ChatsViewModel(
        SessionInteractorImpl(sessions, sessions, sessions),
        ChatsInteractorImpl(conversations, noKeys),
    )

    private fun ChatsViewModel.state() = observeChatsScreenState().value

    @Test
    fun `до первого снимка экран ждёт`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(ChatsUiState.Loading, model.state())
    }

    @Test
    fun `снимок без диалогов — пусто, а не пустой список`() = runTest(dispatcher) {
        conversations.send(emptyList())
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(ChatsUiState.Empty, model.state())
    }

    @Test
    fun `снимок с диалогами кладётся в состояние`() = runTest(dispatcher) {
        conversations.send(listOf(header(chat = chat(id = "живой"), lastMessage = "привет")))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state()

        assertEquals(listOf("живой"), (state as ChatsUiState.Content).conversations.map { it.chat.id })
    }

    @Test
    fun `новый снимок заменяет прежний`() = runTest(dispatcher) {
        conversations.send(listOf(header(chat = chat(id = "первый"), lastMessage = "привет")))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        conversations.send(listOf(header(chat = chat(id = "второй"), lastMessage = "привет")))
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state() as ChatsUiState.Content

        assertEquals(listOf("второй"), state.conversations.map { it.chat.id })
    }

    @Test
    fun `отказ показывается своим текстом`() = runTest(dispatcher) {
        conversations.fail(IllegalStateException("нет доступа"))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("нет доступа", (model.state() as ChatsUiState.Failed).message)
    }

    @Test
    fun `отказ без текста показывается общим сообщением`() = runTest(dispatcher) {
        conversations.fail(IllegalStateException())
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.SERVER_SILENT, (model.state() as ChatsUiState.Failed).message)
    }

    @Test
    fun `повтор подписывается заново`() = runTest(dispatcher) {
        conversations.fail(IllegalStateException("нет доступа"))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.retry()
        conversations.send(listOf(header(chat = chat(id = "живой"), lastMessage = "привет")))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, conversations.subscriptions)
        assertEquals(
            listOf("живой"),
            (model.state() as ChatsUiState.Content).conversations.map { it.chat.id },
        )
    }

    @Test
    fun `отказ подписки проверяет сессию`() = runTest(dispatcher) {
        conversations.fail(IllegalStateException("PERMISSION_DENIED"))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("отказ обязан проверяться на мёртвую сессию", 1, sessions.revalidations)
    }

    @Test
    fun `успешный снимок сессию не переспрашивает`() = runTest(dispatcher) {
        conversations.send(listOf(header(chat = chat(id = "живой"), lastMessage = "привет")))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, sessions.revalidations)
    }

    @Test
    fun `мёртвая сессия, найденная по отказу, снимает сессию с аккаунта`() = runTest(dispatcher) {
        sessions.revalidateFails = SessionFailure.Expired
        conversations.fail(IllegalStateException("PERMISSION_DENIED"))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(Session.Expired, sessions.session.value)
    }

    @Test
    fun `без сессии подписки не бывает`() = runTest(dispatcher) {
        sessions.signOut()
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, conversations.subscriptions)
        assertSame(ChatsUiState.Loading, model.state())
    }
}
