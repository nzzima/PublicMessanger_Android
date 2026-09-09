package com.nzzima.secretmessanger.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzzima.secretmessanger.chats.domain.api.ConversationStarter
import com.nzzima.secretmessanger.profile.domain.api.ProfileInteractor
import com.nzzima.secretmessanger.session.domain.api.SessionInteractor
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Состояние экрана чужого профиля и кнопка «Написать».
 *
 * Профиль слушается, а не читается однократно: собеседник поменяет имя или заметку — экран
 * обновится без перезахода.
 *
 * @param companionId чей профиль показываем.
 * @param fallbackLogin имя из списка контактов; держит экран, пока профиль не пришёл.
 */
class UserProfileViewModel(
    private val companionId: String,
    private val fallbackLogin: String,
    private val sessionInteractor: SessionInteractor,
    private val profileInteractor: ProfileInteractor,
    private val conversationStarter: ConversationStarter,
) : ViewModel() {

    private val userProfileScreenState =
        MutableStateFlow<UserProfileUiState>(UserProfileUiState.Loading(fallbackLogin))

    private var subscription: Job? = null

    /** Текущее состояние экрана. */
    fun observeUserProfileScreenState(): StateFlow<UserProfileUiState> = userProfileScreenState.asStateFlow()

    init {
        subscribe()
    }

    /** Подписывается на профиль заново — нужна после отказа. */
    fun retry() = subscribe()

    /**
     * Открывает переписку с этим человеком, заводя диалог, если его ещё нет.
     *
     * Имя в шапку диалога уходит **из профиля**, а не из списка контактов: список мог
     * устареть, а переименование доедет сюда подпиской.
     *
     * Ограничено [Constants.SUBMIT_TIMEOUT_MS]: за кнопкой стоит чтение открытого ключа
     * собеседника и запись шапки, и молчащая сеть подвесила бы экран навсегда.
     */
    fun onWrite() {
        val state = userProfileScreenState.value as? UserProfileUiState.Content ?: return
        if (state.isOpening) return

        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return

        userProfileScreenState.update { current ->
            if (current is UserProfileUiState.Content) current.copy(isOpening = true, error = null) else current
        }

        viewModelScope.launch {
            val result = withTimeoutOrNull(Constants.SUBMIT_TIMEOUT_MS) {
                conversationStarter.start(uid, companionId, state.profile.login)
            }

            userProfileScreenState.update { current ->
                if (current !is UserProfileUiState.Content) return@update current

                when {
                    result == null -> current.copy(isOpening = false, error = Constants.SERVER_SILENT)
                    result.isSuccess -> current.copy(isOpening = false, opened = result.getOrNull())
                    else -> current.copy(
                        isOpening = false,
                        error = result.exceptionOrNull()?.message ?: Constants.SERVER_SILENT,
                    )
                }
            }
        }
    }

    /** Снимает поручение открыть диалог — экран уже перешёл. */
    fun onOpened() = userProfileScreenState.update { current ->
        if (current is UserProfileUiState.Content) current.copy(opened = null) else current
    }

    private fun subscribe() {
        subscription?.cancel()
        userProfileScreenState.value = UserProfileUiState.Loading(fallbackLogin)

        subscription = viewModelScope.launch {
            profileInteractor.observeProfile(companionId).collect { snapshot ->
                snapshot
                    .onSuccess { profile ->
                        userProfileScreenState.update { current ->
                            // Имя ведёт профиль, как только тот пришёл: список контактов
                            // мог устареть на переименование.
                            val name = profile.login.ifEmpty { fallbackLogin }

                            if (current is UserProfileUiState.Content) {
                                // Начатое заведение и показанный отказ переживают новый
                                // снимок: собеседник мог поправить заметку в этот самый миг.
                                current.copy(name = name, profile = profile)
                            } else {
                                UserProfileUiState.Content(name, profile)
                            }
                        }
                    }
                    .onFailure { error ->
                        userProfileScreenState.value = UserProfileUiState.Failed(
                            name = fallbackLogin,
                            message = error.message ?: Constants.PROFILE_MISSING,
                        )
                        // Отказ Firestore не отличает мёртвую сессию от обрыва связи, а
                        // «Повторить» лечит только второе.
                        sessionInteractor.revalidate()
                    }
            }
        }
    }
}
