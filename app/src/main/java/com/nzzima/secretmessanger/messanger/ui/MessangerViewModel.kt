package com.nzzima.secretmessanger.messanger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzzima.secretmessanger.messanger.domain.models.PlaceUnknown
import com.nzzima.secretmessanger.avatar.domain.api.AvatarInteractor
import com.nzzima.secretmessanger.chats.domain.models.Chat
import com.nzzima.secretmessanger.chats.domain.models.ConversationGone
import com.nzzima.secretmessanger.chats.domain.models.Moment
import com.nzzima.secretmessanger.messanger.domain.api.LocationSource
import com.nzzima.secretmessanger.messanger.domain.api.MessangerInteractor
import com.nzzima.secretmessanger.messanger.domain.models.Dialogue
import com.nzzima.secretmessanger.photo.domain.api.PhotoInteractor
import com.nzzima.secretmessanger.presence.domain.api.PresenceInteractor
import com.nzzima.secretmessanger.voice.domain.api.VoiceInteractor
import com.nzzima.secretmessanger.voice.domain.api.VoicePlayer
import com.nzzima.secretmessanger.voice.domain.api.VoiceRecorder
import com.nzzima.secretmessanger.profile.domain.api.ProfileInteractor
import com.nzzima.secretmessanger.session.domain.api.SessionInteractor
import com.nzzima.secretmessanger.utils.constants.Constants
import com.nzzima.secretmessanger.utils.errors.ErrorText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Состояние экрана переписки и обработка ввода.
 *
 * Идентификатор аккаунта берётся из сессии в момент подписки: экран достижим только из
 * вкладок, то есть при живой сессии.
 *
 * Отказ подписки проверяется на мёртвую сессию — с ней «Повторить» не сработает никогда,
 * и решение принимает оболочка, а не этот экран. Стёртый диалог проверять незачем:
 * сессия тут ни при чём.
 */
