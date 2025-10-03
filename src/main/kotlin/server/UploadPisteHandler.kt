package com.thysmesi.piste.server

import com.thysmesi.piste.service.UploadPisteService
import kotlinx.coroutines.CoroutineScope

interface UploadPisteHandler<Serverbound : Any, Clientbound : Any>: PisteHandler<Serverbound, Clientbound> {
    override val service: UploadPisteService<Serverbound, Clientbound>

    suspend fun handle(channel: UploadPisteHandlerChannel<Serverbound, Clientbound>, scope: CoroutineScope)
}