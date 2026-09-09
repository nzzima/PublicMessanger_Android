package com.nzzima.secretmessanger.chats.domain.api

import com.nzzima.secretmessanger.contacts.domain.models.Contact

/** Заведение диалога с контактом. */
interface ConversationStarter {

    /**
     * Диалог аккаунта [selfId] с [contact]: существующий или заведённый заново.
     *
     * Идентификатор детерминированный — пара uid по алфавиту, — поэтому повторное открытие
     * попадает в тот же диалог, а не плодит вторые. Существующий диалог не трогается вовсе:
     * ни состав, ни ключи, ни имена. Их правит создатель, и слепая запись поверх была бы
     * отклонена правилами целиком.
     *
     * Новый диалог заводится сразу с ключом, запечатанным обоим участникам, и с именами
     * обоих: без своего имени в карте собеседник видел бы диалог без названия, и починить
     * это было бы нечем — имена в шапку Android больше не пишет.
     *
     * @return идентификатор диалога, который надо открыть.
     * @throws com.nzzima.secretmessanger.chats.domain.models.CompanionKeyMissing отказом,
     *   если запечатать ключ собеседнику нечем.
     */
    suspend fun start(selfId: String, contact: Contact): Result<String>
}
