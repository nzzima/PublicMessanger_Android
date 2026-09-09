package com.nzzima.secretmessanger.chats.domain.models

/**
 * Момент времени в переписке: секунды эпохи и наносекунды внутри секунды.
 *
 * Не миллисекунды, и это не запас на будущее. Метка прочтения возвращается в базу **той же
 * самой величиной**, что стоит в документе сообщения: обе стороны сравнивают «дочитал до» с
 * датой реплики, а `Timestamp` в Firestore держит наносекунды. Округли до миллисекунд — и
 * запись уедет вниз на доли микросекунды, после чего «дочитал ровно до этого сообщения»
 * станет «не дочитал». iOS на этом уже обжёгся: галочка не синела, а в логе разницы не
 * видно — секунды совпадают.
 *
 * Для показа и сортировки хватает [millis]; в базу уходит пара целиком.
 */
data class Moment(val seconds: Long, val nanoseconds: Int) : Comparable<Moment> {

    /** Миллисекунды эпохи — для показа и сортировки. */
    val millis: Long get() = seconds * MILLIS_IN_SECOND + nanoseconds / NANOS_IN_MILLI

    override fun compareTo(other: Moment): Int =
        compareValuesBy(this, other, Moment::seconds, Moment::nanoseconds)

    companion object {

        /** Момент из миллисекунд — для того, что рождается на этом устройстве. */
        fun of(millis: Long): Moment = Moment(
            seconds = Math.floorDiv(millis, MILLIS_IN_SECOND),
            nanoseconds = (Math.floorMod(millis, MILLIS_IN_SECOND) * NANOS_IN_MILLI).toInt(),
        )

        private const val MILLIS_IN_SECOND = 1_000L
        private const val NANOS_IN_MILLI = 1_000_000
    }
}
