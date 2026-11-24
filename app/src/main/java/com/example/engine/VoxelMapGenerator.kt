package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.model.*

data class GeneratedWorld(
    val blocks: MutableMap<VoxelCoord, BlockType>,
    val spawnPos: Triple<Float, Float, Float>,
    val entities: MutableList<WorldEntity>,
    val beds: MutableList<BedState>,
    val lootedChests: MutableSet<VoxelCoord> = mutableSetOf(),
    val minYVoid: Float = -4f
)

object VoxelMapGenerator {

    fun generateWaitingLobby(serverType: ServerType, mapName: String): GeneratedWorld {
        val blocks = HashMap<VoxelCoord, BlockType>()
        val isCoral = mapName.contains("Mercan", ignoreCase = true) || mapName.contains("Bulut", ignoreCase = true)
        val floorPrimary = if (isCoral) BlockType.WOOL_CYAN else BlockType.STONE
        val floorTrim = if (isCoral) BlockType.GOLD else BlockType.CLAY

        for (x in -5..5) {
            for (z in -5..5) {
                val isBorder = x == -5 || x == 5 || z == -5 || z == 5
                blocks[VoxelCoord(x, 0, z)] = if (isBorder) floorTrim else floorPrimary
                if (isBorder && (x + z) % 2 == 0) {
                    blocks[VoxelCoord(x, 1, z)] = BlockType.GLASS
                }
            }
        }
        // Corner pillars
        listOf(-5 to -5, 5 to -5, -5 to 5, 5 to 5).forEach { (px, pz) ->
            for (y in 1..3) {
                blocks[VoxelCoord(px, y, pz)] = serverType.let {
                    when (it) {
                        ServerType.BEDWARS -> BlockType.WOOL_RED
                        ServerType.SKYWARS -> BlockType.DIAMOND
                        ServerType.SKYBLOCK -> BlockType.GRASS
                        ServerType.SURVIVAL -> BlockType.WOOD
                        ServerType.BUILD_BATTLE -> BlockType.GOLD
                        else -> BlockType.WOOL_CYAN
                    }
                }
            }
        }
        val entities = mutableListOf(
            WorldEntity(
                id = "bot_wait_1",
                name = "DenizBot_1",
                kind = EntityKind.BOT,
                x = 2.5f, y = 1.0f, z = 2.5f,
                rank = Rank.VIP, level = 8,
                bodyColor = Color(0xFF00ACC1), headColor = Color(0xFFFFCC80)
            ),
            WorldEntity(
                id = "bot_wait_2",
                name = "KralBot_TR",
                kind = EntityKind.BOT,
                x = -2.5f, y = 1.0f, z = 2.0f,
                rank = Rank.MVIP_P, level = 15,
                bodyColor = Color(0xFF2ECC71), headColor = Color(0xFFFFD54F)
            )
        )
        return GeneratedWorld(
            blocks = blocks,
            spawnPos = Triple(0.5f, 1.5f, -2.5f),
            entities = entities,
            beds = mutableListOf()
        )
    }

    fun generateMatchWorld(
        serverType: ServerType,
        mapName: String,
        savedPlotData: String = "",
        includeBots: Boolean = true
    ): GeneratedWorld {
        val world = when (serverType) {
            ServerType.LOBI -> generateMainLobby(mapName)
            ServerType.BEDWARS -> generateBedwars(mapName, includeBots)
            ServerType.SKYWARS -> generateSkywars(mapName, includeBots)
            ServerType.SKYBLOCK -> generateSkyblock(mapName)
            ServerType.SURVIVAL -> generateSurvival(mapName, includeBots)
            ServerType.BUILD_BATTLE -> generateBuildBattle(mapName, includeBots)
            ServerType.CREATIVE -> generateCreative(mapName)
        }
        if (savedPlotData.isNotBlank() && (serverType == ServerType.CREATIVE || serverType == ServerType.SKYBLOCK)) {
            applySerializedPlot(world.blocks, savedPlotData)
        }
        return world
    }

