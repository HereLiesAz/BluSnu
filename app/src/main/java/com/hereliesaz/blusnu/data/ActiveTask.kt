package com.hereliesaz.blusnu.data

/**
 * Data class representing a currently active background task within the application.
 *
 * This class is used to track and display the status of long-running operations
 * such as scanning, attacking, or file processing in the UI.
 *
 * @property id A unique identifier for the task, used for tracking and cancellation.
 * @property name The human-readable name of the task (e.g., "Bluetooth Scan").
 * @property description A brief description of what the task is currently doing (e.g., "Scanning for BLE devices...").
 */
data class ActiveTask(
    // The unique ID allows the task manager to reference specific tasks for updates or removal.
    val id: String,
    // The name provides a high-level label for the user interface.
    val name: String,
    // The description gives context-aware details about the task's progress.
    val description: String
)
