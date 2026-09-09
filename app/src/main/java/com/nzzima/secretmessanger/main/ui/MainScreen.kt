package com.nzzima.secretmessanger.main.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nzzima.secretmessanger.chats.ui.ChatsScreen
import com.nzzima.secretmessanger.contacts.ui.ContactsScreen
import com.nzzima.secretmessanger.messanger.ui.MessangerScreen
import com.nzzima.secretmessanger.profile.ui.ProfileScreen
import com.nzzima.secretmessanger.ui.theme.InkDim
import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Рабочее состояние приложения: три вкладки и свой граф внутри.
 *
 * Граф здесь **отдельный** от [AppNavHost]. Тот выбирает назначение по [RootState] и чистит
 * стек на каждом переходе — состояния оболочки не складываются в историю. Внутри вкладок
 * история, наоборот, нужна: экран переписки открывается поверх списка и возвращает назад.
 *
 * Переключение вкладок стек не копит: назначение стартовой вкладки остаётся в основании, а
 * повторное нажатие по текущей вкладке возвращает её в исходное состояние.
 *
 * Панель вкладок живёт только на самих вкладках: в переписке её место занимает панель
 * ввода, и на iOS таб-бар там тоже скрыт.
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == currentRoute }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Отступы отдаются экранам целиком: вкладки берут сверху статус-бар, переписка
        // снизу — клавиатуру с навигационной полосой. Отсюда остаётся только высота
        // панели вкладок, когда она есть.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentTab != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { navController.switchTo(tab) },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = InkDim,
                                unselectedTextColor = InkDim,
                                indicatorColor = MaterialTheme.colorScheme.background,
                            ),
                        )
                    }
                }
            }
        },
    ) { insets ->
        NavHost(
            navController = navController,
            startDestination = Tab.Contacts.route,
            modifier = Modifier.fillMaxSize().padding(bottom = insets.calculateBottomPadding()),
        ) {
            composable(Tab.Contacts.route) {
                ContactsScreen(onOpen = { convoId -> navController.navigate(messangerRoute(convoId)) })
            }
            composable(Tab.Chats.route) {
                ChatsScreen(onOpen = { convoId -> navController.navigate(messangerRoute(convoId)) })
            }
            composable(Tab.Profile.route) { ProfileScreen() }
            composable(
                route = messangerRoute("{${Constants.CONVO_ID_ARGUMENT}}"),
                arguments = listOf(navArgument(Constants.CONVO_ID_ARGUMENT) { type = NavType.StringType }),
            ) { entry ->
                MessangerScreen(
                    convoId = entry.arguments?.getString(Constants.CONVO_ID_ARGUMENT).orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/** Назначение переписки: диалог задаётся аргументом пути. */
private fun messangerRoute(convoId: String) = "${Constants.MESSANGER_ROUTE}/$convoId"

/**
 * Переход на вкладку [tab].
 *
 * `launchSingleTop` не даёт положить вторую копию той же вкладки, `popUpTo` со стартового
 * назначения — накапливать цепочку из вкладок: возврат из любой ведёт к стартовой, а не
 * перебирает историю переключений.
 */
private fun NavHostController.switchTo(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
