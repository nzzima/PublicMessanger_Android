package com.nzzima.secretmessanger.messanger.ui

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzzima.secretmessanger.messanger.domain.models.Place
import com.nzzima.secretmessanger.messanger.domain.models.Reply
import com.nzzima.secretmessanger.photo.domain.models.PhotoSize
import com.nzzima.secretmessanger.ui.components.AttachIcon
import com.nzzima.secretmessanger.ui.components.Avatar
import com.nzzima.secretmessanger.ui.components.BackButton
import com.nzzima.secretmessanger.ui.components.FailureNotice
import com.nzzima.secretmessanger.ui.components.Field
import com.nzzima.secretmessanger.ui.components.Notice
import com.nzzima.secretmessanger.ui.components.SendIcon
import com.nzzima.secretmessanger.ui.components.shortTime
import com.nzzima.secretmessanger.ui.theme.Accent
import com.nzzima.secretmessanger.ui.theme.ErrorColor
import com.nzzima.secretmessanger.ui.theme.Ink
import com.nzzima.secretmessanger.ui.theme.InkDim
import com.nzzima.secretmessanger.ui.theme.OwnBubble
import com.nzzima.secretmessanger.ui.theme.Raised
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import com.nzzima.secretmessanger.ui.components.PlayIcon
import com.nzzima.secretmessanger.ui.components.StopIcon
import com.nzzima.secretmessanger.ui.components.PinIcon
import com.nzzima.secretmessanger.ui.theme.PlaceGround
import com.nzzima.secretmessanger.ui.theme.PlacePin
import android.content.pm.PackageManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import com.nzzima.secretmessanger.ui.components.MicIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import com.nzzima.secretmessanger.ui.components.GroupIcon
import com.nzzima.secretmessanger.utils.constants.Constants
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Экран переписки одного диалога.
 *
 * Нижний отступ держит сам: таб-бара здесь нет, а панель ввода обязана вставать над
 * клавиатурой. Из отступа клавиатуры вычтена навигационная полоса — её уже оплатил
 * корневой `Scaffold` в [com.nzzima.secretmessanger.main.ui.RootActivity], и целый
 * `imePadding` поднял бы панель над клавиатурой на высоту полосы. Закрытая клавиатура
 * даёт ноль: вычитание не уходит ниже нуля.
 *
 * @param convoId диалог; приходит аргументом назначения.
 * @param backTitle имя экрана, с которого пришли: в переписку заходят и из «Чатов», и из
 *   профиля собеседника.
 * @param onBack возврат туда же.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessangerScreen(
    convoId: String,
    backTitle: String,
    onBack: () -> Unit,
    onMembers: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessangerViewModel = koinViewModel { parametersOf(convoId) },
) {
    val state by viewModel.observeMessangerScreenState().collectAsStateWithLifecycle()

    val content = state as? MessangerUiState.Content

    // Вышли из группы — возвращаемся к списку сразу: диалога у нас больше нет, и показывать
    // на его месте отказ по правам было бы враньём про поломку.
    LaunchedEffect(content?.left) {
        if (content?.left == true) onBack()
    }

    content?.takeIf { it.askingLeave }?.let {
        AlertDialog(
            onDismissRequest = viewModel::onLeaveDismissed,
            title = { Text(Constants.LEAVE_GROUP_QUESTION) },
            text = { Text(Constants.LEAVE_GROUP_EXPLANATION) },
            confirmButton = {
                TextButton(onClick = viewModel::onLeaveConfirmed) {
                    Text(Constants.LEAVE_GROUP, color = ErrorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onLeaveDismissed) {
                    Text(Constants.CANCEL, color = Accent)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    // Прочтение отмечается, только пока экран на глазах: подписка переживает и уход в фон, и
    // переход дальше по стеку, а метка обязана означать «человек это видел».
    LifecycleResumeEffect(Unit) {
        viewModel.onVisible()
        onPauseOrDispose { viewModel.onHidden() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // Название и подпись в столбик: подпись присутствия рядом с именем не
                    // встаёт, а шапка у центрированного заголовка одна на обе строки.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = content?.title.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        content?.presence?.let {
                            Text(text = it, color = InkDim, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                },
                navigationIcon = { BackButton(backTitle, onBack) },
                actions = {
                    // Состав виден всем участникам, а правит его создатель: экран один, а
                    // кнопки внутри разные — так не приходится объяснять, почему у одних
                    // «Участники» есть, а у других нет.
                    if (content?.isGroup == true) {
                        IconButton(onClick = onMembers) {
                            Icon(
                                imageVector = GroupIcon,
                                contentDescription = Constants.MEMBERS_TITLE,
                                tint = Accent,
                            )
                        }
                    }

                    // Выход только у группы и не у создателя: у диалога на двоих выходить
                    // некуда, а создателю правило это запрещает.
                    if (content?.canLeave == true) {
                        TextButton(onClick = viewModel::onLeaveAsked) {
                            Text(Constants.LEAVE_GROUP, color = ErrorColor, fontSize = 15.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
        ) {
            when (val current = state) {
                MessangerUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                is MessangerUiState.Failed ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (current.canRetry) {
                            FailureNotice(current.message, viewModel::retry)
                        } else {
                            Notice(current.message)
                        }
                    }

                is MessangerUiState.Content -> {
                    ReplyList(
                        replies = current.replies,
                        avatars = current.avatars,
                        photos = current.photos,
                        playing = current.playing,
                        progress = current.progress,
                        onOpenPhoto = viewModel::onPhotoOpened,
                        onPlayVoice = viewModel::onVoicePressed,
                        modifier = Modifier.weight(1f),
                    )

                    current.error?.let { message ->
                        Text(
                            text = message,
                            color = ErrorColor,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE_PADDING),
                        )
                    }

                    InputBar(
                        draft = current.draft,
                        canSend = current.canSend,
                        recordingLeft = current.recordingLeft,
                        onDraftChange = viewModel::onDraftChange,
                        onSend = viewModel::onSend,
                        onPhotoPicked = viewModel::onPhotoPicked,
                        onLocationPicked = viewModel::onLocationPicked,
                        onLocationDenied = viewModel::onLocationDenied,
                        onRecordStart = viewModel::onRecordStart,
                        onRecordFinish = viewModel::onRecordFinish,
                        onRecordCancel = viewModel::onRecordCancel,
                        onMicDenied = viewModel::onMicDenied,
                    )
                }
            }
        }
    }

    (state as? MessangerUiState.Content)?.opened?.let { image ->
        FullscreenPhoto(image, viewModel::onPhotoClosed)
    }
}

/**
 * Снимок на весь экран.
 *
 * Закрывается нажатием куда угодно и системным «назад»: своей кнопки нет — она заняла бы
 * место поверх того, ради чего экран и открыт.
 */
@Composable
private fun FullscreenPhoto(image: ByteArray, onClose: () -> Unit) {
    val bitmap = remember(image) {
        runCatching { BitmapFactory.decodeByteArray(image, 0, image.size) }.getOrNull()
    } ?: return

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = Constants.CLOSE_PHOTO,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Лента реплик.
 *
 * `reverseLayout` вместо прокрутки к концу: лента прирастает снизу, и свежая реплика
 * попадает в кадр сама — как чужая, так и своя.
 */
@Composable
private fun ReplyList(
    replies: List<Reply>,
    avatars: Map<String, ByteArray>,
    photos: Map<String, ByteArray>,
    playing: String?,
    progress: Float,
    onOpenPhoto: (String) -> Unit,
    onPlayVoice: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (replies.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Notice(Constants.MESSAGES_EMPTY)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = SIDE_PADDING, vertical = LIST_PADDING),
        // Прижим к низу задаётся явно: своя расстановка перебивает ту, что идёт с
        // reverseLayout, и короткая переписка повисала вверху экрана вместо панели ввода.
        verticalArrangement = Arrangement.spacedBy(REPLY_GAP, Alignment.Bottom),
    ) {
        items(replies.asReversed(), key = { it.id }) { reply ->
            ReplyRow(
                reply = reply,
                avatar = avatars[reply.authorId],
                photo = photos[reply.id],
                playing = playing == reply.id,
                progress = progress,
                onOpenPhoto = onOpenPhoto,
                onPlayVoice = onPlayVoice,
            )
        }
    }
}

/** Реплика: кружок автора, пузырь со своей стороны, под ним время. */
@Composable
private fun ReplyRow(
    reply: Reply,
    avatar: ByteArray?,
    photo: ByteArray?,
    playing: Boolean,
    progress: Float,
    onOpenPhoto: (String) -> Unit,
    onPlayVoice: (String, Int) -> Unit,
) {
    if (reply.service) {
        Text(
            text = reply.text,
            color = InkDim,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = LIST_PADDING),
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (reply.outgoing) Alignment.End else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            // Кружок стоит у каждого пузыря, в том числе у своего, и прижат к его низу: у
            // длинной реплики автор иначе уезжал бы к первой строке текста.
            if (!reply.outgoing) {
                Avatar(
                    name = reply.authorName,
                    image = avatar,
                    size = BUBBLE_AVATAR,
                    modifier = Modifier.padding(end = AVATAR_GAP),
                )
            }

            Column(
                modifier = Modifier
                    .widthIn(max = BUBBLE_MAX_WIDTH)
                    // Оба пузыря весят одинаково, как на iOS: свой приглушённый синий,
                    // чужой светлее фона. Насыщенный свой делал разговор монологом.
                    .background(
                        if (reply.outgoing) OwnBubble else Raised,
                        RoundedCornerShape(BUBBLE_CORNER),
                    )
                    // У снимка поля тоньше: пузырь вокруг фотографии — это рамка, а не лист.
                    .padding(
                        horizontal = if (reply.photo == null) BUBBLE_PADDING else PHOTO_MARGIN,
                        vertical = if (reply.photo == null) BUBBLE_INNER_PADDING else PHOTO_MARGIN,
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (reply.author.isNotEmpty()) {
                    Text(
                        text = reply.author,
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = if (reply.photo == null) 0.dp else 4.dp),
                    )
                }

                when {
                    reply.photo != null -> PhotoBubble(reply, photo, onOpenPhoto)

                    reply.voice != null -> VoiceBubble(reply, playing, progress, onPlayVoice)

                    reply.place != null -> PlaceBubble(reply.place, reply.text)

                    else -> Text(text = reply.text, color = Ink, fontSize = 16.sp)
                }
            }

            if (reply.outgoing) {
                Avatar(
                    name = reply.authorName,
                    image = avatar,
                    size = BUBBLE_AVATAR,
                    modifier = Modifier.padding(start = AVATAR_GAP),
                )
            }
        }

        // Отступ со стороны кружка держит время под пузырём, а не под кружком.
        Row(
            modifier = Modifier.padding(
                start = if (reply.outgoing) BUBBLE_PADDING else BUBBLE_AVATAR + AVATAR_GAP + BUBBLE_PADDING,
                end = if (reply.outgoing) BUBBLE_AVATAR + AVATAR_GAP + BUBBLE_PADDING else BUBBLE_PADDING,
                top = 2.dp,
                bottom = 2.dp,
            ),
        ) {
            // Табличные цифры: без них время пляшет по горизонтали от реплики к реплике.
            Text(text = shortTime(reply.date), color = InkDim, style = TIME_STYLE)

            // Галочки приписаны к времени, а не занимают свою строку: место под пузырём уже
            // отведено. Одна — «ушло в базу», две — «прочитано». Разделять «отправлено» и
            // «доставлено» тут нечем и незачем: записалось в Firestore — значит дошло до
            // всех, кто откроет чат.
            if (reply.outgoing) {
                Text(
                    text = if (reply.read) Constants.READ_MARK else Constants.SENT_MARK,
                    color = if (reply.read) Accent else InkDim,
                    style = TIME_STYLE,
                )
            }
        }
    }
}

/**
 * Скрепка с меню вложений.
 *
 * Вложения живут под одной кнопкой, а не каждое своей: панель ввода упёрлась бы в место уже
 * на втором виде. Меню открывается нажатием — удержание на iOS занято микрофоном, и путать
 * два жеста значило бы терять записи.
 */
@Composable
private fun AttachButton(
    onPhotoPicked: (String) -> Unit,
    onLocationPicked: () -> Unit,
    onLocationDenied: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    // Системный выборщик картинок разрешений не требует вовсе — приложение получает доступ
    // ровно к тому, что человек выбрал, и только на время выбора.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPhotoPicked(it.toString()) }
    }

    // Точное и грубое спрашиваются парой: человек вправе дать только второе, и грубого места
    // для точки на карте достаточно.
    val place = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) onLocationPicked() else onLocationDenied()
    }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(imageVector = AttachIcon, contentDescription = Constants.ATTACH, tint = Accent)
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(Constants.ATTACH_PHOTO) },
                onClick = {
                    open = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )

            DropdownMenuItem(
                text = { Text(Constants.ATTACH_LOCATION) },
                onClick = {
                    open = false
                    place.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
            )
        }
    }
}

/**
 * Снимок в пузыре.
 *
 * Место под него отводится по размерам из сообщения, ещё до того, как приедут байты: иначе
 * лента дёргалась бы при каждой догрузке. Пока байтов нет, на этом месте стоит подпись
 * «📷 Фото» — ею же остаётся снимок, который не открылся: ключа нужной версии у нас нет, и
 * крутить ожидание вечно значило бы обещать несбыточное.
 */
@Composable
private fun PhotoBubble(reply: Reply, image: ByteArray?, onOpen: (String) -> Unit) {
    val attachment = reply.photo ?: return
    val bitmap = remember(image) {
        image?.let { bytes -> runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() }
    }

    Box(
        modifier = Modifier
            .width(attachment.size.bubbleWidth())
            .aspectRatio(attachment.size.ratio())
            .clip(RoundedCornerShape(PHOTO_CORNER))
            .background(Raised)
            .clickable(enabled = bitmap != null) { onOpen(reply.id) },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = Constants.OPEN_PHOTO,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(text = reply.text, color = InkDim, fontSize = 13.sp)
        }
    }
}

