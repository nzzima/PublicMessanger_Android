package com.nzzima.secretmessanger.chats.domain

import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.Moment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Название, признак группы и собеседник выводятся из состава, а не из полей базы. */
class ChatTest {

    @Test
    fun `название диалога на двоих — логин собеседника`() {
        assertEquals("companion", chat().title)
    }

    @Test
    fun `название группы — логины всех, кроме себя`() {
        val group = chat(
            members = listOf("uid-1", "uid-2", "uid-3"),
            logins = mapOf("uid-1" to "self", "uid-2" to "first", "uid-3" to "second"),
        )

        assertEquals("first, second", group.title)
        assertTrue(group.isGroup)
    }

    @Test
    fun `участник без логина в кэше выпадает из названия`() {
        val group = chat(
            members = listOf("uid-1", "uid-2", "uid-3"),
            logins = mapOf("uid-1" to "self", "uid-2" to "first"),
        )

        assertEquals("first", group.title)
    }

    @Test
    fun `у диалога на двоих есть собеседник, у группы — нет`() {
        assertEquals("uid-2", chat().companionId)
        assertFalse(chat().isGroup)

        assertNull(chat(members = listOf("uid-1", "uid-2", "uid-3")).companionId)
    }

    @Test
    fun `идентификатор диалога на двоих не зависит от того, кто его заводит`() {
        assertEquals(
            Chat.conversationId("uid-2", "uid-1"),
            Chat.conversationId("uid-1", "uid-2"),
        )
    }

    @Test
    fun `идентификатор — пара uid по алфавиту через подчёркивание`() {
        assertEquals("uid-1_uid-2", Chat.conversationId("uid-2", "uid-1"))
    }

    @Test
    fun `прочитано, когда дочитали все, кроме автора`() {
        val group = chat(
            members = listOf("uid-1", "uid-2", "uid-3"),
            readUpTo = mapOf("uid-2" to Moment(10, 0), "uid-3" to Moment(10, 0)),
        )

        assertTrue(group.isRead(Moment(9, 0), author = "uid-1"))
    }

    @Test
    fun `один не дочитал — не прочитано`() {
        val group = chat(
            members = listOf("uid-1", "uid-2", "uid-3"),
            readUpTo = mapOf("uid-2" to Moment(10, 0), "uid-3" to Moment(8, 0)),
        )

        assertFalse(group.isRead(Moment(9, 0), author = "uid-1"))
    }

    @Test
    fun `своя отметка на прочтение своей же реплики не влияет`() {
        val own = chat(readUpTo = mapOf("uid-1" to Moment(10, 0)))

        assertFalse("подтвердить обязан собеседник, а не автор", own.isRead(Moment(9, 0), author = "uid-1"))
    }

    @Test
    fun `отметка ровно на реплике считается прочтением`() {
        val read = chat(readUpTo = mapOf("uid-2" to Moment(9, 500)))

        assertTrue(read.isRead(Moment(9, 500), author = "uid-1"))
        assertFalse("наносекунда назад — уже не дочитал", read.isRead(Moment(9, 501), author = "uid-1"))
    }

    @Test
    fun `диалог без отметок не прочитан`() {
        assertFalse(chat().isRead(Moment(9, 0), author = "uid-1"))
    }

    @Test
    fun `диалог без ключей считается незашифрованным`() {
        assertFalse(chat(convoKeys = emptyMap()).isEncrypted)
        assertTrue(chat(convoKeys = mapOf("uid-1_1" to "запись")).isEncrypted)
    }
}
