package com.hereliesaz.blusnu.data

import kotlinx.coroutines.delay

class DuckyScriptParser {

    data class Command(val type: CommandType, val args: String = "")
    enum class CommandType { STRING, DELAY, ENTER, GUI, UNKNOWN }

    fun parse(script: String): List<Command> {
        val commands = mutableListOf<Command>()
        script.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("REM")) return@forEach

            val parts = trimmed.split(" ", limit = 2)
            val command = parts[0].uppercase()
            val args = if (parts.size > 1) parts[1] else ""

            when (command) {
                "STRING" -> commands.add(Command(CommandType.STRING, args))
                "DELAY" -> commands.add(Command(CommandType.DELAY, args))
                "ENTER" -> commands.add(Command(CommandType.ENTER))
                "GUI", "WINDOWS" -> commands.add(Command(CommandType.GUI, args))
                else -> commands.add(Command(CommandType.UNKNOWN, line))
            }
        }
        return commands
    }

    suspend fun execute(script: String, executor: suspend (Command) -> Unit) {
        val commands = parse(script)
        commands.forEach { cmd ->
            if (cmd.type == CommandType.DELAY) {
                val delayTime = cmd.args.toLongOrNull() ?: 100
                delay(delayTime)
            } else {
                executor(cmd)
            }
        }
    }
}
