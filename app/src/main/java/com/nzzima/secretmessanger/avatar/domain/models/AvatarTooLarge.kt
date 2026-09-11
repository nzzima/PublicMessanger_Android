package com.nzzima.secretmessanger.avatar.domain.models

import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Картинку не удалось уместить в бюджет аватара даже на низком качестве.
 *
 * На практике недостижимо: 320×320 в JPEG укладывается в 30–40 КБ при потолке в 200 КБ,
 * упереться можно разве что шумом во весь кадр. Отказ заведён потому, что кодирование
 * может и не состояться вовсе — картинка не разобралась.
 */
class AvatarTooLarge : Exception(Constants.AVATAR_TOO_LARGE)
