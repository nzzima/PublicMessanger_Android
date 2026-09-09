package com.nzzima.secretmessanger.crypto.domain.models

import com.nzzima.secretmessanger.utils.constants.Constants

/** Отказы крипто-слоя. Текст показывается пользователю. */
sealed class CryptoFailure(message: String) : Exception(message) {

    /** Запись не разбирается: не тот формат, не base64, обрезанные байты. */
    data object MalformedPayload : CryptoFailure(Constants.MALFORMED_PAYLOAD)

    /** Ключ не подошёл: тег AES-GCM не сошёлся либо контекст HKDF другой. */
    data object WrongKey : CryptoFailure(Constants.WRONG_KEY)

    /**
     * Ключа диалога на этом устройстве нет: запись в `convoKeys` нам не выдана либо
     * запечатана для другой пары ключей.
     *
     * Отличается от [WrongKey] тем, что шифровать нечем **до** всякой операции, а не
     * получилось расшифровать: отправлять в такой диалог нечего.
     */
    data object NoKey : CryptoFailure(Constants.NO_CONVERSATION_KEY)
}
