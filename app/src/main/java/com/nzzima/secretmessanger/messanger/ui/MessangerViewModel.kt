package com.nzzima.secretmessanger.messanger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.ConversationGone
import com.nzzima.secretmessanger.messanger.domain.api.MessangerInteractor
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
 * Состояние экрана переписки и обработка ввода.
 *
 * Идентификатор аккаунта берётся из сессии в момент подписки: экран достижим только из
 * вкладок, то есть при живой сессии.
 *
 * Отказ подписки проверяется на мёртвую сессию — с ней «Повторить» не сработает никогда,
 * и решение принимает оболочка, а не этот экран. Стёртый диалог проверять незачем:
 * сессия тут ни при чём.
 */
class MessangerViewModel(
    private val convoId: String,
    private val sessionInteractor: SessionInteractor,
    private val messangerInteractor: MessangerInteractor,
) : ViewModel() {

    private val messangerScreenState = MutableStateFlow<MessangerUiState>(MessangerUiState.Loading)
    private var subscription: Job? = null

    /**
     * Шапка последнего снимка — ею запечатывается отправляемое.
     *
     * Держится отдельно от состояния экрана: показывать её нечем, а ключ диалога может
     * приехать при открытом экране, и отправка обязана брать свежий.
     */
    private var chat: Chat? = null

    /** Текущее состояние экрана. */
    fun observeMessangerScreenState(): StateFlow<MessangerUiState> = messangerScreenState.asStateFlow()

    init {
        subscribe()
    }

    /**
     * Подписывается на переписку заново.
     *
     * Нужна после отказа: слушатель Firestore на ошибке снимается, и продолжать слушать
     * прежней подпиской нечего.
     */
    fun retry() = subscribe()

    /** Записывает набранный текст и снимает показанную ошибку. */
    fun onDraftChange(value: String) = messangerScreenState.update { current ->
        if (current is MessangerUiState.Content) current.copy(draft = value, error = null) else current
    }

    /**
     * Отправляет набранное.
     *
     * Отправка ограничена [Constants.SUBMIT_TIMEOUT_MS]. Набранный текст очищается только
     * после успеха: отказ его сохраняет — переписывать заново из-за пропавшей связи
     * человеку не за что.
     */
    fun onSend() {
        val state = messangerScreenState.value as? MessangerUiState.Content ?: return
        val chat = chat ?: return
        val text = state.draft.trim()
        if (text.isEmpty() || state.isSending) return

        messangerScreenState.update { current ->
            if (current is MessangerUiState.Content) current.copy(isSending = true, error = null) else current
        }

        viewModelScope.launch {
            val result = withTimeoutOrNull(Constants.SUBMIT_TIMEOUT_MS) {
                messangerInteractor.send(chat, text)
            }

            messangerScreenState.update { current ->
                if (current !is MessangerUiState.Content) return@update current

                when {
                    result == null -> current.copy(isSending = false, error = Constants.SERVER_SILENT)
                    result.isSuccess -> current.copy(isSending = false, draft = "")
                    else -> current.copy(
                        isSending = false,
                        error = result.exceptionOrNull()?.message ?: Constants.SERVER_SILENT,
                    )
                }
            }
        }
    }

    private fun subscribe() {
        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return

        subscription?.cancel()
        messangerScreenState.value = MessangerUiState.Loading

        subscription = viewModelScope.launch {
            messangerInteractor.observeDialogue(convoId, uid).collect { snapshot ->
                snapshot
                    .onSuccess { dialogue ->
                        chat = dialogue.chat
                        messangerScreenState.update { current ->
                            // Набранное и показанная ошибка переживают новый снимок: чужая
                            // реплика посреди набора текста его не стирает.
                            if (current is MessangerUiState.Content) {
                                current.copy(title = dialogue.chat.title, replies = dialogue.replies)
                            } else {
                                MessangerUiState.Content(dialogue.chat.title, dialogue.replies)
                            }
                        }
                    }
                    .onFailure { error ->
                        val gone = error is ConversationGone
                        messangerScreenState.value = MessangerUiState.Failed(
                            message = error.message ?: Constants.SERVER_SILENT,
                            canRetry = !gone,
                        )

                        // Отказ Firestore не отличает мёртвую сессию от обрыва связи, а
                        // «Повторить» лечит только второе. У стёртого диалога сессия ни
                        // при чём — спрашивать не о чем.
                        if (!gone) sessionInteractor.revalidate()
                    }
            }
        }
    }
}
