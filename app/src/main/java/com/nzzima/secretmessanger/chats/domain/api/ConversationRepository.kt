package com.nzzima.secretmessanger.chats.domain.api

import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.ConversationGone
import com.nzzima.secretmessanger.chats.domain.models.ConversationHeader
import kotlinx.coroutines.flow.Flow

/** Шапки диалогов из коллекции `conversation`. */
interface ConversationRepository {

    /**
     * Диалоги, где состоит [selfId], — целым снимком на каждое изменение любого из них.
     *
     * Порядок произвольный: сортирует вызывающий. Пара `arrayContains` + `orderBy` требует
     * составного индекса, которого в базе нет.
     *
     * Снимок отдаётся целиком, а не изменениями: шапка меняется на каждой отправке, и
     * список всё равно пересобирается заново.
     *
     * Отказ приходит последним значением, после чего поток закрывается: слушатель Firestore
     * после ошибки снимается сам, и продолжать слушать уже нечего — за повторной попыткой
     * нужна новая подписка.
     */
    fun observeHeaders(selfId: String): Flow<Result<List<ConversationHeader>>>

    /**
     * Один диалог [convoId] — свежим значением на каждое изменение его шапки.
     *
     * Слушается, а не читается однократно: состав, имена участников и ключи меняются при
     * открытом экране — добавленному участнику ключ дозапечатывают, и приезжает он сюда.
     *
     * Последняя реплика в [Chat] не входит: экрану переписки она не нужна, там есть сами
     * реплики.
     *
     * Отказ приходит последним значением, после чего поток закрывается — как и у
     * [observeHeaders]. Исчезнувшая шапка приходит [ConversationGone]: диалог стёрли, и
     * повторная подписка ничего не вернёт.
     */
    fun observeChat(convoId: String, selfId: String): Flow<Result<Chat>>
}
