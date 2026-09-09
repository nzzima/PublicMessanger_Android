package com.nzzima.secretmessanger.profile.domain.api

import com.nzzima.secretmessanger.profile.domain.models.Profile
import kotlinx.coroutines.flow.Flow

/**
 * Профиль аккаунта — свой для вкладки и чужой для экрана собеседника.
 *
 * Правило у них одно: пустой логин подменяется именем. Разделять экраны на два сценария
 * было бы обрядом — читаются профили одинаково, а решает, что показывать, слой
 * представления.
 */
interface ProfileInteractor {

    /** Профиль аккаунта [uid]; условия отказа — как у [ProfileReader.observe]. */
    fun observeProfile(uid: String): Flow<Result<Profile>>
}