    private fun generateMainLobby(mapName: String): GeneratedWorld {
        val blocks = HashMap<VoxelCoord, BlockType>()
        val isCoral = mapName.contains("Mercan", ignoreCase = true)
        // Surrounding ocean water
        for (x in -10..10) {
            for (z in -10..10) {
                val dist = kotlin.math.max(kotlin.math.abs(x), kotlin.math.abs(z))
                if (dist <= 7) {
                    val checker = (x + z) % 2 == 0
                    blocks[VoxelCoord(x, 0, z)] = when {
                        dist == 7 -> BlockType.WOOD
                        isCoral -> if (checker) BlockType.CLAY else BlockType.SAND
                        else -> if (checker) BlockType.STONE else BlockType.IRON_BLOCK
                    }
                } else {
                    blocks[VoxelCoord(x, -1, z)] = BlockType.WATER
                }
            }
        }
        // Central fountain/crown monument
        for (x in -1..1) {
            for (z in -1..1) {
                blocks[VoxelCoord(x, 1, z)] = BlockType.WOOL_CYAN
            }
        }
        blocks[VoxelCoord(0, 2, 0)] = BlockType.DIAMOND
        blocks[VoxelCoord(0, 3, 0)] = BlockType.GOLD

        // Portal pedestals for the 6 minigames along the north arc
        val portalServers = listOf(
            ServerType.BEDWARS to (-5 to 5),
            ServerType.SKYWARS to (-3 to 6),
            ServerType.SKYBLOCK to (-1 to 6),
            ServerType.SURVIVAL to (1 to 6),
            ServerType.BUILD_BATTLE to (3 to 6),
            ServerType.CREATIVE to (5 to 5)
        )
        val entities = mutableListOf<WorldEntity>()
        portalServers.forEach { (srv, pos) ->
            val (px, pz) = pos
            blocks[VoxelCoord(px, 1, pz)] = when (srv) {
                ServerType.BEDWARS -> BlockType.WOOL_RED
                ServerType.SKYWARS -> BlockType.DIAMOND
                ServerType.SKYBLOCK -> BlockType.GRASS
                ServerType.SURVIVAL -> BlockType.WOOD
                ServerType.BUILD_BATTLE -> BlockType.GOLD
                else -> BlockType.WOOL_CYAN
            }
            entities += WorldEntity(
                id = "portal_${srv.code}",
                name = srv.displayName.uppercase(),
                kind = EntityKind.NPC_PORTAL,
                x = px + 0.5f,
                y = 2.0f,
                z = pz + 0.5f,
                yaw = 180f,
                bodyColor = srv.accentColor,
                headColor = Color(0xFFFFD54F),
                targetServer = srv
            )
        }
        return GeneratedWorld(
            blocks = blocks,
            spawnPos = Triple(0.5f, 1.5f, -4.5f),
            entities = entities,
            beds = mutableListOf(),
            minYVoid = -5f
        )
    }

