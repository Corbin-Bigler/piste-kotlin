package com.thysmesi.piste

enum class PisteServiceType(val value: UByte) {
    CALL(0x00u),
    DOWNLOAD(0x01u),
    UPLOAD(0x02u),
    STREAM(0x03u)
    ;
    companion object {
        fun from(value: UByte): PisteServiceType? {
            return PisteServiceType.entries.find { it.value == value }
        }
    }
}