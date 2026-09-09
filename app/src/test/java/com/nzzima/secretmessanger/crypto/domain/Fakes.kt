package com.nzzima.secretmessanger.crypto.domain

import android.content.SharedPreferences
import com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys
import com.nzzima.secretmessanger.crypto.domain.api.MasterKeyProvider
import com.nzzima.secretmessanger.crypto.domain.api.PublicKeyRepository
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * [MasterKeyProvider] на обычном ключе AES вместо аппаратного хранилища.
 *
 * Хранилище Android на JVM отсутствует, а шифрование от происхождения ключа не зависит:
 * [com.nzzima.secretmessanger.crypto.data.impl.IdentityKeyStoreImpl] получает `SecretKey` и
 * не знает, кто его выдал. Проверку самого хранилища закрывают инструментальные тесты.
 */
class FakeMasterKeyProvider(
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey(),
) : MasterKeyProvider {

    override fun masterKey(): SecretKey = key
}

/**
 * [SharedPreferences] в памяти.
 *
 * Реализованы только строки: больше [com.nzzima.secretmessanger.crypto.data.impl.IdentityKeyStoreImpl]
 * ничего не пишет и не читает. Остальные методы интерфейса бросают исключение — вызов
 * любого из них означал бы, что хранилище стало делать что-то ещё, и тест обязан упасть.
 */
class FakeSharedPreferences : SharedPreferences {

    private val values = mutableMapOf<String, String>()

    override fun getString(key: String, defValue: String?): String? = values[key] ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun edit(): SharedPreferences.Editor = Editor()

    private inner class Editor : SharedPreferences.Editor {

        private val pending = mutableMapOf<String, String?>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
            pending[key] = value
        }

        override fun remove(key: String): SharedPreferences.Editor = apply { pending[key] = null }

        override fun clear(): SharedPreferences.Editor = apply {
            values.keys.forEach { pending[it] = null }
        }

        override fun apply() {
            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            pending.clear()
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun putStringSet(key: String, values: MutableSet<String>?) = unsupported()
        override fun putInt(key: String, value: Int) = unsupported()
        override fun putLong(key: String, value: Long) = unsupported()
        override fun putFloat(key: String, value: Float) = unsupported()
        override fun putBoolean(key: String, value: Boolean) = unsupported()
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?) = unsupported()
    override fun getInt(key: String, defValue: Int) = unsupported()
    override fun getLong(key: String, defValue: Long) = unsupported()
    override fun getFloat(key: String, defValue: Float) = unsupported()
    override fun getBoolean(key: String, defValue: Boolean) = unsupported()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = unsupported()

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("хранилище ключа пишет только строки")
}

/** Открытые половины в памяти: uid → base64. */
class FakePublicKeyRepository : PublicKeyRepository {

    val stored = mutableMapOf<String, String>()
    var publishes = 0
    var readFails: Throwable? = null
    var publishFails: Throwable? = null

    override suspend fun published(uid: String): Result<String?> =
        readFails?.let { Result.failure(it) } ?: Result.success(stored[uid])

    override suspend fun publish(uid: String, publicKey: String): Result<Unit> {
        publishFails?.let { return Result.failure(it) }
        publishes++
        stored[uid] = publicKey
        return Result.success(Unit)
    }
}

/**
 * Ключей диалогов нет ни у кого.
 *
 * Для моделей экранов: расшифровка и раздача — дело интерактора, и подсовывать им живой
 * крипто-слой значило бы проверять его во второй раз.
 */
class FakeConversationKeys : ConversationKeys {

    override fun open(convoId: String, uid: String, version: Int, entries: Map<String, String>): ByteArray? = null

    override fun sealNew(convoId: String, publicKeys: Map<String, String>): Map<String, String>? = null
}
