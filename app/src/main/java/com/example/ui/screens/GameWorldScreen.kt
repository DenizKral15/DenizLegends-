package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.GameSessionController
import com.example.engine.MatchOutcome
import com.example.engine.MatchResultState
import com.example.engine.VoxelWorldRenderer
import com.example.engine.WorldSessionState
import com.example.model.*
import com.example.ui.components.ItemModelIcon
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun GameWorldScreen(
    uiState: MainUiState,
    sessionController: GameSessionController,
    onMoveInput: (forward: Float, strafe: Float) -> Unit,
    onFlyInput: (up: Boolean, down: Boolean) -> Unit,
    onLookDelta: (dx: Float, dy: Float) -> Unit,
    onJump: () -> Unit,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    onSelectSlot: (Int) -> Unit,
    onOpenInventory: () -> Unit,
    onSwapInventorySlots: (Int, Int) -> Unit,
    onCreativeSetSlot: (GameItem, Int) -> Unit,
    onClearSlot: (Int) -> Unit,
    onPlayerInteract: (WorldEntity, String, String) -> Unit,
    onSavePlot: () -> Unit,
    onOpenStaffPanel: () -> Unit,
    onOpenOfflineSandbox: () -> Unit,
    onSendChat: (String) -> Unit,
    onBuyMatchItem: (GameItem) -> Unit,
    onClaimBankBonus: () -> Unit,
    onVoteBuildBattle: (Int) -> Unit,
    onDismissWorldModal: () -> Unit,
    onReturnTo3DLobby: () -> Unit,
    onContinueSpectating: () -> Unit,
    onLeaveWorld: () -> Unit
) {
    val ws = uiState.worldSession
    var showChatDialog by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()

    BackHandler {
        onLeaveWorld()
    }

    val heldSlot = uiState.displayInventory.getOrNull(ws.selectedSlotIndex)
    val heldItem = heldSlot?.let { GameCatalog.findById(it.itemId) }

    val shakeOffsetX = if (ws.hurtShakeTimer > 0f) cos(ws.hurtShakeTimer * 58f) * 14f * ws.hurtShakeTimer else 0f
    val shakeOffsetY = if (ws.hurtShakeTimer > 0f) sin(ws.hurtShakeTimer * 49f) * 10f * ws.hurtShakeTimer else 0f

    Box(modifier = Modifier.fillMaxSize().background(OceanDeep)) {
        // 1. 3D Software Voxel Renderer Canvas + Drag-to-Look + Viewport Hurt Shake
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = shakeOffsetX
                    translationY = shakeOffsetY
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onLookDelta(dragAmount.x, dragAmount.y)
                    }
                }
                .testTag("voxel_world_canvas")
        ) {
            // Read frameVersion so Canvas invalidates at 60fps
            val _tick = ws.frameVersion
            with(VoxelWorldRenderer) {
                renderWorld(
                    camera = ws.camera,
                    blocks = sessionController.blocks,
                    entities = ws.entities,
                    targetedCoord = ws.targetedCoord,
                    swingProgress = ws.swingProgress,
                    heldItem = heldItem,
                    serverType = ws.serverType,
                    textMeasurer = textMeasurer,
                    hurtFlashAlpha = ws.hurtFlashAlpha,
                    hurtShakeTimer = ws.hurtShakeTimer
                )
            }
        }

        // 2. Top HUD Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Match / Map Info Card
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OceanDeep.copy(alpha = 0.78f))
                        .border(1.dp, ws.serverType.accentColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    val headerText = if (ws.isWaitingPhase) {
                        if (ws.isWaitingPaused || ws.waitingCountdownSec < 0) {
                            "${ws.serverType.displayName} • Bekleniyor (${ws.waitingPlayerCount}/${ws.maxWaitingPlayers})"
                        } else {
                            "${ws.serverType.displayName} • Başlıyor (${ws.waitingCountdownSec}s • ${ws.waitingPlayerCount}/${ws.maxWaitingPlayers})"
                        }
                    } else {
                        "${ws.serverType.displayName} • ${ws.mapName}"
                    }
                    Text(
                        text = headerText,
                        color = if (ws.isWaitingPhase && !ws.isWaitingPaused && ws.waitingCountdownSec in 1..3) GoldAccent else AquaPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("♥ ${ws.hp}/${ws.maxHp}", color = CrimsonDanger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        if (ws.gameMode == GameMode.SURVIVAL || ws.gameMode == GameMode.ADVENTURE) {
                            Text("🍗 ${ws.hunger}/20", color = AmberWarm, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text("Mod: ${ws.gameMode.displayName}", color = GoldLight, fontSize = 11.sp)
                        if (!uiState.isOffline) {
                            val onlineInRoom = ws.remotePlayers.count { it.alive }.coerceAtLeast(1)
                            Text("Oyuncu: $onlineInRoom", color = EmeraldAction, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        if (ws.isFlying) {
                            Text("UÇUŞ AÇIK", color = AquaPrimary, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                        if (ws.serverType == ServerType.BEDWARS) {
                            Text("Demir:${ws.iron} Altın:${ws.gold}", color = TextPrimary, fontSize = 11.sp)
                        }
                    }
                    if (ws.serverType == ServerType.BUILD_BATTLE && !ws.isWaitingPhase) {
                        Text(
                            text = "Tema: ${ws.buildTheme} • Süre: ${ws.matchTimerSec}s",
                            color = GoldAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    if (ws.isSpectating) {
                        Text(
                            text = "İZLEME MODU${ws.spectateTargetName?.let { ": $it" } ?: ""}",
                            color = AmberWarm,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
                }

                // Top-Right Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (ws.serverType == ServerType.CREATIVE || ws.serverType == ServerType.SKYBLOCK) {
                        HudIconButton(
                            icon = Icons.Filled.Save,
                            desc = "Arsayı Kaydet",
                            tint = EmeraldAction,
                            testTag = "save_plot_button",
                            onClick = onSavePlot
                        )
                    }
                    HudIconButton(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        desc = "Sohbet",
                        tint = AquaPrimary,
                        testTag = "open_chat_button",
                        onClick = { showChatDialog = true }
                    )
                    if (uiState.isOffline) {
                        HudIconButton(
                            icon = Icons.Filled.Tune,
                            desc = "Sandbox",
                            tint = EmeraldAction,
                            testTag = "world_sandbox_button",
                            onClick = onOpenOfflineSandbox
                        )
                    } else if (uiState.displayRank.canAccessStaffPanel) {
                        HudIconButton(
                            icon = Icons.Filled.AdminPanelSettings,
                            desc = "Yetkili Paneli",
                            tint = CrimsonDanger,
                            testTag = "world_staff_button",
                            onClick = onOpenStaffPanel
                        )
                    }
                    HudIconButton(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        desc = "Çık",
                        tint = Color.White,
                        testTag = "leave_world_button",
                        onClick = onLeaveWorld
                    )
                }
            }

            // Bedwars Beds Status Strip
            if (!ws.isWaitingPhase && ws.beds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ws.beds.forEach { bed ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(OceanDeep.copy(alpha = 0.78f))
                                .border(1.dp, if (bed.intact) bed.color else Color.Gray, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${bed.label}: ${if (bed.intact) "✔" else "✖"}",
                                color = if (bed.intact) bed.color else Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Event Banner / Winner Banner
            val bannerText = ws.winnerName ?: ws.eventMessage
            if (!bannerText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OceanDeep.copy(alpha = 0.75f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = bannerText,
                        color = if (ws.winnerName != null) GoldAccent else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 3. Bottom Controls: Virtual Joystick (Left), Action Buttons (Right), Hotbar (Center Bottom)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
        ) {
            // Virtual Joystick
            VirtualJoystick(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 64.dp),
                onMove = onMoveInput
            )

            // Right Action Cluster
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 86.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (ws.gameMode == GameMode.CREATIVE || ws.gameMode == GameMode.SPECTATOR) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HoldableFlightArrowButton(
                            icon = Icons.Filled.KeyboardArrowUp,
                            label = "Yüksel",
                            color = AquaPrimary,
                            testTag = "fly_up_arrow",
                            onHoldChanged = { held -> onFlyInput(held, false) }
                        )
                        HoldableFlightArrowButton(
                            icon = Icons.Filled.KeyboardArrowDown,
                            label = "Alçal",
                            color = AmberWarm,
                            testTag = "fly_down_arrow",
                            onHoldChanged = { held -> onFlyInput(false, held) }
                        )
                    }
                }
                val secondaryLabel = when {
                    ws.targetedEntity?.kind == EntityKind.PLAYER || ws.targetedEntity?.kind == EntityKind.BOT -> "ETKİLEŞ"
                    heldItem?.foodRestore ?: 0 > 0 -> "YE / KOY"
                    else -> "KOY / AÇ"
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionGameButton(
                        label = "VUR / KIR",
                        color = CrimsonDanger,
                        testTag = "primary_action_button",
                        onClick = onPrimaryAction
                    )
                    ActionGameButton(
                        label = secondaryLabel,
                        color = EmeraldAction,
                        testTag = "secondary_action_button",
                        onClick = onSecondaryAction
                    )
                }
                ActionGameButton(
                    label = if (ws.gameMode == GameMode.CREATIVE || ws.gameMode == GameMode.SPECTATOR) {
                        if (ws.isFlying) "ZIPLA (Uçuş Açık)" else "ZIPLA (2x: Uç)"
                    } else "ZIPLA",
                    color = if (ws.isFlying) GoldAccent else AquaPrimary,
                    testTag = "jump_button",
                    onClick = onJump
                )
            }

            // Bottom Center: Survival Hearts + Hunger Bar, Held Item Label, and 9-Slot Minecraft Hotbar + (...) Inventory/Creative Button
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (ws.targetedEntity != null && (ws.targetedEntity.kind == EntityKind.PLAYER || ws.targetedEntity.kind == EntityKind.BOT)) {
                    Text(
                        text = "${ws.targetedEntity.name} • ETKİLEŞ için bas",
                        color = AquaPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OceanDeep.copy(alpha = 0.8f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                } else if (heldItem != null) {
                    Text(
                        text = heldItem.name,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OceanDeep.copy(alpha = 0.65f))
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                    )
                }

                if (ws.gameMode == GameMode.SURVIVAL || ws.gameMode == GameMode.ADVENTURE) {
                    SurvivalHeartsAndHungerBar(hp = ws.hp, maxHp = ws.maxHp, hunger = ws.hunger)
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(OceanDeep.copy(alpha = 0.88f))
                        .border(1.5.dp, OceanCardBorder, RoundedCornerShape(10.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val slots = uiState.displayInventory
                    (0..8).forEach { idx ->
                        val slot = slots.getOrNull(idx)
                        val item = slot?.let { GameCatalog.findById(it.itemId) }
                        val selected = idx == ws.selectedSlotIndex
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) AquaPrimary.copy(alpha = 0.25f) else OceanSlate)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) GoldAccent else OceanCardBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectSlot(idx) }
                                .testTag("hotbar_slot_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
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
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)
                                )
                            } else {
                                Text(
                                    text = "${idx + 1}",
                                    color = TextMuted.copy(alpha = 0.55f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // "..." Inventory / Creative Menu Button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (ws.gameMode == GameMode.CREATIVE) EmeraldAction.copy(alpha = 0.25f) else GoldAccent.copy(alpha = 0.22f))
                            .border(
                                width = 1.5.dp,
                                color = if (ws.gameMode == GameMode.CREATIVE) EmeraldAction else GoldAccent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(onClick = onOpenInventory)
                            .testTag("open_inventory_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (ws.gameMode == GameMode.CREATIVE) Icons.Filled.GridView else Icons.Filled.Inventory2,
                                contentDescription = if (ws.gameMode == GameMode.CREATIVE) "Yaratıcı Menü" else "Envanter",
                                tint = if (ws.gameMode == GameMode.CREATIVE) EmeraldAction else GoldAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (ws.gameMode == GameMode.CREATIVE) "Yaratıcı" else "Çanta",
                                color = TextPrimary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 4. Match Victory / Defeat Return-to-Lobby Overlay
            val matchResult = ws.matchResult
            if (matchResult != null && !matchResult.dismissedToSpectate) {
                MatchResultOverlayCard(
                    result = matchResult,
                    modifier = Modifier.align(Alignment.Center),
                    onReturnTo3DLobby = onReturnTo3DLobby,
                    onContinueSpectating = onContinueSpectating,
                    onLeaveToMenu = onLeaveWorld
                )
            }
        }
    }

    WorldActionModalHost(
        uiState = uiState,
        worldSession = ws,
        onDismiss = onDismissWorldModal,
        onBuyMatchItem = onBuyMatchItem,
        onClaimBankBonus = onClaimBankBonus,
        onVoteBuildBattle = onVoteBuildBattle,
        onSelectSlot = onSelectSlot,
        onSwapInventorySlots = onSwapInventorySlots,
        onCreativeSetSlot = onCreativeSetSlot,
        onClearSlot = onClearSlot,
        onPlayerInteract = onPlayerInteract
    )

    if (showChatDialog) {
        RoomChatDrawerDialog(
            uiState = uiState,
            onDismiss = { showChatDialog = false },
            onSendMessage = onSendChat
        )
    }
}

@Composable
private fun VirtualJoystick(
    modifier: Modifier = Modifier,
    onMove: (forward: Float, strafe: Float) -> Unit
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadius = 46f

    Box(
        modifier = modifier
            .size(116.dp)
            .clip(CircleShape)
            .background(OceanDeep.copy(alpha = 0.52f))
            .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val cand = knobOffset + dragAmount
                    val len = hypot(cand.x, cand.y)
                    val clamped = if (len > maxRadius) cand * (maxRadius / len) else cand
                    knobOffset = clamped
                    onMove(-clamped.y / maxRadius, clamped.x / maxRadius)
                }
            }
            .testTag("virtual_joystick"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(knobOffset.x.roundToInt(), knobOffset.y.roundToInt()) }
                .size(46.dp)
                .clip(CircleShape)
                .background(AquaPrimary.copy(alpha = 0.85f))
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

@Composable
private fun HudIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(OceanDeep.copy(alpha = 0.78f))
            .border(1.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .testTag(testTag)
    ) {
        Icon(icon, contentDescription = desc, tint = tint)
    }
}

@Composable
private fun ActionGameButton(
    label: String,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minWidth = 84.dp, minHeight = 48.dp).testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.9f), contentColor = OceanDeep),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, fontWeight = FontWeight.Black, fontSize = 11.sp)
    }
}

@Composable
private fun HoldableFlightArrowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    testTag: String,
    onHoldChanged: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Surface(
        color = if (pressed) color.copy(alpha = 0.35f) else OceanDeep.copy(alpha = 0.85f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, color),
        modifier = Modifier
            .size(width = 58.dp, height = 48.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onHoldChanged(true)
                    waitForUpOrCancellation()
                    pressed = false
                    onHoldChanged(false)
                }
            }
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
            Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SurvivalHeartsAndHungerBar(hp: Int, maxHp: Int, hunger: Int) {
    val heartCount = (maxHp / 2).coerceAtLeast(10)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(OceanDeep.copy(alpha = 0.72f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("survival_status_bars"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 10 Hearts
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until heartCount) {
                val threshold = (i + 1) * 2
                val tint = when {
                    hp >= threshold -> CrimsonDanger
                    hp == threshold - 1 -> CrimsonDanger.copy(alpha = 0.55f)
                    else -> Color(0xFF3A3F4B)
                }
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
            }
        }
        // 10 Hunger drumsticks/bars
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 10) {
                val threshold = (i + 1) * 2
                val color = when {
                    hunger >= threshold -> AmberWarm
                    hunger == threshold - 1 -> AmberWarm.copy(alpha = 0.55f)
                    else -> Color(0xFF3A3F4B)
                }
                Box(
                    modifier = Modifier
                        .size(width = 8.dp, height = 10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun MatchResultOverlayCard(
    result: MatchResultState,
    modifier: Modifier = Modifier,
    onReturnTo3DLobby: () -> Unit,
    onContinueSpectating: () -> Unit,
    onLeaveToMenu: () -> Unit
) {
    val isVictory = result.outcome == MatchOutcome.WON
    val accent = if (isVictory) GoldAccent else CrimsonDanger
    Card(
        modifier = modifier
            .fillMaxWidth(0.88f)
            .widthIn(max = 420.dp)
            .testTag("match_result_overlay"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = OceanSlate.copy(alpha = 0.96f)),
        border = androidx.compose.foundation.BorderStroke(2.dp, accent)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isVictory) Icons.Filled.EmojiEvents else Icons.Filled.HeartBroken,
                contentDescription = result.title,
                tint = accent,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = result.title,
                color = accent,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp
            )
            Text(
                text = result.subtitle,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                color = OceanDeep.copy(alpha = 0.85f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AquaPrimary.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "${result.returnCountdownSec} saniye içinde otomatik 3D Lobiye dönülüyor...",
                    color = AquaPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onReturnTo3DLobby,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("match_result_lobby_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAction, contentColor = OceanDeep)
                ) {
                    Text("3D Lobiye Dön", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onLeaveToMenu,
                    modifier = Modifier.weight(1f).height(48.dp).testTag("match_result_menu_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Text("Ana Menü", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            if (!isVictory) {
                TextButton(
                    onClick = onContinueSpectating,
                    modifier = Modifier.testTag("match_result_spectate_button")
                ) {
                    Text("İzlemeye Devam Et", color = GoldLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
