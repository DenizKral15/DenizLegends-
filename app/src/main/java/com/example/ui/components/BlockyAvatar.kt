package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.Rank
import com.example.model.SKIN_PALETTES

@Composable
fun BlockyAvatar(
    skinIndex: Int,
    rank: Rank,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    onClick: (() -> Unit)? = null
) {
    val bodyColor = SKIN_PALETTES.getOrNull(skinIndex)?.second ?: Color(0xFF00ACC1)
    val darkBody = Color(
        red = (bodyColor.red * 0.72f),
        green = (bodyColor.green * 0.72f),
        blue = (bodyColor.blue * 0.72f)
    )
    val skinTone = Color(0xFFFFCC99)
    val pantsColor = Color(0xFF263238)

    Box(
        modifier = modifier
            .size(size)
            .testTag("avatar_preview")
            .semantics { contentDescription = "Oyuncu karakteri" }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val cx = w * 0.5f

            // Ground shadow oval
            drawOval(
                color = Color.Black.copy(alpha = 0.35f),
                topLeft = Offset(cx - w * 0.28f, h * 0.88f),
                size = Size(w * 0.56f, h * 0.08f)
            )

            // Legs
            val legW = w * 0.14f
            val legH = h * 0.26f
            val legTop = h * 0.64f
            drawRect(pantsColor, Offset(cx - legW - 3f, legTop), Size(legW, legH))
            drawRect(Color.Black.copy(0.45f), Offset(cx - legW - 3f, legTop), Size(legW, legH), style = Stroke(2f))
            drawRect(pantsColor, Offset(cx + 3f, legTop), Size(legW, legH))
            drawRect(Color.Black.copy(0.45f), Offset(cx + 3f, legTop), Size(legW, legH), style = Stroke(2f))

            // Left & Right Arms
            val armW = w * 0.11f
            val armH = h * 0.27f
            val torsoTop = h * 0.36f
            val torsoW = w * 0.30f
            val torsoH = h * 0.28f

            drawRect(darkBody, Offset(cx - torsoW * 0.5f - armW - 4f, torsoTop), Size(armW, armH))
            drawRect(Color.Black.copy(0.45f), Offset(cx - torsoW * 0.5f - armW - 4f, torsoTop), Size(armW, armH), style = Stroke(2f))

            drawRect(darkBody, Offset(cx + torsoW * 0.5f + 4f, torsoTop), Size(armW, armH))
            drawRect(Color.Black.copy(0.45f), Offset(cx + torsoW * 0.5f + 4f, torsoTop), Size(armW, armH), style = Stroke(2f))

            // Torso (front + subtle 3D side trim)
            drawRect(bodyColor, Offset(cx - torsoW * 0.5f, torsoTop), Size(torsoW, torsoH))
            drawRect(Color.White.copy(alpha = 0.22f), Offset(cx - torsoW * 0.22f, torsoTop + 6f), Size(torsoW * 0.44f, torsoH * 0.38f))
            drawRect(Color.Black.copy(0.45f), Offset(cx - torsoW * 0.5f, torsoTop), Size(torsoW, torsoH), style = Stroke(2.2f))

            // Diamond Sword held on right side
            val swordPath = Path().apply {
                moveTo(cx + torsoW * 0.5f + armW * 0.6f, torsoTop + armH * 0.85f)
                lineTo(cx + torsoW * 0.96f, torsoTop + armH * 0.1f)
                lineTo(cx + torsoW * 1.05f, torsoTop + armH * 0.18f)
                lineTo(cx + torsoW * 0.6f, torsoTop + armH * 0.92f)
                close()
            }
            drawPath(swordPath, Color(0xFF00E5FF))
            drawPath(swordPath, Color.White.copy(alpha = 0.7f), style = Stroke(2f))

            // Blocky Head
            val headSize = w * 0.30f
            val headLeft = cx - headSize * 0.5f
            val headTop = torsoTop - headSize + 2f
            drawRect(skinTone, Offset(headLeft, headTop), Size(headSize, headSize))
            // Hair cap
            drawRect(Color(0xFF3E2723), Offset(headLeft, headTop), Size(headSize, headSize * 0.26f))
            drawRect(Color.Black.copy(0.5f), Offset(headLeft, headTop), Size(headSize, headSize), style = Stroke(2.2f))

            // Voxel Eyes & Smile
            val eyeW = headSize * 0.16f
            drawRect(Color.White, Offset(headLeft + headSize * 0.18f, headTop + headSize * 0.44f), Size(eyeW, eyeW))
            drawRect(Color(0xFF1565C0), Offset(headLeft + headSize * 0.23f, headTop + headSize * 0.46f), Size(eyeW * 0.6f, eyeW * 0.8f))
            drawRect(Color.White, Offset(headLeft + headSize * 0.66f, headTop + headSize * 0.44f), Size(eyeW, eyeW))
            drawRect(Color(0xFF1565C0), Offset(headLeft + headSize * 0.69f, headTop + headSize * 0.46f), Size(eyeW * 0.6f, eyeW * 0.8f))

            // Rank Crown or Builder Hat
            if (rank in setOf(Rank.KURUCU, Rank.YONETICI, Rank.DVIP_PP, Rank.MVIP_P, Rank.VIP_P)) {
                val crownColor = if (rank == Rank.KURUCU || rank == Rank.YONETICI) Color(0xFFFF5252) else Color(0xFFFFD54F)
                val crown = Path().apply {
                    moveTo(headLeft + 4f, headTop)
                    lineTo(headLeft + 4f, headTop - headSize * 0.26f)
                    lineTo(cx - headSize * 0.18f, headTop - headSize * 0.12f)
                    lineTo(cx, headTop - headSize * 0.32f)
                    lineTo(cx + headSize * 0.18f, headTop - headSize * 0.12f)
                    lineTo(headLeft + headSize - 4f, headTop - headSize * 0.26f)
                    lineTo(headLeft + headSize - 4f, headTop)
                    close()
                }
                drawPath(crown, crownColor)
                drawPath(crown, Color.Black.copy(0.5f), style = Stroke(1.5f))
            } else if (rank == Rank.MIMAR || rank == Rank.AKADEMI) {
                drawRect(
                    color = rank.tagBg,
                    topLeft = Offset(headLeft - 4f, headTop - headSize * 0.16f),
                    size = Size(headSize + 8f, headSize * 0.18f)
                )
            }
        }
    }
}
