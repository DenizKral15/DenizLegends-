package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.components.BlockyAvatar
import com.example.ui.components.LevelTierBadge
import com.example.ui.components.RankTagAndName
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState

@Composable
fun LobbyHubScreen(
    uiState: MainUiState,
    onCycleSkin: () -> Unit,
    onUpdateUsername: (String) -> Unit,
    onOpenGameSelect: () -> Unit,
    onQuickEnter3DLobby: () -> Unit,
    onOpenMarket: () -> Unit,
    onOpenNetworks: () -> Unit,
    onOpenStaffPanel: () -> Unit,
    onOpenOfflineSandbox: () -> Unit,
    onOpenSupportReport: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onClaimDailyBonus: () -> Unit,
    onRequestOfflineMode: () -> Unit,
    onExitOfflineOrSignOut: () -> Unit
) {
    var showProfileEditDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A2444), Color(0xFF15436B), Color(0xFF0E2942))
                )
            )
    ) {
        // Stylized Voxel Lobby Stage Backdrop
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Platform under avatar
            drawOval(
                color = Color(0xFF1E4D78),
                topLeft = Offset(w * 0.24f, h * 0.65f),
                size = Size(w * 0.52f, h * 0.14f)
            )
            drawOval(
                color = AquaPrimary.copy(alpha = 0.28f),
                topLeft = Offset(w * 0.28f, h * 0.66f),
                size = Size(w * 0.44f, h * 0.09f)
            )
            // Ambient floating voxel blocks
            drawRect(Color(0xFF1B4F72).copy(alpha = 0.45f), Offset(w * 0.06f, h * 0.28f), Size(42f, 42f))
            drawRect(Color(0xFF148F77).copy(alpha = 0.45f), Offset(w * 0.86f, h * 0.34f), Size(46f, 46f))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- TOP BAR ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Player Identity + Level XP + Active Network
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OceanSlate.copy(alpha = 0.88f))
                        .border(1.dp, OceanCardBorder, RoundedCornerShape(12.dp))
                        .clickable { showProfileEditDialog = true }
                        .padding(10.dp)
                        .testTag("profile_header_card")
                ) {
                    RankTagAndName(
                        rank = uiState.displayRank,
                        playerName = uiState.displayName,
                        compact = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LevelTierBadge(level = uiState.displayLevel)
                        val needed = LevelSystem.xpForNextLevel(uiState.displayLevel).coerceAtLeast(1L)
                        val progress = (uiState.displayXp.toFloat() / needed).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = AquaPrimary,
                            trackColor = OceanDeep
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isOffline) Icons.Filled.CloudOff else Icons.Filled.Public,
                            contentDescription = null,
                            tint = if (uiState.isOffline) AmberWarm else AquaPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (uiState.isOffline) "Çevrimdışı Mod (Botlu)" else uiState.activeNetwork.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Currency & Exit/Switch Actions
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CurrencyChip(
                            icon = Icons.Filled.MonetizationOn,
                            value = uiState.displayCoins.toString(),
                            color = GoldAccent
                        )
                        CurrencyChip(
                            icon = Icons.Filled.Diamond,
                            value = uiState.displayGems.toString(),
                            color = AquaPrimary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!uiState.isOffline) {
                            IconButton(
                                onClick = onRequestOfflineMode,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OceanSlate.copy(alpha = 0.85f))
                                    .testTag("switch_offline_button")
                            ) {
                                Icon(
                                    Icons.Filled.CloudOff,
                                    contentDescription = "Çevrimdışı Moda Geç",
                                    tint = AmberWarm
                                )
                            }
                        }
                        IconButton(
                            onClick = onExitOfflineOrSignOut,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OceanSlate.copy(alpha = 0.85f))
                                .testTag("logout_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = if (uiState.isOffline) "Giriş Ekranına Dön" else "Çıkış Yap",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            // --- MIDDLE GAME HUB AREA ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Action Column
                val networkActionsEnabled = !uiState.isMemberBannedActive && !uiState.isNetworkMaintenanceBlocked
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    HubSideButton(
                        icon = Icons.Filled.Storefront,
                        label = "MARKET",
                        accent = GoldAccent,
                        testTag = "hub_market_button",
                        enabled = networkActionsEnabled,
                        onClick = onOpenMarket
                    )
                    if (!uiState.isOffline) {
                        HubSideButton(
                            icon = Icons.Filled.Hub,
                            label = "AĞLAR",
                            accent = AquaPrimary,
                            testTag = "hub_networks_button",
                            enabled = true,
                            onClick = onOpenNetworks
                        )
                    }
                    if (uiState.isOffline) {
                        HubSideButton(
                            icon = Icons.Filled.Tune,
                            label = "SANDBOX",
                            accent = EmeraldAction,
                            testTag = "hub_offline_sandbox_button",
                            onClick = onOpenOfflineSandbox
                        )
                    } else if (uiState.displayRank.canAccessStaffPanel || uiState.displayRank.canBuildAndDesign) {
                        HubSideButton(
                            icon = Icons.Filled.AdminPanelSettings,
                            label = if (uiState.displayRank.canAccessStaffPanel) "YETKİLİ" else "MİMAR",
                            accent = CrimsonDanger,
                            testTag = "hub_staff_button",
                            enabled = !uiState.isMemberBannedActive,
                            onClick = onOpenStaffPanel
                        )
                    }
                }

                // Center Interactive Blocky Character / Ban Disconnection Notice
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isMemberBannedActive) {
                        val banUntil = uiState.currentMember?.banUntilMillis ?: 0L
                        val durationText = formatBanRemainingLabel(banUntil, uiState.banClockMillis)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CrimsonDark.copy(alpha = 0.94f)),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, CrimsonDanger),
                            modifier = Modifier
                                .padding(8.dp)
                                .widthIn(max = 320.dp)
                                .testTag("banned_disconnection_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GppBad,
                                    contentDescription = "Ağdan yasaklandı",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "BAĞLANTI KESİLDİ • YASAKLI",
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "${uiState.activeNetwork.name} ağının tüm maç ve lobilerinden uzaklaştırıldınız.",
                                    color = Color.White.copy(alpha = 0.92f),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Sebep: ${uiState.currentMember?.banReason?.ifBlank { "Kural ihlali" }}",
                                    color = GoldLight,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Süre: $durationText",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = onOpenNetworks,
                                        colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary, contentColor = OceanDeep),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(42.dp).testTag("ban_card_switch_network_button")
                                    ) {
                                        Text("Başka Ağ", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    OutlinedButton(
                                        onClick = onRequestOfflineMode,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldLight),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(42.dp).testTag("ban_card_offline_button")
                                    ) {
                                        Text("Çevrimdışı", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else if (uiState.isNetworkMaintenanceBlocked) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = OceanSlate.copy(alpha = 0.96f)),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.8.dp, AmberWarm),
                            modifier = Modifier
                                .padding(8.dp)
                                .widthIn(max = 320.dp)
                                .testTag("maintenance_disconnection_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Build,
                                    contentDescription = "Sunucu bakımda",
                                    tint = AmberWarm,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "BAKIM MODU AKTİF",
                                    fontWeight = FontWeight.Black,
                                    color = AmberWarm,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "${uiState.activeNetwork.name} şu anda bakım modundadır. Sadece yetkililer ve sunucu sahipleri giriş yapabilir.",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = onOpenNetworks,
                                        colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary, contentColor = OceanDeep),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(42.dp).testTag("maintenance_switch_network_button")
                                    ) {
                                        Text("Başka Ağ", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    OutlinedButton(
                                        onClick = onRequestOfflineMode,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldLight),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(42.dp).testTag("maintenance_offline_button")
                                    ) {
                                        Text("Çevrimdışı", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        if (!uiState.isOffline && uiState.activeNetwork.maintenanceMode) {
                            Surface(
                                color = AmberWarm.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarm),
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Text(
                                    text = "BAKIM MODU AÇIK (Yetkili Erişimi)",
                                    color = AmberWarm,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                        BlockyAvatar(
                            skinIndex = uiState.displaySkinIndex,
                            rank = uiState.displayRank,
                            size = 175.dp,
                            onClick = onCycleSkin
                        )
                        val skinName = SKIN_PALETTES.getOrNull(uiState.displaySkinIndex)?.first ?: "Kaptan Deniz"
                        Surface(
                            color = OceanSlate.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { onCycleSkin() }
                        ) {
                            Text(
                                text = "Kostüm: $skinName (Dokun)",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Right Action Column
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    HubSideButton(
                        icon = Icons.Filled.CardGiftcard,
                        label = "BONUS",
                        accent = Color(0xFFE91E63),
                        testTag = "hub_bonus_button",
                        enabled = networkActionsEnabled,
                        onClick = onClaimDailyBonus
                    )
                    if (!uiState.isOffline) {
                        HubSideButton(
                            icon = Icons.Filled.SupportAgent,
                            label = "DESTEK",
                            accent = SapphireBlue,
                            testTag = "hub_support_button",
                            enabled = networkActionsEnabled,
                            onClick = onOpenSupportReport
                        )
                        HubSideButton(
                            icon = Icons.Filled.Leaderboard,
                            label = "SIRALA",
                            accent = Color(0xFF9B59B6),
                            testTag = "hub_leaderboard_button",
                            enabled = networkActionsEnabled,
                            onClick = onOpenLeaderboard
                        )
                    }
                    HubSideButton(
                        icon = Icons.Filled.Bluetooth,
                        label = "BT AĞ",
                        accent = AquaPrimary,
                        testTag = "hub_bluetooth_button",
                        onClick = onOpenBluetooth
                    )
                }
            }

            // --- BOTTOM PLAY DOCK ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onQuickEnter3DLobby,
                    enabled = !uiState.isMemberBannedActive && !uiState.isNetworkMaintenanceBlocked,
                    modifier = Modifier
                        .height(56.dp)
                        .weight(0.42f)
                        .testTag("enter_3d_lobby_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AquaPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = OceanSlate.copy(alpha = 0.9f),
                        contentColor = AquaPrimary
                    )
                ) {
                    Icon(Icons.Filled.Explore, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("3D LOBİ", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }

                Button(
                    onClick = onOpenGameSelect,
                    enabled = !uiState.isMemberBannedActive && !uiState.isNetworkMaintenanceBlocked,
                    modifier = Modifier
                        .height(56.dp)
                        .weight(0.58f)
                        .testTag("play_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldAction,
                        contentColor = OceanDeep
                    )
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("OYNA", fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.sp)
                }
            }
        }
    }

    if (showProfileEditDialog) {
        var nameInput by remember { mutableStateOf(uiState.displayName) }
        AlertDialog(
            onDismissRequest = { showProfileEditDialog = false },
            containerColor = OceanSlate,
            title = { Text("Oyuncu Profili", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RankTagAndName(rank = uiState.displayRank, playerName = uiState.displayName)
                    LevelTierBadge(level = uiState.displayLevel)
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Kullanıcı Adı") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("username_edit_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateUsername(nameInput)
                        showProfileEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary, contentColor = OceanDeep)
                ) {
                    Text("Kaydet", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileEditDialog = false }) {
                    Text("Kapat", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun CurrencyChip(icon: ImageVector, value: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(OceanSlate.copy(alpha = 0.88f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(value, color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    }
}

@Composable
private fun HubSideButton(
    icon: ImageVector,
    label: String,
    accent: Color,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(74.dp)
            .defaultMinSize(minHeight = 62.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(RoundedCornerShape(12.dp))
            .background(OceanSlate.copy(alpha = 0.88f))
            .border(1.4.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

fun formatBanRemainingLabel(banUntilMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (banUntilMillis <= 0L) return "Kalıcı Yasak"
    val diffMs = (banUntilMillis - maxOf(nowMillis, System.currentTimeMillis())).coerceAtLeast(0L)
    val totalMin = ((diffMs + 59_999L) / 60_000L).coerceAtLeast(1L)
    return if (totalMin >= 60L) {
        val hours = totalMin / 60L
        val mins = totalMin % 60L
        if (mins == 0L) "$hours saat kaldı" else "$hours sa $mins dk kaldı"
    } else {
        "$totalMin dakika kaldı"
    }
}
