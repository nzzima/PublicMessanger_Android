package com.nzzima.secretmessanger.di

import com.nzzima.secretmessanger.auth.data.impl.AuthenticationRepositoryImpl
import com.nzzima.secretmessanger.auth.data.impl.LoginRepositoryImpl
import com.nzzima.secretmessanger.auth.data.impl.ProfileRepositoryImpl
import com.nzzima.secretmessanger.auth.data.impl.RegistrationRepositoryImpl
import com.nzzima.secretmessanger.auth.domain.api.AuthenticationRepository
import com.nzzima.secretmessanger.auth.domain.api.LoginRepository
import com.nzzima.secretmessanger.auth.domain.api.ProfileRepository
import com.nzzima.secretmessanger.auth.domain.api.RegistrationRepository
import com.nzzima.secretmessanger.chats.data.impl.ConversationRepositoryImpl
import com.nzzima.secretmessanger.chats.domain.api.ConversationRepository
import com.nzzima.secretmessanger.contacts.data.impl.ContactsRepositoryImpl
import com.nzzima.secretmessanger.contacts.domain.api.ContactsRepository
import com.nzzima.secretmessanger.crypto.data.impl.IdentityKeyStoreImpl
import com.nzzima.secretmessanger.crypto.data.impl.PublicKeyRepositoryImpl
import com.nzzima.secretmessanger.crypto.domain.api.IdentityKeyStore
import com.nzzima.secretmessanger.crypto.domain.api.PublicKeyRepository
import com.nzzima.secretmessanger.profile.data.impl.ProfileReaderImpl
import com.nzzima.secretmessanger.profile.domain.api.ProfileReader
import com.nzzima.secretmessanger.session.data.impl.SessionRepositoryImpl
import com.nzzima.secretmessanger.session.domain.api.SessionCloser
import com.nzzima.secretmessanger.session.domain.api.SessionReader
import com.nzzima.secretmessanger.session.domain.api.SessionValidator
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * Реализации репозиториев.
 *
 * [SessionRepositoryImpl] объявлен одним определением на три интерфейса: он держит
 * состояние сессии, и второй экземпляр слушал бы авторизацию отдельно.
 */
val repositoryModule = module {

    single<RegistrationRepository> {
        RegistrationRepositoryImpl(get())
    }

    single<AuthenticationRepository> {
        AuthenticationRepositoryImpl(get())
    }

    single<LoginRepository> {
        LoginRepositoryImpl(get())
    }

    single<ProfileRepository> {
        ProfileRepositoryImpl(get())
    }

    single<IdentityKeyStore> {
        IdentityKeyStoreImpl(get(), get())
    }

    single<PublicKeyRepository> {
        PublicKeyRepositoryImpl(get())
    }

    single<ConversationRepository> {
        ConversationRepositoryImpl(get())
    }

    single<ContactsRepository> {
        ContactsRepositoryImpl(get())
    }

    single<ProfileReader> {
        ProfileReaderImpl(get())
    }

    single {
        SessionRepositoryImpl(get())
    } binds arrayOf(SessionReader::class, SessionValidator::class, SessionCloser::class)
}
