package com.thysmesi.server

import com.thysmesi.service.DownloadPisteService

interface DownloadPisteHandler<Serverbound, Clientbound>: PisteHandler<Serverbound, Clientbound> {
    override val service: DownloadPisteService<Serverbound, Clientbound>

    suspend fun handle(request: Serverbound, channel: DownloadPisteHandlerChannel<Serverbound, Clientbound>)
}