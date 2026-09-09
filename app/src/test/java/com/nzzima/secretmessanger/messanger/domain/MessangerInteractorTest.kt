package com.nzzima.secretmessanger.messanger.domain

import com.nzzima.secretmessanger.chats.domain.FakeConversationRepository
import com.nzzima.secretmessanger.chats.domain.chat
import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.ConversationGone
import com.nzzima.secretmessanger.chats.domain.models.Moment
import com.nzzima.secretmessanger.crypto.data.impl.IdentityKeyStoreImpl
import com.nzzima.secretmessanger.crypto.domain.CryptoBox
import com.nzzima.secretmessanger.crypto.domain.FakeMasterKeyProvider
import com.nzzima.secretmessanger.crypto.domain.FakeSharedPreferences
import com.nzzima.secretmessanger.crypto.domain.impl.ConversationKeysImpl
import com.nzzima.secretmessanger.crypto.domain.models.CryptoFailure
import com.nzzima.secretmessanger.messanger.domain.impl.MessangerInteractorImpl
import com.nzzima.secretmessanger.messanger.domain.models.Dialogue
import com.nzzima.secretmessanger.messanger.domain.models.MessageKind
import com.nzzima.secretmessanger.messanger.domain.models.Reply
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Переписка: что показывает лента и что уходит в базу при отправке.
 *
 * Ключи настоящие: реплики распечатываются тем же путём, что и на живом устройстве, —
 * [ConversationKeysImpl] поверх постоянного ключа аккаунта.
 */
class MessangerInteractorTest {

    private val identityKeys = IdentityKeyStoreImpl(FakeSharedPreferences(), FakeMasterKeyProvider())
    private val conversations = FakeConversationRepository()
    private val messages = FakeMessageRepository()
    private val interactor = MessangerInteractorImpl(conversations, messages, ConversationKeysImpl(identityKeys))

    private val identityPrivate = identityKeys.createNew("uid-1")

    /** Диалог, ключ которого выдан аккаунту `uid-1`, и сам этот ключ. */
    private fun sealedChat(
        id: String = "uid-1_uid-2",
        version: Int = 1,
        members: List<String> = listOf("uid-1", "uid-2"),
        logins: Map<String, String> = mapOf("uid-1" to "self", "uid-2" to "companion"),
    ) = CryptoBox.newConversationKey().let { key ->
        val entry = CryptoBox.sealKey(key, CryptoBox.publicKey(identityPrivate), "$id/v$version")
        chat(
            id = id,
            members = members,
            logins = logins,
            convoKeys = mapOf("uid-1_$version" to entry),
            keyVersion = version,
        ) to key
    }

    private suspend fun dialogue(): Dialogue = interactor.observeDialogue("uid-1_uid-2", "uid-1").first().getOrThrow()

    private suspend fun replies(): List<Reply> = dialogue().replies

    private suspend fun failure(): Throwable =
        interactor.observeDialogue("uid-1_uid-2", "uid-1").first().exceptionOrNull()!!

    @Test
    fun `зашифрованная реплика показывается открытым текстом`() = runTest {
        val (chat, key) = sealedChat()
        conversations.sendChat(chat)
        messages.send(listOf(message(body = CryptoBox.seal("завтра в семь", key), encrypted = true)))

        assertEquals("завтра в семь", replies().single().text)
    }

    @Test
    fun `незашифрованная реплика показывается как есть`() = runTest {
        conversations.sendChat(chat())
        messages.send(listOf(message(body = "привет")))

        assertEquals("привет", replies().single().text)
    }

    @Test
    fun `ключ диалога нам не выдан — вместо реплики замок`() = runTest {
        val strangerKey = CryptoBox.newConversationKey()
        conversations.sendChat(chat())
        messages.send(listOf(message(body = CryptoBox.seal("секрет", strangerKey), encrypted = true)))

        assertEquals(Constants.UNREADABLE, replies().single().text)
    }

