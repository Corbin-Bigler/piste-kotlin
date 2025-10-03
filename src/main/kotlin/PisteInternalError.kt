package com.thysmesi.piste

sealed class PisteInternalError: Exception() {
    data object Cancelled: PisteInternalError()
    data object ChannelClosed: PisteInternalError()
    data object UnsupportedService: PisteInternalError()
    data object IncorrectServiceType: PisteInternalError()
}