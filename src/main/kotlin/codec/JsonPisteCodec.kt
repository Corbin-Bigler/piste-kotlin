package com.corbinbigler.piste.codec

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

object JsonPisteCodec: PisteCodec {
    override fun <T> encode(value: T, serializer: KSerializer<T>): ByteArray {
        return Json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8)
    }

    override fun <T> decode(data: ByteArray, serializer: KSerializer<T>): T {
        return Json.decodeFromString(serializer, data.toString(Charsets.UTF_8))
    }
}