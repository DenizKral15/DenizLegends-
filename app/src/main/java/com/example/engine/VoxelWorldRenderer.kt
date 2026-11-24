package com.example.engine

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.model.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class CameraState(
    val x: Float = 0.5f,
    val y: Float = 2.6f,
    val z: Float = -3.5f,
    val yawDeg: Float = 0f,
    val pitchDeg: Float = -10f
)

private data class ProjectedFace(
    val p0: Offset,
    val p1: Offset,
    val p2: Offset,
    val p3: Offset,
    val color: Color,
    val borderColor: Color,
    val isSide: Boolean = false
)

private sealed class RenderableItem(val distSq: Float) {
    class BlockRender(distSq: Float, val coord: VoxelCoord, val type: BlockType, val targeted: Boolean) : RenderableItem(distSq)
    class EntityRender(distSq: Float, val entity: WorldEntity) : RenderableItem(distSq)
}

object VoxelWorldRenderer {

    fun DrawScope.renderWorld(
        camera: CameraState,
        blocks: Map<VoxelCoord, BlockType>,
        entities: List<WorldEntity>,
        targetedCoord: VoxelCoord?,
        swingProgress: Float,
        heldItem: GameItem?,
        serverType: ServerType,
        textMeasurer: TextMeasurer,
        hurtFlashAlpha: Float = 0f,
        hurtShakeTimer: Float = 0f
    ) {
        val w = size.width
        val h = size.height
        val focal = w.coerceAtLeast(h) * 0.72f

        // 1. Sky & Horizon Gradient tailored to ServerType
        val skyTop = when (serverType) {
            ServerType.BEDWARS -> Color(0xFF1A4B8C)
            ServerType.SKYWARS -> Color(0xFF0D2B4E)
            ServerType.SURVIVAL -> Color(0xFF21618C)
            ServerType.BUILD_BATTLE -> Color(0xFF3E206D)
            else -> Color(0xFF125A8A)
        }
        val skyBottom = when (serverType) {
            ServerType.SKYWARS -> Color(0xFFE67E22)
            ServerType.BUILD_BATTLE -> Color(0xFF7D3C98)
            else -> Color(0xFF76D7EA)
        }
        drawRect(
            brush = Brush.verticalGradient(listOf(skyTop, skyBottom, Color(0xFF1B3B5A))),
            size = size
        )

        // Subtle sun
        drawRect(
            color = Color(0xFFFFEE58),
            topLeft = Offset(w * 0.78f, h * 0.08f),
            size = Size(w * 0.06f, w * 0.06f)
        )

        val shakeYaw = if (hurtShakeTimer > 0f) cos(hurtShakeTimer * 52f) * 4.5f * (hurtShakeTimer / 0.4f).coerceAtMost(1f) else 0f
        val shakePitch = if (hurtShakeTimer > 0f) sin(hurtShakeTimer * 44f) * 3.8f * (hurtShakeTimer / 0.4f).coerceAtMost(1f) else 0f
        val yawRad = Math.toRadians((camera.yawDeg + shakeYaw).toDouble()).toFloat()
        val pitchRad = Math.toRadians((camera.pitchDeg + shakePitch).toDouble()).toFloat()
        val cosY = cos(yawRad)
        val sinY = sin(yawRad)
        val cosP = cos(pitchRad)
        val sinP = sin(pitchRad)

        fun project(wx: Float, wy: Float, wz: Float): Offset? {
            val dx = wx - camera.x
            val dy = wy - camera.y
            val dz = wz - camera.z
            val rx = dx * cosY - dz * sinY
            val rz1 = dx * sinY + dz * cosY
            val ry = dy * cosP - rz1 * sinP
            val rz = dy * sinP + rz1 * cosP
            if (rz <= 0.22f) return null
            val sx = (w * 0.5f) + (rx / rz) * focal
            val sy = (h * 0.5f) - (ry / rz) * focal
            return Offset(sx, sy)
        }

        // 2. Collect visible blocks & entities within render distance
        val renderList = ArrayList<RenderableItem>(blocks.size + entities.size)
        val maxDistSq = 28f * 28f

        for ((coord, type) in blocks) {
            if (type == BlockType.AIR) continue
            val cx = coord.x + 0.5f
            val cy = coord.y + 0.5f
            val cz = coord.z + 0.5f
            val dx = cx - camera.x
            val dy = cy - camera.y
            val dz = cz - camera.z
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq > maxDistSq) continue

            val forwardDot = (dx * sinY + dz * cosY) * cosP - dy * sinP
            if (forwardDot < -1.8f) continue

            if (type != BlockType.GLASS && type != BlockType.WATER) {
                val occluded = isOpaque(blocks[VoxelCoord(coord.x + 1, coord.y, coord.z)]) &&
                    isOpaque(blocks[VoxelCoord(coord.x - 1, coord.y, coord.z)]) &&
                    isOpaque(blocks[VoxelCoord(coord.x, coord.y + 1, coord.z)]) &&
                    isOpaque(blocks[VoxelCoord(coord.x, coord.y - 1, coord.z)]) &&
                    isOpaque(blocks[VoxelCoord(coord.x, coord.y, coord.z + 1)]) &&
                    isOpaque(blocks[VoxelCoord(coord.x, coord.y, coord.z - 1)])
                if (occluded) continue
            }

            renderList += RenderableItem.BlockRender(
                distSq = distSq,
                coord = coord,
                type = type,
                targeted = (coord == targetedCoord)
            )
        }

