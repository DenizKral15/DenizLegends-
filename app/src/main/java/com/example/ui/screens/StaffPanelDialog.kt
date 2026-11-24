package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.*
import com.example.ui.components.RankTagAndName
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState
import com.example.viewmodel.StaffResourceAction

@Composable
fun StaffPanelDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onReplyTicket: (SupportTicket, String) -> Unit,
    onReviewReport: (PlayerReport, Boolean) -> Unit,
    onBanPlayer: (NetworkMember, Boolean, String, Long?) -> Unit,
    onMutePlayer: (NetworkMember, Boolean, String, Long?) -> Unit,
    onWarnPlayer: (NetworkMember, String) -> Unit,
    onSpectateRoom: (GameRoom) -> Unit,
    onAssignRank: (NetworkMember, Rank) -> Unit,
    onSetGameMode: (NetworkMember, GameMode) -> Unit,
    onModifyStats: (NetworkMember, StaffResourceAction, Long?, Long?, Long?, Pair<String, Int>?, Pair<String, Int>?) -> Unit,
    onManageMobs: (MobType, Boolean) -> Unit,
    onToggleNetworkMaintenance: (GameNetwork) -> Unit,
    onDeleteNetwork: (GameNetwork) -> Unit
) {
    val rank = uiState.displayRank
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .widthIn(max = 760.dp),
            shape = RoundedCornerShape(18.dp),
            color = OceanSlate,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, CrimsonDanger)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (rank.canAccessStaffPanel) "YETKİLİ PANELİ (${rank.displayName})" else "MİMARİ YETKİ BİLGİSİ (${rank.displayName})",
                            color = CrimsonDanger,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                        Text(
                            text = uiState.activeNetwork.name,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).testTag("close_staff_panel")) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = TextPrimary)
                    }
                }

                if (!rank.canAccessStaffPanel) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OceanCard),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Tasarım & İnşa Hakları", color = AquaPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (rank == Rank.MIMAR) {
                                    "Mimar olarak yönetim paneline erişimin yoktur, ancak Lobi dahil tüm inşaat alanlarında tam Tasarım & İnşa (3x1 Fırça) yetkisine sahipsin!"
                                } else {
                                    "Akademi olarak yönetim paneline erişimin yoktur. Mimar'dan daha kısıtlı temel blok yerleştirme & tasarım yetkisine sahipsin."
                                },
                                color = TextPrimary
                            )
                        }
                    }
                    return@Column
                }

                if (rank.canAccessManagementTab) {
                    TabRow(selectedTabIndex = selectedTab, containerColor = OceanDeep) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Destek & Moderasyon", fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Yönetim", fontWeight = FontWeight.Bold) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTab == 0 || !rank.canAccessManagementTab) {
                        StaffSupportModerationTab(
                            uiState = uiState,
                            rank = rank,
                            onReplyTicket = onReplyTicket,
                            onReviewReport = onReviewReport,
                            onBanPlayer = onBanPlayer,
                            onMutePlayer = onMutePlayer,
                            onWarnPlayer = onWarnPlayer,
                            onSpectateRoom = { r ->
                                onDismiss()
                                onSpectateRoom(r)
                            }
                        )
                    } else {
                        StaffManagementTab(
                            uiState = uiState,
                            staffRank = rank,
                            onAssignRank = onAssignRank,
                            onSetGameMode = onSetGameMode,
                            onModifyStats = onModifyStats,
                            onManageMobs = onManageMobs,
                            onToggleNetworkMaintenance = onToggleNetworkMaintenance,
                            onDeleteNetwork = { net ->
                                onDismiss()
                                onDeleteNetwork(net)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffSupportModerationTab(
    uiState: MainUiState,
    rank: Rank,
    onReplyTicket: (SupportTicket, String) -> Unit,
    onReviewReport: (PlayerReport, Boolean) -> Unit,
    onBanPlayer: (NetworkMember, Boolean, String, Long?) -> Unit,
    onMutePlayer: (NetworkMember, Boolean, String, Long?) -> Unit,
    onWarnPlayer: (NetworkMember, String) -> Unit,
    onSpectateRoom: (GameRoom) -> Unit
) {
    var selectedUid by remember(uiState.networkMembers) {
        mutableStateOf(uiState.networkMembers.firstOrNull()?.userId ?: "")
    }
    val target = uiState.networkMembers.firstOrNull { it.userId == selectedUid }
    var penaltyReason by remember { mutableStateOf("Kural ihlali") }
    var timedMinutes by remember { mutableStateOf("60") }
    var isPermanent by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Ceza & Susturma İşlemleri
        item {
            Card(colors = CardDefaults.cardColors(containerColor = OceanCard)) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Oyuncu Ceza & Moderasyon", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(uiState.networkMembers) { m ->
                            val sel = m.userId == selectedUid
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) CrimsonDanger.copy(alpha = 0.25f) else OceanDeep)
                                    .border(1.dp, if (sel) CrimsonDanger else OceanCardBorder, RoundedCornerShape(8.dp))
                                    .clickable { selectedUid = m.userId }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                RankTagAndName(rank = m.rankEnum, playerName = m.username, compact = true)
                            }
                        }
                    }

                    if (target != null) {
                        OutlinedTextField(
                            value = penaltyReason,
                            onValueChange = { penaltyReason = it },
                            label = { Text("İhlal Sebebi / Uyarı Mesajı") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !isPermanent, onClick = { isPermanent = false }, label = { Text("Süreli") })
                            FilterChip(selected = isPermanent, onClick = { isPermanent = true }, label = { Text("Kalıcı") })
                            if (!isPermanent) {
                                OutlinedTextField(
                                    value = timedMinutes,
                                    onValueChange = { timedMinutes = it.filter(Char::isDigit) },
                                    label = { Text("Dakika") },
                                    singleLine = true,
                                    modifier = Modifier.width(100.dp)
                                )
                            }
                        }
                        val dur = if (isPermanent) null else (timedMinutes.toLongOrNull() ?: 60L)
                        val canTouch = rank.canPunish(target.rankEnum)
                        if (!canTouch) {
                            Text("Bu rütbe (${target.rankEnum.displayName}) korumalıdır!", color = CrimsonDanger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (rank.canBan) {
                                Button(
                                    enabled = canTouch,
                                    onClick = { onBanPlayer(target, !target.banned, penaltyReason, dur) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger, contentColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                ) { Text(if (target.banned) "Ban Aç" else "Yasakla", fontSize = 11.sp) }
                            }
                            if (rank.canMute) {
                                Button(
                                    enabled = canTouch,
                                    onClick = { onMutePlayer(target, !target.muted, penaltyReason, dur) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarm, contentColor = Color.White),
                                    modifier = Modifier.weight(1f)
                                ) { Text(if (target.muted) "Mute Aç" else "Sessizleştir", fontSize = 11.sp) }
                            }
                            if (rank.canWarn) {
                                Button(
                                    enabled = canTouch,
                                    onClick = { onWarnPlayer(target, penaltyReason) },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = OceanDeep),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Uyar (${target.warningsCount})", fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
        }

        // 2. Match Spectate (Maç İzle)
        if (rank.canSpectateMatch) {
            item {
                Text("Maç İzle (Genel / Oyuncu Odaları)", color = AquaPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            items(uiState.rooms.filter { it.status != "FINISHED" }) { room ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(OceanCard)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${room.serverTypeEnum.displayName} • ${room.mapName} (${room.playerCount} kişi)", color = TextPrimary, fontSize = 12.sp)
                    Button(
                        onClick = { onSpectateRoom(room) },
                        colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary, contentColor = OceanDeep)
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("İzle", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Support Tickets
        if (rank.canHandleTickets) {
            item { Text("Oyuncu Destek Talepleri (${uiState.tickets.size})", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            items(uiState.tickets) { ticket ->
                var replyInput by remember(ticket.id) { mutableStateOf(ticket.staffReply) }
                Card(colors = CardDefaults.cardColors(containerColor = OceanCard)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${ticket.subject} — ${ticket.authorName} [${ticket.status}]", color = AquaPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(ticket.message, color = TextPrimary, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = replyInput, onValueChange = { replyInput = it }, label = { Text("Yanıtla") }, singleLine = true, modifier = Modifier.weight(1f))
                            Button(onClick = { onReplyTicket(ticket, replyInput) }) { Text("Gönder", fontSize = 11.sp) }
                        }
                    }
                }
            }
        }

        // 4. Player Reports
        if (rank.canHandleReports) {
            item { Text("Oyuncu Raporları (${uiState.reports.size})", color = CrimsonDanger, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            items(uiState.reports) { report ->
                Card(colors = CardDefaults.cardColors(containerColor = OceanCard)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Hedef: ${report.targetName} (Bildiren: ${report.reporterName}) [${report.status}]", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Sebep: ${report.reason}", color = TextSecondary, fontSize = 12.sp)
                        if (report.status == "PENDING") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onReviewReport(report, true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAction, contentColor = OceanDeep)
                                ) { Text("Kabul Et", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                Button(
                                    onClick = { onReviewReport(report, false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger, contentColor = Color.White)
                                ) { Text("Reddet", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
    }
}
