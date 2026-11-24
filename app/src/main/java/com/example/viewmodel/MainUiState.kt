package com.example.viewmodel

import com.example.engine.WorldSessionState
import com.example.model.*

enum class AppEntryMode {
    AUTH,
    ONLINE,
    OFFLINE
}

enum class StaffResourceAction {
    ADD,
    SUBTRACT,
    SET
}

data class BannedDisconnectInfo(
    val networkId: String,
    val networkName: String,
    val reason: String,
    val banUntilMillis: Long,
    val kickedFromWorld: Boolean
)

fun NetworkMember.isBanActiveAt(nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (!banned) return false
    return banUntilMillis == 0L || banUntilMillis > nowMillis
}

data class MainUiState(
    val entryMode: AppEntryMode = AppEntryMode.AUTH,
    val isInternetAvailable: Boolean = true,
    val isAuthBusy: Boolean = false,
    val authError: String? = null,
    val userAccount: UserAccount? = null,
    val activeNetworkId: String = "official",
    val networks: List<GameNetwork> = listOf(defaultOfficialNetwork()),
    val currentMember: NetworkMember? = null,
    val networkMembers: List<NetworkMember> = emptyList(),
    val rooms: List<GameRoom> = emptyList(),
    val tickets: List<SupportTicket> = emptyList(),
    val reports: List<PlayerReport> = emptyList(),
    val roomChatMessages: List<RoomChatMessage> = emptyList(),
    val offlineProfile: OfflineProfileState = OfflineProfileState(),
    val pendingOfflineWarning: Boolean = false,
    val pendingNetworkWarning: GameNetwork? = null,
    val bannedDisconnectNotice: BannedDisconnectInfo? = null,
    val banClockMillis: Long = System.currentTimeMillis(),
    val worldSession: WorldSessionState = WorldSessionState(),
    val isDailyBonusClaimBusy: Boolean = false,
    val toastMessage: String? = null
) {
    val activeNetwork: GameNetwork
        get() = networks.firstOrNull { it.id == activeNetworkId } ?: defaultOfficialNetwork()

    val isOffline: Boolean
        get() = entryMode == AppEntryMode.OFFLINE

    val displayName: String
        get() = if (isOffline) offlineProfile.username else (currentMember?.username ?: userAccount?.username ?: "Oyuncu")

    val displayRank: Rank
        get() = if (isOffline) Rank.OYUNCU else (currentMember?.rankEnum ?: Rank.OYUNCU)

    val displayLevel: Int
        get() = if (isOffline) offlineProfile.level else (currentMember?.level?.toInt() ?: 1)

    val displayXp: Long
        get() = if (isOffline) offlineProfile.xp else (currentMember?.xp ?: 0L)

    val displayCoins: Long
        get() = if (isOffline) offlineProfile.coins else (currentMember?.coins ?: 0L)

    val displayGems: Long
        get() = if (isOffline) offlineProfile.gems else (currentMember?.gems ?: 0L)

    val displaySkinIndex: Int
        get() = if (isOffline) offlineProfile.skinColor else (currentMember?.skinColor?.toInt() ?: 0)

    val displayInventory: List<InventorySlot>
        get() = if (isOffline) {
            offlineProfile.inventory
        } else {
            parseInventoryString(currentMember?.inventoryJson.orEmpty())
                .ifEmpty {
                    listOf(
                        InventorySlot("wood_sword", 1),
                        InventorySlot("wool_cyan", 32),
                        InventorySlot("wood", 16),
                        InventorySlot("pickaxe", 1),
                        InventorySlot("bread", 4)
                    )
                }
        }

    val isMemberBannedActive: Boolean
        get() {
            if (isOffline) return false
            val m = currentMember ?: return false
            return m.isBanActiveAt(maxOf(banClockMillis, System.currentTimeMillis()))
        }

    val isMemberMutedActive: Boolean
        get() {
            if (isOffline) return false
            val m = currentMember ?: return false
            if (!m.muted) return false
            return m.muteUntilMillis == 0L || m.muteUntilMillis > maxOf(banClockMillis, System.currentTimeMillis())
        }

    val isNetworkMaintenanceBlocked: Boolean
        get() = !isOffline && activeNetwork.maintenanceMode && !displayRank.canAccessStaffPanel

    val activeRoom: GameRoom?
        get() = if (worldSession.active && !isOffline) rooms.firstOrNull { it.id == worldSession.roomId } else null

    val lastDailyBonusClaimMillis: Long
        get() = if (isOffline) {
            offlineProfile.lastDailyBonusClaimMillis
        } else {
            currentMember?.lastDailyBonusAt?.toDate()?.time ?: 0L
        }

    fun dailyBonusRemainingMillisAt(nowMillis: Long = maxOf(banClockMillis, System.currentTimeMillis())): Long =
        DailyBonusPolicy.remainingMillisAt(lastDailyBonusClaimMillis, nowMillis)

    val dailyBonusRemainingMillis: Long
        get() = dailyBonusRemainingMillisAt()

    val canClaimDailyBonusNow: Boolean
        get() = !isDailyBonusClaimBusy && (isOffline || !isMemberBannedActive) && dailyBonusRemainingMillis <= 0L
}

fun defaultOfficialNetwork(): GameNetwork = GameNetwork(
    id = "official",
    name = "DenizCraft Resmi Network",
    description = "DenizCraft resmi çok oyunculu mini oyun ağı",
    policyText = "Hile, hakaret ve bug kullanımı yasaktır. Tüm yetkili kararları geçerlidir.",
    founderUid = "official_founder",
    founderName = "Kurucu",
    official = true,
    active = true,
    playerCount = 1L
)

object ProgressionUtils {
    fun mergeItemIntoSlots(slots: List<InventorySlot>, itemId: String, amount: Int): List<InventorySlot> {
        val mutable = slots.toMutableList()
        val idx = mutable.indexOfFirst { it.itemId == itemId }
        if (idx >= 0) {
            mutable[idx] = mutable[idx].copy(count = (mutable[idx].count + amount).coerceAtMost(999))
        } else if (amount > 0) {
            mutable += InventorySlot(itemId, amount.coerceAtMost(999))
        }
        return mutable
    }

    fun addXpWithLevelUp(currentLevel: Int, currentXp: Long, addedXp: Long): Pair<Int, Long> {
        var lv = currentLevel.coerceIn(1, 40)
        var xp = (currentXp + addedXp).coerceAtLeast(0L)
        while (lv < 40) {
            val needed = LevelSystem.xpForNextLevel(lv)
            if (xp >= needed) {
                xp -= needed
                lv++
            } else {
                break
            }
        }
        return lv to xp
    }
}
