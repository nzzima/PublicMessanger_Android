package com.nzzima.secretmessanger.presence.domain

import com.nzzima.secretmessanger.presence.domain.api.PresenceInteractor
import com.nzzima.secretmessanger.presence.domain.api.PresenceRepository
import com.nzzima.secretmessanger.presence.domain.models.Presence
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** [PresenceRepository] в памяти: снимки подаёт тест, удары считает сам. */
class FakePresenceRepository : PresenceRepository {

    private val everyone = MutableSharedFlow<Result<Map<String, Presence>>>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val one = MutableSharedFlow<Result<Presence?>>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Удары пульса: чей аккаунт и сколько раз. */
    val beats = mutableListOf<String>()

    override fun observeEveryone(): Flow<Result<Map<String, Presence>>> = everyone

    override fun observe(uid: String): Flow<Result<Presence?>> = one

    override suspend fun beat(uid: String): Result<Unit> {
        beats += uid
        return Result.success(Unit)
    }

    /** Отдаёт снимок присутствия всех. */
    fun send(snapshot: Map<String, Presence>) = everyone.tryEmit(Result.success(snapshot))

    /** Отдаёт присутствие одного. */
    fun send(presence: Presence?) = one.tryEmit(Result.success(presence))
}

/** [PresenceInteractor] в памяти — для экранов, которым сам пульс не нужен. */
class FakePresenceInteractor(
    private val online: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet()),
    private val presence: MutableStateFlow<Presence?> = MutableStateFlow(null),
) : PresenceInteractor {

    /** Сколько раз начинали бить пульс. */
    var starts = 0
        private set

    /** Чьё присутствие спрашивали. */
    val watched = mutableListOf<String>()

    override fun observeOnline(): Flow<Set<String>> = online

    override fun observePresence(uid: String): Flow<Presence?> {
        watched += uid
        return presence
    }

    override suspend fun keepAlive(uid: String) {
        starts++
        beating = uid
        try {
            awaitCancellation()
        } finally {
            beating = null
        }
    }

    /** Чей пульс бьётся прямо сейчас; `null` — ничей. */
    var beating: String? = null
        private set

    /** Подаёт список сетевых. */
    fun online(uids: Set<String>) {
        online.value = uids
    }

    /** Подаёт присутствие одного. */
    fun presence(value: Presence?) {
        presence.value = value
    }
}
