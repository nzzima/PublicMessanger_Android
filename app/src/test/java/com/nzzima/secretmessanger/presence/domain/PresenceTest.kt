package com.nzzima.secretmessanger.presence.domain

import com.nzzima.secretmessanger.presence.domain.models.Presence
import com.nzzima.secretmessanger.utils.constants.Constants
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Присутствие — это арифметика по двум датам, и ошибаться в ней на глаз не хочется.
 *
 * Часовой пояс задан явно: с системным тесты зависели бы от машины, на которой их запускают.
 */
class PresenceTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private val now = at("2026-08-19T14:30:00")

    @Test
    fun `свежий удар — в сети`() {
        assertTrue(Presence(now - 10_000).isOnline(now))
        assertEquals(Constants.ONLINE, Presence(now - 10_000).text(now, zone))
    }

    @Test
    fun `пропущенный удар ещё не гасит`() {
        val missed = Presence(now - Constants.PRESENCE_HEARTBEAT_MS - 5_000)

        assertTrue("в метро и в лифте удар теряется — мигать серым из-за этого нельзя", missed.isOnline(now))
    }

    @Test
    fun `за окном присутствия человек гаснет`() {
        assertFalse(Presence(now - Constants.PRESENCE_WINDOW_MS - 1).isOnline(now))
    }

    @Test
    fun `удар из будущего считается сетевым`() {
        val ahead = Presence(now + 30_000)

        assertTrue("часы читателя отстали от серверных — это не повод гасить", ahead.isOnline(now))
    }

    @Test
    fun `минуты склоняются по-русски`() {
        // Единицы здесь нет намеренно: минуту назад человек ещё в сети — окно шире минуты.
        // Первую минуту проверяет случай «только что ушедший».
        val expected = mapOf(
            2 to "в сети 2 минуты назад",
            5 to "в сети 5 минут назад",
            11 to "в сети 11 минут назад",
            21 to "в сети 21 минуту назад",
            22 to "в сети 22 минуты назад",
        )

        for ((minutes, text) in expected) {
            assertEquals(text, Presence(now - minutes * 60_000L).text(now, zone))
        }
    }

    @Test
    fun `только что ушедший — это минута, а не ноль`() {
        val gone = Presence(now - Constants.PRESENCE_WINDOW_MS - 1_000)

        assertEquals("в сети 1 минуту назад", gone.text(now, zone))
    }

    @Test
    fun `за пределами часа — время, а не минуты`() {
        assertEquals("в сети сегодня в 12:32", Presence(at("2026-08-19T12:32:00")).text(now, zone))
    }

    @Test
    fun `вчерашнее называется вчерашним, даже если прошло меньше суток`() {
        assertEquals("в сети вчера в 23:50", Presence(at("2026-08-18T23:50:00")).text(now, zone))
    }

    @Test
    fun `старое показывается датой`() {
        assertEquals("в сети 17 августа", Presence(at("2026-08-17T09:05:00")).text(now, zone))
    }
}
