package com.nzzima.secretmessanger.main.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nzzima.secretmessanger.auth.ui.AuthScreen
import com.nzzima.secretmessanger.auth.ui.RepairProfileScreen
import com.nzzima.secretmessanger.crypto.ui.IdentityScreen
import com.nzzima.secretmessanger.session.ui.SessionExpiredScreen
import com.nzzima.secretmessanger.utils.constants.Constants
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.koin.androidx.compose.koinViewModel

/**
 * Граф навигации приложения.
 *
 * Назначение выводится из [RootState] целиком: [Destination.Main] достижимо только при
 * [RootState.Ready], то есть после того, как ключ проверен и опубликован. Развилка —
 * отдельное назначение, а не диалог поверх вкладок, поэтому обойти её нечем.
 *
 * Стек возврата очищается на каждом переходе: возвращаться с развилки во вкладки или из
 * вкладок на экран входа некуда. История **внутри** вкладок живёт в своём графе — см.
 * [MainScreen].
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: RootViewModel = koinViewModel(),
) {
    val state by viewModel.observeRootState().collectAsStateWithLifecycle()

    // Присутствие держится, пока приложение на глазах: свёрнутое отмечаться перестаёт, и
    // человек гаснет у собеседников сам, без команды «я ушёл», — её свёрнутое приложение и
    // не успело бы отправить.
    LifecycleResumeEffect(Unit) {
        viewModel.onVisible()
        onPauseOrDispose { viewModel.onHidden() }
    }

    val target = when (state) {
        RootState.Anonymous -> Destination.Auth
        RootState.Expired -> Destination.Expired
        RootState.Checking -> Destination.Loading
        is RootState.NeedsProfile -> Destination.Repair
        RootState.NeedsConfirmation, is RootState.Failed -> Destination.Identity
        RootState.Ready -> Destination.Main
    }

    LaunchedEffect(target) {
        if (navController.currentDestination?.route != target.route) {
            navController.navigate(target.route) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Destination.Loading.route,
        modifier = modifier,
    ) {
        composable(Destination.Loading.route) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        composable(Destination.Auth.route) {
            AuthScreen()
        }
        composable(Destination.Expired.route) {
            SessionExpiredScreen(onSignIn = viewModel::signOut)
        }
        composable(Destination.Repair.route) {
            RepairProfileScreen(
                error = (state as? RootState.NeedsProfile)?.error,
                onSubmit = viewModel::repairProfile,
                onSignOut = viewModel::signOut,
            )
        }
        composable(Destination.Identity.route) {
            val failure = state as? RootState.Failed
            IdentityScreen(
                warning = failure?.message ?: Constants.IDENTITY_WARNING,
                actionTitle = if (failure == null) Constants.IDENTITY_CONTINUE else Constants.RETRY,
                onAction = if (failure == null) viewModel::confirmOverwrite else viewModel::retry,
                onSignOut = viewModel::signOut,
            )
        }
        composable(Destination.Main.route) {
            MainScreen()
        }
    }
}
