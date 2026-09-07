package com.nzzima.secretmessanger.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzzima.secretmessanger.profile.domain.api.ProfileInteractor
import com.nzzima.secretmessanger.session.domain.api.SessionInteractor
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Состояние экрана профиля и выход из аккаунта.
 *
 * Выход живёт здесь, а не в шапке «Чатов», где стоял до появления вкладок.
 */
class ProfileViewModel(
    private val sessionInteractor: SessionInteractor,
    private val profileInteractor: ProfileInteractor,
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

    /** Завершает сессию. Локальные данные аккаунта не затрагивает. */
    fun signOut() = sessionInteractor.signOut()

    private fun subscribe() {
        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return

        subscription?.cancel()
        profileScreenState.value = ProfileUiState.Loading

        subscription = viewModelScope.launch {
            profileInteractor.observeProfile(uid).collect { snapshot ->
                snapshot
                    .onSuccess { profileScreenState.value = ProfileUiState.Content(it) }
                    .onFailure {
                        profileScreenState.value = ProfileUiState.Failed(it.message ?: Constants.SERVER_SILENT)
                        // Отказ Firestore не отличает мёртвую сессию от обрыва связи, а
                        // «Повторить» лечит только второе. Проверка разводит эти два случая:
                        // мёртвая сессия уводит с вкладок целиком.
                        sessionInteractor.revalidate()
                    }
            }
        }
    }
}
