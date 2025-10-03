package com.thysmesi.piste.server

import com.thysmesi.piste.PisteId
import com.thysmesi.piste.service.PisteService

interface PisteHandler<Serverbound : Any, Clientbound : Any> {
    val service: PisteService<Serverbound, Clientbound>

    val id: PisteId get() = service.id
}