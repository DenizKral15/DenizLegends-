package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
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
import com.example.model.GameNetwork
import com.example.model.Rank
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState

@Composable
fun NetworkManagerDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onSelectNetwork: (GameNetwork) -> Unit,
    onCreateIndependentNetwork: (name: String, desc: String, policy: String) -> Unit,
    onToggleMaintenance: (GameNetwork) -> Unit,
    onDeleteNetwork: (GameNetwork) -> Unit
) {
    var showCreateForm by remember { mutableStateOf(false) }
    var netName by remember { mutableStateOf("") }
    var netDesc by remember { mutableStateOf("") }
    var netPolicy by remember { mutableStateOf("Saygılı oyun, hile ve spam yasaktır.") }
    var pendingDeleteNet by remember { mutableStateOf<GameNetwork?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.86f)
                .widthIn(max = 680.dp),
            shape = RoundedCornerShape(18.dp),
            color = OceanSlate,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, OceanCardBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AĞLAR (RESMİ & BAĞIMSIZ)",
                            style = MaterialTheme.typography.titleLarge,
                            color = AquaPrimary,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Her ağın kendi yönetimi, kuralları ve hesap verisi vardır",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("close_network_dialog")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Kapat", tint = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { showCreateForm = !showCreateForm },
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("toggle_create_network_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = OceanDeep)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showCreateForm) "Formu Gizle" else "Bağımsız Network Oluştur (Kurucu Ol)",
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                if (showCreateForm) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = OceanCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = netName,
                                onValueChange = { netName = it },
                                label = { Text("Network Adı (örn. KorsanCraft)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("new_network_name_input")
                            )
                            OutlinedTextField(
                                value = netDesc,
                                onValueChange = { netDesc = it },
                                label = { Text("Açıklama") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = netPolicy,
                                onValueChange = { netPolicy = it },
                                label = { Text("Network Kuralları / Politikası") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (netName.trim().length >= 3) {
                                        onCreateIndependentNetwork(netName, netDesc, netPolicy)
                                        netName = ""
                                        netDesc = ""
                                        showCreateForm = false
                                    }
                                },
                                modifier = Modifier.align(Alignment.End).testTag("submit_create_network_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAction, contentColor = OceanDeep)
                            ) {
                                Text("Oluştur ve Katıl", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.networks) { net ->
                        val active = net.id == uiState.activeNetworkId
                        val accent = if (net.official) EmeraldAction else AmberWarm
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) accent.copy(alpha = 0.16f) else OceanDeep.copy(alpha = 0.75f))
                                .border(1.4.dp, if (active) accent else OceanCardBorder, RoundedCornerShape(12.dp))
                                .clickable { onSelectNetwork(net) }
                                .padding(12.dp)
                                .testTag("network_card_${net.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (net.official) {
                                        Icon(Icons.Filled.Security, contentDescription = null, tint = EmeraldAction, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        text = net.name,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                    if (net.maintenanceMode) {
                                        Surface(
                                            color = AmberWarm.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarm)
                                        ) {
                                            Text(
                                                text = "BAKIM",
                                                color = AmberWarm,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (active) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                                        Text("AKTİF", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text(
                                        text = if (net.official) "Resmi Ağ" else "Bağımsız Ağ",
                                        color = accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (net.description.isNotBlank()) {
                                Text(net.description, color = TextSecondary, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Kurucu: ${net.founderName} • Politika: ${net.policyText}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )

                            val uid = uiState.userAccount?.userId
                            val isFounderOrOfficialKurucu = (uid != null && net.founderUid == uid) ||
                                (active && uiState.displayRank == Rank.KURUCU)
                            val canMaint = isFounderOrOfficialKurucu || (active && uiState.displayRank.canManageMaintenance)
                            val canDel = !net.official && net.id != "official" &&
                                (isFounderOrOfficialKurucu || (active && uiState.displayRank.canDeleteNetwork))

                            if (canMaint || canDel) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (canMaint) {
                                        OutlinedButton(
                                            onClick = { onToggleMaintenance(net) },
                                            modifier = Modifier.weight(1f).testTag("toggle_maintenance_${net.id}"),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (net.maintenanceMode) EmeraldAction else AmberWarm)
                                        ) {
                                            Icon(Icons.Filled.Build, contentDescription = null, tint = if (net.maintenanceMode) EmeraldAction else AmberWarm, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (net.maintenanceMode) "Bakımı Kapat" else "Bakıma Al",
                                                color = if (net.maintenanceMode) EmeraldAction else AmberWarm,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    if (canDel) {
                                        OutlinedButton(
                                            onClick = { pendingDeleteNet = net },
                                            modifier = Modifier.weight(1f).testTag("delete_network_${net.id}"),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonDanger)
                                        ) {
                                            Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = CrimsonDanger, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Sunucuyu Sil", color = CrimsonDanger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

    pendingDeleteNet?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteNet = null },
            containerColor = OceanSlate,
            title = { Text("Sunucuyu Sil", color = CrimsonDanger, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    text = "${target.name} sunucusunu kalıcı olarak silmek istediğine emin misin? Bu işlem geri alınamaz.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteNetwork(target)
                        pendingDeleteNet = null
                    },
                    modifier = Modifier.testTag("confirm_delete_network_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger, contentColor = Color.White)
                ) {
                    Text("Evet, Sil", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteNet = null }) {
                    Text("Vazgeç", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun IndependentNetworkWarningDialog(
    targetNetwork: GameNetwork,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OceanSlate,
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Uyarı",
                tint = AmberWarm,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Uyarı (Bağımsız Network)",
                color = GoldAccent,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DenizCraft Resmi Network sunucularına bağlı olmayan farklı bir ağa geçmek üzeresin. Bu networkların kendi kuralları yetkilileri kurucuları moderasyon ve yönetim sistemleri olabilir.",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Hedef Ağ: ${targetNetwork.name} (Kurucu: ${targetNetwork.founderName})",
                    color = AquaPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_independent_network_button"),
                colors = ButtonDefaults.buttonColors(containerColor = AmberWarm, contentColor = Color.White)
            ) {
                Text("Ağa Geç", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç", color = TextSecondary)
            }
        }
    )
}
