package com.nzzima.secretmessanger.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Палитра приложения. Значения совпадают с `Assets.xcassets/Color` на iOS.
 *
 * Схема одна, тёмная.
 */
internal val BgMain = Color(0xFF1D212B)
internal val Raised = Color(0xFF2A2F3D)
internal val Ink = Color(0xFFECEFF5)
internal val InkDim = Color(0xFF8B93A6)
internal val Accent = Color(0xFF5B9BFF)
internal val TabBar = Color(0xFF171A22)
internal val Online = Color(0xFF3FB27F)
internal val OwnBubble = Color(0xFF2F5FA8)
internal val ErrorColor = Color(0xFFFF6B6B)

/**
 * Подложка под булавкой в пузыре геопозиции.
 *
 * Своё значение, а не [Raised]: карточка места лежит внутри пузыря, и на своём же цвете она
 * не читалась бы как отдельная картинка.
 */
internal val PlaceGround = Color(0xFF3A4152)

/**
 * Булавка места.
 *
 * Свой цвет, а не [ErrorColor]: тот означает отказ, и красить им метку значило бы сообщать
 * тревогу там, где её нет.
 */
internal val PlacePin = Color(0xFFE8604C)
