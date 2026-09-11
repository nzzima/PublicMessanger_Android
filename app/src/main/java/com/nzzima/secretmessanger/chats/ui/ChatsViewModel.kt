package com.nzzima.secretmessanger.chats.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzzima.secretmessanger.avatar.domain.api.AvatarInteractor
import com.nzzima.secretmessanger.chats.domain.api.ChatsInteractor
import com.nzzima.secretmessanger.chats.domain.models.Conversation
import com.nzzima.secretmessanger.profile.domain.api.ProfileInteractor
import com.nzzima.secretmessanger.session.domain.api.SessionInteractor
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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
    private val profileInteractor: ProfileInteractor,
    private val avatarInteractor: AvatarInteractor,
) : ViewModel() {

    private val chatsScreenState = MutableStateFlow<ChatsUiState>(ChatsUiState.Loading)
    private var subscription: Job? = null

    /** Собеседники, про которых уже спрашивали, — включая тех, у кого аватара не нашлось. */
    private val askedCompanions = mutableSetOf<String>()

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

    /**
     * Догружает аватары собеседников.
     *
     * Версия аватара лежит в профиле, а шапка диалога кэширует только логины — значит за ней
     * приходится сходить отдельно, по разу на собеседника. Спрашиваем **только про новых**,
     * включая тех, у кого аватара не нашлось: список перечитывается на каждую реплику в любом
     * из диалогов, и без этой пометки один и тот же профиль опрашивался бы снова и снова.
     *
     * У группы собеседника нет вовсе — там значок вместо кружка, и в базу за ним никто не идёт.
     *
     * Аватар, сменённый собеседником посреди разговора, догонит при следующем открытии
     * вкладки: слушатель на чужие профили ради кружка в списке того не стоит. Так же на iOS.
     */
    private fun loadAvatars(conversations: List<Conversation>) {
        val companions = conversations.mapNotNull { it.chat.companionId }.filter(askedCompanions::add)
        if (companions.isEmpty()) return

        viewModelScope.launch {
            companions.forEach { uid ->
                val version = profileInteractor.observeProfile(uid).first().getOrNull()?.avatarVersion ?: 0
                val image = avatarInteractor.avatar(uid, version) ?: return@forEach

                chatsScreenState.update { current ->
                    if (current is ChatsUiState.Content) {
                        current.copy(avatars = current.avatars + (uid to image))
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun subscribe() {
        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return

        subscription?.cancel()
        chatsScreenState.value = ChatsUiState.Loading
        // Подписка начинается с пустого состояния, поэтому и спрошенных помним заново: иначе
        // после отказа список остался бы без картинок навсегда.
        askedCompanions.clear()

        subscription = viewModelScope.launch {
            chatsInteractor.observeConversations(uid).collect { snapshot ->
                snapshot
                    .onSuccess { conversations ->
                        chatsScreenState.update { current ->
                            when {
                                conversations.isEmpty() -> ChatsUiState.Empty
                                // Загруженные аватары переживают снимок: список
                                // перечитывается на каждую реплику в любом из диалогов.
                                current is ChatsUiState.Content -> current.copy(conversations = conversations)
                                else -> ChatsUiState.Content(conversations)
                            }
                        }
                        loadAvatars(conversations)
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
