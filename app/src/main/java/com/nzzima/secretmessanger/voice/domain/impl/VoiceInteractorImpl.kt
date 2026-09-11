package com.nzzima.secretmessanger.voice.domain.impl

import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.openBytes
import com.nzzima.secretmessanger.chats.domain.sealBytes
import com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys
import com.nzzima.secretmessanger.crypto.domain.models.CryptoFailure
import com.nzzima.secretmessanger.voice.domain.api.VoiceInteractor
import com.nzzima.secretmessanger.voice.domain.api.VoiceRepository
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Голосовые: расшифровка в файл и порядок записи.
 *
 * Расшифрованное лежит во временной папке, а не в памяти: проигрыватель играет из файла. Это
 * единственное место, где открытое содержимое переписки оказывается на диске, поэтому папка
 * своя и чистится системой вместе с кэшем приложения.
 *
 * @param folder куда класть расшифрованное; задаётся снаружи, чтобы домен не знал про Context.
 */
class VoiceInteractorImpl(
    private val voices: VoiceRepository,
    private val conversationKeys: ConversationKeys,
    private val folder: File,
) : VoiceInteractor {

    private val guard = Mutex()

    override suspend fun voice(chat: Chat, messageId: String, version: Int): File? = guard.withLock {
        val ready = File(folder, "$messageId.m4a")

        // Отправленное неизменяемо, поэтому файл под тем же именем другим не станет: скачанное
        // однажды годится навсегда.
        if (ready.exists() && ready.length() > 0) return@withLock ready

        val sealed = voices.sealed(chat.id, messageId).getOrNull() ?: return@withLock null
        val raw = conversationKeys.openBytes(chat, sealed, version) ?: return@withLock null

        folder.mkdirs()
        ready.writeBytes(raw)

        ready
    }

    override suspend fun attach(chat: Chat, messageId: String, file: File): Result<Unit> {
        val raw = runCatching { file.readBytes() }.getOrElse { return Result.failure(it) }
        val sealed = conversationKeys.sealBytes(chat, raw) ?: return Result.failure(CryptoFailure.NoKey)

        return voices.put(chat.id, messageId, chat.selfId, sealed)
    }
}
