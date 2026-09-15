package com.nzzima.secretmessanger.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzzima.secretmessanger.avatar.domain.api.AvatarInteractor
import com.nzzima.secretmessanger.profile.domain.api.ProfileInteractor
import com.nzzima.secretmessanger.session.domain.api.SessionInteractor
import com.nzzima.secretmessanger.utils.errors.ErrorText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние экрана своего профиля.
 *
 * Выход из аккаунта отсюда уехал в правку профиля — туда же, где он живёт на iOS. Здесь он
 * стоял временно, пока экрана правки не было.
 */
class ProfileViewModel(
    private val sessionInteractor: SessionInteractor,
    private val profileInteractor: ProfileInteractor,
    private val avatarInteractor: AvatarInteractor,
) : ViewModel() {

    private val profileScreenState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    private var subscription: Job? = null

    /** Текущее состояние экрана. */
    fun observeProfileScreenState(): StateFlow<ProfileUiState> = profileScreenState.asStateFlow()

    init {
        subscribe()
    }

    /** Читает профиль заново — нужна после отказа. */
    fun retry() = subscribe()

    /** Догружает аватар к прочитанному профилю; сменившийся приедет с новой версией. */
    private fun loadAvatar(uid: String, version: Int) {
        viewModelScope.launch {
            val image = avatarInteractor.avatar(uid, version)

            profileScreenState.update { current ->
                if (current is ProfileUiState.Content) current.copy(avatar = image) else current
            }
        }
    }

    private fun subscribe() {
        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return

        subscription?.cancel()
        profileScreenState.value = ProfileUiState.Loading

        subscription = viewModelScope.launch {
            profileInteractor.observeProfile(uid).collect { snapshot ->
                snapshot
                    .onSuccess { profile ->
                        profileScreenState.value = ProfileUiState.Content(profile)
                        loadAvatar(profile.id, profile.avatarVersion)
                    }
                    .onFailure {
                        profileScreenState.value = ProfileUiState.Failed(ErrorText.of(it))
                        // Отказ Firestore не отличает мёртвую сессию от обрыва связи, а
                        // «Повторить» лечит только второе. Проверка разводит эти два случая:
                        // мёртвая сессия уводит с вкладок целиком.
                        sessionInteractor.revalidate()
                    }
            }
        }
    }
}
