package com.nzzima.secretmessanger.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

/**
 * Иконки приложения, нарисованные кодом.
 *
 * Своя отрисовка вместо `material-icons`: набор объявлен устаревшим, а нужна из него
 * горстка глифов. Контуры повторяют символы iOS — `person.circle`, `ellipsis.message`,
 * `person`, `chevron.left`, `arrow.up.circle`.
 *
 * Все строятся в поле 24×24 и рисуются обводкой, поэтому цвет задаёт вызывающий через
 * `tint`.
 */
private const val VIEWPORT = 24f
private val SIZE = 24.dp
private const val STROKE = 1.7f

private fun strokeIcon(name: String, paths: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = SIZE,
        defaultHeight = SIZE,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathData(paths),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

/** Голова и плечи в круге — вкладка «Контакты». */
val ContactsIcon: ImageVector = strokeIcon("contacts") {
    // Обод.
    moveTo(12f, 2.6f)
    arcToRelative(9.4f, 9.4f, 0f, true, true, -0.01f, 0f)
    // Голова.
    moveTo(12f, 7f)
    arcToRelative(2.6f, 2.6f, 0f, true, true, -0.01f, 0f)
    // Плечи, обрезанные ободом.
    moveTo(6.4f, 19.2f)
    curveTo(7.2f, 16.4f, 9.4f, 15f, 12f, 15f)
    curveTo(14.6f, 15f, 16.8f, 16.4f, 17.6f, 19.2f)
}

/** Облако реплики с тремя точками — вкладка «Чаты». */
val ChatsIcon: ImageVector = strokeIcon("chats") {
    moveTo(6f, 4.5f)
    lineTo(18f, 4.5f)
    arcToRelative(3.5f, 3.5f, 0f, false, true, 3.5f, 3.5f)
    lineTo(21.5f, 14f)
    arcToRelative(3.5f, 3.5f, 0f, false, true, -3.5f, 3.5f)
    lineTo(11f, 17.5f)
    lineTo(6.5f, 21f)
    lineTo(6.5f, 17.5f)
    lineTo(6f, 17.5f)
    arcToRelative(3.5f, 3.5f, 0f, false, true, -3.5f, -3.5f)
    lineTo(2.5f, 8f)
    arcToRelative(3.5f, 3.5f, 0f, false, true, 3.5f, -3.5f)
    close()
    // Многоточие.
    moveTo(8f, 11f)
    horizontalLineToRelative(0.01f)
    moveTo(12f, 11f)
    horizontalLineToRelative(0.01f)
    moveTo(16f, 11f)
    horizontalLineToRelative(0.01f)
}

/** Голова и плечи без обода — вкладка «Профиль». */
val ProfileIcon: ImageVector = strokeIcon("profile") {
    moveTo(12f, 4f)
    arcToRelative(3.6f, 3.6f, 0f, true, true, -0.01f, 0f)
    moveTo(4.5f, 20f)
    curveTo(4.5f, 16f, 8f, 14f, 12f, 14f)
    curveTo(16f, 14f, 19.5f, 16f, 19.5f, 20f)
}

/** Шеврон влево — возврат из переписки к списку диалогов. */
val BackIcon: ImageVector = strokeIcon("back") {
    moveTo(14.5f, 4.5f)
    lineTo(8f, 12f)
    lineTo(14.5f, 19.5f)
}

/** Стрелка вверх в круге — отправка реплики. */
val SendIcon: ImageVector = strokeIcon("send") {
    // Обод.
    moveTo(12f, 2.6f)
    arcToRelative(9.4f, 9.4f, 0f, true, true, -0.01f, 0f)
    // Древко.
    moveTo(12f, 16.6f)
    lineTo(12f, 7.8f)
    // Наконечник.
    moveTo(8.3f, 11.5f)
    lineTo(12f, 7.8f)
    lineTo(15.7f, 11.5f)
}
