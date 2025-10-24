package com.corbinbigler.piste.codec

import kotlinx.serialization.KSerializer

interface PisteCodec {
    fun <T> encode(value: T, serializer: KSerializer<T>): ByteArray
    fun <T> decode(data: ByteArray, serializer: KSerializer<T>): T
}