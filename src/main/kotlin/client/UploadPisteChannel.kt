package com.thysmesi.client

import com.thysmesi.PisteChannel

data class UploadPisteChannel<Clientbound, Serverbound>(private val channel: PisteChannel<Clientbound, Serverbound>) {
    suspend fun closed() = channel.closed()
    suspend fun completed() = channel.completed()

    suspend fun send(value: Serverbound) = channel.send(value)
    suspend fun close() = channel.close()
}