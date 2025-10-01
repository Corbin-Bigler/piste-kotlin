package com.thysmesi.server

import com.thysmesi.service.StreamPisteService

interface StreamPisteHandler<Serverbound : Any, Clientbound : Any>: PisteHandler<Serverbound, Clientbound> {
    override val service: StreamPisteService<Serverbound, Clientbound>

    suspend fun handle(channel: StreamPisteHandlerChannel<Serverbound, Clientbound>)
}