package com.corbinbigler.piste.service

import com.corbinbigler.piste.PisteId
import com.corbinbigler.piste.PisteServiceType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

interface CallPisteService<Serverbound : Any, Clientbound : Any>: PisteService<Serverbound, Clientbound> {
    override val type: PisteServiceType
        get() = PisteServiceType.CALL

    companion object {
        inline fun <reified Serverbound : Any, reified Clientbound : Any> from(id: PisteId): CallPisteService<Serverbound, Clientbound> = object : CallPisteService<Serverbound, Clientbound> {
            override val id: PisteId = id
            override val serverboundSerializer: KSerializer<Serverbound> = serializer()
            override val clientboundSerializer: KSerializer<Clientbound> = serializer()
        }
    }
}