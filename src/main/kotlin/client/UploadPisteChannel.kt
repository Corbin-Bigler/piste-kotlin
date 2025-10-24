package com.corbinbigler.piste.client

import com.corbinbigler.piste.PisteChannel

interface UploadPisteChannel<Clientbound, Serverbound> {
    suspend fun closed()
    suspend fun completed(): Clientbound

    suspend fun send(value: Serverbound)
    suspend fun close()
}