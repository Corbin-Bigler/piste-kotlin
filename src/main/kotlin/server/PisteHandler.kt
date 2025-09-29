package com.thysmesi.server

import com.thysmesi.PisteId
import com.thysmesi.service.PisteService

interface PisteHandler<Serverbound, Clientbound> {
    val service: PisteService<Serverbound, Clientbound>

    val id: PisteId get() = service.id
}