package com.nzzima.secretmessanger.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzzima.secretmessanger.auth.domain.FieldRules
import com.nzzima.secretmessanger.avatar.domain.api.AvatarInteractor
import com.nzzima.secretmessanger.profile.domain.api.ProfileEditor
import com.nzzima.secretmessanger.profile.domain.api.ProfileInteractor
import com.nzzima.secretmessanger.session.domain.api.SessionInteractor
import com.nzzima.secretmessanger.utils.constants.Constants
import com.nzzima.secretmessanger.utils.errors.ErrorText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Форма правки своего профиля.
 *
 * Логин на момент открытия держится отдельно от поля ввода: по нему сценарий понимает,
 * менялось ли имя, и надо ли вообще трогать реестр занятых имён.
 */
class EditProfileViewModel(
    private val sessionInteractor: SessionInteractor,
    private val profileInteractor: ProfileInteractor,
    private val profileEditor: ProfileEditor,
    private val avatarInteractor: AvatarInteractor,
) : ViewModel() {

    private val editProfileScreenState = MutableStateFlow<EditProfileUiState>(EditProfileUiState.Loading)

    private var currentLogin = ""

    /** Текущее состояние экрана. */
    fun observeEditProfileScreenState(): StateFlow<EditProfileUiState> = editProfileScreenState.asStateFlow()

    init {
        load()
    }

    /** Читает профиль заново — нужна после отказа. */
    fun retry() = load()

    /** Записывает логин без окружающих пробелов и снимает показанную ошибку. */
    fun onLoginChange(value: String) = update { it.copy(login = value.trim(), error = null) }

    /** Записывает имя и снимает показанную ошибку. */
    fun onNameChange(value: String) = update { it.copy(name = value, error = null) }

    /** Записывает заметку и снимает показанную ошибку. */
    fun onSomeInfoChange(value: String) = update { it.copy(someInfo = value, error = null) }

    /**
     * Сохраняет форму.
     *
     * Логин проверяется до всякой записи — теми же правилами, что на регистрации. Отправка
     * ограничена [Constants.SUBMIT_TIMEOUT_MS]: за кнопкой стоит до трёх записей подряд —
     * захват имени, профиль, освобождение старого, — и молчащая сеть подвесила бы форму.
     */
    fun onSave() {
        val form = editProfileScreenState.value as? EditProfileUiState.Form ?: return
        if (form.isSaving) return

        if (!FieldRules.isValidLogin(form.login)) {
            update { it.copy(error = Constants.INVALID_LOGIN) }
            return
        }

        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return

        update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            val result = withTimeoutOrNull(Constants.SUBMIT_TIMEOUT_MS) {
                profileEditor.save(
                    uid = uid,
                    login = form.login,
                    name = form.name,
                    someInfo = form.someInfo,
                    currentLogin = currentLogin,
                )
            }

            update { current ->
                when {
                    result == null -> current.copy(isSaving = false, error = Constants.SERVER_SILENT)
                    result.isSuccess -> current.copy(isSaving = false, saved = true)
                    else -> current.copy(
                        isSaving = false,
                        error = ErrorText.of(result.exceptionOrNull()),
                    )
                }
            }
        }
    }

    /**
     * Ставит выбранную картинку [source] аватаром.
     *
     * Аватар пишется **сразу**, не дожидаясь «Сохранить», и это не небрежность: он лежит в
     * своём документе и ни с чем в форме не связан, а отказ на проверке логина иначе терял
     * бы выбранное фото. Так же сделано на iOS.
     */
    fun onAvatarPicked(source: String) = changeAvatar { uid, form ->
        avatarInteractor.change(uid, source, form.avatarVersion)
            .map { form.avatarVersion + 1 }
    }

    /** Убирает аватар — тоже сразу. */
    fun onAvatarRemoved() = changeAvatar { uid, _ ->
        avatarInteractor.remove(uid).map { NO_AVATAR }
    }

    /** Завершает сессию. Экран входа откроет навигация по изменению состояния сессии. */
    fun onSignOut() = sessionInteractor.signOut()

    /**
     * Общая часть смены и удаления аватара.
     *
     * Пока запись идёт, вторую не начинаем: номер следующей версии считается от текущей, а
     * она станет известна только по возвращении. Два быстрых выбора подряд иначе ушли бы под
     * одним номером — и второй показывался бы из кэша первым.
     */
    private fun changeAvatar(write: suspend (String, EditProfileUiState.Form) -> Result<Int>) {
        val form = editProfileScreenState.value as? EditProfileUiState.Form ?: return
        if (form.isAvatarChanging) return

        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return

        update { it.copy(isAvatarChanging = true, error = null) }

        viewModelScope.launch {
            write(uid, form)
                .onSuccess { version ->
                    val image = avatarInteractor.avatar(uid, version)
                    update { it.copy(isAvatarChanging = false, avatarVersion = version, avatar = image) }
                }
                .onFailure { error ->
                    update {
                        it.copy(isAvatarChanging = false, error = ErrorText.of(error))
                    }
                }
        }
    }

    private fun load() {
        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return

        editProfileScreenState.value = EditProfileUiState.Loading

        viewModelScope.launch {
            profileInteractor.observeProfile(uid).first()
                .onSuccess { profile ->
                    currentLogin = profile.login
                    editProfileScreenState.value = EditProfileUiState.Form(
                        login = profile.login,
                        name = profile.name,
                        someInfo = profile.someInfo,
                        avatarVersion = profile.avatarVersion,
                    )
                    val image = avatarInteractor.avatar(profile.id, profile.avatarVersion)
                    update { it.copy(avatar = image) }
                }
                .onFailure {
                    editProfileScreenState.value =
                        EditProfileUiState.Failed(it.message ?: Constants.PROFILE_MISSING)
                }
        }
    }

    private fun update(change: (EditProfileUiState.Form) -> EditProfileUiState.Form) =
        editProfileScreenState.update { current ->
            if (current is EditProfileUiState.Form) change(current) else current
        }

    private companion object {
        const val NO_AVATAR = 0
    }
}
