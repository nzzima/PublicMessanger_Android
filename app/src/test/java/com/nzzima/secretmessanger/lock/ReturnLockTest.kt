package com.nzzima.secretmessanger.lock

import com.nzzima.secretmessanger.lock.domain.models.ReturnLock
import com.nzzima.secretmessanger.utils.constants.Constants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правило возврата: запирать ли приложение снова.
 *
 * Здесь два числа и одно решение, и ошибка в нём стоит дорого в обе стороны — либо замок
 * спрашивают по десять раз на дню и его выключают целиком, либо не спрашивают вовсе.
 */
class ReturnLockTest {

    private val now = 1_000_000L

    @Test
    fun `короткая отлучка не запирает`() {
        val away = now - Constants.LOCK_GRACE_MS / 2

        assertFalse("сходить за кодом из СМС — это не «ушёл»", ReturnLock.shouldLock(true, away, now))
    }

    @Test
    fun `долгая отлучка запирает`() {
        val away = now - Constants.LOCK_GRACE_MS - 1

        assertTrue(ReturnLock.shouldLock(true, away, now))
    }

    @Test
    fun `переведённые назад часы запирают`() {
        assertTrue("ушли на неизвестный срок — безопаснее спросить", ReturnLock.shouldLock(true, now + 5_000, now))
    }

    @Test
    fun `уход не с рабочего окна не запирает`() {
        assertFalse(
            "на замке и на входе запирать нечего, а переход сбросил бы введённый пароль",
            ReturnLock.shouldLock(wasReady = false, leftAt = now - Constants.LOCK_GRACE_MS * 10, now = now),
        )
    }

    @Test
    fun `не уходили — не запираем`() {
        assertFalse(ReturnLock.shouldLock(wasReady = true, leftAt = null, now = now))
    }
}
