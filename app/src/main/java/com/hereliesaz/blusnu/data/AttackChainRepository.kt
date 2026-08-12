package com.hereliesaz.blusnu.data

import android.content.Context
import androidx.compose.ui.geometry.Offset
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingState
import com.hereliesaz.blusnu.ui.attackchaining.nodes.*
import java.lang.reflect.Type

/**
 * Repository for managing saved Attack Chains (Workflows) using Shared Preferences.
 *
 * @property context The application context.
 */
class AttackChainRepository(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("attack_chains", Context.MODE_PRIVATE)

    private val gson = GsonBuilder()
        .registerTypeAdapter(AttackNode::class.java, AttackNodeAdapter())
        .registerTypeAdapter(Offset::class.java, OffsetAdapter())
        .create()

    fun saveAttackChain(name: String, state: AttackChainingState) {
        val json = gson.toJson(state)
        sharedPreferences.edit().putString(name, json).apply()
    }

    fun loadAttackChain(name: String): AttackChainingState? {
        val json = sharedPreferences.getString(name, null) ?: return null
        return try {
            gson.fromJson(json, AttackChainingState::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getAllAttackChainNames(): Set<String> {
        return sharedPreferences.all.keys
    }
}

/**
 * 13.13: Custom Gson adapter for Compose [Offset].
 *
 * Compose Offset is an inline/value class that Gson cannot serialize correctly.
 * This adapter serializes it as a plain {x, y} JSON object using [NodePosition]
 * as the intermediate representation, converting to/from [Offset] at the boundary.
 */
private class OffsetAdapter : JsonSerializer<Offset>, JsonDeserializer<Offset> {
    override fun serialize(src: Offset, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val pos = NodePosition.fromOffset(src)
        return context.serialize(pos, NodePosition::class.java)
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Offset {
        val pos = context.deserialize<NodePosition>(json, NodePosition::class.java)
        return pos.toOffset()
    }
}

private class AttackNodeAdapter : JsonSerializer<AttackNode>, JsonDeserializer<AttackNode> {

    companion object {
        private const val TYPE_KEY = "_type"
        private val nodeTypes = mapOf(
            "StartNode" to StartNode::class.java,
            "IfElseNode" to IfElseNode::class.java,
            "WaitNode" to WaitNode::class.java,
            "LoopNode" to LoopNode::class.java,
            "ScanBleNode" to ScanBleNode::class.java,
            "BluesnarfNode" to BluesnarfNode::class.java,
            "KeystrokeInjectionNode" to KeystrokeInjectionNode::class.java
        )
    }

    override fun serialize(src: AttackNode, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val jsonObject = context.serialize(src, src::class.java).asJsonObject
        jsonObject.addProperty(TYPE_KEY, src::class.java.simpleName)
        return jsonObject
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): AttackNode {
        val jsonObject = json.asJsonObject
        val typeName = jsonObject.get(TYPE_KEY)?.asString
            ?: throw IllegalArgumentException("Missing $TYPE_KEY in serialized AttackNode")
        val clazz = nodeTypes[typeName]
            ?: throw IllegalArgumentException("Unknown AttackNode type: $typeName")
        return context.deserialize(jsonObject, clazz)
    }
}
