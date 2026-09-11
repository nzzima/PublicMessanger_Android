package com.nzzima.secretmessanger.voice.data.impl

import android.media.MediaPlayer
import com.nzzima.secretmessanger.voice.domain.api.VoicePlayer
import java.io.File
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [VoicePlayer] поверх `MediaPlayer`.
 *
 * Проигрыватель свой, а не системный: отдавать голосовое в чужое приложение значило бы
 * выпускать расшифрованное наружу — ровно то, от чего оно шифруется.
 *
 * Ход проигрывания опрашивается тиком: уведомления о позиции у `MediaPlayer` нет, а подписка
 * всё равно живёт ровно столько, сколько идёт звук.
 */
class VoicePlayerImpl : VoicePlayer {

    override fun play(file: File): Flow<Float> = callbackFlow {
        val player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener { close() }
            prepare()
            start()
        }

        val total = player.duration.takeIf { it > 0 } ?: 1

        launch {
            while (isActive) {
                trySend((player.currentPosition.toFloat() / total).coerceIn(0f, 1f))
                delay(TICK_MS)
            }
        }

        awaitClose {
            runCatching { player.stop() }
            player.release()
        }
    }

    private companion object {
        /** Тик опроса позиции: чаще незачем, реже — полоска ползёт рывками. */
        const val TICK_MS = 100L
    }
}
