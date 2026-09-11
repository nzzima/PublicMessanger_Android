package com.nzzima.secretmessanger.messanger.domain.api

import com.nzzima.secretmessanger.messanger.domain.models.Place

/** Где телефон находится сейчас. */
interface LocationSource {

    /**
     * Текущее место; `null` — определить не вышло.
     *
     * Разрешение спрашивает экран, а не источник: спрашивать его умеет только `Activity`, и
     * тащить её сюда значило бы тащить платформу в домен.
     */
    suspend fun current(): Place?
}
