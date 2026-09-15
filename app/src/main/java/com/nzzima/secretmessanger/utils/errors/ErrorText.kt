package com.nzzima.secretmessanger.utils.errors

import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Русский текст ошибки для показа человеку.
 *
 * До этого в состояние экрана уходило `error.message`, а у Firebase оно английское и написано
 * для разработчика: «The supplied auth credential is malformed or has expired» посреди русского
 * интерфейса. Из такого текста не следует, что делать дальше.
 *
 * Держится всё на двух ветках: [FirebaseException] в конце разбора отдаёт общий русский текст
 * любой ошибке Firebase, которой нет в списках, а чужое исключение не из наших пакетов не
 * отдаёт свой текст вовсе. Английскому протечь неоткуда — даже из следующей версии SDK.
 *
 * Разбор по коду вынесен в [ofFirestoreCode] и [ofAuthCode], которые принимают **строку**, а не
 * тип Firebase: классы исключений Firebase в JVM-тесте не создаются вовсе
 * (`ExceptionInInitializerError`), и с типами в сигнатуре проверить перевод было бы нечем.
 * Непроверенным остаётся только сам переходник [of] — `when` по типам без единого ветвления
 * внутри.
 *
 * Тот же разбор на iOS — `ErrorText.swift`, тексты совпадают дословно.
 */
object ErrorText {

    /**
     * Что показать вместо [error].
     *
     * Свои исключения несут русский текст из [Constants] и проходят как есть — они точнее
     * любого разбора по коду. Чужие, кроме Firebase, до экрана не доходят, но и им текст не
     * доверяется: он был бы английским.
     */
    fun of(error: Throwable?): String = when (error) {
        null -> Constants.SERVER_SILENT

        is FirebaseFirestoreException -> ofFirestoreCode(error.code.name)

        is FirebaseAuthException -> ofAuthCode(error.errorCode)

        is FirebaseNetworkException -> Constants.NO_CONNECTION

        is FirebaseException -> Constants.SERVER_SILENT

        else -> ownText(error)
    }

    /**
     * То же с запасным текстом [fallback] на случай неразобранной ошибки.
     *
     * Вызывающий знает, что именно не вышло, а разбор по коду — нет: «Аватар не сохранился»
     * полезнее общего «сервер не ответил».
     */
    fun of(error: Throwable?, fallback: String): String =
        of(error).takeIf { it != Constants.SERVER_SILENT } ?: fallback

    /**
     * Текст по имени кода базы — значению [FirebaseFirestoreException.Code].
     *
     * `PERMISSION_DENIED` — не «нет прав» дословно: правила отказывают там, где человек делает
     * не положенное ему: стирает чужую группу, пишет в диалог, из которого его убрали. Текст
     * поэтому про действие.
     *
     * `FAILED_PRECONDITION` вынесен отдельно: так приезжает запрос без составного индекса, и
     * чинит это разработчик, а не человек с телефоном. Спутать его с обрывом связи — значит
     * искать поломку не там.
     */
    fun ofFirestoreCode(code: String): String = when (code) {
        "PERMISSION_DENIED" -> Constants.ACTION_NOT_ALLOWED
        "UNAVAILABLE", "DEADLINE_EXCEEDED" -> Constants.NO_CONNECTION
        "NOT_FOUND" -> Constants.ALREADY_DELETED
        "UNAUTHENTICATED" -> Constants.SESSION_EXPIRED_TEXT
        "RESOURCE_EXHAUSTED" -> Constants.QUOTA_EXCEEDED
        "CANCELLED", "ABORTED" -> Constants.NOT_FINISHED
        "FAILED_PRECONDITION" -> Constants.INDEX_MISSING
        else -> Constants.SERVER_SILENT
    }

    /**
     * Текст по коду входа — значению `FirebaseAuthException.errorCode`.
     *
     * «Нет такого аккаунта» и «неверный пароль» Firebase перестал разделять сам: с защитой от
     * перебора оба случая приезжают как `ERROR_INVALID_CREDENTIAL`, поэтому текст общий.
     */
    fun ofAuthCode(code: String): String = when (code) {
        "ERROR_INVALID_EMAIL" -> Constants.INVALID_EMAIL
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> Constants.WRONG_CREDENTIALS
        "ERROR_USER_NOT_FOUND" -> Constants.NO_SUCH_ACCOUNT
        "ERROR_EMAIL_ALREADY_IN_USE" -> Constants.EMAIL_TAKEN
        "ERROR_WEAK_PASSWORD" -> Constants.SHORT_PASSWORD
        "ERROR_USER_DISABLED" -> Constants.ACCOUNT_DISABLED
        "ERROR_TOO_MANY_REQUESTS" -> Constants.TOO_MANY_ATTEMPTS
        "ERROR_NETWORK_REQUEST_FAILED" -> Constants.NO_CONNECTION
        "ERROR_USER_TOKEN_EXPIRED", "ERROR_INVALID_USER_TOKEN" -> Constants.SESSION_EXPIRED_TEXT
        "ERROR_REQUIRES_RECENT_LOGIN" -> Constants.NEEDS_RECENT_LOGIN
        else -> Constants.SERVER_SILENT
    }

    /**
     * Текст своего исключения; чужому — общий.
     *
     * «Своё» определяется по пакету, а не по списку типов: список пришлось бы дописывать при
     * каждом новом исключении, и забытое отдало бы на экран английский текст библиотеки.
     */
    private fun ownText(error: Throwable): String =
        error.takeIf { it.javaClass.name.startsWith(OWN_PACKAGE) }
            ?.message
            ?.takeIf(String::isNotBlank)
            ?: Constants.SERVER_SILENT

    private const val OWN_PACKAGE = "com.nzzima.secretmessanger"
}
