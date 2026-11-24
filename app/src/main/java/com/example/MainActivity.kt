package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.authStateFlow
import com.example.model.ServerType
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.viewmodel.AppEntryMode
import com.example.viewmodel.BannedDisconnectInfo
import com.example.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DenizCraftTheme {
                DenizCraftRootApp()
            }
        }
    }
}

@Composable
private fun DenizCraftRootApp() {
    val authUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val context = LocalContext.current
    val mainViewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(context)
    )
    key(authUser?.uid ?: "unauthenticated_or_offline") {
        DenizCraftNavigationHost(viewModel = mainViewModel)
    }
}

@Composable
private fun DenizCraftNavigationHost(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showGameSelect by remember { mutableStateOf(false) }
    var showNetworkManager by remember { mutableStateOf(false) }
    var showStaffPanel by remember { mutableStateOf(false) }
    var showOfflineSandbox by remember { mutableStateOf(false) }
    var showMarket by remember { mutableStateOf(false) }
    var showSupportReport by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var showBluetooth by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    LaunchedEffect(uiState.isMemberBannedActive, uiState.isNetworkMaintenanceBlocked) {
        if (uiState.isMemberBannedActive || uiState.isNetworkMaintenanceBlocked) {
            showGameSelect = false
            showMarket = false
            showSupportReport = false
            if (uiState.isMemberBannedActive) {
                showStaffPanel = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.worldSession.active -> {
                    GameWorldScreen(
                        uiState = uiState,
                        sessionController = viewModel.sessionController,
                        onMoveInput = { fwd, str ->
                            viewModel.moveForwardInput = fwd
                            viewModel.moveStrafeInput = str
                        },
                        onFlyInput = { up, down ->
                            viewModel.flyUpInput = up
                            viewModel.flyDownInput = down
                        },
                        onLookDelta = { dx, dy -> viewModel.lookInWorld(dx, dy) },
                        onJump = { viewModel.jumpInWorld() },
                        onPrimaryAction = { viewModel.primaryActionInWorld() },
                        onSecondaryAction = { viewModel.secondaryActionInWorld() },
                        onSelectSlot = { idx -> viewModel.selectHotbarSlot(idx) },
                        onOpenInventory = { viewModel.openInventoryOrCreativeMenu() },
                        onSwapInventorySlots = { from, to -> viewModel.swapInventorySlots(from, to) },
                        onCreativeSetSlot = { item, slotIdx -> viewModel.setCreativeHotbarItem(item, slotIdx) },
                        onClearSlot = { slotIdx -> viewModel.clearHotbarSlot(slotIdx) },
                        onPlayerInteract = { target, act, reason -> viewModel.interactWithPlayer(target, act, reason) },
                        onSavePlot = { viewModel.saveCurrentPlot() },
                        onOpenStaffPanel = {
                            if (!uiState.isOffline) showStaffPanel = true
                        },
                        onOpenOfflineSandbox = {
                            if (uiState.isOffline) showOfflineSandbox = true
                        },
                        onSendChat = { txt -> viewModel.sendRoomChat(txt) },
                        onBuyMatchItem = { item -> viewModel.buyInMatchMarketItem(item) },
                        onClaimBankBonus = { viewModel.claimDailyBonus() },
                        onVoteBuildBattle = { score -> viewModel.submitBuildBattleVote(score) },
                        onDismissWorldModal = { viewModel.dismissWorldModal() },
                        onReturnTo3DLobby = { viewModel.returnTo3DLobbyFromMatch() },
                        onContinueSpectating = { viewModel.continueSpectatingAfterLoss() },
                        onLeaveWorld = { viewModel.leaveWorldSession() }
                    )
                }

                uiState.entryMode == AppEntryMode.AUTH -> {
                    AuthAndEntryScreen(
                        uiState = uiState,
                        onSilentSignInCheck = { viewModel.onSilentSignInCheck(context) },
                        onGoogleSignIn = { viewModel.startInteractiveSignIn(context) },
                        onRequestOfflineMode = { viewModel.requestOfflineModeSwitch() },
                        onConfirmOfflineMode = { viewModel.confirmEnterOfflineMode() },
                        onDismissOfflineWarning = { viewModel.dismissOfflineWarning() }
                    )
                }

                else -> {
                    LobbyHubScreen(
                        uiState = uiState,
                        onCycleSkin = { viewModel.cycleSkin() },
                        onUpdateUsername = { name -> viewModel.updateUsername(name) },
                        onOpenGameSelect = { showGameSelect = true },
                        onQuickEnter3DLobby = {
                            viewModel.startMatch(
                                ServerType.LOBI,
                                ServerType.LOBI.matchMaps.first(),
                                null
                            )
                        },
                        onOpenMarket = { showMarket = true },
                        onOpenNetworks = { if (!uiState.isOffline) showNetworkManager = true },
                        onOpenStaffPanel = { if (!uiState.isOffline) showStaffPanel = true },
                        onOpenOfflineSandbox = { if (uiState.isOffline) showOfflineSandbox = true },
                        onOpenSupportReport = { if (!uiState.isOffline) showSupportReport = true },
                        onOpenLeaderboard = { if (!uiState.isOffline) showLeaderboard = true },
                        onOpenBluetooth = { showBluetooth = true },
                        onClaimDailyBonus = { viewModel.claimDailyBonus() },
                        onRequestOfflineMode = { viewModel.requestOfflineModeSwitch() },
                        onExitOfflineOrSignOut = {
                            if (uiState.isOffline) {
                                viewModel.exitOfflineModeToAuth()
                            } else {
                                viewModel.signOut(context)
                            }
                        }
                    )
                }
            }
        }
    }

    // --- GLOBAL WARNING DIALOGS ---
    if (uiState.pendingOfflineWarning && uiState.entryMode != AppEntryMode.AUTH) {
        OfflineModeWarningDialog(
            onConfirm = { viewModel.confirmEnterOfflineMode() },
            onDismiss = { viewModel.dismissOfflineWarning() }
        )
    }

    uiState.pendingNetworkWarning?.let { targetNet ->
        IndependentNetworkWarningDialog(
            targetNetwork = targetNet,
            onConfirm = { viewModel.confirmSwitchIndependentNetwork() },
            onDismiss = { viewModel.dismissNetworkWarning() }
        )
    }

    // --- FEATURE DIALOGS ---
    if (showGameSelect) {
        GameSelectDialog(
            uiState = uiState,
            onDismiss = { showGameSelect = false },
            onStartMatch = { srv, map, room -> viewModel.startMatch(srv, map, room) },
            onSpectateRoom = { room -> viewModel.spectateRoom(room) }
        )
    }

    if (showNetworkManager && !uiState.isOffline) {
        NetworkManagerDialog(
            uiState = uiState,
            onDismiss = { showNetworkManager = false },
            onSelectNetwork = { net -> viewModel.requestSwitchNetwork(net) },
            onCreateIndependentNetwork = { n, d, p -> viewModel.createAndJoinIndependentNetwork(n, d, p) },
            onToggleMaintenance = { net -> viewModel.toggleNetworkMaintenance(net) },
            onDeleteNetwork = { net -> viewModel.deleteNetwork(net) }
        )
    }

    if (showStaffPanel && !uiState.isOffline) {
        StaffPanelDialog(
            uiState = uiState,
            onDismiss = { showStaffPanel = false },
            onReplyTicket = { t, r -> viewModel.staffReplyTicket(t, r) },
            onReviewReport = { rep, acc -> viewModel.staffReviewReport(rep, acc) },
            onBanPlayer = { m, b, r, mins -> viewModel.staffBanPlayer(m, b, r, mins) },
            onMutePlayer = { m, mut, r, mins -> viewModel.staffMutePlayer(m, mut, r, mins) },
            onWarnPlayer = { m, r -> viewModel.staffWarnPlayer(m, r) },
            onSpectateRoom = { r -> viewModel.spectateRoom(r) },
            onAssignRank = { m, r -> viewModel.staffAssignRank(m, r) },
            onSetGameMode = { m, gm -> viewModel.staffSetGameMode(m, gm) },
            onModifyStats = { m, act, c, g, l, add, rem ->
                viewModel.staffModifyMemberStats(m, act, c, g, l, add, rem)
            },
            onManageMobs = { mob, spawn -> viewModel.staffManageWorldMobs(mob, spawn) },
            onToggleNetworkMaintenance = { net -> viewModel.toggleNetworkMaintenance(net) },
            onDeleteNetwork = { net -> viewModel.deleteNetwork(net) }
        )
    }

    if (showOfflineSandbox && uiState.isOffline) {
        OfflineSandboxDialog(
            uiState = uiState,
            inWorld = uiState.worldSession.active,
            onDismiss = { showOfflineSandbox = false },
            onSetGameMode = { mode -> viewModel.offlineSetGameMode(mode) },
            onEditResources = { c, g, i, go, em, lv ->
                viewModel.offlineEditResources(c, g, i, go, em, lv)
            },
            onEditInventoryItem = { id, delta -> viewModel.offlineEditInventoryItem(id, delta) },
            onSummonMob = { mob -> viewModel.offlineSummonMob(mob) },
            onKillMobs = { mob -> viewModel.offlineKillMobs(mob) }
        )
    }

    if (showMarket) {
        MarketDialog(
            uiState = uiState,
            onDismiss = { showMarket = false },
            onBuyItem = { item -> viewModel.buyMarketItem(item) }
        )
    }

    if (showSupportReport && !uiState.isOffline) {
        SupportAndReportDialog(
            uiState = uiState,
            onDismiss = { showSupportReport = false },
            onSubmitTicket = { s, m -> viewModel.submitSupportTicket(s, m) },
            onSubmitReport = { target, reason -> viewModel.submitPlayerReport(target, reason) }
        )
    }

    if (showLeaderboard && !uiState.isOffline) {
        LeaderboardDialog(
            uiState = uiState,
            onDismiss = { showLeaderboard = false }
        )
    }

    if (showBluetooth) {
        BluetoothDialog(
            uiState = uiState,
            onDismiss = { showBluetooth = false }
        )
    }

    uiState.bannedDisconnectNotice?.let { banNotice ->
        BannedDisconnectionDialog(
            info = banNotice,
            nowMillis = uiState.banClockMillis,
            onSwitchNetwork = {
                viewModel.dismissBannedDisconnectNotice()
                showNetworkManager = true
            },
            onSwitchOffline = {
                viewModel.dismissBannedDisconnectNotice()
                viewModel.requestOfflineModeSwitch()
            },
            onDismiss = { viewModel.dismissBannedDisconnectNotice() }
        )
    }
}

