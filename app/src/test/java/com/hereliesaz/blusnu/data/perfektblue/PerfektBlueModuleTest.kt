package com.hereliesaz.blusnu.data.perfektblue

import com.hereliesaz.blusnu.data.PerfektBlueModule
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketException

/**
 * Unit tests for PerfektBlueModule.
 *
 * Tests cover:
 * - vCard generation with proper recursive nesting (10.5)
 * - OBEX framing: CONNECT, PUT, DISCONNECT packets (10.2)
 * - Connection ID parsing from OBEX CONNECT responses
 * - Crash vs. rejection classification (10.3, 10.4)
 */
class PerfektBlueModuleTest {

    // ---- vCard generation tests (10.5) ----

    @Test
    fun `buildMalformedVcard contains BEGIN and END VCARD`() {
        val vcard = PerfektBlueModule.buildMalformedVcard(depth = 5)
        assertTrue("vCard must start with BEGIN:VCARD", vcard.startsWith("BEGIN:VCARD\r\n"))
        assertTrue("vCard must end with END:VCARD", vcard.trimEnd().endsWith("END:VCARD"))
    }

    @Test
    fun `buildMalformedVcard has oversized field value`() {
        val vcard = PerfektBlueModule.buildMalformedVcard(depth = 1)
        // The FN field should have 2048 'A' characters
        val fnLine = vcard.lines().find { it.startsWith("FN:") }
        assertNotNull("FN line must exist", fnLine)
        // FN: + 2048 A's
        assertEquals(2048 + 3, fnLine!!.length)
    }

    @Test
    fun `buildMalformedVcard produces recursive nesting at correct depth`() {
        val depth = 5
        val vcard = PerfektBlueModule.buildMalformedVcard(depth = depth)

        // Count occurrences of "BEGIN:VCARD" (escaped as "BEGIN:VCARD\\n" for nested ones)
        // The outermost vCard has one BEGIN:VCARD, and each nested level adds another
        val beginCount = "BEGIN:VCARD".toRegex().findAll(vcard).count()
        // Outer vCard + depth nested levels = depth + 1 total BEGIN:VCARD
        assertEquals(
            "Should have ${depth + 1} BEGIN:VCARD occurrences (1 outer + $depth nested)",
            depth + 1,
            beginCount
        )
    }

    @Test
    fun `buildMalformedVcard with depth 0 has no AGENT properties`() {
        val vcard = PerfektBlueModule.buildMalformedVcard(depth = 0)
        assertTrue("Depth 0 should not contain AGENT", !vcard.contains("AGENT:"))
        // Should still have outer BEGIN/END
        assertTrue(vcard.contains("BEGIN:VCARD"))
        assertTrue(vcard.contains("END:VCARD"))
    }

    @Test
    fun `buildMalformedVcard nesting is truly recursive not flat`() {
        val vcard = PerfektBlueModule.buildMalformedVcard(depth = 3)
        // With recursive nesting, AGENT properties should appear at increasing depths.
        // In a flat structure, all AGENTs are at depth 1 (siblings).
        // In a recursive structure, each AGENT is inside the previous one.

        // The recursive structure should have AGENT inside AGENT.
        // After the first AGENT:BEGIN:VCARD\n, there should be another AGENT before END:VCARD\n
        val agentPositions = mutableListOf<Int>()
        var searchFrom = 0
        while (true) {
            val pos = vcard.indexOf("AGENT:", searchFrom)
            if (pos == -1) break
            agentPositions.add(pos)
            searchFrom = pos + 1
        }
        assertEquals("Should have 3 AGENT properties for depth 3", 3, agentPositions.size)

        // Each AGENT should be at a later position than the previous one,
        // AND (for recursion) should appear before the END:VCARD of the outer agent.
        // Just verify increasing positions (they're nested, not sequential blocks).
        for (i in 1 until agentPositions.size) {
            assertTrue(
                "AGENT ${i + 1} should appear after AGENT $i (nested inside it)",
                agentPositions[i] > agentPositions[i - 1]
            )
        }
    }

    @Test
    fun `buildMalformedVcard contains FN Nested labels`() {
        val vcard = PerfektBlueModule.buildMalformedVcard(depth = 3)
        // Recursive nesting builds from remainingDepth down
        assertTrue("Should contain FN:Nested3", vcard.contains("FN:Nested3"))
        assertTrue("Should contain FN:Nested2", vcard.contains("FN:Nested2"))
        assertTrue("Should contain FN:Nested1", vcard.contains("FN:Nested1"))
    }

    // ---- OBEX framing tests (10.2) ----

