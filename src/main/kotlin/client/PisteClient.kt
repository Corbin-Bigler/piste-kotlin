package com.thysmesi.piste.client

import com.thysmesi.Logger
import com.thysmesi.piste.*
import com.thysmesi.piste.codec.PisteCodec
import com.thysmesi.piste.service.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

class PisteClient(
    val codec: PisteCodec,
    val logger: Logger.Tagged = Logger.shared.tagged("PisteClient")
) {

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
        channels.clear()

        for (request in openRequests.values + payloadRequests.values) {
            request.completeExceptionally(PisteInternalError.Cancelled)
        }
        openRequests.clear()
        payloadRequests.clear()
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
                is PisteFrame.Payload -> handlePayload(frame.payload, exchange)
                is PisteFrame.SupportedServicesResponse -> handleSupportedServicesResponse(frame.services, exchange)
                is PisteFrame.Close -> handleClose(exchange)
                is PisteFrame.Open -> handleOpen(exchange)
                is PisteFrame.Error -> handleError(frame.error, exchange)
                is PisteFrame.SupportedServicesRequest,
                is PisteFrame.RequestCall,
                is PisteFrame.RequestDownload,
                is PisteFrame.OpenUpload,
                is PisteFrame.OpenStream -> return
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.error("Internal client error - exchange: $exchange, error: $error")
        }
    }

    private suspend fun handlePayload(payload: ByteArray, exchange: PisteExchange) {
        logger.info("Received Payload frame - payload count: ${payload.size}, exchange: $exchange")
        val payloadRequest = payloadRequests[exchange]
        if (payloadRequest != null) {
            payloadRequest.complete(payload)
            return
        }

        val channelInfo = channels[exchange]
        if(channelInfo != null) {
            val (channel, upload) = channelInfo
            try {
                if (upload) {
                    channel.resumeCompleted(payload)
                    channels.remove(exchange)
                } else {
                    channel.sendInbound(payload)
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
    private fun handleError(error: PisteError, exchange: PisteExchange) {
        logger.info("Received Error frame - error: $error exchange: $exchange")

        val payloadContinuation = payloadRequests[exchange]
        if (payloadContinuation != null) {
            payloadContinuation.completeExceptionally(error)
            return
        }

        val openContinuation = openRequests[exchange]
        if (openContinuation != null) {
            openContinuation.completeExceptionally(error)
            return
        }

        logger.error("Unhandled error - error: $error, exchange: $exchange")
    }

    private fun handleSupportedServicesResponse(services: List<PisteSupportedService>, exchange: PisteExchange) {
        logger.info("Received Supported Services Response frame - services: $services exchange: $exchange")

        val supportedServices = supportedServices
        val supportedServicesMap = services.associate { it.id to it.type }
        if (supportedServices != null && supportedServices.isActive) {
            supportedServices.complete(supportedServicesMap)
        } else {
            this.supportedServices = CompletableDeferred(supportedServicesMap)
        }
    }

    suspend fun <Serverbound : Any, Clientbound : Any> call(service: CallPisteService<Serverbound, Clientbound>, request: Serverbound): Clientbound {
        isSupported(service)
        val exchange = nextExchange()
        val payload = codec.encode(request, service.serverboundSerializer)

        val deferred = CompletableDeferred<ByteArray>()
        payloadRequests[exchange] = deferred

        try {
            send(PisteFrame.RequestCall(service.id, payload), exchange)
        } catch (error: Exception) {
            payloadRequests.remove(exchange)
            deferred.completeExceptionally(error)
        }

        val responseData = deferred.await()
        payloadRequests.remove(exchange)

        val result = codec.decode(responseData, service.clientboundSerializer)
        return result
    }
    suspend fun <Serverbound : Any, Clientbound : Any> download(service: DownloadPisteService<Serverbound, Clientbound>, request: Serverbound): DownloadPisteChannel<Clientbound> {
        isSupported(service)
        val payload = codec.encode(request, service.serverboundSerializer)
        val exchange = nextExchange()

        val channel = PisteChannel<Clientbound, Serverbound>(
            serializer = service.clientboundSerializer,
            close = {
                val (channel, _) = channels[exchange] ?: return@PisteChannel
                channels.remove(exchange)
                channel.resumeClosed(null)
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
        isSupported(service)
        val exchange = nextExchange()

        val channel = PisteChannel<Clientbound, Serverbound>(
            serializer = service.clientboundSerializer,
            send = { outbound ->
                if (channels[exchange] == null) throw PisteInternalError.ChannelClosed
                send(PisteFrame.Payload(codec.encode(outbound, service.serverboundSerializer)), exchange)
            },
            close = {
                val (channel, _) = channels[exchange] ?: return@PisteChannel
                channels.remove(exchange)
                channel.resumeClosed(null)
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
            channels.remove(exchange)
            deferred.completeExceptionally(error)
        }

        deferred.await()
        openRequests.remove(exchange)
    }

    private suspend fun isSupported(service: PisteService<*, *>) {
        val type = getSupportedServices()[service.id] ?: throw PisteInternalError.UnsupportedService
        if (type != service.type) throw PisteInternalError.IncorrectServiceType
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

    private suspend fun send(frame: PisteFrame, exchange: PisteExchange) {
        logger.debug("Sending - frame: $frame, exchange: $exchange")
        outbound(Outbound(exchange, frame.data))
    }

    private fun <Inbound, Outbound> PisteChannel<Inbound, Outbound>.resumeCompleted(data: ByteArray) {
        resumeCompleted(codec.decode(data, serializer))
    }

    private suspend fun <Inbound, Outbound> PisteChannel<Inbound, Outbound>.sendInbound(data: ByteArray) {
        val inbound: Inbound = codec.decode(data, serializer)
        inboundChannel.send(inbound)
    }

    data class Outbound(val exchange: PisteExchange, val frameData: ByteArray)
}