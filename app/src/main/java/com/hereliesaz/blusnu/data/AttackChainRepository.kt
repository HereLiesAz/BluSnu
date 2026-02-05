package com.hereliesaz.blusnu.data

import android.content.Context
import com.google.gson.Gson
import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingState

/**
 * Repository for managing saved Attack Chains (Workflows) using Shared Preferences.
 *
 * Unlike the AttackChainTemplateRepository which stores immutable templates in Room,
 * this repository stores user-created/saved workflows as JSON strings.
 * This allows for flexible serialization of the complex node graph structure.
 *
 * @property context The application context.
 */
class AttackChainRepository(context: Context) {

    // Access Shared Preferences file "attack_chains".
    private val sharedPreferences = context.getSharedPreferences("attack_chains", Context.MODE_PRIVATE)

    // Gson instance for JSON serialization/deserialization.
    private val gson = Gson()

    /**
     * Saves a current workflow state to disk.
     *
     * @param name The unique name for the saved chain.
     * @param state The AttackChainingState object representing the graph.
     */
    fun saveAttackChain(name: String, state: AttackChainingState) {
        // Serialize the state object to JSON.
        val json = gson.toJson(state)
        // Write to SharedPreferences synchronously (apply() is async in background).
        sharedPreferences.edit().putString(name, json).apply()
    }

    /**
     * Loads a saved workflow from disk.
     *
     * @param name The name of the chain to load.
     * @return The restored AttackChainingState, or null if not found.
     */
    fun loadAttackChain(name: String): AttackChainingState? {
        // Retrieve the JSON string.
        val json = sharedPreferences.getString(name, null)
        // Deserialize back to object.
        return gson.fromJson(json, AttackChainingState::class.java)
    }

    /**
     * Retrieves a list of all saved chain names.
     *
     * @return A Set of strings (keys in the SP file).
     */
    fun getAllAttackChainNames(): Set<String> {
        return sharedPreferences.all.keys
    }
}
