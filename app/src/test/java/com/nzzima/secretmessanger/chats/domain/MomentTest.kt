package com.nzzima.secretmessanger.chats.domain

import com.nzzima.secretmessanger.chats.domain.models.Moment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Момент времени — и главное про него: наносекунды не теряются.
 *
 * На этом обжёгся iOS: пересобранное из `Date` время промахивалось вниз на доли микросекунды,
 * и «дочитал ровно до этого сообщения» превращалось в «не дочитал». В секундах разницы не
 * видно, поэтому её и проверяют тесты.
 */
class MomentTest {

    @Test
    fun `наносекунды решают сравнение при равных секундах`() {
        assertTrue(Moment(5, 500) > Moment(5, 499))
        assertTrue(Moment(5, 0) < Moment(5, 1))
    }

    @Test
    fun `секунды старше наносекунд`() {
        assertTrue(Moment(6, 0) > Moment(5, 999_999_999))
    }

    @Test
    fun `равные моменты равны`() {
        assertTrue(Moment(5, 500) >= Moment(5, 500))
        assertEquals(Moment(5, 500), Moment(5, 500))
    }

    @Test
    fun `миллисекунды отбрасывают наносекунды, а не округляют`() {
        assertEquals(5_000, Moment(5, 999_999).millis)
        assertEquals(5_001, Moment(5, 1_000_000).millis)
    }

    @Test
    fun `момент из миллисекунд возвращает те же миллисекунды`() {
        assertEquals(1_788_952_430_410, Moment.of(1_788_952_430_410).millis)
    }

    @Test
    fun `время до эпохи не разъезжается по знаку`() {
        // Часов до 1970 в переписке не бывает, но обычное деление сломалось бы на них молча:
        // дало бы ноль секунд и отрицательные наносекунды, а Timestamp такого не принимает.
        val moment = Moment.of(-500)

        assertEquals(-1, moment.seconds)
        assertEquals(500_000_000, moment.nanoseconds)
        assertEquals(-500, moment.millis)
    }
}
