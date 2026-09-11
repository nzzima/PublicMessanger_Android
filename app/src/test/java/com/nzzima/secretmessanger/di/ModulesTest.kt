package com.nzzima.secretmessanger.di

import com.google.firebase.auth.FirebaseAuth
import android.content.SharedPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.nzzima.secretmessanger.auth.domain.api.AuthenticationInteractor
import com.nzzima.secretmessanger.auth.domain.api.AuthenticationRepository
import com.nzzima.secretmessanger.auth.domain.api.LoginRepository
import com.nzzima.secretmessanger.auth.domain.api.ProfileRepository
import com.nzzima.secretmessanger.auth.domain.api.ProfileRepairInteractor
import com.nzzima.secretmessanger.auth.domain.api.RegistrationInteractor
import com.nzzima.secretmessanger.auth.domain.api.RegistrationProgress
import com.nzzima.secretmessanger.auth.domain.api.RegistrationRepository
import com.nzzima.secretmessanger.chats.domain.api.ChatsInteractor
import com.nzzima.secretmessanger.avatar.domain.api.AvatarEncoder
import com.nzzima.secretmessanger.avatar.domain.api.AvatarInteractor
import com.nzzima.secretmessanger.avatar.domain.api.AvatarRepository
import com.nzzima.secretmessanger.messanger.domain.api.LocationSource
import com.nzzima.secretmessanger.photo.domain.api.PhotoEncoder
import com.nzzima.secretmessanger.photo.domain.api.PhotoInteractor
import com.nzzima.secretmessanger.photo.domain.api.PhotoRepository
import com.nzzima.secretmessanger.chats.domain.api.ConversationRepository
import com.nzzima.secretmessanger.chats.domain.api.ConversationStarter
import com.nzzima.secretmessanger.contacts.domain.api.ContactsInteractor
import com.nzzima.secretmessanger.contacts.domain.api.ContactsRepository
import com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys
import com.nzzima.secretmessanger.crypto.domain.api.IdentityInteractor
import com.nzzima.secretmessanger.crypto.domain.api.IdentityKeyStore
import com.nzzima.secretmessanger.crypto.domain.api.MasterKeyProvider
import com.nzzima.secretmessanger.crypto.domain.api.PublicKeyRepository
import com.nzzima.secretmessanger.messanger.domain.api.MessageRepository
import com.nzzima.secretmessanger.messanger.domain.api.MessangerInteractor
import com.nzzima.secretmessanger.messanger.ui.MessangerViewModel
import com.nzzima.secretmessanger.profile.domain.api.ProfileEditor
import com.nzzima.secretmessanger.profile.domain.api.ProfileInteractor
import com.nzzima.secretmessanger.profile.domain.api.ProfileReader
import com.nzzima.secretmessanger.profile.ui.UserProfileViewModel
import com.nzzima.secretmessanger.session.domain.api.SessionCloser
import com.nzzima.secretmessanger.session.domain.api.SessionInteractor
import com.nzzima.secretmessanger.session.domain.api.SessionReader
import com.nzzima.secretmessanger.session.domain.api.SessionValidator
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

/**
 * Проверка полноты графа зависимостей.
 *
 * Koin разрешает зависимости во время выполнения: пропущенное определение падает не при
 * сборке, а при открытии экрана. Проверка сверяет параметры конструкторов всех определений
 * с объявленными типами и держит эту ошибку на уровне тестов.
 *
 * `extraTypes` перечисляет типы, объявленные в соседних модулях: каждый модуль проверяется
 * отдельно и о чужих определениях не знает.
 *
 * `dataModule` не проверяется: он отдаёт клиентов Firebase, созданных фабриками SDK, а
 * проверка читает конструкторы объявленных типов и требует их аргументы как зависимости.
 *
 * `injections` перечисляет то, что приходит параметром вызова, а не из графа: диалог у
 * модели переписки задаётся аргументом назначения.
 */
@OptIn(KoinExperimentalAPI::class)
class ModulesTest {

    @Test
    fun `граф зависимостей полон`() {
        repositoryModule.verify(
            extraTypes = listOf(
                FirebaseAuth::class,
                FirebaseFirestore::class,
                SharedPreferences::class,
                MasterKeyProvider::class,
            ),
        )

        interactorModule.verify(
            extraTypes = listOf(
                RegistrationRepository::class,
                AuthenticationRepository::class,
                LoginRepository::class,
                ProfileRepository::class,
                SessionReader::class,
                SessionValidator::class,
                SessionCloser::class,
                IdentityKeyStore::class,
                PublicKeyRepository::class,
                ConversationRepository::class,
                ConversationKeys::class,
                MessageRepository::class,
                ContactsRepository::class,
                ProfileReader::class,
                AvatarRepository::class,
                AvatarEncoder::class,
                PhotoRepository::class,
                PhotoEncoder::class,
            ),
        )

        viewModelModule.verify(
            extraTypes = listOf(
                RegistrationInteractor::class,
                RegistrationProgress::class,
                AuthenticationInteractor::class,
                SessionInteractor::class,
                IdentityInteractor::class,
                ProfileRepairInteractor::class,
                ChatsInteractor::class,
                ContactsInteractor::class,
                ProfileInteractor::class,
                ProfileEditor::class,
                AvatarInteractor::class,
                PhotoInteractor::class,
                LocationSource::class,
                MessangerInteractor::class,
                ConversationStarter::class,
            ),
            injections = injectedParameters(
                definition<MessangerViewModel>(String::class),
                definition<UserProfileViewModel>(String::class, String::class),
            ),
        )
    }
}