        for (entity in entities) {
            if (!entity.alive) continue
            val dx = entity.x - camera.x
            val dy = (entity.y + 0.9f) - camera.y
            val dz = entity.z - camera.z
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq > maxDistSq || distSq < 0.25f) continue
            renderList += RenderableItem.EntityRender(distSq, entity)
        }

        // Painter's algorithm: farthest first
        renderList.sortByDescending { it.distSq }

        val facesBuffer = ArrayList<ProjectedFace>(3)

        for (item in renderList) {
            when (item) {
                is RenderableItem.BlockRender -> {
                    drawVoxelCube(
                        coord = item.coord,
                        type = item.type,
                        targeted = item.targeted,
                        camera = camera,
                        blocks = blocks,
                        project = ::project,
                        facesBuffer = facesBuffer
                    )
                }
                is RenderableItem.EntityRender -> {
                    drawBlockyEntity(
                        entity = item.entity,
                        dist = sqrt(item.distSq),
                        project = ::project,
                        textMeasurer = textMeasurer
                    )
                }
            }
        }

        // 3. Center Crosshair (+)
        val cx = w * 0.5f
        val cy = h * 0.5f
        drawLine(Color.White.copy(alpha = 0.85f), Offset(cx - 10f, cy), Offset(cx + 10f, cy), strokeWidth = 2.5f)
        drawLine(Color.White.copy(alpha = 0.85f), Offset(cx, cy - 10f), Offset(cx, cy + 10f), strokeWidth = 2.5f)

        // 4. First-person distinct held item / block / weapon / hand model
        drawHeldItemInHand(
            w = w,
            h = h,
            swingProgress = swingProgress,
            heldItem = heldItem
        )

        // 5. Damage Red Screen Vignette
        if (hurtFlashAlpha > 0.01f) {
            val redAlpha = hurtFlashAlpha.coerceIn(0f, 0.72f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFD50000).copy(alpha = redAlpha * 0.45f),
                        Color(0xFFB71C1C).copy(alpha = redAlpha)
                    ),
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = w.coerceAtLeast(h) * 0.72f
                ),
                size = size
            )
        }
    }

    private fun DrawScope.drawHeldItemInHand(
        w: Float,
        h: Float,
        swingProgress: Float,
        heldItem: GameItem?
    ) {
        val swingOffset = sin(swingProgress * Math.PI).toFloat()
        val bx = w * 0.82f - swingOffset * 68f
        val by = h * 0.81f + swingOffset * 42f

        if (heldItem == null) {
            // Empty Voxel Hand
            val arm = Path().apply {
                moveTo(bx - 16f, by - 26f)
                lineTo(bx + 26f, by - 36f)
                lineTo(bx + 52f, by + 64f)
                lineTo(bx + 6f, by + 74f)
                close()
            }
            drawPath(arm, Color(0xFFE0AC69))
            drawPath(arm, Color(0xFF8D5524).copy(alpha = 0.6f), style = Stroke(2.5f))
            return
        }

        if (heldItem.blockType != null) {
            val bt = heldItem.blockType
            // Isometric 3D held block
            val top = Path().apply {
                moveTo(bx - 40f, by - 40f)
                lineTo(bx - 8f, by - 58f)
                lineTo(bx + 32f, by - 40f)
                lineTo(bx, by - 22f)
                close()
            }
            val leftFace = Path().apply {
                moveTo(bx - 40f, by - 40f)
                lineTo(bx, by - 22f)
                lineTo(bx, by + 32f)
                lineTo(bx - 40f, by + 12f)
                close()
            }
            val rightFace = Path().apply {
                moveTo(bx, by - 22f)
                lineTo(bx + 32f, by - 40f)
                lineTo(bx + 32f, by + 14f)
                lineTo(bx, by + 32f)
                close()
            }
            drawPath(top, bt.topColor)
            drawPath(leftFace, bt.frontColor)
            drawPath(rightFace, bt.sideColor)

            // Block-specific held details
            when (bt) {
                BlockType.TNT -> {
                    val band = Path().apply {
                        moveTo(bx - 40f, by - 20f)
                        lineTo(bx, by - 2f)
                        lineTo(bx + 32f, by - 20f)
                        lineTo(bx + 32f, by - 4f)
                        lineTo(bx, by + 14f)
                        lineTo(bx - 40f, by - 4f)
                        close()
                    }
                    drawPath(band, Color(0xFFF5F5F5))
                }
                BlockType.CHEST -> {
                    drawRect(Color(0xFFFFD54F), Offset(bx - 22f, by - 26f), Size(10f, 14f))
                }
                BlockType.GLASS -> {
                    drawLine(Color.White.copy(alpha = 0.7f), Offset(bx - 28f, by - 26f), Offset(bx - 12f, by - 8f), strokeWidth = 3f)
                }
                else -> {}
            }
            drawPath(top, Color.Black.copy(alpha = 0.35f), style = Stroke(2f))
            drawPath(leftFace, Color.Black.copy(alpha = 0.4f), style = Stroke(2f))
            drawPath(rightFace, Color.Black.copy(alpha = 0.45f), style = Stroke(2f))
            return
        }

        when (heldItem.id) {
            "wood_sword", "stone_sword", "iron_sword", "diamond_sword" -> {
                val bladeColor = heldItem.color
                val edgeHighlight = lerp(bladeColor, Color.White, 0.48f)
                val guardColor = when (heldItem.id) {
                    "diamond_sword" -> Color(0xFF006064)
                    "iron_sword" -> Color(0xFF546E7A)
                    "stone_sword" -> Color(0xFF424242)
                    else -> Color(0xFF5D4037)
                }
                // Hilt
                drawLine(Color(0xFF6D4C41), Offset(bx + 18f, by + 54f), Offset(bx - 2f, by + 20f), strokeWidth = 10f, cap = StrokeCap.Round)
                // Crossguard
                drawLine(guardColor, Offset(bx - 24f, by + 28f), Offset(bx + 18f, by + 8f), strokeWidth = 12f, cap = StrokeCap.Round)
                // Blade
                val blade = Path().apply {
                    moveTo(bx - 12f, by + 16f)
                    lineTo(bx - 56f, by - 68f)
                    lineTo(bx - 42f, by - 82f)
                    lineTo(bx + 6f, by + 6f)
                    close()
                }
                drawPath(blade, bladeColor)
                drawLine(edgeHighlight, Offset(bx - 50f, by - 66f), Offset(bx - 6f, by + 10f), strokeWidth = 4f, cap = StrokeCap.Round)
                drawPath(blade, Color.Black.copy(alpha = 0.45f), style = Stroke(2.5f))
            }

            "pickaxe" -> {
                drawLine(Color(0xFF795548), Offset(bx + 16f, by + 58f), Offset(bx - 26f, by - 34f), strokeWidth = 10f, cap = StrokeCap.Round)
                val pickHead = Path().apply {
                    moveTo(bx - 62f, by - 12f)
                    lineTo(bx - 48f, by - 46f)
                    lineTo(bx + 10f, by - 62f)
                    lineTo(bx + 6f, by - 44f)
                    lineTo(bx - 38f, by - 32f)
                    lineTo(bx - 48f, by - 4f)
                    close()
                }
                drawPath(pickHead, Color(0xFF00E5FF))
                drawPath(pickHead, Color(0xFF006064), style = Stroke(2.5f))
            }

            "iron_armor" -> {
                val plate = Path().apply {
                    moveTo(bx - 36f, by - 36f)
                    lineTo(bx - 12f, by - 36f)
                    lineTo(bx - 6f, by - 24f)
                    lineTo(bx + 8f, by - 24f)
                    lineTo(bx + 14f, by - 36f)
                    lineTo(bx + 36f, by - 36f)
                    lineTo(bx + 42f, by - 6f)
                    lineTo(bx + 28f, by - 6f)
                    lineTo(bx + 26f, by + 32f)
                    lineTo(bx - 24f, by + 32f)
                    lineTo(bx - 26f, by - 6f)
                    lineTo(bx - 42f, by - 6f)
                    close()
                }
                drawPath(plate, Color(0xFFCFD8DC))
                drawPath(plate, Color(0xFF455A64), style = Stroke(2.5f))
            }

            "apple" -> {
                drawLine(Color(0xFF5D4037), Offset(bx - 2f, by - 26f), Offset(bx + 4f, by - 42f), strokeWidth = 5f, cap = StrokeCap.Round)
                drawCircle(Color(0xFFE53935), radius = 26f, center = Offset(bx - 2f, by))
                drawCircle(Color(0xFFFF8A80).copy(alpha = 0.7f), radius = 8f, center = Offset(bx - 12f, by - 10f))
                drawCircle(Color(0xFF43A047), radius = 8f, center = Offset(bx + 10f, by - 32f))
            }

            "bread" -> {
                drawRoundRect(
                    color = Color(0xFFD79B4E),
                    topLeft = Offset(bx - 34f, by - 18f),
                    size = Size(68f, 38f),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                drawRoundRect(
                    color = Color(0xFF8D5524),
                    topLeft = Offset(bx - 34f, by - 18f),
                    size = Size(68f, 38f),
                    cornerRadius = CornerRadius(14f, 14f),
                    style = Stroke(2.5f)
                )
            }

            "cooked_beef" -> {
                drawOval(color = Color(0xFF6D3B1A), topLeft = Offset(bx - 34f, by - 22f), size = Size(68f, 44f))
                drawOval(color = Color(0xFFA15828), topLeft = Offset(bx - 24f, by - 14f), size = Size(48f, 26f))
                drawCircle(Color(0xFFF5ECE3), radius = 7f, center = Offset(bx + 16f, by))
            }

            else -> {
                drawRoundRect(
                    color = heldItem.color,
                    topLeft = Offset(bx - 28f, by - 28f),
                    size = Size(56f, 56f),
                    cornerRadius = CornerRadius(10f, 10f)
                )
            }
        }
    }

    private fun isOpaque(type: BlockType?): Boolean =
        type != null && type != BlockType.AIR && type != BlockType.GLASS && type != BlockType.WATER

    private fun DrawScope.drawVoxelCube(
        coord: VoxelCoord,
        type: BlockType,
        targeted: Boolean,
        camera: CameraState,
        blocks: Map<VoxelCoord, BlockType>,
        project: (Float, Float, Float) -> Offset?,
        facesBuffer: MutableList<ProjectedFace>
    ) {
        val x0 = coord.x.toFloat()
        val y0 = coord.y.toFloat()
        val z0 = coord.z.toFloat()
        val x1 = x0 + 1f
        val y1 = if (type == BlockType.BED) y0 + 0.55f else y0 + 1f
        val z1 = z0 + 1f

        val p000 = project(x0, y0, z0)
        val p100 = project(x1, y0, z0)
        val p110 = project(x1, y1, z0)
        val p010 = project(x0, y1, z0)
        val p001 = project(x0, y0, z1)
        val p101 = project(x1, y0, z1)
        val p111 = project(x1, y1, z1)
        val p011 = project(x0, y1, z1)

        facesBuffer.clear()
        val border = if (targeted) Color(0xFFFFFF00) else Color.Black.copy(alpha = 0.28f)

        if (camera.x < x0 && !isOpaque(blocks[VoxelCoord(coord.x - 1, coord.y, coord.z)])) {
            if (p000 != null && p001 != null && p011 != null && p010 != null) {
                facesBuffer += ProjectedFace(p000, p001, p011, p010, type.sideColor, border, isSide = true)
            }
        } else if (camera.x > x1 && !isOpaque(blocks[VoxelCoord(coord.x + 1, coord.y, coord.z)])) {
            if (p100 != null && p101 != null && p111 != null && p110 != null) {
                facesBuffer += ProjectedFace(p100, p101, p111, p110, type.sideColor, border, isSide = true)
            }
        }

        if (camera.z < z0 && !isOpaque(blocks[VoxelCoord(coord.x, coord.y, coord.z - 1)])) {
            if (p000 != null && p100 != null && p110 != null && p010 != null) {
                facesBuffer += ProjectedFace(p000, p100, p110, p010, type.frontColor, border, isSide = true)
            }
        } else if (camera.z > z1 && !isOpaque(blocks[VoxelCoord(coord.x, coord.y, coord.z + 1)])) {
            if (p001 != null && p101 != null && p111 != null && p011 != null) {
                facesBuffer += ProjectedFace(p001, p101, p111, p011, type.frontColor, border, isSide = true)
            }
        }

        if (camera.y > y1 && !isOpaque(blocks[VoxelCoord(coord.x, coord.y + 1, coord.z)])) {
            if (p010 != null && p110 != null && p111 != null && p011 != null) {
                facesBuffer += ProjectedFace(p010, p110, p111, p011, type.topColor, border, isSide = false)
            }
        } else if (camera.y < y0 && !isOpaque(blocks[VoxelCoord(coord.x, coord.y - 1, coord.z)])) {
            if (p000 != null && p100 != null && p101 != null && p001 != null) {
                facesBuffer += ProjectedFace(p000, p100, p101, p001, type.frontColor, border, isSide = false)
            }
        }

        val strokeWidth = if (targeted) 3.5f else 1.2f
        for (face in facesBuffer) {
            val path = Path().apply {
                moveTo(face.p0.x, face.p0.y)
                lineTo(face.p1.x, face.p1.y)
                lineTo(face.p2.x, face.p2.y)
                lineTo(face.p3.x, face.p3.y)
                close()
            }
            drawPath(path, color = face.color)
            if (face.isSide && type == BlockType.TNT) {
                val m01 = Offset((face.p0.x + face.p3.x) * 0.5f, (face.p0.y + face.p3.y) * 0.5f)
                val m12 = Offset((face.p1.x + face.p2.x) * 0.5f, (face.p1.y + face.p2.y) * 0.5f)
                drawLine(Color(0xFFF5F5F5), m01, m12, strokeWidth = 4.5f)
            } else if (face.isSide && type == BlockType.CHEST) {
                val cx = (face.p0.x + face.p1.x + face.p2.x + face.p3.x) * 0.25f
                val cy = (face.p0.y + face.p1.y + face.p2.y + face.p3.y) * 0.25f
                drawRect(Color(0xFFFFD54F), Offset(cx - 3f, cy - 4f), Size(6f, 8f))
            } else if (!face.isSide && type == BlockType.BED) {
                val pillowPath = Path().apply {
                    moveTo(face.p0.x, face.p0.y)
                    lineTo(face.p1.x, face.p1.y)
                    lineTo(lerpFloat(face.p1.x, face.p2.x, 0.32f), lerpFloat(face.p1.y, face.p2.y, 0.32f))
                    lineTo(lerpFloat(face.p0.x, face.p3.x, 0.32f), lerpFloat(face.p0.y, face.p3.y, 0.32f))
                    close()
                }
                drawPath(pillowPath, Color(0xFFFDFEFE))
            }
            drawPath(path, color = face.borderColor, style = Stroke(width = strokeWidth))
        }
    }

    private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun DrawScope.drawBlockyEntity(
        entity: WorldEntity,
        dist: Float,
        project: (Float, Float, Float) -> Offset?,
        textMeasurer: TextMeasurer
    ) {
        val foot = project(entity.x, entity.y, entity.z) ?: return
        val top = project(entity.x, entity.y + 1.85f, entity.z) ?: return
        val heightPx = (foot.y - top.y).coerceIn(14f, 440f)
        val widthPx = heightPx * 0.44f
        val hurtFrac = (entity.hurtFlashTimer / 0.35f).coerceIn(0f, 0.78f)
        val tintBody = if (hurtFrac > 0.02f) lerp(entity.bodyColor, Color(0xFFFF1744), hurtFrac) else entity.bodyColor
        val tintHead = if (hurtFrac > 0.02f) lerp(entity.headColor, Color(0xFFFF1744), hurtFrac) else entity.headColor

        when (entity.kind) {
            EntityKind.GENERATOR -> {
                // Floating crystal over stone pedestal
                drawRect(
                    color = Color(0xFF455A64),
                    topLeft = Offset(foot.x - widthPx * 0.5f, foot.y - heightPx * 0.16f),
                    size = Size(widthPx, heightPx * 0.16f)
                )
                val crystalSize = widthPx * 0.92f
                val cy = (foot.y + top.y) * 0.5f
                val outer = Path().apply {
                    moveTo(foot.x, cy - crystalSize)
                    lineTo(foot.x + crystalSize * 0.72f, cy)
                    lineTo(foot.x, cy + crystalSize)
                    lineTo(foot.x - crystalSize * 0.72f, cy)
                    close()
                }
                drawPath(outer, Color(0xFF00E5FF))
                drawPath(outer, Color.White, style = Stroke(2.2f))
                drawCircle(Color(0xFFB2EBF2), radius = crystalSize * 0.25f, center = Offset(foot.x, cy))
            }

            EntityKind.NPC_PORTAL -> {
                // Swirling Dimensional Obsidian Portal Archway + Portal Keeper figure
                val archW = widthPx * 1.45f
                val archH = heightPx * 1.05f
                val ax = foot.x - archW * 0.5f
                val ay = foot.y - archH
                drawRect(Color(0xFF1A0033), Offset(ax, ay), Size(archW, archH))
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color(0xFFAB47BC), Color(0xFF00E5FF), Color(0xFF6A1B9A))),
                    topLeft = Offset(ax + archW * 0.14f, ay + archH * 0.12f),
                    size = Size(archW * 0.72f, archH * 0.88f)
                )
                drawRect(Color(0xFFCE93D8), Offset(ax, ay), Size(archW, archH), style = Stroke(2.5f))
                drawHumanoid(foot.x, foot.y, heightPx * 0.82f, widthPx * 0.82f, tintBody, tintHead, Color(0xFF00E5FF))
            }

            EntityKind.NPC_MARKET -> {
                // Merchant Villager with Emerald Apron + Tall Hat
                drawHumanoid(foot.x, foot.y, heightPx, widthPx, Color(0xFF6D4C41), tintHead, Color(0xFF1B1B1B))
                val aprW = widthPx * 0.68f
                val aprH = heightPx * 0.34f
                drawRect(Color(0xFF2ECC71), Offset(foot.x - aprW * 0.5f, foot.y - heightPx * 0.68f), Size(aprW, aprH))
                // Tall merchant hat
                drawRect(Color(0xFF8D6E63), Offset(foot.x - widthPx * 0.58f, foot.y - heightPx - heightPx * 0.08f), Size(widthPx * 1.16f, heightPx * 0.10f))
                drawRect(Color(0xFF5D4037), Offset(foot.x - widthPx * 0.38f, foot.y - heightPx - heightPx * 0.22f), Size(widthPx * 0.76f, heightPx * 0.14f))
            }

            EntityKind.NPC_BANK -> {
                // Banker Villager with Golden Trim + Vault Emblem
                drawHumanoid(foot.x, foot.y, heightPx, widthPx, Color(0xFF1A237E), tintHead, Color(0xFF1A1A1A))
                drawRect(Color(0xFFFFD54F), Offset(foot.x - widthPx * 0.16f, foot.y - heightPx * 0.68f), Size(widthPx * 0.32f, heightPx * 0.28f))
                drawRect(Color(0xFFFFB300), Offset(foot.x - widthPx * 0.42f, foot.y - heightPx - heightPx * 0.14f), Size(widthPx * 0.84f, heightPx * 0.14f))
            }

            EntityKind.MOB -> {
                when (entity.mobType) {
                    MobType.DOMUZ -> drawPigModel(foot.x, foot.y, heightPx, widthPx, tintBody, tintHead)
                    MobType.KOYUN -> drawSheepModel(foot.x, foot.y, heightPx, widthPx, tintBody, tintHead)
                    MobType.CREEPER -> drawCreeperModel(foot.x, foot.y, heightPx, widthPx, tintBody, tintHead)
                    MobType.ISKELET -> drawSkeletonModel(foot.x, foot.y, heightPx, widthPx, tintBody, tintHead)
                    MobType.DEMIR_GOLEM -> drawIronGolemModel(foot.x, foot.y, heightPx, widthPx, tintBody, tintHead)
                    MobType.ZOMBI -> drawZombieModel(foot.x, foot.y, heightPx, widthPx, tintBody, tintHead)
                    null -> drawHumanoid(foot.x, foot.y, heightPx, widthPx, tintBody, tintHead, Color(0xFF1A1A1A))
                }
            }

            EntityKind.PLAYER, EntityKind.BOT -> {
                drawHumanoid(foot.x, foot.y, heightPx, widthPx, tintBody, tintHead, Color(0xFF1A1A1A))
                // Team shoulder armor pads in Bedwars / Skywars
                if (entity.team == "MAVI" || entity.team == "KIRMIZI") {
                    val teamColor = if (entity.team == "KIRMIZI") Color(0xFFE53935) else Color(0xFF1E88E5)
                    val padY = foot.y - heightPx * 0.72f
                    drawRect(teamColor, Offset(foot.x - widthPx * 0.78f, padY), Size(widthPx * 0.32f, heightPx * 0.14f))
                    drawRect(teamColor, Offset(foot.x + widthPx * 0.46f, padY), Size(widthPx * 0.32f, heightPx * 0.14f))
                }
                // Held sword on right hand for combatant players & bots
                val handX = foot.x + widthPx * 0.65f
                val handY = foot.y - heightPx * 0.42f
                drawLine(Color(0xFFB0BEC5), Offset(handX, handY), Offset(handX + widthPx * 0.36f, handY - heightPx * 0.22f), strokeWidth = (widthPx * 0.12f).coerceAtLeast(2.2f), cap = StrokeCap.Round)
                // Rank Crown if VIP / Staff
                if (entity.rank != Rank.OYUNCU) {
                    val crownColor = entity.rank.nameColor
                    val cy = foot.y - heightPx - heightPx * 0.04f
                    val crown = Path().apply {
                        moveTo(foot.x - widthPx * 0.36f, cy)
                        lineTo(foot.x - widthPx * 0.36f, cy - heightPx * 0.10f)
                        lineTo(foot.x - widthPx * 0.12f, cy - heightPx * 0.04f)
                        lineTo(foot.x, cy - heightPx * 0.13f)
                        lineTo(foot.x + widthPx * 0.12f, cy - heightPx * 0.04f)
                        lineTo(foot.x + widthPx * 0.36f, cy - heightPx * 0.10f)
                        lineTo(foot.x + widthPx * 0.36f, cy)
                        close()
                    }
                    drawPath(crown, crownColor)
                }
            }
        }

        // Nameplate & HP bar
        if (dist < 18f) {
            val label = when {
                entity.kind == EntityKind.NPC_PORTAL -> "▶ ${entity.name}"
                entity.kind == EntityKind.NPC_MARKET -> "MARKET"
                entity.kind == EntityKind.NPC_BANK -> "BANKA"
                entity.kind == EntityKind.GENERATOR -> "ÜRETİCİ"
                entity.rank != Rank.OYUNCU -> "[${entity.rank.tagLabel}] ${entity.name}"
                else -> entity.name
            }
            val measured = textMeasurer.measure(
                text = label,
                style = TextStyle(
                    color = entity.rank.nameColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            val labelW = measured.size.width.toFloat() + 12f
            val labelH = measured.size.height.toFloat() + 6f
            val lx = foot.x - labelW * 0.5f
            val ly = top.y - labelH - 12f
            drawRect(Color(0xAA0B192C), Offset(lx, ly), Size(labelW, labelH))
            drawText(textLayoutResult = measured, topLeft = Offset(lx + 6f, ly + 3f))

            if (entity.kind == EntityKind.BOT || entity.kind == EntityKind.PLAYER || entity.kind == EntityKind.MOB) {
                val hpFrac = (entity.hp.toFloat() / entity.maxHp.coerceAtLeast(1)).coerceIn(0f, 1f)
                drawRect(Color(0xAA000000), Offset(foot.x - 24f, top.y - 8f), Size(48f, 5f))
                drawRect(
                    color = if (hpFrac > 0.4f) Color(0xFF2ECC71) else Color(0xFFE74C3C),
                    topLeft = Offset(foot.x - 24f, top.y - 8f),
                    size = Size(48f * hpFrac, 5f)
                )
            }
        }
    }

    private fun DrawScope.drawHumanoid(
        cx: Float,
        footY: Float,
        heightPx: Float,
        widthPx: Float,
        bodyColor: Color,
        headColor: Color,
        eyeColor: Color
    ) {
        val legH = heightPx * 0.35f
        val torsoH = heightPx * 0.36f
        val headH = heightPx * 0.28f

        drawRect(bodyColor.copy(alpha = 0.85f), Offset(cx - widthPx * 0.45f, footY - legH), Size(widthPx * 0.4f, legH))
        drawRect(bodyColor.copy(alpha = 0.72f), Offset(cx + widthPx * 0.05f, footY - legH), Size(widthPx * 0.4f, legH))
        val armW = widthPx * 0.28f
        drawRect(headColor.copy(alpha = 0.9f), Offset(cx - widthPx * 0.5f - armW, footY - legH - torsoH), Size(armW, torsoH * 0.92f))
        drawRect(headColor.copy(alpha = 0.9f), Offset(cx + widthPx * 0.5f, footY - legH - torsoH), Size(armW, torsoH * 0.92f))
        drawRect(bodyColor, Offset(cx - widthPx * 0.5f, footY - legH - torsoH), Size(widthPx, torsoH))
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(cx - widthPx * 0.5f, footY - legH - torsoH), Size(widthPx, torsoH), style = Stroke(1.5f))

        val headSize = widthPx * 0.92f
        val headLeft = cx - headSize * 0.5f
        val headTop = footY - legH - torsoH - headH
        drawRect(headColor, Offset(headLeft, headTop), Size(headSize, headH))
        drawRect(Color.Black.copy(alpha = 0.45f), Offset(headLeft, headTop), Size(headSize, headH), style = Stroke(1.5f))

        val eyeSize = (headSize * 0.16f).coerceAtLeast(2f)
        drawRect(eyeColor, Offset(headLeft + headSize * 0.22f, headTop + headH * 0.38f), Size(eyeSize, eyeSize))
        drawRect(eyeColor, Offset(headLeft + headSize * 0.62f, headTop + headH * 0.38f), Size(eyeSize, eyeSize))
    }

    private fun DrawScope.drawZombieModel(cx: Float, footY: Float, h: Float, w: Float, body: Color, head: Color) {
        val legH = h * 0.35f
        val torsoH = h * 0.36f
        val headH = h * 0.28f
        drawRect(Color(0xFF1E3799), Offset(cx - w * 0.45f, footY - legH), Size(w * 0.4f, legH))
        drawRect(Color(0xFF1E3799), Offset(cx + w * 0.05f, footY - legH), Size(w * 0.4f, legH))
        drawRect(body, Offset(cx - w * 0.5f, footY - legH - torsoH), Size(w, torsoH))
        // Outstretched arms
        drawRect(head, Offset(cx - w * 0.85f, footY - legH - torsoH * 0.9f), Size(w * 0.35f, torsoH * 0.4f))
        drawRect(head, Offset(cx + w * 0.5f, footY - legH - torsoH * 0.9f), Size(w * 0.35f, torsoH * 0.4f))
        val headSize = w * 0.92f
        val ht = footY - legH - torsoH - headH
        drawRect(head, Offset(cx - headSize * 0.5f, ht), Size(headSize, headH))
        val es = (headSize * 0.18f).coerceAtLeast(2f)
        drawRect(Color(0xFF1A1A1A), Offset(cx - headSize * 0.3f, ht + headH * 0.35f), Size(es, es))
        drawRect(Color(0xFF1A1A1A), Offset(cx + headSize * 0.12f, ht + headH * 0.35f), Size(es, es))
    }

    private fun DrawScope.drawSkeletonModel(cx: Float, footY: Float, h: Float, w: Float, body: Color, head: Color) {
        val thinW = w * 0.22f
        val legH = h * 0.38f
        val torsoH = h * 0.34f
        val headH = h * 0.27f
        drawRect(body, Offset(cx - w * 0.32f, footY - legH), Size(thinW, legH))
        drawRect(body, Offset(cx + w * 0.10f, footY - legH), Size(thinW, legH))
        // Spine & Ribs
        drawRect(body, Offset(cx - thinW * 0.5f, footY - legH - torsoH), Size(thinW, torsoH))
        for (i in 0..2) {
            drawRect(body, Offset(cx - w * 0.42f, footY - legH - torsoH + i * torsoH * 0.28f), Size(w * 0.84f, thinW * 0.7f))
        }
        // Bow in right hand
        drawArc(
            color = Color(0xFF8D6E63),
            startAngle = -75f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(cx + w * 0.42f, footY - legH - torsoH),
            size = Size(w * 0.5f, torsoH * 0.9f),
            style = Stroke(3f)
        )
        val hs = w * 0.85f
        val ht = footY - legH - torsoH - headH
        drawRect(head, Offset(cx - hs * 0.5f, ht), Size(hs, headH))
        val es = (hs * 0.22f).coerceAtLeast(2f)
        drawRect(Color.Black, Offset(cx - hs * 0.32f, ht + headH * 0.35f), Size(es, es))
        drawRect(Color.Black, Offset(cx + hs * 0.10f, ht + headH * 0.35f), Size(es, es))
    }

    private fun DrawScope.drawCreeperModel(cx: Float, footY: Float, h: Float, w: Float, body: Color, head: Color) {
        val legH = h * 0.26f
        val torsoH = h * 0.46f
        val headH = h * 0.28f
        // 4 short base feet
        drawRect(body.copy(alpha = 0.85f), Offset(cx - w * 0.52f, footY - legH), Size(w * 0.42f, legH))
        drawRect(body.copy(alpha = 0.75f), Offset(cx + w * 0.10f, footY - legH), Size(w * 0.42f, legH))
        // Armless tall torso
        drawRect(body, Offset(cx - w * 0.44f, footY - legH - torsoH), Size(w * 0.88f, torsoH))
        // Head + iconic creeper frown
        val hs = w * 0.95f
        val hl = cx - hs * 0.5f
        val ht = footY - legH - torsoH - headH
        drawRect(head, Offset(hl, ht), Size(hs, headH))
        val es = (hs * 0.2f).coerceAtLeast(2f)
        drawRect(Color.Black, Offset(hl + hs * 0.18f, ht + headH * 0.24f), Size(es, es))
        drawRect(Color.Black, Offset(hl + hs * 0.62f, ht + headH * 0.24f), Size(es, es))
        drawRect(Color.Black, Offset(hl + hs * 0.36f, ht + headH * 0.52f), Size(hs * 0.28f, headH * 0.24f))
        drawRect(Color.Black, Offset(hl + hs * 0.26f, ht + headH * 0.64f), Size(hs * 0.14f, headH * 0.24f))
        drawRect(Color.Black, Offset(hl + hs * 0.60f, ht + headH * 0.64f), Size(hs * 0.14f, headH * 0.24f))
    }

    private fun DrawScope.drawPigModel(cx: Float, footY: Float, h: Float, w: Float, body: Color, head: Color) {
        val pigH = h * 0.58f
        val pigW = w * 1.35f
        val legH = pigH * 0.35f
        val bodyH = pigH * 0.52f
        drawRect(body.copy(alpha = 0.85f), Offset(cx - pigW * 0.42f, footY - legH), Size(pigW * 0.22f, legH))
        drawRect(body.copy(alpha = 0.85f), Offset(cx + pigW * 0.20f, footY - legH), Size(pigW * 0.22f, legH))
        drawRect(body, Offset(cx - pigW * 0.5f, footY - legH - bodyH), Size(pigW, bodyH))
        // Head + Snout
        val hs = pigW * 0.62f
        val ht = footY - legH - bodyH - hs * 0.25f
        drawRect(head, Offset(cx - hs * 0.5f, ht), Size(hs, hs * 0.82f))
        drawRect(Color(0xFFF48FB1), Offset(cx - hs * 0.25f, ht + hs * 0.42f), Size(hs * 0.5f, hs * 0.28f))
        drawRect(Color.Black, Offset(cx - hs * 0.34f, ht + hs * 0.24f), Size(hs * 0.14f, hs * 0.14f))
        drawRect(Color.Black, Offset(cx + hs * 0.20f, ht + hs * 0.24f), Size(hs * 0.14f, hs * 0.14f))
    }

    private fun DrawScope.drawSheepModel(cx: Float, footY: Float, h: Float, w: Float, body: Color, head: Color) {
        val sH = h * 0.68f
        val sW = w * 1.25f
        val legH = sH * 0.34f
        val woolH = sH * 0.56f
        // Thin brown legs
        drawRect(Color(0xFF8D6E63), Offset(cx - sW * 0.36f, footY - legH), Size(sW * 0.16f, legH))
        drawRect(Color(0xFF8D6E63), Offset(cx + sW * 0.20f, footY - legH), Size(sW * 0.16f, legH))
        // Fluffy wool fleece
        drawRoundRect(body, Offset(cx - sW * 0.5f, footY - legH - woolH), Size(sW, woolH), CornerRadius(8f, 8f))
        // Head
        val hs = sW * 0.52f
        val ht = footY - legH - woolH * 0.78f
        drawRect(head, Offset(cx - hs * 0.5f, ht), Size(hs, hs * 0.85f))
        drawRect(Color.Black, Offset(cx - hs * 0.32f, ht + hs * 0.28f), Size(hs * 0.14f, hs * 0.14f))
        drawRect(Color.Black, Offset(cx + hs * 0.18f, ht + hs * 0.28f), Size(hs * 0.14f, hs * 0.14f))
    }

    private fun DrawScope.drawIronGolemModel(cx: Float, footY: Float, h: Float, w: Float, body: Color, head: Color) {
        val gH = h * 1.18f
        val gW = w * 1.48f
        val legH = gH * 0.34f
        val torsoH = gH * 0.42f
        val headH = gH * 0.22f
        drawRect(body.copy(alpha = 0.85f), Offset(cx - gW * 0.36f, footY - legH), Size(gW * 0.28f, legH))
        drawRect(body.copy(alpha = 0.85f), Offset(cx + gW * 0.08f, footY - legH), Size(gW * 0.28f, legH))
        // Massive chest + green vine accent
        drawRect(body, Offset(cx - gW * 0.5f, footY - legH - torsoH), Size(gW, torsoH))
        drawLine(Color(0xFF4CAF50), Offset(cx - gW * 0.42f, footY - legH - torsoH * 0.2f), Offset(cx + gW * 0.38f, footY - legH - torsoH * 0.85f), strokeWidth = 3.5f)
        // Huge hanging arms
        drawRect(body, Offset(cx - gW * 0.72f, footY - legH - torsoH), Size(gW * 0.22f, torsoH * 1.15f))
        drawRect(body, Offset(cx + gW * 0.50f, footY - legH - torsoH), Size(gW * 0.22f, torsoH * 1.15f))
        // Iron head + amber eyes + nose
        val hs = gW * 0.46f
        val ht = footY - legH - torsoH - headH
        drawRect(head, Offset(cx - hs * 0.5f, ht), Size(hs, headH))
        drawRect(Color(0xFFE53935), Offset(cx - hs * 0.30f, ht + headH * 0.36f), Size(hs * 0.18f, hs * 0.16f))
        drawRect(Color(0xFFE53935), Offset(cx + hs * 0.12f, ht + headH * 0.36f), Size(hs * 0.18f, hs * 0.16f))
        drawRect(Color(0xFF90A4AE), Offset(cx - hs * 0.12f, ht + headH * 0.52f), Size(hs * 0.24f, headH * 0.38f))
    }
}

