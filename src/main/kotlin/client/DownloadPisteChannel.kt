package com.thysmesi.client

import com.thysmesi.PisteChannel
import kotlinx.coroutines.flow.Flow

data class DownloadPisteChannel<Clientbound, Serverbound>(private val channel: PisteChannel<Clientbound, Serverbound>) {
    val inbound: Flow<Clientbound> get() = channel.inbound

    suspend fun closed() = channel.closed()

    suspend fun close() = channel.close()
}