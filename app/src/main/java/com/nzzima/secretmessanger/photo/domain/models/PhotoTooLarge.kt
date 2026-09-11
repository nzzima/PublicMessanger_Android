package com.nzzima.secretmessanger.photo.domain.models

import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Снимок не удалось уместить в бюджет сообщения даже на последнем заходе.
 *
 * На практике недостижимо: три захода уполовинивают сторону до 320 точек, и такой кадр
 * укладывается в 700 КБ с огромным запасом. Отказ заведён потому, что кодирование может и не
 * состояться вовсе — снимок не разобрался.
 */
class PhotoTooLarge : Exception(Constants.PHOTO_TOO_LARGE)
