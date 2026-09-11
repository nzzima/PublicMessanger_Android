package com.nzzima.secretmessanger.main.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.nzzima.secretmessanger.ui.theme.SecretMessangerTheme

/**
 * Единственная Activity приложения. Экраны — назначения графа [AppNavHost].
 *
 * `FragmentActivity`, а не `ComponentActivity`: системный запрос подтверждения владельца
 * строится только на ней. Для Compose разницы нет — она наследник той же самой.
 */
class RootActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideFromRecents()

        setContent {
            SecretMessangerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
                    AppNavHost(modifier = Modifier.padding(insets))
                }
            }
        }
    }

    /**
     * Убирает снимок приложения из переключателя задач.
     *
     * Система делает его сама при уходе в фон, и на нём остаётся открытая переписка — замок
     * от этого не спасает вовсе: снимок виден до того, как приложение вообще запустят.
     *
     * Скрывается **только снимок**, а не экран целиком: `FLAG_SECURE` заодно запретил бы
     * человеку делать скриншоты собственной переписки, а это его право, а не утечка. Ниже
     * тридцать третьего API выбора нет — там остаётся либо всё, либо ничего, и мы выбираем
     * ничего: запрещать скриншоты ради снимка в переключателе — плата не по размеру.
     */
    private fun hideFromRecents() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
    }
}
