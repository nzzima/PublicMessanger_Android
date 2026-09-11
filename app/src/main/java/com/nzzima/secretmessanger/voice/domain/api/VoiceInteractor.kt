package com.nzzima.secretmessanger.voice.domain.api

import com.nzzima.secretmessanger.chats.domain.models.Chat
import java.io.File

/** Голосовые: приложить своё и открыть присланное. */
interface VoiceInteractor {

    /**
     * Файл с открытым звуком реплики [messageId]; `null` — записи нет либо ключа этой версии
     * у нас нет.
     *
     * Именно файл, а не байты: проигрыватель Android умеет играть из файла. Расшифрованное
     * кладётся во временную папку и переживает уход с экрана — уже скачанное второй раз не
     * тянем.
     */
    suspend fun voice(chat: Chat, messageId: String, version: Int): File?

    /**
     * Запечатывает записанное и кладёт байты в подколлекцию диалога.
     *
     * **Сообщение о голосовом пишет вызывающий, и пишет вторым** — тот же порядок, что у
     * снимка: сперва то, за чем пойдут, потом объявление, что оно есть.
     */
    suspend fun attach(chat: Chat, messageId: String, file: File): Result<Unit>
}
