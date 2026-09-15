package com.nzzima.secretmessanger.chats.domain.models

import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Стереть переписку у всех вправе не всякий участник: группу — только создатель.
 *
 * Так же считают и правила базы (`erasable()`). Проверка повторена в приложении, потому что
 * отказ по правам человеку ничего не объясняет.
 */
class CannotErase : Exception(Constants.CANNOT_ERASE)
