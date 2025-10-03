package com.thysmesi.piste

import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class PisteFrameTests {

    private fun roundTrip(frame: PisteFrame): PisteFrame {
        val encoded = frame.data
        val decoded = PisteFrame.from(encoded)
        assertNotNull(decoded, "Decoding returned null for $frame")
        return decoded
    }

    @Test
    fun testRequestCall() {
        val frame = PisteFrame.RequestCall(1234u, byteArrayOf(1, 2, 3))
        val decoded = roundTrip(frame)
        assertIs<PisteFrame.RequestCall>(decoded)
        assertEquals(frame.id, decoded.id)
        assertTrue(frame.payload.contentEquals(decoded.payload))
    }

    @Test
    fun testRequestDownload() {
        val frame = PisteFrame.RequestDownload(5678u, byteArrayOf(9, 8, 7, 6))
        val decoded = roundTrip(frame)
        assertIs<PisteFrame.RequestDownload>(decoded)
        assertEquals(frame.id, decoded.id)
        assertTrue(frame.payload.contentEquals(decoded.payload))
    }

    @Test
    fun testOpenUpload() {
        val frame = PisteFrame.OpenUpload(42u)
        val decoded = roundTrip(frame)
        assertEquals(frame, decoded)
    }

    @Test
    fun testOpenStream() {
        val frame = PisteFrame.OpenStream(99u)
        val decoded = roundTrip(frame)
        assertEquals(frame, decoded)
    }

    @Test
    fun testOpen() {
        val frame = PisteFrame.Open
        val decoded = roundTrip(frame)
        assertEquals(frame, decoded)
    }

    @Test
    fun testClose() {
        val frame = PisteFrame.Close
        val decoded = roundTrip(frame)
        assertEquals(frame, decoded)
    }

    @Test
    fun testPayload() {
        val frame = PisteFrame.Payload("hello".encodeToByteArray())
        val decoded = roundTrip(frame)
        assertIs<PisteFrame.Payload>(decoded)
        assertTrue(frame.payload.contentEquals(decoded.payload))
    }

    @Test
    fun testError() {
        val frame = PisteFrame.Error(PisteError.InternalServerError)
        val decoded = roundTrip(frame)
        assertEquals(frame, decoded)
    }

    @Test
    fun testSupportedServicesRequest() {
        val frame = PisteFrame.SupportedServicesRequest
        val decoded = roundTrip(frame)
        assertEquals(frame, decoded)
    }

    @Test
    fun testSupportedServicesResponse() {
        val services = listOf(
            PisteSupportedService(1u, PisteServiceType.CALL),
            PisteSupportedService(2u, PisteServiceType.STREAM)
        )
        val frame = PisteFrame.SupportedServicesResponse(services)
        val decoded = roundTrip(frame)
        assertIs<PisteFrame.SupportedServicesResponse>(decoded)
        assertEquals(services, decoded.services)
    }
}
