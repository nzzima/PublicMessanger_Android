package com.nzzima.secretmessanger.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzzima.secretmessanger.ui.components.BackButton
import com.nzzima.secretmessanger.ui.components.FailureNotice
import com.nzzima.secretmessanger.ui.components.Field
import com.nzzima.secretmessanger.ui.theme.ErrorColor
import com.nzzima.secretmessanger.ui.theme.InkDim
import com.nzzima.secretmessanger.utils.constants.Constants
import org.koin.androidx.compose.koinViewModel

/**
 * Экран правки своего профиля.
 *
 * Сюда же переехал выход из аккаунта: на iOS он живёт в `EditProfile`, а на экране профиля
 * стоял временно — пока этого экрана не было.
 *
 * Аватара нет: его в Android-приложении не существует.
 *
 * @param backTitle имя экрана, с которого пришли.
 * @param onSaved возврат после сохранения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    backTitle: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = koinViewModel(),
) {
    val state by viewModel.observeEditProfileScreenState().collectAsStateWithLifecycle()
    val saved = (state as? EditProfileUiState.Form)?.saved == true

    LaunchedEffect(saved) {
        if (saved) onSaved()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(Constants.EDIT_PROFILE_TITLE) },
                navigationIcon = { BackButton(backTitle, onBack) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { insets ->
        Box(modifier = Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
            when (val current = state) {
                EditProfileUiState.Loading -> CircularProgressIndicator()

                is EditProfileUiState.Form -> EditProfileForm(current, viewModel)

                is EditProfileUiState.Failed -> FailureNotice(current.message, viewModel::retry)
            }
        }
    }
}

@Composable
private fun EditProfileForm(form: EditProfileUiState.Form, viewModel: EditProfileViewModel) {
    var askingSignOut by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = SIDE_PADDING, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FIELD_GAP),
    ) {
        LabeledField(Constants.PROFILE_LOGIN) {
            Field(
                value = form.login,
                onValueChange = viewModel::onLoginChange,
                placeholder = Constants.PROFILE_LOGIN,
            )
        }

        LabeledField(Constants.PROFILE_NAME) {
            Field(
                value = form.name,
                onValueChange = viewModel::onNameChange,
                placeholder = Constants.PROFILE_NAME,
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrect = true,
            )
        }

        LabeledField(Constants.PROFILE_NOTE) {
            Field(
                value = form.someInfo,
                onValueChange = viewModel::onSomeInfoChange,
                placeholder = Constants.NOTE_PLACEHOLDER,
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrect = true,
                maxLines = NOTE_MAX_LINES,
            )
        }

        form.error?.let { message ->
            Text(
                text = message,
                color = ErrorColor,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = viewModel::onSave,
            enabled = !form.isSaving,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background,
                disabledContainerColor = InkDim,
            ),
            modifier = Modifier.padding(top = BUTTON_GAP),
        ) {
            Text(Constants.SAVE, fontSize = 15.sp)
        }

        TextButton(onClick = { askingSignOut = true }, modifier = Modifier.padding(top = BUTTON_GAP)) {
            Text(Constants.SIGN_OUT, color = MaterialTheme.colorScheme.error, fontSize = 15.sp)
        }
    }

    // Спрашиваем, как на iOS: выход не отменить обратной кнопкой, а на Android случайное
    // нажатие в конце длинной формы стоит ровно того же — нового входа.
    if (askingSignOut) {
        AlertDialog(
            onDismissRequest = { askingSignOut = false },
            title = { Text(Constants.SIGN_OUT_QUESTION) },
            confirmButton = {
                TextButton(onClick = viewModel::onSignOut) {
                    Text(Constants.SIGN_OUT_CONFIRM, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { askingSignOut = false }) { Text(Constants.CANCEL) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

/**
 * Поле с подписью над ним — «Логин», «Имя», «Заметка», как на iOS.
 *
 * Подпись обязательна, а не украшение: заполненное поле прячет свой плейсхолдер, и логин с
 * именем, у которых значения часто совпадают, различить было бы нечем.
 */
@Composable
private fun LabeledField(title: String, field: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LABEL_GAP)) {
        Text(text = title, color = InkDim, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
        field()
    }
}

private val SIDE_PADDING = 30.dp
private val FIELD_GAP = 20.dp
private val BUTTON_GAP = 12.dp
private val LABEL_GAP = 4.dp
private const val NOTE_MAX_LINES = 4
