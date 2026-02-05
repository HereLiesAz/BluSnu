package com.hereliesaz.blusnu.data

import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingState
import com.hereliesaz.blusnu.ui.attackchaining.nodes.BluesnarfNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.KeystrokeInjectionNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.ScanBleNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.StartNode

/**
 * Factory object that provides hardcoded, complex Attack Chain templates.
 *
 * While `AttackChainTemplate` (Entity) stores metadata, this object constructs
 * the actual `AttackChainingState` (nodes and connections) for specific scenarios.
 * This is used to hydrate the canvas when a user selects "Load Template".
 */
object AttackChainTemplates {

    // Template 1: A simple workflow that just starts and scans for BLE devices.
    private val simpleScanTemplate: AttackChainingState by lazy {
        val startNode = StartNode(id = "start")
        val scanNode = ScanBleNode(id = "scan")

        AttackChainingState(
            nodes = mapOf(
                startNode.id to startNode,
                scanNode.id to scanNode
            ),
            connections = listOf(
                // Connect Start -> Scan
                Pair(
                    startNode.outputs.first(),
                    scanNode.inputs.first()
                )
            )
        )
    }

    // Template 2: A more complex workflow: Start -> Bluesnarf -> Key Injection.
    // Logic: If bluesnarfing finds data, it might try to inject keystrokes (hypothetical flow).
    private val snarfAndInjectTemplate: AttackChainingState by lazy {
        val startNode = StartNode(id = "start")
        val bluesnarfNode = BluesnarfNode(id = "bluesnarf")
        val keystrokeNode = KeystrokeInjectionNode(id = "keystroke")

        AttackChainingState(
            nodes = mapOf(
                startNode.id to startNode,
                bluesnarfNode.id to bluesnarfNode,
                keystrokeNode.id to keystrokeNode
            ),
            connections = listOf(
                // Connect Start -> Bluesnarf
                Pair(
                    startNode.outputs.first(),
                    bluesnarfNode.inputs.first()
                ),
                // Connect Bluesnarf -> Keystroke Injection
                Pair(
                    bluesnarfNode.outputs.first(),
                    keystrokeNode.inputs.first()
                )
            )
        )
    }

    // Map exposing the templates by name.
    val templates = mapOf(
        "Simple Scan" to simpleScanTemplate,
        "Snarf and Inject" to snarfAndInjectTemplate
    )
}
