package com.nzzima.secretmessanger.chats.domain.models

import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * Шапки диалога в базе больше нет — его стёрли.
 *
 * Отличается от отказа по правам, которым приходит вычёркивание из состава: шапка
 * заведомо существовала, раз мы её читали, поэтому пустой снимок означает удаление, а не
 * потерю доступа. Повтор подписки здесь не поможет — возвращать нечего.
 */
class ConversationGone : Exception(Constants.CONVERSATION_GONE)