    private fun generateBedwars(mapName: String, includeBots: Boolean): GeneratedWorld {
        val blocks = HashMap<VoxelCoord, BlockType>()
        val isCoral = mapName.contains("Mercan", ignoreCase = true)
        val ground = if (isCoral) BlockType.SAND else BlockType.GRASS
        val trim = if (isCoral) BlockType.CLAY else BlockType.WOOD

        fun buildIsland(cx: Int, cz: Int, wool: BlockType) {
            for (dx in -2..2) {
                for (dz in -2..2) {
                    blocks[VoxelCoord(cx + dx, 3, cz + dz)] = BlockType.STONE
                    blocks[VoxelCoord(cx + dx, 4, cz + dz)] = if (dx == -2 || dx == 2) trim else ground
                }
            }
            blocks[VoxelCoord(cx, 5, cz + 2)] = wool
        }

        // 4 team islands
        buildIsland(0, 0, BlockType.WOOL_CYAN)       // Blue (Player)
        buildIsland(14, 14, BlockType.WOOL_RED)     // Red
        buildIsland(14, 0, BlockType.GRASS)         // Green
        buildIsland(0, 14, BlockType.GOLD)          // Yellow

        // Mid Emerald Island
        for (dx in -2..2) {
            for (dz in -2..2) {
                blocks[VoxelCoord(7 + dx, 4, 7 + dz)] = if (kotlin.math.abs(dx) <= 1 && kotlin.math.abs(dz) <= 1) BlockType.DIAMOND else BlockType.STONE
            }
        }
        blocks[VoxelCoord(7, 5, 7)] = BlockType.GOLD

        // Starter bridge stubs
        for (i in 3..4) {
            blocks[VoxelCoord(0, 4, i)] = BlockType.WOOL_CYAN
            blocks[VoxelCoord(14, 4, 14 - i)] = BlockType.WOOL_RED
        }

        // Beds
        val beds = mutableListOf(
            BedState("MAVI", "Mavi", Color(0xFF00BCD4), VoxelCoord(0, 5, -1), true),
            BedState("KIRMIZI", "Kırmızı", Color(0xFFE74C3C), VoxelCoord(14, 5, 15), true),
            BedState("YESIL", "Yeşil", Color(0xFF2ECC71), VoxelCoord(14, 5, -1), true),
            BedState("SARI", "Sarı", Color(0xFFFFCA28), VoxelCoord(0, 5, 15), true)
        )
        beds.forEach { bed ->
            blocks[bed.coord] = BlockType.BED
        }

        val entities = mutableListOf<WorldEntity>()
        // Player Base Market & Generator
        entities += WorldEntity(
            id = "npc_market_blue",
            name = "MARKET",
            kind = EntityKind.NPC_MARKET,
            x = -1.5f, y = 5.0f, z = -1.5f,
            bodyColor = Color(0xFF8D6E63), headColor = Color(0xFFFFCC80)
        )
        entities += WorldEntity(
            id = "gen_blue",
            name = "ÜRETİCİ",
            kind = EntityKind.GENERATOR,
            x = 1.5f, y = 5.0f, z = -1.5f,
            bodyColor = Color(0xFFCFD8DC), headColor = Color(0xFFFFD54F)
        )

        if (includeBots) {
            entities += WorldEntity(
                id = "bot_red",
                name = "KorsanBot",
                kind = EntityKind.BOT,
                x = 14.5f, y = 5.0f, z = 13.5f,
                team = "KIRMIZI", rank = Rank.VIP_P, level = 14,
                bodyColor = Color(0xFFE74C3C), headColor = Color(0xFFFFCC80)
            )
            entities += WorldEntity(
                id = "bot_green",
                name = "ZümrütBot",
                kind = EntityKind.BOT,
                x = 14.5f, y = 5.0f, z = 1.5f,
                team = "YESIL", rank = Rank.MVIP, level = 22,
                bodyColor = Color(0xFF2ECC71), headColor = Color(0xFFFFCC80)
            )
            entities += WorldEntity(
                id = "bot_yellow",
                name = "AltınBot",
                kind = EntityKind.BOT,
                x = 0.5f, y = 5.0f, z = 13.5f,
                team = "SARI", rank = Rank.OYUNCU, level = 9,
                bodyColor = Color(0xFFFFCA28), headColor = Color(0xFFFFCC80)
            )
        }

        return GeneratedWorld(
            blocks = blocks,
            spawnPos = Triple(0.5f, 5.5f, 0.5f),
            entities = entities,
            beds = beds,
            minYVoid = -2f
        )
    }

    private fun generateSkywars(mapName: String, includeBots: Boolean): GeneratedWorld {
        val blocks = HashMap<VoxelCoord, BlockType>()
        val isVolcanic = mapName.contains("Volkan", ignoreCase = true)
        val topBlock = if (isVolcanic) BlockType.OBSIDIAN else BlockType.GRASS
        val underBlock = if (isVolcanic) BlockType.BRICK else BlockType.STONE

        val islandCenters = listOf(0 to 0, 12 to 0, 0 to 12, 12 to 12)
        islandCenters.forEach { (cx, cz) ->
            for (dx in -1..1) {
                for (dz in -1..1) {
                    blocks[VoxelCoord(cx + dx, 4, cz + dz)] = underBlock
                    blocks[VoxelCoord(cx + dx, 5, cz + dz)] = topBlock
                }
            }
            blocks[VoxelCoord(cx + 1, 6, cz)] = BlockType.CHEST
            blocks[VoxelCoord(cx - 1, 6, cz + 1)] = BlockType.WOOD
            blocks[VoxelCoord(cx - 1, 7, cz + 1)] = BlockType.LEAVES
        }
        // Center island
        for (dx in -2..2) {
            for (dz in -2..2) {
                blocks[VoxelCoord(6 + dx, 5, 6 + dz)] = if (dx == 0 && dz == 0) BlockType.DIAMOND else BlockType.STONE
            }
        }
        blocks[VoxelCoord(6, 6, 6)] = BlockType.CHEST
        blocks[VoxelCoord(6, 6, 5)] = BlockType.GOLD

        val entities = mutableListOf<WorldEntity>()
        if (includeBots) {
            entities += WorldEntity(
                id = "sw_bot_1", name = "SkyKral", kind = EntityKind.BOT,
                x = 12.5f, y = 6.0f, z = 0.5f, rank = Rank.DVIP_PP, level = 28,
                bodyColor = Color(0xFF3498DB), headColor = Color(0xFFFFCC80)
            )
            entities += WorldEntity(
                id = "sw_bot_2", name = "BulutAvcısı", kind = EntityKind.BOT,
                x = 0.5f, y = 6.0f, z = 12.5f, rank = Rank.VIP, level = 12,
                bodyColor = Color(0xFFE67E22), headColor = Color(0xFFFFCC80)
            )
            entities += WorldEntity(
                id = "sw_bot_3", name = "KorsanTR", kind = EntityKind.BOT,
                x = 12.5f, y = 6.0f, z = 12.5f, rank = Rank.YOUTUBE, level = 33,
                bodyColor = Color(0xFFD32F2F), headColor = Color(0xFFFFCC80)
            )
        }

        return GeneratedWorld(
            blocks = blocks,
            spawnPos = Triple(0.5f, 6.5f, 0.5f),
            entities = entities,
            beds = mutableListOf(),
            minYVoid = -2f
        )
    }

