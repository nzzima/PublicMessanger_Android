package com.nzzima.secretmessanger.messanger.domain.api

import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.Moment
import com.nzzima.secretmessanger.messanger.domain.models.Dialogue
import com.nzzima.secretmessanger.messanger.domain.models.Place
import com.nzzima.secretmessanger.voice.domain.models.Recording
import kotlinx.coroutines.flow.Flow

/** Переписка одного диалога: что показать и что отправить. */
interface MessangerInteractor {

    /**
     * Диалог [convoId] глазами аккаунта [selfId]: шапка и окно расшифрованных реплик.
     *
     * Значения приходят на изменение любой из двух подписок — шапки и реплик. Отказ
     * любой из них закрывает экран целиком: без шапки нечем расшифровывать, без реплик
     * нечего показывать.
     */
    fun observeDialogue(convoId: String, selfId: String): Flow<Result<Dialogue>>

    /**
     * Отправляет текст [text] в диалог [chat].
     *
     * Текст закрывается ключом текущей версии. Диалог без ключей — начатый до появления
     * шифрования — принимает текст открытым: его история и так лежит в базе читаемой, а
     * задним числом её не зашифровать.
     *
     * @param text непустой текст без окружающих пробелов; проверяет вызывающий.
     * @return отказ [com.nzzima.secretmessanger.crypto.domain.models.CryptoFailure.NoKey],
     *   если ключ диалога нам не выдан: шифровать нечем, и в базу ничего не уходит.
     */
    suspend fun send(chat: Chat, text: String): Result<Unit>

    /**
     * Отправляет снимок [source] в диалог [chat].
     *
     * Две записи в том же порядке, что у аватара: сперва байты в `images/{messageId}`,
     * потом сообщение о них. Обратный порядок оставил бы в ленте пузырь, ведущий в пустоту.
     *
     * В теле сообщения и в шапке едет запечатанное «📷 Фото»: без превью диалог из одних
     * снимков выпал бы из «Чатов» — список отбрасывает шапку с пустой последней репликой.
     *
     * @param source адрес снимка от системного выборщика.
     * @return отказ [com.nzzima.secretmessanger.photo.domain.models.PhotoTooLarge], если
     *   снимок не разобрался, и
     *   [com.nzzima.secretmessanger.crypto.domain.models.CryptoFailure.NoKey], если ключа
     *   диалога у нас нет: открытым снимок в базу не уйдёт.
     */
    suspend fun sendPhoto(chat: Chat, source: String): Result<Unit>

    /**
     * Отправляет записанное голосовое [recording] в диалог [chat].
     *
     * Две записи в том же порядке, что у снимка: сперва байты в `audio/{messageId}`, потом
     * сообщение о них с длительностью. В теле и в шапке — запечатанное «🎤 Голосовое
     * сообщение»: без превью диалог из одних голосовых выпал бы из «Чатов».
     */
    suspend fun sendVoice(chat: Chat, recording: Recording): Result<Unit>

    /**
     * Отправляет точку [place] в диалог [chat].
     *
     * Запись одна: своей подколлекции у точки нет — две координаты помещаются в само
     * сообщение и шифруются как текст. В шапку при этом уходит «📍 Геопозиция», а не
     * координаты: список диалогов показывает превью, а не место.
     *
     * @return отказ [com.nzzima.secretmessanger.crypto.domain.models.CryptoFailure.NoKey],
     *   если ключа диалога у нас нет: класть в базу открытую точку мы не станем.
     */
    suspend fun sendLocation(chat: Chat, place: Place): Result<Unit>

    /**
     * Отмечает, что мы дочитали диалог [chat] до момента [upTo].
     *
     * Записи не будет, если отметка уже стоит на этом моменте или дальше: она только растёт,
     * а повторная запись разбудила бы слушателя шапки у собеседника впустую.
     *
     * @param upTo время последней **чужой** реплики — [Dialogue.lastIncoming].
     */
    suspend fun markRead(chat: Chat, upTo: Moment): Result<Unit>
}
