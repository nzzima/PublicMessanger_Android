package com.nzzima.secretmessanger.messanger.domain.models

/**
 * Реплика в том виде, в каком её показывает лента.
 *
 * @property text открытый текст либо
 *   [com.nzzima.secretmessanger.utils.constants.Constants.UNREADABLE], если ключа у нас
 *   нет. У вложений — пометка вида: показать их Android пока нечем.
 * @property author логин автора; пусто у своих реплик и в диалоге на двоих, где
 *   подписывать нечего.
 * @property outgoing наша ли это реплика: от неё зависит сторона и цвет пузыря.
 * @property date время отправки в миллисекундах эпохи — для показа этого хватает.
 * @property read прочитали ли реплику все, кроме нас. Считается только для своих: чужой
 *   реплике «прочитано» ничего не сообщает — она и так перед глазами.
 * @property service отметка о смене ключа: строка посреди ленты, без пузыря и автора.
 */
data class Reply(
    val id: String,
    val text: String,
    val author: String,
    val outgoing: Boolean,
    val date: Long,
    val read: Boolean,
    val service: Boolean,
)
