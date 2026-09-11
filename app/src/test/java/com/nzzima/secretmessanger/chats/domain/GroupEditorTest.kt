package com.nzzima.secretmessanger.chats.domain

import com.nzzima.secretmessanger.chats.domain.impl.GroupEditorImpl
import com.nzzima.secretmessanger.chats.domain.models.CompanionKeyMissing
import com.nzzima.secretmessanger.chats.domain.models.NotAGroup
import com.nzzima.secretmessanger.chats.domain.models.NotTheOwner
import com.nzzima.secretmessanger.crypto.data.impl.IdentityKeyStoreImpl
import com.nzzima.secretmessanger.crypto.domain.CryptoBox
import com.nzzima.secretmessanger.crypto.domain.FakeMasterKeyProvider
import com.nzzima.secretmessanger.crypto.domain.FakePublicKeyRepository
import com.nzzima.secretmessanger.crypto.domain.FakeSharedPreferences
import com.nzzima.secretmessanger.crypto.domain.impl.ConversationKeysImpl
import com.nzzima.secretmessanger.messanger.domain.FakeMessageRepository
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Правка состава группы.
 *
 * Ключи настоящие: здесь проверяется не карта в шапке, а то, что добавленный сможет
 * распечатать историю, а удалённый — не сможет прочитать будущее.
 */
class GroupEditorTest {

    private val identityKeys = IdentityKeyStoreImpl(FakeSharedPreferences(), FakeMasterKeyProvider())
    private val conversations = FakeConversationRepository()
    private val publicKeys = FakePublicKeyRepository()
    private val messages = FakeMessageRepository()
    private val crypto = ConversationKeysImpl(identityKeys)
    private val editor = GroupEditorImpl(conversations, crypto, publicKeys, identityKeys, messages)

    private lateinit var identityPrivate: ByteArray

    private fun encode(key: ByteArray) = Base64.getEncoder().encodeToString(key)

    /** Чужая пара ключей: открытая половина ложится в реестр, закрытая остаётся тесту. */
    private fun stranger(uid: String): ByteArray {
        val keys = IdentityKeyStoreImpl(FakeSharedPreferences(), FakeMasterKeyProvider())
        val private = keys.createNew(uid)

        publicKeys.stored[uid] = encode(CryptoBox.publicKey(private))

        return private
    }

    @Before
    fun setUp() {
        identityPrivate = identityKeys.createNew("uid-1")
    }

    /** Группа из троих с ключами нужных версий, запечатанными нам. */
    private fun group(versions: Int = 1, owner: String = "uid-1"): com.nzzima.secretmessanger.chats.domain.models.Chat {
        val id = "группа"
        val entries = (1..versions).associate { version ->
            val key = CryptoBox.newConversationKey()

            "uid-1_$version" to CryptoBox.sealKey(key, CryptoBox.publicKey(identityPrivate), "$id/v$version")
        }

        return chat(
            id = id,
            members = listOf("uid-1", "uid-2", "uid-3"),
            logins = mapOf("uid-1" to "self", "uid-2" to "второй", "uid-3" to "третий"),
            convoKeys = entries,
            keyVersion = versions,
            owner = owner,
        )
    }

    @Test
    fun `добавленный получает все версии ключа, а не только текущую`() = runTest {
        stranger("uid-9")

        assertTrue(editor.add(group(versions = 3), mapOf("uid-9" to "новичок")).isSuccess)

        val (members, keys, version) = conversations.updated.single()
        assertEquals(listOf("uid-1", "uid-2", "uid-3", "uid-9"), members)
        assertEquals(
            "без старых ключей новичок увидел бы стену нерасшифрованного",
            setOf("uid-9_1", "uid-9_2", "uid-9_3"),
            keys.keys,
        )
        assertEquals("добавление ключ не ротирует", null, version)
    }

    @Test
    fun `добавляемый без опубликованного ключа отменяет добавление целиком`() = runTest {
        val result = editor.add(group(), mapOf("uid-9" to "безключа"))

        assertTrue(result.exceptionOrNull() is CompanionKeyMissing)
        assertTrue("дозапечатывания в Android нет", conversations.updated.isEmpty())
    }

    @Test
    fun `удаление перевыпускает ключ оставшимся и только им`() = runTest {
        stranger("uid-2")
        stranger("uid-3")

        assertTrue(editor.remove(group(versions = 2), "uid-3").isSuccess)

        val (members, keys, version) = conversations.updated.single()
        assertEquals(listOf("uid-1", "uid-2"), members)
        assertEquals("удалённый ключом новой версии не наделяется", setOf("uid-1_3", "uid-2_3"), keys.keys)
        assertEquals(3, version)
    }

    @Test
    fun `после удаления в ленте появляется отметка о смене ключа`() = runTest {
        stranger("uid-2")
        stranger("uid-3")

        editor.remove(group(), "uid-3")

        assertEquals(listOf("группа" to "uid-1"), messages.notes)
    }

    @Test
    fun `неудавшаяся правка состава не оставляет отметки в ленте`() = runTest {
        stranger("uid-2")
        stranger("uid-3")
        conversations.updateFails = IllegalStateException("нет доступа")

        val result = editor.remove(group(), "uid-3")

        assertEquals("нет доступа", result.exceptionOrNull()?.message)
        assertTrue("отметка соврала бы о том, чего не было", messages.notes.isEmpty())
    }

    @Test
    fun `не создатель состав не правит`() = runTest {
        stranger("uid-9")
        val foreign = group(owner = "uid-2")

        assertTrue(editor.add(foreign, mapOf("uid-9" to "новичок")).exceptionOrNull() is NotTheOwner)
        assertTrue(editor.remove(foreign, "uid-3").exceptionOrNull() is NotTheOwner)
        assertTrue(conversations.updated.isEmpty())
    }

    @Test
    fun `себя из состава не убирают — для этого есть выход`() = runTest {
        assertTrue(editor.remove(group(), "uid-1").exceptionOrNull() is NotTheOwner)
        assertTrue(conversations.updated.isEmpty())
    }

    @Test
    fun `состав диалога на двоих не правится вовсе`() = runTest {
        stranger("uid-9")

        assertTrue(editor.add(chat(), mapOf("uid-9" to "новичок")).exceptionOrNull() is NotAGroup)
    }
}
