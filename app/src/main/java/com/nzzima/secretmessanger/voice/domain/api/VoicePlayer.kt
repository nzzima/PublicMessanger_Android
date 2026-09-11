package com.nzzima.secretmessanger.voice.domain.api

import java.io.File
import kotlinx.coroutines.flow.Flow

/** Проигрывание голосовых. */
interface VoicePlayer {

    /**
     * Играет [file], отдавая долю проигранного от 0 до 1, и завершается в конце записи.
     *
     * Отмена подписки останавливает звук — отдельной команды «стоп» нет намеренно: экран
     * может уйти вместе с проигрыванием, и забыть её послать было бы легче, чем отменить.
     */
    fun play(file: File): Flow<Float>
}
