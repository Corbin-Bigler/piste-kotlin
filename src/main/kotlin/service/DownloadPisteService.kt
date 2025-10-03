package com.thysmesi.piste.service

import com.thysmesi.piste.PisteId
import com.thysmesi.piste.PisteServiceType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

interface DownloadPisteService<Serverbound : Any, Clientbound : Any>: PisteService<Serverbound, Clientbound> {
    override val type: PisteServiceType
        get() = PisteServiceType.DOWNLOAD

    companion object {
        inline fun <reified Serverbound : Any, reified Clientbound : Any> from(
            id: PisteId
        ): DownloadPisteService<Serverbound, Clientbound> = object : DownloadPisteService<Serverbound, Clientbound> {
            override val id: PisteId = id
            override val serverboundSerializer: KSerializer<Serverbound> = serializer()
            override val clientboundSerializer: KSerializer<Clientbound> = serializer()
        }
    }
}