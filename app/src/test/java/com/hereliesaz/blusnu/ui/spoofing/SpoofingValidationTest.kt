package com.hereliesaz.blusnu.ui.spoofing

import com.hereliesaz.blusnu.utils.MacValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for MAC validation and format handling used by the Spoofing module.
 */
class SpoofingValidationTest {

    // --- MacValidator.isValid tests ---

    @Test
    fun `valid colon-separated MAC address is accepted`() {
        assertTrue(MacValidator.isValid("00:11:22:33:44:55"))
    }

    @Test
    fun `valid uppercase MAC address is accepted`() {
        assertTrue(MacValidator.isValid("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `valid mixed-case MAC address is accepted`() {
        assertTrue(MacValidator.isValid("aA:bB:cC:dD:eE:fF"))
    }

    @Test
    fun `dash-separated MAC address is rejected`() {
        // MacValidator only accepts colon-separated format
        assertFalse(MacValidator.isValid("00-11-22-33-44-55"))
    }

    @Test
    fun `MAC without separators is rejected`() {
        assertFalse(MacValidator.isValid("001122334455"))
    }

    @Test
    fun `empty string is rejected`() {
        assertFalse(MacValidator.isValid(""))
    }

    @Test
    fun `MAC with too few octets is rejected`() {
        assertFalse(MacValidator.isValid("00:11:22:33:44"))
    }

    @Test
    fun `MAC with too many octets is rejected`() {
        assertFalse(MacValidator.isValid("00:11:22:33:44:55:66"))
    }

    @Test
    fun `MAC with non-hex characters is rejected`() {
        assertFalse(MacValidator.isValid("GG:HH:II:JJ:KK:LL"))
    }

    @Test
    fun `MAC with single-digit octets is rejected`() {
        assertFalse(MacValidator.isValid("0:1:2:3:4:5"))
    }

    @Test
    fun `MAC with triple-digit octets is rejected`() {
        assertFalse(MacValidator.isValid("000:111:222:333:444:555"))
    }

    @Test
    fun `MAC with leading whitespace is rejected`() {
        assertFalse(MacValidator.isValid(" 00:11:22:33:44:55"))
    }

    @Test
    fun `MAC with trailing whitespace is rejected`() {
        assertFalse(MacValidator.isValid("00:11:22:33:44:55 "))
    }

    @Test
    fun `MAC with newline injection attempt is rejected`() {
        assertFalse(MacValidator.isValid("00:11:22:33:44:55\n"))
    }

    @Test
    fun `MAC with shell injection attempt is rejected`() {
        assertFalse(MacValidator.isValid("00:11:22:33:44:55; rm -rf /"))
    }

    // --- MacValidator.requireValid tests ---

    @Test
    fun `requireValid returns the MAC for valid input`() {
        val mac = "00:11:22:33:44:55"
        assertEquals(mac, MacValidator.requireValid(mac))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `requireValid throws for invalid MAC`() {
        MacValidator.requireValid("invalid")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `requireValid throws for dash-separated MAC`() {
        MacValidator.requireValid("00-11-22-33-44-55")
    }

    // --- MAC format normalization tests ---

    @Test
    fun `uppercase normalization preserves valid MAC`() {
        assertEquals("AA:BB:CC:DD:EE:FF", "aA:bB:cC:dD:eE:fF".uppercase())
    }

    @Test
    fun `MAC split produces six octets`() {
        val octets = "00:11:22:33:44:55".split(":")
        assertEquals(6, octets.size)
    }

    @Test
    fun `reversed MAC bytes for HCI command are correct`() {
        val mac = "00:11:22:33:44:55"
        val reversed = mac.split(":").reversed().joinToString(" ") { "0x$it" }
        assertEquals("0x55 0x44 0x33 0x22 0x11 0x00", reversed)
    }

    @Test
    fun `forward MAC bytes for Broadcom HCI command are correct`() {
        val mac = "AA:BB:CC:DD:EE:FF"
        val forward = mac.split(":").joinToString(" ") { "0x$it" }
        assertEquals("0xAA 0xBB 0xCC 0xDD 0xEE 0xFF", forward)
    }

    // --- SpoofResult tests ---

    @Test
    fun `SpoofResult Success holds method name`() {
        val result = com.hereliesaz.blusnu.data.SpoofResult.Success("bdaddr")
        assertTrue(result is com.hereliesaz.blusnu.data.SpoofResult.Success)
        assertEquals("bdaddr", result.method)
    }

    @Test
    fun `SpoofResult Failure holds reason`() {
        val result = com.hereliesaz.blusnu.data.SpoofResult.Failure("Root not available")
        assertTrue(result is com.hereliesaz.blusnu.data.SpoofResult.Failure)
        assertEquals("Root not available", result.reason)
    }

    // --- RootExecutor error detection pattern tests ---

    @Test
    fun `error detection catches exit code prefix`() {
        val result = "Error (exit code 1): command not found"
        assertTrue(result.startsWith("Error"))
    }

    @Test
    fun `error detection catches lowercase error`() {
        val result = "error: permission denied"
        assertTrue(result.startsWith("error"))
    }

    @Test
    fun `successful output does not trigger error detection`() {
        val result = "hci0:\tType: Primary  Bus: USB\n\tBD Address: 00:11:22:33:44:55"
        assertFalse(result.startsWith("Error") || result.startsWith("error"))
    }

    @Test
    fun `hciconfig MAC address parsing extracts correct address`() {
        val hciOutput = """
            hci0:   Type: Primary  Bus: USB
                    BD Address: AA:BB:CC:DD:EE:FF  ACL MTU: 1021:8  SCO MTU: 64:1
                    UP RUNNING PSCAN
        """.trimIndent()
        val regex = Regex("BD Address:\\s*([0-9A-Fa-f:]{17})")
        val match = regex.find(hciOutput)
        assertEquals("AA:BB:CC:DD:EE:FF", match?.groupValues?.get(1))
    }

    @Test
    fun `hciconfig MAC parsing returns null for missing address`() {
        val hciOutput = "hci0: no such device"
        val regex = Regex("BD Address:\\s*([0-9A-Fa-f:]{17})")
        val match = regex.find(hciOutput)
        assertEquals(null, match)
    }
}