    private fun generateSkyblock(mapName: String): GeneratedWorld {
        val blocks = HashMap<VoxelCoord, BlockType>()
        for (x in -3..3) {
            for (z in -3..3) {
                blocks[VoxelCoord(x, 3, z)] = BlockType.DIRT
                blocks[VoxelCoord(x, 4, z)] = BlockType.GRASS
            }
        }
        // Oak tree
        for (y in 5..7) blocks[VoxelCoord(-2, y, 2)] = BlockType.WOOD
        for (dx in -1..1) for (dz in -1..1) blocks[VoxelCoord(-2 + dx, 8, 2 + dz)] = BlockType.LEAVES
        // Cobble & Ore island bridge
        for (x in 4..7) blocks[VoxelCoord(x, 4, 0)] = BlockType.WOOD
        for (dx in 8..10) {
            for (dz in -1..1) {
                blocks[VoxelCoord(dx, 4, dz)] = if (dx == 9 && dz == 0) BlockType.DIAMOND else BlockType.STONE
            }
        }
        blocks[VoxelCoord(2, 5, -2)] = BlockType.CHEST
        val entities = mutableListOf(
            WorldEntity(
                id = "sb_bank", name = "BANKACI / PAZAR", kind = EntityKind.NPC_BANK,
                x = -2.0f, y = 5.0f, z = -2.0f,
                bodyColor = Color(0xFFFFB300), headColor = Color(0xFFFFCC80)
            ),
            WorldEntity(
                id = "sb_sheep", name = "Ada Koyunu", kind = EntityKind.MOB,
                x = 1.5f, y = 5.0f, z = 1.5f, mobType = MobType.KOYUN,
                hp = 10, maxHp = 10,
                bodyColor = MobType.KOYUN.bodyColor, headColor = MobType.KOYUN.headColor
            )
        )
        return GeneratedWorld(
            blocks = blocks,
            spawnPos = Triple(0.5f, 5.5f, 0.5f),
            entities = entities,
            beds = mutableListOf(),
            minYVoid = -3f
        )
    }

