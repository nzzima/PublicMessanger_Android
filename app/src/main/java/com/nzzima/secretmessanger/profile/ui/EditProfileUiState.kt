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
     * @property avatarVersion текущая версия аватара: следующая пишется на единицу больше.
     * @property avatar байты аватара; `null` — его нет.
     * @property isAvatarChanging идёт запись картинки; на это время кнопки не принимают нажатий.
     * @property saved поручение экрану вернуться: правка сохранена.
     * @property error причина, по которой сохранить не вышло.
     */
    data class Form(
        val login: String,
        val name: String,
        val someInfo: String,
        val avatarVersion: Int = 0,
        val avatar: ByteArray? = null,
        val isAvatarChanging: Boolean = false,
        val isSaving: Boolean = false,
        val saved: Boolean = false,
        val error: String? = null,
    ) : EditProfileUiState

    /** Профиля нет или чтение отказало. */
    data class Failed(val message: String) : EditProfileUiState
}
