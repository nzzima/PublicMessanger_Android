package com.nzzima.secretmessanger.voice.domain.api

/**
 * Байты голосовых — подколлекция диалога, документ на сообщение.
 *
 * ```
 * conversation/{convoId}/audio/{messageId}   senderId: String, data: bytes (запечатанный AAC)
 * ```
 *
 * Устроено ровно как у снимков и по той же причине: лента перечитывает последние пятьдесят
 * реплик при каждом изменении любой из них, и звук ездить там не может.
 */
interface VoiceRepository {

    /** Запечатанные байты голосового реплики [messageId]; `null` — документа нет. */
    suspend fun sealed(convoId: String, messageId: String): Result<ByteArray?>

    /** Кладёт запечатанные байты [sealed]; `senderId` сверяется правилом при записи. */
    suspend fun put(convoId: String, messageId: String, senderId: String, sealed: ByteArray): Result<Unit>
}
