package com.thysmesi

import com.thysmesi.codec.PisteCodec
import com.thysmesi.server.PisteServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer

class PisteChannel<Inbound, Outbound>(
    val serializer: KSerializer<Inbound>,
    send: suspend (Outbound) -> Unit = {},
    close: suspend () -> Unit = {}
) {
    private val mutex = Mutex()
    private val sendClosure = send
    private val closeClosure = close

    val inboundChannel = Channel<Inbound>()
    val inbound: Flow<Inbound> = inboundChannel.receiveAsFlow()

    private val openedDeferred = CompletableDeferred<Unit>()
    suspend fun opened(): Unit = openedDeferred.await()
    fun resumeOpened() {
        openedDeferred.complete(Unit)
    }

    private val closedDeferred = CompletableDeferred<Unit>()
    suspend fun closed(): Unit = closedDeferred.await()
    fun resumeClosed(error: Exception?) {
        onClosed(error)
    }

    private val completedDeferred = CompletableDeferred<Inbound>()
    suspend fun completed(): Inbound = completedDeferred.await()
    fun resumeCompleted(data: ByteArray, codec: PisteCodec) {
        onCompleted(codec.decode(data, serializer))
    }

    suspend fun send(value: Outbound) {
        mutex.withLock {
            sendClosure(value)
        }
    }

    suspend fun close() {
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