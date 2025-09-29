package com.thysmesi

enum class PisteFrameType(val value: UByte) {
    REQUEST(0x00u),
    OPEN(0x01u),
    OPENED(0x02u),
    CLOSE(0x03u),
    ERROR(0x04u),
    PAYLOAD(0x05u),
    ;
    companion object {
        fun from(value: UByte): PisteFrameType? {
            return PisteFrameType.entries.find { it.value == value }
        }
    }
}