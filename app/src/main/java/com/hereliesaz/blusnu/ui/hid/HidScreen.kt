package com.hereliesaz.blusnu.ui.hid

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hereliesaz.blusnu.data.HidConnectionState
import com.hereliesaz.blusnu.data.HidKeyMap

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HidScreen(viewModel: HidViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("HID Controller", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Shows paired Bluetooth devices. Pair via system Settings first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Mode selector
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.mode == HidMode.BLE,
                onClick = { viewModel.setMode(HidMode.BLE) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                enabled = state.bleSupported
            ) { Text("BLE HID") }
            SegmentedButton(
                selected = state.mode == HidMode.CLASSIC,
                onClick = { viewModel.setMode(HidMode.CLASSIC) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                enabled = state.classicSupported
            ) { Text("Classic HID") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Connection section
        ConnectionSection(state = state, viewModel = viewModel)

        Spacer(modifier = Modifier.height(8.dp))

        // Tab selector
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.tab == HidTab.KEYBOARD,
                onClick = { viewModel.setTab(HidTab.KEYBOARD) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Default.Keyboard, contentDescription = null) }
            ) { Text("Keyboard") }
            SegmentedButton(
                selected = state.tab == HidTab.TOUCHPAD,
                onClick = { viewModel.setTab(HidTab.TOUCHPAD) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Default.Mouse, contentDescription = null) }
            ) { Text("Touchpad") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val isConnected = state.connectionState == HidConnectionState.CONNECTED

        when (state.tab) {
            HidTab.KEYBOARD -> KeyboardTab(viewModel = viewModel, enabled = isConnected)
            HidTab.TOUCHPAD -> TouchpadTab(viewModel = viewModel, enabled = isConnected)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status log
        StatusLog(messages = state.statusMessages, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
private fun ConnectionSection(state: HidUiState, viewModel: HidViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Device picker
            var expanded by remember { mutableStateOf(false) }
            val selectedName = state.selectedDevice?.name ?: state.selectedDevice?.address ?: "Select device"

            Row(verticalAlignment = Alignment.CenterVertically) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Device") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        leadingIcon = {
                            Icon(
                                if (state.connectionState == HidConnectionState.CONNECTED)
                                    Icons.Default.BluetoothConnected
                                else Icons.Default.Bluetooth,
                                contentDescription = null
                            )
                        }
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        state.pairedDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.name ?: device.address) },
                                onClick = {
                                    viewModel.selectDevice(device)
                                    expanded = false
                                }
                            )
                        }
                        if (state.pairedDevices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No paired devices") },
                                onClick = { expanded = false },
                                enabled = false
                            )
                        }
                    }
                }
                IconButton(onClick = { viewModel.refreshPairedDevices() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Connection status & buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val connectionText = when (state.connectionState) {
                    HidConnectionState.DISCONNECTED -> "Disconnected"
                    HidConnectionState.REGISTERING -> "Registering..."
                    HidConnectionState.REGISTERED -> "Ready"
                    HidConnectionState.CONNECTING -> "Connecting..."
                    HidConnectionState.CONNECTED -> "Connected"
                    HidConnectionState.ERROR -> "Error"
                }

                Text(
                    text = connectionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (state.connectionState) {
                        HidConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                        HidConnectionState.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .weight(1f)
                )

                when (state.connectionState) {
                    HidConnectionState.DISCONNECTED, HidConnectionState.ERROR -> {
                        Button(onClick = { viewModel.initialize() }) {
                            Text("Initialize")
                        }
                    }
                    HidConnectionState.REGISTERED -> {
                        Button(
                            onClick = { viewModel.connect() },
                            enabled = state.selectedDevice != null
                        ) {
                            Text(if (state.mode == HidMode.BLE) "Advertise" else "Connect")
                        }
                    }
                    HidConnectionState.CONNECTED -> {
                        OutlinedButton(
                            onClick = { viewModel.disconnect() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Disconnect")
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun KeyboardTab(viewModel: HidViewModel, enabled: Boolean) {
    var text by remember { mutableStateOf("") }

    Column {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Type text to send") },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (text.isNotBlank()) {
                        viewModel.typeText(text)
                        text = ""
                    }
                }
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (text.isNotBlank()) {
                    viewModel.typeText(text)
                    text = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && text.isNotBlank()
        ) {
            Text("Send Text")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Special keys row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SpecialKeyButton("Enter", HidKeyMap.KEY_ENTER, enabled, viewModel, Modifier.weight(1f))
            SpecialKeyButton("Bksp", HidKeyMap.KEY_BACKSPACE, enabled, viewModel, Modifier.weight(1f))
            SpecialKeyButton("Tab", HidKeyMap.KEY_TAB, enabled, viewModel, Modifier.weight(1f))
            SpecialKeyButton("Esc", HidKeyMap.KEY_ESCAPE, enabled, viewModel, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SpecialKeyButton("Up", HidKeyMap.KEY_UP_ARROW, enabled, viewModel, Modifier.weight(1f))
            SpecialKeyButton("Down", HidKeyMap.KEY_DOWN_ARROW, enabled, viewModel, Modifier.weight(1f))
            SpecialKeyButton("Left", HidKeyMap.KEY_LEFT_ARROW, enabled, viewModel, Modifier.weight(1f))
            SpecialKeyButton("Right", HidKeyMap.KEY_RIGHT_ARROW, enabled, viewModel, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Modifier combos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(
                onClick = {
                    viewModel.sendSpecialKey(
                        HidKeyMap.charToHid('c')!!.keyCode,
                        HidKeyMap.MOD_LEFT_CTRL
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = enabled
            ) { Text("Ctrl+C", style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(
                onClick = {
                    viewModel.sendSpecialKey(
                        HidKeyMap.charToHid('v')!!.keyCode,
                        HidKeyMap.MOD_LEFT_CTRL
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = enabled
            ) { Text("Ctrl+V", style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(
                onClick = {
                    viewModel.sendSpecialKey(
                        HidKeyMap.charToHid('z')!!.keyCode,
                        HidKeyMap.MOD_LEFT_CTRL
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = enabled
            ) { Text("Ctrl+Z", style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(
                onClick = {
                    viewModel.sendSpecialKey(
                        HidKeyMap.charToHid('a')!!.keyCode,
                        HidKeyMap.MOD_LEFT_CTRL
                    )
                },
                modifier = Modifier.weight(1f),
                enabled = enabled
            ) { Text("Ctrl+A", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun SpecialKeyButton(
    label: String,
    keyCode: Byte,
    enabled: Boolean,
    viewModel: HidViewModel,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = { viewModel.sendSpecialKey(keyCode) },
        modifier = modifier,
        enabled = enabled
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TouchpadTab(viewModel: HidViewModel, enabled: Boolean) {
    Column {
        // Touchpad area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (enabled) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
                .then(
                    if (enabled) {
                        Modifier
                            .pointerInput(Unit) {
                                detectDragGestures { _, dragAmount ->
                                    viewModel.sendMouseMove(
                                        dragAmount.x.toInt(),
                                        dragAmount.y.toInt()
                                    )
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { viewModel.sendMouseClick(HidKeyMap.MOUSE_BUTTON_LEFT) },
                                    onDoubleTap = {
                                        viewModel.sendMouseClick(HidKeyMap.MOUSE_BUTTON_LEFT)
                                        viewModel.sendMouseClick(HidKeyMap.MOUSE_BUTTON_LEFT)
                                    },
                                    onLongPress = { viewModel.sendMouseClick(HidKeyMap.MOUSE_BUTTON_RIGHT) }
                                )
                            }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (enabled) "Drag to move cursor\nTap = Left click\nLong press = Right click"
                else "Connect to enable touchpad",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mouse buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.sendMouseClick(HidKeyMap.MOUSE_BUTTON_LEFT) },
                modifier = Modifier.weight(1f),
                enabled = enabled
            ) { Text("Left Click") }
            Button(
                onClick = { viewModel.sendMouseClick(HidKeyMap.MOUSE_BUTTON_MIDDLE) },
                modifier = Modifier.weight(1f),
                enabled = enabled
            ) { Text("Middle") }
            Button(
                onClick = { viewModel.sendMouseClick(HidKeyMap.MOUSE_BUTTON_RIGHT) },
                modifier = Modifier.weight(1f),
                enabled = enabled
            ) { Text("Right Click") }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Scroll buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.sendMouseScroll(5) },
                modifier = Modifier.weight(1f),
                enabled = enabled
            ) { Text("Scroll Up") }
            OutlinedButton(
                onClick = { viewModel.sendMouseScroll(-5) },
                modifier = Modifier.weight(1f),
                enabled = enabled
            ) { Text("Scroll Down") }
        }
    }
}

@Composable
private fun StatusLog(messages: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("Log", style = MaterialTheme.typography.labelMedium)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(messages) { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
