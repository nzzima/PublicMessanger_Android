package com.nzzima.secretmessanger.messanger.domain.models

import com.nzzima.secretmessanger.photo.domain.models.PhotoSize

/**
 * Снимок реплики: чем сверстать пузырь и чем открыть байты.
 *
 * Версия ключа приложена к самому вложению, а не взята у диалога: снимок, отправленный до
 * ротации, открывается только прежним ключом. На iOS это было дефектом — голосовые брали
 * текущую версию и после удаления участника переставали открываться.
 */
data class PhotoAttachment(val size: PhotoSize, val keyVersion: Int)
