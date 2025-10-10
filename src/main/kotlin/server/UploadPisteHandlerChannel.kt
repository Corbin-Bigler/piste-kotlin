package com.thysmesi.piste.server

import com.thysmesi.piste.PisteChannel
import kotlinx.coroutines.flow.Flow

interface UploadPisteHandlerChannel<Serverbound, Clientbound> {
    val inbound: Flow<Serverbound>

    suspend fun closed()

    suspend fun close()
    suspend fun complete(response: Clientbound)
}