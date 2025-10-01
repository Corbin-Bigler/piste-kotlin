package com.thysmesi.server

import com.thysmesi.service.CallPisteService

interface CallPisteHandler<Serverbound : Any, Clientbound : Any>: PisteHandler<Serverbound, Clientbound> {
    override val service: CallPisteService<Serverbound, Clientbound>

    suspend fun handle(request: Serverbound): Clientbound
}