class MessangerViewModel(
    private val convoId: String,
    private val sessionInteractor: SessionInteractor,
    private val messangerInteractor: MessangerInteractor,
    private val profileInteractor: ProfileInteractor,
    private val avatarInteractor: AvatarInteractor,
    private val photoInteractor: PhotoInteractor,
    private val locationSource: LocationSource,
    private val presenceInteractor: PresenceInteractor,
    private val voiceInteractor: VoiceInteractor,
    private val voiceRecorder: VoiceRecorder,
    private val voicePlayer: VoicePlayer,
) : ViewModel() {

    private val messangerScreenState = MutableStateFlow<MessangerUiState>(MessangerUiState.Loading)
    private var subscription: Job? = null

    /** Участники, про которых уже спрашивали, — включая тех, у кого аватара не нашлось. */
    private val askedMembers = mutableSetOf<String>()

    /** Реплики, снимки которых уже заказывали, — включая те, что не открылись. */
    private val askedPhotos = mutableSetOf<String>()

    /** Подписка на присутствие собеседника; у группы её нет. */
    private var presence: Job? = null

    /** Обратный отсчёт записи и подписка на звучащее — по одной за раз. */
    private var countdown: Job? = null
    private var playback: Job? = null

    /**
     * Шапка последнего снимка — ею запечатывается отправляемое.
     *
     * Держится отдельно от состояния экрана: показывать её нечем, а ключ диалога может
     * приехать при открытом экране, и отправка обязана брать свежий.
     */
    private var chat: Chat? = null

    /** Время последней чужой реплики — ею отмечается прочтение. */
    private var lastIncoming: Moment? = null

    /**
     * Виден ли экран.
     *
     * Подписка живёт дольше показа: свёрнутое приложение продолжает получать снимки, и без
     * этой проверки чат, убранный в карман вместе с телефоном, отмечал бы входящие
     * прочитанными. Собеседник видел бы две галочки на том, чего никто не читал.
     */
    private var visible = false

    /** Текущее состояние экрана. */
    fun observeMessangerScreenState(): StateFlow<MessangerUiState> = messangerScreenState.asStateFlow()

    init {
        subscribe()
    }

    /**
     * Подписывается на переписку заново.
     *
     * Нужна после отказа: слушатель Firestore на ошибке снимается, и продолжать слушать
     * прежней подпиской нечего.
     */
    fun retry() = subscribe()

    /** Экран показался: с этого мига входящие считаются прочитанными. */
    fun onVisible() {
        visible = true
        markRead()
    }

    /** Экран ушёл с глаз — отмечать прочтение больше нечем. */
    fun onHidden() {
        visible = false
    }

    /** Записывает набранный текст и снимает показанную ошибку. */
    fun onDraftChange(value: String) = messangerScreenState.update { current ->
        if (current is MessangerUiState.Content) current.copy(draft = value, error = null) else current
    }

    /**
     * Отправляет набранное.
     *
     * Отправка ограничена [Constants.SUBMIT_TIMEOUT_MS]. Набранный текст очищается только
     * после успеха: отказ его сохраняет — переписывать заново из-за пропавшей связи
     * человеку не за что.
     */
    fun onSend() {
        val text = (messangerScreenState.value as? MessangerUiState.Content)?.draft?.trim().orEmpty()
        if (text.isEmpty()) return

        sending(clearsDraft = true) { chat -> messangerInteractor.send(chat, text) }
    }

    /**
     * Отправляет выбранный снимок [source].
     *
     * Идёт тем же путём, что текст, включая срок в [Constants.SUBMIT_TIMEOUT_MS]: снимок уже
     * подогнан под бюджет, и если за этот срок он не уехал, дело не в его размере.
     */
    fun onPhotoPicked(source: String) = sending { chat -> messangerInteractor.sendPhoto(chat, source) }

    /**
     * Отправляет текущее место.
     *
     * Разрешение спрашивает экран и зовёт это только с ним. Место может не определиться и с
     * разрешением — выключенная геолокация, отказ приёмника, — и тогда в ленту ничего не
     * уходит, а причина показывается строкой.
     */
    fun onLocationPicked() = sending { chat ->
        val place = locationSource.current() ?: return@sending Result.failure(PlaceUnknown())

        messangerInteractor.sendLocation(chat, place)
    }

    /** Разрешения на место не дали: отправлять точку нечем. */
    fun onLocationDenied() = messangerScreenState.update { current ->
        if (current is MessangerUiState.Content) current.copy(error = Constants.PLACE_DENIED) else current
    }

    /** Раскрывает снимок реплики [messageId] на весь экран. */
    fun onPhotoOpened(messageId: String) = messangerScreenState.update { current ->
        if (current is MessangerUiState.Content) current.copy(opened = current.photos[messageId]) else current
    }

    /** Закрывает раскрытый снимок. */
    fun onPhotoClosed() = messangerScreenState.update { current ->
        if (current is MessangerUiState.Content) current.copy(opened = null) else current
    }

    /**
     * Общая часть отправки: срок, признак отправки и разбор итога.
     *
     * Набранный текст очищается только у успешной отправки текста — вложение его не трогает
     * вовсе: подпись к снимку никто не набирал.
     */
    private fun sending(clearsDraft: Boolean = false, send: suspend (Chat) -> Result<Unit>) {
        val state = messangerScreenState.value as? MessangerUiState.Content ?: return
        val chat = chat ?: return
        if (state.isSending) return

        messangerScreenState.update { current ->
            if (current is MessangerUiState.Content) current.copy(isSending = true, error = null) else current
        }

        viewModelScope.launch {
            val result = withTimeoutOrNull(Constants.SUBMIT_TIMEOUT_MS) { send(chat) }

            messangerScreenState.update { current ->
                if (current !is MessangerUiState.Content) return@update current

                when {
                    result == null -> current.copy(isSending = false, error = Constants.SERVER_SILENT)
                    result.isSuccess -> current.copy(isSending = false, draft = if (clearsDraft) "" else current.draft)
                    else -> current.copy(
                        isSending = false,
                        error = ErrorText.of(result.exceptionOrNull()),
                    )
                }
            }
        }
    }

    /**
     * Догружает снимки показанных реплик.
     *
     * Заказываем **только новые**, включая не открывшиеся: снимок лежит в неизменяемом
     * документе, поэтому второй раз спрашивать его незачем, а лента перечитывается на каждую
     * реплику.
     */
    /**
     * Следит за присутствием собеседника — один раз за подписку.
     *
     * У группы не заводится вовсе: присутствие одного участника из нескольких в шапке ничего
     * не значит, а выбирать из них одного было бы враньём — как и с аватаром группы.
     */
    private fun watchPresence(chat: Chat) {
        val companion = chat.companionId ?: return
        if (presence?.isActive == true) return

        presence = viewModelScope.launch {
            presenceInteractor.observePresence(companion).collect { seen ->
                val text = seen?.text(System.currentTimeMillis())

                messangerScreenState.update { current ->
                    if (current is MessangerUiState.Content) current.copy(presence = text) else current
                }
            }
        }
    }

    private fun loadPhotos(dialogue: Dialogue) {
        val pending = dialogue.replies.filter { it.photo != null && askedPhotos.add(it.id) }
        if (pending.isEmpty()) return

        viewModelScope.launch {
            pending.forEach { reply ->
                val attachment = reply.photo ?: return@forEach
                val image = photoInteractor.photo(dialogue.chat, reply.id, attachment.keyVersion) ?: return@forEach

                messangerScreenState.update { current ->
                    if (current is MessangerUiState.Content) {
                        current.copy(photos = current.photos + (reply.id to image))
                    } else {
                        current
                    }
                }
            }
        }
    }

    /**
     * Начинает запись голосового.
     *
     * Разрешение спрашивает экран и зовёт это только с ним. Отказ самого рекордера — занятый
     * микрофон или некуда писать — показывается строкой: молча проглотить удержание значило
     * бы оставить человека говорить в пустоту.
     */
    fun onRecordStart() {
        if (!voiceRecorder.start()) {
            update { it.copy(error = Constants.VOICE_FAILED) }
            return
        }

        update { it.copy(recordingLeft = (Constants.VOICE_MAX_MS / MILLIS_IN_SECOND).toInt(), error = null) }

        countdown = viewModelScope.launch {
            while (true) {
                delay(MILLIS_IN_SECOND)

                val left = ((messangerScreenState.value as? MessangerUiState.Content)?.recordingLeft ?: 0) - 1

                // Дойдя до потолка, рекордер останавливается сам, и наговорённое остаётся в
                // файле. Экрану остаётся отправить его, как будто палец подняли вовремя.
                if (left <= 0) {
                    onRecordFinish()
                    return@launch
                }

                update { it.copy(recordingLeft = left) }
            }
        }
    }

    /** Палец подняли: запись заканчивается и уходит в диалог. */
    fun onRecordFinish() {
        countdown?.cancel()
        countdown = null
        update { it.copy(recordingLeft = null) }

        // Слишком короткое нажатие звука не содержит — рекордер отдаёт null, и отправлять
        // нечего. Ошибки тут тоже нет: человек просто ткнул в микрофон.
        val recording = voiceRecorder.stop() ?: return

        sending { chat -> messangerInteractor.sendVoice(chat, recording) }
    }

    /** Запись брошена: палец увели с кнопки. */
    fun onRecordCancel() {
        countdown?.cancel()
        countdown = null
        voiceRecorder.cancel()
        update { it.copy(recordingLeft = null) }
    }

    /** Разрешения на микрофон не дали: записывать нечем. */
    fun onMicDenied() = update { it.copy(error = Constants.MIC_DENIED) }

    /**
     * Включает или выключает звучание реплики [messageId].
     *
     * Второе нажатие по звучащей останавливает: отдельной кнопки «стоп» нет — та же кнопка
     * и означает «хватит».
     */
    fun onVoicePressed(messageId: String, version: Int) {
        val chat = chat ?: return
        val current = messangerScreenState.value as? MessangerUiState.Content ?: return

        playback?.cancel()
        playback = null

        if (current.playing == messageId) {
            update { it.copy(playing = null, progress = 0f) }
            return
        }

        playback = viewModelScope.launch {
            val file = voiceInteractor.voice(chat, messageId, version)

            if (file == null) {
                update { it.copy(error = Constants.UNREADABLE) }
                return@launch
            }

            update { it.copy(playing = messageId, progress = 0f) }

            voicePlayer.play(file).collect { progress ->
                update { it.copy(progress = progress) }
            }

            // Поток кончается вместе с записью — это и есть «доиграло».
            update { it.copy(playing = null, progress = 0f) }
        }
    }

    /** Правка состояния экрана, когда оно содержательное; иначе оставляем как есть. */
    private fun update(change: (MessangerUiState.Content) -> MessangerUiState.Content) =
        messangerScreenState.update { current ->
            if (current is MessangerUiState.Content) change(current) else current
        }

    /** Спрашивает подтверждение выхода: уйти из группы молча по одному нажатию нельзя. */
    fun onLeaveAsked() = update { it.copy(askingLeave = true) }

    /** Передумали выходить. */
    fun onLeaveDismissed() = update { it.copy(askingLeave = false) }

    /**
     * Выходит из группы.
     *
     * Подписки снимаются **до** записи: сразу после неё доступ к диалогу пропадает вместе с
     * членством, и слушатели получили бы отказ по правам — вышедший увидел бы собственный
     * уход как поломку. На iOS ту же беду лечат отметкой «мы уходим», которую проверяет
     * обработчик отказа; здесь проще не создавать отказ вовсе.
     *
     * Не прошло — подписываемся заново: человек остался в группе, и показывать её надо как
     * прежде.
     */
    fun onLeaveConfirmed() {
        val chat = chat ?: return

        update { it.copy(askingLeave = false, isSending = true, error = null) }

        subscription?.cancel()
        presence?.cancel()

        viewModelScope.launch {
            messangerInteractor.leave(chat)
                .onSuccess { update { it.copy(isSending = false, left = true) } }
                .onFailure { error ->
                    update {
                        it.copy(isSending = false, error = ErrorText.of(error))
                    }
                    // Переподписка молча: обычная сбрасывает экран в ожидание, а вместе с
                    // ним стёрлась бы и причина, ради которой человек здесь остался.
                    subscribe(keepShown = true)
                }
        }
    }

    /**
     * Отмечает прочтение по последней чужой реплике.
     *
     * Отсев повторов — дело сценария: он сверяется с отметкой из шапки, а та только растёт.
     */
    private fun markRead() {
        val chat = chat ?: return
        val upTo = lastIncoming ?: return
        if (!visible) return

        viewModelScope.launch { messangerInteractor.markRead(chat, upTo) }
    }

    /**
     * Догружает аватары участников.
     *
     * Спрашиваем **только про новых**, включая безаватарных: снимок приходит на каждую
     * реплику, а состав меняется разве что при добавлении человека в группу — он-то новым и
     * окажется. Сменённый собеседником аватар догонит при следующем открытии: слушатель на
     * чужие профили ради кружка у пузыря того не стоит. Так же на iOS.
     *
     * Свой аватар берётся наравне с чужими: кружок стоит у каждого пузыря.
     */
    private fun loadAvatars(chat: Chat) {
        val members = chat.members.filter(askedMembers::add)
        if (members.isEmpty()) return

        viewModelScope.launch {
            members.forEach { uid ->
                val version = profileInteractor.observeProfile(uid).first().getOrNull()?.avatarVersion ?: 0
                val image = avatarInteractor.avatar(uid, version) ?: return@forEach

                messangerScreenState.update { current ->
                    if (current is MessangerUiState.Content) {
                        current.copy(avatars = current.avatars + (uid to image))
                    } else {
                        current
                    }
                }
            }
        }
    }

    /**
     * Подписывается на переписку.
     *
     * @param keepShown оставить показанное вместо ожидания. Нужно там, где подписка
     *   восстанавливается после неудачи: сброс в ожидание стёр бы и причину, ради которой
     *   человек на этом экране остался.
     */
    private fun subscribe(keepShown: Boolean = false) {
        val uid = sessionInteractor.observeSession().value.uidOrNull ?: return

        subscription?.cancel()
        presence?.cancel()
        playback?.cancel()
        playback = null
        presence = null

        if (!keepShown) messangerScreenState.value = MessangerUiState.Loading
        // Подписка начинается с пустого состояния, поэтому и спрошенных помним заново.
        askedMembers.clear()
        askedPhotos.clear()

        subscription = viewModelScope.launch {
            messangerInteractor.observeDialogue(convoId, uid).collect { snapshot ->
                snapshot
                    .onSuccess { dialogue ->
                        chat = dialogue.chat
                        lastIncoming = dialogue.lastIncoming
                        markRead()
                        messangerScreenState.update { current ->
                            // Набранное и показанная ошибка переживают новый снимок: чужая
                            // реплика посреди набора текста его не стирает.
                            if (current is MessangerUiState.Content) {
                                current.copy(title = dialogue.chat.title, replies = dialogue.replies)
                            } else {
                                MessangerUiState.Content(dialogue.chat.title, dialogue.replies)
                            }.copy(
                                isGroup = dialogue.chat.isGroup,
                                canLeave = dialogue.chat.isGroup && dialogue.chat.owner != dialogue.chat.selfId,
                            )
                        }
                        loadAvatars(dialogue.chat)
                        loadPhotos(dialogue)
                        watchPresence(dialogue.chat)
                    }
                    .onFailure { error ->
                        val gone = error is ConversationGone
                        messangerScreenState.value = MessangerUiState.Failed(
                            message = ErrorText.of(error),
                            canRetry = !gone,
                        )

                        // Отказ Firestore не отличает мёртвую сессию от обрыва связи, а
                        // «Повторить» лечит только второе. У стёртого диалога сессия ни
                        // при чём — спрашивать не о чем.
                        if (!gone) sessionInteractor.revalidate()
                    }
            }
        }
    }

    private companion object {
        const val MILLIS_IN_SECOND = 1000L
    }
}
