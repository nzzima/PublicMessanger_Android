package com.nzzima.secretmessanger.messanger.ui

import com.nzzima.secretmessanger.Refused
import com.nzzima.secretmessanger.chats.domain.FakeConversationRepository
import com.nzzima.secretmessanger.chats.domain.chat
import com.nzzima.secretmessanger.chats.domain.models.ConversationGone
import com.nzzima.secretmessanger.chats.domain.models.Moment
import com.nzzima.secretmessanger.avatar.domain.FakeAvatarInteractor
import com.nzzima.secretmessanger.crypto.domain.FakeConversationKeys
import com.nzzima.secretmessanger.messanger.domain.FakeLocationSource
import com.nzzima.secretmessanger.messanger.domain.FakePhotoInteractor
import com.nzzima.secretmessanger.messanger.domain.FakeVoiceInteractor
import com.nzzima.secretmessanger.messanger.domain.FakeVoicePlayer
import com.nzzima.secretmessanger.messanger.domain.FakeVoiceRecorder
import com.nzzima.secretmessanger.presence.domain.FakePresenceInteractor
import com.nzzima.secretmessanger.presence.domain.models.Presence
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
import com.nzzima.secretmessanger.messanger.domain.models.MessageKind
import com.nzzima.secretmessanger.photo.domain.models.PhotoSize
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private val photos = FakePhotoInteractor()
    private val places = FakeLocationSource()
    private val presence = FakePresenceInteractor()
    private val voices = FakeVoiceInteractor()
    private val recorder = FakeVoiceRecorder()
    private val player = FakeVoicePlayer()
    private val avatars = FakeAvatarInteractor(image = byteArrayOf(1, 2, 3))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MessangerViewModel(
        "uid-1_uid-2",
        SessionInteractorImpl(sessions, sessions, sessions),
        MessangerInteractorImpl(conversations, messages, noKeys, photos, voices),
        profiles,
        avatars,
        photos,
        places,
        presence,
        voices,
        recorder,
        player,
    )

    /** Та же модель, но телефон места не знает: геолокация выключена или приёмник молчит. */
    private fun viewModelWithoutPlace() = MessangerViewModel(
        "uid-1_uid-2",
        SessionInteractorImpl(sessions, sessions, sessions),
        MessangerInteractorImpl(conversations, messages, noKeys, photos, voices),
        profiles,
        avatars,
        photos,
        FakeLocationSource(place = null),
        presence,
        voices,
        recorder,
        player,
    ).also {
        conversations.sendChat(chat())
        messages.send(listOf(message(body = "привет")))
        dispatcher.scheduler.advanceUntilIdle()
    }

    /** Открытая группа, в которой мы не создатель: из неё можно выйти. */
    private fun groupOpened(): MessangerViewModel {
        conversations.sendChat(
            chat(
                id = "группа",
                members = listOf("uid-1", "uid-2", "uid-3"),
                logins = mapOf("uid-1" to "self", "uid-2" to "второй", "uid-3" to "третий"),
                owner = "uid-2",
            ),
        )
        messages.send(listOf(message(body = "привет")))

        return MessangerViewModel(
            "группа",
            SessionInteractorImpl(sessions, sessions, sessions),
            MessangerInteractorImpl(conversations, messages, noKeys, photos, voices),
            profiles,
            avatars,
            photos,
            places,
            presence,
            voices,
            recorder,
            player,
        ).also { dispatcher.scheduler.advanceUntilIdle() }
    }

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
        messages.fail(Refused("PERMISSION_DENIED"))
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

    @Test
    fun `снимки показанных реплик догружаются`() = runTest(dispatcher) {
        conversations.sendChat(chat())
        messages.send(listOf(message(id = "m-1", kind = MessageKind.Photo, size = PhotoSize(800, 600))))
        val model = viewModel().also { dispatcher.scheduler.advanceUntilIdle() }

        assertEquals(listOf("m-1" to 1), photos.requested)
        assertEquals(listOf(7.toByte(), 7, 7), model.content().photos.getValue("m-1").toList())
    }

    @Test
    fun `снимок заказывается один раз, а не на каждую реплику`() = runTest(dispatcher) {
        conversations.sendChat(chat())
        messages.send(listOf(message(id = "m-1", kind = MessageKind.Photo, size = PhotoSize(800, 600))))
        viewModel().also { dispatcher.scheduler.advanceUntilIdle() }

        messages.send(
            listOf(
                message(id = "m-1", kind = MessageKind.Photo, size = PhotoSize(800, 600)),
                message(id = "m-2", body = "и текст"),
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("отправленное неизменяемо — второй раз качать нечего", 1, photos.requested.size)
    }

    @Test
    fun `выбранный снимок уходит в диалог`() = runTest(dispatcher) {
        val model = opened()

        model.onPhotoPicked("content://pic")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, photos.attached.size)
        assertEquals("content://pic", photos.attached.single().second)
    }

    @Test
    fun `точка спрашивается у телефона и уходит в диалог`() = runTest(dispatcher) {
        val model = opened()

        model.onLocationPicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, places.requests)
        assertEquals(MessageKind.Location, messages.sent.single().second.kind)
    }

    @Test
    fun `неопределившееся место показывается строкой, а в базу не уходит`() = runTest(dispatcher) {
        val model = viewModelWithoutPlace()

        model.onLocationPicked()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.PLACE_UNKNOWN, model.content().error)
        assertTrue(messages.sent.isEmpty())
    }

    @Test
    fun `без разрешения на место в ленту ничего не уходит`() = runTest(dispatcher) {
        val model = opened()

        model.onLocationDenied()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.PLACE_DENIED, model.content().error)
        assertEquals(0, places.requests)
        assertTrue(messages.sent.isEmpty())
    }

    @Test
    fun `вложение не трогает набранный текст`() = runTest(dispatcher) {
        val model = opened()
        model.onDraftChange("допишу потом")

        model.onPhotoPicked("content://pic")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("подпись к снимку никто не набирал", "допишу потом", model.content().draft)
    }

    @Test
    fun `раскрытый снимок закрывается`() = runTest(dispatcher) {
        conversations.sendChat(chat())
        messages.send(listOf(message(id = "m-1", kind = MessageKind.Photo, size = PhotoSize(800, 600))))
        val model = viewModel().also { dispatcher.scheduler.advanceUntilIdle() }

        model.onPhotoOpened("m-1")
        assertNotNull(model.content().opened)

        model.onPhotoClosed()
        assertNull(model.content().opened)
    }

    @Test
    fun `подпись присутствия приходит в шапку диалога на двоих`() = runTest(dispatcher) {
        val model = opened()
        presence.presence(Presence(System.currentTimeMillis()))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("uid-2"), presence.watched)
        assertEquals(Constants.ONLINE, model.content().presence)
    }

    @Test
    fun `у группы присутствие не спрашивается вовсе`() = runTest(dispatcher) {
        conversations.sendChat(
            chat(
                members = listOf("uid-1", "uid-2", "uid-3"),
                logins = mapOf("uid-1" to "self", "uid-2" to "второй", "uid-3" to "третий"),
            ),
        )
        messages.send(listOf(message(body = "привет")))
        val model = viewModel().also { dispatcher.scheduler.advanceUntilIdle() }

        assertTrue("присутствие одного из нескольких ни о чём не говорит", presence.watched.isEmpty())
        assertNull(model.content().presence)
    }

    @Test
    fun `удержание записывает, отпускание отправляет`() = runTest(dispatcher) {
        val model = opened()

        model.onRecordStart()
        // Только текущая работа: advanceUntilIdle промотал бы весь потолок записи, и она
        // остановилась бы сама, не дождавшись поднятого пальца.
        dispatcher.scheduler.runCurrent()

        assertEquals(120, model.content().recordingLeft)

        model.onRecordFinish()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("отсчёт кончился вместе с записью", model.content().recordingLeft)
        assertEquals(MessageKind.Voice, messages.sent.single().second.kind)
    }

    @Test
    fun `отсчёт идёт вниз`() = runTest(dispatcher) {
        val model = opened()
        model.onRecordStart()

        dispatcher.scheduler.advanceTimeBy(3_100)
        dispatcher.scheduler.runCurrent()

        assertEquals(117, model.content().recordingLeft)
        model.onRecordCancel()
    }

    @Test
    fun `слишком короткое нажатие ничего не отправляет`() = runTest(dispatcher) {
        val model = opened()
        recorder.records(null)

        model.onRecordStart()
        model.onRecordFinish()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue("ткнул в микрофон — это не ошибка и не сообщение", messages.sent.isEmpty())
        assertNull(model.content().error)
    }

    @Test
    fun `уведённый палец бросает запись`() = runTest(dispatcher) {
        val model = opened()

        model.onRecordStart()
        model.onRecordCancel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, recorder.cancels)
        assertTrue(messages.sent.isEmpty())
        assertNull(model.content().recordingLeft)
    }

    @Test
    fun `занятый микрофон показывается строкой`() = runTest(dispatcher) {
        val model = opened()
        recorder.starts = false

        model.onRecordStart()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.VOICE_FAILED, model.content().error)
        assertNull("говорить в пустоту человека оставлять нельзя", model.content().recordingLeft)
    }

    @Test
    fun `нажатие играет, повторное останавливает`() = runTest(dispatcher) {
        val model = opened()

        model.onVoicePressed("m-1", version = 1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("m-1" to 1), voices.requested)
        assertEquals("m-1", model.content().playing)

        model.onVoicePressed("m-1", version = 1)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull("та же кнопка и означает «хватит»", model.content().playing)
    }

    @Test
    fun `ход проигрывания доезжает до экрана`() = runTest(dispatcher) {
        val model = opened()
        model.onVoicePressed("m-1", version = 1)
        dispatcher.scheduler.advanceUntilIdle()

        player.to(0.4f)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0.4f, model.content().progress, 0.001f)
    }

    @Test
    fun `нечитаемое голосовое не притворяется звучащим`() = runTest(dispatcher) {
        val model = MessangerViewModel(
            "uid-1_uid-2",
            SessionInteractorImpl(sessions, sessions, sessions),
            MessangerInteractorImpl(conversations, messages, noKeys, photos, voices),
            profiles,
            avatars,
            photos,
            places,
            presence,
            FakeVoiceInteractor(file = null),
            recorder,
            player,
        ).also {
            conversations.sendChat(chat())
            messages.send(listOf(message(body = "привет")))
            dispatcher.scheduler.advanceUntilIdle()
        }

        model.onVoicePressed("m-1", version = 1)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(model.content().playing)
        assertEquals(Constants.UNREADABLE, model.content().error)
    }

    @Test
    fun `выход предлагается только в группе и только не создателю`() = runTest(dispatcher) {
        val model = opened()

        assertFalse("из диалога на двоих выходить некуда", model.content().canLeave)

        conversations.sendChat(
            chat(
                members = listOf("uid-1", "uid-2", "uid-3"),
                logins = mapOf("uid-1" to "self", "uid-2" to "второй", "uid-3" to "третий"),
                owner = "uid-2",
            ),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(model.content().canLeave)
    }

    @Test
    fun `создателю выход не предлагается`() = runTest(dispatcher) {
        conversations.sendChat(
            chat(
                members = listOf("uid-1", "uid-2", "uid-3"),
                logins = mapOf("uid-1" to "self", "uid-2" to "второй", "uid-3" to "третий"),
                owner = "uid-1",
            ),
        )
        messages.send(listOf(message(body = "привет")))
        val model = viewModel().also { dispatcher.scheduler.advanceUntilIdle() }

        assertFalse(model.content().canLeave)
    }

    @Test
    fun `выход спрашивает подтверждение, а не уходит молча`() = runTest(dispatcher) {
        val model = groupOpened()

        model.onLeaveAsked()

        assertTrue(model.content().askingLeave)
        assertTrue("до подтверждения ничего не записано", conversations.left.isEmpty())
    }

    @Test
    fun `подтверждённый выход вычёркивает и уводит с экрана`() = runTest(dispatcher) {
        val model = groupOpened()

        model.onLeaveAsked()
        model.onLeaveConfirmed()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("uid-2", "uid-3"), conversations.left.getValue("группа"))
        assertTrue(model.content().left)
    }

    @Test
    fun `неудавшийся выход оставляет в группе и показывает причину`() = runTest(dispatcher) {
        val model = groupOpened()
        conversations.leaveFails = Refused("нет связи")

        model.onLeaveAsked()
        model.onLeaveConfirmed()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("нет связи", model.content().error)
        assertFalse("человек остался в группе — экран обязан остаться тоже", model.content().left)
    }

    @Test
    fun `передумавший остаётся`() = runTest(dispatcher) {
        val model = groupOpened()

        model.onLeaveAsked()
        model.onLeaveDismissed()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(model.content().askingLeave)
        assertTrue(conversations.left.isEmpty())
    }
}
