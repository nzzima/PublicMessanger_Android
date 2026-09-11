package com.nzzima.secretmessanger.chats.domain.models

import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Из диалога на двоих не выходят.
 *
 * Правило базы такую запись пропустило бы, но собеседник остался бы с диалогом на одного:
 * писать в него он сможет, а читать это будет некому. Выход придуман для групп.
 */
class NotAGroup : Exception(Constants.NOT_A_GROUP)
