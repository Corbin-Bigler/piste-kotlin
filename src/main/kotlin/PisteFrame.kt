package com.thysmesi.piste

import com.thysmesi.piste.utility.toByteArray
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder

sealed interface PisteFrame {
    val type: PisteFrameType

    val data: ByteArray get() {
        val output = ByteArrayOutputStream()
        output.write(type.value.toInt())

        fun writeOpen(id: PisteId) = output.write(id.toByteArray(ByteOrder.LITTLE_ENDIAN))
        fun writeRequest(id: PisteId, payload: ByteArray) {
            output.write(id.toByteArray(ByteOrder.LITTLE_ENDIAN))
            output.write(payload)
        }
        when (this) {
            is RequestCall -> writeRequest(id, payload)
            is RequestDownload -> writeRequest(id, payload)
            is OpenStream -> writeOpen(id)
            is OpenUpload -> writeOpen(id)
            is Error -> output.write(error.value.toByteArray(ByteOrder.LITTLE_ENDIAN))
            is Payload -> output.write(this.payload)
            is SupportedServicesResponse -> {
                output.write(services.size.toUInt().toByteArray(ByteOrder.LITTLE_ENDIAN))
                for (service in services) {
                    output.write(service.id.toByteArray(ByteOrder.LITTLE_ENDIAN))
                    output.write(service.type.value.toInt())
                }
            }
            is SupportedServicesRequest, is Close, is Open -> {}
        }

        return output.toByteArray()
    }

    data class RequestCall(val id: PisteId, val payload: ByteArray): PisteFrame {
        override val type = PisteFrameType.REQUEST_CALL
    }
    data class RequestDownload(val id: PisteId, val payload: ByteArray): PisteFrame {
        override val type = PisteFrameType.REQUEST_DOWNLOAD
    }
    data class OpenUpload(val id: PisteId): PisteFrame {
        override val type = PisteFrameType.OPEN_UPLOAD
    }
    data class OpenStream(val id: PisteId): PisteFrame {
        override val type = PisteFrameType.OPEN_STREAM
    }
    data object Open: PisteFrame {
        override val type = PisteFrameType.OPEN
    }
    data object Close: PisteFrame {
        override val type = PisteFrameType.CLOSE
    }
    data class Payload(val payload: ByteArray): PisteFrame {
        override val type = PisteFrameType.PAYLOAD
    }
    data class Error(val error: PisteError): PisteFrame {
        override val type = PisteFrameType.ERROR
    }
    data object SupportedServicesRequest: PisteFrame {
        override val type = PisteFrameType.SUPPORTED_SERVICES_REQUEST
    }
    data class  SupportedServicesResponse(val services: List<PisteSupportedService>): PisteFrame {
        override val type = PisteFrameType.SUPPORTED_SERVICES_RESPONSE
    }

    companion object {
        fun from(data: ByteArray): PisteFrame? {
            var cursor = 0

            fun read(size: Int): Long? {
                if (cursor + size > data.size) return null
                var result = 0L
                for (i in 0 until size) {
                    result = result or ((data[cursor + i].toLong() and 0xFF) shl (8 * i))
                }
                cursor += size
                return result
            }

            val typeValue = read(UByte.SIZE_BYTES)?.toUByte() ?: return null
            val frameType = PisteFrameType.from(typeValue) ?: return null

            return when (frameType) {
                PisteFrameType.REQUEST_CALL -> {
                    val id = read(UInt.SIZE_BYTES)?.toUInt() ?: return null
                    val payload = data.copyOfRange(cursor, data.size)
                    RequestCall(id, payload)
                }
                PisteFrameType.REQUEST_DOWNLOAD -> {
                    val id = read(UInt.SIZE_BYTES)?.toUInt() ?: return null
                    val payload = data.copyOfRange(cursor, data.size)
                    RequestDownload(id, payload)
                }
                PisteFrameType.OPEN_UPLOAD -> {
                    val id = read(UInt.SIZE_BYTES)?.toUInt() ?: return null
                    OpenUpload(id)
                }
                PisteFrameType.OPEN_STREAM -> {
                    val id = read(UInt.SIZE_BYTES)?.toUInt() ?: return null
                    OpenStream(id)
                }
                PisteFrameType.OPEN -> Open
                PisteFrameType.CLOSE -> Close
                PisteFrameType.PAYLOAD -> Payload(data.copyOfRange(cursor, data.size))
                PisteFrameType.ERROR -> {
                    val error = read(UShort.SIZE_BYTES)?.toUShort()?.let { PisteError.from(it) } ?: return null
                    Error(error)
                }
                PisteFrameType.SUPPORTED_SERVICES_REQUEST -> SupportedServicesRequest
                PisteFrameType.SUPPORTED_SERVICES_RESPONSE -> {
                    val size = read(UInt.SIZE_BYTES)?.toInt() ?: return null
                    val supportedServices: MutableList<PisteSupportedService> = mutableListOf()
                    for (index in 0..<size) {
                        val id = read(UInt.SIZE_BYTES)?.toUInt() ?: return null
                        val serviceType = read(UByte.SIZE_BYTES)?.toUByte()?.let { PisteServiceType.from(it) } ?: return null
                        supportedServices.add(PisteSupportedService(id, serviceType))
                    }
                    SupportedServicesResponse(supportedServices)
                }
            }
        }
    }
}