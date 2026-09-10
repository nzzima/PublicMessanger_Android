package com.nzzima.secretmessanger.profile.ui

import com.nzzima.secretmessanger.profile.domain.FakeProfileReader
import com.nzzima.secretmessanger.profile.domain.api.ProfileEditor
import com.nzzima.secretmessanger.profile.domain.impl.ProfileInteractorImpl
import com.nzzima.secretmessanger.profile.domain.models.Profile
import com.nzzima.secretmessanger.session.domain.FakeSessionRepository
import com.nzzima.secretmessanger.session.domain.impl.SessionInteractorImpl
import com.nzzima.secretmessanger.session.domain.models.Session
import com.nzzima.secretmessanger.utils.constants.Constants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Форма правки профиля: заполнение, проверка логина, сохранение и выход. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sessions = FakeSessionRepository(Session.Authenticated("uid-1"))
    private val profiles = FakeProfileReader()
    private val editor = FakeProfileEditor()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = EditProfileViewModel(
        SessionInteractorImpl(sessions, sessions, sessions),
        ProfileInteractorImpl(profiles),
        editor,
    )

    private fun EditProfileViewModel.form() = observeEditProfileScreenState().value as EditProfileUiState.Form

    /** Форма с прочитанным профилем. */
    private fun opened(): EditProfileViewModel {
        profiles.send(Profile(id = "uid-1", login = "blue", name = "Никита", someInfo = "заметка"))

        return viewModel().also { dispatcher.scheduler.advanceUntilIdle() }
    }

    @Test
    fun `форма заполняется прочитанным профилем`() = runTest(dispatcher) {
        val model = opened()

        assertEquals("blue", model.form().login)
        assertEquals("Никита", model.form().name)
        assertEquals("заметка", model.form().someInfo)
    }

    @Test
    fun `сохранение передаёт логин, каким он был при открытии`() = runTest(dispatcher) {
        val model = opened()

        model.onLoginChange("red")
        model.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(Saved(uid = "uid-1", login = "red", name = "Никита", someInfo = "заметка", currentLogin = "blue")),
            editor.saves,
        )
        assertTrue("экран обязан вернуться", model.form().saved)
    }

    @Test
    fun `негодный логин отбивается до всякой записи`() = runTest(dispatcher) {
        val model = opened()

        model.onLoginChange("ab")
        model.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.INVALID_LOGIN, model.form().error)
        assertTrue("в базу ничего уходить не должно", editor.saves.isEmpty())
        assertFalse(model.form().saved)
    }

    @Test
    fun `логин запоминается без окружающих пробелов`() = runTest(dispatcher) {
        val model = opened()

        model.onLoginChange("  red  ")

        assertEquals("red", model.form().login)
    }

    @Test
    fun `правка поля снимает показанную ошибку`() = runTest(dispatcher) {
        val model = opened()
        model.onLoginChange("ab")
        model.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        model.onNameChange("Никита К.")

        assertNull(model.form().error)
    }

    @Test
    fun `занятое имя показывается строкой и на экране оставляет`() = runTest(dispatcher) {
        val model = opened()
        editor.refusal = IllegalStateException(Constants.LOGIN_TAKEN)

        model.onLoginChange("red")
        model.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.LOGIN_TAKEN, model.form().error)
        assertFalse(model.form().saved)
    }

    @Test
    fun `молчащая сеть снимает сохранение по таймауту`() = runTest(dispatcher) {
        val model = opened()
        editor.hang()

        model.onSave()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Constants.SERVER_SILENT, model.form().error)
        assertFalse(model.form().isSaving)
    }

    @Test
    fun `второе нажатие во время сохранения не принимается`() = runTest(dispatcher) {
        val model = opened()
        val pending = editor.hang()

        model.onSave()
        // Только текущие задачи: advanceUntilIdle прокрутил бы время за таймаут.
        dispatcher.scheduler.runCurrent()
        model.onSave()
        dispatcher.scheduler.runCurrent()

        assertTrue(model.form().isSaving)
        assertEquals(1, editor.saves.size)

        pending.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `выход завершает сессию`() = runTest(dispatcher) {
        val model = opened()

        model.onSignOut()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(Session.Anonymous, sessions.session.value)
    }

    @Test
    fun `профиля нет — форма не открывается`() = runTest(dispatcher) {
        profiles.fail(IllegalStateException("профиля нет"))
        val model = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        val state = model.observeEditProfileScreenState().value as EditProfileUiState.Failed

        assertEquals("профиля нет", state.message)
    }
}

/** Что ушло в сохранение. */
private data class Saved(
    val uid: String,
    val login: String,
    val name: String,
    val someInfo: String,
    val currentLogin: String,
)

/** Правка профиля в памяти: помнит вызовы и умеет зависнуть. */
private class FakeProfileEditor : ProfileEditor {

    val saves = mutableListOf<Saved>()
    var refusal: Throwable? = null

    private var pending: CompletableDeferred<Unit>? = null

    /** Подвешивает следующее сохранение; отпускается выполнением возвращённого. */
    fun hang(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { pending = it }

    override suspend fun save(
        uid: String,
        login: String,
        name: String,
        someInfo: String,
        currentLogin: String,
    ): Result<Unit> {
        saves += Saved(uid, login, name, someInfo, currentLogin)
        pending?.await()

        return refusal?.let { Result.failure(it) } ?: Result.success(Unit)
    }
}
