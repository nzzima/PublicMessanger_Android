package com.nzzima.secretmessanger.di

import com.nzzima.secretmessanger.auth.domain.api.AuthenticationInteractor
import com.nzzima.secretmessanger.auth.domain.api.ProfileRepairInteractor
import com.nzzima.secretmessanger.auth.domain.api.RegistrationInteractor
import com.nzzima.secretmessanger.auth.domain.impl.AuthenticationInteractorImpl
import com.nzzima.secretmessanger.auth.domain.impl.ProfileRepairInteractorImpl
import com.nzzima.secretmessanger.auth.domain.impl.RegistrationInteractorImpl
import com.nzzima.secretmessanger.chats.domain.api.ChatsInteractor
import com.nzzima.secretmessanger.chats.domain.api.ConversationStarter
import com.nzzima.secretmessanger.chats.domain.impl.ChatsInteractorImpl
import com.nzzima.secretmessanger.chats.domain.impl.ConversationStarterImpl
import com.nzzima.secretmessanger.contacts.domain.api.ContactsInteractor
import com.nzzima.secretmessanger.contacts.domain.impl.ContactsInteractorImpl
import com.nzzima.secretmessanger.crypto.domain.api.ConversationKeys
import com.nzzima.secretmessanger.crypto.domain.api.IdentityInteractor
import com.nzzima.secretmessanger.crypto.domain.impl.ConversationKeysImpl
import com.nzzima.secretmessanger.crypto.domain.impl.IdentityInteractorImpl
import com.nzzima.secretmessanger.messanger.domain.api.MessangerInteractor
import com.nzzima.secretmessanger.messanger.domain.impl.MessangerInteractorImpl
import com.nzzima.secretmessanger.profile.domain.api.ProfileInteractor
import com.nzzima.secretmessanger.profile.domain.impl.ProfileInteractorImpl
import com.nzzima.secretmessanger.session.domain.api.SessionInteractor
import com.nzzima.secretmessanger.session.domain.impl.SessionInteractorImpl
import org.koin.dsl.module

/**
 * Сценарии, которыми пользуется слой представления.
 *
 * [ConversationKeysImpl] объявлен одиночкой ради кэша открытых ключей диалогов: второй
 * экземпляр распечатывал бы их заново.
 */
val interactorModule = module {

    single<RegistrationInteractor> {
        RegistrationInteractorImpl(get(), get(), get())
    }

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
        MessangerInteractorImpl(get(), get(), get())
    }

    single<ProfileInteractor> {
        ProfileInteractorImpl(get())
    }
}