    @Test
    fun `реплика открывается своей версией ключа, а не текущей версией диалога`() = runTest {
        val id = "uid-1_uid-2"
        val old = CryptoBox.newConversationKey()
        val current = CryptoBox.newConversationKey()
        conversations.sendChat(
            chat(
                id = id,
                convoKeys = mapOf(
                    "uid-1_1" to CryptoBox.sealKey(old, CryptoBox.publicKey(identityPrivate), "$id/v1"),
                    "uid-1_2" to CryptoBox.sealKey(current, CryptoBox.publicKey(identityPrivate), "$id/v2"),
                ),
                keyVersion = 2,
            ),
        )
        messages.send(
            listOf(
                message(id = "m-1", body = CryptoBox.seal("до ротации", old), encrypted = true, version = 1),
                message(id = "m-2", body = CryptoBox.seal("после", current), encrypted = true, version = 2),
            ),
        )

        assertEquals(listOf("до ротации", "после"), replies().map { it.text })
    }

    @Test
    fun `вложения подменяются пометкой вида, а не расшифровываются`() = runTest {
        val (chat, key) = sealedChat()
        conversations.sendChat(chat)
        messages.send(
            listOf(
                message(id = "m-1", body = CryptoBox.seal("что угодно", key), encrypted = true, kind = MessageKind.Voice),
                message(id = "m-2", body = CryptoBox.seal("что угодно", key), encrypted = true, kind = MessageKind.Photo),
                message(id = "m-3", body = CryptoBox.seal("55.7,37.6", key), encrypted = true, kind = MessageKind.Location),
            ),
        )

        assertEquals(
            listOf(Constants.VOICE_MESSAGE, Constants.PHOTO_MESSAGE, Constants.LOCATION_MESSAGE),
            replies().map { it.text },
        )
    }

    @Test
    fun `отметка о смене ключа — служебная строка без автора`() = runTest {
        conversations.sendChat(chat())
        messages.send(listOf(message(body = "", kind = MessageKind.KeyNotice)))

        val reply = replies().single()

        assertEquals(Constants.KEY_ROTATED, reply.text)
        assertTrue("отметка не пузырь", reply.service)
    }

    @Test
    fun `своя реплика отличается от чужой`() = runTest {
        conversations.sendChat(chat())
        messages.send(
            listOf(
                message(id = "m-1", senderId = "uid-1"),
                message(id = "m-2", senderId = "uid-2"),
            ),
        )

        assertEquals(listOf(true, false), replies().map { it.outgoing })
    }

    @Test
    fun `в диалоге на двоих реплики не подписаны`() = runTest {
        conversations.sendChat(chat())
        messages.send(listOf(message(senderId = "uid-2")))

        assertEquals("", replies().single().author)
    }

    @Test
    fun `в группе подписаны только чужие реплики`() = runTest {
        conversations.sendChat(
            chat(
                members = listOf("uid-1", "uid-2", "uid-3"),
                logins = mapOf("uid-1" to "self", "uid-2" to "companion", "uid-3" to "third"),
            ),
        )
        messages.send(
            listOf(
                message(id = "m-1", senderId = "uid-1"),
                message(id = "m-2", senderId = "uid-3"),
            ),
        )

        assertEquals(listOf("", "third"), replies().map { it.author })
    }

    @Test
    fun `отказ по репликам закрывает экран`() = runTest {
        conversations.sendChat(chat())
        messages.fail(IllegalStateException("нет доступа"))

        assertEquals("нет доступа", failure().message)
    }

    @Test
    fun `стёртый диалог приходит отказом`() = runTest {
        conversations.failChat(ConversationGone())
        messages.send(listOf(message()))

        assertTrue("тип отказа обязан дойти до экрана", failure() is ConversationGone)
    }

    @Test
    fun `своя реплика прочитана, когда собеседник дочитал до неё`() = runTest {
        conversations.sendChat(chat(readUpTo = mapOf("uid-2" to Moment(10, 0))))
        messages.send(
            listOf(
                message(id = "m-1", senderId = "uid-1", date = Moment(9, 0)),
                message(id = "m-2", senderId = "uid-1", date = Moment(11, 0)),
            ),
        )

        assertEquals(listOf(true, false), replies().map { it.read })
    }

