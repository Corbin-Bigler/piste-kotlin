package com.thysmesi.piste.client

import com.thysmesi.piste.PisteChannel

interface UploadPisteChannel<Clientbound, Serverbound> {
    suspend fun closed()
    suspend fun completed(): Clientbound

    suspend fun send(value: Serverbound)
    suspend fun close()
}