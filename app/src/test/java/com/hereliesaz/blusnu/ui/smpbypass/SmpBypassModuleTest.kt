package com.hereliesaz.blusnu.ui.smpbypass

import android.bluetooth.BluetoothDevice
import com.hereliesaz.blusnu.data.PairingMethod
import com.hereliesaz.blusnu.data.SmpBypassModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SmpBypassModule] pairing-method classification and
 * address-type detection logic.
 */
class SmpBypassModuleTest {

    private lateinit var module: SmpBypassModule

    @Before
    fun setup() {
        module = SmpBypassModule()
    }

    // --- Address type detection (5.4, 5.11) ---

    @Test
    fun `public address detected for 00 prefix`() {
        // 00:11:22:33:44:55 -- first octet 0x00, MSBs = 00 -> public
        val type = module.detectAddressType("00:11:22:33:44:55")
        assertEquals(SmpBypassModule.ADDRESS_TYPE_PUBLIC, type)
    }

    @Test
    fun `public address detected for low first octet`() {
        // 3F = 0011 1111, MSBs = 00 -> public
        val type = module.detectAddressType("3F:22:33:44:55:66")
        assertEquals(SmpBypassModule.ADDRESS_TYPE_PUBLIC, type)
    }

    @Test
    fun `random address detected for static random C0 prefix`() {
        // C0 = 1100 0000, MSBs = 11 -> static random
        val type = module.detectAddressType("C0:11:22:33:44:55")
        assertEquals(SmpBypassModule.ADDRESS_TYPE_RANDOM, type)
    }

    @Test
    fun `random address detected for resolvable private 40 prefix`() {
        // 40 = 0100 0000, MSBs = 01 -> resolvable private
        val type = module.detectAddressType("40:11:22:33:44:55")
        assertEquals(SmpBypassModule.ADDRESS_TYPE_RANDOM, type)
    }

    @Test
    fun `random address detected for FF prefix`() {
        // FF = 1111 1111, MSBs = 11 -> random
        val type = module.detectAddressType("FF:FF:FF:FF:FF:FF")
        assertEquals(SmpBypassModule.ADDRESS_TYPE_RANDOM, type)
    }

    @Test
    fun `random address detected for 80 prefix`() {
        // 80 = 1000 0000, MSBs = 10 -> random
        val type = module.detectAddressType("80:00:00:00:00:00")
        assertEquals(SmpBypassModule.ADDRESS_TYPE_RANDOM, type)
    }

    @Test
    fun `invalid mac falls back to public`() {
        val type = module.detectAddressType("not:a:mac")
        assertEquals(SmpBypassModule.ADDRESS_TYPE_PUBLIC, type)
    }

    @Test
    fun `empty string falls back to public`() {
        val type = module.detectAddressType("")
        assertEquals(SmpBypassModule.ADDRESS_TYPE_PUBLIC, type)
    }

    // --- Pairing method classification (5.1, 5.2, 5.3, 5.11) ---

    @Test
    fun `just works detected when bonded without stronger method`() {
        val result = module.classifyPairingMethod(
            btmgmtOutput = "Paired",
            bondState = BluetoothDevice.BOND_BONDED
        )
        assertEquals(PairingMethod.JUST_WORKS, result.method)
        assertTrue(result.vulnerable)
        assertTrue(result.bonded)
    }

    @Test
    fun `passkey entry detected from output`() {
        val result = module.classifyPairingMethod(
            btmgmtOutput = "Passkey required for pairing",
            bondState = BluetoothDevice.BOND_BONDED
        )
        assertEquals(PairingMethod.PASSKEY_ENTRY, result.method)
        assertFalse(result.vulnerable)
    }

    @Test
    fun `numeric comparison detected from output`() {
        val result = module.classifyPairingMethod(
            btmgmtOutput = "Numeric Comparison: 123456",
            bondState = BluetoothDevice.BOND_BONDED
        )
        assertEquals(PairingMethod.NUMERIC_COMPARISON, result.method)
        assertFalse(result.vulnerable)
    }

    @Test
    fun `OOB detected from output`() {
        val result = module.classifyPairingMethod(
            btmgmtOutput = "OOB data required",
            bondState = BluetoothDevice.BOND_NONE
        )
        assertEquals(PairingMethod.OOB, result.method)
        assertFalse(result.vulnerable)
    }

    @Test
    fun `rejected pairing detected`() {
        val result = module.classifyPairingMethod(
            btmgmtOutput = "Pairing rejected by remote device",
            bondState = BluetoothDevice.BOND_NONE
        )
        assertEquals(PairingMethod.REJECTED, result.method)
        assertFalse(result.vulnerable)
        assertFalse(result.bonded)
    }

    @Test
    fun `timeout detected`() {
        val result = module.classifyPairingMethod(
            btmgmtOutput = "Connection timed out",
            bondState = BluetoothDevice.BOND_NONE
        )
        assertEquals(PairingMethod.UNKNOWN, result.method)
        assertFalse(result.vulnerable)
        assertFalse(result.bonded)
    }

    @Test
    fun `already paired detected`() {
        val result = module.classifyPairingMethod(
            btmgmtOutput = "Already paired with device",
            bondState = BluetoothDevice.BOND_BONDED
        )
        assertEquals(PairingMethod.UNKNOWN, result.method)
        assertFalse(result.vulnerable)
        assertTrue(result.bonded)
    }

    @Test
    fun `error output detected`() {
        val result = module.classifyPairingMethod(
            btmgmtOutput = "Error (exit code 1): permission denied",
            bondState = BluetoothDevice.BOND_NONE
        )
        assertEquals(PairingMethod.UNKNOWN, result.method)
        assertFalse(result.vulnerable)
    }

    @Test
    fun `not vulnerable when not bonded even if output contains Paired as substring`() {
        // 5.3: Error text containing "Paired" should NOT trigger false positive
        // when device.bondState is not BOND_BONDED.
        val result = module.classifyPairingMethod(
            btmgmtOutput = "Error: Paired device disconnected unexpectedly",
            bondState = BluetoothDevice.BOND_NONE
        )
        // Since bondState is NONE, it should NOT be classified as Just Works vulnerable.
        assertFalse(result.vulnerable)
        assertFalse(result.bonded)
    }

    @Test
    fun `confirm keyword triggers numeric comparison`() {
        val result = module.classifyPairingMethod(
            btmgmtOutput = "Confirm passkey 654321",
            bondState = BluetoothDevice.BOND_BONDED
        )
        assertEquals(PairingMethod.NUMERIC_COMPARISON, result.method)
        assertFalse(result.vulnerable)
    }

    @Test
    fun `PIN keyword triggers passkey entry`() {
        val result = module.classifyPairingMethod(
            btmgmtOutput = "Enter PIN code",
            bondState = BluetoothDevice.BOND_BONDED
        )
        assertEquals(PairingMethod.PASSKEY_ENTRY, result.method)
        assertFalse(result.vulnerable)
    }

    @Test
    fun `denied keyword triggers rejected`() {
        val result = module.classifyPairingMethod(
            btmgmtOutput = "Access denied",
            bondState = BluetoothDevice.BOND_NONE
        )
        assertEquals(PairingMethod.REJECTED, result.method)
        assertFalse(result.vulnerable)
    }

    @Test
    fun `inconclusive result when output is empty and not bonded`() {
        val result = module.classifyPairingMethod(
            btmgmtOutput = "",
            bondState = BluetoothDevice.BOND_NONE
        )
        assertEquals(PairingMethod.UNKNOWN, result.method)
        assertFalse(result.vulnerable)
        assertFalse(result.bonded)
    }
}
