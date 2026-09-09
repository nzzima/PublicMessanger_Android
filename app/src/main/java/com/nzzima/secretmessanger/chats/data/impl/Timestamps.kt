package com.nzzima.secretmessanger.chats.data.impl

import com.google.firebase.Timestamp
import com.nzzima.secretmessanger.chats.domain.models.Moment

/**
 * Перевод между временем Firestore и временем домена.
 *
 * Пара целиком, без округления: на этом держится метка прочтения — она возвращается в базу
 * той же величиной, что стоит в документе реплики.
 */
internal fun Timestamp.toMoment(): Moment = Moment(seconds, nanoseconds)

internal fun Moment.toTimestamp(): Timestamp = Timestamp(seconds, nanoseconds)
