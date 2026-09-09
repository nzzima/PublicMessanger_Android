package com.nzzima.secretmessanger.ui.components

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Время реплики в коротком формате системной локали, как на iOS.
 *
 * Одинаково подписаны список диалогов и лента переписки: одно и то же событие в двух
 * местах не должно выглядеть по-разному.
 */
fun shortTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
