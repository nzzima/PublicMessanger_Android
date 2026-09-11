package com.nzzima.secretmessanger.profile.domain.models

/**
 * Собственный профиль владельца устройства.
 *
 * @property id uid из Firebase Auth, он же идентификатор документа.
 * @property login логин; при пустом поле подставляется [name] — так же читает iOS.
 * @property name имя.
 * @property someInfo заметка о себе; пустая строка, если её не писали.
 * @property avatarVersion маркер аватара: ноль — аватара нет, и в базу за ним никто не идёт.
 */
data class Profile(
    val id: String,
    val login: String,
    val name: String,
    val someInfo: String,
    val avatarVersion: Int = 0,
)
