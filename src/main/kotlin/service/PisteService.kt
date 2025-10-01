package com.thysmesi.service

import com.thysmesi.PisteId
import com.thysmesi.PisteServiceType
import kotlinx.serialization.KSerializer

interface PisteService<Serverbound : Any, Clientbound : Any> {
    val id: PisteId
    val type: PisteServiceType

    val serverboundSerializer: KSerializer<Serverbound>
    val clientboundSerializer: KSerializer<Clientbound>
}