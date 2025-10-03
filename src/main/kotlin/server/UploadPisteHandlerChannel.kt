package com.thysmesi.piste.server

import com.thysmesi.piste.PisteChannel
import kotlinx.coroutines.flow.Flow

data class UploadPisteHandlerChannel<Serverbound, Clientbound>(private val channel: PisteChannel<Serverbound, Clientbound>) {
    val inbound: Flow<Serverbound> get() = channel.inbound

    suspend fun closed() = channel.closed()

    suspend fun close() = channel.close()
    suspend fun complete(response: Clientbound) = channel.send(response)
}