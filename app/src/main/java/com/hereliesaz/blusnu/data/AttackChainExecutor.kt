package com.hereliesaz.blusnu.data

import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingState
import com.hereliesaz.blusnu.ui.attackchaining.nodes.AttackNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.StartNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * The runtime engine responsible for executing an Attack Chain.
 *
 * This class takes a static representation of a workflow ([AttackChainingState]),
 * finds the starting point, and recursively executes connected nodes.
 * It manages the flow of execution and data context between steps.
 */
class AttackChainExecutor {

    // A shared flow to emit real-time execution logs/results to the UI.
    private val _output = MutableSharedFlow<String>()
    val output = _output.asSharedFlow()

    /**
     * Starts the execution of the provided attack chain.
     *
     * @param state The current state of the canvas (nodes and connections).
     * @param scope The CoroutineScope in which to run the execution (usually ViewModel scope).
     */
    fun execute(state: AttackChainingState, scope: CoroutineScope) {
        scope.launch {
            // Find the unique StartNode to begin execution.
            // Assumption: There is exactly one StartNode in valid chains.
            val startNode = state.nodes.values.find { it is StartNode }

            if (startNode != null) {
                // Begin recursive execution from the start node.
                executeNode(startNode, state)
            } else {
                _output.emit("No start node found")
            }
        }
    }

    /**
     * Recursively executes a node and its successors.
     *
     * @param node The node to execute.
     * @param state The global state (to look up connections).
     * @param context The execution context passed from the previous node (containing results, target device, etc.).
     */
    private suspend fun executeNode(node: AttackNode, state: AttackChainingState, context: com.hereliesaz.blusnu.ui.attackchaining.nodes.ExecutionContext = com.hereliesaz.blusnu.ui.attackchaining.nodes.ExecutionContext()) {
        // Log start of node execution.
        _output.emit("Executing node: ${node.name}")

        // Run the node's specific logic (e.g., Scan, Connect, Attack).
        // This is a suspend function that returns an ExecutionResult.
        val result = node.execute(context)

        // Log the output/result of the node.
        _output.emit("Result: ${result.output}")

        // Determine which nodes are connected to this node's output ports.
        // We filter the list of connections to find those originating from this node.
        val connections = state.connections.filter { it.first.nodeId == node.id }

        // Iterate through all connected downstream nodes.
        for (connection in connections) {
            // Retrieve the actual Node object using the ID from the connection destination.
            val nextNode = state.nodes[connection.second.nodeId]

            if (nextNode != null) {
                // Create a new context for the next node.
                // It inherits state from the current result (e.g., if this node found a device, pass it on).
                val nextContext = context.copy(
                    lastResult = result.output,
                    // If the current node produced a target device, pass it. Otherwise, keep the existing one.
                    targetDevice = result.targetDevice ?: context.targetDevice
                )

                // Recursively execute the next node.
                // Note: This simple implementation executes branches sequentially (Depth-First).
                executeNode(nextNode, state, nextContext)
            }
        }
    }
}
