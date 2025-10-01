package com.thysmesi.server

import com.thysmesi.service.DownloadPisteService

interface DownloadPisteHandler<Serverbound : Any, Clientbound : Any>: PisteHandler<Serverbound, Clientbound> {
    override val service: DownloadPisteService<Serverbound, Clientbound>

    suspend fun handle(request: Serverbound, channel: DownloadPisteHandlerChannel<Serverbound, Clientbound>)
}