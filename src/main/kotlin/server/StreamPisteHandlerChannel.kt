package com.corbinbigler.piste.server

import com.corbinbigler.piste.PisteChannel
import kotlinx.coroutines.flow.Flow

interface StreamPisteHandlerChannel<Serverbound, Clientbound> {
    val inbound: Flow<Serverbound>

    suspend fun closed()

    suspend fun send(value: Clientbound)
    suspend fun close()
}