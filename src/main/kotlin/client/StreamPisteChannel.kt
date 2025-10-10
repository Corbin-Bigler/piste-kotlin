package com.thysmesi.piste.client

import com.thysmesi.piste.PisteChannel
import kotlinx.coroutines.flow.Flow

interface StreamPisteChannel<Clientbound, Serverbound> {
    val inbound: Flow<Clientbound>

    suspend fun closed()

    suspend fun send(value: Serverbound)
    suspend fun close()
}