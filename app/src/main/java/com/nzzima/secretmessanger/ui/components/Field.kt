package com.nzzima.secretmessanger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzzima.secretmessanger.ui.theme.Accent
import com.nzzima.secretmessanger.ui.theme.Ink
import com.nzzima.secretmessanger.ui.theme.InkDim
import com.nzzima.secretmessanger.ui.theme.Raised

/**
 * Поле ввода: высота 50, скругление 15, фон [Raised] — как `TextField` на iOS.
 *
 * Умолчания собраны под поля авторизации: одна строка, без автозамены и заглавных.
 * Переписка задаёт своё — там пишут предложения, а не логины.
 *
 * @param maxLines больше одной строки — поле растёт вниз от той же высоты 50.
 */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    autoCorrect: Boolean = false,
    maxLines: Int = 1,
) {
    val singleLine = maxLines == 1

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = TextStyle(color = Ink, fontSize = 16.sp),
        cursorBrush = SolidColor(Accent),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            autoCorrectEnabled = autoCorrect,
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(if (singleLine) Modifier.height(FIELD_HEIGHT) else Modifier.heightIn(min = FIELD_HEIGHT))
            .background(Raised, RoundedCornerShape(FIELD_CORNER)),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (singleLine) Modifier.fillMaxHeight() else Modifier.padding(vertical = FIELD_INNER_PADDING))
                    .padding(horizontal = FIELD_INNER_PADDING),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = InkDim, fontSize = 16.sp)
                }
                inner()
            }
        },
    )
}

private val FIELD_HEIGHT = 50.dp
private val FIELD_CORNER = 15.dp
private val FIELD_INNER_PADDING = 10.dp
