package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LevelSystem
import com.example.model.Rank

@Composable
fun RankTagAndName(
    rank: Rank,
    playerName: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rgbRank")
    val rgbShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rgbPhase"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (rank.tagLabel.isNotEmpty()) {
            val tagModifier = when {
                rank.isRgb -> {
                    val palette = listOf(
                        Color(0xFFFF1744),
                        Color(0xFFFFEA00),
                        Color(0xFF00E676),
                        Color(0xFF00E5FF),
                        Color(0xFFD500F9),
                        Color(0xFFFF1744)
                    )
                    val idx = ((rgbShift * 4).toInt()).coerceIn(0, 3)
                    val subColors = listOf(palette[idx], palette[idx + 1], palette[idx + 2])
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Brush.horizontalGradient(subColors))
                        .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                }
                rank.isBicolorWhiteBlack -> {
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF101010), Color(0xFF333333))
                            )
                        )
                        .border(1.2.dp, Color.White, RoundedCornerShape(4.dp))
                }
                rank.isYouTubeRedWhite -> {
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFD32F2F), Color(0xFFB71C1C))
                            )
                        )
                        .border(1.2.dp, Color.White, RoundedCornerShape(4.dp))
                }
                rank.isTikTokPinkYellow -> {
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFF2A7A), Color(0xFFE91E63))
                            )
                        )
                        .border(1.2.dp, Color(0xFFFFEB3B), RoundedCornerShape(4.dp))
                }
                else -> {
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(rank.tagBg)
                        .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(4.dp))
                }
            }

            Row(
                modifier = tagModifier.padding(
                    horizontal = if (compact) 5.dp else 7.dp,
                    vertical = if (compact) 1.dp else 2.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rank.tagLabel,
                    color = rank.tagTextColor,
                    fontSize = if (compact) 10.sp else 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                if (rank.plusText.isNotEmpty()) {
                    Text(
                        text = rank.plusText,
                        color = rank.plusColor,
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }

        Text(
            text = playerName,
            color = rank.nameColor,
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun LevelTierBadge(
    level: Int,
    modifier: Modifier = Modifier
) {
    val tier = LevelSystem.getTierInfo(level)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tier.color.copy(alpha = 0.22f))
            .border(1.dp, tier.color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = tier.fullName,
            color = tier.color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
