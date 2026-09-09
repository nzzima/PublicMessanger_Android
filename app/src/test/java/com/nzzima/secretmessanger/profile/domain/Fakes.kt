package com.nzzima.secretmessanger.profile.domain

import com.nzzima.secretmessanger.profile.domain.api.ProfileReader
import com.nzzima.secretmessanger.profile.domain.models.Profile
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

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
