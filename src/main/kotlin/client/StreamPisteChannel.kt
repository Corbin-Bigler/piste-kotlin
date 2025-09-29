package com.thysmesi.client

import com.thysmesi.PisteChannel
import kotlinx.coroutines.flow.Flow

data class StreamPisteChannel<Clientbound, Serverbound>(private val channel: PisteChannel<Clientbound, Serverbound>) {
    val inbound: Flow<Clientbound> get() = channel.inbound

    suspend fun opened() = channel.opened()
    suspend fun closed() = channel.closed()

    suspend fun send(value: Serverbound) = channel.send(value)
    suspend fun close() = channel.close()
}