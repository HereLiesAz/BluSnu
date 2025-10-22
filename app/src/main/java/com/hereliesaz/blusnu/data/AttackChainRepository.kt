package com.hereliesaz.blusnu.data

import android.content.Context
import com.google.gson.Gson
import com.hereliesaz.blusnu.ui.attackchaining.AttackChainingState

class AttackChainRepository(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("attack_chains", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveAttackChain(name: String, state: AttackChainingState) {
        val json = gson.toJson(state)
        sharedPreferences.edit().putString(name, json).apply()
    }

    fun loadAttackChain(name: String): AttackChainingState? {
        val json = sharedPreferences.getString(name, null)
        return gson.fromJson(json, AttackChainingState::class.java)
    }

    fun getAllAttackChainNames(): Set<String> {
        return sharedPreferences.all.keys
    }
}
