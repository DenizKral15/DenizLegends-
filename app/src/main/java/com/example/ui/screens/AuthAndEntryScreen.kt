package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Rank
import com.example.ui.components.BlockyAvatar
import com.example.ui.theme.*
import com.example.viewmodel.MainUiState

@Composable
fun AuthAndEntryScreen(
    uiState: MainUiState,
    onSilentSignInCheck: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onRequestOfflineMode: () -> Unit,
    onConfirmOfflineMode: () -> Unit,
    onDismissOfflineWarning: () -> Unit
) {
    LaunchedEffect(Unit) {
        onSilentSignInCheck()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF071526), Color(0xFF0E2C4D), Color(0xFF13486E))
                )
            )
    ) {
        // Voxel horizon decorative grid
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val blocks = listOf(
                Triple(0.08f, 0.72f, Color(0xFF1F618D)),
                Triple(0.18f, 0.78f, Color(0xFF2E86C1)),
                Triple(0.80f, 0.70f, Color(0xFF1ABC9C)),
                Triple(0.88f, 0.76f, Color(0xFF16A085)),
                Triple(0.45f, 0.85f, Color(0xFF117864))
            )
            blocks.forEach { (fx, fy, c) ->
                val sz = 54f
                drawRect(c.copy(alpha = 0.35f), Offset(w * fx, h * fy), Size(sz, sz))
                drawRect(Color.White.copy(alpha = 0.14f), Offset(w * fx, h * fy), Size(sz, 10f))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Connectivity Indicator
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(OceanSlate.copy(alpha = 0.85f))
                    .border(
                        1.dp,
                        if (uiState.isInternetAvailable) EmeraldAction else AmberWarm,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isInternetAvailable) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                    contentDescription = "İnternet durumu",
                    tint = if (uiState.isInternetAvailable) EmeraldAction else AmberWarm,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (uiState.isInternetAvailable) "Çevrimiçi Bağlantı Hazır" else "İnternet Yok — Çevrimdışı Mod Önerilir",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
            }

            // Center Hero Game Identity
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 480.dp)
            ) {
                BlockyAvatar(
                    skinIndex = 0,
                    rank = Rank.KURUCU,
                    size = 148.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "DENİZCRAFT",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = AquaPrimary
                )
                Text(
                    text = "FIGHT AND BUILD",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = GoldAccent
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Bedwars • Skywars • Skyblock • Survival • BuildBattle • Creative",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // Bottom Action Card
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(OceanSlate.copy(alpha = 0.92f))
                    .border(1.5.dp, OceanCardBorder, RoundedCornerShape(18.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.authError != null) {
                    Text(
                        text = uiState.authError,
                        color = CrimsonDanger,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = onGoogleSignIn,
                    enabled = !uiState.isAuthBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_signin_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldAction,
                        contentColor = OceanDeep
                    )
                ) {
                    if (uiState.isAuthBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = OceanDeep,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    } else {
                        Icon(Icons.Filled.SportsEsports, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "GOOGLE İLE ÇOK OYUNCULU OYNA",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }

                OutlinedButton(
                    onClick = onRequestOfflineMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("offline_mode_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, GoldAccent)
                ) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ÇEVRİMDIŞI MOD (BOTLARLA OYNA)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    if (uiState.pendingOfflineWarning) {
        OfflineModeWarningDialog(
            onConfirm = onConfirmOfflineMode,
            onDismiss = onDismissOfflineWarning
        )
    }
}

@Composable
fun OfflineModeWarningDialog(
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
                text = "Uyarı (Çevrimdışı mod)",
                color = GoldAccent,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Çevrimdışı moda geçmek üzeresin. Bu modda rapor ceza ve destek sistemleri geçersizdir ve tüm ilerleme istemci tarafında tutulur.",
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_offline_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AmberWarm,
                    contentColor = Color.White
                )
            ) {
                Text("Çevrimdışı Moda Geç", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_offline_button")
            ) {
                Text("İptal", color = TextSecondary)
            }
        }
    )
}
