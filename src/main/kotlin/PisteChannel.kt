package com.corbinbigler.piste

import com.corbinbigler.piste.client.DownloadPisteChannel
import com.corbinbigler.piste.client.StreamPisteChannel
import com.corbinbigler.piste.client.UploadPisteChannel
import com.corbinbigler.piste.server.DownloadPisteHandlerChannel
import com.corbinbigler.piste.server.StreamPisteHandlerChannel
import com.corbinbigler.piste.server.UploadPisteHandlerChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer

class PisteChannel<Inbound, Outbound>(
    val serializer: KSerializer<Inbound>,
    close: suspend () -> Unit,
    send: suspend (Outbound) -> Unit = {},
):
    DownloadPisteChannel<Inbound>,
    UploadPisteChannel<Inbound, Outbound>,
    StreamPisteChannel<Inbound, Outbound>,
    DownloadPisteHandlerChannel<Outbound>,
    UploadPisteHandlerChannel<Inbound, Outbound>,
    StreamPisteHandlerChannel<Inbound, Outbound>
{
    private val mutex = Mutex()
    private val sendClosure = send
    private val closeClosure = close

    val inboundChannel = Channel<Inbound>()
    override val inbound: Flow<Inbound> = inboundChannel.receiveAsFlow()

    private val closedDeferred = CompletableDeferred<Unit>()
    override suspend fun closed(): Unit = closedDeferred.await()
    fun resumeClosed(error: Exception?) {
        onClosed(error)
    }

    private val completedDeferred = CompletableDeferred<Inbound>()
    override suspend fun completed(): Inbound = completedDeferred.await()
    fun resumeCompleted(inbound: Inbound) {
        onCompleted(inbound)
    }

    override suspend fun complete(response: Outbound) {
        send(response)
    }

    override suspend fun send(value: Outbound) {
        mutex.withLock {
            sendClosure(value)
        }
    }

    override suspend fun close() {
        onClosed(null)
        closeClosure()
    }

    private fun onClosed(error: Throwable?) {
        if (error != null) {
            closedDeferred.completeExceptionally(error)
        } else {
            closedDeferred.complete(Unit)
        }
        inboundChannel.close(error)
    }

    private fun onCompleted(value: Inbound) {
        closedDeferred.complete(Unit)
        completedDeferred.complete(value)
        inboundChannel.close()
    }
}