/** Пропорции снимка; нулевой стороны в базе быть не должно, но делить на неё нельзя. */
private fun PhotoSize.ratio(): Float =
    if (width > 0 && height > 0) width.toFloat() / height else 1f

/**
 * Ширина пузыря со снимком.
 *
 * У вертикального кадра её задаёт потолок высоты, а не ширины: при ширине по горизонтальному
 * снимок 9:16 вырастал на пол-экрана и выталкивал из кадра собственное время и кружок автора.
 */
private fun PhotoSize.bubbleWidth(): Dp {
    val ratio = ratio()

    return if (ratio >= 1f) PHOTO_WIDTH else PHOTO_MAX_HEIGHT * ratio
}

/**
 * Микрофон: удержание записывает, отпускание отправляет.
 *
 * Удержание, а не нажатие, — как на iOS: короткая запись начинается и кончается одним жестом,
 * и отдельного «стоп» не нужно. Уведённый с кнопки палец запись **бросает**: передумать после
 * первых слов человек вправе, и отправлять их было бы грубо.
 */
@Composable
private fun MicButton(
    recording: Boolean,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onDenied: () -> Unit,
) {
    val context = LocalContext.current

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) onDenied()
    }

    val allowed = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    Icon(
        imageVector = MicIcon,
        contentDescription = Constants.RECORD_VOICE,
        tint = if (recording) ErrorColor else Accent,
        modifier = Modifier
            .size(MIC_BUTTON)
            .padding(8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    // Разрешение спрашивается на первом удержании, а не при открытии экрана:
                    // спрашивать микрофон у того, кто просто зашёл почитать, незачем.
                    onPress = {
                        if (!allowed()) {
                            permission.launch(Manifest.permission.RECORD_AUDIO)
                            return@detectTapGestures
                        }

                        onStart()
                        if (tryAwaitRelease()) onFinish() else onCancel()
                    },
                )
            },
    )
}

