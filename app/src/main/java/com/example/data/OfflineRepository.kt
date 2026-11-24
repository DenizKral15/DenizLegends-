package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.GameMode
import com.example.model.OfflineProfileState
import com.example.model.parseInventoryString
import com.example.model.serializeInventorySlots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.offlineDataStore: DataStore<Preferences> by preferencesDataStore(name = "denizcraft_offline_profile")

/**
 * Strictly client-side Offline Mode storage.
 * Easily modifiable by the local player in Offline Mode without affecting Online play.
 */
class OfflineRepository(context: Context) {
    private val dataStore = context.applicationContext.offlineDataStore

    private object Keys {
        val USERNAME = stringPreferencesKey("username")
        val LEVEL = intPreferencesKey("level")
        val XP = longPreferencesKey("xp")
        val COINS = longPreferencesKey("coins")
        val GEMS = longPreferencesKey("gems")
        val IRON = intPreferencesKey("iron")
        val GOLD = intPreferencesKey("gold")
        val EMERALD = intPreferencesKey("emerald")
        val DIAMOND = intPreferencesKey("diamond")
        val GAME_MODE = stringPreferencesKey("game_mode")
        val SKIN_COLOR = intPreferencesKey("skin_color")
        val INVENTORY = stringPreferencesKey("inventory")
        val CREATIVE_PLOT = stringPreferencesKey("creative_plot")
        val SKYBLOCK_PLOT = stringPreferencesKey("skyblock_plot")
        val LAST_DAILY_BONUS_MILLIS = longPreferencesKey("last_daily_bonus_millis")
    }

    fun observeOfflineProfile(): Flow<OfflineProfileState> =
        dataStore.data.map { prefs ->
            val defaultState = OfflineProfileState()
            val invStr = prefs[Keys.INVENTORY]
            OfflineProfileState(
                username = prefs[Keys.USERNAME] ?: defaultState.username,
                level = (prefs[Keys.LEVEL] ?: defaultState.level).coerceIn(1, 40),
                xp = (prefs[Keys.XP] ?: defaultState.xp).coerceAtLeast(0L),
                coins = (prefs[Keys.COINS] ?: defaultState.coins).coerceAtLeast(0L),
                gems = (prefs[Keys.GEMS] ?: defaultState.gems).coerceAtLeast(0L),
                iron = (prefs[Keys.IRON] ?: defaultState.iron).coerceAtLeast(0),
                gold = (prefs[Keys.GOLD] ?: defaultState.gold).coerceAtLeast(0),
                emerald = (prefs[Keys.EMERALD] ?: defaultState.emerald).coerceAtLeast(0),
                diamond = (prefs[Keys.DIAMOND] ?: defaultState.diamond).coerceAtLeast(0),
                gameMode = GameMode.fromCode(prefs[Keys.GAME_MODE]),
                skinColor = prefs[Keys.SKIN_COLOR] ?: defaultState.skinColor,
                inventory = if (invStr != null) parseInventoryString(invStr) else defaultState.inventory,
                creativePlotData = prefs[Keys.CREATIVE_PLOT] ?: "",
                skyblockPlotData = prefs[Keys.SKYBLOCK_PLOT] ?: "",
                lastDailyBonusClaimMillis = (prefs[Keys.LAST_DAILY_BONUS_MILLIS] ?: 0L).coerceAtLeast(0L)
            )
        }

    suspend fun saveOfflineProfile(state: OfflineProfileState) {
        dataStore.edit { prefs ->
            prefs[Keys.USERNAME] = state.username
            prefs[Keys.LEVEL] = state.level.coerceIn(1, 40)
            prefs[Keys.XP] = state.xp.coerceAtLeast(0L)
            prefs[Keys.COINS] = state.coins.coerceAtLeast(0L)
            prefs[Keys.GEMS] = state.gems.coerceAtLeast(0L)
            prefs[Keys.IRON] = state.iron.coerceAtLeast(0)
            prefs[Keys.GOLD] = state.gold.coerceAtLeast(0)
            prefs[Keys.EMERALD] = state.emerald.coerceAtLeast(0)
            prefs[Keys.DIAMOND] = state.diamond.coerceAtLeast(0)
            prefs[Keys.GAME_MODE] = state.gameMode.code
            prefs[Keys.SKIN_COLOR] = state.skinColor
            prefs[Keys.INVENTORY] = serializeInventorySlots(state.inventory)
            prefs[Keys.CREATIVE_PLOT] = state.creativePlotData
            prefs[Keys.SKYBLOCK_PLOT] = state.skyblockPlotData
            prefs[Keys.LAST_DAILY_BONUS_MILLIS] = state.lastDailyBonusClaimMillis.coerceAtLeast(0L)
        }
    }
}
