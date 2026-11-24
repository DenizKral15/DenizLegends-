package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.model.*
import com.example.ui.components.LevelTierBadge
import com.example.ui.components.RankTagAndName
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState
import com.example.viewmodel.StaffResourceAction

@Composable
fun StaffManagementTab(
    uiState: MainUiState,
    staffRank: Rank,
    onAssignRank: (NetworkMember, Rank) -> Unit,
    onSetGameMode: (NetworkMember, GameMode) -> Unit,
    onModifyStats: (NetworkMember, StaffResourceAction, Long?, Long?, Long?, Pair<String, Int>?, Pair<String, Int>?) -> Unit,
    onManageMobs: (MobType, Boolean) -> Unit,
    onToggleNetworkMaintenance: (GameNetwork) -> Unit,
    onDeleteNetwork: (GameNetwork) -> Unit
) {
    val members = uiState.networkMembers
    var selectedTargetUid by remember(members) {
        mutableStateOf(members.firstOrNull()?.userId ?: "")
    }
    val target = members.firstOrNull { it.userId == selectedTargetUid }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var actionType by remember { mutableStateOf(StaffResourceAction.ADD) }
    var coinsInput by remember { mutableStateOf("100") }
    var gemsInput by remember { mutableStateOf("5") }
    var levelInput by remember { mutableStateOf("1") }
    var selectedCategory by remember { mutableStateOf(ItemCategory.BLOK) }
    val catItems = remember(selectedCategory) { GameCatalog.items.filter { it.category == selectedCategory } }
    var selectedItemId by remember(catItems) { mutableStateOf(catItems.firstOrNull()?.id ?: GameCatalog.items.first().id) }
    var itemAmountInput by remember { mutableStateOf("16") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (staffRank.canManageMaintenance || staffRank.canDeleteNetwork) {
            item {
                val activeNet = uiState.activeNetwork
                Card(colors = CardDefaults.cardColors(containerColor = OceanCard)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Sunucu Durumu & Bakım Yönetimi (${activeNet.name})",
                            color = GoldAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (activeNet.maintenanceMode) "Durum: BAKIM MODU AÇIK (Sadece yetkililer girebilir)" else "Durum: Herkese Açık",
                            color = if (activeNet.maintenanceMode) AmberWarm else EmeraldAction,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            if (staffRank.canManageMaintenance) {
                                Button(
                                    onClick = { onToggleNetworkMaintenance(activeNet) },
                                    modifier = Modifier.weight(1f).testTag("staff_toggle_maintenance_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (activeNet.maintenanceMode) EmeraldAction else AmberWarm,
                                        contentColor = OceanDeep
                                    )
                                ) {
                                    Text(
                                        text = if (activeNet.maintenanceMode) "Bakımı Kapat" else "Bakıma Al",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if (staffRank.canDeleteNetwork && !activeNet.official && activeNet.id != "official") {
                                Button(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.weight(1f).testTag("staff_delete_network_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger, contentColor = Color.White)
                                ) {
                                    Text("Sunucuyu Sil", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Hedef Oyuncu Seç", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(members) { m ->
                    val sel = m.userId == selectedTargetUid
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) AquaPrimary.copy(alpha = 0.22f) else OceanCard)
                            .border(1.dp, if (sel) AquaPrimary else OceanCardBorder, RoundedCornerShape(10.dp))
                            .clickable { selectedTargetUid = m.userId }
                            .padding(8.dp)
                    ) {
                        RankTagAndName(rank = m.rankEnum, playerName = m.username, compact = true)
                        LevelTierBadge(level = m.level.toInt())
                    }
                }
            }
        }

        if (target != null) {
            // 1. Rank Assignment
            item {
                val isSelfTarget = target.userId == uiState.currentMember?.userId
                Card(colors = CardDefaults.cardColors(containerColor = OceanCard)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Rütbe Ayarla (${target.username})", color = AquaPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(Rank.entries) { r ->
                                val allowed = staffRank.canAssignRank(r, target.rankEnum, isSelf = isSelfTarget)
                                FilterChip(
                                    selected = target.rankEnum == r,
                                    enabled = allowed,
                                    onClick = { onAssignRank(target, r) },
                                    label = { Text(r.displayName, fontSize = 11.sp) }
                                )
                            }
                        }
                        if (isSelfTarget && target.rankEnum == Rank.KURUCU) {
                            Text("Kurucu kendi rütbesini düşüremez veya hesabını cezalandıramaz.", color = GoldAccent, fontSize = 10.sp)
                        } else if (staffRank == Rank.YONETICI) {
                            Text("Not: Yönetici, Kurucu ve Yönetici atayamaz/değiştiremez.", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }

            // 2. GameMode Assignment
            item {
                Card(colors = CardDefaults.cardColors(containerColor = OceanCard)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Oyun Modu Ayarla", color = AquaPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GameMode.entries.forEach { gm ->
                                FilterChip(
                                    selected = target.gameModeEnum == gm,
                                    onClick = { onSetGameMode(target, gm) },
                                    label = { Text(gm.displayName, fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Resources & Level (Arttır / Azalt / Eşitle)
            item {
                Card(colors = CardDefaults.cardColors(containerColor = OceanCard)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Kaynak & Seviye Yönetimi (Mevcut: ${target.coins} Coin, ${target.gems} Elmas, Lv.${target.level})",
                            color = AquaPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = actionType == StaffResourceAction.ADD, onClick = { actionType = StaffResourceAction.ADD }, label = { Text("Arttır") })
                            FilterChip(selected = actionType == StaffResourceAction.SUBTRACT, onClick = { actionType = StaffResourceAction.SUBTRACT }, label = { Text("Azalt") })
                            FilterChip(selected = actionType == StaffResourceAction.SET, onClick = { actionType = StaffResourceAction.SET }, label = { Text("Eşitle") })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = coinsInput, onValueChange = { coinsInput = it.filter(Char::isDigit) }, label = { Text("Coin") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = gemsInput, onValueChange = { gemsInput = it.filter(Char::isDigit) }, label = { Text("Elmas") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = levelInput, onValueChange = { levelInput = it.filter(Char::isDigit) }, label = { Text("Seviye") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onModifyStats(target, actionType, coinsInput.toLongOrNull(), gemsInput.toLongOrNull(), null, null, null) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = OceanDeep)
                            ) { Text("Kaynağı Uygula", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = { onModifyStats(target, actionType, null, null, levelInput.toLongOrNull(), null, null) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary, contentColor = OceanDeep)
                            ) { Text("Seviye Uygula", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // 4. Add / Remove Item (Tür / Eşya / Miktar)
            item {
                Card(colors = CardDefaults.cardColors(containerColor = OceanCard)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Eşya Ekle / Sil (Tür / Eşya / Miktar)", color = AquaPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(ItemCategory.entries) { cat ->
                                FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(cat.displayName, fontSize = 11.sp) })
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(catItems) { item ->
                                FilterChip(selected = selectedItemId == item.id, onClick = { selectedItemId = item.id }, label = { Text(item.name, fontSize = 11.sp) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = itemAmountInput, onValueChange = { itemAmountInput = it.filter(Char::isDigit) }, label = { Text("Miktar") }, singleLine = true, modifier = Modifier.width(100.dp))
                            val amt = (itemAmountInput.toIntOrNull() ?: 1).coerceIn(1, 999)
                            Button(
                                onClick = { onModifyStats(target, StaffResourceAction.ADD, null, null, null, selectedItemId to amt, null) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAction, contentColor = OceanDeep)
                            ) { Text("Ekle", fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = { onModifyStats(target, StaffResourceAction.SUBTRACT, null, null, null, null, selectedItemId to amt) },
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger, contentColor = Color.White)
                            ) { Text("Sil", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }

        // 5. Mob Management (Yaratık Yönetimi)
        item {
            Card(colors = CardDefaults.cardColors(containerColor = OceanCard)) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Yaratık Yönetimi (Canlı Dünya)", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    MobType.entries.forEach { mob ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(mob.displayName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { onManageMobs(mob, true) }) { Text("Spawn", fontSize = 11.sp) }
                                OutlinedButton(onClick = { onManageMobs(mob, false) }) { Text("Kill", fontSize = 11.sp, color = CrimsonDanger) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val activeNet = uiState.activeNetwork
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = OceanSlate,
            title = { Text("Sunucuyu Sil", color = CrimsonDanger, fontWeight = FontWeight.Black) },
            text = { Text("${activeNet.name} sunucusunu kalıcı olarak silmek istediğine emin misin?", color = TextPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteNetwork(activeNet)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger, contentColor = Color.White)
                ) { Text("Sil", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("İptal", color = TextSecondary) }
            }
        )
    }
}
