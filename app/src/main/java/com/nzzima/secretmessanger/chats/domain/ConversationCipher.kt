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