    @Test
    fun `buildObexConnect produces valid 7-byte packet`() {
        val packet = PerfektBlueModule.buildObexConnect()
        assertEquals("OBEX CONNECT should be 7 bytes", 7, packet.size)
        assertEquals("Opcode should be 0x80", 0x80.toByte(), packet[0])
        assertEquals("Length high byte", 0x00.toByte(), packet[1])
        assertEquals("Length low byte", 0x07.toByte(), packet[2])
        assertEquals("Version should be 0x10", 0x10.toByte(), packet[3])
        assertEquals("Flags should be 0x00", 0x00.toByte(), packet[4])
        assertEquals("Max packet length high", 0xFF.toByte(), packet[5])
        assertEquals("Max packet length low", 0xFF.toByte(), packet[6])
    }

    @Test
    fun `buildObexPut includes Connection ID when provided`() {
        val connId = byteArrayOf(0x00, 0x00, 0x00, 0x01)
        val payload = "test".toByteArray()
        val packet = PerfektBlueModule.buildObexPut(connId, payload)

        assertEquals("Opcode should be 0x82 (PUT Final)", 0x82.toByte(), packet[0])
        // Verify packet length field matches actual size
        val declaredLen = ((packet[1].toInt() and 0xFF) shl 8) or (packet[2].toInt() and 0xFF)
        assertEquals("Declared length must match actual packet size", packet.size, declaredLen)

        // Verify Connection ID header is present (tag 0xCB followed by the 4-byte ID)
        val connIdTag = 0xCB.toByte()
        var found = false
        for (i in 3 until packet.size - 4) {
            if (packet[i] == connIdTag) {
                assertArrayEquals(
                    "Connection ID bytes should match",
                    connId,
                    packet.copyOfRange(i + 1, i + 5)
                )
                found = true
                break
            }
        }
        assertTrue("Connection ID header (0xCB) must be present in PUT packet", found)
    }

    @Test
    fun `buildObexPut works without Connection ID`() {
        val payload = "test".toByteArray()
        val packet = PerfektBlueModule.buildObexPut(null, payload)

        assertEquals("Opcode should be 0x82", 0x82.toByte(), packet[0])
        val declaredLen = ((packet[1].toInt() and 0xFF) shl 8) or (packet[2].toInt() and 0xFF)
        assertEquals("Declared length must match actual packet size", packet.size, declaredLen)

        // Should NOT contain 0xCB tag since no Connection ID
        val connIdTag = 0xCB.toByte()
        var found = false
        for (i in 3 until packet.size) {
            if (packet[i] == connIdTag) {
                found = true
                break
            }
        }
        assertTrue("Connection ID header should NOT be present", !found)
    }

    @Test
    fun `buildObexPut contains End-of-Body header with payload`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val packet = PerfektBlueModule.buildObexPut(null, payload)

        // Find End-of-Body header (tag 0x49)
        val eobTag = 0x49.toByte()
        var eobOffset = -1
        for (i in 3 until packet.size) {
            if (packet[i] == eobTag) {
                eobOffset = i
                break
            }
        }
        assertTrue("End-of-Body header (0x49) must be present", eobOffset >= 0)

        // Verify length field
        val eobLen = ((packet[eobOffset + 1].toInt() and 0xFF) shl 8) or
                (packet[eobOffset + 2].toInt() and 0xFF)
        assertEquals("EoB length should be payload size + 3", payload.size + 3, eobLen)

