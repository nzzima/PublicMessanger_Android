package com.nzzima.secretmessanger.profile.ui

import com.nzzima.secretmessanger.profile.domain.models.Profile

/**
 * Состояние экрана чужого профиля.
 *
 * Заголовок есть у всех состояний, включая ожидание: имя приходит из списка контактов
 * вместе с переходом, и экран не должен открываться безымянным.
 */
sealed interface UserProfileUiState {

    val title: String

    /** Профиль ещё не пришёл: показывается ожидание, а не пустые поля. */
    data class Loading(override val title: String) : UserProfileUiState

    /**
     * Профиль прочитан.
     *
     * @property isOpening идёт заведение диалога; на это время нажатия не принимаются.
     * @property opened диалог, который надо открыть. Разовое поручение экрану: тот
     *   переходит и снимает его [UserProfileViewModel.onOpened].
     * @property error причина, по которой диалог не завёлся.
     */
    data class Content(
        override val title: String,
        val profile: Profile,
        val isOpening: Boolean = false,
        val opened: String? = null,
        val error: String? = null,
    ) : UserProfileUiState

    /** Профиля нет или чтение отказало. [message] показывается на экране. */
    data class Failed(override val title: String, val message: String) : UserProfileUiState
}
