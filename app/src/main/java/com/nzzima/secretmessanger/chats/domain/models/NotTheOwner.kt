package com.nzzima.secretmessanger.chats.domain.models

import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Состав группы правит только её создатель.
 *
 * Так же считают и правила базы: они сверяют `owner` с автором записи. Проверка повторена в
 * приложении, потому что отказ по правам человеку ничего не объясняет.
 */
class NotTheOwner : Exception(Constants.NOT_THE_OWNER)
