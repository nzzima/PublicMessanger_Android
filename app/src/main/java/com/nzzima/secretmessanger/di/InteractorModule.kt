package com.nzzima.secretmessanger.di

import com.nzzima.secretmessanger.auth.domain.api.AuthenticationInteractor
import com.nzzima.secretmessanger.auth.domain.api.ProfileRepairInteractor
import com.nzzima.secretmessanger.auth.domain.api.RegistrationInteractor
import com.nzzima.secretmessanger.auth.domain.api.RegistrationMarker
import com.nzzima.secretmessanger.auth.domain.api.RegistrationProgress
import com.nzzima.secretmessanger.auth.domain.impl.AuthenticationInteractorImpl
import com.nzzima.secretmessanger.auth.domain.impl.ProfileRepairInteractorImpl
import com.nzzima.secretmessanger.auth.domain.impl.RegistrationInteractorImpl
import com.nzzima.secretmessanger.auth.domain.impl.RegistrationProgressImpl
import com.nzzima.secretmessanger.avatar.domain.api.AvatarInteractor
import com.nzzima.secretmessanger.avatar.domain.impl.AvatarInteractorImpl
import com.nzzima.secretmessanger.chats.domain.api.ChatsInteractor
import com.nzzima.secretmessanger.chats.domain.api.ConversationStarter
import com.nzzima.secretmessanger.chats.domain.api.ChatEraser
import com.nzzima.secretmessanger.chats.domain.api.GroupEditor
import com.nzzima.secretmessanger.chats.domain.impl.ChatsInteractorImpl
import com.nzzima.secretmessanger.chats.domain.impl.ConversationStarterImpl
import com.nzzima.secretmessanger.chats.domain.impl.ChatEraserImpl
import com.nzzima.secretmessanger.chats.domain.impl.GroupEditorImpl
import com.nzzima.secretmessanger.contacts.domain.api.ContactsInteractor
import com.nzzima.secretmessanger.contacts.domain.impl.ContactsInteractorImpl
import com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys
import com.nzzima.secretmessanger.crypto.domain.api.IdentityInteractor
import com.nzzima.secretmessanger.crypto.domain.impl.ConversationKeysImpl
import com.nzzima.secretmessanger.crypto.domain.impl.IdentityInteractorImpl
import com.nzzima.secretmessanger.messanger.domain.api.MessangerInteractor
import com.nzzima.secretmessanger.messanger.domain.impl.MessangerInteractorImpl
import com.nzzima.secretmessanger.photo.domain.api.PhotoInteractor
import com.nzzima.secretmessanger.photo.domain.impl.PhotoInteractorImpl
import com.nzzima.secretmessanger.presence.domain.api.PresenceInteractor
import com.nzzima.secretmessanger.presence.domain.impl.PresenceInteractorImpl
import com.nzzima.secretmessanger.profile.domain.api.ProfileEditor
import com.nzzima.secretmessanger.profile.domain.api.ProfileInteractor
import com.nzzima.secretmessanger.profile.domain.impl.ProfileEditorImpl
import com.nzzima.secretmessanger.profile.domain.impl.ProfileInteractorImpl
import com.nzzima.secretmessanger.session.domain.api.SessionInteractor
import com.nzzima.secretmessanger.session.domain.impl.SessionInteractorImpl
import com.nzzima.secretmessanger.voice.domain.api.VoiceInteractor
import com.nzzima.secretmessanger.voice.domain.impl.VoiceInteractorImpl
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * Сценарии, которыми пользуется слой представления.
 *
 * [ConversationKeysImpl] объявлен одиночкой ради кэша открытых ключей диалогов: второй
 * экземпляр распечатывал бы их заново.
 */
val interactorModule = module {

    single<RegistrationInteractor> {
        RegistrationInteractorImpl(get(), get(), get(), get())
    }

    // Одиночка обязателен: помечает регистрация, а ждёт оболочка — признак у них общий.
    single {
        RegistrationProgressImpl()
    } binds arrayOf(RegistrationProgress::class, RegistrationMarker::class)

    single<AuthenticationInteractor> {
        AuthenticationInteractorImpl(get())
    }

    single<ProfileRepairInteractor> {
        ProfileRepairInteractorImpl(get(), get())
    }

    single<IdentityInteractor> {
        IdentityInteractorImpl(get(), get())
    }

    single<SessionInteractor> {
        SessionInteractorImpl(get(), get(), get())
    }

    single<ConversationKeys> {
        ConversationKeysImpl(get())
    }

    single<ChatsInteractor> {
        ChatsInteractorImpl(get(), get())
    }

    single<ConversationStarter> {
        ConversationStarterImpl(get(), get(), get(), get(), get())
    }

    single<ContactsInteractor> {
        ContactsInteractorImpl(get())
    }

    single<MessangerInteractor> {
        MessangerInteractorImpl(get(), get(), get(), get(), get())
    }

    single<ProfileInteractor> {
        ProfileInteractorImpl(get())
    }

    single<ProfileEditor> {
        ProfileEditorImpl(get(), get(), get())
    }

    // Одиночка ради кэша картинок: второй экземпляр качал бы их заново.
    single<AvatarInteractor> {
        AvatarInteractorImpl(get(), get(), get())
    }

    // Тоже одиночка и по той же причине: кэш снимков переживает уход с экрана переписки.
    single<PhotoInteractor> {
        PhotoInteractorImpl(get(), get(), get())
    }

    single<GroupEditor> {
        GroupEditorImpl(get(), get(), get(), get(), get())
    }

    single<ChatEraser> {
        ChatEraserImpl(get(), get(), get())
    }

    single<PresenceInteractor> {
        PresenceInteractorImpl(get())
    }

    // Расшифрованные голосовые лежат в своей папке кэша: система вычистит её вместе с
    // остальным кэшем приложения, а мы не станем разводить открытый звук по всему диску.
    single<VoiceInteractor> {
        VoiceInteractorImpl(get(), get(), File(androidContext().cacheDir, "voice"))
    }
}
