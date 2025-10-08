package com.thysmesi.piste

sealed class PisteError(val value: UShort): Exception() {
    data object InternalServerError: PisteError(0x00u)
    data object DecodingFailed: PisteError(0x01u)
    data object UnsupportedService: PisteError(0x02u)
    data object ChannelClosed: PisteError(0x03u)
    data object UnsupportedFrameType: PisteError(0x04u)

    companion object {
        private val map by lazy {
            mapOf(
                InternalServerError.value to InternalServerError,
                DecodingFailed.value to DecodingFailed,
                UnsupportedService.value to UnsupportedService,
                ChannelClosed.value to ChannelClosed,
                UnsupportedFrameType.value to UnsupportedFrameType
            )
        }

        fun from(value: UShort): PisteError? = map[value]
    }
}