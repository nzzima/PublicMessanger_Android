package com.nzzima.secretmessanger.utils.constants

/**
 * Значения, общие для нескольких слоёв.
 *
 * Имена коллекций и полей Firestore входят в схему, общую с iOS-приложением: их изменение
 * разрывает совместимость платформ. Тексты отказов и подписи экранов совпадают дословно с
 * `FieldValidator` и `RegistrationViewPresenter` на iOS.
 */
object Constants {

    const val LOGINS_COLLECTION = "logins"
    const val USERS_COLLECTION = "users"
    const val CONVERSATION_COLLECTION = "conversation"
    const val MESSAGES_COLLECTION = "messages"
    const val AVATARS_COLLECTION = "avatars"
    const val IMAGES_COLLECTION = "images"
    const val AUDIO_COLLECTION = "audio"
    const val PRESENCE_COLLECTION = "presence"

    const val UID_FIELD = "uid"
    const val LOGIN_FIELD = "login"
    const val NAME_FIELD = "name"
    const val SOME_INFO_FIELD = "someInfo"
    const val PUBLIC_KEY_FIELD = "publicKey"

    const val USERS_FIELD = "users"
    const val LOGINS_FIELD = "logins"
    const val OWNER_FIELD = "owner"
    const val CONVO_KEYS_FIELD = "convoKeys"
    const val KEY_VERSION_FIELD = "keyVersion"
    const val LAST_MESSAGE_FIELD = "lastMessage"
    const val LAST_ENCRYPTED_FIELD = "lastEnc"
    const val LAST_VERSION_FIELD = "lastV"
    const val DATE_FIELD = "date"
    const val READ_UP_TO_FIELD = "readUpTo"

    const val IMAGE_DATA_FIELD = "data"
    const val LAST_SEEN_FIELD = "lastSeen"
    const val DURATION_FIELD = "duration"
    const val WIDTH_FIELD = "width"
    const val HEIGHT_FIELD = "height"

    const val AVATAR_DATA_FIELD = "data"
    const val AVATAR_VERSION_FIELD = "version"
    const val PROFILE_AVATAR_VERSION_FIELD = "avatarVersion"

    const val SENDER_ID_FIELD = "senderId"
    const val MESSAGE_FIELD = "message"
    const val ENCRYPTED_FIELD = "enc"
    const val VERSION_FIELD = "v"
    const val TYPE_FIELD = "type"

    const val VOICE_TYPE = "audio"
    const val PHOTO_TYPE = "image"
    const val LOCATION_TYPE = "location"
    const val KEY_NOTICE_TYPE = "keyRotated"

    const val IDENTITY_PREFERENCES = "com.nzzima.secretmessanger.identity"
    const val IDENTITY_ENTRY_PREFIX = "identity."
    const val MASTER_KEY_ALIAS = "com.nzzima.secretmessanger.master"

    const val AUTH_ROUTE = "auth"
    const val LOCK_ROUTE = "lock"
    const val EXPIRED_ROUTE = "expired"
    const val IDENTITY_ROUTE = "identity"
    const val REPAIR_ROUTE = "repair"
    const val LOADING_ROUTE = "loading"
    const val MAIN_ROUTE = "main"

    const val CONTACTS_ROUTE = "contacts"
    const val CHATS_ROUTE = "chats"
    const val PROFILE_ROUTE = "profile"
    const val MESSANGER_ROUTE = "messanger"
    const val USER_ROUTE = "user"
    const val EDIT_ROUTE = "edit"
    const val NEW_GROUP_ROUTE = "newGroup"
    const val CONVO_ID_ARGUMENT = "convoId"
    const val USER_ID_ARGUMENT = "userId"
    const val LOGIN_ARGUMENT = "login"

    const val LOGIN_MIN_LENGTH = 3
    const val LOGIN_MAX_LENGTH = 20
    const val PASSWORD_MIN_LENGTH = 6

    const val SUBMIT_TIMEOUT_MS = 20_000L

    const val MESSAGE_WINDOW = 50L

    const val FIRST_KEY_VERSION = 1

    /**
     * Сколько можно отсутствовать, чтобы приложение не переспрашивало замок.
     *
     * Без порога биометрия срабатывала бы на каждое переключение — сходить за ссылкой,
     * ответить на звонок, посмотреть код из СМС. Приложение, которое переспрашивает по десять
     * раз на дню, выключают целиком, и защиты от него остаётся ноль. Минута — это «отвлёкся»,
     * а не «ушёл».
     */
    const val LOCK_GRACE_MS = 60_000L

