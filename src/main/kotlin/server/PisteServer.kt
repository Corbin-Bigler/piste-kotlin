package com.thysmesi.piste.server

import com.thysmesi.Logger
import com.thysmesi.piste.*
import com.thysmesi.piste.codec.PisteCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import java.util.concurrent.ConcurrentHashMap
import javax.xml.crypto.Data
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

class PisteServer(
    val codec: PisteCodec,
    handlers: List<PisteHandler<*, *>>,
    logger: Logger = Logger.shared
) {
    val logger: Logger.Tagged = logger.tagged(javaClass.simpleName)
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

    suspend fun cancelAll() {
        for ((exchange, channel) in channels) {
            channel.resumeClosed(PisteInternalError.Cancelled)
            send(PisteFrame.Error(PisteError.ChannelClosed), exchange)
        }
        channels.clear()
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
                is PisteFrame.RequestCall -> handleRequestCall(frame.id, frame.payload, exchange)
                is PisteFrame.RequestDownload -> handleRequestDownload(frame.id, frame.payload, exchange)
                is PisteFrame.OpenUpload -> handleOpenUpload(frame.id, exchange)
                is PisteFrame.OpenStream -> handleOpenStream(frame.id, exchange)
                is PisteFrame.Payload -> handlePayload(frame.payload, exchange)
                is PisteFrame.Close -> handleClose(exchange)
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
            logger.error("Internal server error - exchange: $exchange, error: $error")
            sendError(PisteError.InternalServerError, exchange)
        }
    }

    private suspend fun handleRequestCall(id: PisteId, payload: ByteArray, exchange: PisteExchange) {
        logger.info("Received Request Call frame - id: $id, payload size: ${payload.size}, exchange: $exchange")
        val handler = handlers[id] ?: throw PisteError.UnsupportedService
        if (handler !is CallPisteHandler) throw PisteError.UnsupportedFrameType
        val response = handler.handleRequest(payload, this)
        sendCatching(PisteFrame.Payload(response), exchange)
    }

    private suspend fun handleRequestDownload(id: PisteId, payload: ByteArray, exchange: PisteExchange) {
        logger.info("Received Request Download frame - id: $id, payload size: ${payload.size}, exchange: $exchange")
        val handler = handlers[id] ?: throw PisteError.UnsupportedService
        if (handler !is DownloadPisteHandler) throw PisteError.UnsupportedFrameType
        val channel = handler.handleRequest(
            payload = payload,
            send = { response ->
                if (!channels.containsKey(exchange)) throw PisteInternalError.ChannelClosed
                send(PisteFrame.Payload(response), exchange)
            },
            close = {
                if (!channels.containsKey(exchange)) return@handleRequest
                removeChannel(exchange)
                sendCatching(PisteFrame.Close, exchange)
            },
            server = this
        )
        channels[exchange] = channel
        sendCatching(PisteFrame.Open, exchange)
    }

    private suspend fun handleOpenUpload(id: PisteId, exchange: PisteExchange) {
        logger.info("Received Open Upload frame - id: ${id}, exchange: $exchange")
        val handler = handlers[id] ?: throw PisteError.UnsupportedService
        if (handler !is UploadPisteHandler) throw PisteError.UnsupportedFrameType
        val channel = handler.handleOpen(
            send = { response ->
                if (!channels.containsKey(exchange)) throw PisteInternalError.ChannelClosed
                removeChannel(exchange)
                sendCatching(PisteFrame.Payload(response), exchange)
            },
            close = {
                if (!channels.containsKey(exchange)) return@handleOpen
                removeChannel(exchange)
                sendCatching(PisteFrame.Close, exchange)
            },
            codec = codec
        )
        channels[exchange] = channel
        sendCatching(PisteFrame.Open, exchange)
    }

    private suspend fun handleOpenStream(id: PisteId, exchange: PisteExchange) {
        logger.info("Received Open Stream frame - id: $id, exchange: $exchange")
        val handler = handlers[id] ?: throw PisteError.UnsupportedService
        if (handler !is StreamPisteHandler) throw PisteError.UnsupportedFrameType
        val channel = handler.handleOpen(
            send = { response ->
                if (!channels.containsKey(exchange)) throw PisteInternalError.ChannelClosed
                send(PisteFrame.Payload(response), exchange)
            },
            close = {
                if (!channels.containsKey(exchange)) return@handleOpen
                removeChannel(exchange)
                sendCatching(PisteFrame.Close, exchange)
            },
            codec = codec
        )
        channels[exchange] = channel
        sendCatching(PisteFrame.Open, exchange)
    }

    private suspend fun handlePayload(payload: ByteArray, exchange: PisteExchange) {
        logger.info("Received Payload frame - payload count: ${payload.size}, exchange: $exchange")
        val channel = channels[exchange] ?: throw PisteError.ChannelClosed
        channel.sendInbound(payload, this)
    }

    private fun handleClose(exchange: PisteExchange) {
        logger.info("Received Close frame - exchange: $exchange")
        if(channels[exchange] == null) throw PisteError.ChannelClosed
        removeChannel(exchange)
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

    private fun removeChannel(exchange: PisteExchange) {
        val channel = channels[exchange]
        if (channel != null) {
            channel.resumeClosed(null)
            channels.remove(exchange)
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

    private suspend fun <Inbound, Outbound> PisteChannel<Inbound, Outbound>.sendInbound(payload: ByteArray, server: PisteServer) {
        val inbound: Inbound = server.handleDecode(payload, serializer)
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
        handle(StreamPisteHandlerChannel(channel), CoroutineScope(coroutineContext))

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
        handle(UploadPisteHandlerChannel(channel), CoroutineScope(coroutineContext))

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

        handle(request, DownloadPisteHandlerChannel(channel), CoroutineScope(coroutineContext))

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

    data class Outbound(val exchange: PisteExchange, val frameData: ByteArray)
}