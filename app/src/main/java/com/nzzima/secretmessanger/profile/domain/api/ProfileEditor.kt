package com.nzzima.secretmessanger.profile.domain.api

/** Правка своего профиля: поля и перенос захвата логина в реестре. */
interface ProfileEditor {

    /**
     * Сохраняет профиль аккаунта [uid].
     *
     * @param currentLogin логин на момент открытия формы. По нему видно, менялось ли имя:
     *   не менялось — реестр не трогается вовсе.
     * @return отказ [com.nzzima.secretmessanger.auth.domain.models.LoginTaken], если новое
     *   имя занято другим аккаунтом; профиль в этом случае не меняется ни в одном поле.
     */
    suspend fun save(
        uid: String,
        login: String,
        name: String,
        someInfo: String,
        currentLogin: String,
    ): Result<Unit>
}
