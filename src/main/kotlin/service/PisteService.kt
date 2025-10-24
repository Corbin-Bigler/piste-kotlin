package com.corbinbigler.piste.service

import com.corbinbigler.piste.PisteId
import com.corbinbigler.piste.PisteServiceType
import kotlinx.serialization.KSerializer

interface PisteService<Serverbound : Any, Clientbound : Any> {
    val id: PisteId
    val type: PisteServiceType

    val serverboundSerializer: KSerializer<Serverbound>
    val clientboundSerializer: KSerializer<Clientbound>
}