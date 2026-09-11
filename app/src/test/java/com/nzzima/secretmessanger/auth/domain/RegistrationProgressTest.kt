package com.nzzima.secretmessanger.auth.domain

import com.nzzima.secretmessanger.auth.domain.impl.RegistrationProgressImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Признак идущей регистрации: ждущий обязан дождаться конца — и обязан дождаться в любом
 * исходе, потому что незакрытая пометка оставила бы оболочку ждать вечно.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationProgressTest {

    private val progress = RegistrationProgressImpl()

    @Test
    fun `ожидание кончается вместе с регистрацией, а не раньше`() = runTest {
        val journal = mutableListOf<String>()

        val waiting = launch {
            progress.awaitIdle()
            journal += "дождались"
        }

        progress.whileRegistering { journal += "профиль записан" }
        waiting.join()

        assertEquals(listOf("профиль записан", "дождались"), journal)
    }

    @Test
    fun `свободный признак не задерживает никого`() = runTest {
        progress.awaitIdle()

        assertEquals("обычный вход не должен платить за это ожидание", 0, testScheduler.currentTime)
    }

    @Test
    fun `отказ регистрации снимает пометку`() = runTest {
        val failed = runCatching {
            progress.whileRegistering { throw IllegalStateException("нет связи") }
        }
        val waiting = launch { progress.awaitIdle() }

        assertTrue(failed.isFailure)
        waiting.join()
        assertTrue("после отказа ждать больше нечего", waiting.isCompleted)
    }

    @Test
    fun `итог блока возвращается как есть`() = runTest {
        assertEquals("uid-1", progress.whileRegistering { "uid-1" })
    }
}
