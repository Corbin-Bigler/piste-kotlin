package com.thysmesi

enum class PisteFrameType(val value: UByte) {
    REQUEST_CALL(0x00u),
    REQUEST_DOWNLOAD(0x01u),
    OPEN_UPLOAD(0x02u),
    OPEN_STREAM(0x03u),
    OPEN(0x04u),
    CLOSE(0x05u),
    PAYLOAD(0x06u),
    ERROR(0x07u),
    SUPPORTED_SERVICES_REQUEST(0x08u),
    SUPPORTED_SERVICES_RESPONSE(0x09u)
    ;
    companion object {
        fun from(value: UByte): PisteFrameType? {
            return PisteFrameType.entries.find { it.value == value }
        }
    }
}