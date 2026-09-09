package com.nzzima.secretmessanger.contacts.ui

import com.nzzima.secretmessanger.chats.domain.api.ConversationStarter
import com.nzzima.secretmessanger.contacts.domain.FakeContactsRepository
import com.nzzima.secretmessanger.contacts.domain.impl.ContactsInteractorImpl
import com.nzzima.secretmessanger.contacts.domain.models.Contact
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

/** Открытие переписки из контактов: поручение экрану, отказ и защита от второго нажатия. */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sessions = FakeSessionRepository(Session.Authenticated("uid-1"))
    private val contacts = FakeContactsRepository()
    private val starter = FakeConversationStarter()

    private val contact = Contact(id = "uid-2", login = "companion")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ContactsViewModel(
        SessionInteractorImpl(sessions, sessions, sessions),
        ContactsInteractorImpl(contacts),
        starter,
    )

    private fun ContactsViewModel.content() = observeContactsScreenState().value as ContactsUiState.Content

    /** Экран со списком из одного контакта. */
    private fun opened(): ContactsViewModel {
        contacts.send(listOf(contact))

        return viewModel().also { dispatcher.scheduler.advanceUntilIdle() }
    }

    @Test
    fun `нажатие поручает экрану открыть диалог`() = runTest(dispatcher) {
        val model = opened()

        model.onContactTap(contact)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("uid-1_uid-2", model.content().opened)
        assertFalse(model.content().isOpening)
        assertEquals(listOf("uid-1" to "uid-2"), starter.calls)
    }

    @Test
    fun `поручение снимается после перехода`() = runTest(dispatcher) {
        val model = opened()
        model.onContactTap(contact)
        dispatcher.scheduler.advanceUntilIdle()

        model.onOpened()

        assertNull(model.content().opened)
    }

    @Test
    fun `второе нажатие во время заведения не принимается`() = runTest(dispatcher) {
        val model = opened()
        val pending = starter.hang()

        model.onContactTap(contact)
        // Только текущие задачи: advanceUntilIdle прокрутил бы виртуальное время за
        // таймаут отправки, и незавершённое заведение успело бы им оборваться.
        dispatcher.scheduler.runCurrent()
        model.onContactTap(contact)
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

        model.onContactTap(contact)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.SERVER_SILENT, model.content().error)
        assertFalse(model.content().isOpening)
        assertNull(model.content().opened)
    }

    @Test
    fun `отказ показывается строкой и никуда не ведёт`() = runTest(dispatcher) {
        val model = opened()
        starter.refusal = IllegalStateException(Constants.COMPANION_KEY_MISSING)

        model.onContactTap(contact)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.COMPANION_KEY_MISSING, model.content().error)
        assertNull(model.content().opened)
        assertFalse(model.content().isOpening)
    }

    @Test
    fun `новый снимок списка не отменяет начатого заведения`() = runTest(dispatcher) {
        val model = opened()
        val pending = starter.hang()

        model.onContactTap(contact)
        dispatcher.scheduler.runCurrent()
        contacts.send(listOf(contact, Contact(id = "uid-3", login = "third")))
        dispatcher.scheduler.runCurrent()

        assertTrue("заведение обязано пережить снимок", model.content().isOpening)
        assertEquals(2, model.content().contacts.size)

        pending.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `без сессии нажатие ничего не делает`() = runTest(dispatcher) {
        val model = opened()
        sessions.signOut()

        model.onContactTap(contact)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(starter.calls.isEmpty())
    }
}

/** Заведение диалога в памяти: помнит вызовы и умеет зависнуть. */
private class FakeConversationStarter : ConversationStarter {

    /** Пары «кто открыл — с кем», в порядке вызова. */
    val calls = mutableListOf<Pair<String, String>>()

    /** Чем отказывает заведение; `null` — проходит. */
    var refusal: Throwable? = null

    /** Незавершённое заведение: пока не выполнено, вызов не возвращается. */
    private var pending: CompletableDeferred<Unit>? = null

    /** Подвешивает следующее заведение; отпускается выполнением возвращённого. */
    fun hang(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { pending = it }

    override suspend fun start(selfId: String, contact: Contact): Result<String> {
        calls += selfId to contact.id
        pending?.await()

        return refusal?.let { Result.failure(it) }
            ?: Result.success(listOf(selfId, contact.id).sorted().joinToString("_"))
    }
}
