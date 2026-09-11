package com.nzzima.secretmessanger.messanger.domain

import com.nzzima.secretmessanger.messanger.domain.models.Place
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Формат точки — договор с iOS, и ломается он молча: в базу уедет строка, которую там
 * разберут не так или не разберут вовсе.
 */
class PlaceTest {

    private val locale = Locale.getDefault()

    @Before fun setUp() = Locale.setDefault(Locale("ru", "RU"))

    @After fun tearDown() = Locale.setDefault(locale)

    @Test
    fun `дробная часть печатается точкой даже под русской локалью`() {
        val payload = Place.payload(55.75, 37.62)

        assertEquals("с запятой строка распалась бы на четыре куска вместо двух", "55.750000,37.620000", payload)
    }

    @Test
    fun `шесть знаков после запятой — этооколо десяти сантиметров`() {
        assertEquals("0.000001,0.000000", Place.payload(0.0000014, 0.0000004))
    }

    @Test
    fun `своя же нагрузка разбирается обратно`() {
        val place = Place(55.751244, 37.618423)

        assertEquals(place, Place.parse(Place.payload(place.latitude, place.longitude)))
    }

    @Test
    fun `не пара чисел — не точка`() {
        for (bad in listOf("", "55.75", "55.75,37.62,10", "широта,долгота", "55,75,37,62")) {
            assertNull(bad, Place.parse(bad))
        }
    }

    @Test
    fun `координаты вне земного шара отбрасываются`() {
        assertNull(Place.parse("95.000000,37.620000"))
        assertNull(Place.parse("55.750000,190.000000"))
    }
}
