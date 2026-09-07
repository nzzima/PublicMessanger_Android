package com.nzzima.secretmessanger.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nzzima.secretmessanger.ui.theme.Ink
import com.nzzima.secretmessanger.ui.theme.InkDim
import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Сообщение о том, что вход устарел, и единственный выход с экрана.
 *
 * Кнопки «Повторить» здесь нет намеренно: сессия мертва окончательно, и повтор запроса не
 * пройдёт ни сейчас, ни позже. [onSignIn] завершает мёртвую сессию и открывает экран входа.
 */
@Composable
fun SessionExpiredScreen(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = SIDE_PADDING),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = Constants.SESSION_EXPIRED_TITLE,
            color = Ink,
            style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 24.sp, letterSpacing = 1.5.sp),
        )

        Text(
            text = Constants.SESSION_EXPIRED_EXPLANATION,
            color = InkDim,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )

        Button(
            onClick = onSignIn,
            modifier = Modifier.padding(top = 32.dp).height(BUTTON_HEIGHT).width(BUTTON_WIDTH),
            shape = RoundedCornerShape(BUTTON_CORNER),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        ) {
            Text(Constants.SESSION_EXPIRED_SUBMIT, fontSize = 15.sp, maxLines = 1)
        }
    }
}

private val SIDE_PADDING = 30.dp
private val BUTTON_HEIGHT = 40.dp
private val BUTTON_WIDTH = 260.dp
private val BUTTON_CORNER = 14.dp
