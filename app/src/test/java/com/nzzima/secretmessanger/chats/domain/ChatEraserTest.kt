package com.nzzima.secretmessanger.chats.domain

import com.nzzima.secretmessanger.chats.domain.impl.ChatEraserImpl
import com.nzzima.secretmessanger.chats.domain.models.CannotErase
import com.nzzima.secretmessanger.crypto.domain.FakeConversationKeys
import com.nzzima.secretmessanger.messanger.domain.FakeMessageRepository
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Удаление переписки: кому положено, в каком порядке и что остаётся после обрыва.
 *
 * Порядок здесь не украшение: правила на подколлекции читают состав из шапки через `get()`,
 * и стёртая первой шапка заперла бы вложения в базе навсегда.
 */
class ChatEraserTest {

    private val conversations = FakeConversationRepository()
    private val messages = FakeMessageRepository()
    private val keys = FakeConversationKeys()
    private val eraser = ChatEraserImpl(conversations, messages, keys)

    @Test
    fun `диалог на двоих стирает любой из двоих, а не только заводивший`() = runTest {
        val chat = chat(selfId = "uid-2", owner = "uid-1")

        val result = eraser.erase(chat)

        assertTrue(result.isSuccess)
        assertEquals(listOf(chat.id), conversations.erased)
    }

    @Test
    fun `группу стирает только создатель`() = runTest {
        val chat = chat(
            id = "группа",
            selfId = "uid-2",
            members = listOf("uid-1", "uid-2", "uid-3"),
            owner = "uid-1",
        )
        messages.stock[chat.id] = 5

        val result = eraser.erase(chat)

        assertTrue(result.exceptionOrNull() is CannotErase)
        assertTrue("чужая группа обязана остаться целой", conversations.erased.isEmpty())
        assertTrue("до реплик дело дойти не должно", messages.pages.isEmpty())
    }

    @Test
    fun `свою группу создатель стирает`() = runTest {
        val chat = chat(id = "группа", members = listOf("uid-1", "uid-2", "uid-3"))

        assertTrue(eraser.erase(chat).isSuccess)
        assertEquals(listOf("группа"), conversations.erased)
    }

    @Test
    fun `группа, ужавшаяся до двоих, стирается любым из оставшихся`() = runTest {
        // «Группа» здесь всегда выводилась из состава, а не из отдельного поля, — значит
        // ушедшие до двоих превращают её в обычный диалог. Так же считает правило в базе.
        val chat = chat(id = "бывшая-группа", selfId = "uid-2", members = listOf("uid-1", "uid-2"), owner = "uid-1")

        assertTrue(eraser.erase(chat).isSuccess)
        assertEquals(listOf("бывшая-группа"), conversations.erased)
    }

    @Test
    fun `реплики стираются страницами, пока не опустеет`() = runTest {
        val chat = chat()
        messages.stock[chat.id] = 450

        eraser.erase(chat)

        val page = Constants.ERASE_PAGE.toInt()
        assertEquals(listOf(page, page, 50, 0), messages.pages)
    }

    @Test
    fun `шапка стирается последней — к этому моменту реплик не осталось`() = runTest {
        val chat = chat()
        messages.stock[chat.id] = 300

        var leftWhenHeaderErased: Int? = null
        conversations.onErase = { leftWhenHeaderErased = messages.stock[chat.id] }

        eraser.erase(chat)

        assertEquals(listOf(chat.id), conversations.erased)
        assertEquals("шапка ушла, пока реплики ещё лежали", 0, leftWhenHeaderErased)
    }

    @Test
    fun `оборванное стирание реплик шапку не трогает`() = runTest {
        val chat = chat()
        messages.eraseFails = IllegalStateException("связь пропала")

        val result = eraser.erase(chat)

        assertEquals("связь пропала", result.exceptionOrNull()?.message)
        // Шапка на месте — диалог остался в списке, и повтор доделает начатое. Стёртая
        // первой, она оставила бы вложения недостижимыми навсегда.
        assertTrue(conversations.erased.isEmpty())
    }

    @Test
    fun `отказ шапки оставляет ключи в памяти`() = runTest {
        val chat = chat()
        conversations.eraseFails = IllegalStateException("нет прав")

        val result = eraser.erase(chat)

        assertEquals("нет прав", result.exceptionOrNull()?.message)
        assertTrue("диалог никуда не делся — забывать его рано", keys.forgotten.isEmpty())
    }

    @Test
    fun `ключи стёртого диалога забываются`() = runTest {
        val chat = chat()

        eraser.erase(chat)

        // Идентификатор диалога на двоих детерминированный: заведённый заново, он получит тот
        // же id и новый ключ, а кэш отдал бы на него старый.
        assertEquals(listOf(chat.id), keys.forgotten)
    }

    @Test
    fun `пустая переписка стирается одной шапкой`() = runTest {
        val chat = chat()

        assertTrue(eraser.erase(chat).isSuccess)
        assertEquals(listOf(0), messages.pages)
        assertEquals(listOf(chat.id), conversations.erased)
    }
}
