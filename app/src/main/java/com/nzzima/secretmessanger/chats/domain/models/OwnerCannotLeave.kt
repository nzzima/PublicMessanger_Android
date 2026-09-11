package com.nzzima.secretmessanger.chats.domain.models

import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Создатель из своей группы не выходит.
 *
 * Правило базы это же и запрещает: группа осталась бы без того, кто правит состав и
 * перевыпускает ключ. Закончить её создатель может иначе — стерев целиком.
 */
class OwnerCannotLeave : Exception(Constants.OWNER_CANNOT_LEAVE)
