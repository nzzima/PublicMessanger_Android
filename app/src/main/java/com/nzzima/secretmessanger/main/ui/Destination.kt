package com.nzzima.secretmessanger.main.ui

import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Назначения оболочки. [route] — идентификатор в графе [AppNavHost].
 *
 * [Main] — рабочее состояние целиком: вкладки и их собственный граф живут внутри
 * [MainScreen], оболочка о них не знает.
 */
enum class Destination(val route: String) {
    Loading(Constants.LOADING_ROUTE),
    Auth(Constants.AUTH_ROUTE),
    Expired(Constants.EXPIRED_ROUTE),
    Repair(Constants.REPAIR_ROUTE),
    Identity(Constants.IDENTITY_ROUTE),
    Main(Constants.MAIN_ROUTE),
}
