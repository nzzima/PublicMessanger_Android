package com.nzzima.secretmessanger.lock.ui

import android.os.Build
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.nzzima.secretmessanger.ui.theme.ErrorColor
import com.nzzima.secretmessanger.ui.theme.Ink
import com.nzzima.secretmessanger.ui.theme.InkDim
import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Замок приложения.
 *
 * Своей кнопки «выйти» здесь нет: замок стоит перед уже открытой сессией, и выйти из неё
 * можно изнутри, подтвердив себя. Запертое приложение не показывает ни переписки, ни имени —
 * только просьбу подтвердить.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }

    val ask = {
        val activity = context as? FragmentActivity

        if (activity == null) {
            error = Constants.LOCK_FAILED
        } else {
            error = null
            activity.askOwner(
                onSuccess = onUnlocked,
                onFailure = { reason -> error = reason ?: Constants.LOCK_FAILED },
            )
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = Constants.LOCK_TITLE,
            color = Ink,
            style = TextStyle(fontFamily = FontFamily.Serif, fontSize = 24.sp, letterSpacing = 1.sp),
            textAlign = TextAlign.Center,
        )

        Text(
            text = Constants.LOCK_HINT,
            color = InkDim,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )

        Button(
            onClick = ask,
            modifier = Modifier.padding(top = 40.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        ) {
            Text(Constants.LOCK_UNLOCK, fontSize = 16.sp)
        }

        error?.let {
            Text(
                text = it,
                color = ErrorColor,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/**
 * Системный запрос подтверждения.
 *
 * **Мерка та же, что у проверки доступности** — иначе замок запирает наглухо: на iOS ровно
 * это и случилось 24.08.2026, когда проверяли «биометрия или код-пароль», а спрашивали
 * «только биометрия». Человек без настроенного отпечатка получал «попробуйте снова» столько
 * раз, сколько готов был нажимать.
 *
 * Отказ человека («Отмена») ошибкой не считается: это не промах, а решение остаться снаружи.
 */
private fun FragmentActivity.askOwner(onSuccess: () -> Unit, onFailure: (String?) -> Unit) {
    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

            override fun onAuthenticationError(code: Int, message: CharSequence) {
                val cancelled = code == BiometricPrompt.ERROR_USER_CANCELED ||
                    code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    code == BiometricPrompt.ERROR_CANCELED

                onFailure(if (cancelled) null else message.toString())
            }
        },
    )

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(Constants.LOCK_TITLE)
        .setSubtitle(Constants.LOCK_HINT)
        .apply {
            // Пара «биометрия + код-пароль» одним доводом выражается только с тридцатого API;
            // ниже код-пароль подключается отдельной настройкой, а своей кнопки отказа там
            // быть не должно — система ставит её сама.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
            } else {
                @Suppress("DEPRECATION")
                setDeviceCredentialAllowed(true)
            }
        }
        .build()

    prompt.authenticate(info)
}
