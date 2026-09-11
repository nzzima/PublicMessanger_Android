package com.nzzima.secretmessanger.photo.data.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.nzzima.secretmessanger.photo.domain.api.PhotoEncoder
import com.nzzima.secretmessanger.photo.domain.models.EncodedPhoto
import com.nzzima.secretmessanger.photo.domain.models.PhotoSize
import com.nzzima.secretmessanger.utils.constants.Constants
import com.nzzima.secretmessanger.utils.media.sampleSize
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PhotoEncoder] поверх `BitmapFactory`.
 *
 * Кодирование уходит с главного потока: снимок с камеры — это мегапиксели, и перекодирование
 * заметно подвешивало бы интерфейс.
 */
class PhotoEncoderImpl(private val context: Context) : PhotoEncoder {

    override suspend fun encode(source: String): EncodedPhoto? = withContext(Dispatchers.Default) {
        val uri = Uri.parse(source)
        val decoded = decode(uri) ?: return@withContext null
        val upright = rotated(decoded, uri)

        // Три захода: сторона, качество, снова сторона вдвое меньше. Порядок не случайный —
        // качество дешевле размера, и уполовинивать кадр стоит только когда качество уже не
        // спасает. Первый заход обычно и последний.
        var side = Constants.PHOTO_SIDE

        repeat(PASSES) {
            val scaled = scaled(upright, side)

            QUALITIES.firstNotNullOfOrNull { quality -> compressed(scaled, quality) }
                ?.let { bytes -> return@withContext EncodedPhoto(bytes, PhotoSize(scaled.width, scaled.height)) }

            side /= 2
        }

        null
    }

    /** Снимок, уменьшенный при чтении: целиком в память не поместился бы и не понадобился. */
    private fun decode(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val longer = maxOf(bounds.outWidth, bounds.outHeight)
        if (longer <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(longer, Constants.PHOTO_SIDE)
        }

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

    /** Кадр, вписанный в квадрат стороной [side] без обрезки: пропорции снимка сохраняются. */
    private fun scaled(bitmap: Bitmap, side: Int): Bitmap {
        val longer = maxOf(bitmap.width, bitmap.height)
        if (longer <= side) return bitmap

        val ratio = side.toFloat() / longer

        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun compressed(bitmap: Bitmap, quality: Int): ByteArray? =
        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            stream.toByteArray().takeIf { it.size <= Constants.PHOTO_BUDGET }
        }

    private companion object {
        val QUALITIES = listOf(70, 50, 35)
        const val PASSES = 3
    }
}
