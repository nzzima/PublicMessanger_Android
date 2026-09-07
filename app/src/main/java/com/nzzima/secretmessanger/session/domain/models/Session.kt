package com.nzzima.secretmessanger.session.domain.models

/** Состояние сессии пользователя. */
sealed interface Session {

    /** Сессии нет: пользователь не вошёл. */
    data object Anonymous : Session

    /** Пользователь вошёл. [uid] — идентификатор аккаунта. */
    data class Authenticated(val uid: String) : Session

    /**
     * Сессия на устройстве есть, но сервис её не признаёт.
     *
     * Отдельное состояние, а не [Anonymous]: запросы к базе отказывают одинаково, но
     * человеку нужно объяснить, почему его вывели из аккаунта, которым он только что
     * пользовался. Выход из этого состояния один — новый вход.
     */
    data object Expired : Session

    /** Идентификатор аккаунта действующей сессии; `null` для [Anonymous] и [Expired]. */
    val uidOrNull: String? get() = (this as? Authenticated)?.uid
}
