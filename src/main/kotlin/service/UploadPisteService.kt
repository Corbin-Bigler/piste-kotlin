package com.thysmesi.service

import com.thysmesi.PisteId
import com.thysmesi.PisteServiceType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

interface UploadPisteService<Serverbound : Any, Clientbound : Any>: PisteService<Serverbound, Clientbound> {
    override val type: PisteServiceType
        get() = PisteServiceType.UPLOAD

    companion object {
        inline fun <reified Serverbound : Any, reified Clientbound : Any> from(
            id: PisteId
        ): UploadPisteService<Serverbound, Clientbound> = object : UploadPisteService<Serverbound, Clientbound> {
            override val id: PisteId = id
            override val serverboundSerializer: KSerializer<Serverbound> = serializer()
            override val clientboundSerializer: KSerializer<Clientbound> = serializer()
        }
    }
}