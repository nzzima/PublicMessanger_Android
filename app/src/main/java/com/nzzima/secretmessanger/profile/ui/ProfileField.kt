package com.nzzima.secretmessanger.profile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzzima.secretmessanger.ui.theme.Ink
import com.nzzima.secretmessanger.ui.theme.InkDim

/**
 * Строка профиля: подпись, значение и разделитель под ними.
 *
 * Общая у своего профиля и чужого: поля в них разные, а вид один.
 *
 * @param monospaced для технических строк, которые не читают, а сверяют.
 */
@Composable
internal fun ProfileField(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    monospaced: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = title, color = InkDim, fontSize = 13.sp)
        Text(
            text = value,
            color = Ink,
            style = if (monospaced) {
                TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            } else {
                TextStyle(fontSize = 17.sp)
            },
            modifier = Modifier.padding(top = 2.dp),
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
