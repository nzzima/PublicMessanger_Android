package com.nzzima.secretmessanger.profile.domain.impl

import com.nzzima.secretmessanger.auth.domain.api.LoginRepository
import com.nzzima.secretmessanger.auth.domain.api.ProfileRepository
import com.nzzima.secretmessanger.auth.domain.models.LoginAvailability
import com.nzzima.secretmessanger.auth.domain.models.LoginTaken
import com.nzzima.secretmessanger.chats.domain.api.ConversationRepository
import com.nzzima.secretmessanger.profile.domain.api.ProfileEditor

/**
 * Правка профиля с переносом логина.
 *
 * **Порядок шагов при переименовании — вся суть, и он обратный интуитивному:** сначала
 * занять новое имя, потом переписать профиль, и только последним отпустить старое.
 *
 * Отпусти старое первым — и своё имя окажется свободным раньше, чем выяснится, достанется
 * ли новое: на занятом новом человек остался бы вовсе без логина, а старое к тому моменту
 * мог забрать кто угодно. Профиль же нельзя переписать раньше захвата: правило на
 * `users/{uid}` требует, чтобы логин в нём был уже занят этим аккаунтом.
 *
 * Обрыв посередине ничего не ломает: за человеком окажутся заняты оба имени, а повторная
 * попытка увидит новое уже своим и доведёт дело до конца.
 */
class ProfileEditorImpl(
    private val profiles: ProfileRepository,
    private val logins: LoginRepository,
    private val conversations: ConversationRepository,
) : ProfileEditor {

    override suspend fun save(
        uid: String,
        login: String,
        name: String,
        someInfo: String,
        currentLogin: String,
    ): Result<Unit> {
        // Сравнение с учётом регистра: «red» → «Red» для реестра не переименование, ключ
        // там нижним регистром, — а для собеседников переименование, и разослать его надо.
        val renamed = login != currentLogin

        if (LoginRepository.key(login) == LoginRepository.key(currentLogin)) {
            // Имя за нами держит та же запись реестра, что и раньше. Пойди мы общим путём,
            // последним шагом удалили бы собственный захват.
            return profiles.updateProfile(uid, login, name, someInfo)
                .onSuccess { if (renamed) spreadLogin(uid, login) }
        }

        when (logins.check(login, uid).getOrElse { return Result.failure(it) }) {
            LoginAvailability.TAKEN -> return Result.failure(LoginTaken())
            LoginAvailability.FREE -> logins.claim(login, uid).getOrElse { return Result.failure(it) }
            // Имя уже наше — так выглядит повторная попытка после обрыва.
            LoginAvailability.MINE -> Unit
        }

        profiles.updateProfile(uid, login, name, someInfo).getOrElse { return Result.failure(it) }

        // Старое имя не отпустилось — переименование при этом состоялось, и держать человека
        // на экране незачем. Оно останется занятым за нами: неприятно, но безвредно, и
        // следующая правка того же логина увидит его своим.
        logins.release(currentLogin)

        spreadLogin(uid, login)
        return Result.success(Unit)
    }

    /**
     * Рассылает новое имя по шапкам своих диалогов.
     *
     * Неудача не отменяет переименования — оно уже состоялось в профиле и в реестре. Цена
     * известна: в этих диалогах собеседники увидят старое имя, и догонит оно только со
     * следующим переименованием — дописывать имя при каждой отправке, как делает iOS,
     * Android не умеет.
     */
    private suspend fun spreadLogin(uid: String, login: String) {
        conversations.renameInConversations(uid, login)
    }
}