/**
 * Голосовое в пузыре: кнопка, полоска хода и длительность.
 *
 * Звук приезжает только по нажатию — качать все записи подряд значило бы платить за
 * неслушанное. Та же кнопка и останавливает: отдельной «стоп» нет, это одно и то же действие.
 */
@Composable
private fun VoiceBubble(reply: Reply, playing: Boolean, progress: Float, onPlay: (String, Int) -> Unit) {
    val voice = reply.voice ?: return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.width(VOICE_WIDTH),
    ) {
        Icon(
            imageVector = if (playing) StopIcon else PlayIcon,
            contentDescription = if (playing) Constants.STOP_VOICE else Constants.PLAY_VOICE,
            tint = Ink,
            modifier = Modifier
                .size(VOICE_BUTTON)
                .clip(CircleShape)
                .clickable { onPlay(reply.id, voice.keyVersion) }
                .padding(4.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { if (playing) progress else 0f },
                color = Ink,
                trackColor = InkDim,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(text = voice.seconds.asDuration(), color = InkDim, style = TIME_STYLE)
        }
    }
}

/** Длительность в виде «0:07»: секунды с ведущим нулём, минуты без. */
private fun Double.asDuration(): String {
    val total = toInt().coerceAtLeast(0)

    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

/**
 * Точка на карте.
 *
 * **Настоящей карты здесь нет и быть не может.** Офлайн у приложения нет ни тайлов, ни
 * картографических данных, а тянуть их из сети значило бы рассказывать чужому серверу, где
 * находится собеседник, — на этом и держалось решение обойтись без Maps SDK. Подложка
 * нарисована кодом и честно декоративна: она даёт пузырю вес и читается как место с одного
 * взгляда, а само место показывают координаты и — по нажатию — настоящие карты телефона.
 */
@Composable
private fun PlaceBubble(place: Place, label: String) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.clickable {
            val point = Place.payload(place.latitude, place.longitude)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$point?q=$point"))

            // Карты может не быть вовсе — тогда нажатие просто ничего не делает.
            runCatching { context.startActivity(intent) }
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(PHOTO_WIDTH)
                .height(PLACE_HEIGHT)
                .clip(RoundedCornerShape(PHOTO_CORNER))
                .background(PlaceGround),
            contentAlignment = Alignment.Center,
        ) {
            PlaceGrid(place)

            Icon(
                imageVector = PinIcon,
                contentDescription = label,
                tint = PlacePin,
                modifier = Modifier.size(PIN_SIZE),
            )
        }

        Text(text = label, color = Ink, fontSize = 15.sp)

        // На экране запятая с пробелом, в базе — без: там это разделитель пары, а не знак
        // препинания.
        Text(
            text = Place.payload(place.latitude, place.longitude).replace(",", ", "),
            color = InkDim,
            style = TextStyle(fontSize = 12.sp, fontFeatureSettings = "tnum"),
        )
    }
}

