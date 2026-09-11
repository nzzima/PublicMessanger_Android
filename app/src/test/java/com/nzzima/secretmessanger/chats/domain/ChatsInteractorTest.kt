package com.nzzima.secretmessanger.chats.domain

import com.nzzima.secretmessanger.chats.domain.impl.ChatsInteractorImpl
import com.nzzima.secretmessanger.chats.domain.models.Conversation
import com.nzzima.secretmessanger.crypto.data.impl.IdentityKeyStoreImpl
import com.nzzima.secretmessanger.crypto.domain.CryptoBox
import com.nzzima.secretmessanger.crypto.domain.FakeMasterKeyProvider
import com.nzzima.secretmessanger.crypto.domain.FakeSharedPreferences
import com.nzzima.secretmessanger.crypto.domain.impl.ConversationKeysImpl
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Список диалогов: что показывается, в каком порядке и что не показывается вовсе.
 *
 * Ключи настоящие: превью распечатывается тем же путём, что и на живом устройстве, —
 * [ConversationKeysImpl] поверх постоянного ключа аккаунта.
 */
class ChatsInteractorTest {

    private val identityKeys = IdentityKeyStoreImpl(FakeSharedPreferences(), FakeMasterKeyProvider())
    private val conversations = FakeConversationRepository()
    private val interactor = ChatsInteractorImpl(conversations, ConversationKeysImpl(identityKeys))

    private val identityPrivate = identityKeys.createNew("uid-1")

    /** Диалог, ключ которого выдан аккаунту `uid-1`, и сам этот ключ. */
    private fun sealedChat(id: String = "uid-1_uid-2", version: Int = 1) =
        CryptoBox.newConversationKey().let { key ->
            val entry = CryptoBox.sealKey(key, CryptoBox.publicKey(identityPrivate), "$id/v$version")
            chat(id = id, convoKeys = mapOf("uid-1_$version" to entry), keyVersion = version) to key
        }

    private suspend fun conversations(): List<Conversation> =
        interactor.observeConversations("uid-1").first().getOrThrow()

    @Test
    fun `зашифрованная реплика показывается открытым текстом`() = runTest {
        val (chat, key) = sealedChat()
        conversations.send(
            listOf(header(chat = chat, lastMessage = CryptoBox.seal("завтра в семь", key), encrypted = true)),
        )

        assertEquals("завтра в семь", conversations().single().preview)
    }

    @Test
    fun `незашифрованная реплика показывается как есть`() = runTest {
        conversations.send(listOf(header(lastMessage = "привет")))

        assertEquals("привет", conversations().single().preview)
    }

    @Test
    fun `ключ диалога нам не выдан — вместо превью замок, строка остаётся`() = runTest {
        val strangerKey = CryptoBox.newConversationKey()
        conversations.send(
            listOf(header(lastMessage = CryptoBox.seal("секрет", strangerKey), encrypted = true)),
        )

        val conversation = conversations().single()

        assertEquals(Constants.UNREADABLE, conversation.preview)
        assertEquals("диалог обязан остаться в списке", "uid-1_uid-2", conversation.chat.id)
    }

    @Test
    fun `реплика закрыта другим ключом — вместо превью замок`() = runTest {
        val (chat, _) = sealedChat()
        val otherKey = CryptoBox.newConversationKey()
        conversations.send(
            listOf(header(chat = chat, lastMessage = CryptoBox.seal("секрет", otherKey), encrypted = true)),
        )

        assertEquals(Constants.UNREADABLE, conversations().single().preview)
    }

    @Test
    fun `реплика не разбирается — вместо превью замок`() = runTest {
        val (chat, _) = sealedChat()
        conversations.send(listOf(header(chat = chat, lastMessage = "не base64 вовсе", encrypted = true)))

        assertEquals(Constants.UNREADABLE, conversations().single().preview)
    }

    @Test
    fun `реплика открывается своей версией ключа, а не текущей версией диалога`() = runTest {
        val id = "uid-1_uid-2"
        val old = CryptoBox.newConversationKey()
        val current = CryptoBox.newConversationKey()
        val entries = mapOf(
            "uid-1_1" to CryptoBox.sealKey(old, CryptoBox.publicKey(identityPrivate), "$id/v1"),
            "uid-1_2" to CryptoBox.sealKey(current, CryptoBox.publicKey(identityPrivate), "$id/v2"),
        )
        conversations.send(
            listOf(
                header(
                    chat = chat(id = id, convoKeys = entries, keyVersion = 2),
                    lastMessage = CryptoBox.seal("до ротации", old),
                    encrypted = true,
                    version = 1,
                ),
            ),
        )

        assertEquals("до ротации", conversations().single().preview)
    }

    @Test
    fun `диалог без единого сообщения остаётся в списке с подписью`() = runTest {
        conversations.send(
            listOf(
                header(chat = chat(id = "пустой"), lastMessage = ""),
                header(chat = chat(id = "живой"), lastMessage = "привет"),
            ),
        )

        val list = conversations()

        // До 11.09.2026 пустой отбрасывался, и заведённая группа пропадала насовсем: другого
        // входа в неё нет, а в диалог на двоих можно вернуться из профиля собеседника.
        assertEquals(setOf("пустой", "живой"), list.map { it.chat.id }.toSet())
        assertEquals(Constants.MESSAGES_EMPTY, list.first { it.chat.id == "пустой" }.preview)
    }

    @Test
    fun `свежие сверху`() = runTest {
        conversations.send(
            listOf(
                header(chat = chat(id = "средний"), date = 200),
                header(chat = chat(id = "старый"), date = 100),
                header(chat = chat(id = "свежий"), date = 300),
            ),
        )

        assertEquals(listOf("свежий", "средний", "старый"), conversations().map { it.chat.id })
    }

    @Test
    fun `отказ подписки доходит до вызывающего`() = runTest {
        val error = IllegalStateException("нет доступа")
        conversations.fail(error)

        val result = interactor.observeConversations("uid-1").first()

        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `подписка запрашивается на переданный аккаунт`() = runTest {
        conversations.send(emptyList())

        conversations()

        assertEquals("uid-1", conversations.requestedFor)
    }
}
