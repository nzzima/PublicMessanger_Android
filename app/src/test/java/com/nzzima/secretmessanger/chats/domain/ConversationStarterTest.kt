package com.nzzima.secretmessanger.chats.domain

import com.nzzima.secretmessanger.chats.domain.impl.ConversationStarterImpl
import com.nzzima.secretmessanger.chats.domain.models.CompanionKeyMissing
import com.nzzima.secretmessanger.contacts.domain.models.Contact
import com.nzzima.secretmessanger.crypto.data.impl.IdentityKeyStoreImpl
import com.nzzima.secretmessanger.crypto.domain.CryptoBox
import com.nzzima.secretmessanger.crypto.domain.FakeMasterKeyProvider
import com.nzzima.secretmessanger.crypto.domain.FakePublicKeyRepository
import com.nzzima.secretmessanger.crypto.domain.FakeSharedPreferences
import com.nzzima.secretmessanger.crypto.domain.impl.ConversationKeysImpl
import com.nzzima.secretmessanger.crypto.domain.models.CryptoFailure
import com.nzzima.secretmessanger.profile.domain.FakeProfileReader
import com.nzzima.secretmessanger.profile.domain.models.Profile
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Заведение диалога из «Контактов».
 *
 * Ключи настоящие: раздача проверяется тем же путём, что и на живом устройстве, — сам ключ
 * диалога распечатывается обеими сторонами, а не сверяется по длине строки.
 */
class ConversationStarterTest {

    private val identityKeys = IdentityKeyStoreImpl(FakeSharedPreferences(), FakeMasterKeyProvider())
    private val companionKeys = IdentityKeyStoreImpl(FakeSharedPreferences(), FakeMasterKeyProvider())
    private val conversations = FakeConversationRepository()
    private val publicKeys = FakePublicKeyRepository()
    private val profiles = FakeProfileReader()
    private val starter = ConversationStarterImpl(
        conversations,
        ConversationKeysImpl(identityKeys),
        publicKeys,
        identityKeys,
        profiles,
    )

    private val contact = Contact(id = "uid-2", login = "companion")

    private lateinit var companionPrivate: ByteArray

    private fun encode(publicKey: ByteArray) = Base64.getEncoder().encodeToString(publicKey)

    @Before
    fun setUp() {
        identityKeys.createNew("uid-1")
        companionPrivate = companionKeys.createNew("uid-2")
        publicKeys.stored["uid-2"] = encode(CryptoBox.publicKey(companionPrivate))
        profiles.send(Profile(id = "uid-1", login = "self", name = "", someInfo = ""))
    }

    @Test
    fun `новый диалог заводится с обоими участниками, именами и создателем`() = runTest {
        val convoId = starter.start("uid-1", contact).getOrThrow()

        assertEquals("uid-1_uid-2", convoId)

        val created = conversations.created.single()

        assertEquals(listOf("uid-1", "uid-2"), created.members)
        assertEquals(mapOf("uid-1" to "self", "uid-2" to "companion"), created.logins)
        assertEquals("создатель — тот, кто завёл", "uid-1", created.owner)
        assertEquals(1, created.keyVersion)
    }

    @Test
    fun `ключ нового диалога открывается обеими сторонами`() = runTest {
        starter.start("uid-1", contact).getOrThrow()

        val created = conversations.created.single()
        val mine = ConversationKeysImpl(identityKeys)
            .open(created.id, "uid-1", version = 1, entries = created.convoKeys)
        val theirs = ConversationKeysImpl(companionKeys)
            .open(created.id, "uid-2", version = 1, entries = created.convoKeys)

        assertTrue("свою запись обязаны открыть", mine != null)
        assertTrue("запись собеседника обязана открыться его ключом", theirs != null)
        assertTrue("ключ диалога у обоих один", mine.contentEquals(theirs))
    }

    @Test
    fun `существующий диалог не заводится заново`() = runTest {
        conversations.stored["uid-1_uid-2"] = chat()

        val convoId = starter.start("uid-1", contact).getOrThrow()

        assertEquals("uid-1_uid-2", convoId)
        assertTrue("существующую шапку трогать нечем", conversations.created.isEmpty())
    }

    @Test
    fun `у собеседника нет опубликованной половины — диалог не заводится`() = runTest {
        publicKeys.stored.remove("uid-2")

        val result = starter.start("uid-1", contact)

        assertTrue(result.exceptionOrNull() is CompanionKeyMissing)
        assertTrue("в базу не должно уйти ничего", conversations.created.isEmpty())
    }

    @Test
    fun `негодная половина собеседника — тот же отказ`() = runTest {
        publicKeys.stored["uid-2"] = "это не base64"

        val result = starter.start("uid-1", contact)

        assertTrue(result.exceptionOrNull() is CompanionKeyMissing)
        assertTrue(conversations.created.isEmpty())
    }

    @Test
    fun `своего ключа на устройстве нет — отказ до всякой записи`() = runTest {
        identityKeys.forget("uid-1")

        val result = starter.start("uid-1", contact)

        assertSame(CryptoFailure.NoKey, result.exceptionOrNull())
        assertTrue(conversations.created.isEmpty())
    }

    @Test
    fun `проигранная гонка за диалог не отказ`() = runTest {
        conversations.createFails = IllegalStateException("PERMISSION_DENIED")
        // Собеседник завёл тот же диалог, пока мы запечатывали ключ.
        conversations.stored["uid-1_uid-2"] = chat()

        assertEquals("uid-1_uid-2", starter.start("uid-1", contact).getOrThrow())
    }

    @Test
    fun `отказ записи без появившейся шапки доходит отказом`() = runTest {
        conversations.createFails = IllegalStateException("нет связи")

        assertEquals("нет связи", starter.start("uid-1", contact).exceptionOrNull()?.message)
    }

    @Test
    fun `отказ чтения шапки доходит отказом и ничего не заводит`() = runTest {
        conversations.readFails = IllegalStateException("нет связи")

        val result = starter.start("uid-1", contact)

        assertEquals("нет связи", result.exceptionOrNull()?.message)
        assertTrue(conversations.created.isEmpty())
    }

    @Test
    fun `профиль без логина не выдумывается — отказ доходит наверх`() = runTest {
        profiles.fail(IllegalStateException("профиля нет"))

        val result = starter.start("uid-1", contact)

        assertEquals("профиля нет", result.exceptionOrNull()?.message)
        assertTrue(conversations.created.isEmpty())
    }
}