    @Test
    fun `чужая реплика не помечается прочитанной никогда`() = runTest {
        conversations.sendChat(chat(readUpTo = mapOf("uid-1" to Moment(99, 0), "uid-2" to Moment(99, 0))))
        messages.send(listOf(message(senderId = "uid-2", date = Moment(9, 0))))

        assertFalse(replies().single().read)
    }

    @Test
    fun `отмечаться нужно по последней чужой реплике, а не по последней вообще`() = runTest {
        conversations.sendChat(chat())
        messages.send(
            listOf(
                message(id = "m-1", senderId = "uid-2", date = Moment(9, 0)),
                message(id = "m-2", senderId = "uid-2", date = Moment(10, 250)),
                message(id = "m-3", senderId = "uid-1", date = Moment(11, 0)),
            ),
        )

        assertEquals(Moment(10, 250), dialogue().lastIncoming)
    }

    @Test
    fun `в диалоге из одних своих реплик отмечаться нечем`() = runTest {
        conversations.sendChat(chat())
        messages.send(listOf(message(senderId = "uid-1")))

        assertNull(dialogue().lastIncoming)
    }

    @Test
    fun `отметка уходит в базу той же величиной, с наносекундами`() = runTest {
        val upTo = Moment(1_788_952_430, 123_456_789)

        assertTrue(interactor.markRead(chat(), upTo).isSuccess)

        assertEquals(Triple("uid-1_uid-2", "uid-1", upTo), conversations.receipts.single())
    }

    @Test
    fun `отметка не переписывается, если уже стоит там же или дальше`() = runTest {
        val marked = chat(readUpTo = mapOf("uid-1" to Moment(10, 500)))

        assertTrue(interactor.markRead(marked, Moment(10, 500)).isSuccess)
        assertTrue(interactor.markRead(marked, Moment(9, 0)).isSuccess)

        assertTrue("лишняя запись будит слушателя у собеседника", conversations.receipts.isEmpty())
    }

    @Test
    fun `отметка на наносекунду вперёд всё же пишется`() = runTest {
        val marked = chat(readUpTo = mapOf("uid-1" to Moment(10, 500)))

        interactor.markRead(marked, Moment(10, 501))

        assertEquals(Moment(10, 501), conversations.receipts.single().third)
    }

    @Test
    fun `отправленное закрыто текущим ключом и подписано нами`() = runTest {
        val (chat, key) = sealedChat()

        assertTrue(interactor.send(chat, "завтра в семь").isSuccess)

        val (convoId, sent) = messages.sent.single()

        assertEquals("uid-1_uid-2", convoId)
        assertEquals("uid-1", sent.senderId)
        assertEquals(1, sent.version)
        assertTrue("реплика обязана уйти зашифрованной", sent.encrypted)
        assertSame(MessageKind.Text, sent.kind)
        assertEquals("завтра в семь", CryptoBox.open(sent.body, key))
    }

    @Test
    fun `в диалог без ключей текст уходит открытым`() = runTest {
        val chat: Chat = chat(convoKeys = emptyMap(), keyVersion = 0)

        assertTrue(interactor.send(chat, "привет").isSuccess)

        val sent = messages.sent.single().second

        assertFalse("шифровать нечем — признака шифрования быть не должно", sent.encrypted)
        assertEquals("привет", sent.body)
    }

    @Test
    fun `без своей записи в ключах отправка отказывает и в базу ничего не уходит`() = runTest {
        val stranger = chat(convoKeys = mapOf("uid-2_1" to "чужая запись"), keyVersion = 1)

        val result = interactor.send(stranger, "привет")

        assertSame(CryptoFailure.NoKey, result.exceptionOrNull())
        assertTrue("отправка без ключа не пишет ничего", messages.sent.isEmpty())
    }
}
