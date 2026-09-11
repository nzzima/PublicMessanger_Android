package com.nzzima.secretmessanger.chats.ui

import com.nzzima.secretmessanger.chats.domain.api.ConversationStarter
import com.nzzima.secretmessanger.contacts.domain.FakeContactsRepository
import com.nzzima.secretmessanger.contacts.domain.impl.ContactsInteractorImpl
import com.nzzima.secretmessanger.contacts.domain.models.Contact
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Выбор участников: главное здесь — **группа начинается с двух**.
 *
 * С одним отмеченным это диалог на двоих, и заводить его надо из «Контактов»: там он попадёт в
 * существующий, а здесь завёл бы второй с тем же человеком.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewGroupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sessions = FakeSessionRepository(Session.Authenticated("uid-1"))
    private val contacts = FakeContactsRepository()
    private val starter = FakeGroupStarter()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = NewGroupViewModel(
        SessionInteractorImpl(sessions, sessions, sessions),
        ContactsInteractorImpl(contacts),
        starter,
    )

    private fun NewGroupViewModel.content() = observeNewGroupScreenState().value as NewGroupUiState.Content

    private fun opened(): NewGroupViewModel {
        contacts.send(listOf(Contact("uid-2", "второй"), Contact("uid-3", "третий"), Contact("uid-4", "четвёртый")))

        return viewModel().also { dispatcher.scheduler.advanceUntilIdle() }
    }

    @Test
    fun `с одним отмеченным создавать нельзя`() = runTest(dispatcher) {
        val model = opened()

        model.onToggle("uid-2")

        assertFalse("с одним это обычный диалог", model.content().canCreate)
    }

    @Test
    fun `с двумя отмеченными можно`() = runTest(dispatcher) {
        val model = opened()

        model.onToggle("uid-2")
        model.onToggle("uid-3")

        assertTrue(model.content().canCreate)
    }

    @Test
    fun `повторное нажатие снимает отметку`() = runTest(dispatcher) {
        val model = opened()

        model.onToggle("uid-2")
        model.onToggle("uid-2")

        assertTrue(model.content().chosen.isEmpty())
    }

    @Test
    fun `заводится группа ровно из отмеченных`() = runTest(dispatcher) {
        val model = opened()
        model.onToggle("uid-2")
        model.onToggle("uid-4")

        model.onCreate()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf("uid-2" to "второй", "uid-4" to "четвёртый"), starter.groups.single())
        assertEquals("группа-1", model.content().created)
    }

    @Test
    fun `отказ показывается строкой, а группа не открывается`() = runTest(dispatcher) {
        val model = opened()
        starter.refusal = IllegalStateException("нет связи")
        model.onToggle("uid-2")
        model.onToggle("uid-3")

        model.onCreate()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("нет связи", model.content().error)
        assertNull(model.content().created)
    }

    @Test
    fun `отметки переживают новый снимок контактов`() = runTest(dispatcher) {
        val model = opened()
        model.onToggle("uid-2")

        contacts.send(listOf(Contact("uid-2", "переименовался"), Contact("uid-3", "третий")))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("uid-2"), model.content().chosen)
    }

    @Test
    fun `открытую группу второй раз не открываем`() = runTest(dispatcher) {
        val model = opened()
        model.onToggle("uid-2")
        model.onToggle("uid-3")
        model.onCreate()
        dispatcher.scheduler.advanceUntilIdle()

        model.onOpened()

        assertNull(model.content().created)
    }

    @Test
    fun `без контактов группу собирать не из кого`() = runTest(dispatcher) {
        contacts.send(emptyList())
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(NewGroupUiState.Empty, model.observeNewGroupScreenState().value)
    }

    @Test
    fun `отказ подписки показывается своим текстом`() = runTest(dispatcher) {
        contacts.fail(IllegalStateException("нет доступа"))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "нет доступа",
            (model.observeNewGroupScreenState().value as NewGroupUiState.Failed).message,
        )
    }
}

/** Заведение групп в памяти. */
private class FakeGroupStarter : ConversationStarter {

    /** Составы заведённых групп, в порядке вызова. */
    val groups = mutableListOf<Map<String, String>>()

    /** Чем отказывает заведение; `null` — проходит. */
    var refusal: Throwable? = null

    override suspend fun start(selfId: String, companionId: String, companionLogin: String): Result<String> =
        Result.success(listOf(selfId, companionId).sorted().joinToString("_"))

    override suspend fun startGroup(selfId: String, members: Map<String, String>): Result<String> {
        refusal?.let { return Result.failure(it) }

        groups += members
        return Result.success("группа-${groups.size}")
    }
}
