package com.nzzima.secretmessanger.chats.domain.models

import com.nzzima.secretmessanger.utils.constants.Constants

/**
 * У собеседника нет годной открытой половины ключа, и запечатать ему ключ диалога нечем.
 *
 * Один отказ на два случая — половины нет в профиле вовсе и половина не разбирается, —
 * потому что для заводящего разницы нет: диалог, который собеседник не прочитает, не
 * заводится ни в том, ни в другом.
 *
 * Отказ окончательный только на сейчас: собеседник опубликует половину, войдя в
 * приложение, и попытка пройдёт.
 */
class CompanionKeyMissing : Exception(Constants.COMPANION_KEY_MISSING)
