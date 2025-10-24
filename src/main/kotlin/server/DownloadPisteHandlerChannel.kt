package com.corbinbigler.piste.server

import com.corbinbigler.piste.PisteChannel

interface DownloadPisteHandlerChannel<Clientbound> {
    suspend fun closed()

    suspend fun send(value: Clientbound)
    suspend fun close()
}