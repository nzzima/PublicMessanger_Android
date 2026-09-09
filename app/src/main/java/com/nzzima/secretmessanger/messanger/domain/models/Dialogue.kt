package com.nzzima.secretmessanger.messanger.domain.models

import com.nzzima.secretmessanger.chats.domain.models.Chat

/**
 * Всё, что показывает экран переписки: сам диалог и окно его реплик.
 *
 * Шапка и реплики приезжают двумя подписками и складываются здесь: ключ может приехать
 * при открытом экране — например, когда создатель дозапечатывает его тому, кто раньше не
 * публиковал открытый ключ, — и тогда уже показанные реплики расшифровываются заново.
 *
 * @property replies от старых к свежим — в том же порядке, в каком их отдаёт база.
 */
data class Dialogue(
    val chat: Chat,
    val replies: List<Reply>,
)
