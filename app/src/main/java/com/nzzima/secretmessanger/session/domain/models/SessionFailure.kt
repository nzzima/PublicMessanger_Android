package com.nzzima.secretmessanger.session.domain.models

import com.nzzima.secretmessanger.utils.constants.Constants

/** Отказы сессии, различаемые вызывающим. Текст показывается пользователю. */
sealed class SessionFailure(message: String) : Exception(message) {

    /**
     * Сессия на устройстве есть, но сервис её больше не признаёт: сменили пароль, отключили
     * или удалили аккаунт, отозвали токен обновления.
     *
     * Отличается от отказа связи тем, что не проходит никогда: помогает только новый вход.
     */
    data object Expired : SessionFailure(Constants.SESSION_EXPIRED)
}
