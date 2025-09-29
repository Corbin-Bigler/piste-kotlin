package com.thysmesi.server

import com.thysmesi.service.UploadPisteService

interface UploadPisteHandler<Serverbound, Clientbound>: PisteHandler<Serverbound, Clientbound> {
    override val service: UploadPisteService<Serverbound, Clientbound>

    suspend fun handle(channel: UploadPisteHandlerChannel<Serverbound, Clientbound>)
}