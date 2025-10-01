package com.thysmesi.server

import com.thysmesi.service.UploadPisteService

interface UploadPisteHandler<Serverbound : Any, Clientbound : Any>: PisteHandler<Serverbound, Clientbound> {
    override val service: UploadPisteService<Serverbound, Clientbound>

    suspend fun handle(channel: UploadPisteHandlerChannel<Serverbound, Clientbound>)
}