package com.thysmesi.piste.server

import com.thysmesi.piste.service.StreamPisteService
import kotlinx.coroutines.CoroutineScope

interface StreamPisteHandler<Serverbound : Any, Clientbound : Any>: PisteHandler<Serverbound, Clientbound> {
    override val service: StreamPisteService<Serverbound, Clientbound>

    suspend fun handle(channel: StreamPisteHandlerChannel<Serverbound, Clientbound>, scope: CoroutineScope)
}