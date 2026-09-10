package com.nzzima.secretmessanger.profile.domain

import com.nzzima.secretmessanger.auth.domain.FakeLoginRepository
import com.nzzima.secretmessanger.auth.domain.FakeProfileRepository
import com.nzzima.secretmessanger.auth.domain.models.LoginTaken
import com.nzzima.secretmessanger.chats.domain.FakeConversationRepository
import com.nzzima.secretmessanger.profile.domain.impl.ProfileEditorImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правка профиля, и прежде всего — **порядок шагов при переименовании**.
 *
 * Журнал общий на три фейка: занять новое имя, переписать профиль, отпустить старое. Порядок
 * здесь не деталь реализации, а единственное, что не даёт остаться без логина вовсе, поэтому
 * проверяется он, а не только итог.
 */
class ProfileEditorTest {

    private val journal = mutableListOf<String>()
    private val logins = FakeLoginRepository(
        taken = mutableMapOf("blue" to "uid-1"),
        journal = journal,
    )
    private val profiles = FakeProfileRepository(journal = journal)
    private val conversations = FakeConversationRepository(journal = journal)
    private val editor = ProfileEditorImpl(profiles, logins, conversations)

    private suspend fun save(login: String = "red", currentLogin: String = "blue") =
        editor.save(uid = "uid-1", login = login, name = "Никита", someInfo = "заметка", currentLogin = currentLogin)

    @Test
    fun `сначала захват, потом профиль, и только потом отпускание старого`() = runTest {
        assertTrue(save().isSuccess)

        assertEquals(listOf("claim:red", "profile:red", "release:blue", "rename:red"), journal)
    }

    @Test
    fun `после переименования реестр держит новое имя, а старое свободно`() = runTest {
        save()

        assertEquals("uid-1", logins.owner("red"))
        assertNull(logins.owner("blue"))
    }

    @Test
    fun `поля профиля записываются целиком`() = runTest {
        save()

        val updated = requireNotNull(profiles.updated)

        assertEquals("uid-1", updated.uid)
        assertEquals("red", updated.login)
        assertEquals("Никита", updated.name)
        assertEquals("заметка", updated.someInfo)
    }

    @Test
    fun `имя не менялось — реестр не трогается и рассылки нет`() = runTest {
        assertTrue(save(login = "blue", currentLogin = "blue").isSuccess)

        assertTrue("захватывать нечего", logins.claimed.isEmpty())
        assertTrue("отпускать нечего — это тот же захват", logins.released.isEmpty())
        assertTrue("собеседникам сообщать нечего", conversations.renames.isEmpty())
        assertEquals("blue", requireNotNull(profiles.updated).login)
    }

    @Test
    fun `сменился только регистр — захват тот же, но собеседники узнают`() = runTest {
        assertTrue(save(login = "Blue", currentLogin = "blue").isSuccess)

        assertTrue("ключ реестра нижним регистром — запись та же", logins.claimed.isEmpty())
        assertTrue("свой же захват удалять нельзя", logins.released.isEmpty())
        assertEquals(listOf("uid-1" to "Blue"), conversations.renames)
    }

    @Test
    fun `занятое чужим имя отбивается до всякой записи`() = runTest {
        val logins = FakeLoginRepository(taken = mutableMapOf("red" to "uid-2", "blue" to "uid-1"))
        val profiles = FakeProfileRepository()
        val conversations = FakeConversationRepository()

        val result = ProfileEditorImpl(profiles, logins, conversations)
            .save("uid-1", "red", "Никита", "заметка", "blue")

        assertTrue(result.exceptionOrNull() is LoginTaken)
        assertNull("профиль не должен измениться ни в одном поле", profiles.updated)
        assertTrue(logins.released.isEmpty())
        assertEquals("uid-1", logins.owner("blue"))
    }

    @Test
    fun `имя уже наше — повторная попытка после обрыва доводит дело до конца`() = runTest {
        val logins = FakeLoginRepository(
            taken = mutableMapOf("red" to "uid-1", "blue" to "uid-1"),
            journal = journal,
        )

        val result = ProfileEditorImpl(profiles, logins, conversations)
            .save("uid-1", "red", "Никита", "заметка", "blue")

        assertTrue(result.isSuccess)
        assertTrue("занимать заново нечего", logins.claimed.isEmpty())
        assertEquals(listOf("blue"), logins.released)
        assertEquals("red", requireNotNull(profiles.updated).login)
    }

    @Test
    fun `профиль не записался — старое имя остаётся за нами`() = runTest {
        val logins = FakeLoginRepository(taken = mutableMapOf("blue" to "uid-1"))
        val profiles = FakeProfileRepository(updateFails = IllegalStateException("нет связи"))

        val result = ProfileEditorImpl(profiles, logins, conversations)
            .save("uid-1", "red", "Никита", "заметка", "blue")

        assertEquals("нет связи", result.exceptionOrNull()?.message)
        assertTrue("отпускать старое на полпути нельзя", logins.released.isEmpty())
        assertEquals("uid-1", logins.owner("blue"))
    }

    @Test
    fun `старое имя не отпустилось — переименование всё равно состоялось`() = runTest {
        val logins = FakeLoginRepository(
            taken = mutableMapOf("blue" to "uid-1"),
            releaseFails = IllegalStateException("нет связи"),
        )

        val result = ProfileEditorImpl(profiles, logins, conversations)
            .save("uid-1", "red", "Никита", "заметка", "blue")

        assertTrue("держать человека на экране незачем", result.isSuccess)
        assertEquals("red", requireNotNull(profiles.updated).login)
    }

    @Test
    fun `рассылка имени не удалась — правка всё равно состоялась`() = runTest {
        conversations.renameFails = IllegalStateException("нет связи")

        assertTrue(save().isSuccess)
        assertEquals("red", requireNotNull(profiles.updated).login)
    }
}
