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
import com.example.ui.components.ItemModelIcon
import com.example.ui.components.LevelTierBadge
import com.example.ui.components.RankTagAndName
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState

@Composable
fun MarketDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onBuyItem: (GameItem) -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.84f).widthIn(max = 680.dp),
            shape = RoundedCornerShape(18.dp),
            color = OceanSlate,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldAccent)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("DENİZCRAFT MARKET", color = GoldAccent, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("Bakiyen: ${uiState.displayCoins} Coin", color = AquaPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = TextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GameCatalog.items) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(OceanCard)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(OceanDeep),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ItemModelIcon(item = item, modifier = Modifier.size(28.dp))
                                }
                                Column {
                                    Text(item.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${item.category.displayName} • Hasar: ${item.attackDamage}", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                            Button(
                                onClick = { onBuyItem(item) },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = OceanDeep)
                            ) {
                                Text("${item.priceCoins} Coin", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupportAndReportDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onSubmitTicket: (String, String) -> Unit,
    onSubmitReport: (NetworkMember, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var targetUid by remember(uiState.networkMembers) { mutableStateOf(uiState.networkMembers.firstOrNull()?.userId ?: "") }
    var reportReason by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.84f).widthIn(max = 680.dp),
            shape = RoundedCornerShape(18.dp),
            color = OceanSlate,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SapphireBlue)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("DESTEK & OYUNCU RAPORU", color = AquaPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = TextPrimary)
                    }
                }
                TabRow(selectedTabIndex = selectedTab, containerColor = OceanDeep) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Destek Talebi") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Oyuncu Raporla") })
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 0) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Konu") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ticket_subject_input"))
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Açıklama") }, modifier = Modifier.fillMaxWidth().testTag("ticket_message_input"))
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    onSubmitTicket(subject, message)
                                    subject = ""
                                    message = ""
                                },
                                modifier = Modifier.fillMaxWidth().testTag("submit_ticket_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAction, contentColor = OceanDeep)
                            ) { Text("Destek Talebi Gönder", fontWeight = FontWeight.Bold) }
                        }
                        item { Text("Taleplerin", color = GoldAccent, fontWeight = FontWeight.Bold) }
                        items(uiState.tickets) { t ->
                            Card(colors = CardDefaults.cardColors(containerColor = OceanCard)) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("${t.subject} [${t.status}]", color = AquaPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(t.message, color = TextPrimary, fontSize = 12.sp)
                                    if (t.staffReply.isNotBlank()) {
                                        Text("Yetkili Yanıtı (${t.repliedBy}): ${t.staffReply}", color = EmeraldAction, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Raporlanacak Oyuncuyu Seç:", color = GoldAccent, fontWeight = FontWeight.Bold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(uiState.networkMembers) { m ->
                                val sel = m.userId == targetUid
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) CrimsonDanger.copy(alpha = 0.25f) else OceanCard)
                                        .border(1.dp, if (sel) CrimsonDanger else OceanCardBorder, RoundedCornerShape(8.dp))
                                        .clickable { targetUid = m.userId }
                                        .padding(8.dp)
                                ) {
                                    RankTagAndName(rank = m.rankEnum, playerName = m.username, compact = true)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = reportReason,
                            onValueChange = { reportReason = it },
                            label = { Text("İhlal Sebebi (Hile, Küfür, Grief vb.)") },
                            modifier = Modifier.fillMaxWidth().testTag("report_reason_input")
                        )
                        Button(
                            onClick = {
                                val target = uiState.networkMembers.firstOrNull { it.userId == targetUid }
                                if (target != null) {
                                    onSubmitReport(target, reportReason)
                                    reportReason = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("submit_report_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger, contentColor = Color.White)
                        ) { Text("Rapor Gönder", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit
) {
    val sorted = remember(uiState.networkMembers) {
        uiState.networkMembers.sortedWith(compareByDescending<NetworkMember> { it.level }.thenByDescending { it.coins })
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
            shape = RoundedCornerShape(16.dp),
            color = OceanSlate,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF9B59B6))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${uiState.activeNetwork.name} Sıralaması", color = AquaPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = TextPrimary)
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sorted.indices.toList()) { idx ->
                        val m = sorted[idx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(OceanCard)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("#${idx + 1}", color = GoldAccent, fontWeight = FontWeight.Black, fontSize = 11.sp)
                                RankTagAndName(rank = m.rankEnum, playerName = m.username, compact = true)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                LevelTierBadge(level = m.level.toInt())
                                Text("${m.coins} Coin", color = GoldLight, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
