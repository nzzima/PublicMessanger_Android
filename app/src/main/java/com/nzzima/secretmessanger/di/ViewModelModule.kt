package com.nzzima.secretmessanger.di

import com.nzzima.secretmessanger.auth.ui.AuthViewModel
import com.nzzima.secretmessanger.chats.ui.ChatsViewModel
import com.nzzima.secretmessanger.contacts.ui.ContactsViewModel
import com.nzzima.secretmessanger.main.ui.RootViewModel
import com.nzzima.secretmessanger.messanger.ui.MessangerViewModel
import com.nzzima.secretmessanger.profile.ui.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Модели представления экранов.
 *
 * [MessangerViewModel] получает диалог параметром: он приходит аргументом назначения, а
 * не из графа зависимостей.
 */
val viewModelModule = module {

    viewModel {
        AuthViewModel(get(), get())
    }

    viewModel {
        ChatsViewModel(get(), get())
    }

    viewModel {
        ContactsViewModel(get(), get())
    }

    viewModel {
        ProfileViewModel(get(), get())
    }

    viewModel { (convoId: String) ->
        MessangerViewModel(convoId, get(), get())
    }

    viewModel {
        RootViewModel(get(), get(), get())
    }
}