    /** Как часто приложение отмечается, пока оно на экране. */
    const val PRESENCE_HEARTBEAT_MS = 30_000L

    /**
     * Сколько человек ещё считается сетевым после последнего удара пульса.
     *
     * Больше двух ударов, а не одного: пропущенный удар — обычное дело в метро и в лифте, и
     * с окном в один удар собеседник мигал бы серым на ровном месте. Плата честная — ушедший
     * ещё минуту числится в сети.
     */
    const val PRESENCE_WINDOW_MS = 70_000L

    /** Срок ожидания точки: дольше человек уже не ждёт, а GPS в помещении молчит вечно. */
    const val LOCATION_TIMEOUT_MS = 8_000L

    /** Насколько старой точке ещё верим без нового замера. */
    const val LOCATION_FRESH_MS = 5 * 60 * 1000L

    /**
     * Потолок записи. Держит он не вежливость, а лимит документа Firestore в мебибайт: две
     * минуты в этих настройках — около 360 КБ, треть лимита. Запас нужен потому, что упереться
     * в лимит значит потерять уже наговорённое на отправке.
     */
    const val VOICE_MAX_MS = 120_000L

    /** Моно, 24 кГц и 24 кбит/с — это речь, а не музыка; тот же кодек, что пишет iOS. */
    const val VOICE_SAMPLE_RATE = 24_000
    const val VOICE_BIT_RATE = 24_000

    const val PHOTO_SIDE = 1280
    const val PHOTO_BUDGET = 700_000
    const val PHOTO_CACHE_SIZE = 20

    const val AVATAR_SIDE = 320
    const val AVATAR_BUDGET = 200_000
    const val AVATAR_CACHE_SIZE = 100

    const val EMAIL_TAKEN = "Эта почта уже занята"
    const val WEAK_PASSWORD = "Пароль слишком простой"
    const val WRONG_CREDENTIALS = "Неверная почта или пароль"
    const val LOGIN_TAKEN = "Логин уже занят — выберите другой"
    const val INVALID_EMAIL = "Проверьте адрес почты"
    const val INVALID_LOGIN = "Логин — от 3 до 20 символов: латиница, цифры, подчёркивание"
    const val SHORT_PASSWORD = "Пароль должен быть не короче 6 символов"
    const val PASSWORDS_MISMATCH = "Пароли не совпадают"
    const val SERVER_SILENT = "Сервер не ответил. Проверьте связь и попробуйте снова"
    const val MALFORMED_PAYLOAD = "Не удалось разобрать зашифрованные данные"
    const val WRONG_KEY = "Сообщение зашифровано другим ключом"

    const val AUTH_TITLE = "Авторизация"
    const val REGISTER_TITLE = "Регистрация"
    const val SIGN_IN_SUBMIT = "Войти"
    const val REGISTER_SUBMIT = "Зарегистрироваться"
    const val SWITCH_TO_REGISTER = "Нет аккаунта? Зарегистрироваться"
    const val SWITCH_TO_SIGN_IN = "Уже есть аккаунт? Войти"

    const val EMAIL_PLACEHOLDER = "Email"
    const val LOGIN_PLACEHOLDER = "Логин"
    const val PASSWORD_PLACEHOLDER = "Пароль"
    const val PASSWORD_REPEAT_PLACEHOLDER = "Повторите пароль"

    const val CHATS_TITLE = "Чаты"
    const val CHATS_EMPTY = "Диалогов пока нет"
    const val UNREADABLE = "🔒 Сообщение не расшифровано"

    const val MESSAGES_EMPTY = "Здесь пока ничего не написано"
    const val MESSAGE_PLACEHOLDER = "Сообщение"
    const val SEND_MESSAGE = "Отправить"
    const val BACK = "Назад"
    const val VOICE_MESSAGE = "🎤 Голосовое сообщение"
    const val PHOTO_MESSAGE = "📷 Фото"
    const val LOCATION_MESSAGE = "📍 Геопозиция"
    const val KEY_ROTATED = "ключ обновлён"
    const val SENT_MARK = " ✓"
    const val READ_MARK = " ✓✓"
    const val NO_CONVERSATION_KEY = "Ключ этого диалога вам ещё не выдан"
    const val CONVERSATION_GONE = "Диалог удалён"

