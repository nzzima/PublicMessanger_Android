package com.nzzima.secretmessanger.presence.domain

import com.nzzima.secretmessanger.presence.domain.impl.PresenceInteractorImpl
import com.nzzima.secretmessanger.presence.domain.models.Presence
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тик присутствия: главное здесь — **точка гаснет сама**.
 *
 * Человек, переставший отмечаться, новых снимков не создаёт, и без тика он горел бы зелёным
 * до первого чужого удара пульса.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PresenceInteractorTest {

    private val presence = FakePresenceRepository()
    private var now = 1_000_000L
    private val interactor = PresenceInteractorImpl(presence) { now }

    @Test
    fun `точка гаснет по тику, без единого нового снимка`() = runTest {
        presence.send(mapOf("uid-2" to Presence(now)))

        val seen = mutableListOf<Set<String>>()
        val watching = launch { interactor.observeOnline().toList(seen) }
        runCurrent()

        assertEquals(setOf("uid-2"), seen.last())

        now += Constants.PRESENCE_WINDOW_MS + 1
        testScheduler.advanceTimeBy(Constants.PRESENCE_HEARTBEAT_MS + 1)
        runCurrent()

        assertTrue("молчание — тоже сведение о человеке", seen.last().isEmpty())
        watching.cancel()
    }

    @Test
    fun `пульс бьётся раз в срок и не чаще`() = runTest {
        val beating = launch { interactor.keepAlive("uid-1") }
        runCurrent()

        assertEquals("первый удар — сразу, иначе полминуты числился бы офлайном", 1, presence.beats.size)

        testScheduler.advanceTimeBy(Constants.PRESENCE_HEARTBEAT_MS * 3 + 1)
        runCurrent()

        assertEquals(listOf("uid-1", "uid-1", "uid-1", "uid-1"), presence.beats)
        beating.cancel()
    }

    @Test
    fun `отмена прекращает пульс`() = runTest {
        val beating = launch { interactor.keepAlive("uid-1") }
        runCurrent()
        beating.cancel()

        testScheduler.advanceTimeBy(Constants.PRESENCE_HEARTBEAT_MS * 5)
        runCurrent()

        assertEquals("ушедший обязан замолчать, а не отмечаться из фона", 1, presence.beats.size)
    }
}
