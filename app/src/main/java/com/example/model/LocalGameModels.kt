package com.example.model

import androidx.compose.ui.graphics.Color

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class VoxelCoord(val x: Int, val y: Int, val z: Int) {
    fun key(): String = "${x}_${y}_${z}"
}

data class InventorySlot(
    val itemId: String,
    val count: Int
)

fun parseInventoryString(raw: String): List<InventorySlot> {
    if (raw.isBlank()) return emptyList()
    return raw.split(",")
        .mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val count = parts[1].toIntOrNull() ?: 0
                if (parts[0].isNotBlank() && count > 0) InventorySlot(parts[0], count) else null
            } else null
        }
}

fun serializeInventorySlots(slots: List<InventorySlot>): String =
    slots.filter { it.count > 0 }.joinToString(",") { "${it.itemId}:${it.count}" }

fun swapInventoryIndices(slots: List<InventorySlot>, fromIdx: Int, toIdx: Int): List<InventorySlot> {
    if (fromIdx !in slots.indices || toIdx < 0 || fromIdx == toIdx) return slots
    val mutable = slots.toMutableList()
    if (toIdx < mutable.size) {
        val tmp = mutable[fromIdx]
        mutable[fromIdx] = mutable[toIdx]
        mutable[toIdx] = tmp
    } else {
        val item = mutable.removeAt(fromIdx)
        mutable.add(toIdx.coerceAtMost(mutable.size), item)
    }
    return mutable
}

fun putItemAtHotbarSlot(slots: List<InventorySlot>, slotIndex: Int, itemId: String, amount: Int): List<InventorySlot> {
    val targetIdx = slotIndex.coerceIn(0, 8)
    val mutable = slots.toMutableList()
    if (targetIdx < mutable.size) {
        mutable[targetIdx] = InventorySlot(itemId, amount.coerceIn(1, 999))
    } else {
        mutable += InventorySlot(itemId, amount.coerceIn(1, 999))
    }
    return mutable
}

data class WorldEntity(
    val id: String,
    val name: String,
    val kind: EntityKind,
    val x: Float,
    val y: Float,
    val z: Float,
    val yaw: Float = 0f,
    val hp: Int = 20,
    val maxHp: Int = 20,
    val rank: Rank = Rank.OYUNCU,
    val level: Int = 1,
    val team: String = "NONE",
    val bodyColor: Color = Color(0xFF00BCD4),
    val headColor: Color = Color(0xFFFFCC80),
    val mobType: MobType? = null,
    val targetServer: ServerType? = null,
    val alive: Boolean = true,
    val attackCooldown: Float = 0f,
    val hurtFlashTimer: Float = 0f,
    val velY: Float = 0f,
    val onGround: Boolean = true,
    val aiActionCooldown: Float = 0f,
    val aiWanderTimer: Float = 0f,
    val strafeSign: Float = 1f
)

enum class MatchmakingPhase {
    WAITING_FOR_PLAYERS,
    COUNTDOWN_30S,
    FULL_ROOM_3S
}

data class MatchmakingPlan(
    val phase: MatchmakingPhase,
    val targetStartAtMillis: Long,
    val remainingSec: Int
)

object MatchmakingPolicy {
    fun evaluate(
        playerCount: Int,
        maxPlayers: Int,
        currentStartAtMillis: Long,
        nowMillis: Long
    ): MatchmakingPlan {
        if (playerCount <= 1) {
            return MatchmakingPlan(
                phase = MatchmakingPhase.WAITING_FOR_PLAYERS,
                targetStartAtMillis = 0L,
                remainingSec = -1
            )
        }
        val safeMax = maxPlayers.coerceAtLeast(2)
        if (playerCount >= safeMax) {
            val capped = nowMillis + 3_000L
            val target = if (currentStartAtMillis > 0L) minOf(currentStartAtMillis, capped) else capped
            val rem = (((target - nowMillis) + 999L) / 1000L).toInt().coerceIn(0, 3)
            return MatchmakingPlan(
                phase = MatchmakingPhase.FULL_ROOM_3S,
                targetStartAtMillis = target,
                remainingSec = rem
            )
        }
        val target = if (currentStartAtMillis > 0L) currentStartAtMillis else (nowMillis + 30_000L)
        val rem = (((target - nowMillis) + 999L) / 1000L).toInt().coerceIn(0, 30)
        return MatchmakingPlan(
            phase = MatchmakingPhase.COUNTDOWN_30S,
            targetStartAtMillis = target,
            remainingSec = rem
        )
    }
}

