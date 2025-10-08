package com.thysmesi.piste.server

import com.thysmesi.piste.PisteChannel

data class DownloadPisteHandlerChannel<Clientbound>(private val channel: PisteChannel<*, Clientbound>) {
    suspend fun closed() = channel.closed()

    suspend fun send(value: Clientbound) = channel.send(value)
    suspend fun close() = channel.close()
}