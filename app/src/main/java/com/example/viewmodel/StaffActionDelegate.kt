package com.example.viewmodel

import com.example.data.StaffRepository
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Encapsulates RBAC-checked Staff Panel Firestore mutations so MainViewModel stays lean.
 */
class StaffActionDelegate(
    private val staffRepo: StaffRepository,
    private val scope: CoroutineScope,
    private val getState: () -> MainUiState,
    private val onToast: (String) -> Unit,
    private val onBannedPlayerEvict: (networkId: String, targetUid: String) -> Unit = { _, _ -> }
) {
    fun submitSupportTicket(subject: String, message: String) {
        val state = getState()
        if (state.isOffline) return
        if (state.isMemberBannedActive) {
            onToast("Bu ağdan yasaklı olduğunuz için destek talebi gönderemezsiniz.")
            return
        }
        scope.launch {
            staffRepo.createSupportTicket(state.activeNetworkId, state.displayName, subject, message)
                .onSuccess { onToast("Destek talebi gönderildi!") }
                .onFailure { onToast(it.localizedMessage ?: "Talep gönderilemedi.") }
        }
    }

    fun submitPlayerReport(targetMember: NetworkMember, reason: String) {
        submitPlayerReportByUid(targetMember.userId, targetMember.username, targetMember.rankEnum, reason)
    }

    fun submitPlayerReportByUid(targetUid: String, targetUsername: String, targetRank: Rank, reason: String) {
        val state = getState()
        if (state.isOffline) return
        if (state.isMemberBannedActive) {
            onToast("Bu ağdan yasaklı olduğunuz için oyuncu raporlayamazsınız.")
            return
        }
        if (targetRank.isProtectedFromPunishment) {
            onToast("Kurucu rütbesi rapor korumasına sahiptir!")
            return
        }
        scope.launch {
            staffRepo.createReport(state.activeNetworkId, state.displayName, targetUid, targetUsername, reason)
                .onSuccess { onToast("Rapor iletildi.") }
                .onFailure { onToast(it.localizedMessage ?: "Rapor gönderilemedi.") }
        }
    }

    fun replyTicket(ticket: SupportTicket, replyText: String) {
        val state = getState()
        if (state.isOffline || !state.displayRank.canHandleTickets) return
        scope.launch {
            staffRepo.replySupportTicket(state.activeNetworkId, ticket.id, state.displayName, replyText)
                .onSuccess { onToast("Destek talebi yanıtlandı.") }
                .onFailure { onToast(it.localizedMessage ?: "İşlem başarısız.") }
        }
    }

    fun reviewReport(report: PlayerReport, accepted: Boolean) {
        val state = getState()
        if (state.isOffline || !state.displayRank.canHandleReports) return
        scope.launch {
            staffRepo.reviewReport(state.activeNetworkId, report.id, accepted, state.displayName)
                .onSuccess { onToast(if (accepted) "Rapor kabul edildi." else "Rapor reddedildi.") }
                .onFailure { onToast(it.localizedMessage ?: "İşlem başarısız.") }
        }
    }

    fun banPlayer(target: NetworkMember, banned: Boolean, reason: String, durationMinutes: Long?) {
        val state = getState()
        if (state.isOffline || !state.displayRank.canBan) return
        val isSelf = target.userId == state.currentMember?.userId
        if (!state.displayRank.canPunish(target.rankEnum, isSelf = isSelf)) {
            onToast(if (isSelf && target.rankEnum == Rank.KURUCU) "Kurucu kendi hesabını yasaklayamaz!" else "Bu rütbeye yasaklama yetkiniz yok!")
            return
        }
        scope.launch {
            staffRepo.banMember(state.activeNetworkId, target.userId, banned, reason, durationMinutes)
                .onSuccess {
                    if (banned) {
                        onBannedPlayerEvict(state.activeNetworkId, target.userId)
                        onToast("${target.username} yasaklandı ve ağın tüm odalarından uzaklaştırıldı.")
                    } else {
                        onToast("${target.username} yasağı kaldırıldı.")
                    }
                }
                .onFailure { onToast(it.localizedMessage ?: "Ban başarısız.") }
        }
    }

    fun mutePlayer(target: NetworkMember, muted: Boolean, reason: String, durationMinutes: Long?) {
        val state = getState()
        if (state.isOffline || !state.displayRank.canMute) return
        val isSelf = target.userId == state.currentMember?.userId
        if (!state.displayRank.canPunish(target.rankEnum, isSelf = isSelf)) {
            onToast(if (isSelf && target.rankEnum == Rank.KURUCU) "Kurucu kendi hesabını susturamaz!" else "Bu rütbeye susturma yetkiniz yok!")
            return
        }
        scope.launch {
            staffRepo.muteMember(state.activeNetworkId, target.userId, muted, reason, durationMinutes)
                .onSuccess { onToast(if (muted) "${target.username} susturuldu." else "${target.username} susturması açıldı.") }
                .onFailure { onToast(it.localizedMessage ?: "Mute başarısız.") }
        }
    }

    fun warnPlayer(target: NetworkMember, reason: String) {
        val state = getState()
        if (state.isOffline || !state.displayRank.canWarn) return
        val isSelf = target.userId == state.currentMember?.userId
        if (!state.displayRank.canPunish(target.rankEnum, isSelf = isSelf)) {
            onToast(if (isSelf && target.rankEnum == Rank.KURUCU) "Kurucu kendi hesabını uyaramaz!" else "Bu rütbeyi uyarma yetkiniz yok!")
            return
        }
        scope.launch {
            staffRepo.warnMember(state.activeNetworkId, target.userId, target.warningsCount)
                .onSuccess { onToast("${target.username} uyarıldı ($reason).") }
                .onFailure { onToast(it.localizedMessage ?: "Uyarı başarısız.") }
        }
    }

    fun assignRank(target: NetworkMember, newRank: Rank) {
        val state = getState()
        val isSelf = target.userId == state.currentMember?.userId
        if (state.isOffline || !state.displayRank.canAssignRank(newRank, target.rankEnum, isSelf = isSelf)) {
            onToast(
                if (isSelf && target.rankEnum == Rank.KURUCU && newRank != Rank.KURUCU) {
                    "Kurucu hesabının rütbesi kendi tarafından düşürülemez!"
                } else {
                    "Bu rütbeyi atama yetkiniz bulunmuyor!"
                }
            )
            return
        }
        scope.launch {
            staffRepo.setMemberRank(state.activeNetworkId, target.userId, newRank)
                .onSuccess { onToast("${target.username} -> ${newRank.displayName} atandı.") }
                .onFailure { onToast(it.localizedMessage ?: "Rütbe değiştirilemedi.") }
        }
    }

    fun setGameMode(target: NetworkMember, mode: GameMode, onLocalModeUpdated: (GameMode) -> Unit) {
        val state = getState()
        if (state.isOffline || !state.displayRank.canAccessManagementTab) return
        scope.launch {
            staffRepo.setMemberGameMode(state.activeNetworkId, target.userId, mode)
                .onSuccess {
                    onLocalModeUpdated(mode)
                    onToast("${target.username} oyun modu: ${mode.displayName}")
                }
                .onFailure { onToast(it.localizedMessage ?: "Oyun modu değiştirilemedi.") }
        }
    }

    fun modifyMemberStats(
        target: NetworkMember,
        action: StaffResourceAction,
        coinsAmount: Long?,
        gemsAmount: Long?,
        levelAmount: Long?,
        addItem: Pair<String, Int>? = null,
        removeItem: Pair<String, Int>? = null
    ) {
        val state = getState()
        if (state.isOffline || !state.displayRank.canAccessManagementTab) return
        val newCoins = if (coinsAmount != null) when (action) {
            StaffResourceAction.ADD -> target.coins + coinsAmount
            StaffResourceAction.SUBTRACT -> (target.coins - coinsAmount).coerceAtLeast(0L)
            StaffResourceAction.SET -> coinsAmount.coerceAtLeast(0L)
        } else target.coins

        val newGems = if (gemsAmount != null) when (action) {
            StaffResourceAction.ADD -> target.gems + gemsAmount
            StaffResourceAction.SUBTRACT -> (target.gems - gemsAmount).coerceAtLeast(0L)
            StaffResourceAction.SET -> gemsAmount.coerceAtLeast(0L)
        } else target.gems

        val newLevel = if (levelAmount != null) when (action) {
            StaffResourceAction.ADD -> (target.level + levelAmount).coerceIn(1L, 40L)
            StaffResourceAction.SUBTRACT -> (target.level - levelAmount).coerceIn(1L, 40L)
            StaffResourceAction.SET -> levelAmount.coerceIn(1L, 40L)
        } else target.level

        var slots = parseInventoryString(target.inventoryJson)
        if (addItem != null) slots = ProgressionUtils.mergeItemIntoSlots(slots, addItem.first, addItem.second)
        if (removeItem != null) {
            slots = slots.mapNotNull { slot ->
                if (slot.itemId == removeItem.first) {
                    val left = slot.count - removeItem.second
                    if (left > 0) slot.copy(count = left) else null
                } else slot
            }
        }
        scope.launch {
            staffRepo.updateMemberEconomyAndLevel(
                networkId = state.activeNetworkId,
                targetUid = target.userId,
                coins = newCoins,
                gems = newGems,
                level = newLevel,
                inventoryJson = serializeInventorySlots(slots)
            ).onSuccess {
                onToast("${target.username} verileri güncellendi.")
            }.onFailure {
                onToast(it.localizedMessage ?: "Güncelleme başarısız.")
            }
        }
    }
}
