package com.corbinbigler.piste.server

import com.corbinbigler.piste.PisteId
import com.corbinbigler.piste.service.PisteService

interface PisteHandler<Serverbound : Any, Clientbound : Any> {
    val service: PisteService<Serverbound, Clientbound>

    val id: PisteId get() = service.id
}