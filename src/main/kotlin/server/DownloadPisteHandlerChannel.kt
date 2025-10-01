package com.thysmesi.server

import com.thysmesi.PisteChannel

data class DownloadPisteHandlerChannel<Serverbound, Clientbound>(private val channel: PisteChannel<Serverbound, Clientbound>) {
    suspend fun closed() = channel.closed()

    suspend fun send(value: Clientbound) = channel.send(value)
    suspend fun close() = channel.close()
}