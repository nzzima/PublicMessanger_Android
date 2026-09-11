package com.nzzima.secretmessanger.utils.media

/**
 * Кэш картинок в памяти с вытеснением самой давней.
 *
 * `LinkedHashMap` в порядке обращения вместо `android.util.LruCache`: домен обходится без
 * платформенных классов, а заодно проверяется обычными JVM-тестами, без заглушек Android.
 */
internal class LruBytes(private val limit: Int) : LinkedHashMap<String, ByteArray>(CAPACITY, LOAD, true) {

    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>): Boolean = size > limit

    private companion object {
        const val CAPACITY = 16
        const val LOAD = 0.75f
    }
}
