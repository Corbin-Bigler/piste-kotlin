package com.corbinbigler.piste.server

import com.corbinbigler.piste.service.DownloadPisteService
import kotlinx.coroutines.CoroutineScope

interface DownloadPisteHandler<Serverbound : Any, Clientbound : Any>: PisteHandler<Serverbound, Clientbound> {
    override val service: DownloadPisteService<Serverbound, Clientbound>

    suspend fun handle(request: Serverbound, channel: DownloadPisteHandlerChannel<Clientbound>, scope: CoroutineScope)
}