package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
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
import com.example.model.GameRoom
import com.example.model.ServerType
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState

@Composable
fun GameSelectDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onStartMatch: (ServerType, String, GameRoom?) -> Unit,
    onSpectateRoom: (GameRoom) -> Unit
) {
    var selectedServer by remember { mutableStateOf(ServerType.BEDWARS) }
    var selectedMap by remember(selectedServer) { mutableStateOf(selectedServer.matchMaps.first()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .widthIn(max = 720.dp),
            shape = RoundedCornerShape(18.dp),
            color = OceanSlate,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, OceanCardBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SUNUCULAR & MİNİ OYUNLAR",
                            style = MaterialTheme.typography.titleLarge,
                            color = AquaPrimary,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (uiState.isOffline) "Çevrimdışı Bot Modu — Tüm Haritalar Açık" else uiState.activeNetwork.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("close_game_select_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Selected Server Map Preview & Play Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = OceanCard),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedServer.displayName.uppercase(),
                                    color = selectedServer.accentColor,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = selectedServer.subtitle,
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            val canEnterRooms = uiState.isOffline || (!uiState.isMemberBannedActive && !uiState.isNetworkMaintenanceBlocked)
                            Button(
                                onClick = {
                                    onDismiss()
                                    onStartMatch(selectedServer, selectedMap, null)
                                },
                                enabled = canEnterRooms,
                                modifier = Modifier.height(48.dp).testTag("start_selected_server_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldAction,
                                    contentColor = OceanDeep
                                )
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.isOffline) "BOTLA BAŞLAT" else "OYNA / KATIL",
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Bekleme Alanı: ${selectedServer.waitingLobbyMap}",
                            color = GoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Map, contentDescription = null, tint = AquaPrimary, modifier = Modifier.size(16.dp))
                            selectedServer.matchMaps.forEach { mapOption ->
                                FilterChip(
                                    selected = selectedMap == mapOption,
                                    onClick = { selectedMap = mapOption },
                                    label = { Text(mapOption, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 7 Servers List + Active Multiplayer Rooms
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "Sunucu Seçimi (7 Sunucu)",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                    }
                    items(ServerType.entries) { srv ->
                        val isSel = srv == selectedServer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) srv.accentColor.copy(alpha = 0.2f) else OceanDeep.copy(alpha = 0.7f))
                                .border(
                                    width = if (isSel) 1.6.dp else 1.dp,
                                    color = if (isSel) srv.accentColor else OceanCardBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedServer = srv }
                                .padding(12.dp)
                                .testTag("server_item_${srv.code.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = srv.displayName,
                                    color = if (isSel) srv.accentColor else TextPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Bekleme: ${srv.waitingLobbyMap} • Maç: ${srv.matchMaps.joinToString(" / ")}",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = "Max ${srv.defaultMaxPlayers}",
                                color = GoldLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!uiState.isOffline) {
                        val matchingRooms = uiState.rooms.filter {
                            it.serverType == selectedServer.code && it.status != "FINISHED"
                        }
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Aktif Çok Oyunculu Odalar (${matchingRooms.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = AquaPrimary
                            )
                        }
                        if (matchingRooms.isEmpty()) {
                            item {
                                Text(
                                    text = "Bu sunucuda açık oda yok. Yukarıdan yeni oda kurabilirsin!",
                                    color = TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        } else {
                            items(matchingRooms) { room ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(OceanCard)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${room.serverTypeEnum.displayName} — ${room.mapName}",
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "Durum: ${room.status} • Oyuncu: ${room.playerCount}/${room.maxPlayers}",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (uiState.displayRank.canSpectateMatch) {
                                            IconButton(
                                                onClick = {
                                                    onDismiss()
                                                    onSpectateRoom(room)
                                                },
                                                enabled = !uiState.isMemberBannedActive && !uiState.isNetworkMaintenanceBlocked,
                                                modifier = Modifier.size(48.dp).testTag("spectate_room_${room.id}")
                                            ) {
                                                Icon(Icons.Filled.Visibility, contentDescription = "Maç İzle", tint = GoldAccent)
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                onDismiss()
                                                onStartMatch(room.serverTypeEnum, room.mapName, room)
                                            },
                                            enabled = !uiState.isMemberBannedActive && !uiState.isNetworkMaintenanceBlocked,
                                            colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary, contentColor = OceanDeep)
                                        ) {
                                            Text("Katıl", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
