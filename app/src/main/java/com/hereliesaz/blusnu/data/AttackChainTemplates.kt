package com.hereliesaz.blusnu.data

import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingState
import com.hereliesaz.blusnu.ui.attackchaining.nodes.ScanBleNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.BluesnarfNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.IfElseNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.KeystrokeInjectionNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.LoopNode
import com.hereliesaz.blusnu.ui.attackchaining.nodes.WaitNode

object AttackChainTemplates {

    val templates = mapOf(
        "BLE Smart Lock Audit" to createBleSmartLockAuditTemplate(),
        "Opportunistic Eavesdropping" to createOpportunisticEavesdroppingTemplate()
    )

    private fun createBleSmartLockAuditTemplate(): AttackChainingState {
        val scanNode = ScanBleNode(id = "scan_ble")
        val ifNode = IfElseNode(id = "if_lock_found")
        val keystrokeNode = KeystrokeInjectionNode(id = "keystroke_injection")

        return AttackChainingState(
            nodes = mapOf(
                scanNode.id to scanNode,
                ifNode.id to ifNode,
                keystrokeNode.id to keystrokeNode
            ),
            connections = listOf(
                Pair(scanNode.outputs.first(), ifNode.inputs.first()),
                Pair(ifNode.outputs.first(), keystrokeNode.inputs.first())
            )
        )
    }

    private fun createOpportunisticEavesdroppingTemplate(): AttackChainingState {
        val scanNode = ScanBleNode(id = "scan_ble")
        val loopNode = LoopNode(id = "loop_devices")
        val bluesnarfNode = BluesnarfNode(id = "bluesnarf")
        val waitNode = WaitNode(id = "wait")

        return AttackChainingState(
            nodes = mapOf(
                scanNode.id to scanNode,
                loopNode.id to loopNode,
                bluesnarfNode.id to bluesnarfNode,
                waitNode.id to waitNode
            ),
            connections = listOf(
                Pair(scanNode.outputs.first(), loopNode.inputs.first()),
                Pair(loopNode.outputs.first(), bluesnarfNode.inputs.first()),
                Pair(bluesnarfNode.outputs.first(), waitNode.inputs.first()),
                Pair(waitNode.outputs.first(), loopNode.inputs.first())
            )
        )
    }
}