    const val CONTACTS_TITLE = "Контакты"
    const val CONTACTS_EMPTY = "Кроме вас здесь пока никого нет"
    const val COMPANION_KEY_MISSING = "Ключа шифрования у собеседника нет — переписку с ним завести нечем"

    const val PROFILE_MISSING = "Профиль этого аккаунта не найден"

    const val PROFILE_TITLE = "Профиль"
    const val PROFILE_LOGIN = "Логин"
    const val PROFILE_NAME = "Имя"
    const val PROFILE_IDENTIFIER = "Идентификатор"
    const val PROFILE_NOTE = "Заметка"
    const val WRITE_MESSAGE = "Написать"
    const val EDIT_PROFILE = "Изменить"
    const val EDIT_PROFILE_TITLE = "Редактирование"
    const val SAVE = "Сохранить"
    const val NOTE_PLACEHOLDER = "Заметка о себе"
    const val GROUP_CHAT = "Группа"
    const val NEW_GROUP = "Новая группа"
    const val NEW_GROUP_TITLE = "Новая группа"
    const val NEW_GROUP_HINT = "Отметьте двоих или больше: с одним это обычный диалог"
    const val CREATE_GROUP = "Создать"
    const val CHOSEN_MARK = "✓"
    const val ONLINE = "в сети"
    const val LOCK_TITLE = "Подтвердите, что это вы"
    const val LOCK_HINT = "Переписка откроется после подтверждения"
    const val LOCK_UNLOCK = "Подтвердить"
    const val LOCK_FAILED = "Подтвердить не вышло"
    const val ATTACH = "Прикрепить"
    const val RECORD_VOICE = "Записать голосовое"
    const val RECORDING = "Запись…"
    const val RECORD_HINT = "Удерживайте, чтобы записать"
    const val MIC_DENIED = "Без доступа к микрофону записывать нечем"
    const val VOICE_FAILED = "Запись не удалась"
    const val PLAY_VOICE = "Слушать"
    const val STOP_VOICE = "Остановить"
    const val ATTACH_PHOTO = "Фото"
    const val ATTACH_LOCATION = "Геопозиция"
    const val PHOTO_TOO_LARGE = "Это изображение не удалось уместить в размер сообщения"
    const val PLACE_UNKNOWN = "Телефон не смог определить место"
    const val PLACE_DENIED = "Без доступа к месту отправить точку нечем"
    const val OPEN_PHOTO = "Открыть снимок"
    const val CLOSE_PHOTO = "Закрыть снимок"
    const val CHANGE_AVATAR = "Изменить аватар"
    const val REMOVE_AVATAR = "Убрать фото"
    const val AVATAR_TOO_LARGE = "Это изображение не удалось уместить в размер аватара"
    const val SIGN_OUT_QUESTION = "Вы действительно хотите выйти?"
    const val SIGN_OUT_CONFIRM = "Выйти из аккаунта"
    const val CANCEL = "Отмена"

    const val SIGN_OUT = "Выйти"

    const val REPAIR_TITLE = "Регистрация не завершена"
    const val REPAIR_EXPLANATION = "Аккаунт создан, но логин за ним не закреплён — регистрация оборвалась на полпути. Выберите логин, и вход продолжится. Прежний, если он остался за вами, тоже подойдёт."
    const val REPAIR_SUBMIT = "Продолжить"

    const val IDENTITY_TITLE = "Ключ этого аккаунта"
    const val IDENTITY_WARNING = "У этого аккаунта уже есть ключ шифрования, а на этом устройстве его нет — перенести ключ нечем. Продолжить можно только с новым ключом: прежняя переписка тогда не откроется ни здесь, ни там, где остался старый."
    const val IDENTITY_CONTINUE = "Продолжить со своим ключом"
    const val RETRY = "Повторить"

    const val SESSION_EXPIRED = "Вход устарел — войдите заново"
    const val SESSION_EXPIRED_TITLE = "Вход устарел"
    const val SESSION_EXPIRED_EXPLANATION = "Сервис больше не признаёт вход с этого устройства: пароль могли сменить, а сам вход — устареть от долгого перерыва. Ключ и переписка на месте, нужен только новый вход."
    const val SESSION_EXPIRED_SUBMIT = "Войти заново"
}