    private fun generateSurvival(mapName: String, includeBots: Boolean): GeneratedWorld {
        val blocks = HashMap<VoxelCoord, BlockType>()
        val isCave = mapName.contains("Mağara", ignoreCase = true)
        for (x in -8..8) {
            for (z in -8..8) {
                val hill = if ((x * x + z * z) > 40) 1 else 0
                blocks[VoxelCoord(x, 0, z)] = BlockType.STONE
                blocks[VoxelCoord(x, 1, z)] = if (isCave) BlockType.STONE else BlockType.DIRT
                if (x in -5..-3 && z in -5..-3) {
                    blocks[VoxelCoord(x, 1, z)] = BlockType.WATER
                } else {
                    blocks[VoxelCoord(x, 2 + hill, z)] = if (isCave) BlockType.STONE else BlockType.GRASS
                }
            }
        }
        // Trees & Ore outcrops
        listOf(-4 to 4, 4 to -4, 5 to 5).forEach { (tx, tz) ->
            blocks[VoxelCoord(tx, 3, tz)] = BlockType.WOOD
            blocks[VoxelCoord(tx, 4, tz)] = BlockType.WOOD
            blocks[VoxelCoord(tx, 5, tz)] = BlockType.LEAVES
            blocks[VoxelCoord(tx + 1, 4, tz)] = BlockType.LEAVES
            blocks[VoxelCoord(tx - 1, 4, tz)] = BlockType.LEAVES
        }
        blocks[VoxelCoord(2, 3, 3)] = BlockType.DIAMOND
        blocks[VoxelCoord(-2, 3, -2)] = BlockType.GOLD

        val entities = mutableListOf(
            WorldEntity("mob_z1", "Zombi", EntityKind.MOB, 5.5f, 3.0f, -2.5f, mobType = MobType.ZOMBI, hp = 20, maxHp = 20, bodyColor = MobType.ZOMBI.bodyColor, headColor = MobType.ZOMBI.headColor),
            WorldEntity("mob_s1", "İskelet", EntityKind.MOB, -5.5f, 3.0f, 3.5f, mobType = MobType.ISKELET, hp = 16, maxHp = 16, bodyColor = MobType.ISKELET.bodyColor, headColor = MobType.ISKELET.headColor),
            WorldEntity("mob_p1", "Domuz", EntityKind.MOB, 1.5f, 3.0f, 4.5f, mobType = MobType.DOMUZ, hp = 10, maxHp = 10, bodyColor = MobType.DOMUZ.bodyColor, headColor = MobType.DOMUZ.headColor)
        )
        if (includeBots) {
            entities += WorldEntity("surv_bot", "AvcıPro", kind = EntityKind.BOT, x = -3.5f, y = 3.0f, z = -1.5f, rank = Rank.MVIP, level = 18, bodyColor = Color(0xFF4CAF50), headColor = Color(0xFFFFCC80))
        }
        return GeneratedWorld(
            blocks = blocks,
            spawnPos = Triple(0.5f, 3.5f, 0.5f),
            entities = entities,
            beds = mutableListOf(),
            minYVoid = -4f
        )
    }

    private fun generateBuildBattle(mapName: String, includeBots: Boolean): GeneratedWorld {
        val blocks = HashMap<VoxelCoord, BlockType>()
        for (x in -6..6) {
            for (z in -6..6) {
                val border = x == -6 || x == 6 || z == -6 || z == 6
                blocks[VoxelCoord(x, 0, z)] = if (border) BlockType.GOLD else BlockType.IRON_BLOCK
            }
        }
        val entities = mutableListOf<WorldEntity>()
        if (includeBots) {
            entities += WorldEntity("bb_bot", "MimarBot", EntityKind.BOT, 4.5f, 1.0f, 4.5f, rank = Rank.MIMAR, level = 26, bodyColor = Color(0xFF00BCD4), headColor = Color(0xFFFFCC80))
        }
        return GeneratedWorld(
            blocks = blocks,
            spawnPos = Triple(0.5f, 1.5f, -4.5f),
            entities = entities,
            beds = mutableListOf(),
            minYVoid = -4f
        )
    }

    private fun generateCreative(mapName: String): GeneratedWorld {
        val blocks = HashMap<VoxelCoord, BlockType>()
        val radius = if (mapName.contains("Dev", ignoreCase = true)) 8 else 6
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val border = x == -radius || x == radius || z == -radius || z == radius
                blocks[VoxelCoord(x, 0, z)] = if (border) BlockType.BRICK else BlockType.GRASS
            }
        }
        return GeneratedWorld(
            blocks = blocks,
            spawnPos = Triple(0.5f, 1.5f, -4.0f),
            entities = mutableListOf(),
            beds = mutableListOf(),
            minYVoid = -4f
        )
    }

    fun serializePlot(blocks: Map<VoxelCoord, BlockType>): String {
        // Store blocks above y == 0 (or non-default edits) compactly: "x,y,z:CODE;"
        return blocks.entries
            .filter { (coord, type) -> coord.y > 0 && type != BlockType.AIR }
            .take(500)
            .joinToString(";") { (c, t) -> "${c.x},${c.y},${c.z}:${t.code}" }
    }

    fun applySerializedPlot(target: MutableMap<VoxelCoord, BlockType>, data: String) {
        if (data.isBlank()) return
        data.split(";").forEach { token ->
            val parts = token.split(":")
            if (parts.size == 2) {
                val coords = parts[0].split(",")
                if (coords.size == 3) {
                    val x = coords[0].toIntOrNull()
                    val y = coords[1].toIntOrNull()
                    val z = coords[2].toIntOrNull()
                    if (x != null && y != null && z != null) {
                        val bt = BlockType.fromCode(parts[1])
                        if (bt == BlockType.AIR) target.remove(VoxelCoord(x, y, z))
                        else target[VoxelCoord(x, y, z)] = bt
                    }
                }
            }
        }
    }
}
