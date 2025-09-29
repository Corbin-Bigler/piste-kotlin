package com.thysmesi

enum class PisteError(val value: UShort) {
    UNHANDLED_ERROR(0x00u),
    DECODING_FAILED(0x01u),
    INVALID_ACTION(0x02u),
    INVALID_FRAME(0x03u),
    INVALID_FRAME_TYPE(0x04u),
    UNSUPPORTED_SERVICE(0x05u),
    CHANNEL_CLOSED(0x06u)
    ;
    companion object {
        fun from(value: UShort): PisteError? {
            return PisteError.entries.find { it.value == value }
        }
    }
}