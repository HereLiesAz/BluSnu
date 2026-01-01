package com.hereliesaz.blusnu.data

import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingState
import com.hereliesaz.blusnu.ui.attackchaining.nodes.AttackNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.IfElseNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.NodeConnector
import com.hereliesaz.blusnu.ui.attackchaining.nodes.StartNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.WaitNode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttackChainExecutorTest {

    @Test
    fun `executor executes sequential nodes`() = runTest {
        val startNode = StartNode(id = "start")
        val waitNode = WaitNode(id = "wait")
        val state = AttackChainingState(
            nodes = mapOf("start" to startNode, "wait" to waitNode),
            connections = listOf(
                NodeConnector("out", "start") to NodeConnector("in", "wait")
            )
        )

        val executor = AttackChainExecutor()
        val results = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            executor.output.toList(results)
        }

        // execute now takes a CoroutineScope, we pass `this` which is TestScope
        executor.execute(state, this)

        // Advance time for delays in nodes
        advanceTimeBy(2000)

        // Verify start executed
        assertTrue(results.any { it.contains("Executing node: Start") })
        // Verify wait executed
        assertTrue(results.any { it.contains("Executing node: Wait") })

        job.cancel()
    }
}
