package com.nzzima.secretmessanger.photo.domain.models

/**
 * Размеры снимка в точках — те, что едут в самом сообщении.
 *
 * Байты лежат отдельно и приезжают позже, а пузырь надо сверстать сразу: без размеров лента
 * прыгала бы при каждой догрузке.
 */
data class PhotoSize(val width: Int, val height: Int)
