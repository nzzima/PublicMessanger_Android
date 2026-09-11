package com.nzzima.secretmessanger.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.nzzima.secretmessanger.ui.theme.InkDim
import com.nzzima.secretmessanger.utils.constants.Constants
import com.nzzima.secretmessanger.ui.theme.Raised

/**
 * Кружок с аватаром, а без него — с первой буквой имени.
 *
 * Заглушка буквой, а не картинкой: на iOS в бандле лежит `basicUserImage.jpg`, но безликий
 * силуэт в списке из десяти человек не помогает никому — буква хотя бы различает строки.
 *
 * Байты декодируются один раз на пару «картинка + размер»: JPEG 320×320 разбирается быстро,
 * но делать это на каждую перерисовку списка незачем.
 *
 * @param image байты JPEG либо `null` — аватара нет или он ещё не загрузился.
 */
@Composable
fun Avatar(name: String, image: ByteArray?, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(image) {
        image?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }

    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Raised),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                text = name.take(1).uppercase(),
                color = InkDim,
                fontSize = size.letterSize(),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Буква занимает чуть меньше половины кружка — так же смотрится и в 44, и в 120 точках. */
private fun Dp.letterSize(): TextUnit = (value * LETTER_SHARE).sp

private const val LETTER_SHARE = 0.42f

/**
 * Кружок группы: значок вместо лица.
 *
 * Фотографии у группы быть не может — показывать одного участника из нескольких значило бы
 * врать, а буква первого врала бы вдвойне. Заодно значок отличает группу от диалога на двоих
 * с одного взгляда: раньше список этого не умел. Так же на iOS.
 */
@Composable
fun GroupAvatar(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Raised),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = GroupIcon,
            contentDescription = Constants.GROUP_CHAT,
            tint = InkDim,
            modifier = Modifier.size(size * ICON_SHARE),
        )
    }
}

/** Значок занимает чуть больше половины кружка — как буква в [Avatar], только шире. */
private const val ICON_SHARE = 0.58f
