package com.thysmesi

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class PisteChannel<Inbound, Outbound>(
    private val sendClosure: suspend (Outbound) -> Unit = {},
    private val closeClosure: suspend () -> Unit = {}
) {

    private val inboundChannel = Channel<Inbound>(Channel.UNLIMITED)
    val inbound: Flow<Inbound> = inboundChannel.receiveAsFlow()

    private val openedDeferred = CompletableDeferred<Unit>()
    suspend fun opened(): Unit = openedDeferred.await()

    private val closedDeferred = CompletableDeferred<Unit>()
    suspend fun closed(): Unit = closedDeferred.await()

    private val completedDeferred = CompletableDeferred<Inbound>()
    suspend fun completed(): Inbound = completedDeferred.await()

    suspend fun send(value: Outbound) {
        sendClosure(value)
    }

    suspend fun close() {
        onClosed(null)
        closeClosure()
    }

    suspend fun emitInbound(value: Inbound) {
        inboundChannel.send(value)
    }

    suspend fun onClosed(error: Exception?) {
        if (error != null) {
            closedDeferred.completeExceptionally(error)
        } else {
            closedDeferred.complete(Unit)
        }
        inboundChannel.close(error)
    }

    suspend fun onCompleted(value: Inbound) {
        closedDeferred.complete(Unit)
        completedDeferred.complete(value)
        inboundChannel.close()
    }
}