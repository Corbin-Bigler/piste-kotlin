package com.thysmesi.service

import com.thysmesi.PisteId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

interface PisteService<Serverbound, Clientbound> {
    val id: PisteId

    val title: String
    val description: String
}

inline val <reified Serverbound> PisteService<Serverbound, *>.serverboundSerializer: KSerializer<Serverbound> get() = serializer<Serverbound>()
inline val <reified Clientbound> PisteService<*, Clientbound>.clientboundSerializer: KSerializer<Clientbound> get() = serializer<Clientbound>()