package com.nzzima.secretmessanger.utils.media

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Уменьшение при чтении: снимок с камеры не должен ехать в память целиком.
 *
 * Проверка арифметикой, без картинок: сам `BitmapFactory` в JVM-тестах недоступен, а
 * ошибался здесь именно счёт — перебор степеней двойки уходил за границу типа.
 *
 * Сторона задаётся доводом: у аватара она 320, у снимка переписки 1280.
 */
class DownscaleTest {

    private companion object {
        /** Сторона аватара: на ней и считались все случаи ниже. */
        const val SIDE = 320
    }

    @Test
    fun `снимок уменьшается до стороны аватара`() {
        assertEquals("1280 при стороне 320 — это ровно вчетверо", 4, sampleSize(1280, SIDE))
    }

    @Test
    fun `последняя ступень не переступает сторону аватара`() {
        assertEquals("500 / 2 = 250 уже меньше 320", 1, sampleSize(500, SIDE))
    }

    @Test
    fun `ровно сторона аватара читается как есть`() {
        assertEquals(1, sampleSize(320, SIDE))
    }

    @Test
    fun `мелкая картинка не уменьшается`() {
        assertEquals("320 из неё всё равно не сделать", 1, sampleSize(100, SIDE))
    }

    @Test
    fun `огромная сторона не уводит счёт за границу типа`() {
        assertEquals("2^22: следующая ступень увела бы сторону ниже 320", 4_194_304, sampleSize(Int.MAX_VALUE, SIDE))
    }
}
