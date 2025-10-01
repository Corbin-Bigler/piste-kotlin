package com.thysmesi.server

import com.thysmesi.PisteId
import com.thysmesi.service.PisteService

interface PisteHandler<Serverbound : Any, Clientbound : Any> {
    val service: PisteService<Serverbound, Clientbound>

    val id: PisteId get() = service.id
}