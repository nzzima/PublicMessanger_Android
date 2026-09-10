package com.nzzima.secretmessanger.profile.ui

/**
 * Состояние экрана правки своего профиля.
 *
 * Профиль читается один раз, при открытии: форме живые обновления не нужны, а снимок
 * посреди набора текста затирал бы набранное.
 */
sealed interface EditProfileUiState {

    /** Профиль ещё не прочитан. */
    data object Loading : EditProfileUiState

    /**
     * Форма с полями.
     *
     * @property saved поручение экрану вернуться: правка сохранена.
     * @property error причина, по которой сохранить не вышло.
     */
    data class Form(
        val login: String,
        val name: String,
        val someInfo: String,
        val isSaving: Boolean = false,
        val saved: Boolean = false,
        val error: String? = null,
    ) : EditProfileUiState

    /** Профиля нет или чтение отказало. */
    data class Failed(val message: String) : EditProfileUiState
}
