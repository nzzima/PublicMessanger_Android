package com.nzzima.secretmessanger.presence.domain.models

import com.nzzima.secretmessanger.utils.constants.Constants
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Присутствие человека: когда его видели в последний раз.
 *
 * В базе лежит **одно** поле — время последнего удара пульса. Флага `online: Boolean` там нет
 * намеренно: приложение, снятое из переключателя задач, дописать «я ушёл» уже не успеет, и
 * такой флаг застрял бы на «в сети» навсегда — врал бы молча, до следующего запуска.
 * Вычисляемое присутствие врать не умеет: пульс прекратился — человек гаснет сам.
 *
 * Тип чистый: ни Firestore, ни Compose. Всё, что он делает, — арифметика по двум датам, и
 * ошибаться в ней на глаз не хочется.
 *
 * @property lastSeen время последнего удара в миллисекундах эпохи — **серверное**, а не с
 *   часов писавшего: с клиентским «в сети» рисуется себе руками переводом часов.
 */
data class Presence(val lastSeen: Long) {

    /**
     * В сети ли человек прямо сейчас.
     *
     * [now] берётся с часов **читающего**, а [lastSeen] — с сервера. Разошедшиеся часы
     * читателя сдвинут ответ на всю величину расхождения, и сделать с этим нечего:
     * спрашивать время у сервера на каждую перерисовку дороже, чем стоит сама точка.
     *
     * Отрицательная разница — часы читателя отстали — считается «в сети»: удар пришёл,
     * просто по нашим часам он из будущего.
     */
    fun isOnline(now: Long): Boolean = now - lastSeen < Constants.PRESENCE_WINDOW_MS

    /**
     * Подпись под именем: «в сети» либо когда человека видели в последний раз.
     *
     * **Без рода.** «Был» и «была» требуют знать пол, которого в профиле нет и не
     * предвидится, а «был(а) в сети» — канцелярия в приложении для переписки.
     *
     * @param zone часовой пояс читателя; доводом ради тестов, как и [now].
     */
    fun text(now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (isOnline(now)) return Constants.ONLINE

        val elapsed = now - lastSeen

        // Внутри часа человеку интереснее «сколько прошло», а не «в котором часу»: «в сети
        // 20 минут назад» читается сразу, «в сети сегодня в 14:32» требует посмотреть на
        // свои часы и вычесть.
        if (elapsed < HOUR_MS) {
            val minutes = (elapsed / MINUTE_MS).toInt().coerceAtLeast(1)

            return "${Constants.ONLINE} $minutes ${minutesWord(minutes)} назад"
        }

        val seen = Instant.ofEpochMilli(lastSeen).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = TIME.format(Instant.ofEpochMilli(lastSeen).atZone(zone))

        // «Сегодня» и «вчера» считаются от переданного now, а не от системных часов: иначе
        // подпись зависела бы от двух источников времени сразу.
        return when (seen) {
            today -> "${Constants.ONLINE} сегодня в $time"
            today.minusDays(1) -> "${Constants.ONLINE} вчера в $time"
            else -> "${Constants.ONLINE} ${DAY.format(Instant.ofEpochMilli(lastSeen).atZone(zone))}"
        }
    }

    /** Русский счёт: 1 минуту, 2 минуты, 5 минут — и отдельно вторая дюжина. */
    private fun minutesWord(count: Int): String = when {
        count % 100 in 11..14 -> "минут"
        count % 10 == 1 -> "минуту"
        count % 10 in 2..4 -> "минуты"
        else -> "минут"
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 60 * MINUTE_MS

        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", RUSSIAN)
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", RUSSIAN)
    }
}
