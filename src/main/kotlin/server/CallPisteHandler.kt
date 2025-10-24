package com.corbinbigler.piste.server

import com.corbinbigler.piste.service.CallPisteService
import kotlinx.coroutines.CoroutineScope

interface CallPisteHandler<Serverbound : Any, Clientbound : Any>: PisteHandler<Serverbound, Clientbound> {
    override val service: CallPisteService<Serverbound, Clientbound>

    suspend fun handle(request: Serverbound): Clientbound
}