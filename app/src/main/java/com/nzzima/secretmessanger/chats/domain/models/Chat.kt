package com.nzzima.secretmessanger.chats.domain.models

/**
 * Переписка: с кем она и чем шифруется.
 *
 * Ни названия, ни признака группы в базе нет — и то и другое выводится из состава.
 *
 * @property id идентификатор шапки `conversation/{id}`.
 * @property members участники; [selfId] входит в них всегда.
 * @property logins кэш имён участников из шапки: uid → логин.
 * @property owner создатель, единственный, кто меняет состав. Пусто у диалогов,
 *   заведённых до появления поля: состав в них не меняет никто.
 * @property selfId владелец текущей сессии.
 * @property convoKeys ключ диалога, запечатанный каждому участнику; см.
 *   [com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys].
 * @property keyVersion текущая версия ключа диалога.
 */
data class Chat(
    val id: String,
    val members: List<String>,
    val logins: Map<String, String>,
    val owner: String,
    val selfId: String,
    val convoKeys: Map<String, String>,
    val keyVersion: Int,
) {

    /** Группа — больше двух участников. */
    val isGroup: Boolean get() = members.size > 2

    /**
     * Шифруется ли переписка.
     *
     * Пустая карта ключей означает диалог, начатый до появления шифрования: его история
     * лежит в базе открытой, и новые реплики уходят туда же открытыми — задним числом
     * их не зашифровать. Так же читает iOS.
     */
    val isEncrypted: Boolean get() = convoKeys.isNotEmpty()

    /**
     * Название — логины всех, кроме себя.
     *
     * Участник, чьего логина в кэше шапки нет, из названия выпадает.
     */
    val title: String get() = members.filter { it != selfId }.mapNotNull(logins::get).joinToString(", ")

    /**
     * Собеседник в диалоге на двоих; `null` у группы.
     *
     * `null` здесь означает не «нет собеседника», а «одним его не назвать»: участников
     * несколько, и выбирать из них одного было бы враньём.
     */
    val companionId: String? get() = if (isGroup) null else members.firstOrNull { it != selfId }
}
