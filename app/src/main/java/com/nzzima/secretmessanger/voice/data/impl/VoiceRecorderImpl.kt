package com.nzzima.secretmessanger.voice.data.impl

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.nzzima.secretmessanger.utils.constants.Constants
import com.nzzima.secretmessanger.voice.domain.api.VoiceRecorder
import com.nzzima.secretmessanger.voice.domain.models.Recording
import java.io.File

/**
 * [VoiceRecorder] поверх `MediaRecorder`.
 *
 * **Кодек и контейнер выбраны не по вкусу, а по договору**: AAC в MPEG-4 — ровно то, что
 * пишет iOS (`kAudioFormatMPEG4AAC`), и записанное здесь обязано играть там. Моно, 24 кГц и
 * 24 кбит/с — это речь, а не музыка: только поэтому голосовое на порядок меньше снимка,
 * помещается в документ Firestore и не требует Cloud Storage вместе с платным тарифом.
 *
 * Разрешение на микрофон проверяет экран — сюда приходят уже с ним.
 */
class VoiceRecorderImpl(private val context: Context) : VoiceRecorder {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    override val isRecording: Boolean get() = recorder != null

    override fun start(): Boolean {
        if (isRecording) return false

        val target = File(context.cacheDir, "voice-${System.currentTimeMillis()}.m4a")

        val created = runCatching {
            recorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(MONO)
                setAudioSamplingRate(Constants.VOICE_SAMPLE_RATE)
                setAudioEncodingBitRate(Constants.VOICE_BIT_RATE)
                // Потолок ставит сам рекордер: дойдя до него, он останавливается сам, и
                // наговорённое остаётся в файле — в отличие от отказа записи в базу.
                setMaxDuration(Constants.VOICE_MAX_MS.toInt())
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
        }.getOrNull() ?: run {
            target.delete()
            return false
        }

        recorder = created
        file = target
        startedAt = SystemClock.elapsedRealtime()

        return true
    }

    override fun stop(): Recording? {
        val active = recorder ?: return null
        val target = file
        val elapsed = SystemClock.elapsedRealtime() - startedAt

        recorder = null
        file = null

        // Остановка до первого кадра валится исключением, и файл остаётся пустым: нажатие
        // вместо удержания — обычное дело, отправлять из него нечего.
        val stopped = runCatching {
            active.stop()
            active.release()
        }.isSuccess

        if (!stopped) {
            runCatching { active.release() }
            target?.delete()
            return null
        }

        if (target == null || target.length() == 0L || elapsed < MIN_MS) {
            target?.delete()
            return null
        }

        return Recording(target, seconds = elapsed / MILLIS_IN_SECOND)
    }

    override fun cancel() {
        val active = recorder ?: return

        recorder = null
        runCatching { active.stop() }
        runCatching { active.release() }
        file?.delete()
        file = null
    }

    private fun recorder(context: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()

    private companion object {
        const val MONO = 1

        /** Короче этого в файле обычно нет ни одного кадра звука. */
        const val MIN_MS = 400L
        const val MILLIS_IN_SECOND = 1000.0
    }
}
