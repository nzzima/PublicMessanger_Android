package com.nzzima.secretmessanger.photo.domain.models

/** Снимок, подогнанный под бюджет сообщения: байты JPEG и их размеры. */
data class EncodedPhoto(val bytes: ByteArray, val size: PhotoSize) {

    override fun equals(other: Any?): Boolean =
        this === other || (other is EncodedPhoto && size == other.size && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + size.hashCode()
}
