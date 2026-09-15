package com.nzzima.secretmessanger.chats.ui

import com.nzzima.secretmessanger.Refused
import com.nzzima.secretmessanger.chats.domain.FakeConversationRepository
import com.nzzima.secretmessanger.chats.domain.api.GroupEditor
import com.nzzima.secretmessanger.chats.domain.chat
import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.contacts.domain.FakeContactsRepository
import com.nzzima.secretmessanger.contacts.domain.impl.ContactsInteractorImpl
import com.nzzima.secretmessanger.contacts.domain.models.Contact
import com.nzzima.secretmessanger.session.domain.FakeSessionRepository
import com.nzzima.secretmessanger.session.domain.impl.SessionInteractorImpl
import com.nzzima.secretmessanger.session.domain.models.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Экран участников: что показано и кому позволено править. */
@OptIn(ExperimentalCoroutinesApi::class)
class MembersViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sessions = FakeSessionRepository(Session.Authenticated("uid-1"))
    private val conversations = FakeConversationRepository()
    private val contacts = FakeContactsRepository()
    private val editor = FakeGroupEditor(conversations)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MembersViewModel(
        "группа",
        SessionInteractorImpl(sessions, sessions, sessions),
        editor,
        ContactsInteractorImpl(contacts),
    )

    private fun MembersViewModel.content() = observeMembersScreenState().value as MembersUiState.Content

    private fun group(owner: String = "uid-1") = chat(
        id = "группа",
        members = listOf("uid-2", "uid-1", "uid-3"),
        logins = mapOf("uid-1" to "self", "uid-2" to "второй", "uid-3" to "третий"),
        owner = owner,
    )

    private fun opened(owner: String = "uid-1"): MembersViewModel {
        conversations.sendChat(group(owner))
        contacts.send(listOf(Contact("uid-2", "второй"), Contact("uid-3", "третий"), Contact("uid-9", "новичок")))

        return viewModel().also { dispatcher.scheduler.advanceUntilIdle() }
    }

    @Test
    fun `создатель стоит первым, остальные по алфавиту`() = runTest(dispatcher) {
        val model = opened()

        assertEquals(listOf("self", "второй", "третий"), model.content().members.map { it.login })
        assertTrue(model.content().members.first().isOwner)
    }

    @Test
    fun `себя и создателя видно по пометке`() = runTest(dispatcher) {
        val model = opened(owner = "uid-2")

        val members = model.content().members.associateBy { it.login }

        assertTrue(members.getValue("self").isSelf)
        assertTrue(members.getValue("второй").isOwner)
    }

    @Test
    fun `править состав может только создатель`() = runTest(dispatcher) {
        assertTrue(opened(owner = "uid-1").content().canManage)
        assertFalse(opened(owner = "uid-2").content().canManage)
    }

    @Test
    fun `в кандидаты не попадают те, кто уже в группе`() = runTest(dispatcher) {
        val model = opened()

        assertEquals(listOf("новичок"), model.content().candidates.map { it.login })
    }

    @Test
    fun `добавляются ровно отмеченные`() = runTest(dispatcher) {
        val model = opened()

        model.onAddAsked()
        model.onToggle("uid-9")
        model.onAddConfirmed()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf("uid-9" to "новичок"), editor.added.single())
        assertFalse("список добавления закрывается сам", model.content().adding)
    }

    @Test
    fun `убирается тот, кого выбрали`() = runTest(dispatcher) {
        val model = opened()

        model.onRemove("uid-3")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("uid-3"), editor.removed)
    }

    @Test
    fun `отказ показывается строкой, а состав остаётся прежним`() = runTest(dispatcher) {
        val model = opened()
        editor.refusal = Refused("нет доступа")

        model.onRemove("uid-3")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("нет доступа", model.content().error)
        assertEquals(3, model.content().members.size)
    }

    @Test
    fun `передумавший добавлять закрывает список, никого не добавив`() = runTest(dispatcher) {
        val model = opened()

        model.onAddAsked()
        model.onToggle("uid-9")
        model.onAddDismissed()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(model.content().adding)
        assertTrue(model.content().chosen.isEmpty())
        assertTrue(editor.added.isEmpty())
    }
}

/** Правка состава в памяти; шапку отдаёт настоящий фейк репозитория. */
private class FakeGroupEditor(private val conversations: FakeConversationRepository) : GroupEditor {

    /** Составы, которые просили добавить. */
    val added = mutableListOf<Map<String, String>>()

    /** Кого просили убрать. */
    val removed = mutableListOf<String>()

    /** Чем отказывает правка; `null` — проходит. */
    var refusal: Throwable? = null

    override fun observe(convoId: String, selfId: String): Flow<Result<Chat>> =
        conversations.observeChat(convoId, selfId)

    override suspend fun add(chat: Chat, members: Map<String, String>): Result<Unit> {
        refusal?.let { return Result.failure(it) }

        added += members
        return Result.success(Unit)
    }

    override suspend fun remove(chat: Chat, uid: String): Result<Unit> {
        refusal?.let { return Result.failure(it) }

        removed += uid
        return Result.success(Unit)
    }
}
