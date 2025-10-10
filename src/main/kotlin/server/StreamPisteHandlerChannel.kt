package com.thysmesi.piste.server

import com.thysmesi.piste.PisteChannel
import kotlinx.coroutines.flow.Flow

interface StreamPisteHandlerChannel<Serverbound, Clientbound> {
    val inbound: Flow<Serverbound>

    suspend fun closed()

    suspend fun send(value: Clientbound)
    suspend fun close()
}