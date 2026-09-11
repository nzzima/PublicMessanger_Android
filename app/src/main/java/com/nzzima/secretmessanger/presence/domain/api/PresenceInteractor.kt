package com.nzzima.secretmessanger.presence.domain.api

import com.nzzima.secretmessanger.presence.domain.models.Presence
import kotlinx.coroutines.flow.Flow

/** Присутствие: своё отмечается, чужое показывается. */
interface PresenceInteractor {

    /**
     * Кто сейчас в сети.
     *
     * Пересчитывается **и на снимок, и по тику**: человек, переставший отмечаться, новых
     * снимков не создаёт, а гаснуть обязан сам — иначе точка горела бы зелёным до первого
     * чужого удара пульса.
     */
    fun observeOnline(): Flow<Set<String>>

    /**
     * Присутствие человека [uid]; `null` — он не отмечался ни разу.
     *
     * Повторяется по тому же тику: подпись «в сети 5 минут назад» стареет сама по себе.
     */
    fun observePresence(uid: String): Flow<Presence?>

    /**
     * Бьёт пульс аккаунта [uid], пока вызывающий не отменит.
     *
     * Отмена — единственный способ остановиться, и это нарочно: присутствие кончается вместе
     * с показом экрана, а не по отдельной команде, которую можно забыть отправить.
     */
    suspend fun keepAlive(uid: String)
}
