package com.nzzima.secretmessanger.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nzzima.secretmessanger.avatar.data.impl.AvatarEncoderImpl
import com.nzzima.secretmessanger.utils.constants.Constants
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Кодировщик аватара на живом устройстве.
 *
 * Проверяет то, чего не достаёт JVM-тестам: `BitmapFactory`, `Matrix` и `ExifInterface`
 * существуют только на устройстве, а обрезка, разворот и бюджет живут именно в них.
 * Счёт уменьшения при чтении закрыт отдельно и без устройства — `AvatarSampleSizeTest`.
 */
@RunWith(AndroidJUnit4::class)
class AvatarEncoderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val encoder = AvatarEncoderImpl(context)
    private val files = mutableListOf<File>()

    @After
    fun tearDown() = files.forEach { it.delete() }

    @Test
    fun wideImageIsCroppedToCentreSquare() {
        // Красное лежит ровно за границами центрального квадрата кадра 1600×900. Обрезка
        // оставит зелёное до самого края; сжатие в квадрат затащило бы красное внутрь.
        val source = file(
            bitmap(1600, 900) { x, _ -> if (x < 350 || x >= 1250) Color.RED else Color.GREEN },
        )

        val avatar = decoded(source)

        assertEquals(Constants.AVATAR_SIDE, avatar.width)
        assertEquals(Constants.AVATAR_SIDE, avatar.height)
        assertTrue("красное осталось за квадратом", avatar.isGreenAt(4, 160))
        assertTrue(avatar.isGreenAt(315, 160))
    }

    @Test
    fun smallImageIsNotStretched() {
        val avatar = decoded(file(bitmap(200, 200) { _, _ -> Color.GREEN }))

        assertEquals("растянутое лицо хуже мелкого", 200, avatar.width)
        assertEquals(200, avatar.height)
    }

    @Test
    fun exifMarkTurnsThePicture() {
        // Верхняя половина пикселей жёлтая. Поворот на 90 по часовой уводит её вправо —
        // без поворота она осталась бы сверху.
        val source = file(
            bitmap(600, 1200) { _, y -> if (y < 600) Color.YELLOW else Color.BLUE },
            orientation = ExifInterface.ORIENTATION_ROTATE_90,
        )

        val avatar = decoded(source)

        assertTrue("левая половина — низ снимка", avatar.isBlueAt(80, 160))
        assertTrue("правая половина — верх снимка", avatar.isYellowAt(240, 160))
    }

    @Test
    fun photoSizedImageFitsTheBudget() {
        val source = file(bitmap(2400, 1800) { x, y -> Color.rgb(x % 256, y % 256, (x + y) % 256) })

        val bytes = requireNotNull(runBlocking { encoder.encode(source) })

        assertTrue("потолок держит правило Firestore", bytes.size <= Constants.AVATAR_BUDGET)
        assertEquals(Constants.AVATAR_SIDE, BitmapFactory.decodeByteArray(bytes, 0, bytes.size).width)
    }

    @Test
    fun unreadableFileGivesNothing() {
        val broken = File(context.cacheDir, "broken.jpg").apply { writeText("никакой не jpeg") }
        files += broken

        assertNull(runBlocking { encoder.encode(android.net.Uri.fromFile(broken).toString()) })
    }

    private fun decoded(source: String): Bitmap {
        val bytes = requireNotNull(runBlocking { encoder.encode(source) })

        return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            .also { assertNotNull(it) }
    }

    private fun bitmap(width: Int, height: Int, color: (Int, Int) -> Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) bitmap.setPixel(x, y, color(x, y))
        }

        return bitmap
    }

    /** Кладёт картинку в файл и отдаёт её адрес — тот же вид, в каком его даёт выборщик. */
    private fun file(bitmap: Bitmap, orientation: Int? = null): String {
        val file = File(context.cacheDir, "avatar-${files.size}.jpg")
        files += file

        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }

        orientation?.let {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, it.toString())
                saveAttributes()
            }
        }

        return android.net.Uri.fromFile(file).toString()
    }

    // Сравнение по преобладающему каналу: JPEG цвета сдвигает, точного совпадения не будет.
    private fun Bitmap.isGreenAt(x: Int, y: Int) = getPixel(x, y).let { Color.green(it) > Color.red(it) + 40 }

    private fun Bitmap.isBlueAt(x: Int, y: Int) = getPixel(x, y).let { Color.blue(it) > Color.red(it) + 40 }

    private fun Bitmap.isYellowAt(x: Int, y: Int) =
        getPixel(x, y).let { Color.red(it) > 150 && Color.green(it) > 150 && Color.blue(it) < 120 }
}
