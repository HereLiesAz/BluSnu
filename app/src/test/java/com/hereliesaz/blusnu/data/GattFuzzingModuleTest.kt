package com.hereliesaz.blusnu.data

import android.bluetooth.BluetoothGattCharacteristic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class GattFuzzingModuleTest {

    private lateinit var module: GattFuzzingModule

    @Before
    fun setup() {
        module = GattFuzzingModule()
    }

    // --- formatUuid tests (Finding 11.5 / 11.8) ---

    @Test
    fun `formatUuid shortens 16-bit standard BT UUID`() {
        // Heart Rate Service: 0000180d-0000-1000-8000-00805f9b34fb
        val uuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        assertEquals("0x180D", module.formatUuid(uuid))
    }

    @Test
    fun `formatUuid shortens 32-bit standard BT UUID`() {
        // A 32-bit UUID: 12345678-0000-1000-8000-00805f9b34fb
        val uuid = UUID.fromString("12345678-0000-1000-8000-00805f9b34fb")
        assertEquals("0x12345678", module.formatUuid(uuid))
    }

    @Test
    fun `formatUuid returns full string for non-standard UUID`() {
        val uuid = UUID.fromString("12345678-abcd-efab-cdef-123456789abc")
        assertEquals("12345678-abcd-efab-cdef-123456789abc", module.formatUuid(uuid))
    }

    @Test
    fun `formatUuid handles Generic Access 16-bit UUID`() {
        // Generic Access: 0x1800
        val uuid = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        assertEquals("0x1800", module.formatUuid(uuid))
    }

    @Test
    fun `formatUuid handles 32-bit UUID with leading zeros in upper half`() {
        // 00011234-0000-1000-8000-00805f9b34fb -- not 16-bit (upper half is 0001, not 0000)
        val uuid = UUID.fromString("00011234-0000-1000-8000-00805f9b34fb")
        assertEquals("0x00011234", module.formatUuid(uuid))
    }

    // --- Payload generation tests (Finding 11.8) ---

    @Test
    fun `generateFuzzPayloads returns expected number of payloads`() {
        val payloads = module.generateFuzzPayloads()
        assertEquals(13, payloads.size)
    }

    @Test
    fun `generateFuzzPayloads includes empty payload`() {
        val payloads = module.generateFuzzPayloads()
        val empty = payloads.find { it.first == "empty" }
        assertTrue("Expected empty payload", empty != null)
        assertEquals(0, empty!!.second.size)
    }

    @Test
    fun `generateFuzzPayloads includes overflow payload over 512 bytes`() {
        val payloads = module.generateFuzzPayloads()
        val overflow = payloads.find { it.first == "overflow" }
        assertTrue("Expected overflow payload", overflow != null)
        assertTrue("Overflow payload should exceed 512 bytes", overflow!!.second.size > 512)
    }

    @Test
    fun `generateFuzzPayloads all payloads have unique names`() {
        val payloads = module.generateFuzzPayloads()
        val names = payloads.map { it.first }
        assertEquals("All payload names must be unique", names.size, names.toSet().size)
    }

    // --- Write type selection tests (Findings 11.2 / 11.8) ---

    @Test
    fun `selectWriteType prefers DEFAULT when both WRITE and WRITE_NO_RESPONSE supported`() {
        val props = BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        assertEquals(
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            module.selectWriteType(props)
        )
    }

    @Test
    fun `selectWriteType returns DEFAULT when only WRITE supported`() {
        val props = BluetoothGattCharacteristic.PROPERTY_WRITE
        assertEquals(
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            module.selectWriteType(props)
        )
    }

    @Test
    fun `selectWriteType returns NO_RESPONSE when only WRITE_NO_RESPONSE supported`() {
        val props = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        assertEquals(
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
            module.selectWriteType(props)
        )
    }

    @Test
    fun `selectWriteType prefers DEFAULT even with additional properties`() {
        val props = BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY
        assertEquals(
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            module.selectWriteType(props)
        )
    }
}
