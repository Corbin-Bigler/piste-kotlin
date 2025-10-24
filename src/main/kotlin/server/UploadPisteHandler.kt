package com.corbinbigler.piste.server

import com.corbinbigler.piste.service.UploadPisteService
import kotlinx.coroutines.CoroutineScope

interface UploadPisteHandler<Serverbound : Any, Clientbound : Any>: PisteHandler<Serverbound, Clientbound> {
    override val service: UploadPisteService<Serverbound, Clientbound>

    suspend fun handle(channel: UploadPisteHandlerChannel<Serverbound, Clientbound>, scope: CoroutineScope)
}