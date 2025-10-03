package com.thysmesi.piste.server

import com.thysmesi.piste.PisteChannel
import kotlinx.coroutines.flow.Flow

data class StreamPisteHandlerChannel<Serverbound, Clientbound>(private val channel: PisteChannel<Serverbound, Clientbound>) {
    val inbound: Flow<Serverbound> get() = channel.inbound

    suspend fun closed() = channel.closed()

    suspend fun send(value: Clientbound) = channel.send(value)
    suspend fun close() = channel.close()
}