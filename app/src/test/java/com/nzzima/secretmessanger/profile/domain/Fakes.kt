package com.nzzima.secretmessanger.profile.domain

import com.nzzima.secretmessanger.profile.domain.api.ProfileInteractor
import com.nzzima.secretmessanger.profile.domain.api.ProfileReader
import com.nzzima.secretmessanger.profile.domain.models.Profile
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Профиль в памяти.
 *
 * Начального значения нет: до первого [send] подписчик не получает ничего — так же ведёт
 * себя Firestore, пока не пришёл первый снимок.
 */
class FakeProfileReader : ProfileReader {

    private val snapshots = MutableSharedFlow<Result<Profile>>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun observe(uid: String): Flow<Result<Profile>> = snapshots

    /** Отдаёт подписчикам очередной профиль. */
    fun send(profile: Profile) = snapshots.tryEmit(Result.success(profile))

    /** Отдаёт подписчикам отказ. */
    fun fail(error: Throwable) = snapshots.tryEmit(Result.failure(error))
}

/**
 * [ProfileInteractor] в памяти: профиль на собеседника и счётчик обращений.
 *
 * Счётчик и есть суть половины проверок: список перечитывается на каждую реплику в любом из
 * диалогов, и опрашивать по профилю на снимок было бы расточительством.
 */
class FakeCompanionProfiles : ProfileInteractor {

    private val profiles = mutableMapOf<String, Profile>()
    private val asked = mutableMapOf<String, Int>()

    fun put(uid: String, version: Int) {
        profiles[uid] = Profile(id = uid, login = uid, name = uid, someInfo = "", avatarVersion = version)
    }

    /** Сколько раз спрашивали профиль [uid]. */
    fun requests(uid: String): Int = asked[uid] ?: 0

    override fun observeProfile(uid: String): Flow<Result<Profile>> {
        asked[uid] = requests(uid) + 1

        return flowOf(
            profiles[uid]?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("профиля нет")),
        )
    }
}
