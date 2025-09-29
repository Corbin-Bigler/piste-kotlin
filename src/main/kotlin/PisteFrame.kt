package com.thysmesi

import com.thysmesi.utility.toByteArray
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder

sealed interface PisteFrame {
    val type: PisteFrameType
    val data: ByteArray get() {
        val output = ByteArrayOutputStream()
        output.write(type.value.toInt())

        when (this) {
            is Request -> {
                output.write(id.toByteArray(ByteOrder.LITTLE_ENDIAN))
                output.write(payload)
            }
            is Error -> {
                output.write(error.value.toByteArray(ByteOrder.LITTLE_ENDIAN))
            }
            is Open -> {
                output.write(id.toByteArray(ByteOrder.LITTLE_ENDIAN))
            }
            is Payload -> output.write(this.payload)
            is Close, is Opened -> {}
        }

        return output.toByteArray()
    }

    data class Request(val id: PisteId, val payload: ByteArray): PisteFrame {
        override val type = PisteFrameType.REQUEST
    }
    data class Open(val id: PisteId): PisteFrame {
        override val type = PisteFrameType.OPEN
    }
    data object Opened: PisteFrame {
        override val type = PisteFrameType.OPENED
    }
    data object Close: PisteFrame {
        override val type = PisteFrameType.CLOSE
    }
    data class Error(val error: PisteError): PisteFrame {
        override val type = PisteFrameType.ERROR
    }
    data class Payload(val payload: ByteArray): PisteFrame {
        override val type = PisteFrameType.PAYLOAD
    }

    companion object {
        fun from(data: ByteArray): PisteFrame? {
            var cursor = 0

            fun readLong(size: Int): Long? {
                if (cursor + size > data.size) return null
                var result = 0L
                for (i in 0 until size) {
                    result = result or ((data[cursor + i].toLong() and 0xFF) shl (8 * i))
                }
                cursor += size
                return result
            }

            val typeValue = readLong(UByte.SIZE_BYTES)?.toUByte() ?: return null
            val type = PisteFrameType.from(typeValue) ?: return null

            return when (type) {
                PisteFrameType.REQUEST -> {
                    val id = readLong(UInt.SIZE_BYTES)?.toUInt() ?: return null
                    val payload = data.copyOfRange(cursor, data.size)
                    Request(id, payload)
                }
                PisteFrameType.ERROR -> {
                    val errorValue = readLong(UShort.SIZE_BYTES)?.toUShort() ?: return null
                    val error = PisteError.from(errorValue) ?: return null
                    Error(error)
                }
                PisteFrameType.OPEN -> {
                    val id = readLong(UInt.SIZE_BYTES)?.toUInt() ?: return null
                    Open(id)
                }
                PisteFrameType.PAYLOAD -> {
                    val payload = data.copyOfRange(cursor, data.size)
                    Payload(payload)
                }
                PisteFrameType.OPENED -> Opened
                PisteFrameType.CLOSE -> Close
            }
        }
    }
}