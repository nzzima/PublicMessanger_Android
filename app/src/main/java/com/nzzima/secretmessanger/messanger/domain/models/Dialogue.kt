package com.nzzima.secretmessanger.messanger.domain.models

import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.Moment

/**
 * Всё, что показывает экран переписки: сам диалог и окно его реплик.
 *
 * Шапка и реплики приезжают двумя подписками и складываются здесь: ключ может приехать
 * при открытом экране — например, когда создатель дозапечатывает его тому, кто раньше не
 * публиковал открытый ключ, — и тогда уже показанные реплики расшифровываются заново.
 *
 * @property replies от старых к свежим — в том же порядке, в каком их отдаёт база.
 * @property lastIncoming время последней **чужой** реплики; ею отмечается прочтение. `null` —
 *   чужих реплик в окне нет, отмечаться нечем. Своя реплика сюда не попадает: метить её
 *   незачем, а лишняя запись разбудила бы слушателя шапки у собеседника без всякой пользы.
 */
data class Dialogue(
    val chat: Chat,
    val replies: List<Reply>,
    val lastIncoming: Moment?,
)
