package com.nzzima.secretmessanger.main.ui

import android.net.Uri
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
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
import com.nzzima.secretmessanger.profile.ui.EditProfileScreen
import com.nzzima.secretmessanger.profile.ui.ProfileScreen
import com.nzzima.secretmessanger.profile.ui.UserProfileScreen
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
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    // Высота задаётся явно: у Material3 она 80 точек — заметно выше, чем
                    // таб-бар iOS, и этот запас отъедал ленту на каждом экране.
                    modifier = Modifier.height(TAB_BAR_HEIGHT),
                    // Свой отступ под системную полосу панель не ставит: его уже поставил
                    // корневой Scaffold. Со вторым внутри панели оставалась пустая полоса
                    // в полсотни точек, из-за которой она и выглядела громоздкой.
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
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
                ContactsScreen(
                    onOpen = { contact -> navController.navigate(userRoute(contact.id, contact.login)) },
                )
            }
            composable(Tab.Chats.route) {
                ChatsScreen(onOpen = { convoId -> navController.navigate(messangerRoute(convoId)) })
            }
            composable(Tab.Profile.route) {
                ProfileScreen(onEdit = { navController.navigate(Constants.EDIT_ROUTE) })
            }
            composable(Constants.EDIT_ROUTE) {
                EditProfileScreen(
                    backTitle = remember { navController.backTitle() },
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                route = userRoute("{${Constants.USER_ID_ARGUMENT}}", "{${Constants.LOGIN_ARGUMENT}}"),
                arguments = listOf(
                    navArgument(Constants.USER_ID_ARGUMENT) { type = NavType.StringType },
                    // Имя необязательно: без него заголовок просто пуст до прихода профиля.
                    navArgument(Constants.LOGIN_ARGUMENT) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                UserProfileScreen(
                    userId = entry.arguments?.getString(Constants.USER_ID_ARGUMENT).orEmpty(),
                    login = entry.arguments?.getString(Constants.LOGIN_ARGUMENT).orEmpty(),
                    backTitle = remember { navController.backTitle() },
                    onBack = { navController.popBackStack() },
                    onWrite = { convoId -> navController.navigate(messangerRoute(convoId)) },
                )
            }
            composable(
                route = messangerRoute("{${Constants.CONVO_ID_ARGUMENT}}"),
                arguments = listOf(navArgument(Constants.CONVO_ID_ARGUMENT) { type = NavType.StringType }),
            ) { entry ->
                MessangerScreen(
                    convoId = entry.arguments?.getString(Constants.CONVO_ID_ARGUMENT).orEmpty(),
                    backTitle = remember { navController.backTitle() },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/** Назначение переписки: диалог задаётся аргументом пути. */
private fun messangerRoute(convoId: String) = "${Constants.MESSANGER_ROUTE}/$convoId"

/**
 * Имя экрана, с которого пришли, — подпись кнопки возврата.
 *
 * Вкладки называются собой: «Контакты», «Чаты», «Профиль». Всё остальное — «Назад»: у
 * профиля собеседника имя совпадает с заголовком переписки, и подпись повторяла бы то, что
 * и так написано по центру.
 *
 * Читается один раз, при первой сборке экрана: дальше стек под ним не меняется, а
 * пересборка с уже другим `previousBackStackEntry` подменила бы подпись на ходу.
 */
private fun NavHostController.backTitle(): String {
    val route = previousBackStackEntry?.destination?.route

    return Tab.entries.firstOrNull { it.route == route }?.title ?: Constants.BACK
}

/**
 * Назначение чужого профиля: аккаунт в пути, имя из списка контактов — запросом.
 *
 * Имя едет запросом, а не вторым отрезком пути, потому что бывает пустым: у пустого
 * отрезка назначение просто не совпало бы. Кодируется — в логине пробелов не бывает, но
 * поле имени профиля их допускает.
 */
private fun userRoute(userId: String, login: String) =
    "${Constants.USER_ROUTE}/$userId?${Constants.LOGIN_ARGUMENT}=${Uri.encode(login)}"

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

/** Высота таб-бара без системной полосы: значок в 24 точки, подпись под ним и поля. */
private val TAB_BAR_HEIGHT = 56.dp
