package com.nzzima.secretmessanger.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzzima.secretmessanger.photo.data.impl.PhotoEncoderImpl
import com.nzzima.secretmessanger.utils.constants.Constants
import java.io.File
import java.util.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Кодировщик снимка на живом устройстве.
 *
 * Проверяет то, чего не достаёт JVM-тестам: `BitmapFactory`, `Matrix` и `ExifInterface`
 * существуют только на устройстве. Главное здесь — **бюджет**: документ Firestore держит
 * миллион байт, и снимок, который в него не уложился, потерян вместе с отправкой.
 */
@RunWith(AndroidJUnit4::class)
class PhotoEncoderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val encoder = PhotoEncoderImpl(context)
    private val files = mutableListOf<File>()

    @After
    fun tearDown() = files.forEach { it.delete() }

    @Test
    fun photoSizedImageFitsTheBudget() {
        // Шум во весь кадр — худшее, что бывает для JPEG: ровные заливки сжимаются в
        // килобайты и бюджет не проверяют вовсе.
        val source = file(noise(3000, 4000))

        val encoded = requireNotNull(runBlocking { encoder.encode(source) })

        assertTrue("бюджет держит правило Firestore", encoded.bytes.size <= Constants.PHOTO_BUDGET)
        assertEquals(Constants.PHOTO_SIDE, maxOf(encoded.size.width, encoded.size.height))
    }

    @Test
    fun proportionsSurviveTheFit() {
        val encoded = requireNotNull(runBlocking { encoder.encode(file(noise(2000, 1000))) })

        assertEquals("снимок вписывается, а не обрезается, как аватар", 2f, encoded.size.ratio(), 0.01f)
        assertEquals(Constants.PHOTO_SIDE, encoded.size.width)
        assertEquals(Constants.PHOTO_SIDE / 2, encoded.size.height)
    }

    @Test
    fun smallImageIsNotStretched() {
        val encoded = requireNotNull(runBlocking { encoder.encode(file(noise(300, 200))) })

        assertEquals(300, encoded.size.width)
        assertEquals(200, encoded.size.height)
    }

    @Test
    fun exifMarkTurnsThePicture() {
        // Вертикальный кадр с меткой «повернуть на 90» становится горизонтальным.
        val source = file(noise(600, 1200), orientation = ExifInterface.ORIENTATION_ROTATE_90)

        val encoded = requireNotNull(runBlocking { encoder.encode(source) })

        assertTrue("снятое боком уехало бы набок", encoded.size.width > encoded.size.height)
    }

    @Test
    fun unreadableFileGivesNothing() {
        val broken = File(context.cacheDir, "broken.jpg").apply { writeText("никакой не jpeg") }
        files += broken

        assertNull(runBlocking { encoder.encode(Uri.fromFile(broken).toString()) })
    }

    private fun com.nzzima.secretmessanger.photo.domain.models.PhotoSize.ratio(): Float =
        width.toFloat() / height

    /** Кадр из случайных пикселей: сжимается плохо и бюджет проверяет по-честному. */
    private fun noise(width: Int, height: Int): Bitmap {
        val random = Random(SEED)
        val pixels = IntArray(width * height) { Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** Кладёт кадр в файл и отдаёт его адрес — тот же вид, в каком его даёт выборщик. */
    private fun file(bitmap: Bitmap, orientation: Int? = null): String {
        val file = File(context.cacheDir, "photo-${files.size}.jpg")
        files += file

        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }

        orientation?.let {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, it.toString())
                saveAttributes()
            }
        }

        return Uri.fromFile(file).toString()
    }

    private companion object {
        const val SEED = 42L
    }
}
