package com.nzzima.secretmessanger.presence.domain.impl

import com.nzzima.secretmessanger.presence.domain.api.PresenceInteractor
import com.nzzima.secretmessanger.presence.domain.api.PresenceRepository
import com.nzzima.secretmessanger.presence.domain.models.Presence
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Присутствие поверх [PresenceRepository] плюс тик, от которого гаснут точки.
 *
 * @param now часы читателя; доводом ради тестов — в бою это системное время.
 */
class PresenceInteractorImpl(
    private val presence: PresenceRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : PresenceInteractor {

    override fun observeOnline(): Flow<Set<String>> =
        combine(presence.observeEveryone(), ticker()) { snapshot, _ ->
            snapshot.getOrNull().orEmpty()
                .filterValues { it.isOnline(now()) }
                .keys
        }

    override fun observePresence(uid: String): Flow<Presence?> =
        combine(presence.observe(uid), ticker()) { snapshot, _ -> snapshot.getOrNull() }

    override suspend fun keepAlive(uid: String) {
        while (true) {
            presence.beat(uid)
            delay(Constants.PRESENCE_HEARTBEAT_MS)
        }
    }

    /**
     * Тик той же частоты, что и пульс.
     *
     * Чаще незачем: раньше следующего удара состояние всё равно не меняется. Первый тик
     * выдаётся сразу, иначе список ждал бы полминуты, прежде чем зажечь хоть одну точку.
     */
    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(Constants.PRESENCE_HEARTBEAT_MS)
        }
    }
}
