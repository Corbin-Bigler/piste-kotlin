package com.thysmesi.piste.client

import com.thysmesi.piste.PisteChannel
import kotlinx.coroutines.flow.Flow

interface DownloadPisteChannel<Clientbound> {
    val inbound: Flow<Clientbound>

    suspend fun closed()
    suspend fun close()
}