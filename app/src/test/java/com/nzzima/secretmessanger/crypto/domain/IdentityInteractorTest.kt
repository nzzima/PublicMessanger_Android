package com.nzzima.secretmessanger.crypto.domain

import com.nzzima.secretmessanger.crypto.data.impl.IdentityKeyStoreImpl
import com.nzzima.secretmessanger.crypto.domain.impl.IdentityInteractorImpl
import com.nzzima.secretmessanger.crypto.domain.models.IdentityState
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Развилка ключа при входе — все пять строк таблицы решений.
 *
 * Большинство проверок утверждают **отсутствие** действия: чужая половина в профиле не
 * затирается и ключ на устройстве не заводится, пока подтверждения не было.
 */
class IdentityInteractorTest {

    private val keys = IdentityKeyStoreImpl(FakeSharedPreferences(), FakeMasterKeyProvider())
    private val publicKeys = FakePublicKeyRepository()
    private val interactor = IdentityInteractorImpl(keys, publicKeys)

    private fun publicOf(identityPrivate: ByteArray) =
        Base64.getEncoder().encodeToString(CryptoBox.publicKey(identityPrivate))

    private fun strangerKey() = publicOf(CryptoBox.newIdentityPrivateKey())

    @Test
    fun `ключа нет нигде — заводится и публикуется молча`() = runTest {
        val state = interactor.prepare("uid-1").getOrThrow()

        assertSame(IdentityState.Ready, state)
        val created = keys.existing("uid-1")
        assertTrue("ключ обязан появиться", created != null)
        assertEquals(publicOf(requireNotNull(created)), publicKeys.stored["uid-1"])
    }

    @Test
    fun `ключ есть, в профиле пусто — публикуется молча, новый не заводится`() = runTest {
        val existing = keys.createNew("uid-1")

        val state = interactor.prepare("uid-1").getOrThrow()

        assertSame(IdentityState.Ready, state)
        assertEquals(publicOf(existing), publicKeys.stored["uid-1"])
        assertTrue("прежний ключ не должен меняться", existing.contentEquals(keys.existing("uid-1")))
    }

    @Test
    fun `в профиле наша же половина — не публикуется повторно`() = runTest {
        val existing = keys.createNew("uid-1")
        publicKeys.stored["uid-1"] = publicOf(existing)
        publicKeys.publishes = 0

        val state = interactor.prepare("uid-1").getOrThrow()

        assertSame(IdentityState.Ready, state)
        assertEquals("публиковать своё же незачем", 0, publicKeys.publishes)
    }

    @Test
    fun `ключ есть, в профиле чужая половина — спрашивает и ничего не трогает`() = runTest {
        val existing = keys.createNew("uid-1")
        val stranger = strangerKey()
        publicKeys.stored["uid-1"] = stranger
        publicKeys.publishes = 0

        val state = interactor.prepare("uid-1").getOrThrow()

        assertSame(IdentityState.NeedsConfirmation, state)
        assertEquals("чужая половина обязана уцелеть", stranger, publicKeys.stored["uid-1"])
        assertEquals(0, publicKeys.publishes)
        assertTrue(existing.contentEquals(keys.existing("uid-1")))
    }

    @Test
    fun `ключа нет, в профиле чужая половина — спрашивает и ключ не заводит`() = runTest {
        val stranger = strangerKey()
        publicKeys.stored["uid-1"] = stranger

        val state = interactor.prepare("uid-1").getOrThrow()

        assertSame(IdentityState.NeedsConfirmation, state)
        assertNull("ключ не должен заводиться до подтверждения", keys.existing("uid-1"))
        assertEquals(stranger, publicKeys.stored["uid-1"])
    }

    @Test
    fun `профиль не прочитан — отказ, и ничего не публикуется`() = runTest {
        publicKeys.readFails = IllegalStateException("client is offline")

        val result = interactor.prepare("uid-1")

        assertTrue(result.isFailure)
        assertNull(keys.existing("uid-1"))
        assertEquals(0, publicKeys.publishes)
    }

    @Test
    fun `подтверждение публикует свой ключ поверх чужого`() = runTest {
        val existing = keys.createNew("uid-1")
        publicKeys.stored["uid-1"] = strangerKey()

        interactor.publishOverwriting("uid-1").getOrThrow()

        assertEquals(publicOf(existing), publicKeys.stored["uid-1"])
        assertTrue("свой ключ обязан остаться прежним", existing.contentEquals(keys.existing("uid-1")))
    }

    @Test
    fun `подтверждение без своего ключа заводит новый и публикует его`() = runTest {
        val stranger = strangerKey()
        publicKeys.stored["uid-1"] = stranger

        interactor.publishOverwriting("uid-1").getOrThrow()

        val created = requireNotNull(keys.existing("uid-1"))
        assertEquals(publicOf(created), publicKeys.stored["uid-1"])
        assertNotEquals(stranger, publicKeys.stored["uid-1"])
    }

    @Test
    fun `отказ публикации возвращается как есть`() = runTest {
        publicKeys.publishFails = IllegalStateException("PERMISSION_DENIED")

        val result = interactor.prepare("uid-1")

        assertEquals("PERMISSION_DENIED", result.exceptionOrNull()?.message)
    }
}

/** [PublicKeyRepository] в памяти. Считает публикации, чтобы ловить лишние. */