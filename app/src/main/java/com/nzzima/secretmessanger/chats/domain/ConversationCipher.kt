package com.nzzima.secretmessanger.chats.domain

import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.crypto.domain.CryptoBox
import com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys

// Шифрование реплик диалога: ключ из шапки плюс симметричный примитив. Обе операции нужны и
// списку диалогов, и переписке — список открывает превью последней реплики, переписка все
// реплики окна и запечатывает отправляемую.

/**
 * Открытый текст записи [payload], закрытой ключом версии [version].
 *
 * @param version версия берётся у самой записи, а не у диалога: после ротации старые
 *   реплики открываются только старым ключом.
 * @return `null`, если ключа этой версии у нас нет либо запись им не открывается.
 */
fun ConversationKeys.openText(chat: Chat, payload: String, version: Int): String? =
    open(chat.id, chat.selfId, version, chat.convoKeys)
        ?.let { key -> runCatching { CryptoBox.open(payload, key) }.getOrNull() }

/**
 * Запечатывает [text] текущим ключом диалога — им закрывается всё, что отправляется сейчас.
 *
 * @return `null`, если ключа текущей версии у нас нет: отправлять в такой диалог нечего.
 */
fun ConversationKeys.sealText(chat: Chat, text: String): String? =
    open(chat.id, chat.selfId, chat.keyVersion, chat.convoKeys)
        ?.let { key -> CryptoBox.seal(text, key) }

/**
 * Открытые байты вложения [sealed], закрытого ключом версии [version].
 *
 * Версия берётся у самой реплики по той же причине, что у текста: снимок, отправленный до
 * ротации, открывается только прежним ключом. На iOS этот случай был дефектом — `loadVoice`
 * брал текущую версию, и записанное до удаления участника переставало открываться.
 */
fun ConversationKeys.openBytes(chat: Chat, sealed: ByteArray, version: Int): ByteArray? =
    open(chat.id, chat.selfId, version, chat.convoKeys)
        ?.let { key -> runCatching { CryptoBox.open(sealed, key) }.getOrNull() }

/**
 * Запечатывает байты вложения текущим ключом диалога.
 *
 * @return `null`, если ключа текущей версии у нас нет: класть снимок в базу открытым мы не
 *   станем — то же правило, что у текста.
 */
fun ConversationKeys.sealBytes(chat: Chat, raw: ByteArray): ByteArray? =
    open(chat.id, chat.selfId, chat.keyVersion, chat.convoKeys)
        ?.let { key -> CryptoBox.seal(raw, key) }