@Composable
private fun BannedDisconnectionDialog(
    info: BannedDisconnectInfo,
    nowMillis: Long,
    onSwitchNetwork: () -> Unit,
    onSwitchOffline: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OceanSlate,
        icon = {
            Icon(
                imageVector = Icons.Filled.GppBad,
                contentDescription = "Ağdan uzaklaştırıldı",
                tint = CrimsonDanger,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                text = "Bağlantı Kesildi (Disconnection)",
                color = CrimsonDanger,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (info.kickedFromWorld) {
                        "${info.networkName} ağından yasaklandığınız için mevcut maçtan / bekleme alanından ve lobiden atıldınız."
                    } else {
                        "${info.networkName} ağından yasaklandığınız için tüm maçlara, bekleme alanlarına ve lobilere erişiminiz kapatıldı."
                    },
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Sebep: ${info.reason}",
                    color = GoldLight,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Süre: ${formatBanRemainingLabel(info.banUntilMillis, nowMillis)}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Button(
                        onClick = onSwitchNetwork,
                        modifier = Modifier.weight(1f).testTag("ban_dialog_switch_network"),
                        colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary, contentColor = OceanDeep)
                    ) {
                        Text("Başka Ağa Geç", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onSwitchOffline,
                        modifier = Modifier.weight(1f).testTag("ban_dialog_offline"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight)
                    ) {
                        Text("Çevrimdışı Mod", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("ban_dialog_ok")
            ) {
                Text("Tamam", color = TextSecondary, fontWeight = FontWeight.Bold)
            }
        }
    )
}
