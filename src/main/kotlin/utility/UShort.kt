package com.thysmesi.utility

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun UShort.toByteArray(byteOrder: ByteOrder): ByteArray {
    return ByteBuffer.allocate(UShort.SIZE_BYTES)
        .order(byteOrder)
        .putShort(this.toShort())
        .array()
}