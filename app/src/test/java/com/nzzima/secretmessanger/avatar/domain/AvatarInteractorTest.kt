package com.nzzima.secretmessanger.avatar.domain

import com.nzzima.secretmessanger.auth.domain.FakeProfileRepository
import com.nzzima.secretmessanger.avatar.domain.impl.AvatarInteractorImpl
import com.nzzima.secretmessanger.avatar.domain.models.AvatarTooLarge
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Аватары: кэш, порядок записи и маркер в профиле.
 *
 * Журнал общий у репозитория картинок и репозитория профилей: сначала картинка, потом
 * маркер — обратный порядок означал бы маркер, ведущий в пустоту.
 */
class AvatarInteractorTest {

    private val journal = mutableListOf<String>()
    private val avatars = FakeAvatarRepository(journal)
    private val profiles = FakeProfileRepository(journal = journal)
    private val encoder = FakeAvatarEncoder()
    private val interactor = AvatarInteractorImpl(avatars, encoder, profiles)

    @Test
    fun `нулевая версия в базу не ходит вовсе`() = runTest {
        avatars.stored["uid-2"] = byteArrayOf(9)

        assertNull(interactor.avatar("uid-2", version = 0))
        assertEquals("человек без аватара не должен стоить промаха", 0, avatars.reads)
    }

    @Test
    fun `прочитанное берётся из памяти, а не из базы`() = runTest {
        avatars.stored["uid-2"] = byteArrayOf(9)

        val first = interactor.avatar("uid-2", version = 1)
        val second = interactor.avatar("uid-2", version = 1)

        assertArrayEquals(byteArrayOf(9), first)
        assertArrayEquals(byteArrayOf(9), second)
        assertEquals(1, avatars.reads)
    }

    @Test
    fun `новая версия читается заново`() = runTest {
        avatars.stored["uid-2"] = byteArrayOf(9)
        interactor.avatar("uid-2", version = 1)

        avatars.stored["uid-2"] = byteArrayOf(7)
        val updated = interactor.avatar("uid-2", version = 2)

        assertArrayEquals("версия — часть ключа, устареть кэш не может", byteArrayOf(7), updated)
        assertEquals(2, avatars.reads)
    }

    @Test
    fun `сначала картинка, потом маркер в профиле`() = runTest {
        assertTrue(interactor.change("uid-1", "content://pic", currentVersion = 0).isSuccess)

        assertEquals(listOf("picture:1", "marker:1"), journal)
    }

    @Test
    fun `версия растёт на единицу от текущей`() = runTest {
        interactor.change("uid-1", "content://pic", currentVersion = 4)

        assertEquals(listOf(5), profiles.avatarVersions)
    }

    @Test
    fun `своя картинка сразу попадает в кэш`() = runTest {
        interactor.change("uid-1", "content://pic", currentVersion = 0)

        assertArrayEquals(byteArrayOf(1, 2, 3), interactor.avatar("uid-1", version = 1))
        assertEquals("за своей же картинкой в базу ходить незачем", 0, avatars.reads)
    }

    @Test
    fun `картинка не закодировалась — в базу не уходит ничего`() = runTest {
        val interactor = AvatarInteractorImpl(avatars, FakeAvatarEncoder(encoded = null), profiles)

        val result = interactor.change("uid-1", "content://pic", currentVersion = 0)

        assertTrue(result.exceptionOrNull() is AvatarTooLarge)
        assertTrue(journal.isEmpty())
    }

    @Test
    fun `картинка не записалась — маркер не ставится`() = runTest {
        avatars.putFails = IllegalStateException("нет связи")

        val result = interactor.change("uid-1", "content://pic", currentVersion = 0)

        assertEquals("нет связи", result.exceptionOrNull()?.message)
        assertTrue("маркер вёл бы в пустоту", profiles.avatarVersions.isEmpty())
    }

    @Test
    fun `удаление идёт тем же порядком - сперва картинка`() = runTest {
        assertTrue(interactor.remove("uid-1").isSuccess)

        assertEquals(listOf("delete", "marker:0"), journal)
    }

    @Test
    fun `картинка не удалилась — маркер остаётся прежним`() = runTest {
        avatars.deleteFails = IllegalStateException("нет связи")

        val result = interactor.remove("uid-1")

        assertEquals("нет связи", result.exceptionOrNull()?.message)
        assertTrue(profiles.avatarVersions.isEmpty())
    }
}
