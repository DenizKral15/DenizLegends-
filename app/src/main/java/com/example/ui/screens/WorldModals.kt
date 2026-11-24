package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
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
import com.example.engine.WorldModalType
import com.example.engine.WorldSessionState
import com.example.model.*
import com.example.ui.components.ItemModelIcon
import com.example.ui.components.RankTagAndName
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState

@Composable
fun WorldActionModalHost(
    uiState: MainUiState,
    worldSession: WorldSessionState,
    onDismiss: () -> Unit,
    onBuyMatchItem: (GameItem) -> Unit,
    onClaimBankBonus: () -> Unit,
    onVoteBuildBattle: (Int) -> Unit,
    onSelectSlot: (Int) -> Unit,
    onSwapInventorySlots: (Int, Int) -> Unit,
    onCreativeSetSlot: (GameItem, Int) -> Unit,
    onClearSlot: (Int) -> Unit,
    onPlayerInteract: (WorldEntity, String, String) -> Unit
) {
    when (worldSession.activeModal) {
        WorldModalType.MARKET_NPC -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = OceanSlate,
                title = { Text("Maç Marketi (Demir / Altın)", color = GoldAccent, fontWeight = FontWeight.Black) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Mevcut: ${worldSession.iron} Demir • ${worldSession.gold} Altın",
                            color = AquaPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(GameCatalog.items.take(10)) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(OceanCard)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(OceanDeep),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ItemModelIcon(item = item, modifier = Modifier.size(26.dp))
                                        }
                                        Column {
                                            Text(item.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                text = "${item.priceIron} Demir, ${item.priceGold} Altın",
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { onBuyMatchItem(item) },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAction, contentColor = OceanDeep)
                                    ) {
                                        Text("Al", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("Kapat", color = TextSecondary) }
                }
            )
        }
        WorldModalType.BANK_NPC -> {
            val bonusRemaining = uiState.dailyBonusRemainingMillis
            val bonusReady = bonusRemaining <= 0L
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = OceanSlate,
                title = { Text("Skyblock Banka & Pazar", color = GoldAccent, fontWeight = FontWeight.Black) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bakiye: ${uiState.displayCoins} Coin • ${uiState.displayGems} Elmas", color = TextPrimary)
                        Text(
                            text = if (bonusReady) {
                                "Günlük ada kazancın hazır! 24 saatte bir +250 Coin, +5 Elmas toplayabilirsin."
                            } else {
                                "Günlük bonus zaten alındı. Kalan süre: ${DailyBonusPolicy.formatRemainingTurkish(bonusRemaining)}"
                            },
                            color = if (bonusReady) TextSecondary else AmberWarm,
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onClaimBankBonus()
                            onDismiss()
                        },
                        enabled = !uiState.isDailyBonusClaimBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (bonusReady) GoldAccent else OceanCard,
                            contentColor = if (bonusReady) OceanDeep else TextPrimary
                        )
                    ) {
                        Text(
                            text = if (bonusReady) "Günlük Bonus Topla (+250 Coin)" else "Bekle (${DailyBonusPolicy.formatCompactBadge(bonusRemaining)})",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Kapat", color = TextSecondary) }
                }
            )
        }
        WorldModalType.BUILD_BATTLE_VOTE -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = OceanSlate,
                title = { Text("BuildBattle Oylama", color = GoldAccent, fontWeight = FontWeight.Black) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Tema: ${worldSession.buildTheme}", color = AquaPrimary, fontWeight = FontWeight.Bold)
                        Text("Yapıya kaç yıldız veriyorsun?", color = TextPrimary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            (1..5).forEach { stars ->
                                IconButton(
                                    onClick = { onVoteBuildBattle(stars) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(OceanCard)
                                        .testTag("vote_star_$stars")
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Filled.Star, contentDescription = "$stars Yıldız", tint = GoldAccent)
                                        Text("$stars", fontSize = 10.sp, color = TextPrimary)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }
        WorldModalType.INVENTORY -> {
            MinecraftInventoryDialog(
                uiState = uiState,
                selectedHotbarSlot = worldSession.selectedSlotIndex,
                onSelectHotbarSlot = onSelectSlot,
                onSwapSlots = onSwapInventorySlots,
                onClearSlot = onClearSlot,
                onDismiss = onDismiss
            )
        }
        WorldModalType.CREATIVE_MENU -> {
            CreativeMenuDialog(
                uiState = uiState,
                selectedHotbarSlot = worldSession.selectedSlotIndex,
                onSelectHotbarSlot = onSelectSlot,
                onCreativeSetSlot = onCreativeSetSlot,
                onClearSlot = onClearSlot,
                onDismiss = onDismiss
            )
        }
        WorldModalType.PLAYER_INTERACT -> {
            val target = worldSession.interactTargetEntity
            if (target != null) {
                val heldItemName = uiState.displayInventory.getOrNull(worldSession.selectedSlotIndex)
                    ?.let { GameCatalog.findById(it.itemId)?.name }
                PlayerInteractDialog(
                    target = target,
                    heldItemName = heldItemName,
                    onAction = { act, reason -> onPlayerInteract(target, act, reason) },
                    onDismiss = onDismiss
                )
            }
        }
        null -> Unit
    }
}

@Composable
fun RoomChatDrawerDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var msgText by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
            shape = RoundedCornerShape(16.dp),
            color = OceanSlate,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, OceanCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Oda Sohbeti", color = AquaPrimary, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = TextPrimary)
                    }
                }
                if (uiState.isMemberMutedActive) {
                    Text(
                        text = "Susturuldun! Sebep: ${uiState.currentMember?.muteReason}",
                        color = CrimsonDanger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (uiState.roomChatMessages.isEmpty()) {
                        item {
                            Text(
                                text = if (uiState.isOffline) "Çevrimdışı modda botlar sohbete izleyici olarak katılır." else "Henüz mesaj yok.",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    items(uiState.roomChatMessages) { msg ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(OceanDeep.copy(alpha = 0.7f))
                                .padding(8.dp)
                        ) {
                            RankTagAndName(
                                rank = Rank.fromCode(msg.senderRank),
                                playerName = msg.senderName,
                                compact = true
                            )
                            Text(msg.text, color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = msgText,
                        onValueChange = { msgText = it },
                        placeholder = { Text("Mesaj yaz...") },
                        enabled = !uiState.isMemberMutedActive,
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("room_chat_input")
                    )
                    IconButton(
                        onClick = {
                            if (msgText.isNotBlank()) {
                                onSendMessage(msgText)
                                msgText = ""
                            }
                        },
                        enabled = !uiState.isMemberMutedActive,
                        modifier = Modifier.size(48.dp).testTag("send_chat_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gönder", tint = AquaPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineSandboxDialog(
    uiState: MainUiState,
    inWorld: Boolean,
    onDismiss: () -> Unit,
    onSetGameMode: (GameMode) -> Unit,
    onEditResources: (coins: Long, gems: Long, iron: Int, gold: Int, emerald: Int, level: Int) -> Unit,
    onEditInventoryItem: (String, Int) -> Unit,
    onSummonMob: (MobType) -> Unit,
    onKillMobs: (MobType?) -> Unit
) {
    val p = uiState.offlineProfile
    var coinsText by remember(p.coins) { mutableStateOf(p.coins.toString()) }
    var ironText by remember(p.iron) { mutableStateOf(p.iron.toString()) }
    var goldText by remember(p.gold) { mutableStateOf(p.gold.toString()) }
    var levelNum by remember(p.level) { mutableIntStateOf(p.level) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.84f),
            shape = RoundedCornerShape(16.dp),
            color = OceanSlate,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, EmeraldAction)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ÇEVRİMDIŞI SANDBOX KONTROLÜ", color = EmeraldAction, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text("Tüm değişiklikler sadece senin cihazında tutulur", color = TextSecondary, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = TextPrimary)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Game Mode Switcher
                    item {
                        Text("Oyuncu Modu Kontrolü", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GameMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = p.gameMode == mode,
                                    onClick = { onSetGameMode(mode) },
                                    label = { Text(mode.displayName, fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f).testTag("offline_mode_${mode.code.lowercase()}")
                                )
                            }
                        }
                    }

                    // 2. Resources & Level Control
                    item {
                        Text("Kaynak ve Seviye Kontrolü", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = coinsText,
                                onValueChange = { coinsText = it.filter(Char::isDigit) },
                                label = { Text("Coin") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = ironText,
                                onValueChange = { ironText = it.filter(Char::isDigit) },
                                label = { Text("Demir") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = goldText,
                                onValueChange = { goldText = it.filter(Char::isDigit) },
                                label = { Text("Altın") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Seviye: ${LevelSystem.getTierInfo(levelNum).fullName}", color = AquaPrimary, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { if (levelNum > 1) levelNum-- }) { Text("-1") }
                                OutlinedButton(onClick = { if (levelNum < 40) levelNum++ }) { Text("+1") }
                            }
                        }
                        Button(
                            onClick = {
                                onEditResources(
                                    coinsText.toLongOrNull() ?: p.coins,
                                    p.gems,
                                    ironText.toIntOrNull() ?: p.iron,
                                    goldText.toIntOrNull() ?: p.gold,
                                    p.emerald,
                                    levelNum
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("offline_save_resources_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary, contentColor = OceanDeep)
                        ) {
                            Text("Kaynak & Seviyeyi Uygula", fontWeight = FontWeight.Bold)
                        }
                    }

                    // 3. Mob Summon / Kill (when inside 3D World)
                    item {
                        Text("Yaratık Yönetimi (Summon / Kill)", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (!inWorld) {
                            Text("Yaratık çağırmak için önce 3D haritaya gir.", color = TextMuted, fontSize = 12.sp)
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                            MobType.entries.forEach { mob ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(OceanCard)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(mob.displayName, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { onSummonMob(mob) },
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAction, contentColor = OceanDeep)
                                        ) { Text("Summon") }
                                        Button(
                                            onClick = { onKillMobs(mob) },
                                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger, contentColor = Color.White)
                                        ) { Text("Kill") }
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { onKillMobs(null) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Text("Tüm Yaratıkları Öldür (/kill @e)", color = CrimsonDanger, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // 4. Inventory Direct Editor
                    item {
                        Text("Envanter Kontrolü (+/-)", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    items(GameCatalog.items.take(8)) { item ->
                        val count = p.inventory.firstOrNull { it.itemId == item.id }?.count ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(OceanCard)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${item.name} ($count)", color = TextPrimary, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { onEditInventoryItem(item.id, -8) }) { Text("-8") }
                                Button(
                                    onClick = { onEditInventoryItem(item.id, 16) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary, contentColor = OceanDeep)
                                ) { Text("+16") }
                            }
                        }
                    }
                }
            }
        }
    }
}
