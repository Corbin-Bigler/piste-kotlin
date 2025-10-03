package com.thysmesi.piste.service

import com.thysmesi.piste.PisteId
import com.thysmesi.piste.PisteServiceType
import kotlinx.serialization.KSerializer

interface PisteService<Serverbound : Any, Clientbound : Any> {
    val id: PisteId
    val type: PisteServiceType

    val serverboundSerializer: KSerializer<Serverbound>
    val clientboundSerializer: KSerializer<Clientbound>
}