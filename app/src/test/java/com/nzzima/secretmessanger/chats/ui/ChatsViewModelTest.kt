package com.nzzima.secretmessanger.chats.ui

import com.nzzima.secretmessanger.avatar.domain.FakeAvatarInteractor
import com.nzzima.secretmessanger.chats.domain.FakeConversationRepository
import com.nzzima.secretmessanger.chats.domain.chat
import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.header
import com.nzzima.secretmessanger.chats.domain.impl.ChatEraserImpl
import com.nzzima.secretmessanger.chats.domain.impl.ChatsInteractorImpl
import com.nzzima.secretmessanger.messanger.domain.FakeMessageRepository
import com.nzzima.secretmessanger.crypto.domain.FakeConversationKeys
import com.nzzima.secretmessanger.profile.domain.FakeCompanionProfiles
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    private val messages = FakeMessageRepository()

    private val profiles = FakeCompanionProfiles()
    private val avatars = FakeAvatarInteractor(image = byteArrayOf(1, 2, 3))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ChatsViewModel(
        SessionInteractorImpl(sessions, sessions, sessions),
        ChatsInteractorImpl(conversations, noKeys),
        profiles,
        avatars,
        ChatEraserImpl(conversations, messages, noKeys),
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

    @Test
    fun `аватар собеседника доезжает до строки`() = runTest(dispatcher) {
        profiles.put("uid-2", version = 2)
        conversations.send(listOf(header(chat = chat(id = "живой"), lastMessage = "привет")))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state() as ChatsUiState.Content

        assertEquals(listOf("uid-2" to 2), avatars.requested)
        assertEquals(listOf(1.toByte(), 2, 3), state.avatars.getValue("uid-2").toList())
    }

    @Test
    fun `у группы аватар не спрашивается вовсе`() = runTest(dispatcher) {
        val group = chat(
            id = "группа",
            members = listOf("uid-1", "uid-2", "uid-3"),
            logins = mapOf("uid-1" to "self", "uid-2" to "второй", "uid-3" to "третий"),
        )
        conversations.send(listOf(header(chat = group, lastMessage = "привет")))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("одного из нескольких показывать нечем — там значок", emptyList<Pair<String, Int>>(), avatars.requested)
        assertTrue((model.state() as ChatsUiState.Content).avatars.isEmpty())
    }

    @Test
    fun `профиль собеседника спрашивается один раз, а не на каждую реплику`() = runTest(dispatcher) {
        profiles.put("uid-2", version = 2)
        conversations.send(listOf(header(chat = chat(id = "живой"), lastMessage = "привет")))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        conversations.send(listOf(header(chat = chat(id = "живой"), lastMessage = "и ещё одна")))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, profiles.requests("uid-2"))
        assertEquals("картинка обязана пережить новый снимок", 1, (model.state() as ChatsUiState.Content).avatars.size)
    }

    @Test
    fun `безаватарный собеседник не оставляет в карте пустоты`() = runTest(dispatcher) {
        profiles.put("uid-2", version = 0)
        conversations.send(listOf(header(chat = chat(id = "живой"), lastMessage = "привет")))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue((model.state() as ChatsUiState.Content).avatars.isEmpty())
        assertEquals("спросить о нём всё равно надо было — ровно раз", 1, profiles.requests("uid-2"))
    }

    /** Экран со списком из одного диалога [chat]. */
    private fun opened(chat: Chat = chat(id = "живой")): ChatsViewModel {
        conversations.send(listOf(header(chat = chat, lastMessage = "привет")))

        return viewModel().also { dispatcher.scheduler.advanceUntilIdle() }
    }

    private fun ChatsViewModel.content() = state() as ChatsUiState.Content

    private fun ChatsViewModel.asked() = content().conversations.single().let(::onEraseAsked)

    @Test
    fun `долгое нажатие спрашивает про удаление`() = runTest(dispatcher) {
        val model = opened()

        model.asked()

        assertEquals("живой", model.content().asking?.chat?.id)
    }

    @Test
    fun `про чужую группу вопрос не задаётся вовсе`() = runTest(dispatcher) {
        val model = opened(
            chat(id = "группа", selfId = "uid-1", members = listOf("uid-1", "uid-2", "uid-3"), owner = "uid-2"),
        )

        model.asked()

        // Кнопки нет и вопроса нет: правила откажут, а отказ по правам человеку ничего не
        // объясняет.
        assertNull(model.content().asking)
    }

    @Test
    fun `отмена закрывает вопрос, ничего не стерев`() = runTest(dispatcher) {
        val model = opened()
        model.asked()

        model.onEraseDismissed()

        assertNull(model.content().asking)
        assertTrue(conversations.erased.isEmpty())
    }

    @Test
    fun `подтверждение стирает переписку и закрывает вопрос`() = runTest(dispatcher) {
        val model = opened()
        messages.stock["живой"] = 5
        model.asked()

        model.onEraseConfirmed()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("живой"), conversations.erased)
        assertNull(model.content().asking)
        assertFalse(model.content().isErasing)
    }

    @Test
    fun `строка уходит из списка подпиской, а не рукой`() = runTest(dispatcher) {
        val model = opened()
        model.asked()

        model.onEraseConfirmed()
        dispatcher.scheduler.advanceUntilIdle()

        // Снимок ещё не пришёл — строка на месте: второго источника правды у списка нет.
        assertEquals(listOf("живой"), model.content().conversations.map { it.chat.id })

        conversations.send(emptyList())
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(ChatsUiState.Empty, model.state())
    }

    @Test
    fun `отказ остаётся в вопросе, а переписка — в списке`() = runTest(dispatcher) {
        val model = opened()
        conversations.eraseFails = IllegalStateException("нет прав")
        model.asked()

        model.onEraseConfirmed()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("нет прав", model.content().error)
        assertEquals("повторить надо там же, где спросили", "живой", model.content().asking?.chat?.id)
        assertFalse(model.content().isErasing)
    }

    @Test
    fun `молчащая сеть снимает стирание по сроку`() = runTest(dispatcher) {
        val model = opened()
        messages.hangErase()
        model.asked()

        model.onEraseConfirmed()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.SERVER_SILENT, model.content().error)
        assertFalse(model.content().isErasing)
    }

    @Test
    fun `второе подтверждение во время стирания не принимается`() = runTest(dispatcher) {
        val model = opened()
        val pending = messages.hangErase()
        model.asked()

        model.onEraseConfirmed()
        // Только текущие задачи: advanceUntilIdle прокрутил бы время за срок, и незавершённое
        // стирание успело бы им оборваться.
        dispatcher.scheduler.runCurrent()
        model.onEraseConfirmed()
        dispatcher.scheduler.runCurrent()

        assertTrue(model.content().isErasing)
        assertEquals(1, messages.pages.size)

        pending.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `во время стирания вопрос не закрывается отменой`() = runTest(dispatcher) {
        val model = opened()
        val pending = messages.hangErase()
        model.asked()

        model.onEraseConfirmed()
        dispatcher.scheduler.runCurrent()
        model.onEraseDismissed()

        assertEquals("закрывать нечего — стирание уже пошло", "живой", model.content().asking?.chat?.id)

        pending.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
    }
}
