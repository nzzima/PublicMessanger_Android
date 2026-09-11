package com.nzzima.secretmessanger.avatar.data.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.nzzima.secretmessanger.avatar.domain.api.AvatarEncoder
import com.nzzima.secretmessanger.utils.constants.Constants
import com.nzzima.secretmessanger.utils.media.sampleSize
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [AvatarEncoder] поверх `BitmapFactory`.
 *
 * Кодирование уходит с главного потока: снимок с камеры — это мегапиксели, и перекодирование
 * заметно подвешивало бы интерфейс.
 */
class AvatarEncoderImpl(private val context: Context) : AvatarEncoder {

    override suspend fun encode(source: String): ByteArray? = withContext(Dispatchers.Default) {
        val uri = Uri.parse(source)
        val decoded = decode(uri) ?: return@withContext null
        val square = squared(rotated(decoded, uri))

        // Качество понижается ступенями, пока картинка не уложится в бюджет. Первая ступень
        // обычно и последняя: 320×320 в JPEG — это 30–40 КБ при потолке в 200 КБ.
        QUALITIES.firstNotNullOfOrNull { quality ->
            ByteArrayOutputStream().use { stream ->
                square.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                stream.toByteArray().takeIf { it.size <= Constants.AVATAR_BUDGET }
            }
        }
    }

    /**
     * Картинка из выборщика, уменьшенная при чтении.
     *
     * `inSampleSize` считается по первому проходу без пикселей: без него в память ехал бы
     * снимок целиком — двенадцать мегапикселей ради кружка в 320 точек.
     */
    private fun decode(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val smaller = minOf(bounds.outWidth, bounds.outHeight)
        if (smaller <= 0) return null

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(smaller, Constants.AVATAR_SIDE) }

        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    /**
     * Разворот по метке EXIF.
     *
     * Снятое боком иначе уехало бы набок: камера пишет пиксели как есть, а поворот держит
     * отдельным полем. iOS разворачивает картинку сам при отрисовке, Android — нет.
     */
    private fun rotated(bitmap: Bitmap, uri: Uri): Bitmap {
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }

        val matrix = Matrix().apply { postRotate(degrees) }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Квадрат по центру, сжатый до стороны аватара. */
    private fun squared(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(
            bitmap,
            (bitmap.width - side) / 2,
            (bitmap.height - side) / 2,
            side,
            side,
        )

        if (side <= Constants.AVATAR_SIDE) return square

        return square.scale(Constants.AVATAR_SIDE)
    }

    private fun Bitmap.scale(side: Int): Bitmap = Bitmap.createScaledBitmap(this, side, side, true)

    private companion object {
        val QUALITIES = listOf(80, 60, 40)
    }
}