/**
 * Подложка под булавкой: сетка «кварталов».
 *
 * Расположение линий выведено из самих координат — у двух разных мест рисунок разный. Это не
 * география, а лишь способ не показывать одну и ту же картинку у всех точек: выдавать сетку
 * за карту было бы враньём, а одинаковая подложка выглядела бы заглушкой.
 */
@Composable
private fun PlaceGrid(place: Place) {
    val ground = InkDim

    Canvas(modifier = Modifier.fillMaxSize()) {
        val step = size.height / GRID_ROWS
        val shift = ((place.latitude + place.longitude) % 1.0).toFloat() * step

        for (row in 0..GRID_ROWS) {
            val y = row * step + shift
            drawLine(ground, Offset(0f, y), Offset(size.width, y), GRID_STROKE, alpha = GRID_ALPHA)
        }

        for (column in 0..GRID_COLUMNS) {
            val x = column * (size.width / GRID_COLUMNS) + shift
            drawLine(ground, Offset(x, 0f), Offset(x, size.height), GRID_STROKE, alpha = GRID_ALPHA)
        }
    }
}

/**
 * Панель ввода.
 *
 * Поле — то же, что на авторизации, но с заглавной после точки и автозаменой: здесь
 * пишут предложения, а не логины.
 */
@Composable
private fun InputBar(
    draft: String,
    canSend: Boolean,
    recordingLeft: Int?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onPhotoPicked: (String) -> Unit,
    onLocationPicked: () -> Unit,
    onLocationDenied: () -> Unit,
    onRecordStart: () -> Unit,
    onRecordFinish: () -> Unit,
    onRecordCancel: () -> Unit,
    onMicDenied: () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.surface)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE_PADDING, vertical = LIST_PADDING),
        verticalAlignment = Alignment.Bottom,
    ) {
        AttachButton(onPhotoPicked, onLocationPicked, onLocationDenied)

        if (recordingLeft == null) {
            Field(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = Constants.MESSAGE_PLACEHOLDER,
                modifier = Modifier.weight(1f),
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrect = true,
                maxLines = INPUT_MAX_LINES,
            )
        } else {
            // Счёт идёт вниз: важно, сколько ещё можно говорить, а не сколько уже сказано.
            Text(
                text = "${Constants.RECORDING} $recordingLeft",
                color = ErrorColor,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f).padding(start = 8.dp, bottom = 12.dp),
            )
        }

        // Микрофон стоит на месте стрелки, а не рядом: обе кнопки означают «отправить
        // сейчас», и держать их одновременно — значит спорить с шириной панели. Пустое поле
        // отправлять нечем, поэтому там микрофон; появился текст — появилась стрелка.
        if (draft.isBlank()) {
            MicButton(recordingLeft != null, onRecordStart, onRecordFinish, onRecordCancel, onMicDenied)
        } else {
            IconButton(onClick = onSend, enabled = canSend) {
                Icon(
                    imageVector = SendIcon,
                    contentDescription = Constants.SEND_MESSAGE,
                    tint = if (canSend) Accent else InkDim,
                )
            }
        }
    }
}

private val TIME_STYLE = TextStyle(fontSize = 10.sp, fontFeatureSettings = "tnum")
private val SIDE_PADDING = 12.dp
private val LIST_PADDING = 8.dp
private val REPLY_GAP = 6.dp
private val BUBBLE_MAX_WIDTH = 260.dp
private val BUBBLE_AVATAR = 30.dp
private val PHOTO_WIDTH = 220.dp
private val PHOTO_MAX_HEIGHT = 260.dp
private val PLACE_HEIGHT = 120.dp
private val VOICE_WIDTH = 180.dp
private val VOICE_BUTTON = 32.dp
private val MIC_BUTTON = 48.dp
private val PIN_SIZE = 34.dp
private const val GRID_ROWS = 4
private const val GRID_COLUMNS = 6
private const val GRID_STROKE = 1.5f
private const val GRID_ALPHA = 0.35f
private val PHOTO_CORNER = 11.dp
private val PHOTO_MARGIN = 4.dp
private val AVATAR_GAP = 8.dp
private val BUBBLE_CORNER = 15.dp
private val BUBBLE_PADDING = 12.dp
private val BUBBLE_INNER_PADDING = 8.dp
private const val INPUT_MAX_LINES = 5
