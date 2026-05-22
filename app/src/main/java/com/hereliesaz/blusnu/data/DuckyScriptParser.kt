package com.hereliesaz.blusnu.data

import kotlinx.coroutines.delay

/**
 * Utility class for parsing and executing DuckyScript.
 *
 * DuckyScript is the scripting language used by USB Rubber Ducky and similar
 * Keystroke Injection tools (like Flipper Zero or the BlueDucky attack).
 * This parser converts a text-based script into a sequence of actionable commands.
 */
class DuckyScriptParser {

    /**
     * Data class representing a parsed command.
     * @property type The type of action (e.g., type a string, wait, press enter).
     * @property args Any arguments associated with the command (e.g., the text to type, delay duration).
     */
    data class Command(val type: CommandType, val args: String = "")

    /**
     * Enum of supported DuckyScript commands.
     */
    enum class CommandType {
        STRING,     // Type out the following text.
        DELAY,      // Pause execution for N milliseconds.
        ENTER,      // Press the Enter key.
        GUI,        // Press the GUI (Windows/Command) key.
        UNKNOWN     // Unrecognized command.
    }

    /**
     * Parses a raw DuckyScript string into a list of Command objects.
     *
     * @param script The raw script content.
     * @return A list of parsed commands.
     */
    fun parse(script: String): List<Command> {
        val commands = mutableListOf<Command>()

        // Process line by line.
        script.lines().forEach { line ->
            val trimmed = line.trim()

            // Skip empty lines or comments (REM).
            if (trimmed.isEmpty() || trimmed.startsWith("REM")) return@forEach

            // Split command from arguments (limit 2 parts).
            val parts = trimmed.split(" ", limit = 2)
            val command = parts[0].uppercase()
            val args = if (parts.size > 1) parts[1] else ""

            when (command) {
                "STRING" -> commands.add(Command(CommandType.STRING, args))
                "DELAY" -> commands.add(Command(CommandType.DELAY, args))
                "ENTER" -> commands.add(Command(CommandType.ENTER))
                // Supports both GUI and WINDOWS keywords.
                "GUI", "WINDOWS" -> commands.add(Command(CommandType.GUI, args))
                else -> commands.add(Command(CommandType.UNKNOWN, line))
            }
        }
        return commands
    }

    /**
     * Executes a script using a provided executor callback.
     *
     * @param script The raw script to run.
     * @param executor A suspending function that performs the actual HID injection for a given command.
     */
    suspend fun execute(script: String, executor: suspend (Command) -> Unit) {
        val commands = parse(script)

        commands.forEach { cmd ->
            // Handle DELAY commands internally to manage timing.
            if (cmd.type == CommandType.DELAY) {
                // Default to 100ms if parsing fails.
                val delayTime = cmd.args.toLongOrNull() ?: 100
                delay(delayTime)
            } else {
                // Delegate the actual keystroke injection to the caller (e.g., KeystrokeInjectionModule).
                executor(cmd)
            }
        }
    }
}
