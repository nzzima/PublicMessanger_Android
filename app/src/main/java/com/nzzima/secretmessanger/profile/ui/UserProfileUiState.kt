package com.nzzima.secretmessanger.profile.ui

import com.nzzima.secretmessanger.profile.domain.models.Profile

/**
 * Состояние экрана чужого профиля.
 *
 * Имя известно во всех состояниях, включая ожидание: оно приходит из списка контактов
 * вместе с переходом, и экран не должен открываться безымянным. В шапке его нет — там
 * только возврат и «Написать»: имя человека уже написано в теле экрана, и второй раз его
 * показывать незачем.
 */
sealed interface UserProfileUiState {

    val name: String

    /** Профиль ещё не пришёл: показывается ожидание, а не пустые поля. */
    data class Loading(override val name: String) : UserProfileUiState

    /**
     * Профиль прочитан.
     *
     * @property isOpening идёт заведение диалога; на это время нажатия не принимаются.
     * @property opened диалог, который надо открыть. Разовое поручение экрану: тот
     *   переходит и снимает его [UserProfileViewModel.onOpened].
     * @property error причина, по которой диалог не завёлся.
     */
    data class Content(
        override val name: String,
        val profile: Profile,
        val avatar: ByteArray? = null,
        val presence: String? = null,
        val isOpening: Boolean = false,
        val opened: String? = null,
        val error: String? = null,
    ) : UserProfileUiState

    /** Профиля нет или чтение отказало. [message] показывается на экране. */
    data class Failed(override val name: String, val message: String) : UserProfileUiState
}
