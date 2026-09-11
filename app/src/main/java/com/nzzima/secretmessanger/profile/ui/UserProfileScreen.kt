package com.nzzima.secretmessanger.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nzzima.secretmessanger.profile.domain.models.Profile
import com.nzzima.secretmessanger.ui.components.Avatar
import com.nzzima.secretmessanger.ui.components.BackButton
import com.nzzima.secretmessanger.ui.components.FailureNotice
import com.nzzima.secretmessanger.ui.components.WriteIcon
import com.nzzima.secretmessanger.ui.theme.Accent
import com.nzzima.secretmessanger.ui.theme.ErrorColor
import com.nzzima.secretmessanger.ui.theme.Ink
import com.nzzima.secretmessanger.ui.theme.InkDim
import com.nzzima.secretmessanger.utils.constants.Constants
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Экран чужого профиля.
 *
 * Заголовка в шапке нет намеренно: имя человека написано в теле экрана, и второй раз ему
 * там делать нечего. В шапке остаются возврат и «Написать».
 *
 * Показывает **только публичные поля** — логин, имя, заметку. Ни почты, ни идентификатора
 * здесь нет: почта это личные контактные данные, а идентификатор в чужом профиле —
 * технический шум. Граница та же, что у `ProfileInfo` на iOS.
 *
 * Аватара и присутствия нет: ни того, ни другого в Android-приложении пока не существует.
 *
 * @param userId чей профиль.
 * @param login имя из списка контактов; держит заголовок, пока профиль не пришёл.
 * @param backTitle имя экрана, с которого пришли.
 * @param onWrite переход в переписку с этим человеком.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    login: String,
    backTitle: String,
    onBack: () -> Unit,
    onWrite: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserProfileViewModel = koinViewModel { parametersOf(userId, login) },
) {
    val state by viewModel.observeUserProfileScreenState().collectAsStateWithLifecycle()
    val opened = (state as? UserProfileUiState.Content)?.opened

    LaunchedEffect(opened) {
        if (opened != null) {
            onWrite(opened)
            viewModel.onOpened()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { BackButton(backTitle, onBack) },
                actions = {
                    // Кнопка живёт в шапке, как на iOS, и доступна только с прочитанным
                    // профилем: без него неизвестно имя, которое уйдёт в шапку диалога.
                    val content = state as? UserProfileUiState.Content

                    IconButton(onClick = viewModel::onWrite, enabled = content?.isOpening == false) {
                        Icon(
                            imageVector = WriteIcon,
                            contentDescription = Constants.WRITE_MESSAGE,
                            tint = if (content == null) InkDim else Accent,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { insets ->
        Box(modifier = Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
            when (val current = state) {
                // Имя показывается и в ожидании: оно известно из списка контактов, и
                // экран не должен открываться безымянным.
                is UserProfileUiState.Loading -> LoadingBody(current.name)

                is UserProfileUiState.Content ->
                    UserProfileBody(current.profile, current.avatar, current.presence, current.error)

                is UserProfileUiState.Failed -> FailureNotice(current.message, viewModel::retry)
            }
        }
    }
}

@Composable
private fun LoadingBody(name: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PersonName(name)

        CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
    }
}

@Composable
private fun UserProfileBody(profile: Profile, avatar: ByteArray?, presence: String?, error: String?) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(
            name = profile.name.ifEmpty { profile.login },
            image = avatar,
            size = PROFILE_AVATAR,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        PersonName(profile.name.ifEmpty { profile.login })

        // Подпись стоит под именем, а не рядом с кружком: «в сети вчера в 21:14» в строку с
        // именем не встаёт, а переносить имя ради неё — хуже.
        presence?.let {
            Text(text = it, color = InkDim, fontSize = 13.sp)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileField(Constants.PROFILE_LOGIN, profile.login)
            ProfileField(Constants.PROFILE_NAME, profile.name)

            // Заметка — строкой таблицы, а не подписью под именем, как в своём профиле:
            // здесь она чужая, и читать её как представление человека не за что.
            ProfileField(Constants.PROFILE_NOTE, profile.someInfo)
        }

        error?.let { message ->
            Text(
                text = message,
                color = ErrorColor,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
        }
    }
}

/** Имя человека — единственное место, где оно написано на этом экране. */
@Composable
private fun PersonName(name: String) {
    Text(
        text = name,
        color = Ink,
        fontSize = 21.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
