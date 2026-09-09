package com.nzzima.secretmessanger.main.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.nzzima.secretmessanger.ui.components.ChatsIcon
import com.nzzima.secretmessanger.ui.components.ContactsIcon
import com.nzzima.secretmessanger.ui.components.ProfileIcon
import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Вкладки нижней панели. Порядок совпадает с iOS: контакты, чаты, профиль.
 *
 * [route] — назначение во внутреннем графе [MainScreen].
 */
enum class Tab(val route: String, val title: String, val icon: ImageVector) {
    Contacts(Constants.CONTACTS_ROUTE, Constants.CONTACTS_TITLE, ContactsIcon),
    Chats(Constants.CHATS_ROUTE, Constants.CHATS_TITLE, ChatsIcon),
    Profile(Constants.PROFILE_ROUTE, Constants.PROFILE_TITLE, ProfileIcon),
}
