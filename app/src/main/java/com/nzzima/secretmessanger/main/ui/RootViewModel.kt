package com.nzzima.secretmessanger.main.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzzima.secretmessanger.auth.domain.api.ProfileRepairInteractor
import com.nzzima.secretmessanger.auth.domain.api.RegistrationProgress
import com.nzzima.secretmessanger.crypto.domain.api.IdentityInteractor
import com.nzzima.secretmessanger.crypto.domain.models.IdentityState
import com.nzzima.secretmessanger.presence.domain.api.PresenceInteractor
import com.nzzima.secretmessanger.session.domain.api.SessionInteractor
import com.nzzima.secretmessanger.session.domain.models.Session
import com.nzzima.secretmessanger.session.domain.models.SessionFailure
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Состояние оболочки приложения.
 *
 * Развилка ключа проверяется здесь, а не в обработчике входа: сессия Firebase живёт
 * месяцами, и человек с давней сессией проверку бы не проходил вовсе.
 *
 * [RootState.Ready] выставляется только после успешной проверки, поэтому мимо развилки в
 * список диалогов не попасть.
 *
 * Порядок проверок обязателен: сначала сессия, потом профиль, потом ключ. Профиль и ключ
 * читаются из Firestore, а мёртвая сессия отказывает там неотличимо от обрыва связи — без
 * первой проверки вход упирался бы в «Повторить», которое не сработает никогда.
 *
 * Профиль раньше ключа по своей причине: публикация открытой половины пишет `users/{uid}`
 * слиянием, а правило этой коллекции требует в записи логин, занятый тем же аккаунтом, — на
 * аккаунте без профиля публикация не проходит, и развилка ключа стала бы тупиком без выхода.
 *
 * Перед проверкой профиля оболочка пережидает идущую регистрацию: сессию открывает её первый
 * шаг, а профиль пишет третий, и в промежутке отсутствие профиля не значит ничего. Без
 * ожидания удавшаяся регистрация уводила на экран достройки.
 */
class RootViewModel(
    private val sessionInteractor: SessionInteractor,
    private val identityInteractor: IdentityInteractor,
    private val profileRepairInteractor: ProfileRepairInteractor,
    private val registrationProgress: RegistrationProgress,
    private val presenceInteractor: PresenceInteractor,
) : ViewModel() {

    private val rootState = MutableStateFlow<RootState>(RootState.Checking)

    /** Виден ли экран: пульс бьётся, только пока приложение на глазах. */
    private val visible = MutableStateFlow(false)

    /** Текущее состояние оболочки. */
    fun observeRootState(): StateFlow<RootState> = rootState.asStateFlow()

    /** Экран показался: с этого мига человек числится в сети. */
    fun onVisible() {
        visible.value = true
    }

    /** Экран ушёл с глаз — пульс прекращается, и человек гаснет сам через окно присутствия. */
    fun onHidden() {
        visible.value = false
    }

    init {
        // Пульс бьётся, пока выполняются оба условия: экран на глазах и вход пройден целиком.
        // Отмечаться раньше развилки ключа значило бы числиться в сети, ещё не войдя.
        viewModelScope.launch {
            combine(rootState, visible) { state, seen -> seen && state == RootState.Ready }
                .distinctUntilChanged()
                .collectLatest { beating ->
                    val uid = uidOrNull()

                    if (beating && uid != null) presenceInteractor.keepAlive(uid)
                }
        }

        viewModelScope.launch {
            // collectLatest, а не collect: смена сессии обрывает незаконченную проверку.
            // Проигранная гонка за логин удаляет только что созданный аккаунт, и проверка по
            // нему договаривала бы про уже несуществующего человека.
            sessionInteractor.observeSession().collectLatest { session ->
                when (session) {
                    is Session.Anonymous -> rootState.value = RootState.Anonymous
                    is Session.Expired -> rootState.value = RootState.Expired
                    is Session.Authenticated -> prepare(session.uid)
                }
            }
        }
    }

    /** Повторяет проверку после отказа связи. */
    fun retry() {
        val uid = uidOrNull() ?: return
        viewModelScope.launch { prepare(uid) }
    }

    /**
     * Публикует свою открытую половину поверх чужой.
     *
     * Вызывается только с экрана развилки, то есть после осознанного подтверждения.
     */
    fun confirmOverwrite() {
        val uid = uidOrNull() ?: return
        rootState.value = RootState.Checking
        viewModelScope.launch {
            identityInteractor.publishOverwriting(uid)
                .onSuccess { rootState.value = RootState.Ready }
                .onFailure { rootState.value = RootState.Failed(it.message ?: Constants.SERVER_SILENT) }
        }
    }

    /**
     * Достраивает оборванную регистрацию под именем [login] и продолжает вход.
     *
     * Вызывается только с экрана достройки. Неудача возвращает на него же с причиной:
     * занятый логин лечится другим логином, а не повтором того же.
     */
    fun repairProfile(login: String) {
        val uid = uidOrNull() ?: return
        rootState.value = RootState.Checking
        viewModelScope.launch {
            profileRepairInteractor.complete(uid, login)
                .onSuccess { prepare(uid) }
                .onFailure { rootState.value = RootState.NeedsProfile(it.message ?: Constants.SERVER_SILENT) }
        }
    }

    /** Завершает сессию. Ключ на устройстве не стирается. */
    fun signOut() = sessionInteractor.signOut()

    private suspend fun prepare(uid: String) {
        rootState.value = RootState.Checking

        registrationProgress.awaitIdle()

        sessionInteractor.revalidate().onFailure { error ->
            // Мёртвую сессию репозиторий уже перевёл в Session.Expired; состояние
            // выставляется здесь же, чтобы не зависеть от порядка повторной выдачи потока.
            rootState.value = when (error) {
                is SessionFailure.Expired -> RootState.Expired
                else -> RootState.Failed(error.message ?: Constants.SERVER_SILENT)
            }
            return
        }

        val complete = profileRepairInteractor.isComplete(uid).getOrElse { error ->
            rootState.value = RootState.Failed(error.message ?: Constants.SERVER_SILENT)
            return
        }

        if (!complete) {
            rootState.value = RootState.NeedsProfile()
            return
        }

        identityInteractor.prepare(uid)
            .onSuccess {
                rootState.value = when (it) {
                    IdentityState.Ready -> RootState.Ready
                    IdentityState.NeedsConfirmation -> RootState.NeedsConfirmation
                }
            }
            .onFailure { rootState.value = RootState.Failed(it.message ?: Constants.SERVER_SILENT) }
    }

    private fun uidOrNull() = (sessionInteractor.observeSession().value as? Session.Authenticated)?.uid
}
