package com.thysmesi

enum class PisteStreamAction(val value: UByte) {
    CLOSE(0x00u),
    OPENED(0x01u),
    CLOSED(0x02u)
    ;
    companion object {
        fun from(value: UByte): PisteStreamAction? {
            return PisteStreamAction.entries.find { it.value == value }
        }
    }
}