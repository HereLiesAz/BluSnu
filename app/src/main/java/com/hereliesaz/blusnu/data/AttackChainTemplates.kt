package com.hereliesaz.blusnu.data

import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingState
import com.hereliesaz.blusnu.ui.attackchaining.nodes.BluesnarfNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.KeystrokeInjectionNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.ScanBleNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.StartNode

object AttackChainTemplates {

    private val simpleScanTemplate: AttackChainingState by lazy {
        val startNode = StartNode(id = "start")
        val scanNode = ScanBleNode(id = "scan")
        AttackChainingState(
            nodes = mapOf(
                startNode.id to startNode,
                scanNode.id to scanNode
            ),
            connections = listOf(
                Pair(
                    startNode.outputs.first(),
                    scanNode.inputs.first()
                )
            )
        )
    }

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
                Pair(
                    startNode.outputs.first(),
                    bluesnarfNode.inputs.first()
                ),
                Pair(
                    bluesnarfNode.outputs.first(),
                    keystrokeNode.inputs.first()
                )
            )
        )
    }

    val templates = mapOf(
        "Simple Scan" to simpleScanTemplate,
        "Snarf and Inject" to snarfAndInjectTemplate
    )
}
