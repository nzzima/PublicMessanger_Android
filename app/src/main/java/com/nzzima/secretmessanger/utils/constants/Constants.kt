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
    const val EXPIRED_ROUTE = "expired"
    const val IDENTITY_ROUTE = "identity"
    const val REPAIR_ROUTE = "repair"
    const val LOADING_ROUTE = "loading"
    const val MAIN_ROUTE = "main"

    const val CONTACTS_ROUTE = "contacts"
    const val CHATS_ROUTE = "chats"
    const val PROFILE_ROUTE = "profile"
    const val MESSANGER_ROUTE = "messanger"
    const val CONVO_ID_ARGUMENT = "convoId"

    const val LOGIN_MIN_LENGTH = 3
    const val LOGIN_MAX_LENGTH = 20
    const val PASSWORD_MIN_LENGTH = 6

    const val SUBMIT_TIMEOUT_MS = 20_000L

    const val MESSAGE_WINDOW = 50L

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
    const val NO_CONVERSATION_KEY = "Ключ этого диалога вам ещё не выдан"
    const val CONVERSATION_GONE = "Диалог удалён"

    const val CONTACTS_TITLE = "Контакты"
    const val CONTACTS_EMPTY = "Кроме вас здесь пока никого нет"

    const val PROFILE_MISSING = "Профиль этого аккаунта не найден"

    const val PROFILE_TITLE = "Профиль"
    const val PROFILE_LOGIN = "Логин"
    const val PROFILE_NAME = "Имя"
    const val PROFILE_IDENTIFIER = "Идентификатор"

    const val SIGN_OUT = "Выйти"

    const val REPAIR_TITLE = "Регистрация не завершена"
    const val REPAIR_EXPLANATION = "Аккаунт создан, но логин за ним не закреплён — регистрация оборвалась на полпути. Выберите логин, и вход продолжится. Прежний, если он остался за вами, тоже подойдёт."
    const val REPAIR_SUBMIT = "Продолжить"

    const val IDENTITY_TITLE = "Ключ этого аккаунта"
    const val IDENTITY_WARNING = "У этого аккаунта уже есть ключ шифрования, заведённый на другом устройстве. Перенести его сюда нечем. Продолжить можно только со своим ключом — тогда прежняя переписка не откроется ни здесь, ни на том устройстве."
    const val IDENTITY_CONTINUE = "Продолжить со своим ключом"
    const val RETRY = "Повторить"

    const val SESSION_EXPIRED = "Вход устарел — войдите заново"
    const val SESSION_EXPIRED_TITLE = "Вход устарел"
    const val SESSION_EXPIRED_EXPLANATION = "Сервис больше не признаёт вход с этого устройства: пароль могли сменить, а сам вход — устареть от долгого перерыва. Ключ и переписка на месте, нужен только новый вход."
    const val SESSION_EXPIRED_SUBMIT = "Войти заново"
}
