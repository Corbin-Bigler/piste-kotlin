package com.thysmesi.piste

import com.thysmesi.Logger
import com.thysmesi.piste.client.PisteClient
import com.thysmesi.piste.codec.JsonPisteCodec
import com.thysmesi.piste.server.*
import com.thysmesi.piste.service.CallPisteService
import com.thysmesi.piste.service.DownloadPisteService
import com.thysmesi.piste.service.StreamPisteService
import com.thysmesi.piste.service.UploadPisteService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PisteIntegrationTests {
    private class EchoHandler : CallPisteHandler<String, String> {
        override val service = CallPisteService.from<String, String>(0u)

        override suspend fun handle(request: String): String {
            return "echo:$request"
        }
    }

    private fun buildClient(handlers: List<PisteHandler<*, *>>): PisteClient {
        val server = PisteServer(
            codec = JsonPisteCodec,
            handlers = handlers
        )

        val client = PisteClient(
            codec = JsonPisteCodec,
        )

        server.onOutbound { outbound ->
            client.handle(outbound.exchange, outbound.frameData)
        }
        client.onOutbound { outbound ->
            server.handle(outbound.exchange, outbound.frameData)
        }

        return client
    }

    @Test
    fun `client call goes through server and returns handler response`() = runTest {
        val handler = EchoHandler()
        val client = buildClient(listOf(handler))

        val response = client.call(handler.service, "hello world")
        assertEquals("echo:hello world", response, "Client should receive server's handler response")
    }

    @Test
    fun `client download opens channel and receives streamed responses`() = runTest {
        class DownloadHandler : DownloadPisteHandler<String, String> {
            override val service = DownloadPisteService.from<String, String>(0u)

            override suspend fun handle(
                request: String,
                channel: DownloadPisteHandlerChannel<String>,
                scope: CoroutineScope
            ) {
                launch {
                    channel.send("chunk1-$request")
                    channel.send("chunk2-$request")
                    channel.close()
                }
            }
        }

        val handler = DownloadHandler()
        val client = buildClient(listOf(handler))

        val downloadChannel = client.download(handler.service, "file123")

        val received = mutableListOf<String>()

        val job = launch {
            downloadChannel.inbound.collect {
                received.add(it)
            }
        }

        job.join()

        assertEquals(listOf("chunk1-file123", "chunk2-file123"), received)
    }

    @Test
    fun `client upload sends data to server and receives response`() = runTest {
        class UploadHandler : UploadPisteHandler<String, String> {
            override val service = UploadPisteService.from<String, String>(0u)
            var received = mutableListOf<String>()

            override suspend fun handle(channel: UploadPisteHandlerChannel<String, String>, scope: CoroutineScope) {
                launch {
                    try {
                        channel.inbound.collect { msg ->
                            received.add(msg)
                            if (msg == "part2") {
                                channel.complete("ack:$msg")
                            }
                        }
                    } finally {
                    }
                }
            }
        }

        val handler = UploadHandler()
        val client = buildClient(listOf(handler))

        val uploadChannel = client.upload(handler.service)

        var response: String? = null

        val job = launch {
            response = uploadChannel.completed()
        }

        uploadChannel.send("part1")
        uploadChannel.send("part2")

        job.join()

        assertEquals(listOf("part1", "part2"), handler.received)
        assertEquals(response, "ack:part2")
    }

    @Test
    fun `client and server can exchange messages over a stream`() = runTest {
        class StreamHandler : StreamPisteHandler<String, String> {
            override val service = StreamPisteService.from<String, String>(0u)
            val received = mutableListOf<String>()

            override suspend fun handle(channel: StreamPisteHandlerChannel<String, String>, scope: CoroutineScope) {
                launch {
                    channel.inbound.collect { msg ->
                        received.add(msg)
                        runCatching {
                            channel.send("echo:$msg")
                        }
                    }
                }
            }
        }

        val handler = StreamHandler()
        val client = buildClient(listOf(handler))

        val streamChannel = client.stream(handler.service)
        val responses = mutableListOf<String>()

        val job = launch {
            streamChannel.inbound.collect {
                responses.add(it)
                if (responses.size == 3) streamChannel.close()
            }
        }

        streamChannel.send("one")
        streamChannel.send("two")
        streamChannel.send("three")

        job.join()

        assertEquals(listOf("one", "two", "three"), handler.received)

        assertEquals(listOf("echo:one", "echo:two", "echo:three"), responses)
    }

}
