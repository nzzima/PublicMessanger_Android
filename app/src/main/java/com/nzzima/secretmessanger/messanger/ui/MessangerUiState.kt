package com.nzzima.secretmessanger.messanger.ui

import com.nzzima.secretmessanger.messanger.domain.models.Reply

/** Состояние экрана переписки. */
sealed interface MessangerUiState {

    /** Первый снимок ещё не пришёл. */
    data object Loading : MessangerUiState

    /**
     * Переписка открыта.
     *
     * @property title название диалога — логины всех, кроме себя.
     * @property replies окно реплик, от старых к свежим; пустым бывает у диалога, в
     *   котором ещё никто ничего не написал.
     * @property avatars байты аватаров по идентификатору участника; кого в карте нет — тот
     *   ещё не загрузился либо аватара не имеет.
     * @property photos байты снимков по идентификатору реплики; чего в карте нет — то ещё
     *   грузится либо не открылось: ключа нужной версии у нас нет.
     * @property opened снимок, раскрытый на весь экран; `null` — лента как обычно.
     * @property presence подпись под названием: когда собеседника видели в последний раз.
     *   `null` у группы — там собеседник не один, и присутствие одного из них ни о чём.
     * @property recordingLeft сколько секунд записи осталось; `null` — запись не идёт. Счёт
     *   идёт вниз: человеку важно, сколько ещё можно говорить, а не сколько уже сказано.
     * @property playing реплика, которая звучит прямо сейчас; `null` — тишина.
     * @property progress доля проигранного у звучащей реплики.
     * @property draft набранный, но не отправленный текст. Переживает отказ отправки:
     *   очищается только после успешной записи.
     * @property error причина, по которой отправка не прошла.
     */
    data class Content(
        val title: String,
        val replies: List<Reply>,
        val avatars: Map<String, ByteArray> = emptyMap(),
        val photos: Map<String, ByteArray> = emptyMap(),
        val opened: ByteArray? = null,
        val presence: String? = null,
        val recordingLeft: Int? = null,
        val playing: String? = null,
        val progress: Float = 0f,
        val draft: String = "",
        val isSending: Boolean = false,
        val error: String? = null,
    ) : MessangerUiState {

        /** Есть ли что отправлять и не идёт ли отправка уже. */
        val canSend: Boolean get() = draft.isNotBlank() && !isSending
    }

    /**
     * Подписка отказала.
     *
     * @property canRetry можно ли подписаться заново. У стёртого диалога нельзя:
     *   возвращать нечего, и кнопка «Повторить» обещала бы несбыточное.
     */
    data class Failed(val message: String, val canRetry: Boolean) : MessangerUiState
}
