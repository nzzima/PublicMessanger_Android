package com.nzzima.secretmessanger.photo.domain.api

import com.nzzima.secretmessanger.photo.domain.models.EncodedPhoto

/** Подготовка выбранного снимка к отправке. */
interface PhotoEncoder {

    /**
     * Подгоняет снимок [source] под бюджет сообщения
     * [com.nzzima.secretmessanger.utils.constants.Constants.PHOTO_BUDGET].
     *
     * Не «сжимает на всякий случай», а именно подгоняет: камера отдаёт двенадцать
     * мегапикселей и четыре мегабайта — вчетверо больше, чем вмещает документ Firestore.
     * Сторона режется до
     * [com.nzzima.secretmessanger.utils.constants.Constants.PHOTO_SIDE], дальше падает
     * качество, и лишь потом сторона уполовинивается — так же на iOS.
     *
     * @param source адрес снимка в том виде, в каком его отдал системный выборщик.
     * @return `null`, если снимок не разобрался или не уместился даже на последнем заходе.
     */
    suspend fun encode(source: String): EncodedPhoto?
}
