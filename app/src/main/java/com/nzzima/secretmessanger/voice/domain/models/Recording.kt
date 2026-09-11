package com.nzzima.secretmessanger.voice.domain.models

import java.io.File

/**
 * Записанное голосовое: файл на диске и его длительность.
 *
 * Длительность меряет рекордер, а не считается из байтов: у переменного битрейта из размера
 * её не вывести, а показывать её надо ещё до того, как кто-то нажмёт «слушать».
 *
 * @property seconds длительность в секундах; в сообщение уходит этим же числом.
 */
data class Recording(val file: File, val seconds: Double)
