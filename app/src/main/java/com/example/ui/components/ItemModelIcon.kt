package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.example.model.BlockType
import com.example.model.GameItem

@Composable
fun ItemModelIcon(
    item: GameItem?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (item == null) return@Canvas
        val w = size.width
        val h = size.height
        val pad = w * 0.08f
        val innerW = w - pad * 2f
        val innerH = h - pad * 2f

        when (item.id) {
            "wood_sword", "stone_sword", "iron_sword", "diamond_sword" -> {
                val bladeColor = item.color
                val bladeHighlight = lerp(bladeColor, Color.White, 0.45f)
                val guardColor = when (item.id) {
                    "diamond_sword" -> Color(0xFF006064)
                    "iron_sword" -> Color(0xFF546E7A)
                    "stone_sword" -> Color(0xFF424242)
                    else -> Color(0xFF5D4037)
                }
                // Diagonal blade from top-right to bottom-left
                val blade = Path().apply {
                    moveTo(w * 0.86f, h * 0.12f)
                    lineTo(w * 0.88f, h * 0.28f)
                    lineTo(w * 0.40f, h * 0.72f)
                    lineTo(w * 0.28f, h * 0.60f)
                    close()
                }
                drawPath(blade, bladeColor)
                drawLine(
                    color = bladeHighlight,
                    start = Offset(w * 0.83f, h * 0.16f),
                    end = Offset(w * 0.35f, h * 0.63f),
                    strokeWidth = (w * 0.07f).coerceAtLeast(1.5f),
                    cap = StrokeCap.Round
                )
                drawPath(blade, Color.Black.copy(alpha = 0.45f), style = Stroke((w * 0.04f).coerceAtLeast(1f)))
                // Crossguard
                drawLine(
                    color = guardColor,
                    start = Offset(w * 0.22f, h * 0.52f),
                    end = Offset(w * 0.48f, h * 0.78f),
                    strokeWidth = (w * 0.12f).coerceAtLeast(2.5f),
                    cap = StrokeCap.Round
                )
                // Handle & Pommel
                drawLine(
                    color = Color(0xFF6D4C41),
                    start = Offset(w * 0.32f, h * 0.68f),
                    end = Offset(w * 0.15f, h * 0.85f),
                    strokeWidth = (w * 0.11f).coerceAtLeast(2f),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = guardColor,
                    radius = (w * 0.07f).coerceAtLeast(2f),
                    center = Offset(w * 0.14f, h * 0.86f)
                )
            }

            "pickaxe" -> {
                // Wooden handle
                drawLine(
                    color = Color(0xFF795548),
                    start = Offset(w * 0.16f, h * 0.84f),
                    end = Offset(w * 0.72f, h * 0.28f),
                    strokeWidth = (w * 0.12f).coerceAtLeast(2.2f),
                    cap = StrokeCap.Round
                )
                // Curved Diamond Pickaxe Head
                val head = Path().apply {
                    moveTo(w * 0.30f, h * 0.16f)
                    lineTo(w * 0.76f, h * 0.16f)
                    lineTo(w * 0.86f, h * 0.68f)
                    lineTo(w * 0.72f, h * 0.64f)
                    lineTo(w * 0.64f, h * 0.32f)
                    lineTo(w * 0.34f, h * 0.28f)
                    close()
                }
                drawPath(head, Color(0xFF00E5FF))
                drawPath(head, Color(0xFF006064), style = Stroke((w * 0.04f).coerceAtLeast(1f)))
            }

            "iron_armor" -> {
                val armorPath = Path().apply {
                    moveTo(w * 0.14f, h * 0.18f)
                    lineTo(w * 0.35f, h * 0.16f)
                    lineTo(w * 0.42f, h * 0.28f)
                    lineTo(w * 0.58f, h * 0.28f)
                    lineTo(w * 0.65f, h * 0.16f)
                    lineTo(w * 0.86f, h * 0.18f)
                    lineTo(w * 0.90f, h * 0.46f)
                    lineTo(w * 0.74f, h * 0.48f)
                    lineTo(w * 0.74f, h * 0.86f)
                    lineTo(w * 0.26f, h * 0.86f)
                    lineTo(w * 0.26f, h * 0.48f)
                    lineTo(w * 0.10f, h * 0.46f)
                    close()
                }
                drawPath(armorPath, Color(0xFFCFD8DC))
                drawRect(
                    color = Color(0xFFECEFF1),
                    topLeft = Offset(w * 0.32f, h * 0.36f),
                    size = Size(w * 0.36f, h * 0.16f)
                )
                drawPath(armorPath, Color(0xFF455A64), style = Stroke((w * 0.05f).coerceAtLeast(1.2f)))
            }

            "apple" -> {
                // Stem & Leaf
                drawLine(
                    color = Color(0xFF5D4037),
                    start = Offset(w * 0.50f, h * 0.26f),
                    end = Offset(w * 0.54f, h * 0.11f),
                    strokeWidth = (w * 0.08f).coerceAtLeast(1.8f),
                    cap = StrokeCap.Round
                )
                drawOval(
                    color = Color(0xFF43A047),
                    topLeft = Offset(w * 0.54f, h * 0.10f),
                    size = Size(w * 0.24f, h * 0.14f)
                )
                // Red Apple body
                drawRoundRect(
                    color = Color(0xFFE53935),
                    topLeft = Offset(w * 0.18f, h * 0.24f),
                    size = Size(w * 0.64f, h * 0.64f),
                    cornerRadius = CornerRadius(w * 0.24f, w * 0.24f)
                )
                drawCircle(
                    color = Color(0xFFFF8A80),
                    radius = w * 0.09f,
                    center = Offset(w * 0.36f, h * 0.42f)
                )
            }

            "bread" -> {
                drawRoundRect(
                    color = Color(0xFF8D5524),
                    topLeft = Offset(w * 0.12f, h * 0.36f),
                    size = Size(w * 0.76f, h * 0.44f),
                    cornerRadius = CornerRadius(w * 0.20f, w * 0.20f)
                )
                drawRoundRect(
                    color = Color(0xFFE0B16E),
                    topLeft = Offset(w * 0.16f, h * 0.28f),
                    size = Size(w * 0.68f, h * 0.38f),
                    cornerRadius = CornerRadius(w * 0.18f, w * 0.18f)
                )
                // Crust slashes
                for (i in 0..2) {
                    val sx = w * (0.30f + i * 0.17f)
                    drawLine(
                        color = Color(0xFF7B4819),
                        start = Offset(sx, h * 0.34f),
                        end = Offset(sx + w * 0.08f, h * 0.48f),
                        strokeWidth = (w * 0.06f).coerceAtLeast(1.4f),
                        cap = StrokeCap.Round
                    )
                }
            }

            "cooked_beef" -> {
                drawRoundRect(
                    color = Color(0xFF5D2E1C),
                    topLeft = Offset(w * 0.12f, h * 0.26f),
                    size = Size(w * 0.76f, h * 0.52f),
                    cornerRadius = CornerRadius(w * 0.20f, w * 0.20f)
                )
                drawRoundRect(
                    color = Color(0xFFA04832),
                    topLeft = Offset(w * 0.20f, h * 0.32f),
                    size = Size(w * 0.60f, h * 0.38f),
                    cornerRadius = CornerRadius(w * 0.15f, w * 0.15f)
                )
                // Marbling stripe
                drawLine(
                    color = Color(0xFFFFD1B3),
                    start = Offset(w * 0.28f, h * 0.48f),
                    end = Offset(w * 0.62f, h * 0.42f),
                    strokeWidth = (w * 0.07f).coerceAtLeast(1.6f),
                    cap = StrokeCap.Round
                )
            }

            else -> {
                // Detailed isometric/textured voxel block model per BlockType
                val bt = item.blockType
                val topC = bt?.topColor ?: item.color
                val frontC = bt?.frontColor ?: lerp(item.color, Color.Black, 0.22f)
                val sideC = bt?.sideColor ?: lerp(item.color, Color.Black, 0.12f)

                // Top bevel strip + main block face
                drawRect(frontC, Offset(pad, pad + innerH * 0.24f), Size(innerW, innerH * 0.76f))
                drawRect(topC, Offset(pad, pad), Size(innerW, innerH * 0.28f))
                drawRect(sideC, Offset(pad + innerW * 0.72f, pad + innerH * 0.28f), Size(innerW * 0.28f, innerH * 0.72f))

                when (bt) {
                    BlockType.TNT -> {
                        drawRect(Color.White, Offset(pad, h * 0.42f), Size(innerW, h * 0.22f))
                        // Mini TNT bars
                        drawRect(Color(0xFF1A1A1A), Offset(w * 0.22f, h * 0.46f), Size(w * 0.14f, h * 0.14f))
                        drawRect(Color(0xFF1A1A1A), Offset(w * 0.43f, h * 0.46f), Size(w * 0.14f, h * 0.14f))
                        drawRect(Color(0xFF1A1A1A), Offset(w * 0.64f, h * 0.46f), Size(w * 0.14f, h * 0.14f))
                    }
                    BlockType.CHEST -> {
                        drawRect(Color(0xFF4E342E), Offset(pad, h * 0.44f), Size(innerW, h * 0.08f))
                        drawRect(Color(0xFFECEFF1), Offset(w * 0.43f, h * 0.40f), Size(w * 0.14f, h * 0.18f))
                    }
                    BlockType.BRICK -> {
                        drawLine(Color(0xFFCFD8DC), Offset(pad, h * 0.50f), Offset(w - pad, h * 0.50f), strokeWidth = 2f)
                        drawLine(Color(0xFFCFD8DC), Offset(pad, h * 0.74f), Offset(w - pad, h * 0.74f), strokeWidth = 2f)
                        drawLine(Color(0xFFCFD8DC), Offset(w * 0.50f, h * 0.50f), Offset(w * 0.50f, h * 0.74f), strokeWidth = 2f)
                    }
                    BlockType.GLASS -> {
                        drawLine(Color.White.copy(alpha = 0.85f), Offset(w * 0.24f, h * 0.34f), Offset(w * 0.44f, h * 0.54f), strokeWidth = 2.5f)
                        drawLine(Color.White.copy(alpha = 0.7f), Offset(w * 0.54f, h * 0.52f), Offset(w * 0.74f, h * 0.72f), strokeWidth = 2f)
                    }
                    BlockType.WOOD -> {
                        drawLine(Color(0xFF5D4037), Offset(pad, h * 0.52f), Offset(w - pad, h * 0.52f), strokeWidth = 1.6f)
                        drawLine(Color(0xFF5D4037), Offset(pad, h * 0.74f), Offset(w - pad, h * 0.74f), strokeWidth = 1.6f)
                    }
                    BlockType.DIAMOND, BlockType.GOLD, BlockType.IRON_BLOCK -> {
                        drawRect(
                            color = Color.White.copy(alpha = 0.45f),
                            topLeft = Offset(w * 0.24f, h * 0.38f),
                            size = Size(w * 0.34f, h * 0.34f)
                        )
                    }
                    BlockType.WOOL_CYAN, BlockType.WOOL_RED -> {
                        drawRect(Color.White.copy(alpha = 0.18f), Offset(w * 0.22f, h * 0.42f), Size(w * 0.22f, h * 0.22f))
                        drawRect(Color.White.copy(alpha = 0.14f), Offset(w * 0.52f, h * 0.58f), Size(w * 0.20f, h * 0.20f))
                    }
                    BlockType.OBSIDIAN -> {
                        drawRect(Color(0xFF7E57C2), Offset(w * 0.26f, h * 0.42f), Size(w * 0.16f, h * 0.16f))
                        drawRect(Color(0xFF5E35B1), Offset(w * 0.56f, h * 0.62f), Size(w * 0.18f, h * 0.16f))
                    }
                    else -> Unit
                }

                drawRect(
                    color = Color.Black.copy(alpha = 0.45f),
                    topLeft = Offset(pad, pad),
                    size = Size(innerW, innerH),
                    style = Stroke((w * 0.045f).coerceAtLeast(1.2f))
                )
            }
        }
    }
}
