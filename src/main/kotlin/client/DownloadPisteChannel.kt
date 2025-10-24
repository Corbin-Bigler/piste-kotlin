package com.corbinbigler.piste.client

import com.corbinbigler.piste.PisteChannel
import kotlinx.coroutines.flow.Flow

interface DownloadPisteChannel<Clientbound> {
    val inbound: Flow<Clientbound>

    suspend fun closed()
    suspend fun close()
}