package com.hereliesaz.blusnu.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Singleton manager for tracking currently active background tasks across the app.
 *
 * Similar in pattern to [ActionLogger], this object provides a centralized registry
 * of running tasks (scans, attacks, file transfers, etc.) exposed as a [StateFlow].
 * The Dashboard observes this flow to display active task cards in real-time.
 *
 * Attack ViewModels should call [add] when a task starts and [remove] when it
 * completes or is cancelled.
 */
object ActiveTaskManager {

    private val _tasks = MutableStateFlow<List<ActiveTask>>(emptyList())

    /** Observable list of currently active tasks. */
    val tasks: StateFlow<List<ActiveTask>> = _tasks.asStateFlow()

    /**
     * Registers a new active task.
     *
     * @param task The [ActiveTask] to add. Its [ActiveTask.id] should be unique
     *             among currently active tasks.
     */
    fun add(task: ActiveTask) {
        _tasks.update { current -> current + task }
    }

    /**
     * Convenience overload that constructs an [ActiveTask] inline.
     *
     * @param id Unique identifier for the task.
     * @param name Human-readable label (e.g. "BLUFFS Attack").
     * @param description Short status text (e.g. "Running against AA:BB:CC:DD:EE:FF").
     */
    fun add(id: String, name: String, description: String = "") {
        add(ActiveTask(id = id, name = name, description = description))
    }

    /**
     * Removes a task by its [id]. No-op if no task with that id exists.
     */
    fun remove(id: String) {
        _tasks.update { current -> current.filterNot { it.id == id } }
    }

    /**
     * Returns the current snapshot of active tasks.
     */
    fun getTasks(): List<ActiveTask> = _tasks.value

    /**
     * Clears all active tasks. Useful on app reset or when the user logs out.
     */
    fun clear() {
        _tasks.value = emptyList()
    }
}
