package com.nzzima.secretmessanger.contacts.domain.models

/**
 * Человек в списке контактов.
 *
 * @property id uid из Firebase Auth, он же идентификатор документа профиля.
 * @property login отображаемое имя: логин, а при пустом логине — имя из профиля.
 * @property avatarVersion маркер аватара из профиля; ноль — аватара нет.
 */
data class Contact(
    val id: String,
    val login: String,
    val avatarVersion: Int = 0,
)
