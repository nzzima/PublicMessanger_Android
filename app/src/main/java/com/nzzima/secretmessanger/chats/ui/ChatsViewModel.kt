package com.nzzima.secretmessanger.chats.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzzima.secretmessanger.chats.domain.api.ChatsInteractor
import com.nzzima.secretmessanger.session.domain.api.SessionInteractor
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Состояние экрана списка диалогов.
 *
 * Идентификатор аккаунта берётся из сессии в момент подписки: вкладка достижима только из
 * [com.nzzima.secretmessanger.main.ui.RootState.Ready], то есть при живой сессии. Смену
 * аккаунта модель не отслеживает — выход уводит с вкладок целиком.
 *
 * Отказ подписки проверяется на мёртвую сессию: с ней «Повторить» не сработает никогда, и
 * решение принимает оболочка, а не эта вкладка.
 */
class ChatsViewModel(
    private val sessionInteractor: SessionInteractor,
    private val chatsInteractor: ChatsInteractor,
) : ViewModel() {

    private val chatsScreenState = MutableStateFlow<ChatsUiState>(ChatsUiState.Loading)
    private var subscription: Job? = null

    /** Текущее состояние экрана. */
    fun observeChatsScreenState(): StateFlow<ChatsUiState> = chatsScreenState.asStateFlow()

    init {
        subscribe()
    }

    /**
     * Подписывается на список заново.
     *
     * Нужна после отказа: слушатель Firestore на ошибке снимается, и продолжать слушать
     * прежней подпиской нечего.
     */
    fun retry() = subscribe()

    private fun subscribe() {
        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return

        subscription?.cancel()
        chatsScreenState.value = ChatsUiState.Loading

        subscription = viewModelScope.launch {
            chatsInteractor.observeConversations(uid).collect { snapshot ->
                snapshot
                    .onSuccess {
                        chatsScreenState.value =
                            if (it.isEmpty()) ChatsUiState.Empty else ChatsUiState.Content(it)
                    }
                    .onFailure {
                        chatsScreenState.value = ChatsUiState.Failed(it.message ?: Constants.SERVER_SILENT)
                        // Отказ Firestore не отличает мёртвую сессию от обрыва связи, а
                        // «Повторить» лечит только второе. Проверка разводит эти два случая:
                        // мёртвая сессия уводит с вкладок целиком.
                        sessionInteractor.revalidate()
                    }
            }
        }
    }
}
