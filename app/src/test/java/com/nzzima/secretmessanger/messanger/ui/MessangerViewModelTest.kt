package com.nzzima.secretmessanger.messanger.ui

import com.nzzima.secretmessanger.chats.domain.FakeConversationRepository
import com.nzzima.secretmessanger.chats.domain.chat
import com.nzzima.secretmessanger.chats.domain.models.ConversationGone
import com.nzzima.secretmessanger.chats.domain.models.Moment
import com.nzzima.secretmessanger.avatar.domain.FakeAvatarInteractor
import com.nzzima.secretmessanger.crypto.domain.FakeConversationKeys
import com.nzzima.secretmessanger.profile.domain.FakeCompanionProfiles
import com.nzzima.secretmessanger.crypto.domain.models.CryptoFailure
import com.nzzima.secretmessanger.messanger.domain.FakeMessageRepository
import com.nzzima.secretmessanger.messanger.domain.impl.MessangerInteractorImpl
import com.nzzima.secretmessanger.messanger.domain.message
import com.nzzima.secretmessanger.session.domain.FakeSessionRepository
import com.nzzima.secretmessanger.session.domain.impl.SessionInteractorImpl
import com.nzzima.secretmessanger.session.domain.models.Session
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Состояния экрана переписки: лента, набранный текст, отправка и отказы. */
@OptIn(ExperimentalCoroutinesApi::class)
class MessangerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sessions = FakeSessionRepository(Session.Authenticated("uid-1"))
    private val conversations = FakeConversationRepository()
    private val messages = FakeMessageRepository()

    /** Диалог без шифрования: расшифровка здесь не проверяется, это дело интерактора. */
    private val noKeys = FakeConversationKeys()

    private val profiles = FakeCompanionProfiles()
    private val avatars = FakeAvatarInteractor(image = byteArrayOf(1, 2, 3))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MessangerViewModel(
        "uid-1_uid-2",
        SessionInteractorImpl(sessions, sessions, sessions),
        MessangerInteractorImpl(conversations, messages, noKeys),
        profiles,
        avatars,
    )

    private fun MessangerViewModel.state() = observeMessangerScreenState().value

    private fun MessangerViewModel.content() = state() as MessangerUiState.Content

    /** Открытая переписка: шапка и одна чужая реплика уже пришли, экран на глазах. */
    private fun opened(): MessangerViewModel {
        conversations.sendChat(chat())
        messages.send(listOf(message(body = "привет")))

        return viewModel().also {
            it.onVisible()
            dispatcher.scheduler.advanceUntilIdle()
        }
    }

    @Test
    fun `до первого снимка экран ждёт`() = runTest(dispatcher) {
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(MessangerUiState.Loading, model.state())
    }

    @Test
    fun `шапка без реплик экран не открывает`() = runTest(dispatcher) {
        conversations.sendChat(chat())
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame("реплики — вторая половина снимка", MessangerUiState.Loading, model.state())
    }

    @Test
    fun `снимок кладётся в состояние вместе с названием диалога`() = runTest(dispatcher) {
        val model = opened()

        assertEquals("companion", model.content().title)
        assertEquals(listOf("привет"), model.content().replies.map { it.text })
    }

    @Test
    fun `диалог без единой реплики открывается пустым`() = runTest(dispatcher) {
        conversations.sendChat(chat())
        messages.send(emptyList())
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(model.content().replies.isEmpty())
    }

    @Test
    fun `набранное переживает новый снимок`() = runTest(dispatcher) {
        val model = opened()

        model.onDraftChange("недописанное")
        messages.send(listOf(message(id = "m-2", body = "чужая реплика")))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("недописанное", model.content().draft)
    }

    @Test
    fun `отправка уходит в базу и очищает набранное`() = runTest(dispatcher) {
        val model = opened()

        model.onDraftChange("  завтра в семь  ")
        model.onSend()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("завтра в семь", messages.sent.single().second.body)
        assertEquals("", model.content().draft)
        assertFalse(model.content().isSending)
    }

    @Test
    fun `пустой набор не отправляется`() = runTest(dispatcher) {
        val model = opened()

        model.onDraftChange("   ")
        model.onSend()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(messages.sent.isEmpty())
        assertFalse("кнопка обязана быть недоступна", model.content().canSend)
    }

    @Test
    fun `отказ отправки сохраняет набранное и называет причину`() = runTest(dispatcher) {
        val model = opened()
        messages.refusal = CryptoFailure.NoKey

        model.onDraftChange("завтра в семь")
        model.onSend()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("завтра в семь", model.content().draft)
        assertEquals(Constants.NO_CONVERSATION_KEY, model.content().error)
    }

    @Test
    fun `правка набранного снимает показанную ошибку`() = runTest(dispatcher) {
        val model = opened()
        messages.refusal = CryptoFailure.NoKey

        model.onDraftChange("первая попытка")
        model.onSend()
        dispatcher.scheduler.advanceUntilIdle()

        model.onDraftChange("вторая")

        assertEquals(null, model.content().error)
    }

    @Test
    fun `отказ подписки закрывает экран и проверяет сессию`() = runTest(dispatcher) {
        conversations.sendChat(chat())
        messages.fail(IllegalStateException("PERMISSION_DENIED"))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state() as MessangerUiState.Failed

        assertEquals("PERMISSION_DENIED", state.message)
        assertTrue("обрыв связи лечится повтором", state.canRetry)
        assertEquals(1, sessions.revalidations)
    }

    @Test
    fun `стёртый диалог повторять не предлагает и сессию не трогает`() = runTest(dispatcher) {
        conversations.failChat(ConversationGone())
        messages.send(listOf(message()))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.state() as MessangerUiState.Failed

        assertEquals(Constants.CONVERSATION_GONE, state.message)
        assertFalse("возвращать нечего", state.canRetry)
        assertEquals("сессия здесь ни при чём", 0, sessions.revalidations)
    }

    @Test
    fun `открытый экран отмечает прочтение по последней чужой реплике`() = runTest(dispatcher) {
        conversations.sendChat(chat())
        messages.send(listOf(message(senderId = "uid-2", date = Moment(10, 250))))
        val model = viewModel()
        model.onVisible()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Triple("uid-1_uid-2", "uid-1", Moment(10, 250)), conversations.receipts.single())
    }

    @Test
    fun `невидимый экран прочтение не отмечает`() = runTest(dispatcher) {
        conversations.sendChat(chat())
        messages.send(listOf(message(senderId = "uid-2", date = Moment(10, 0))))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue("свёрнутое приложение не читает", conversations.receipts.isEmpty())
    }

    @Test
    fun `экран вернулся на глаза — отмечает то, что накопилось`() = runTest(dispatcher) {
        conversations.sendChat(chat())
        messages.send(listOf(message(senderId = "uid-2", date = Moment(10, 0))))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        model.onVisible()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Moment(10, 0), conversations.receipts.single().third)
    }

    @Test
    fun `ушедший с глаз экран новые реплики прочитанными не считает`() = runTest(dispatcher) {
        val model = opened()
        conversations.receipts.clear()

        model.onHidden()
        messages.send(listOf(message(id = "m-2", senderId = "uid-2", date = Moment(20, 0))))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(conversations.receipts.isEmpty())
    }

    @Test
    fun `свои реплики прочтение не отмечают`() = runTest(dispatcher) {
        conversations.sendChat(chat())
        messages.send(listOf(message(senderId = "uid-1", date = Moment(10, 0))))
        val model = viewModel()
        model.onVisible()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue("отмечаться нечем: чужих реплик нет", conversations.receipts.isEmpty())
    }

    @Test
    fun `без сессии подписки не бывает`() = runTest(dispatcher) {
        sessions.signOut()
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertSame(MessangerUiState.Loading, model.state())
        assertEquals(null, conversations.requestedChat)
    }

    @Test
    fun `аватары участников доезжают до ленты`() = runTest(dispatcher) {
        profiles.put("uid-1", version = 1)
        profiles.put("uid-2", version = 3)
        val model = opened()

        val state = model.content()

        assertEquals("свой кружок берётся наравне с чужим", setOf("uid-1", "uid-2"), state.avatars.keys)
        assertEquals(listOf("uid-1" to 1, "uid-2" to 3), avatars.requested)
    }

    @Test
    fun `профиль участника спрашивается один раз, а не на каждую реплику`() = runTest(dispatcher) {
        profiles.put("uid-2", version = 3)
        val model = opened()

        messages.send(listOf(message(body = "привет"), message(id = "второе", body = "и ещё")))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, profiles.requests("uid-2"))
        assertEquals("картинка обязана пережить новый снимок", 1, model.content().avatars.size)
    }

    @Test
    fun `добавленный в диалог участник спрашивается, а прежние — нет`() = runTest(dispatcher) {
        profiles.put("uid-2", version = 3)
        profiles.put("uid-3", version = 1)
        val model = opened()

        conversations.sendChat(
            chat(
                members = listOf("uid-1", "uid-2", "uid-3"),
                logins = mapOf("uid-1" to "self", "uid-2" to "второй", "uid-3" to "третий"),
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, profiles.requests("uid-2"))
        assertEquals("новичка спросить надо — иначе он останется без лица", 1, profiles.requests("uid-3"))
        assertEquals(setOf("uid-2", "uid-3"), model.content().avatars.keys)
    }

    @Test
    fun `безаватарный участник не оставляет в карте пустоты`() = runTest(dispatcher) {
        profiles.put("uid-2", version = 0)
        val model = opened()

        assertTrue(model.content().avatars.isEmpty())
        assertEquals("спросить о нём всё равно надо было — ровно раз", 1, profiles.requests("uid-2"))
    }
}
