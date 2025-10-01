package com.thysmesi.server

import com.thysmesi.*
import com.thysmesi.codec.PisteCodec
import kotlinx.serialization.KSerializer
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

class PisteServer(
    val codec: PisteCodec,
    handlers: List<PisteHandler<*, *>>,
    logger: Logger = Logger.shared
) {
    val logger: Logger.Tagged = logger.tagged("PisteServer")
    private val handlers: Map<PisteId, PisteHandler<*, *>>

    private var outbound: suspend (Outbound) -> Unit = {}
    private var channels: ConcurrentHashMap<PisteExchange, PisteChannel<*, *>> = ConcurrentHashMap()

    init {
        val handlersDictionary: MutableMap<PisteId, PisteHandler<*, *>> = handlers.fold(mutableMapOf()) { dict, handler ->
            assert(!dict.containsKey(handler.id)) {
                "Handler for id: ${handler.id} already registered (this is a bug)"
            }
            dict[handler.id] = handler
            return@fold dict
        }

        this.handlers = handlersDictionary
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
                is PisteFrame.RequestCall -> handleRequestCall(frame, exchange)
                is PisteFrame.RequestDownload -> handleRequestDownload(frame, exchange)
                is PisteFrame.OpenUpload -> handleOpenUpload(frame, exchange)
                is PisteFrame.OpenStream -> handleOpenStream(frame, exchange)
                is PisteFrame.Payload -> handlePayload(frame, exchange)
                is PisteFrame.Close -> handleClose(frame, exchange)
                is PisteFrame.SupportedServicesRequest -> handleSupportedServicesRequest(exchange)
                is PisteFrame.Open,
                is PisteFrame.Error,
                is PisteFrame.SupportedServicesResponse -> handleUnsupported(frame, exchange)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: PisteError) {
            sendError(error, exchange)
        } catch (error: Exception) {
            logger.error("Internal server error - error: $error, exchange: $exchange")
            sendError(PisteError.InternalServerError, exchange)
        }
    }

    private suspend fun handleRequestCall(frame: PisteFrame.RequestCall, exchange: PisteExchange) {
        logger.info("Received Request Call frame - id: ${frame.id}, payload size: ${frame.payload.size}, exchange: $exchange")
        val handler = handlers[frame.id] ?: throw PisteError.UnsupportedService
        if (handler !is CallPisteHandler) throw PisteError.UnsupportedFrameType
        val response = handler.handleRequest(frame.payload, this)
        sendCatching(PisteFrame.Payload(response), exchange)
    }

    private suspend fun handleRequestDownload(frame: PisteFrame.RequestDownload, exchange: PisteExchange) {
        logger.info("Received Request Download frame - id: ${frame.id}, payload size: ${frame.payload.size}, exchange: $exchange")
        val handler = handlers[frame.id] ?: throw PisteError.UnsupportedService
        if (handler !is DownloadPisteHandler) throw PisteError.UnsupportedFrameType
        val channel = handler.handleRequest(
            payload = frame.payload,
            send = { response ->
                if (!channels.containsKey(exchange)) throw PisteError.ChannelClosed
                send(PisteFrame.Payload(response), exchange)
            },
            close = {
                if (!channels.containsKey(exchange)) return@handleRequest
                channels.remove(exchange)
                sendCatching(PisteFrame.Close, exchange)
            },
            server = this
        )
        channels[exchange] = channel
        sendCatching(PisteFrame.Open, exchange)
    }

    private suspend fun handleOpenUpload(frame: PisteFrame.OpenUpload, exchange: PisteExchange) {
        logger.info("Received Open Upload frame - id: ${frame.id}, exchange: $exchange")
        val handler = handlers[frame.id] ?: throw PisteError.UnsupportedService
        if (handler !is UploadPisteHandler) throw PisteError.UnsupportedFrameType
        val channel = handler.handleOpen(
            send = { response ->
                if (!channels.containsKey(exchange)) throw PisteError.ChannelClosed
                channels.remove(exchange)
                sendCatching(PisteFrame.Payload(response), exchange)
            },
            close = {
                if (!channels.containsKey(exchange)) return@handleOpen
                channels.remove(exchange)
                sendCatching(PisteFrame.Close, exchange)
            },
            codec = codec
        )
        channels[exchange] = channel
        sendCatching(PisteFrame.Open, exchange)
    }

    private suspend fun handleOpenStream(frame: PisteFrame.OpenStream, exchange: PisteExchange) {
        logger.info("Received Open Stream frame - id: ${frame.id}, exchange: $exchange")
        val handler = handlers[frame.id] ?: throw PisteError.UnsupportedService
        if (handler !is StreamPisteHandler) throw PisteError.UnsupportedFrameType
        val channel = handler.handleOpen(
            send = { response ->
                if (!channels.containsKey(exchange)) throw PisteError.ChannelClosed
                send(PisteFrame.Payload(response), exchange)
            },
            close = {
                if (!channels.containsKey(exchange)) return@handleOpen
                channels.remove(exchange)
                sendCatching(PisteFrame.Close, exchange)
            },
            codec = codec
        )
        channels[exchange] = channel
        sendCatching(PisteFrame.Open, exchange)
    }

    private suspend fun handlePayload(frame: PisteFrame.Payload, exchange: PisteExchange) {
        logger.info("Received Payload frame - payload count: ${frame.payload.size}, exchange: $exchange")
        val channel = channels[exchange] ?: throw PisteError.ChannelClosed
        channel.sendInbound(frame.payload, this)
    }

    private fun handleClose(frame: PisteFrame.Close, exchange: PisteExchange) {
        logger.info("Received Close frame - exchange: $exchange")
        val channel = channels[exchange] ?: throw PisteError.ChannelClosed
        channels.remove(exchange)
        channel.resumeClosed(null)
    }

    private suspend fun handleSupportedServicesRequest(exchange: PisteExchange) {
        logger.info("Received Supported Services Request frame - exchange: $exchange")
        sendCatching(
            PisteFrame.SupportedServicesResponse(
                handlers.values.map { PisteSupportedService(it.id, it.service.type) }
            ),
            exchange
        )
    }

    private suspend fun handleUnsupported(frame: PisteFrame, exchange: PisteExchange) {
        logger.info("Received Unsupported frame - type: ${frame.type} exchange: $exchange")
        sendCatching(PisteFrame.Error(PisteError.UnsupportedFrameType), exchange)
    }

    private fun <T> handleDecode(payload: ByteArray, serializer: KSerializer<T>): T {
        try {
            return codec.decode(payload, serializer)
        } catch (error: Exception) {
            logger.error("Failed to decode - payload count: ${payload.size}, error: $error")
            throw PisteError.DecodingFailed
        }
    }

    private suspend fun sendError(error: PisteError, exchange: PisteExchange) {
        sendCatching(PisteFrame.Error(error), exchange)
    }
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

    data class Outbound(val exchange: PisteExchange, val frameData: ByteArray)

    private suspend fun <Inbound, Outbound> PisteChannel<Inbound, Outbound>.sendInbound(data: ByteArray, server: PisteServer) {
        val inbound: Inbound = server.handleDecode(data, serializer)
        inboundChannel.send(inbound)
    }

    private suspend fun <Serverbound : Any, Clientbound : Any> StreamPisteHandler<Serverbound, Clientbound>.handleOpen(
        send: suspend (response: ByteArray) -> Unit,
        close: suspend () -> Unit,
        codec: PisteCodec
    ): PisteChannel<*, *> {
        val channel = PisteChannel<Serverbound, Clientbound>(
            serializer = service.serverboundSerializer,
            send = { send(codec.encode(it, service.clientboundSerializer)) },
            close = close
        )
        handle(StreamPisteHandlerChannel(channel))

        return channel
    }

    private suspend fun <Serverbound : Any, Clientbound : Any> UploadPisteHandler<Serverbound, Clientbound>.handleOpen(
        send: suspend (response: ByteArray) -> Unit,
        close: suspend () -> Unit,
        codec: PisteCodec
    ): PisteChannel<*, *> {
        val channel = PisteChannel<Serverbound, Clientbound>(
            serializer = service.serverboundSerializer,
            send = { send(codec.encode(it, service.clientboundSerializer)) },
            close = close
        )
        handle(UploadPisteHandlerChannel(channel))

        return channel
    }

    private suspend fun <Serverbound : Any, Clientbound : Any> DownloadPisteHandler<Serverbound, Clientbound>.handleRequest(
        payload: ByteArray,
        send: suspend (response: ByteArray) -> Unit,
        close: suspend () -> Unit,
        server: PisteServer
    ): PisteChannel<*, *> {
        val request: Serverbound = server.handleDecode(payload, service.serverboundSerializer)
        val channel = PisteChannel<Serverbound, Clientbound>(
            serializer = service.serverboundSerializer,
            send = { send(server.codec.encode(it, service.clientboundSerializer)) },
            close = close
        )

        handle(request, DownloadPisteHandlerChannel(channel))

        return channel
    }

    private suspend fun <Serverbound : Any, Clientbound : Any> CallPisteHandler<Serverbound, Clientbound>.handleRequest(
        payload: ByteArray,
        server: PisteServer
    ): ByteArray {
        val request: Serverbound = server.handleDecode(payload, service.serverboundSerializer)
        val response = handle(request)

        return server.codec.encode(response, service.clientboundSerializer)
    }
}