enum class EntityKind {
    PLAYER,
    BOT,
    MOB,
    NPC_PORTAL,
    NPC_MARKET,
    NPC_BANK,
    GENERATOR
}

data class BedState(
    val team: String,
    val label: String,
    val color: Color,
    val coord: VoxelCoord,
    val intact: Boolean = true
)

object DailyBonusPolicy {
    const val COOLDOWN_MS: Long = 24L * 60L * 60L * 1000L
    const val DAILY_COINS: Long = 250L
    const val DAILY_GEMS: Long = 5L
    const val DAILY_XP: Long = 60L

    fun remainingMillisAt(lastClaimMillis: Long, nowMillis: Long = System.currentTimeMillis()): Long {
        if (lastClaimMillis <= 0L) return 0L
        return (lastClaimMillis + COOLDOWN_MS - nowMillis).coerceAtLeast(0L)
    }

    fun canClaimAt(lastClaimMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return remainingMillisAt(lastClaimMillis, nowMillis) <= 0L
    }

    fun formatRemainingTurkish(remainingMillis: Long): String {
        if (remainingMillis <= 0L) return "Hazır"
        val totalMin = ((remainingMillis + 59_999L) / 60_000L).coerceAtLeast(1L)
        return if (totalMin >= 60L) {
            val hours = totalMin / 60L
            val mins = totalMin % 60L
            if (mins == 0L) "$hours saat" else "$hours sa $mins dk"
        } else {
            "$totalMin dakika"
        }
    }

    fun formatCompactBadge(remainingMillis: Long): String {
        if (remainingMillis <= 0L) return "BONUS"
        val hours = remainingMillis / 3_600_000L
        val mins = (remainingMillis % 3_600_000L) / 60_000L
        return if (hours >= 1L) "${hours}s ${mins}d" else "${mins.coerceAtLeast(1L)} dk"
    }
}

data class OfflineProfileState(
    val username: String = "YerelOyuncu",
    val level: Int = 5,
    val xp: Long = 120L,
    val coins: Long = 2500L,
    val gems: Long = 50L,
    val iron: Int = 32,
    val gold: Int = 12,
    val emerald: Int = 4,
    val diamond: Int = 4,
    val gameMode: GameMode = GameMode.SURVIVAL,
    val skinColor: Int = 0,
    val inventory: List<InventorySlot> = listOf(
        InventorySlot("iron_sword", 1),
        InventorySlot("wool_cyan", 64),
        InventorySlot("wood", 32),
        InventorySlot("stone", 32),
        InventorySlot("pickaxe", 1),
        InventorySlot("bread", 4),
        InventorySlot("tnt", 4)
    ),
    val creativePlotData: String = "",
    val skyblockPlotData: String = "",
    val lastDailyBonusClaimMillis: Long = 0L
)

val SKIN_PALETTES: List<Pair<String, Color>> = listOf(
    "Kaptan Deniz" to Color(0xFF00ACC1),
    "Kral Altın" to Color(0xFFFFB300),
    "Zümrüt Savaşçı" to Color(0xFF2ECC71),
    "Kızıl Korsan" to Color(0xFFE53935),
    "Mor Büyücü" to Color(0xFF8E24AA),
    "Obsidyen Şövalye" to Color(0xFF37474F)
)
