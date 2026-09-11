package com.nzzima.secretmessanger.photo.domain.api

/**
 * Байты снимков — подколлекция диалога, документ на сообщение.
 *
 * ```
 * conversation/{convoId}/images/{messageId}   senderId: String, data: bytes (запечатанный JPEG)
 * ```
 *
 * Подколлекция, а не само сообщение: лента перечитывает последние пятьдесят реплик при
 * каждом изменении любой из них, и мегабайты ездить туда-сюда не могут.
 *
 * Cloud Storage здесь не участвует, и дело не в тарифе: **правила Storage не читают
 * Firestore**, а «файл видят только участники диалога» без шапки не выразить. Здесь участие
 * проверяет та же шапка тем же `get()`, правилами, которые уже написаны.
 */
interface PhotoRepository {

    /** Запечатанные байты снимка реплики [messageId]; `null` — документа нет. */
    suspend fun sealed(convoId: String, messageId: String): Result<ByteArray?>

    /**
     * Кладёт запечатанные байты [sealed] снимку реплики [messageId].
     *
     * `senderId` сверяется правилом при записи: подделать автора снимка нельзя, как и
     * автора сообщения.
     */
    suspend fun put(convoId: String, messageId: String, senderId: String, sealed: ByteArray): Result<Unit>
}
