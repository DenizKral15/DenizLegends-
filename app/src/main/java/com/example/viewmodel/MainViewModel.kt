package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.engine.*
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(
    private val denizRepo: DenizCraftRepository,
    staffRepo: StaffRepository,
    private val offlineRepo: OfflineRepository,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    val sessionController = GameSessionController()

    @Volatile
    private var currentAuthUid: String? = Firebase.auth.currentUser?.uid

    private val _uiState = MutableStateFlow(
        MainUiState(entryMode = if (currentAuthUid != null) AppEntryMode.ONLINE else AppEntryMode.AUTH)
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val staffActions = StaffActionDelegate(
        staffRepo = staffRepo,
        scope = viewModelScope,
        getState = { _uiState.value },
        onToast = { showToast(it) },
        onBannedPlayerEvict = { networkId, targetUid ->
            viewModelScope.launch {
                denizRepo.removePlayerFromAllNetworkRooms(networkId, targetUid, updateRoomCounts = true)
            }
        }
    )

    private var bootstrapJob: Job? = null
    private var networkObserversJob: Job? = null
    private var roomObserversJob: Job? = null
    private var banExpiryWatcherJob: Job? = null
    private var gameLoopJob: Job? = null
    private var syncAccumulator: Float = 0f
    private var lastJumpTapMs: Long = 0L
    private var lastAnnouncedBanKey: String? = null
    private var lastMatchmakingSyncMs: Long = 0L
    private val seenActiveNetworkIds: MutableSet<String> = HashSet()
    private val processedInteractionIds: MutableSet<String> = HashSet()

    @Volatile var moveForwardInput: Float = 0f
    @Volatile var moveStrafeInput: Float = 0f
    @Volatile var flyUpInput: Boolean = false
    @Volatile var flyDownInput: Boolean = false

    init {
        viewModelScope.launch {
            connectivityObserver.observeOnlineStatus().collect { online ->
                _uiState.update { it.copy(isInternetAvailable = online) }
            }
        }
        viewModelScope.launch {
            offlineRepo.observeOfflineProfile().collect { profile ->
                _uiState.update { it.copy(offlineProfile = profile) }
            }
        }
        viewModelScope.launch {
            Firebase.auth.authStateFlow()
                .map { it?.uid }
                .distinctUntilChanged()
                .collect { uid ->
                    currentAuthUid = uid
                    if (uid != null) {
                        bootstrapOnlineSession(uid)
                    } else {
                        bootstrapJob?.cancel()
                        networkObserversJob?.cancel()
                        roomObserversJob?.cancel()
                        banExpiryWatcherJob?.cancel()
                        lastAnnouncedBanKey = null
                        if (_uiState.value.entryMode != AppEntryMode.OFFLINE) {
                            stopGameLoop()
                            _uiState.update { state ->
                                MainUiState(
                                    entryMode = AppEntryMode.AUTH,
                                    isInternetAvailable = state.isInternetAvailable,
                                    offlineProfile = state.offlineProfile
                                )
                            }
                        }
                    }
                }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                val st = _uiState.value
                if (st.dailyBonusRemainingMillis > 0L || st.isMemberBannedActive) {
                    _uiState.update { it.copy(banClockMillis = System.currentTimeMillis()) }
                }
            }
        }
    }

    fun onSilentSignInCheck(context: Context) {
        if (currentAuthUid != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthBusy = true) }
            AuthHelper.attemptSilentSignIn(context)
            _uiState.update { it.copy(isAuthBusy = false) }
        }
    }

    fun startInteractiveSignIn(activityContext: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthBusy = true, authError = null) }
            AuthHelper.signInInteractive(
                activityContext = activityContext,
                onSuccess = { _uiState.update { it.copy(isAuthBusy = false, authError = null) } },
                onCancelled = { _uiState.update { it.copy(isAuthBusy = false, authError = "Giriş iptal edildi.") } },
                onError = { err -> _uiState.update { it.copy(isAuthBusy = false, authError = err) } }
            )
        }
    }

    fun requestOfflineModeSwitch() = _uiState.update { it.copy(pendingOfflineWarning = true) }
    fun dismissOfflineWarning() = _uiState.update { it.copy(pendingOfflineWarning = false) }

    fun confirmEnterOfflineMode() {
        networkObserversJob?.cancel()
        roomObserversJob?.cancel()
        banExpiryWatcherJob?.cancel()
        lastAnnouncedBanKey = null
        stopGameLoop()
        _uiState.update {
            it.copy(
                pendingOfflineWarning = false,
                bannedDisconnectNotice = null,
                pendingNetworkWarning = null,
                entryMode = AppEntryMode.OFFLINE,
                worldSession = WorldSessionState(),
                roomChatMessages = emptyList(),
                toastMessage = "Çevrimdışı moda geçildi. Botlarla oynuyorsun!"
            )
        }
    }

    fun exitOfflineModeToAuth() {
        stopGameLoop()
        _uiState.update { it.copy(entryMode = AppEntryMode.AUTH, worldSession = WorldSessionState()) }
    }

    fun signOut(context: Context) {
        stopGameLoop()
        networkObserversJob?.cancel()
        roomObserversJob?.cancel()
        banExpiryWatcherJob?.cancel()
        lastAnnouncedBanKey = null
        viewModelScope.launch { AuthHelper.signOut(context) }
    }

    private fun bootstrapOnlineSession(uid: String) {
        bootstrapJob?.cancel()
        bootstrapJob = viewModelScope.launch {
            val account = denizRepo.ensureUserAccount().getOrNull()
            denizRepo.ensureOfficialNetworkIfAdmin()
            val initialNet = account?.preferredNetworkId?.takeIf { it.isNotBlank() } ?: "official"
            _uiState.update {
                it.copy(entryMode = AppEntryMode.ONLINE, userAccount = account, activeNetworkId = initialNet)
            }
            launch {
                denizRepo.observeNetworks().catch { }.collect { remoteNets ->
                    remoteNets.forEach { seenActiveNetworkIds.add(it.id) }
                    val hasOfficial = remoteNets.any { it.id == "official" }
                    val merged = if (hasOfficial) remoteNets else listOf(defaultOfficialNetwork()) + remoteNets
                    val curState = _uiState.value
                    val curNetId = curState.activeNetworkId
                    if (curNetId != "official" && seenActiveNetworkIds.contains(curNetId) && remoteNets.none { it.id == curNetId }) {
                        seenActiveNetworkIds.remove(curNetId)
                        _uiState.update { it.copy(networks = merged) }
                        switchActiveNetworkInternal(uid, "official")
                        showToast("Bulunduğunuz sunucu silindi. Resmi Ağa aktarıldınız.")
                        return@collect
                    }
                    _uiState.update { it.copy(networks = merged) }
                    val activeNet = merged.firstOrNull { it.id == curNetId }
                    if (activeNet != null && activeNet.maintenanceMode && !curState.displayRank.canAccessStaffPanel && curState.worldSession.active && curState.worldSession.networkId == curNetId) {
                        leaveWorldSession()
                        showToast("Sunucu bakım moduna alındı! Lobiden ve maçtan çıkarıldınız.")
                    }
                }
            }
            switchActiveNetworkInternal(uid, initialNet)
        }
    }

    fun requestSwitchNetwork(network: GameNetwork) {
        if (_uiState.value.isOffline || network.id == _uiState.value.activeNetworkId) return
        if (!network.official) {
            _uiState.update { it.copy(pendingNetworkWarning = network) }
        } else {
            val uid = currentAuthUid ?: return
            switchActiveNetworkInternal(uid, network.id)
        }
    }

    fun dismissNetworkWarning() = _uiState.update { it.copy(pendingNetworkWarning = null) }

    fun confirmSwitchIndependentNetwork() {
        val target = _uiState.value.pendingNetworkWarning ?: return
        val uid = currentAuthUid ?: return
        _uiState.update { it.copy(pendingNetworkWarning = null) }
        switchActiveNetworkInternal(uid, target.id)
    }

    fun createAndJoinIndependentNetwork(name: String, description: String, policyText: String) {
        val uid = currentAuthUid ?: return
        val account = _uiState.value.userAccount ?: return
        viewModelScope.launch {
            denizRepo.createIndependentNetwork(name, description, policyText, account.username)
                .onSuccess { netId ->
                    switchActiveNetworkInternal(uid, netId, isFounder = true)
                    showToast("Bağımsız Network oluşturuldu! Kurucu sensin.")
                }
                .onFailure { showToast(it.localizedMessage ?: "Network oluşturulamadı.") }
        }
    }

    fun toggleNetworkMaintenance(network: GameNetwork) {
        val st = _uiState.value
        val uid = currentAuthUid ?: return
        if (st.isOffline) return
        val canManage = (st.activeNetworkId == network.id && st.displayRank.canManageMaintenance) ||
            network.founderUid == uid ||
            denizRepo.isOfficialAdminEmail()
        if (!canManage) {
            showToast("Bakım modunu yönetmek için Yönetici veya Kurucu yetkisi gerekir.")
            return
        }
        val nextEnabled = !network.maintenanceMode
        viewModelScope.launch {
            denizRepo.setNetworkMaintenanceMode(network.id, nextEnabled)
                .onSuccess {
                    showToast(
                        if (nextEnabled) "${network.name} bakım moduna alındı. Sadece yetkililer girebilir."
                        else "${network.name} bakım modundan çıkarıldı."
                    )
                }
                .onFailure { showToast(it.localizedMessage ?: "Bakım modu güncellenemedi.") }
        }
    }

    fun deleteNetwork(network: GameNetwork) {
        val st = _uiState.value
        val uid = currentAuthUid ?: return
        if (st.isOffline) return
        if (network.id == "official" || network.official) {
            showToast("Resmi Network silinemez.")
            return
        }
        val canDelete = (st.activeNetworkId == network.id && st.displayRank.canDeleteNetwork) ||
            network.founderUid == uid ||
            denizRepo.isOfficialAdminEmail()
        if (!canDelete) {
            showToast("Sunucuyu silmek için Kurucu yetkisi gerekir.")
            return
        }
        viewModelScope.launch {
            denizRepo.deleteIndependentNetwork(network.id)
                .onSuccess {
                    seenActiveNetworkIds.remove(network.id)
                    if (_uiState.value.activeNetworkId == network.id) {
                        switchActiveNetworkInternal(uid, "official")
                    }
                    showToast("${network.name} sunucusu silindi.")
                }
                .onFailure { showToast(it.localizedMessage ?: "Sunucu silinemedi.") }
        }
    }

    private fun switchActiveNetworkInternal(uid: String, networkId: String, isFounder: Boolean = false) {
        networkObserversJob?.cancel()
        roomObserversJob?.cancel()
        banExpiryWatcherJob?.cancel()
        stopGameLoop()
        lastAnnouncedBanKey = null
        val prevWs = _uiState.value.worldSession
        if (prevWs.active && !_uiState.value.isOffline) {
            viewModelScope.launch { denizRepo.leaveRoom(prevWs.networkId, prevWs.roomId) }
        }
        val account = _uiState.value.userAccount
        val username = account?.username ?: "Oyuncu"
        val skin = account?.skinColor ?: 0L

        _uiState.update {
            it.copy(
                activeNetworkId = networkId,
                currentMember = null,
                bannedDisconnectNotice = null,
                worldSession = WorldSessionState(),
                roomChatMessages = emptyList()
            )
        }

        networkObserversJob = viewModelScope.launch {
            val netObj = _uiState.value.networks.firstOrNull { it.id == networkId }
            denizRepo.ensureNetworkMember(networkId, username, skin, isFounder || (netObj?.founderUid == uid))
            if (account != null && account.preferredNetworkId != networkId) {
                denizRepo.updateUserAccountCosmetics(account.username, account.skinColor, networkId)
            }
            launch {
                denizRepo.observeMember(networkId, uid).catch { }.collect { m ->
                    handleMemberSnapshot(networkId, uid, m)
                }
            }
            launch { denizRepo.observeNetworkMembers(networkId).catch { }.collect { list -> _uiState.update { it.copy(networkMembers = list) } } }
            launch { denizRepo.observeRooms(networkId).catch { }.collect { r -> _uiState.update { it.copy(rooms = r) } } }
            launch { staffRepoObserveTickets(networkId) }
            launch { staffRepoObserveReports(networkId) }
        }
    }

    private fun handleMemberSnapshot(networkId: String, uid: String, member: NetworkMember?) {
        if (_uiState.value.activeNetworkId != networkId || _uiState.value.isOffline) return
        val nowMs = System.currentTimeMillis()
        if (member != null && member.isBanActiveAt(nowMs)) {
            if (member.banUntilMillis > 0L) {
                scheduleBanExpiryWatcher(member.banUntilMillis)
            } else {
                banExpiryWatcherJob?.cancel()
            }
            evictLocalUserFromNetworkOnBan(networkId, uid, member)
        } else {
            banExpiryWatcherJob?.cancel()
            lastAnnouncedBanKey = null
            _uiState.update {
                it.copy(
                    currentMember = member,
                    bannedDisconnectNotice = null,
                    banClockMillis = nowMs
                )
            }
            val updatedState = _uiState.value
            if (updatedState.isNetworkMaintenanceBlocked && updatedState.worldSession.active) {
                leaveWorldSession()
                showToast("Sunucu bakım moduna alındı! Sadece yetkililer girebilir.")
            }
        }
    }

    private fun evictLocalUserFromNetworkOnBan(networkId: String, uid: String, member: NetworkMember) {
        val currentState = _uiState.value
        val wasInWorld = currentState.worldSession.active && currentState.worldSession.networkId == networkId
        if (wasInWorld) {
            stopGameLoop()
            roomObserversJob?.cancel()
            roomObserversJob = null
            processedInteractionIds.clear()
        }
        viewModelScope.launch {
            denizRepo.removePlayerFromAllNetworkRooms(networkId, uid, updateRoomCounts = false)
        }
        val banKey = "$networkId:${member.banUntilMillis}:${member.banReason}"
        val shouldAnnounce = wasInWorld || lastAnnouncedBanKey != banKey
        if (shouldAnnounce) {
            lastAnnouncedBanKey = banKey
        }
        val netName = currentState.networks.firstOrNull { it.id == networkId }?.name ?: currentState.activeNetwork.name
        val notice = BannedDisconnectInfo(
            networkId = networkId,
            networkName = netName,
            reason = member.banReason.ifBlank { "Kural ihlali" },
            banUntilMillis = member.banUntilMillis,
            kickedFromWorld = wasInWorld
        )
        _uiState.update { st ->
            st.copy(
                currentMember = member,
                worldSession = if (wasInWorld) WorldSessionState() else st.worldSession,
                roomChatMessages = if (wasInWorld) emptyList() else st.roomChatMessages,
                pendingNetworkWarning = null,
                bannedDisconnectNotice = if (shouldAnnounce) notice else st.bannedDisconnectNotice,
                toastMessage = if (wasInWorld) {
                    "Bu ağdan yasaklandınız! Tüm maçlardan ve lobilerden çıkarıldınız."
                } else st.toastMessage
            )
        }
    }

    private fun scheduleBanExpiryWatcher(banUntilMillis: Long) {
        banExpiryWatcherJob?.cancel()
        banExpiryWatcherJob = viewModelScope.launch {
            while (isActive) {
                val remaining = banUntilMillis - System.currentTimeMillis()
                if (remaining <= 0L) {
                    lastAnnouncedBanKey = null
                    _uiState.update {
                        it.copy(
                            banClockMillis = System.currentTimeMillis(),
                            bannedDisconnectNotice = null,
                            toastMessage = "Süreli yasağınız sona erdi."
                        )
                    }
                    break
                }
                delay(remaining.coerceAtMost(30_000L) + 100L)
            }
        }
    }

    fun dismissBannedDisconnectNotice() {
        _uiState.update { it.copy(bannedDisconnectNotice = null) }
    }

    private val staffRepoRef: StaffRepository = staffRepo
    private suspend fun staffRepoObserveTickets(networkId: String) {
        staffRepoRef.observeTickets(networkId).catch { }.collect { list -> _uiState.update { it.copy(tickets = list) } }
    }
    private suspend fun staffRepoObserveReports(networkId: String) {
        staffRepoRef.observeReports(networkId).catch { }.collect { list -> _uiState.update { it.copy(reports = list) } }
    }

    // --- PROFILE & MARKET ---
    fun cycleSkin() {
        val next = (_uiState.value.displaySkinIndex + 1) % SKIN_PALETTES.size
        if (_uiState.value.isOffline) {
            viewModelScope.launch { offlineRepo.saveOfflineProfile(_uiState.value.offlineProfile.copy(skinColor = next)) }
        } else {
            val m = _uiState.value.currentMember ?: return
            viewModelScope.launch {
                denizRepo.updateSelfProgress(m.networkId, m.coins, m.gems, m.xp, m.level, m.inventoryJson, next.toLong())
            }
        }
    }

    fun updateUsername(newName: String) {
        val clean = newName.trim().take(24)
        if (clean.length < 2) {
            showToast("İsim en az 2 karakter olmalıdır.")
            return
        }
        if (_uiState.value.isOffline) {
            viewModelScope.launch {
                offlineRepo.saveOfflineProfile(_uiState.value.offlineProfile.copy(username = clean))
                showToast("Oyuncu adı güncellendi.")
            }
        } else {
            val acc = _uiState.value.userAccount ?: return
            viewModelScope.launch {
                denizRepo.updateUserAccountCosmetics(clean, acc.skinColor, _uiState.value.activeNetworkId)
                _uiState.value.currentMember?.let { m ->
                    denizRepo.updateSelfProgress(m.networkId, m.coins, m.gems, m.xp, m.level, m.inventoryJson, m.skinColor)
                }
                showToast("İsim güncellendi.")
            }
        }
    }

    fun claimDailyBonus() {
        val state = _uiState.value
        if (!state.isOffline && state.isMemberBannedActive) {
            showToast("Bu ağdan yasaklı olduğunuz için günlük bonus alamazsınız.")
            return
        }
        if (state.isDailyBonusClaimBusy) return
        val nowMs = System.currentTimeMillis()
        val remaining = DailyBonusPolicy.remainingMillisAt(state.lastDailyBonusClaimMillis, nowMs)
        if (remaining > 0L) {
            _uiState.update { it.copy(banClockMillis = nowMs) }
            showToast("Günlük bonus zaten alındı! Tekrar almak için ${DailyBonusPolicy.formatRemainingTurkish(remaining)} beklemelisin.")
            return
        }
        _uiState.update { it.copy(isDailyBonusClaimBusy = true, banClockMillis = nowMs) }
        viewModelScope.launch {
            try {
                val latestState = _uiState.value
                if (latestState.isOffline) {
                    val p = latestState.offlineProfile
                    val offlineNow = System.currentTimeMillis()
                    val offlineRemaining = DailyBonusPolicy.remainingMillisAt(p.lastDailyBonusClaimMillis, offlineNow)
                    if (offlineRemaining > 0L) {
                        showToast("Günlük bonus zaten alındı! Tekrar almak için ${DailyBonusPolicy.formatRemainingTurkish(offlineRemaining)} beklemelisin.")
                    } else {
                        val (nextLv, nextXp) = ProgressionUtils.addXpWithLevelUp(
                            p.level,
                            p.xp,
                            DailyBonusPolicy.DAILY_XP
                        )
                        offlineRepo.saveOfflineProfile(
                            p.copy(
                                coins = p.coins + DailyBonusPolicy.DAILY_COINS,
                                gems = p.gems + DailyBonusPolicy.DAILY_GEMS,
                                level = nextLv,
                                xp = nextXp,
                                lastDailyBonusClaimMillis = offlineNow
                            )
                        )
                        _uiState.update { it.copy(banClockMillis = offlineNow) }
                        showToast("Günlük Bonus: +250 Coin, +5 Elmas, +60 XP!")
                    }
                } else {
                    val m = latestState.currentMember ?: return@launch
                    denizRepo.claimDailyBonus(
                        networkId = m.networkId,
                        coinsDelta = DailyBonusPolicy.DAILY_COINS,
                        gemsDelta = DailyBonusPolicy.DAILY_GEMS,
                        addedXp = DailyBonusPolicy.DAILY_XP
                    ).onSuccess {
                        _uiState.update { it.copy(banClockMillis = System.currentTimeMillis()) }
                        showToast("Günlük Bonus alındı: +250 Coin, +5 Elmas!")
                    }.onFailure { err ->
                        when (err) {
                            is DailyBonusCooldownException ->
                                showToast("Günlük bonus zaten alındı! Tekrar almak için ${DailyBonusPolicy.formatRemainingTurkish(err.remainingMillis)} beklemelisin.")
                            is FirebaseFirestoreException ->
                                showToast("Günlük bonus 24 saat dolmadan tekrar alınamaz.")
                            else ->
                                showToast(err.localizedMessage ?: "Günlük bonus alınamadı.")
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(isDailyBonusClaimBusy = false) }
            }
        }
    }

    fun buyMarketItem(item: GameItem) {
        val state = _uiState.value
        if (!state.isOffline && state.isMemberBannedActive) {
            showToast("Bu ağdan yasaklı olduğunuz için marketi kullanamazsınız.")
            return
        }
        if (state.displayCoins < item.priceCoins) {
            showToast("Yetersiz Coin! (${item.priceCoins} gerekli)")
            return
        }
        val addAmt = if (item.category == ItemCategory.BLOK) 16 else 1
        val newInv = ProgressionUtils.mergeItemIntoSlots(state.displayInventory, item.id, addAmt)
        if (state.isOffline) {
            viewModelScope.launch {
                offlineRepo.saveOfflineProfile(state.offlineProfile.copy(coins = state.offlineProfile.coins - item.priceCoins, inventory = newInv))
                showToast("${item.name} satın alındı!")
            }
        } else {
            val m = state.currentMember ?: return
            viewModelScope.launch {
                denizRepo.updateSelfProgress(m.networkId, m.coins - item.priceCoins, m.gems, m.xp, m.level, serializeInventorySlots(newInv), m.skinColor)
                showToast("${item.name} satın alındı!")
            }
        }
    }

    fun buyInMatchMarketItem(item: GameItem) {
        val ws = _uiState.value.worldSession
        if (ws.iron < item.priceIron || ws.gold < item.priceGold) {
            showToast("Yetersiz kaynak! (${item.priceIron} Demir, ${item.priceGold} Altın)")
            return
        }
        val addAmt = if (item.category == ItemCategory.BLOK) 16 else 1
        val newInv = ProgressionUtils.mergeItemIntoSlots(_uiState.value.displayInventory, item.id, addAmt)
        _uiState.update {
            it.copy(worldSession = ws.copy(iron = ws.iron - item.priceIron, gold = ws.gold - item.priceGold, eventMessage = "${item.name} alındı!"))
        }
        persistInventoryUpdate(newInv)
    }

    // --- 3D MATCH & ROOM SYNC ---
    fun startMatch(serverType: ServerType, mapName: String, existingRoom: GameRoom? = null) {
        val state = _uiState.value
        if (!state.isOffline && state.isMemberBannedActive) {
            showToast("Bu ağdan yasaklısınız: ${state.currentMember?.banReason}")
            return
        }
        if (!state.isOffline && state.isNetworkMaintenanceBlocked) {
            showToast("Sunucu şu anda bakım modunda! Sadece yetkililer girebilir.")
            return
        }
        if (state.isOffline) {
            val plot = when (serverType) {
                ServerType.CREATIVE -> state.offlineProfile.creativePlotData
                ServerType.SKYBLOCK -> state.offlineProfile.skyblockPlotData
                else -> ""
            }
            val s = sessionController.startSession(
                "offline_${serverType.code}", "offline", serverType, mapName, state.offlineProfile.gameMode,
                plot, isOffline = true,
                initialIron = state.offlineProfile.iron, initialGold = state.offlineProfile.gold,
                initialEmerald = state.offlineProfile.emerald, initialDiamond = state.offlineProfile.diamond
            )
            _uiState.update { it.copy(worldSession = s) }
            startGameLoop()
            return
        }
        val uid = currentAuthUid ?: return
        val netId = state.activeNetworkId
        val member = state.currentMember ?: return
        viewModelScope.launch {
            processedInteractionIds.clear()
            lastMatchmakingSyncMs = 0L
            val savedPlot = if (serverType == ServerType.CREATIVE || serverType == ServerType.SKYBLOCK) {
                denizRepo.loadPlayerPlot(netId, serverType.code)?.blocksData.orEmpty()
            } else ""
            val room = if (existingRoom != null) {
                denizRepo.joinExistingRoom(netId, existingRoom)
            } else {
                denizRepo.findOrCreateRoom(netId, serverType, mapName).getOrElse {
                    showToast("Oda başlatılamadı: ${it.localizedMessage}")
                    return@launch
                }
            }
            var s = sessionController.startSession(
                room.id, netId, serverType, room.mapName.ifBlank { mapName },
                member.gameModeEnum, savedPlot, isOffline = false, customTheme = room.theme
            )
            if (s.isWaitingPhase) {
                val pCount = room.playerCount.toInt().coerceAtLeast(1)
                val plan = MatchmakingPolicy.evaluate(
                    playerCount = pCount,
                    maxPlayers = serverType.defaultMaxPlayers,
                    currentStartAtMillis = room.matchStartAtMillis,
                    nowMillis = System.currentTimeMillis()
                )
                s = s.copy(
                    waitingPlayerCount = pCount,
                    waitingCountdownSec = plan.remainingSec,
                    isWaitingPaused = plan.phase == MatchmakingPhase.WAITING_FOR_PLAYERS
                )
                if (room.status == "PLAYING" || (plan.targetStartAtMillis > 0L && plan.remainingSec <= 0)) {
                    s = sessionController.transitionToMatchArena(s, savedPlot)
                }
            }
            _uiState.update { it.copy(worldSession = s, roomChatMessages = emptyList()) }
            syncLocalPresenceToRoom(s)
            startRoomObservers(netId, room.id, uid)
            startGameLoop()
        }
    }

    fun spectateRoom(room: GameRoom, targetPlayerName: String? = null) {
        val state = _uiState.value
        if (!state.isOffline && state.isMemberBannedActive) {
            showToast("Bu ağdan yasaklısınız: ${state.currentMember?.banReason}")
            return
        }
        if (!state.isOffline && state.isNetworkMaintenanceBlocked) {
            showToast("Sunucu şu anda bakım modunda!")
            return
        }
        if (state.isOffline || !state.displayRank.canSpectateMatch) return
        val uid = currentAuthUid ?: return
        val s = sessionController.startSession(
            room.id, room.networkId, room.serverTypeEnum, room.mapName,
            GameMode.SPECTATOR, "", isOffline = false, spectating = true, spectateTargetName = targetPlayerName
        )
        _uiState.update { it.copy(worldSession = s, roomChatMessages = emptyList()) }
        startRoomObservers(room.networkId, room.id, uid)
        startGameLoop()
    }

    private fun startRoomObservers(networkId: String, roomId: String, localUid: String) {
        roomObserversJob?.cancel()
        roomObserversJob = viewModelScope.launch {
            launch {
                denizRepo.observeRoomPlayers(networkId, roomId).catch { }.collect { players ->
                    _uiState.update { st ->
                        if (!st.worldSession.active) st else st.copy(worldSession = sessionController.mergeRemotePlayers(st.worldSession, players, localUid))
                    }
                }
            }
            launch {
                denizRepo.observeRoomBlocks(networkId, roomId).catch { }.collect { blocks ->
                    _uiState.update { st ->
                        if (!st.worldSession.active) st else st.copy(worldSession = sessionController.mergeRemoteBlocks(st.worldSession, blocks))
                    }
                }
            }
            launch {
                denizRepo.observeRoomChat(networkId, roomId).catch { }.collect { msgs -> _uiState.update { it.copy(roomChatMessages = msgs) } }
            }
            launch {
                denizRepo.observeRoomInteractions(networkId, roomId).catch { }.collect { interactions ->
                    interactions.filter { it.targetUid == localUid && it.id.isNotBlank() && processedInteractionIds.add(it.id) }
                        .forEach { interaction ->
                            handleIncomingInteraction(networkId, roomId, interaction)
                        }
                }
            }
        }
    }

    private fun handleIncomingInteraction(networkId: String, roomId: String, interaction: RoomInteraction) {
        viewModelScope.launch { denizRepo.consumeRoomInteraction(networkId, roomId, interaction.id) }
        val st = _uiState.value
        val ws = st.worldSession
        if (!ws.active) return
        when (interaction.actionType) {
            "HIT" -> {
                val attacker = ws.remotePlayers.firstOrNull { it.userId == interaction.fromUid }
                val dx = if (attacker != null) ws.camera.x - attacker.x.toFloat() else 0.5f
                val dz = if (attacker != null) ws.camera.z - attacker.z.toFloat() else 0.5f
                val len = kotlin.math.hypot(dx, dz).coerceAtLeast(0.01f)
                val nextWs = sessionController.applyPlayerDamage(
                    state = ws,
                    damage = interaction.amount.toInt().coerceAtLeast(1),
                    sourceName = interaction.fromName,
                    knockX = (dx / len) * 0.45f,
                    knockZ = (dz / len) * 0.45f
                )
                _uiState.update { it.copy(worldSession = nextWs) }
            }
            "GIFT_COIN" -> {
                val coinsAmt = interaction.amount.toInt().coerceAtLeast(1)
                grantRewards(coinsAmt, 0)
                _uiState.update {
                    it.copy(worldSession = it.worldSession.copy(eventMessage = "${interaction.fromName} sana +$coinsAmt Coin hediye etti!"))
                }
            }
            "GIFT_ITEM" -> {
                val itemAmt = interaction.amount.toInt().coerceAtLeast(1)
                val itemName = GameCatalog.findById(interaction.itemId)?.name ?: interaction.itemId
                val nextInv = ProgressionUtils.mergeItemIntoSlots(st.displayInventory, interaction.itemId, itemAmt)
                persistInventoryUpdate(nextInv)
                _uiState.update {
                    it.copy(worldSession = it.worldSession.copy(eventMessage = "${interaction.fromName} sana $itemAmt x $itemName verdi!"))
                }
            }
            "WAVE" -> {
                _uiState.update {
                    it.copy(worldSession = it.worldSession.copy(eventMessage = "${interaction.fromName} sana el salladı!"))
                }
            }
        }
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        syncAccumulator = 0f
        gameLoopJob = viewModelScope.launch {
            var lastNs = System.nanoTime()
            while (isActive) {
                delay(33L)
                val now = System.nanoTime()
                val dt = ((now - lastNs) / 1_000_000_000.0).toFloat()
                lastNs = now
                val st = _uiState.value
                val ws = st.worldSession
                if (!ws.active) break
                val plot = if (st.isOffline) {
                    if (ws.serverType == ServerType.CREATIVE) st.offlineProfile.creativePlotData else st.offlineProfile.skyblockPlotData
                } else ""
                val steppedWs = sessionController.stepFrame(
                    current = ws,
                    dt = dt,
                    moveForward = moveForwardInput,
                    moveStrafe = moveStrafeInput,
                    flyUp = flyUpInput,
                    flyDown = flyDownInput,
                    savedPlotData = plot,
                    onMatchRewards = { c, x -> grantRewards(c, x) },
                    onMatchEnded = { won ->
                        if (!st.isOffline && ws.roomId.isNotBlank()) {
                            viewModelScope.launch {
                                denizRepo.updateRoomMatchmaking(
                                    ws.networkId,
                                    ws.roomId,
                                    "FINISHED",
                                    0L,
                                    if (won) st.displayName else ""
                                )
                            }
                        }
                    },
                    onAutoReturnToLobby = {
                        viewModelScope.launch { returnTo3DLobbyFromMatch() }
                    }
                )
                var updatedWs = steppedWs
                if (!st.isOffline && updatedWs.isWaitingPhase) {
                    val room = st.rooms.firstOrNull { it.id == updatedWs.roomId }
                    if (room != null && room.status == "PLAYING") {
                        updatedWs = sessionController.transitionToMatchArena(updatedWs)
                    } else {
                        val nowMs = System.currentTimeMillis()
                        val activeCount = maxOf(
                            updatedWs.remotePlayers.count { it.alive },
                            room?.playerCount?.toInt() ?: 1,
                            1
                        )
                        val plan = MatchmakingPolicy.evaluate(
                            playerCount = activeCount,
                            maxPlayers = updatedWs.maxWaitingPlayers,
                            currentStartAtMillis = room?.matchStartAtMillis ?: 0L,
                            nowMillis = nowMs
                        )
                        val waitMsg = when (plan.phase) {
                            MatchmakingPhase.WAITING_FOR_PLAYERS ->
                                "Oyuncular bekleniyor ($activeCount/${updatedWs.maxWaitingPlayers}) — En az 2 oyuncu gerekli"
                            MatchmakingPhase.FULL_ROOM_3S ->
                                "Oda Dolu! Maç ${plan.remainingSec}s içinde başlıyor!"
                            MatchmakingPhase.COUNTDOWN_30S ->
                                "Eşleştirme ($activeCount/${updatedWs.maxWaitingPlayers}) • Maça ${plan.remainingSec}s"
                        }
                        updatedWs = updatedWs.copy(
                            waitingPlayerCount = activeCount,
                            waitingCountdownSec = plan.remainingSec,
                            isWaitingPaused = plan.phase == MatchmakingPhase.WAITING_FOR_PLAYERS,
                            eventMessage = waitMsg
                        )
                        if (room != null && (nowMs - lastMatchmakingSyncMs) >= 850L) {
                            val uid = currentAuthUid
                            val canDriveRoom = uid != null && (room.hostUid == uid || room.matchStartAtMillis == 0L || activeCount >= updatedWs.maxWaitingPlayers)
                            if (plan.targetStartAtMillis > 0L && plan.remainingSec <= 0) {
                                lastMatchmakingSyncMs = nowMs
                                if (canDriveRoom) {
                                    viewModelScope.launch {
                                        denizRepo.updateRoomMatchmaking(updatedWs.networkId, updatedWs.roomId, "PLAYING", 0L)
                                    }
                                }
                                updatedWs = sessionController.transitionToMatchArena(updatedWs)
                            } else if (room.matchStartAtMillis != plan.targetStartAtMillis && canDriveRoom) {
                                lastMatchmakingSyncMs = nowMs
                                viewModelScope.launch {
                                    denizRepo.updateRoomMatchmaking(updatedWs.networkId, updatedWs.roomId, "WAITING", plan.targetStartAtMillis)
                                }
                            }
                        }
                    }
                }
                _uiState.update { it.copy(worldSession = updatedWs) }
                syncAccumulator += dt
                if (!st.isOffline && syncAccumulator >= 0.35f) {
                    syncAccumulator = 0f
                    syncLocalPresenceToRoom(updatedWs)
                }
            }
        }
    }

    private fun stopGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = null
        moveForwardInput = 0f
        moveStrafeInput = 0f
        flyUpInput = false
        flyDownInput = false
    }

    private fun syncLocalPresenceToRoom(ws: WorldSessionState) {
        val uid = currentAuthUid ?: return
        val st = _uiState.value
        if (!st.isOffline && st.isMemberBannedActive) return
        val m = st.currentMember ?: return
        val rp = RoomPlayer(
            userId = uid, roomId = ws.roomId, networkId = ws.networkId, username = m.username,
            rank = m.rank, level = m.level, team = if (ws.serverType == ServerType.BEDWARS) "MAVI" else "NONE",
            x = ws.camera.x.toDouble(), y = (ws.camera.y - 1.55f).toDouble(), z = ws.camera.z.toDouble(),
            yaw = ws.camera.yawDeg.toDouble(), hp = ws.hp.toLong(), alive = ws.hp > 0 && ws.gameMode != GameMode.SPECTATOR,
            skinColor = m.skinColor
        )
        viewModelScope.launch { denizRepo.syncLocalPlayerInRoom(ws.networkId, ws.roomId, rp) }
    }

    fun leaveWorldSession() {
        val st = _uiState.value
        val ws = st.worldSession
        stopGameLoop()
        roomObserversJob?.cancel()
        if (ws.active && (ws.serverType == ServerType.CREATIVE || ws.serverType == ServerType.SKYBLOCK) && (st.isOffline || !st.isMemberBannedActive)) {
            saveCurrentPlot()
        }
        if (!st.isOffline && ws.active) viewModelScope.launch { denizRepo.leaveRoom(ws.networkId, ws.roomId) }
        _uiState.update { it.copy(worldSession = WorldSessionState(), roomChatMessages = emptyList()) }
    }

    fun returnTo3DLobbyFromMatch() {
        val st = _uiState.value
        if (!st.worldSession.active) return
        leaveWorldSession()
        if (st.isOffline || !st.isNetworkMaintenanceBlocked) {
            startMatch(ServerType.LOBI, ServerType.LOBI.matchMaps.first())
        }
    }

    fun continueSpectatingAfterLoss() {
        _uiState.update { it.copy(worldSession = sessionController.dismissMatchResultToSpectate(it.worldSession)) }
    }

    fun saveCurrentPlot() {
        val st = _uiState.value
        val ws = st.worldSession
        if (!ws.active) return
        if (!st.isOffline && st.isMemberBannedActive) {
            showToast("Bu ağdan yasaklı olduğunuz için arsa kaydedemezsiniz.")
            return
        }
        val serialized = VoxelMapGenerator.serializePlot(sessionController.blocks)
        if (st.isOffline) {
            val updated = if (ws.serverType == ServerType.CREATIVE) st.offlineProfile.copy(creativePlotData = serialized)
            else st.offlineProfile.copy(skyblockPlotData = serialized)
            viewModelScope.launch {
                offlineRepo.saveOfflineProfile(updated)
                showToast("Arsa / Ada cihazına kaydedildi!")
            }
        } else {
            viewModelScope.launch {
                denizRepo.savePlayerPlot(ws.networkId, ws.serverType.code, "${st.displayName} Arsası", serialized, (sessionController.blocks.size / 10L).coerceAtLeast(1L))
                showToast("Arsa / Ada hesabına buluta kaydedildi!")
            }
        }
    }

    // --- WORLD INPUTS ---
    fun jumpInWorld() {
        val now = System.currentTimeMillis()
        val ws = _uiState.value.worldSession
        val canFly = ws.gameMode == GameMode.CREATIVE || ws.gameMode == GameMode.SPECTATOR
        if (canFly && (now - lastJumpTapMs) <= 340L) {
            lastJumpTapMs = 0L
            _uiState.update { it.copy(worldSession = sessionController.toggleFlying(it.worldSession)) }
        } else {
            lastJumpTapMs = now
            _uiState.update { it.copy(worldSession = sessionController.jump(it.worldSession)) }
        }
    }

    fun lookInWorld(dx: Float, dy: Float) = _uiState.update { it.copy(worldSession = sessionController.lookDelta(it.worldSession, dx, dy)) }
    fun selectHotbarSlot(index: Int) = _uiState.update { it.copy(worldSession = it.worldSession.copy(selectedSlotIndex = index.coerceIn(0, 8))) }
    fun dismissWorldModal() = _uiState.update { it.copy(worldSession = it.worldSession.copy(activeModal = null, interactTargetEntity = null)) }

    fun openInventoryOrCreativeMenu() {
        val ws = _uiState.value.worldSession
        if (!ws.active) return
        val modal = if (ws.gameMode == GameMode.CREATIVE) WorldModalType.CREATIVE_MENU else WorldModalType.INVENTORY
        _uiState.update { it.copy(worldSession = it.worldSession.copy(activeModal = modal)) }
    }

    fun swapInventorySlots(fromIdx: Int, toIdx: Int) {
        val nextInv = swapInventoryIndices(_uiState.value.displayInventory, fromIdx, toIdx)
        persistInventoryUpdate(nextInv)
    }

    fun setCreativeHotbarItem(item: GameItem, slotIdx: Int, amount: Int? = null) {
        val amt = amount ?: if (item.category == ItemCategory.BLOK || item.category == ItemCategory.YIYECEK || item.category == ItemCategory.OZEL) 64 else 1
        val targetSlot = slotIdx.coerceIn(0, 8)
        val nextInv = putItemAtHotbarSlot(_uiState.value.displayInventory, targetSlot, item.id, amt)
        persistInventoryUpdate(nextInv)
        _uiState.update {
            it.copy(
                worldSession = it.worldSession.copy(
                    selectedSlotIndex = targetSlot.coerceAtMost((nextInv.size - 1).coerceAtLeast(0)),
                    eventMessage = "${item.name} ($amt) Hotbar ${targetSlot + 1}. slota eklendi!"
                )
            )
        }
    }

    fun clearHotbarSlot(slotIdx: Int) {
        val cur = _uiState.value.displayInventory
        if (slotIdx !in cur.indices) return
        val nextInv = cur.toMutableList().apply { removeAt(slotIdx) }
        persistInventoryUpdate(nextInv)
        _uiState.update {
            val nextSel = it.worldSession.selectedSlotIndex.coerceIn(0, (nextInv.size - 1).coerceAtLeast(0))
            it.copy(worldSession = it.worldSession.copy(selectedSlotIndex = nextSel, eventMessage = "Slot temizlendi"))
        }
    }

    fun interactWithPlayer(target: WorldEntity, actionType: String, customReason: String = "Oyun içi rapor") {
        val st = _uiState.value
        val ws = st.worldSession
        if (!ws.active) return
        val targetUid = target.id.removePrefix("player_")
        val isOnlinePlayer = target.kind == EntityKind.PLAYER && !st.isOffline

        when (actionType) {
            "WAVE" -> {
                if (isOnlinePlayer) {
                    viewModelScope.launch {
                        denizRepo.sendRoomInteraction(ws.networkId, ws.roomId, st.displayName, targetUid, "WAVE")
                    }
                }
                _uiState.update {
                    it.copy(worldSession = ws.copy(activeModal = null, interactTargetEntity = null, eventMessage = "${target.name} oyuncusuna el salladın!"))
                }
            }
            "GIFT_COIN" -> {
                if (st.displayCoins < 25L) {
                    showToast("Hediye göndermek için en az 25 Coin gerekli!")
                    return
                }
                grantRewards(-25, 0)
                if (isOnlinePlayer) {
                    viewModelScope.launch {
                        denizRepo.sendRoomInteraction(ws.networkId, ws.roomId, st.displayName, targetUid, "GIFT_COIN", amount = 25L)
                    }
                }
                _uiState.update {
                    it.copy(worldSession = ws.copy(activeModal = null, interactTargetEntity = null, eventMessage = "${target.name} oyuncusuna +25 Coin hediye edildi!"))
                }
            }
            "GIFT_ITEM" -> {
                val heldSlot = st.displayInventory.getOrNull(ws.selectedSlotIndex)
                if (heldSlot == null || heldSlot.count <= 0) {
                    showToast("Paylaşmak için hotbar'dan bir eşya seç!")
                    return
                }
                val itemName = GameCatalog.findById(heldSlot.itemId)?.name ?: heldSlot.itemId
                if (ws.gameMode != GameMode.CREATIVE) {
                    val nextInv = st.displayInventory.mapIndexedNotNull { idx, slot ->
                        if (idx == ws.selectedSlotIndex) {
                            if (slot.count > 1) slot.copy(count = slot.count - 1) else null
                        } else slot
                    }
                    persistInventoryUpdate(nextInv)
                }
                if (isOnlinePlayer) {
                    viewModelScope.launch {
                        denizRepo.sendRoomInteraction(ws.networkId, ws.roomId, st.displayName, targetUid, "GIFT_ITEM", amount = 1L, itemId = heldSlot.itemId)
                    }
                }
                _uiState.update {
                    it.copy(worldSession = ws.copy(activeModal = null, interactTargetEntity = null, eventMessage = "${target.name} oyuncusuna $itemName verildi!"))
                }
            }
            "HIT" -> {
                val heldItem = st.displayInventory.getOrNull(ws.selectedSlotIndex)?.let { GameCatalog.findById(it.itemId) }
                val dmg = heldItem?.attackDamage ?: 2
                if (isOnlinePlayer) {
                    viewModelScope.launch {
                        denizRepo.sendRoomInteraction(ws.networkId, ws.roomId, st.displayName, targetUid, "HIT", amount = dmg.toLong())
                    }
                }
                val updated = ws.entities.map {
                    if (it.id == target.id) {
                        val nextHp = if (target.kind == EntityKind.BOT) (it.hp - dmg).coerceAtLeast(0) else it.hp
                        it.copy(hp = nextHp, alive = nextHp > 0 || target.kind == EntityKind.PLAYER, hurtFlashTimer = 0.35f)
                    } else it
                }
                _uiState.update {
                    it.copy(worldSession = ws.copy(entities = updated, activeModal = null, interactTargetEntity = null, eventMessage = "${target.name}: -$dmg ♥"))
                }
            }
            "REPORT" -> {
                if (isOnlinePlayer) {
                    staffActions.submitPlayerReportByUid(targetUid, target.name, target.rank, customReason)
                } else {
                    showToast("${target.name} botu hakkında not alındı.")
                }
                _uiState.update {
                    it.copy(worldSession = ws.copy(activeModal = null, interactTargetEntity = null))
                }
            }
        }
    }

    fun primaryActionInWorld() {
        val st = _uiState.value
        val ws = st.worldSession
        val heldItem = st.displayInventory.getOrNull(ws.selectedSlotIndex)?.let { GameCatalog.findById(it.itemId) }
        val canBreakProtected = st.displayRank.canBuildAndDesign || (st.isOffline && ws.gameMode == GameMode.CREATIVE)
        val next = sessionController.handlePrimaryAction(
            ws, heldItem,
            canBreakProtectedBlocks = canBreakProtected,
            onBlockBroken = { c, _ -> if (!st.isOffline && !ws.isWaitingPhase) viewModelScope.launch { denizRepo.syncRoomBlock(ws.networkId, ws.roomId, c, BlockType.AIR) } },
            onEntityReward = { coins, xp -> grantRewards(coins, xp) },
            onPortalTrigger = { srv -> leaveWorldSession(); startMatch(srv, srv.matchMaps.first()) },
            onPlayerHit = { targetEntity, dmg ->
                if (!st.isOffline) {
                    val targetUid = targetEntity.id.removePrefix("player_")
                    viewModelScope.launch {
                        denizRepo.sendRoomInteraction(ws.networkId, ws.roomId, st.displayName, targetUid, "HIT", amount = dmg.toLong())
                    }
                }
            }
        )
        _uiState.update { it.copy(worldSession = next) }
    }

    fun secondaryActionInWorld() {
        val st = _uiState.value
        val ws = st.worldSession
        val heldItem = st.displayInventory.getOrNull(ws.selectedSlotIndex)?.let { GameCatalog.findById(it.itemId) }
        val rank = st.displayRank
        val canBuildInWorld = rank.canBuildAndDesign || (st.isOffline && ws.gameMode == GameMode.CREATIVE)
        val next = sessionController.handleSecondaryAction(
            ws, heldItem, canBuildInWorld, rank.hasFullDesignTools,
            onBlockPlaced = { c, bt -> if (!st.isOffline && !ws.isWaitingPhase) viewModelScope.launch { denizRepo.syncRoomBlock(ws.networkId, ws.roomId, c, bt) } },
            onConsumeInventoryItem = { id ->
                persistInventoryUpdate(st.displayInventory.mapNotNull { slot -> if (slot.itemId == id && slot.count > 1) slot.copy(count = slot.count - 1) else if (slot.itemId == id) null else slot })
            },
            onChestLooted = { loot, coins ->
                var inv = st.displayInventory
                loot.forEach { slot -> inv = ProgressionUtils.mergeItemIntoSlots(inv, slot.itemId, slot.count) }
                persistInventoryUpdate(inv)
                grantRewards(coins, 30)
            },
            onPortalTrigger = { srv -> leaveWorldSession(); startMatch(srv, srv.matchMaps.first()) }
        )
        _uiState.update { it.copy(worldSession = next) }
    }

    fun submitBuildBattleVote(score: Int) {
        val bonus = score * 20
        grantRewards(bonus, score * 10)
        _uiState.update { st ->
            val ws = st.worldSession
            if (!st.isOffline && ws.roomId.isNotBlank()) {
                viewModelScope.launch {
                    denizRepo.updateRoomMatchmaking(ws.networkId, ws.roomId, "FINISHED", 0L, st.displayName)
                }
            }
            st.copy(
                worldSession = ws.copy(
                    activeModal = null,
                    isVotingPhase = false,
                    winnerName = "Puanın: $score ★ (+$bonus Coin!)",
                    matchResult = MatchResultState(
                        outcome = MatchOutcome.WON,
                        title = "OYLAMA TAMAMLANDI!",
                        subtitle = "Yapı Puanın: $score ★ (+$bonus Coin)",
                        returnCountdownSec = 8
                    )
                )
            )
        }
    }

    fun sendRoomChat(text: String) {
        val st = _uiState.value
        if (st.isOffline) { showToast("Botlarla sohbet: \"$text\""); return }
        if (st.isMemberBannedActive) { showToast("Bu ağdan yasaklısınız!"); return }
        if (st.isMemberMutedActive) { showToast("Susturuldunuz! Sebep: ${st.currentMember?.muteReason}"); return }
        val m = st.currentMember ?: return
        viewModelScope.launch {
            denizRepo.sendRoomChatMessage(st.worldSession.networkId, st.worldSession.roomId, m, text)
                .onFailure { showToast(it.localizedMessage ?: "Mesaj gönderilemedi.") }
        }
    }

    // --- OFFLINE-ONLY SANDBOX ---
    fun offlineSetGameMode(mode: GameMode) {
        if (!_uiState.value.isOffline) return
        viewModelScope.launch {
            offlineRepo.saveOfflineProfile(_uiState.value.offlineProfile.copy(gameMode = mode))
            _uiState.update { it.copy(worldSession = it.worldSession.copy(gameMode = mode, eventMessage = "Mod: ${mode.displayName}")) }
        }
    }

    fun offlineEditResources(coins: Long, gems: Long, iron: Int, gold: Int, emerald: Int, level: Int) {
        if (!_uiState.value.isOffline) return
        val p = _uiState.value.offlineProfile
        viewModelScope.launch {
            offlineRepo.saveOfflineProfile(p.copy(coins = coins.coerceAtLeast(0L), gems = gems.coerceAtLeast(0L), iron = iron.coerceAtLeast(0), gold = gold.coerceAtLeast(0), emerald = emerald.coerceAtLeast(0), level = level.coerceIn(1, 40)))
            _uiState.update { it.copy(worldSession = it.worldSession.copy(iron = iron.coerceAtLeast(0), gold = gold.coerceAtLeast(0), emerald = emerald.coerceAtLeast(0))) }
            showToast("Yerel kaynaklar güncellendi.")
        }
    }

    fun offlineEditInventoryItem(itemId: String, delta: Int) {
        if (!_uiState.value.isOffline) return
        val nextInv = if (delta >= 0) ProgressionUtils.mergeItemIntoSlots(_uiState.value.offlineProfile.inventory, itemId, delta)
        else _uiState.value.offlineProfile.inventory.mapNotNull { if (it.itemId == itemId && it.count + delta > 0) it.copy(count = it.count + delta) else if (it.itemId == itemId) null else it }
        viewModelScope.launch { offlineRepo.saveOfflineProfile(_uiState.value.offlineProfile.copy(inventory = nextInv)) }
    }

    fun offlineSummonMob(mobType: MobType) {
        if (_uiState.value.isOffline && _uiState.value.worldSession.active) {
            _uiState.update { it.copy(worldSession = sessionController.summonMob(it.worldSession, mobType)) }
        }
    }

    fun offlineKillMobs(mobType: MobType?) {
        if (_uiState.value.isOffline && _uiState.value.worldSession.active) {
            _uiState.update { it.copy(worldSession = sessionController.killAllMobs(it.worldSession, mobType)) }
        }
    }

    // --- DELEGATED STAFF / SUPPORT / REPORT ---
    fun submitSupportTicket(subject: String, message: String) = staffActions.submitSupportTicket(subject, message)
    fun submitPlayerReport(target: NetworkMember, reason: String) = staffActions.submitPlayerReport(target, reason)
    fun staffReplyTicket(ticket: SupportTicket, replyText: String) = staffActions.replyTicket(ticket, replyText)
    fun staffReviewReport(report: PlayerReport, accepted: Boolean) = staffActions.reviewReport(report, accepted)
    fun staffBanPlayer(target: NetworkMember, banned: Boolean, reason: String, mins: Long?) = staffActions.banPlayer(target, banned, reason, mins)
    fun staffMutePlayer(target: NetworkMember, muted: Boolean, reason: String, mins: Long?) = staffActions.mutePlayer(target, muted, reason, mins)
    fun staffWarnPlayer(target: NetworkMember, reason: String) = staffActions.warnPlayer(target, reason)
    fun staffAssignRank(target: NetworkMember, newRank: Rank) = staffActions.assignRank(target, newRank)
    fun staffSetGameMode(target: NetworkMember, mode: GameMode) = staffActions.setGameMode(target, mode) { m ->
        if (target.userId == currentAuthUid && _uiState.value.worldSession.active) {
            _uiState.update { it.copy(worldSession = it.worldSession.copy(gameMode = m)) }
        }
    }
    fun staffModifyMemberStats(target: NetworkMember, action: StaffResourceAction, coins: Long?, gems: Long?, level: Long?, add: Pair<String, Int>? = null, remove: Pair<String, Int>? = null) =
        staffActions.modifyMemberStats(target, action, coins, gems, level, add, remove)

    fun staffManageWorldMobs(mobType: MobType, spawn: Boolean) {
        val st = _uiState.value
        if (st.isOffline || !st.displayRank.canAccessManagementTab) return
        if (!st.worldSession.active) { showToast("Yaratık yönetimi için önce bir maç odasında olmalısın."); return }
        _uiState.update { it.copy(worldSession = if (spawn) sessionController.summonMob(it.worldSession, mobType) else sessionController.killAllMobs(it.worldSession, mobType)) }
    }

    private fun grantRewards(coinsDelta: Int, xpDelta: Int) {
        val st = _uiState.value
        val (nextLv, nextXp) = ProgressionUtils.addXpWithLevelUp(st.displayLevel, st.displayXp, xpDelta.toLong())
        if (st.isOffline) {
            val p = st.offlineProfile
            viewModelScope.launch { offlineRepo.saveOfflineProfile(p.copy(coins = p.coins + coinsDelta, level = nextLv, xp = nextXp)) }
        } else {
            val m = st.currentMember ?: return
            viewModelScope.launch { denizRepo.updateSelfProgress(m.networkId, m.coins + coinsDelta, m.gems, nextXp, nextLv.toLong(), m.inventoryJson, m.skinColor) }
        }
    }

    private fun persistInventoryUpdate(slots: List<InventorySlot>) {
        val st = _uiState.value
        if (st.isOffline) {
            viewModelScope.launch { offlineRepo.saveOfflineProfile(st.offlineProfile.copy(inventory = slots)) }
        } else {
            val m = st.currentMember ?: return
            viewModelScope.launch { denizRepo.updateSelfProgress(m.networkId, m.coins, m.gems, m.xp, m.level, serializeInventorySlots(slots), m.skinColor) }
        }
    }

    fun showToast(msg: String) = _uiState.update { it.copy(toastMessage = msg) }
    fun clearToast() = _uiState.update { it.copy(toastMessage = null) }

    override fun onCleared() { super.onCleared(); stopGameLoop() }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appCtx = context.applicationContext
            return MainViewModel(DenizCraftRepository(appCtx), StaffRepository(appCtx), OfflineRepository(appCtx), ConnectivityObserver(appCtx)) as T
        }
    }
}
