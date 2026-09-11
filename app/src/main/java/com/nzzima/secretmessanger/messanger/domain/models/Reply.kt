package com.nzzima.secretmessanger.messanger.domain.models


/**
 * Реплика в том виде, в каком её показывает лента.
 *
 * @property text открытый текст либо
 *   [com.nzzima.secretmessanger.utils.constants.Constants.UNREADABLE], если ключа у нас
 *   нет. У вложений — пометка вида: показать их Android пока нечем.
 * @property author подпись над пузырём — логин автора; пусто у своих реплик и в диалоге на
 *   двоих, где подписывать нечего.
 * @property authorId автор: по нему берётся аватар. Пустой подписи он не подчиняется —
 *   кружок стоит у каждого пузыря, в том числе у своего.
 * @property authorName имя автора для буквы-заглушки, когда аватара нет. Отдельно от
 *   [author] потому, что подпись бывает пустой, а кружок пустым не бывает.
 * @property outgoing наша ли это реплика: от неё зависит сторона и цвет пузыря.
 * @property date время отправки в миллисекундах эпохи — для показа этого хватает.
 * @property read прочитали ли реплику все, кроме нас. Считается только для своих: чужой
 *   реплике «прочитано» ничего не сообщает — она и так перед глазами.
 * @property photo снимок; `null` — реплика не снимок. Байты приезжают отдельно и позже:
 *   пузырь верстается по размерам, иначе лента прыгала бы на каждой догрузке.
 * @property place точка на карте; `null` — реплика не точка либо координаты не
 *   разобрались (чужой формат или нет ключа).
 * @property service отметка о смене ключа: строка посреди ленты, без пузыря и автора.
 */
data class Reply(
    val id: String,
    val text: String,
    val author: String,
    val authorId: String,
    val authorName: String,
    val outgoing: Boolean,
    val date: Long,
    val read: Boolean,
    val service: Boolean,
    val photo: PhotoAttachment? = null,
    val place: Place? = null,
)
