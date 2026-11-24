package com.example.engine

import com.example.model.*
import kotlin.math.*

data class RaycastHit(
    val hitCoord: VoxelCoord,
    val placeCoord: VoxelCoord,
    val blockType: BlockType
)

object GamePhysics {

    fun raycastBlock(
        camera: CameraState,
        blocks: Map<VoxelCoord, BlockType>,
        maxDistance: Float = 5.5f
    ): RaycastHit? {
        val yawRad = Math.toRadians(camera.yawDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(camera.pitchDeg.toDouble()).toFloat()
        val dirX = sin(yawRad) * cos(pitchRad)
        val dirY = sin(pitchRad)
        val dirZ = cos(yawRad) * cos(pitchRad)

        var prevCoord: VoxelCoord? = null
        val step = 0.08f
        var t = 0.3f
        while (t <= maxDistance) {
            val px = camera.x + dirX * t
            val py = camera.y + dirY * t
            val pz = camera.z + dirZ * t
            val c = VoxelCoord(floor(px).toInt(), floor(py).toInt(), floor(pz).toInt())
            val b = blocks[c]
            if (b != null && b != BlockType.AIR && b != BlockType.WATER) {
                return RaycastHit(
                    hitCoord = c,
                    placeCoord = prevCoord ?: c,
                    blockType = b
                )
            }
            prevCoord = c
            t += step
        }
        return null
    }

    fun findTargetEntity(
        camera: CameraState,
        entities: List<WorldEntity>,
        maxDistance: Float = 4.6f
    ): WorldEntity? {
        val yawRad = Math.toRadians(camera.yawDeg.toDouble()).toFloat()
        val pitchRad = Math.toRadians(camera.pitchDeg.toDouble()).toFloat()
        val dirX = sin(yawRad) * cos(pitchRad)
        val dirY = sin(pitchRad)
        val dirZ = cos(yawRad) * cos(pitchRad)
        var best: WorldEntity? = null
        var bestDist = maxDistance
        for (e in entities) {
            if (!e.alive) continue
            val dx = e.x - camera.x
            val dy = (e.y + 0.95f) - camera.y
            val dz = e.z - camera.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.01f)
            if (dist > bestDist) continue
            val dot3D = (dx * dirX + dy * dirY + dz * dirZ) / dist
            if (dot3D > 0.72f) {
                best = e
                bestDist = dist
            }
        }
        return best
    }

    fun hasSolidBlockAt(x: Float, y: Float, z: Float, blocks: Map<VoxelCoord, BlockType>): Boolean {
        val c = VoxelCoord(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())
        val b = blocks[c]
        return b != null && b.solid
    }

    fun isColliding(
        x: Float,
        feetY: Float,
        z: Float,
        blocks: Map<VoxelCoord, BlockType>
    ): Boolean {
        val r = 0.28f
        val sampleOffsets = listOf(-r to -r, r to -r, -r to r, r to r)
        val heightSlices = listOf(0.08f, 0.90f, 1.72f)
        for ((ox, oz) in sampleOffsets) {
            for (hy in heightSlices) {
                if (hasSolidBlockAt(x + ox, feetY + hy, z + oz, blocks)) return true
            }
        }
        return false
    }

    fun hasFloorUnder(
        x: Float,
        feetY: Float,
        z: Float,
        blocks: Map<VoxelCoord, BlockType>
    ): Boolean {
        val r = 0.24f
        val offsets = listOf(-r to -r, r to -r, -r to r, r to r)
        for ((ox, oz) in offsets) {
            if (hasSolidBlockAt(x + ox, feetY - 0.02f, z + oz, blocks)) {
                return true
            }
        }
        return false
    }

    fun hasCeilingAbove(
        x: Float,
        headY: Float,
        z: Float,
        blocks: Map<VoxelCoord, BlockType>
    ): Boolean {
        val r = 0.24f
        val offsets = listOf(-r to -r, r to -r, -r to r, r to r)
        for ((ox, oz) in offsets) {
            if (hasSolidBlockAt(x + ox, headY, z + oz, blocks)) {
                return true
            }
        }
        return false
    }
}
