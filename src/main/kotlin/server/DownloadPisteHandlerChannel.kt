package com.thysmesi.piste.server

import com.thysmesi.piste.PisteChannel

interface DownloadPisteHandlerChannel<Clientbound> {
    suspend fun closed()

    suspend fun send(value: Clientbound)
    suspend fun close()
}