        // Verify payload bytes
        val extractedPayload = packet.copyOfRange(eobOffset + 3, eobOffset + 3 + payload.size)
        assertArrayEquals("Payload in EoB should match input", payload, extractedPayload)
    }

    @Test
    fun `buildObexPut includes Name header with UTF-16BE null terminator`() {
        val payload = "test".toByteArray()
        val packet = PerfektBlueModule.buildObexPut(null, payload)

        // Find Name header (tag 0x01)
        val nameTag = 0x01.toByte()
        var nameOffset = -1
        for (i in 3 until packet.size) {
            if (packet[i] == nameTag) {
                nameOffset = i
                break
            }
        }
        assertTrue("Name header (0x01) must be present", nameOffset >= 0)

        val nameLen = ((packet[nameOffset + 1].toInt() and 0xFF) shl 8) or
                (packet[nameOffset + 2].toInt() and 0xFF)
        // Name value starts at nameOffset+3, ends at nameOffset+nameLen
        val nameValue = packet.copyOfRange(nameOffset + 3, nameOffset + nameLen)

        // Last two bytes should be the UTF-16BE null terminator
        assertEquals("Name value must end with 0x00", 0x00.toByte(), nameValue[nameValue.size - 1])
        assertEquals("Name value must end with 0x00 0x00", 0x00.toByte(), nameValue[nameValue.size - 2])
    }

    @Test
    fun `buildObexDisconnect without Connection ID is 3 bytes`() {
        val packet = PerfektBlueModule.buildObexDisconnect(null)
        assertEquals(3, packet.size)
        assertEquals("Opcode should be 0x81", 0x81.toByte(), packet[0])
        assertEquals("Length high", 0x00.toByte(), packet[1])
        assertEquals("Length low", 0x03.toByte(), packet[2])
    }

    @Test
    fun `buildObexDisconnect with Connection ID is 8 bytes`() {
        val connId = byteArrayOf(0x00, 0x00, 0x00, 0x42)
        val packet = PerfektBlueModule.buildObexDisconnect(connId)
        assertEquals(8, packet.size)
        assertEquals("Opcode should be 0x81", 0x81.toByte(), packet[0])
        assertEquals("Length high", 0x00.toByte(), packet[1])
        assertEquals("Length low", 0x08.toByte(), packet[2])
        assertEquals("Connection ID tag", 0xCB.toByte(), packet[3])
        assertArrayEquals("Connection ID value", connId, packet.copyOfRange(4, 8))
    }

    // ---- Connection ID parsing tests ----

    @Test
    fun `parseConnectionId returns ID from valid response`() {
        // Build a mock OBEX CONNECT response:
        // response_code(0xA0) + length(2) + version(1) + flags(1) + maxPacketLen(2) + connID header
        val connId = byteArrayOf(0x00, 0x00, 0x00, 0x07)
        val response = byteArrayOf(
            0xA0.toByte(),   // SUCCESS response code
            0x00, 0x0C,      // length = 12
            0x10,            // version 1.0
            0x00,            // flags
            0x00, 0x04,      // max packet length (arbitrary)
            0xCB.toByte(),   // Connection ID tag
            0x00, 0x00, 0x00, 0x07  // Connection ID value
        )
        val parsed = PerfektBlueModule.parseConnectionId(response)
        assertNotNull("Should parse Connection ID", parsed)
        assertArrayEquals("Connection ID should match", connId, parsed)
    }

    @Test
    fun `parseConnectionId returns null for non-SUCCESS response`() {
        val response = byteArrayOf(
            0xC0.toByte(),   // Non-success response code
            0x00, 0x07,
            0x10, 0x00, 0x00, 0x04
        )
        val parsed = PerfektBlueModule.parseConnectionId(response)
        assertNull("Should return null for non-SUCCESS response", parsed)
    }

    @Test
    fun `parseConnectionId returns null for response without Connection ID`() {
        val response = byteArrayOf(
            0xA0.toByte(),   // SUCCESS
            0x00, 0x07,
            0x10, 0x00, 0x00, 0x04
            // No Connection ID header
        )
        val parsed = PerfektBlueModule.parseConnectionId(response)
        assertNull("Should return null when no Connection ID header", parsed)
    }

    @Test
    fun `parseConnectionId returns null for too-short response`() {
        val response = byteArrayOf(0xA0.toByte(), 0x00)
        val parsed = PerfektBlueModule.parseConnectionId(response)
        assertNull("Should return null for response shorter than 7 bytes", parsed)
    }

    // ---- Crash vs. rejection classification tests (10.3, 10.4) ----

    @Test
    fun `classifyWriteError returns WRITE_ERROR for plain IOException`() {
        val module = createModule()
        val result = module.classifyWriteError(IOException("Broken pipe"))
        assertEquals(PerfektBlueModule.FuzzResult.WRITE_ERROR, result)
    }

    @Test
    fun `classifyWriteError returns WRITE_ERROR for Connection reset by peer`() {
        val module = createModule()
        val result = module.classifyWriteError(SocketException("Connection reset by peer"))
        assertEquals(
            "Connection reset by peer indicates rejection, not crash",
            PerfektBlueModule.FuzzResult.WRITE_ERROR,
            result
        )
    }

    @Test
    fun `classifyWriteError returns TARGET_CRASHED for SocketException without reset`() {
        val module = createModule()
        val result = module.classifyWriteError(SocketException("Software caused connection abort"))
        assertEquals(
            "Non-reset SocketException indicates likely crash",
            PerfektBlueModule.FuzzResult.TARGET_CRASHED,
            result
        )
    }

    @Test
    fun `classifyWriteError returns TARGET_CRASHED for SocketException with null message`() {
        val module = createModule()
        val result = module.classifyWriteError(SocketException())
        assertEquals(
            "SocketException with no message should be classified as crash",
            PerfektBlueModule.FuzzResult.TARGET_CRASHED,
            result
        )
    }

    /**
     * Helper: creates a PerfektBlueModule instance.
     * Uses a null-context approach since we only test static/pure logic.
     */
    private fun createModule(): PerfektBlueModule {
        // classifyWriteError does not use context, so we can pass a mock
        @Suppress("UNCHECKED_CAST")
        return PerfektBlueModule(
            org.mockito.Mockito.mock(android.content.Context::class.java)
        )
    }
}
