package com.hereliesaz.blusnu.ui.bluesmack

import com.hereliesaz.blusnu.data.BlueSmackModule
import com.hereliesaz.blusnu.utils.MacValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for BlueSmack parameter validation: packet size bounds, count bounds,
 * interface name validation, and MAC address validation.
 */
class BlueSmackValidationTest {

    // --- Packet size bounds ---

    @Test
    fun `packet size at lower bound is valid`() {
        val (size, _) = BlueSmackModule.clampParameters(1, 100)
        assertEquals(1, size)
    }

    @Test
    fun `packet size at upper bound is valid`() {
        val (size, _) = BlueSmackModule.clampParameters(65535, 100)
        assertEquals(65535, size)
    }

    @Test
    fun `packet size below lower bound is clamped to 1`() {
        val (size, _) = BlueSmackModule.clampParameters(0, 100)
        assertEquals(1, size)
    }

    @Test
    fun `negative packet size is clamped to 1`() {
        val (size, _) = BlueSmackModule.clampParameters(-100, 100)
        assertEquals(1, size)
    }

    @Test
    fun `packet size above upper bound is clamped to 65535`() {
        val (size, _) = BlueSmackModule.clampParameters(70000, 100)
        assertEquals(65535, size)
    }

    @Test
    fun `typical packet size passes through unchanged`() {
        val (size, _) = BlueSmackModule.clampParameters(600, 100)
        assertEquals(600, size)
    }

    // --- Packet count bounds ---

    @Test
    fun `count at lower bound is valid`() {
        val (_, count) = BlueSmackModule.clampParameters(600, 1)
        assertEquals(1, count)
    }

    @Test
    fun `count at upper bound is valid`() {
        val (_, count) = BlueSmackModule.clampParameters(600, 100_000)
        assertEquals(100_000, count)
    }

    @Test
    fun `count below lower bound is clamped to 1`() {
        val (_, count) = BlueSmackModule.clampParameters(600, 0)
        assertEquals(1, count)
    }

    @Test
    fun `negative count is clamped to 1`() {
        val (_, count) = BlueSmackModule.clampParameters(600, -50)
        assertEquals(1, count)
    }

    @Test
    fun `count above upper bound is clamped to 100000`() {
        val (_, count) = BlueSmackModule.clampParameters(600, 200_000)
        assertEquals(100_000, count)
    }

    @Test
    fun `typical count passes through unchanged`() {
        val (_, count) = BlueSmackModule.clampParameters(600, 1000)
        assertEquals(1000, count)
    }

    // --- Interface name validation ---

    @Test
    fun `hci0 is a valid interface name`() {
        BlueSmackModule.requireValidInterface("hci0")
    }

    @Test
    fun `hci1 is a valid interface name`() {
        BlueSmackModule.requireValidInterface("hci1")
    }

    @Test
    fun `wlan0 is a valid interface name`() {
        BlueSmackModule.requireValidInterface("wlan0")
    }

    @Test
    fun `alphanumeric string is a valid interface name`() {
        BlueSmackModule.requireValidInterface("abc123XYZ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty string is rejected`() {
        BlueSmackModule.requireValidInterface("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `interface name with spaces is rejected`() {
        BlueSmackModule.requireValidInterface("hci 0")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `interface name with semicolon is rejected`() {
        BlueSmackModule.requireValidInterface("hci0;rm -rf /")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `interface name with backtick is rejected`() {
        BlueSmackModule.requireValidInterface("hci0`whoami`")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `interface name with dollar sign is rejected`() {
        BlueSmackModule.requireValidInterface("hci\$0")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `interface name with pipe is rejected`() {
        BlueSmackModule.requireValidInterface("hci0|cat /etc/passwd")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `interface name with slash is rejected`() {
        BlueSmackModule.requireValidInterface("hci0/../../etc")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `interface name with dash is rejected`() {
        BlueSmackModule.requireValidInterface("hci-0")
    }

    // --- MAC address validation ---

    @Test
    fun `valid MAC address is accepted`() {
        assertTrue(MacValidator.isValid("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `lowercase MAC address is accepted`() {
        assertTrue(MacValidator.isValid("aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun `mixed case MAC address is accepted`() {
        assertTrue(MacValidator.isValid("Aa:Bb:Cc:Dd:Ee:Ff"))
    }

    @Test
    fun `empty string is invalid MAC`() {
        assertFalse(MacValidator.isValid(""))
    }

    @Test
    fun `MAC without colons is invalid`() {
        assertFalse(MacValidator.isValid("AABBCCDDEEFF"))
    }

    @Test
    fun `MAC with semicolons is invalid`() {
        assertFalse(MacValidator.isValid("AA;BB;CC;DD;EE;FF"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `requireValid throws on invalid MAC`() {
        MacValidator.requireValid("not-a-mac")
    }

    @Test
    fun `requireValid returns valid MAC`() {
        val result = MacValidator.requireValid("AA:BB:CC:DD:EE:FF")
        assertEquals("AA:BB:CC:DD:EE:FF", result)
    }
}
