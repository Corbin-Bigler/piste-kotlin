package com.thysmesi.client

import com.thysmesi.*
import com.thysmesi.codec.PisteCodec
import com.thysmesi.service.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

class PisteClient(
    val codec: PisteCodec,
    logger: Logger = Logger.shared
) {
    val logger: Logger.Tagged = logger.tagged(javaClass.simpleName)

    private val channels: ConcurrentHashMap<PisteExchange, Pair<PisteChannel<*, *>, Boolean>> = ConcurrentHashMap()
    private var outbound: suspend (Outbound) -> Unit = { }

    private val payloadRequests: ConcurrentHashMap<PisteExchange, CompletableDeferred<ByteArray>> = ConcurrentHashMap()
    private val openRequests: ConcurrentHashMap<PisteExchange, CompletableDeferred<Unit>> = ConcurrentHashMap()
    private val exchange = AtomicInteger(0)
    private var supportedServices: CompletableDeferred<Map<PisteId, PisteServiceType>>? = null

    suspend fun cancelAll() {
        logger.info("Canceling all channels and requests")

        for ((exchange, channel) in channels) {
            channel.first.resumeClosed(PisteInternalError.Cancelled)
            send(PisteFrame.Close, exchange)
        }
        for (request in openRequests.values + payloadRequests.values) {
            request.completeExceptionally(PisteInternalError.Cancelled)
        }
    }

    fun onOutbound(callback: suspend (Outbound) -> Unit) {
        this.outbound = callback
    }

    suspend fun handle(exchange: PisteExchange, frameData: ByteArray) {
        val frame = PisteFrame.from(frameData) ?: run {
            logger.error("Failed to decode frame for exchange $exchange")
            return
        }

        try {
            when (frame) {
                is PisteFrame.Payload -> handlePayload(frame, exchange)
                is PisteFrame.SupportedServicesResponse -> handleSupportedServicesResponse(frame)
                is PisteFrame.Close -> handleClose(exchange)
                is PisteFrame.Open -> handleOpen(exchange)
                is PisteFrame.Error -> handleError(frame, exchange)
                is PisteFrame.SupportedServicesRequest,
                is PisteFrame.RequestCall,
                is PisteFrame.RequestDownload,
                is PisteFrame.OpenUpload,
                is PisteFrame.OpenStream -> return
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.error("Internal client error - error: $error, exchange: $exchange")
        }
    }

    private suspend fun handlePayload(frame: PisteFrame.Payload, exchange: PisteExchange) {
        logger.info("Received Payload frame - payload count: ${frame.payload.size}, exchange: $exchange")
        val payloadRequest = payloadRequests[exchange]
        if (payloadRequest != null) {
            payloadRequest.complete(frame.payload)
            return
        }

        val channelInfo = channels[exchange]
        if(channelInfo != null) {
            val (channel, upload) = channelInfo
            try {
                if (upload) {
                    channel.resumeCompleted(frame.payload, this)
                    channels.remove(exchange)
                } else {
                    channel.sendInbound(frame.payload, this)
                }
            } catch (error: Exception) {
                send(PisteFrame.Close, exchange)
                channel.resumeClosed(error)
            }
        }
    }
    private fun handleClose(exchange: PisteExchange) {
        logger.info("Received Close frame - exchange: $exchange")
        val channelInfo = channels[exchange]
        if (channelInfo != null) {
            channels.remove(exchange)
            channelInfo.first.resumeClosed(null)
        }
    }
    private fun handleOpen(exchange: PisteExchange) {
        logger.info("Received Open frame - exchange: $exchange")

        val openRequest = openRequests[exchange]
        if (openRequest != null) {
            openRequest.complete(Unit)
            return
        }
    }
    private fun handleError(frame: PisteFrame.Error, exchange: PisteExchange) {
        logger.info("Received Error frame - error: ${frame.error} exchange: $exchange")

        val payloadContinuation = payloadRequests[exchange]
        if (payloadContinuation != null) {
            payloadContinuation.completeExceptionally(frame.error)
            return
        }

        val openContinuation = openRequests[exchange]
        if (openContinuation != null) {
            openContinuation.completeExceptionally(frame.error)
            return
        }

        logger.error("Unhandled error - error: ${frame.error}, exchange: $exchange")
    }

    private fun handleSupportedServicesResponse(frame: PisteFrame.SupportedServicesResponse) {
        logger.info("Received Supported Services Response frame - services: ${frame.services} exchange: $exchange")

        val supportedServices = supportedServices
        val supportedServicesMap = frame.services.associate { it.id to it.type }
        if (supportedServices != null && supportedServices.isActive) {
            supportedServices.complete(supportedServicesMap)
        } else {
            this.supportedServices = CompletableDeferred(supportedServicesMap)
        }
    }

    suspend fun <Serverbound : Any, Clientbound : Any> call(service: CallPisteService<Serverbound, Clientbound>, request: Serverbound): Clientbound {
        if (!isSupported(service)) {
            throw PisteInternalError.UnsupportedService
        }

        val exchange = nextExchange()
        val payload = codec.encode(request, service.serverboundSerializer)
        val responseData = requestPayload(PisteFrame.RequestCall(service.id, payload), exchange)

        val result = codec.decode(responseData, service.clientboundSerializer)
        return result
    }
    suspend fun <Serverbound : Any, Clientbound : Any> download(service: DownloadPisteService<Serverbound, Clientbound>, request: Serverbound): DownloadPisteChannel<Clientbound, Serverbound> {
        if (!isSupported(service)) {
            throw PisteInternalError.UnsupportedService
        }

        val payload = codec.encode(request, service.serverboundSerializer)
        val exchange = nextExchange()

        val channel = PisteChannel<Clientbound, Serverbound>(
            serializer = service.clientboundSerializer,
            close = {
                channels.remove(exchange)
                send(PisteFrame.Close, exchange)
            }
        )

        channels[exchange] = channel to false

        requestOpen(PisteFrame.RequestDownload(service.id, payload), exchange)

        return DownloadPisteChannel(channel)
    }
    suspend fun <Serverbound : Any, Clientbound : Any> upload(service: UploadPisteService<Serverbound, Clientbound>): UploadPisteChannel<Clientbound, Serverbound> {
        return UploadPisteChannel(openOutboundChannel(service, true))
    }
    suspend fun <Serverbound : Any, Clientbound : Any> stream(service: StreamPisteService<Serverbound, Clientbound>): StreamPisteChannel<Clientbound, Serverbound> {
        return StreamPisteChannel(openOutboundChannel(service, false))
    }

    private suspend fun <Serverbound : Any, Clientbound : Any> openOutboundChannel(service: PisteService<Serverbound, Clientbound>, upload: Boolean): PisteChannel<Clientbound, Serverbound> {
        if (!isSupported(service)) {
            throw PisteInternalError.UnsupportedService
        }

        val exchange = nextExchange()

        val channel = PisteChannel<Clientbound, Serverbound>(
            serializer = service.clientboundSerializer,
            send = { outbound ->
                if (channels[exchange] == null) throw PisteError.ChannelClosed
                send(PisteFrame.Payload(codec.encode(outbound, service.serverboundSerializer)), exchange)
            },
            close = {
                channels.remove(exchange)
                send(PisteFrame.Close, exchange)
            }
        )

        channels[exchange] = channel to upload

        val frame = if (upload) PisteFrame.OpenUpload(service.id)
        else PisteFrame.OpenStream(service.id)
        requestOpen(frame, exchange)

        return channel
    }

    private suspend fun requestOpen(frame: PisteFrame, exchange: PisteExchange) {
        val deferred = CompletableDeferred<Unit>()
        openRequests[exchange] = deferred

        try {
            send(frame, exchange)
        } catch (error: Exception) {
            openRequests.remove(exchange)
            deferred.completeExceptionally(error)
        }

        val response = deferred.await()
        openRequests.remove(exchange)
        return response
    }

    private suspend fun requestPayload(frame: PisteFrame, exchange: PisteExchange): ByteArray {
        val deferred = CompletableDeferred<ByteArray>()
        payloadRequests[exchange] = deferred

        try {
            send(frame, exchange)
        } catch (error: Exception) {
            payloadRequests.remove(exchange)
            deferred.completeExceptionally(error)
        }

        val response = deferred.await()
        payloadRequests.remove(exchange)
        return response
    }

    private suspend fun isSupported(service: PisteService<*, *>): Boolean {
        val type = getSupportedServices()[service.id] ?: return false
        return type == service.type
    }
    private suspend fun getSupportedServices(): Map<PisteId, PisteServiceType> {
        val supportedServices = supportedServices
        if (supportedServices != null) {
            return supportedServices.await()
        } else {
            val deferred = CompletableDeferred<Map<PisteId, PisteServiceType>>()
            logger.info("Fetching new service information")
            this.supportedServices = deferred
            send(PisteFrame.SupportedServicesRequest, nextExchange())
            return deferred.await()
        }
    }
    private fun nextExchange(): UInt = exchange.getAndIncrement().toUInt()

    private suspend fun sendCatching(frame: PisteFrame, exchange: PisteExchange) {
        try {
            send(frame, exchange)
        } catch(error: Exception) {
            logger.error("Caught Sending - frame: $frame, exchange: $exchange, error: $error")
        }
    }
    private suspend fun send(frame: PisteFrame, exchange: PisteExchange) {
        logger.debug("Sending - frame: $frame, exchange: $exchange")
        outbound(Outbound(exchange, frame.data))
    }

    private fun <Inbound, Outbound> PisteChannel<Inbound, Outbound>.resumeCompleted(data: ByteArray, client: PisteClient) {
        resumeCompleted(codec.decode(data, serializer))
    }

    private suspend fun <Inbound, Outbound> PisteChannel<Inbound, Outbound>.sendInbound(data: ByteArray, client: PisteClient) {
        val inbound: Inbound = codec.decode(data, serializer)
        inboundChannel.send(inbound)
    }

    data class Outbound(val exchange: PisteExchange, val frameData: ByteArray)
}