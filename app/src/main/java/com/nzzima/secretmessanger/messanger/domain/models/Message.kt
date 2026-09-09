package com.nzzima.secretmessanger.messanger.domain.models

/**
 * Реплика так, как она лежит в `conversation/{id}/messages/{messageId}`.
 *
 * Расшифровкой занимается вызывающий: ключ диалога знает интерактор, а модель к
 * хранилищу ключей не ходит.
 *
 * @property senderId автор; сверяется правилом при записи, подделать его нельзя.
 * @property body поле `message`: шифротекст либо открытый текст, смотря по [encrypted].
 *   У [MessageKind.KeyNotice] пусто — содержимого у отметки нет вовсе.
 * @property encrypted поле `enc`. Реплики, написанные до появления шифрования, его не
 *   имеют и читаются как есть.
 * @property version версия ключа, которой закрыт [body]; поле `v`.
 * @property date время отправки в миллисекундах эпохи.
 * @property kind вид реплики; выводится из поля `type`.
 */
data class Message(
    val id: String,
    val senderId: String,
    val body: String,
    val encrypted: Boolean,
    val version: Int,
    val date: Long,
    val kind: MessageKind,
)
