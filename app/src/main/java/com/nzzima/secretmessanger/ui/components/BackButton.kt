package com.nzzima.secretmessanger.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzzima.secretmessanger.ui.theme.Accent
import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Кнопка возврата: шеврон и имя экрана, **с которого пришли**.
 *
 * Подпись именно предыдущего экрана, как на iOS. Одного шеврона мало: он стоит вплотную к
 * заголовку, и связка читалась как «назад к nzzima», хотя вела в «Контакты». Поэтому же
 * заголовок на таких экранах уходит в центр — иначе подпись и заголовок сливаются в одну
 * строку.
 *
 * @param title имя предыдущего экрана; для экранов без имени — «Назад».
 */
@Composable
fun BackButton(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clickable(onClick = onBack).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BackIcon, contentDescription = Constants.BACK, tint = Accent)

        Text(
            text = title,
            color = Accent,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
