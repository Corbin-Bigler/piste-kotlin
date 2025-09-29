package com.thysmesi.utility

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun UInt.toByteArray(byteOrder: ByteOrder): ByteArray {
    return ByteBuffer.allocate(UInt.SIZE_BYTES)
        .order(byteOrder)
        .putInt(this.toInt())
        .array()
}