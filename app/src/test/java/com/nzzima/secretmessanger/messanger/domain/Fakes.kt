package com.nzzima.secretmessanger.messanger.domain

import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.Moment
import com.nzzima.secretmessanger.messanger.domain.api.LocationSource
import com.nzzima.secretmessanger.messanger.domain.api.MessageRepository
import com.nzzima.secretmessanger.messanger.domain.models.Message
import com.nzzima.secretmessanger.messanger.domain.models.MessageKind
import com.nzzima.secretmessanger.messanger.domain.models.Place
import com.nzzima.secretmessanger.photo.domain.api.PhotoInteractor
import com.nzzima.secretmessanger.photo.domain.models.PhotoSize
import com.nzzima.secretmessanger.voice.domain.api.VoiceInteractor
import com.nzzima.secretmessanger.voice.domain.api.VoicePlayer
import com.nzzima.secretmessanger.voice.domain.api.VoiceRecorder
import com.nzzima.secretmessanger.voice.domain.models.Recording
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Реплики в памяти.
 *
 * Начального значения нет: до первого [send] подписчик не получает ничего — так же ведёт
 * себя Firestore, пока не пришёл первый снимок.
 */
class FakeMessageRepository : MessageRepository {

    private val snapshots = MutableSharedFlow<Result<List<Message>>>(
        replay = 1,
        extraBufferCapacity = BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Отправленное: диалог и сама реплика, в порядке отправки. */
    val sent = mutableListOf<Pair<String, Message>>()

    /** Превью, ушедшие в шапку, в том же порядке. */
    val previews = mutableListOf<String>()

    /** Чем отказывает отправка; `null` — отправка проходит. */
    var refusal: Throwable? = null

    override fun observeLast(convoId: String, limit: Long): Flow<Result<List<Message>>> = snapshots

    override suspend fun send(convoId: String, message: Message, preview: String): Result<Unit> {
        refusal?.let { return Result.failure(it) }

        sent += convoId to message
        previews += preview
        return Result.success(Unit)
    }

    /** Отметки о смене ключа: в каком диалоге и от кого. */
    val notes = mutableListOf<Pair<String, String>>()

    override suspend fun note(convoId: String, senderId: String): Result<Unit> {
        notes += convoId to senderId
        return Result.success(Unit)
    }

    /** Сколько реплик лежит в диалоге: стираются они страницами, по [erasePage]. */
    val stock = mutableMapOf<String, Int>()

    /** Размеры стёртых страниц, в порядке вызова: по ним видно, что стирание шло страницами. */
    val pages = mutableListOf<Int>()

    /** Чем отказывает стирание страницы; `null` — проходит. */
    var eraseFails: Throwable? = null

    private var pendingErase: CompletableDeferred<Unit>? = null

    /** Подвешивает стирание страниц; отпускается выполнением возвращённого. */
    fun hangErase(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { pendingErase = it }

    override suspend fun erasePage(convoId: String, limit: Long): Result<Int> {
        eraseFails?.let { return Result.failure(it) }

        val left = stock[convoId] ?: 0
        val page = minOf(left, limit.toInt())

        // Вызов отмечается до ожидания: по нему видно, что начатое стирание одно, даже пока
        // оно висит.
        stock[convoId] = left - page
        pages += page
        pendingErase?.await()

        return Result.success(page)
    }

    /** Отдаёт подписчикам очередной снимок. */
    fun send(messages: List<Message>) = snapshots.tryEmit(Result.success(messages))

    /** Отдаёт подписчикам отказ. */
    fun fail(error: Throwable) = snapshots.tryEmit(Result.failure(error))

    private companion object {
        const val BUFFER = 8
    }
}

/** Реплика: минимум полей, за которые цепляются проверки. */
fun message(
    id: String = "m-1",
    senderId: String = "uid-2",
    body: String = "привет",
    encrypted: Boolean = false,
    version: Int = 1,
    date: Moment = Moment(0, 0),
    kind: MessageKind = MessageKind.Text,
    size: PhotoSize? = null,
    seconds: Double? = null,
) = Message(
    id = id,
    senderId = senderId,
    body = body,
    encrypted = encrypted,
    version = version,
    date = date,
    kind = kind,
    size = size,
    seconds = seconds,
)

/** [PhotoInteractor] в памяти: отдаёт заданные байты и помнит, о чём спрашивали. */
class FakePhotoInteractor(
    private val image: ByteArray? = byteArrayOf(7, 7, 7),
    private val size: PhotoSize = PhotoSize(800, 600),
) : PhotoInteractor {

    /** Пары «реплика + версия ключа», снимки которых заказывали. */
    val requested = mutableListOf<Pair<String, Int>>()

    /** Приложенное: пары «реплика + адрес снимка», в порядке отправки. */
    val attached = mutableListOf<Pair<String, String>>()

    /** Чем отказывает вложение; `null` — проходит. */
    var refusal: Throwable? = null

    override suspend fun photo(chat: Chat, messageId: String, version: Int): ByteArray? {
        requested += messageId to version
        return image
    }

    override suspend fun attach(chat: Chat, messageId: String, source: String): Result<PhotoSize> {
        refusal?.let { return Result.failure(it) }

        attached += messageId to source
        return Result.success(size)
    }
}

/** [LocationSource] в памяти: отдаёт заданную точку либо ничего. */
class FakeLocationSource(private val place: Place? = Place(55.75, 37.62)) : LocationSource {

    /** Сколько раз спрашивали место. */
    var requests = 0
        private set

    override suspend fun current(): Place? {
        requests++
        return place
    }
}

/** [VoiceInteractor] в памяти: файл отдаёт заданный, приложенное помнит. */
class FakeVoiceInteractor(private val file: File? = File("voice.m4a")) : VoiceInteractor {

    /** Пары «реплика + версия ключа», звук которых заказывали. */
    val requested = mutableListOf<Pair<String, Int>>()

    /** Приложенное: пары «реплика + файл». */
    val attached = mutableListOf<Pair<String, File>>()

    /** Чем отказывает вложение; `null` — проходит. */
    var refusal: Throwable? = null

    override suspend fun voice(chat: Chat, messageId: String, version: Int): File? {
        requested += messageId to version
        return file
    }

    override suspend fun attach(chat: Chat, messageId: String, file: File): Result<Unit> {
        refusal?.let { return Result.failure(it) }

        attached += messageId to file
        return Result.success(Unit)
    }
}

/** [VoiceRecorder], которым распоряжается тест. */
class FakeVoiceRecorder(private var result: Recording? = Recording(File("voice.m4a"), seconds = 3.0)) : VoiceRecorder {

    override var isRecording = false
        private set

    /** Сколько раз запись бросали. */
    var cancels = 0
        private set

    /** Пускать ли запись вообще: `false` — микрофон занят. */
    var starts = true

    override fun start(): Boolean {
        if (!starts) return false

        isRecording = true
        return true
    }

    override fun stop(): Recording? {
        isRecording = false
        return result
    }

    override fun cancel() {
        isRecording = false
        cancels++
    }

    /** Задаёт, что вернёт следующая остановка; `null` — записи не вышло. */
    fun records(recording: Recording?) {
        result = recording
    }
}

/** [VoicePlayer] в памяти: ход проигрывания подаёт тест. */
class FakeVoicePlayer : VoicePlayer {

    private val progress = MutableSharedFlow<Float>(replay = 1, extraBufferCapacity = 8)

    /** Файлы, которые просили сыграть. */
    val played = mutableListOf<File>()

    override fun play(file: File): Flow<Float> {
        played += file
        return progress
    }

    /** Двигает полоску. */
    fun to(value: Float) = progress.tryEmit(value)
}
