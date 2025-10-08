package com.thysmesi.piste.client

import com.thysmesi.piste.PisteChannel
import kotlinx.coroutines.flow.Flow

data class DownloadPisteChannel<Clientbound>(private val channel: PisteChannel<Clientbound, *>) {
    val inbound: Flow<Clientbound> get() = channel.inbound

    suspend fun closed() = channel.closed()
    suspend fun close() = channel.close()
}