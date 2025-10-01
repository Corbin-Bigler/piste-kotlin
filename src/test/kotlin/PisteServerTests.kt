package com.thysmesi

import com.thysmesi.service.CallPisteService
import com.thysmesi.codec.JsonPisteCodec
import com.thysmesi.server.*
import com.thysmesi.service.DownloadPisteService
import com.thysmesi.service.StreamPisteService
import com.thysmesi.service.UploadPisteService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PisteServerTests {

    private class FakeCallHandler<T : Any, V : Any>(
        override val service: CallPisteService<T, V>,
        val response: V
    ) : CallPisteHandler<T, V> {
        override suspend fun handle(request: T): V = response
    }

    private class FakeDownloadHandler<T : Any, V : Any>(
        override val service: DownloadPisteService<T, V>
    ) : DownloadPisteHandler<T, V> {
        override suspend fun handle(
            request: T,
            channel: DownloadPisteHandlerChannel<T, V>
        ) {}
    }

    private class FakeUploadHandler<T : Any, V : Any>(
        override val service: UploadPisteService<T, V>
    ) : UploadPisteHandler<T, V> {
        var capturedChannel: UploadPisteHandlerChannel<T, V>? = null

        override suspend fun handle(channel: UploadPisteHandlerChannel<T, V>) {
            capturedChannel = channel
        }
    }

    private class FakeStreamHandler<T : Any, V : Any>(
        override val service: StreamPisteService<T, V>
    ) : StreamPisteHandler<T, V> {
        var capturedChannel: StreamPisteHandlerChannel<T, V>? = null

        override suspend fun handle(channel: StreamPisteHandlerChannel<T, V>) {
            capturedChannel = channel
        }
    }

    @Test
    fun `duplicate handler ids should fail`() {
        val handler1 = FakeCallHandler(CallPisteService.from<String, String>(0u), "response1")
        val handler2 = FakeCallHandler(CallPisteService.from<String, String>(0u), "response2")

        assertFailsWith<AssertionError> {
            PisteServer(
                codec = JsonPisteCodec,
                handlers = listOf(handler1, handler2),
                logger = Logger.shared
            )
        }
    }

    @Test
    fun `invalid frame data should not crash and send nothing`() = runTest {
        val handler = FakeCallHandler(CallPisteService.from<String, String>(0u), "ok")

        val sent = mutableListOf<PisteServer.Outbound>()
        val server = PisteServer(
            codec = JsonPisteCodec,
            handlers = listOf(handler),
            logger = Logger.shared
        ).apply {
            onOutbound { sent.add(it) }
        }

        val invalidBytes = byteArrayOf(0x00, 0x01, 0x02)

        server.handle(42u, invalidBytes)

        assertTrue(sent.isEmpty(), "Server should not send anything on invalid input")
    }

    @Test
    fun `RequestCall dispatches to correct handler`() = runTest {
        val handler = FakeCallHandler(CallPisteService.from<String, String>(0u), "handler response")

        val sent = mutableListOf<PisteServer.Outbound>()
        val server = PisteServer(
            codec = JsonPisteCodec,
            handlers = listOf(handler),
            logger = Logger.shared
        ).apply {
            onOutbound { sent.add(it) }
        }

        val request = "ping"
        val payload = JsonPisteCodec.encode(request, handler.service.serverboundSerializer)
        val frame = PisteFrame.RequestCall(handler.id, payload)
        val frameData = frame.data

        server.handle(1u, frameData)

        assertEquals(1, sent.size, "Should have sent one outbound frame")
        val outboundFrame = PisteFrame.from(sent.first().frameData)
        assertIs<PisteFrame.Payload>(outboundFrame, "Response should be a Payload frame")

        val decoded = JsonPisteCodec.decode(
            outboundFrame.payload,
            handler.service.clientboundSerializer
        )
        assertEquals("handler response", decoded, "Handler response should match expected")
    }

    @Test
    fun `RequestDownload opens channel and sends Open frame`() = runTest {
        val handler = FakeDownloadHandler(
            DownloadPisteService.from<String, String>(0u)
        )

        val sent = mutableListOf<PisteServer.Outbound>()
        val server = PisteServer(
            codec = JsonPisteCodec,
            handlers = listOf(handler),
            logger = Logger.shared
        ).apply {
            onOutbound { sent.add(it) }
        }

        val request = "download please"
        val payload = JsonPisteCodec.encode(request, handler.service.serverboundSerializer)
        val frame = PisteFrame.RequestDownload(handler.id, payload)

        server.handle(42u, frame.data)

        val outboundFrame = PisteFrame.from(sent.first().frameData)
        assertIs<PisteFrame.Open>(outboundFrame, "Expected an Open frame after RequestDownload")
    }

    @Test
    fun `RequestDownload with wrong handler type sends UnsupportedFrameType error`() = runTest {
        val callHandler = FakeCallHandler(
            CallPisteService.from<String, String>(0u),
            "call response"
        )

        val sent = mutableListOf<PisteServer.Outbound>()
        val server = PisteServer(
            codec = JsonPisteCodec,
            handlers = listOf(callHandler),
            logger = Logger.shared
        ).apply {
            onOutbound { sent.add(it) }
        }

        val payload = JsonPisteCodec.encode("bad request", callHandler.service.serverboundSerializer)
        val frame = PisteFrame.RequestDownload(callHandler.id, payload)

        server.handle(42u, frame.data)

        val outboundFrame = PisteFrame.from(sent.first().frameData)
        assertIs<PisteFrame.Error>(outboundFrame, "Should respond with an Error frame")
        assertEquals(PisteError.UnsupportedFrameType, outboundFrame.error)
    }

    @Test
    fun `OpenUpload opens channel and sends Open frame`() = runTest {
        val handler = FakeUploadHandler(
            UploadPisteService.from<String, String>(0u)
        )

        val sent = mutableListOf<PisteServer.Outbound>()
        val server = PisteServer(
            codec = JsonPisteCodec,
            handlers = listOf(handler),
            logger = Logger.shared
        ).apply {
            onOutbound { sent.add(it) }
        }

        val frame = PisteFrame.OpenUpload(handler.id)
        server.handle(42u, frame.data)

        assertEquals(1, sent.size, "Expected an outbound frame")
        val outboundFrame = PisteFrame.from(sent.first().frameData)
        assertIs<PisteFrame.Open>(outboundFrame, "OpenUpload should result in an Open frame")
    }

    @Test
    fun `OpenStream opens channel and sends Open frame`() = runTest {
        val handler = FakeStreamHandler(
            StreamPisteService.from<String, String>(0u)
        )

        val sent = mutableListOf<PisteServer.Outbound>()
        val server = PisteServer(
            codec = JsonPisteCodec,
            handlers = listOf(handler),
            logger = Logger.shared
        ).apply {
            onOutbound { sent.add(it) }
        }

        val frame = PisteFrame.OpenStream(handler.id)
        server.handle(42u, frame.data)

        assertEquals(1, sent.size, "Expected an outbound frame")
        val outboundFrame = PisteFrame.from(sent.first().frameData)
        assertIs<PisteFrame.Open>(outboundFrame, "OpenStream should result in an Open frame")
    }

    @Test
    fun `Payload is routed to handler channel`() = runTest {
        val handler = FakeUploadHandler(
            UploadPisteService.from<String, String>(0u)
        )

        val sent = mutableListOf<PisteServer.Outbound>()
        val server = PisteServer(
            codec = JsonPisteCodec,
            handlers = listOf(handler),
            logger = Logger.shared
        ).apply {
            onOutbound { sent.add(it) }
        }

        val openFrame = PisteFrame.OpenUpload(handler.service.id)
        server.handle(42u, openFrame.data)

        val channel = handler.capturedChannel
        assertNotNull(channel, "Handler should have received a channel")

        val payload = JsonPisteCodec.encode("hello inbound", handler.service.serverboundSerializer)
        val payloadFrame = PisteFrame.Payload(payload)
        launch {
            server.handle(42u, payloadFrame.data)
        }

        val received = channel.inbound.first()
        assertEquals("hello inbound", received)
    }

    @Test
    fun `Close frame closes handler channel`() = runTest {
        val handler = FakeUploadHandler(
            UploadPisteService.from<String, String>(0u)
        )

        val sent = mutableListOf<PisteServer.Outbound>()
        val server = PisteServer(
            codec = JsonPisteCodec,
            handlers = listOf(handler),
            logger = Logger.shared
        ).apply {
            onOutbound { sent.add(it) }
        }

        val openFrame = PisteFrame.OpenUpload(handler.service.id)
        server.handle(42u, openFrame.data)

        val channel = handler.capturedChannel
        assertNotNull(channel, "Handler should have received a channel")

        launch {
            server.handle(42u, PisteFrame.Close.data)
        }

        val collected = mutableListOf<String>()
        channel.inbound.collect {
            collected.add(it)
        }
        assertTrue(collected.isEmpty(), "No inbound messages expected after close")
    }

    @Test
    fun `SupportedServicesRequest returns all registered services`() = runTest {
        val handler1 = FakeCallHandler(CallPisteService.from<String, String>(0u), "h1")
        val handler2 = FakeCallHandler(CallPisteService.from<String, String>(1u), "h2")

        val sent = mutableListOf<PisteServer.Outbound>()
        val server = PisteServer(
            codec = JsonPisteCodec,
            handlers = listOf(handler1, handler2),
            logger = Logger.shared
        ).apply {
            onOutbound { sent.add(it) }
        }

        // Send SupportedServicesRequest
        val frame = PisteFrame.SupportedServicesRequest
        server.handle(42u, frame.data)

        assertEquals(1, sent.size, "Expected a single outbound frame")

        val outboundFrame = PisteFrame.from(sent.first().frameData)
        assertIs<PisteFrame.SupportedServicesResponse>(outboundFrame)

        val services = outboundFrame.services.map { it.id }
        assertTrue(handler1.id in services)
        assertTrue(handler2.id in services)
    }

    @Test
    fun `Unsupported frame returns UnsupportedFrameType error`() = runTest {
        val handler = FakeCallHandler(CallPisteService.from<String, String>(0u), "resp")

        val sent = mutableListOf<PisteServer.Outbound>()
        val server = PisteServer(
            codec = JsonPisteCodec,
            handlers = listOf(handler),
            logger = Logger.shared
        ).apply {
            onOutbound { sent.add(it) }
        }

        val unsupportedFrame = PisteFrame.Error(PisteError.InternalServerError)
        server.handle(42u, unsupportedFrame.data)

        assertEquals(1, sent.size, "Expected error response")

        val outboundFrame = PisteFrame.from(sent.first().frameData)
        assertIs<PisteFrame.Error>(outboundFrame)
        assertEquals(PisteError.UnsupportedFrameType, outboundFrame.error)
    }

    @Test
    fun `Multiple sends on same channel are serialized`() = runTest {
        val handler = FakeStreamHandler(
            StreamPisteService.from<String, String>(0u)
        )

        val sent = mutableListOf<String>()
        val server = PisteServer(
            codec = JsonPisteCodec,
            handlers = listOf(handler),
            logger = Logger.shared
        ).apply {
            onOutbound { outbound ->
                val frame = PisteFrame.from(outbound.frameData)
                if (frame is PisteFrame.Payload) {
                    val decoded = JsonPisteCodec.decode(frame.payload, handler.service.clientboundSerializer)
                    sent.add(decoded)
                }
            }
        }

        val openFrame = PisteFrame.OpenStream(handler.service.id)
        server.handle(42u, openFrame.data)
        val channel = handler.capturedChannel!!

        val jobs = List(10) { i ->
            launch {
                channel.send("msg-$i")
            }
        }
        jobs.forEach { it.join() }

        assertEquals((0..9).map { "msg-$it" }, sent, "Messages should be serialized in order")
    }
}