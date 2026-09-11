package com.nzzima.secretmessanger.di

import com.nzzima.secretmessanger.auth.data.impl.AuthenticationRepositoryImpl
import com.nzzima.secretmessanger.auth.data.impl.LoginRepositoryImpl
import com.nzzima.secretmessanger.auth.data.impl.ProfileRepositoryImpl
import com.nzzima.secretmessanger.auth.data.impl.RegistrationRepositoryImpl
import com.nzzima.secretmessanger.auth.domain.api.AuthenticationRepository
import com.nzzima.secretmessanger.auth.domain.api.LoginRepository
import com.nzzima.secretmessanger.auth.domain.api.ProfileRepository
import com.nzzima.secretmessanger.auth.domain.api.RegistrationRepository
import com.nzzima.secretmessanger.avatar.data.impl.AvatarEncoderImpl
import com.nzzima.secretmessanger.avatar.data.impl.AvatarRepositoryImpl
import com.nzzima.secretmessanger.avatar.domain.api.AvatarEncoder
import com.nzzima.secretmessanger.avatar.domain.api.AvatarRepository
import com.nzzima.secretmessanger.chats.data.impl.ConversationRepositoryImpl
import com.nzzima.secretmessanger.chats.domain.api.ConversationRepository
import com.nzzima.secretmessanger.contacts.data.impl.ContactsRepositoryImpl
import com.nzzima.secretmessanger.contacts.domain.api.ContactsRepository
import com.nzzima.secretmessanger.crypto.data.impl.IdentityKeyStoreImpl
import com.nzzima.secretmessanger.crypto.data.impl.PublicKeyRepositoryImpl
import com.nzzima.secretmessanger.crypto.domain.api.IdentityKeyStore
import com.nzzima.secretmessanger.crypto.domain.api.PublicKeyRepository
import com.nzzima.secretmessanger.messanger.data.impl.MessageRepositoryImpl
import com.nzzima.secretmessanger.messanger.data.impl.LocationSourceImpl
import com.nzzima.secretmessanger.messanger.domain.api.LocationSource
import com.nzzima.secretmessanger.lock.data.impl.BiometricGateImpl
import com.nzzima.secretmessanger.lock.domain.api.BiometricGate
import com.nzzima.secretmessanger.messanger.domain.api.MessageRepository
import com.nzzima.secretmessanger.photo.data.impl.PhotoEncoderImpl
import com.nzzima.secretmessanger.photo.data.impl.PhotoRepositoryImpl
import com.nzzima.secretmessanger.photo.domain.api.PhotoEncoder
import com.nzzima.secretmessanger.photo.domain.api.PhotoRepository
import com.nzzima.secretmessanger.presence.data.impl.PresenceRepositoryImpl
import com.nzzima.secretmessanger.presence.domain.api.PresenceRepository
import com.nzzima.secretmessanger.profile.data.impl.ProfileReaderImpl
import com.nzzima.secretmessanger.profile.domain.api.ProfileReader
import com.nzzima.secretmessanger.session.data.impl.SessionRepositoryImpl
import com.nzzima.secretmessanger.voice.data.impl.VoicePlayerImpl
import com.nzzima.secretmessanger.voice.data.impl.VoiceRecorderImpl
import com.nzzima.secretmessanger.voice.data.impl.VoiceRepositoryImpl
import com.nzzima.secretmessanger.voice.domain.api.VoicePlayer
import com.nzzima.secretmessanger.voice.domain.api.VoiceRecorder
import com.nzzima.secretmessanger.voice.domain.api.VoiceRepository
import com.nzzima.secretmessanger.session.domain.api.SessionCloser
import com.nzzima.secretmessanger.session.domain.api.SessionReader
import com.nzzima.secretmessanger.session.domain.api.SessionValidator
import org.koin.android.ext.koin.androidContext
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

    single<AvatarRepository> {
        AvatarRepositoryImpl(get())
    }

    single<AvatarEncoder> {
        AvatarEncoderImpl(androidContext())
    }

    single<PhotoRepository> {
        PhotoRepositoryImpl(get())
    }

    single<PhotoEncoder> {
        PhotoEncoderImpl(androidContext())
    }

    single<LocationSource> {
        LocationSourceImpl(androidContext())
    }

    single<PresenceRepository> {
        PresenceRepositoryImpl(get())
    }

    single<BiometricGate> {
        BiometricGateImpl(androidContext())
    }

    single<VoiceRepository> {
        VoiceRepositoryImpl(get())
    }

    // Рекордер одиночка: микрофон один, и второй экземпляр отбирал бы его у первого.
    single<VoiceRecorder> {
        VoiceRecorderImpl(androidContext())
    }

    single<VoicePlayer> {
        VoicePlayerImpl()
    }

    single<MessageRepository> {
        MessageRepositoryImpl(get())
    }

    single<ProfileReader> {
        ProfileReaderImpl(get())
    }

    single {
        SessionRepositoryImpl(get())
    } binds arrayOf(SessionReader::class, SessionValidator::class, SessionCloser::class)
}
