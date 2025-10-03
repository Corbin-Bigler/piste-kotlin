package com.thysmesi.piste

import com.thysmesi.Logger
import com.thysmesi.piste.client.PisteClient
import com.thysmesi.piste.codec.JsonPisteCodec
import com.thysmesi.piste.server.*
import com.thysmesi.piste.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PisteClientTests {
    private val callSvc = CallPisteService.from<String, String>(0u)

    private suspend fun buildClient(sent: MutableList<PisteClient.Outbound> = mutableListOf(), services: List<PisteService<*, *>> = listOf()): PisteClient {
        val client = PisteClient(
            codec = JsonPisteCodec,
            logger = Logger.shared
        ).apply {
            onOutbound { out ->
                if(PisteFrame.from(out.frameData)?.type != PisteFrameType.SUPPORTED_SERVICES_REQUEST) {
                    sent.add(out)
                }
            }
        }

        client.handle(0xffffffffu, PisteFrame.SupportedServicesResponse(services.map { PisteSupportedService(it.id, it.type) }).data)

        return client
    }

    @Test
    fun `Invalid service throws`() = runTest {
        val sent = mutableListOf<PisteClient.Outbound>()

        val client = buildClient(sent)

        client.handle(0x00u, byteArrayOf(0x01, 0x02))

        assertTrue(sent.isEmpty(), "Client should not emit anything when fed invalid bytes")
    }

    @Test
    fun `call sends RequestCall and returns decoded response`() = runTest {
        val sent = mutableListOf<PisteClient.Outbound>()
        val client = buildClient(sent, listOf(callSvc))

        val response = async {
            val response = async {
                client.call(callSvc, "ping")
            }
            launch {
                val request = sent.first()
                val frame = PisteFrame.from(request.frameData)
                assertIs<PisteFrame.RequestCall>(frame)
                assertEquals(callSvc.id, frame.id)
                client.handle(request.exchange, PisteFrame.Payload("\"pong\"".toByteArray(Charsets.UTF_8)).data)
            }
            return@async response.await()
        }

        assertEquals("pong", response.await())
    }

    @Test
    fun `unsupported service throws UnsupportedService`() = runTest {
        val client = buildClient()
        assertFailsWith<PisteInternalError.UnsupportedService> {
            client.call(CallPisteService.from<String, String>(999u), "hello")
        }
    }

    @Test
    fun `cancelAll cancels all active requests and channels`() = runTest {
        class HangingHandler : CallPisteHandler<String, String> {
            override val service = CallPisteService.from<String, String>(0u)
            override suspend fun handle(request: String): String {
                delay(Long.MAX_VALUE)
                return "never"
            }
        }
        val client = buildClient(services = listOf(HangingHandler().service))

        val job = launch {
            launch {
                client.cancelAll()
            }
            runCatching {
                client.call(HangingHandler().service, "hang")
            }
        }

        job.join()

        assertTrue(job.isCompleted)
    }
}
