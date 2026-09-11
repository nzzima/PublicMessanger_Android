package com.nzzima.secretmessanger.di

import com.nzzima.secretmessanger.auth.ui.AuthViewModel
import com.nzzima.secretmessanger.chats.ui.ChatsViewModel
import com.nzzima.secretmessanger.chats.ui.NewGroupViewModel
import com.nzzima.secretmessanger.contacts.ui.ContactsViewModel
import com.nzzima.secretmessanger.main.ui.RootViewModel
import com.nzzima.secretmessanger.messanger.ui.MessangerViewModel
import com.nzzima.secretmessanger.profile.ui.EditProfileViewModel
import com.nzzima.secretmessanger.profile.ui.ProfileViewModel
import com.nzzima.secretmessanger.profile.ui.UserProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Модели представления экранов.
 *
 * [MessangerViewModel] и [UserProfileViewModel] получают параметры: диалог и человек
 * приходят аргументами назначения, а не из графа зависимостей.
 */
val viewModelModule = module {

    viewModel {
        AuthViewModel(get(), get())
    }

    viewModel {
        ChatsViewModel(get(), get(), get(), get())
    }

    viewModel {
        ContactsViewModel(get(), get(), get(), get())
    }

    viewModel {
        NewGroupViewModel(get(), get(), get())
    }

    viewModel {
        ProfileViewModel(get(), get(), get())
    }

    viewModel {
        EditProfileViewModel(get(), get(), get(), get())
    }

    viewModel { (convoId: String) ->
        MessangerViewModel(convoId, get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }

    viewModel { (userId: String, login: String) ->
        UserProfileViewModel(userId, login, get(), get(), get(), get(), get())
    }

    viewModel {
        RootViewModel(get(), get(), get(), get(), get(), get())
    }
}
