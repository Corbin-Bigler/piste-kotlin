package com.thysmesi

sealed class PisteInternalError: Exception() {
    data object Cancelled: PisteInternalError()
}