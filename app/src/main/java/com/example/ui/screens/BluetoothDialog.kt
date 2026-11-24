package com.example.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BluetoothMultiplayerManager
import com.example.data.DiscoveredBlePeer
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState

@Composable
fun BluetoothDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val btManager = remember(context) { BluetoothMultiplayerManager(context) }
    val btState by btManager.state.collectAsStateWithLifecycle()

    DisposableEffect(btManager) {
        btManager.registerReceiver()
        onDispose {
            btManager.release()
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        btManager.refreshHardwareAndPermissions()
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        btManager.refreshHardwareAndPermissions()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.86f)
                .widthIn(max = 700.dp),
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = AquaPrimary, modifier = Modifier.size(26.dp))
                        Column {
                            Text("BLUETOOTH & YAKIN CİHAZLAR", color = AquaPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text("BLE Tarama, Yayın (Host) ve GATT Bağlantısı", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).testTag("close_bluetooth_dialog")) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Real Hardware & Virtual Device Diagnostics Card
                Card(colors = CardDefaults.cardColors(containerColor = OceanCard), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(btState.statusLog, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill("BT Donanım", btState.hasBluetoothFeature)
                            StatusPill("BLE", btState.hasBleFeature)
                            StatusPill("Adaptör", btState.adapterAvailable)
                            StatusPill("Açık", btState.isBluetoothEnabled)
                        }

                        if (!btState.hasRequiredPermissions) {
                            Button(
                                onClick = {
                                    permissionsLauncher.launch(BluetoothMultiplayerManager.requiredRuntimePermissions())
                                },
                                modifier = Modifier.fillMaxWidth().testTag("request_bt_permissions_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = OceanDeep)
                            ) {
                                Text("Bluetooth İzinlerini Ver", fontWeight = FontWeight.ExtraBold)
                            }
                        } else if (btState.adapterAvailable && !btState.isBluetoothEnabled) {
                            Button(
                                onClick = {
                                    enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                },
                                modifier = Modifier.fillMaxWidth().testTag("enable_bluetooth_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary, contentColor = OceanDeep)
                            ) {
                                Text("Bluetooth'u Aç", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Row: Scan + Advertise
                val canOperate = btState.adapterAvailable && btState.isBluetoothEnabled && btState.hasRequiredPermissions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (btState.isScanning) btManager.stopBleScan() else btManager.startBleScan()
                        },
                        enabled = canOperate,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("bt_scan_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (btState.isScanning) CrimsonDanger else EmeraldAction,
                            contentColor = if (btState.isScanning) Color.White else OceanDeep
                        )
                    ) {
                        Icon(if (btState.isScanning) Icons.Filled.Stop else Icons.Filled.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (btState.isScanning) "Taramayı Durdur" else "Cihaz Tara", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            btManager.toggleBleAdvertising(uiState.displayName, uiState.activeNetwork.name)
                        },
                        enabled = canOperate,
                        modifier = Modifier.weight(1f).height(48.dp).testTag("bt_advertise_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (btState.isAdvertising) AmberWarm else AquaPrimary,
                            contentColor = OceanDeep
                        )
                    ) {
                        Icon(Icons.Filled.Sensors, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (btState.isAdvertising) "Yayını Kapat" else "BLE Yayın Aç", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Connected GATT Peer Banner
                if (btState.connectedPeerName != null || btState.lastGattMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = OceanDeep)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            btState.connectedPeerName?.let { peer ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Bağlı: $peer", color = EmeraldAction, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Button(
                                        onClick = { btManager.sendRoomInviteOverGatt("Davet:${uiState.displayName}@${uiState.activeNetwork.name}") },
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = OceanDeep)
                                    ) {
                                        Text("Oda Daveti Gönder", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            btState.lastGattMessage?.let { msg ->
                                Text(msg, color = AquaPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Taranan BLE Cihazları (${btState.scannedDevices.size})", color = AquaPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (btState.scannedDevices.isEmpty()) {
                        item {
                            Text(
                                text = if (!btState.adapterAvailable) {
                                    "Sanal cihaz adaptörü algılanmadı. Android Emulator'de sanal BLE donanımı aktif edildiğinde cihazlar burada listelenir."
                                } else {
                                    "Yakında keşfedilen BLE cihazı yok."
                                },
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        items(btState.scannedDevices, key = { "scan_${it.address}" }) { peer ->
                            BluetoothPeerRow(peer = peer, canConnect = canOperate) {
                                btManager.connectToPeer(peer.address)
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Eşleşmiş Bluetooth Cihazları (${btState.bondedDevices.size})", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (btState.bondedDevices.isEmpty()) {
                        item {
                            Text("Eşleşmiş Bluetooth cihazı veya oyun kolu bulunamadı.", color = TextMuted, fontSize = 12.sp)
                        }
                    } else {
                        items(btState.bondedDevices, key = { "bond_${it.address}" }) { peer ->
                            BluetoothPeerRow(peer = peer, canConnect = canOperate) {
                                btManager.connectToPeer(peer.address)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, ok: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (ok) EmeraldAction.copy(alpha = 0.2f) else CrimsonDanger.copy(alpha = 0.2f))
            .border(1.dp, if (ok) EmeraldAction else CrimsonDanger, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = "$label: ${if (ok) "Var" else "Yok"}",
            color = if (ok) EmeraldAction else CrimsonDanger,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BluetoothPeerRow(
    peer: DiscoveredBlePeer,
    canConnect: Boolean,
    onConnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(OceanCard)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(peer.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (peer.isDenizCraftPeer) {
                    Text("DENİZCRAFT", color = AquaPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
            Text(
                text = if (peer.rssi != 0) "${peer.address} • ${peer.rssi} dBm" else peer.address,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
        Button(
            onClick = onConnect,
            enabled = canConnect,
            colors = ButtonDefaults.buttonColors(containerColor = AquaPrimary, contentColor = OceanDeep)
        ) {
            Text("Bağlan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
