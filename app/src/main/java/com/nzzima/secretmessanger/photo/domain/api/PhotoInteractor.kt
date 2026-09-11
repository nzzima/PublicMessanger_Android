package com.nzzima.secretmessanger.photo.domain.api

import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.photo.domain.models.PhotoSize

/** Снимки переписки: показать присланный и приложить свой. */
interface PhotoInteractor {

    /**
     * Открытые байты снимка реплики [messageId], запечатанного ключом версии [version];
     * `null` — снимка нет либо ключа этой версии у нас нет.
     *
     * Уже прочитанное берётся из памяти: отправленное неизменяемо, поэтому снимок под тем же
     * идентификатором реплики другим не станет.
     */
    suspend fun photo(chat: Chat, messageId: String, version: Int): ByteArray?

    /**
     * Готовит снимок [source] к отправке в диалог [chat] под идентификатором [messageId]:
     * кодирует, запечатывает и кладёт байты в подколлекцию.
     *
     * **Сообщение о снимке пишет вызывающий, и пишет вторым** — тот же порядок, что у
     * аватара: сперва то, за чем пойдут, потом объявление, что оно есть. Обратный порядок
     * оставил бы в ленте пузырь, ведущий в пустоту.
     *
     * Свой же снимок кладётся в кэш сразу: показывать надо ровно то, что увидят остальные, —
     * сжатую версию, а не исходник, — и качать его обратно из базы незачем.
     *
     * @return размеры подогнанного снимка: они едут в сообщении.
     */
    suspend fun attach(chat: Chat, messageId: String, source: String): Result<PhotoSize>
}
