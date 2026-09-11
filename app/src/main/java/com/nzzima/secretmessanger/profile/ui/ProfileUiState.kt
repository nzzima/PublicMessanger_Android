package com.nzzima.secretmessanger.profile.ui

import com.nzzima.secretmessanger.profile.domain.models.Profile

/** Состояние экрана профиля. */
sealed interface ProfileUiState {

    /** Профиль ещё не пришёл: экран показывает ожидание, а не пустые поля. */
    data object Loading : ProfileUiState

    /** Профиль прочитан. [avatar] — байты аватара; `null` — его нет или он ещё грузится. */
    data class Content(val profile: Profile, val avatar: ByteArray? = null) : ProfileUiState

    /** Документа нет или чтение отказало. [message] показывается на экране. */
    data class Failed(val message: String) : ProfileUiState
}
