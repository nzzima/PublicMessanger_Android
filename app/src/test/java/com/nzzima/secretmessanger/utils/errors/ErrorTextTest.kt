package com.nzzima.secretmessanger.utils.errors

import com.nzzima.secretmessanger.chats.domain.models.CannotErase
import com.nzzima.secretmessanger.utils.constants.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Проверяется не перевод как таковой, а договор: на экран не уходит ни одной английской строки.
 * Поэтому главные проверки здесь — про неразобранный код и чужое исключение, а не про удачные
 * случаи.
 *
 * Разбор берётся по строке кода: сами классы исключений Firebase в JVM-тесте не создаются —
 * `ExceptionInInitializerError` ещё на конструкторе.
 */
class ErrorTextTest {

    @Test
    fun `код базы переводится`() {
        assertEquals(Constants.ACTION_NOT_ALLOWED, ErrorText.ofFirestoreCode("PERMISSION_DENIED"))
        assertEquals(Constants.ALREADY_DELETED, ErrorText.ofFirestoreCode("NOT_FOUND"))
        assertEquals(Constants.NO_CONNECTION, ErrorText.ofFirestoreCode("UNAVAILABLE"))
    }

    @Test
    fun `код входа переводится`() {
        assertEquals(Constants.INVALID_EMAIL, ErrorText.ofAuthCode("ERROR_INVALID_EMAIL"))
        assertEquals(Constants.EMAIL_TAKEN, ErrorText.ofAuthCode("ERROR_EMAIL_ALREADY_IN_USE"))
    }

    /** Firebase с защитой от перебора отвечает одинаково на неверный пароль и на чужую почту. */
    @Test
    fun `неверный пароль и негодные данные говорят одно и то же`() {
        assertEquals(
            ErrorText.ofAuthCode("ERROR_WRONG_PASSWORD"),
            ErrorText.ofAuthCode("ERROR_INVALID_CREDENTIAL"),
        )
    }

    /** Отсутствие индекса чинит разработчик — спутать его с обрывом связи значит искать не там. */
    @Test
    fun `нехватка индекса не выглядит обрывом связи`() {
        assertNotEquals(
            ErrorText.ofFirestoreCode("FAILED_PRECONDITION"),
            ErrorText.ofFirestoreCode("UNAVAILABLE"),
        )
    }

    @Test
    fun `неразобранный код базы не протекает английским`() {
        assertEquals(Constants.SERVER_SILENT, ErrorText.ofFirestoreCode("INTERNAL"))
    }

    /** Ветка ради будущих версий SDK: новый код входа свой текст на экран не отдаст. */
    @Test
    fun `неразобранный код входа не протекает английским`() {
        assertEquals(Constants.SERVER_SILENT, ErrorText.ofAuthCode("ERROR_SOMETHING_NEW"))
    }

    /** Свои исключения несут русский текст и точнее любого разбора по коду. */
    @Test
    fun `своё исключение проходит как есть`() {
        assertEquals(Constants.CANNOT_ERASE, ErrorText.of(CannotErase()))
    }

    /** Чужому исключению текст не доверяется: он был бы английским. */
    @Test
    fun `чужое исключение не отдаёт свой текст`() {
        assertEquals(Constants.SERVER_SILENT, ErrorText.of(IllegalStateException("Something went wrong")))
    }

    @Test
    fun `без ошибки текст всё равно есть`() {
        assertEquals(Constants.SERVER_SILENT, ErrorText.of(null))
    }

    @Test
    fun `запасной текст берётся только на неразобранной ошибке`() {
        assertEquals(Constants.CANNOT_ERASE, ErrorText.of(CannotErase(), "Аватар не сохранился"))
        assertEquals("Аватар не сохранился", ErrorText.of(IllegalStateException("boom"), "Аватар не сохранился"))
    }
}
