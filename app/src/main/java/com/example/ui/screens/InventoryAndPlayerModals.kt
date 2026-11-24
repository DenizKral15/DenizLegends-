package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.*
import com.example.ui.components.BlockyAvatar
import com.example.ui.components.ItemModelIcon
import com.example.ui.components.LevelTierBadge
import com.example.ui.components.RankTagAndName
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState

@Composable
fun MinecraftInventoryDialog(
    uiState: MainUiState,
    selectedHotbarSlot: Int,
    onSelectHotbarSlot: (Int) -> Unit,
    onSwapSlots: (Int, Int) -> Unit,
    onClearSlot: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val slots = uiState.displayInventory
    var pickFromIdx by remember { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .testTag("minecraft_inventory_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = OceanSlate,
            border = BorderStroke(2.dp, AquaPrimary)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Envanter & Çanta", color = GoldAccent, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text(
                            text = if (pickFromIdx == null) "Taşımak istediğin eşyaya dokun"
                            else "Hedef slota dokun (Slot ${pickFromIdx!! + 1} seçili)",
                            color = if (pickFromIdx == null) TextSecondary else AquaPrimary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = TextPrimary)
                    }
                }

                // Backpack slots (index 9..end)
                Text("Sırt Çantası", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                val backpack = if (slots.size > 9) slots.drop(9) else emptyList()
                if (backpack.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(OceanDeep.copy(alpha = 0.55f))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Sırt çantası boş — eşyaların ilk 9 Hotbar slotunda.", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.heightIn(max = 170.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(backpack) { relIdx, slot ->
                            val realIdx = 9 + relIdx
                            InventoryGridTile(
                                slot = slot,
                                slotNumber = realIdx + 1,
                                isSelected = pickFromIdx == realIdx,
                                isActiveHotbar = false,
                                testTag = "backpack_slot_$realIdx",
                                onClick = {
                                    val from = pickFromIdx
                                    if (from == null) {
                                        pickFromIdx = realIdx
                                    } else {
                                        onSwapSlots(from, realIdx)
                                        pickFromIdx = null
                                    }
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = OceanCardBorder)

                // Hotbar slots (0..8)
                Text("Hotbar (1 - 9)", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (0..8).forEach { idx ->
                        val slot = slots.getOrNull(idx)
                        InventoryGridTile(
                            slot = slot,
                            slotNumber = idx + 1,
                            isSelected = pickFromIdx == idx,
                            isActiveHotbar = idx == selectedHotbarSlot,
                            testTag = "inv_hotbar_slot_$idx",
                            onClick = {
                                val from = pickFromIdx
                                if (from == null) {
                                    if (slot != null) {
                                        pickFromIdx = idx
                                    }
                                    onSelectHotbarSlot(idx)
                                } else {
                                    onSwapSlots(from, idx)
                                    onSelectHotbarSlot(idx)
                                    pickFromIdx = null
                                }
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val selSlot = slots.getOrNull(selectedHotbarSlot)
                    val selItem = selSlot?.let { GameCatalog.findById(it.itemId) }
                    Text(
                        text = selItem?.name ?: "Seçili slot boş",
                        color = AquaPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (selSlot != null) {
                        OutlinedButton(
                            onClick = {
                                onClearSlot(selectedHotbarSlot)
                                pickFromIdx = null
                            }
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Slotu Sil", tint = CrimsonDanger, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sil", color = CrimsonDanger, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreativeMenuDialog(
    uiState: MainUiState,
    selectedHotbarSlot: Int,
    onSelectHotbarSlot: (Int) -> Unit,
    onCreativeSetSlot: (GameItem, Int) -> Unit,
    onClearSlot: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<ItemCategory?>(null) }
    var query by remember { mutableStateOf("") }
    val filteredItems = remember(selectedCategory, query) {
        GameCatalog.items.filter { item ->
            (selectedCategory == null || item.category == selectedCategory) &&
                (query.isBlank() || item.name.contains(query, ignoreCase = true))
        }
    }
    val slots = uiState.displayInventory

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .testTag("creative_menu_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = OceanSlate,
            border = BorderStroke(2.dp, EmeraldAction)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Yaratıcı Menü (Creative)", color = EmeraldAction, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text("Sınırsız blok ve eşya al • Hedef slot: ${selectedHotbarSlot + 1}", color = TextSecondary, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = TextPrimary)
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Blok, kılıç veya eşya ara...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AquaPrimary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("creative_search_input")
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("Tümü", fontSize = 11.sp) }
                        )
                    }
                    items(ItemCategory.entries) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(filteredItems) { _, item ->
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(OceanCard)
                                .border(1.dp, OceanCardBorder, RoundedCornerShape(10.dp))
                                .clickable { onCreativeSetSlot(item, selectedHotbarSlot) }
                                .padding(8.dp)
                                .testTag("creative_item_${item.id}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OceanDeep.copy(alpha = 0.7f))
                                    .border(1.dp, OceanCardBorder, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                ItemModelIcon(item = item, modifier = Modifier.size(30.dp))
                            }
                            Text(
                                text = item.name,
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val badge = when {
                                item.blockType != null -> "+64"
                                item.foodRestore > 0 -> "+64"
                                else -> "+1"
                            }
                            Text(badge, color = EmeraldAction, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                HorizontalDivider(color = OceanCardBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hotbar Hedefi Seç (1-9):", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onClearSlot(selectedHotbarSlot) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Slotu Temizle", tint = CrimsonDanger, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Çöp", color = CrimsonDanger, fontSize = 11.sp)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (0..8).forEach { idx ->
                        val slot = slots.getOrNull(idx)
                        InventoryGridTile(
                            slot = slot,
                            slotNumber = idx + 1,
                            isSelected = false,
                            isActiveHotbar = idx == selectedHotbarSlot,
                            testTag = "creative_target_slot_$idx",
                            onClick = { onSelectHotbarSlot(idx) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerInteractDialog(
    target: WorldEntity,
    heldItemName: String?,
    onAction: (actionType: String, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reportReason by remember { mutableStateOf("") }
    var showReportBox by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("player_interact_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = OceanSlate,
            border = BorderStroke(2.dp, AquaPrimary)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val skinIdx = SKIN_PALETTES.indexOfFirst { it.second == target.bodyColor }.coerceAtLeast(0)
                        BlockyAvatar(skinIndex = skinIdx, rank = target.rank, size = 48.dp)
                        Column {
                            RankTagAndName(rank = target.rank, playerName = target.name, compact = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                LevelTierBadge(level = target.level)
                                Text("♥ ${target.hp}/${target.maxHp}", color = CrimsonDanger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = TextPrimary)
                    }
                }

                HorizontalDivider(color = OceanCardBorder)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onAction("WAVE", "") },
                        modifier = Modifier.weight(1f).testTag("interact_wave_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary, contentColor = OceanDeep)
                    ) {
                        Icon(Icons.Filled.WavingHand, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("El Salla", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onAction("GIFT_COIN", "") },
                        modifier = Modifier.weight(1f).testTag("interact_gift_coin_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = OceanDeep)
                    ) {
                        Icon(Icons.Filled.MonetizationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+25 Coin", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onAction("GIFT_ITEM", "") },
                        enabled = heldItemName != null,
                        modifier = Modifier.weight(1f).testTag("interact_gift_item_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAction, contentColor = OceanDeep)
                    ) {
                        Icon(Icons.Filled.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(heldItemName?.take(10) ?: "Eşya Ver", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                    }
                    Button(
                        onClick = { onAction("HIT", "") },
                        modifier = Modifier.weight(1f).testTag("interact_attack_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger, contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Saldır (PvP)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (target.kind == EntityKind.PLAYER) {
                    if (target.rank.isProtectedFromPunishment) {
                        Text("Kurucu rütbesi rapor korumasına sahiptir.", color = GoldAccent, fontSize = 11.sp)
                    } else if (!showReportBox) {
                        OutlinedButton(
                            onClick = { showReportBox = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Oyuncuyu Raporla", color = AmberWarm, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        OutlinedTextField(
                            value = reportReason,
                            onValueChange = { reportReason = it },
                            placeholder = { Text("Rapor sebebi...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onAction("REPORT", reportReason.ifBlank { "Oyun içi kural ihlali" }) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarm, contentColor = OceanDeep)
                        ) {
                            Text("Raporu Gönder", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryGridTile(
    slot: InventorySlot?,
    slotNumber: Int,
    isSelected: Boolean,
    isActiveHotbar: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val item = slot?.let { GameCatalog.findById(it.itemId) }
    val borderColor = when {
        isSelected -> EmeraldAction
        isActiveHotbar -> GoldAccent
        else -> OceanCardBorder
    }
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) EmeraldAction.copy(alpha = 0.22f) else if (isActiveHotbar) AquaPrimary.copy(alpha = 0.22f) else OceanDeep)
            .border(if (isSelected || isActiveHotbar) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$slotNumber",
            color = TextMuted,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(3.dp)
        )
        if (slot != null) {
            ItemModelIcon(
                item = item,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "${slot.count}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp)
            )
        }
    }
}
