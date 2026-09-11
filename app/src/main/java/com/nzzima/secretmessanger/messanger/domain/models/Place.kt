package com.nzzima.secretmessanger.messanger.domain.models

import java.util.Locale

/**
 * Точка на карте — единственное вложение, которому нечего хранить отдельно.
 *
 * Две координаты помещаются в само сообщение и шифруются как обычный текст, тем же ключом:
 * в базе не видно ни места, ни того, что это вообще место. Подколлекции у фото и голосовых
 * заводились не ради порядка, а ради размера — две цифры ездить могут.
 */
data class Place(val latitude: Double, val longitude: Double) {

    companion object {

        /**
         * Полезная нагрузка сообщения — «широта,долгота». Формат нарочно простейший: это две
         * цифры, и городить вокруг них JSON значило бы отдать в базу лишние байты.
         *
         * Шесть знаков после запятой — около десяти сантиметров, заведомо точнее всего, что
         * отдаёт телефон.
         *
         * **Локаль задаётся явно**: с русской в дробной части печаталась бы запятая, и строка
         * «широта,долгота» распалась бы на четыре куска вместо двух. Формат общий с iOS,
         * менять его нельзя.
         */
        fun payload(latitude: Double, longitude: Double): String =
            String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude)

        /** Точка из полезной нагрузки; `null` — это не пара координат. */
        fun parse(payload: String): Place? {
            val parts = payload.split(",")
            if (parts.size != 2) return null

            val latitude = parts[0].toDoubleOrNull() ?: return null
            val longitude = parts[1].toDoubleOrNull() ?: return null

            return Place(latitude, longitude).takeIf { it.isValid() }
        }
    }

    /** Координаты вне земного шара показывать не на чем. */
    private fun isValid(): Boolean = latitude in -90.0..90.0 && longitude in -180.0..180.0
}
