package com.nzzima.secretmessanger.messanger.domain.api

import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.Moment
import com.nzzima.secretmessanger.messanger.domain.models.Dialogue
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
     * Отмечает, что мы дочитали диалог [chat] до момента [upTo].
     *
     * Записи не будет, если отметка уже стоит на этом моменте или дальше: она только растёт,
     * а повторная запись разбудила бы слушателя шапки у собеседника впустую.
     *
     * @param upTo время последней **чужой** реплики — [Dialogue.lastIncoming].
     */
    suspend fun markRead(chat: Chat, upTo: Moment): Result<Unit>
}
