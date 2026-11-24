package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.model.*
import kotlin.math.*
import kotlin.random.Random

enum class WorldModalType {
    MARKET_NPC,
    BANK_NPC,
    BUILD_BATTLE_VOTE,
    INVENTORY,
    CREATIVE_MENU,
    PLAYER_INTERACT
}

enum class MatchOutcome {
    WON,
    LOST
}

data class MatchResultState(
    val outcome: MatchOutcome,
    val title: String,
    val subtitle: String,
    val returnCountdownSec: Int = 8,
    val dismissedToSpectate: Boolean = false
)

data class WorldSessionState(
    val active: Boolean = false,
    val roomId: String = "",
    val networkId: String = "official",
    val serverType: ServerType = ServerType.LOBI,
    val mapName: String = "",
    val waitingMapName: String = "",
    val isWaitingPhase: Boolean = false,
    val isWaitingPaused: Boolean = false,
    val waitingPlayerCount: Int = 1,
    val maxWaitingPlayers: Int = 8,
    val waitingCountdownSec: Int = 6,
    val matchTimerSec: Int = 180,
    val buildTheme: String = "Deniz Kalesi",
    val isVotingPhase: Boolean = false,
    val winnerName: String? = null,
    val matchResult: MatchResultState? = null,
    val isSpectating: Boolean = false,
    val spectateTargetName: String? = null,
    val camera: CameraState = CameraState(),
    val velY: Float = 0f,
    val onGround: Boolean = true,
    val isFlying: Boolean = false,
    val hp: Int = 20,
    val maxHp: Int = 20,
    val hunger: Int = 20,
    val hurtFlashAlpha: Float = 0f,
    val hurtShakeTimer: Float = 0f,
    val gameMode: GameMode = GameMode.SURVIVAL,
    val beds: List<BedState> = emptyList(),
    val entities: List<WorldEntity> = emptyList(),
    val remotePlayers: List<RoomPlayer> = emptyList(),
    val targetedCoord: VoxelCoord? = null,
    val targetedEntity: WorldEntity? = null,
    val interactTargetEntity: WorldEntity? = null,
    val iron: Int = 12,
    val gold: Int = 2,
    val emerald: Int = 0,
    val diamond: Int = 0,
    val selectedSlotIndex: Int = 0,
    val swingProgress: Float = 0f,
    val activeModal: WorldModalType? = null,
    val eventMessage: String? = null,
    val frameVersion: Long = 0L
)

class GameSessionController {
    val blocks: MutableMap<VoxelCoord, BlockType> = HashMap()
    private val protectedMapBlocks: MutableSet<VoxelCoord> = HashSet()
    private val playerPlacedBlocks: MutableSet<VoxelCoord> = HashSet()
    private val lootedChests: MutableSet<VoxelCoord> = HashSet()
    private var minYVoid: Float = -4f
    private var spawnPos: Triple<Float, Float, Float> = Triple(0.5f, 2.5f, 0.5f)
    private var isOfflineSession: Boolean = false

    private var timerAccum: Float = 0f
    private var genAccum: Float = 0f
    private var botBuildAccum: Float = 0f
    private var hungerAccum: Float = 0f
    private var starveAccum: Float = 0f
    private var regenAccum: Float = 0f
    private var lastServerBlockHash: Int = 0

    companion object {
        private val BUILD_THEMES = listOf(
            "Deniz Kalesi",
            "Korsan Gemisi",
            "Gökyüzü Tapınağı",
            "Kristal Kule",
            "Dev Yengeç",
            "Modern Villa"
        )
    }

    fun startSession(
        roomId: String,
        networkId: String,
        serverType: ServerType,
        mapName: String,
        gameMode: GameMode,
        savedPlotData: String,
        isOffline: Boolean,
        spectating: Boolean = false,
        spectateTargetName: String? = null,
        initialIron: Int = 12,
        initialGold: Int = 2,
        initialEmerald: Int = 0,
        initialDiamond: Int = 0,
        customTheme: String = ""
    ): WorldSessionState {
        isOfflineSession = isOffline
        lootedChests.clear()
        playerPlacedBlocks.clear()
        protectedMapBlocks.clear()
        timerAccum = 0f
        genAccum = 0f
        botBuildAccum = 0f
        hungerAccum = 0f
        starveAccum = 0f
        regenAccum = 0f
        lastServerBlockHash = 0

        val needsWaiting = !spectating && (
            serverType == ServerType.BEDWARS ||
                serverType == ServerType.SKYWARS ||
                serverType == ServerType.BUILD_BATTLE
            )

        val generated = if (needsWaiting) {
            VoxelMapGenerator.generateWaitingLobby(serverType, serverType.waitingLobbyMap)
        } else {
            VoxelMapGenerator.generateMatchWorld(
                serverType = serverType,
                mapName = mapName,
                savedPlotData = savedPlotData,
                includeBots = isOffline || serverType != ServerType.LOBI
            )
        }

        blocks.clear()
        blocks.putAll(generated.blocks)
        if (needsWaiting || serverType == ServerType.LOBI) {
            protectedMapBlocks.addAll(generated.blocks.keys)
        } else if (serverType == ServerType.BEDWARS || serverType == ServerType.SKYWARS) {
            generated.blocks.forEach { (coord, type) ->
                if (type != BlockType.BED && type != BlockType.CHEST) {
                    protectedMapBlocks.add(coord)
                }
            }
        }
        spawnPos = generated.spawnPos
        minYVoid = generated.minYVoid

        val theme = customTheme.ifBlank { BUILD_THEMES.random() }
        val effectiveMode = when {
            spectating -> GameMode.SPECTATOR
            serverType == ServerType.CREATIVE || serverType == ServerType.BUILD_BATTLE -> GameMode.CREATIVE
            else -> gameMode
        }

        // In offline mode, bots fill the lobby so countdown starts immediately at 3s.
        // In online mode, start paused until >= 2 real players join.
        val initialPaused = needsWaiting && !isOffline
        val initialCountdown = when {
            !needsWaiting -> 0
            isOffline -> 3
            else -> -1
        }
        val initialPlayerCount = if (isOffline) serverType.defaultMaxPlayers else 1

        return WorldSessionState(
            active = true,
            roomId = roomId,
            networkId = networkId,
            serverType = serverType,
            mapName = mapName,
            waitingMapName = serverType.waitingLobbyMap,
            isWaitingPhase = needsWaiting,
            isWaitingPaused = initialPaused,
            waitingPlayerCount = initialPlayerCount,
            maxWaitingPlayers = serverType.defaultMaxPlayers,
            waitingCountdownSec = initialCountdown,
            matchTimerSec = if (serverType == ServerType.BUILD_BATTLE) 90 else 240,
            buildTheme = theme,
            isVotingPhase = false,
            winnerName = null,
            matchResult = null,
            isSpectating = spectating,
            spectateTargetName = spectateTargetName,
            camera = CameraState(
                x = spawnPos.first,
                y = spawnPos.second + 1.55f,
                z = spawnPos.third,
                yawDeg = 0f,
                pitchDeg = -8f
            ),
            isFlying = effectiveMode == GameMode.SPECTATOR,
            hp = 20,
            maxHp = 20,
            hunger = 20,
            hurtFlashAlpha = 0f,
            hurtShakeTimer = 0f,
            gameMode = effectiveMode,
            beds = generated.beds,
            entities = generated.entities,
            iron = initialIron,
            gold = initialGold,
            emerald = initialEmerald,
            diamond = initialDiamond,
            eventMessage = if (needsWaiting) {
                if (isOffline) "Bekleme Alanı: ${serverType.waitingLobbyMap}"
                else "Oyuncular bekleniyor (1/${serverType.defaultMaxPlayers})..."
            } else {
                "${serverType.displayName} • $mapName"
            }
        )
    }

    fun transitionToMatchArena(
        state: WorldSessionState,
        savedPlotData: String = ""
    ): WorldSessionState {
        val includeBots = isOfflineSession || state.remotePlayers.count { it.alive } <= 1
        val generated = VoxelMapGenerator.generateMatchWorld(
            serverType = state.serverType,
            mapName = state.mapName,
            savedPlotData = savedPlotData,
            includeBots = includeBots
        )
        blocks.clear()
        blocks.putAll(generated.blocks)
        protectedMapBlocks.clear()
        playerPlacedBlocks.clear()
        if (state.serverType == ServerType.BEDWARS || state.serverType == ServerType.SKYWARS) {
            generated.blocks.forEach { (coord, type) ->
                if (type != BlockType.BED && type != BlockType.CHEST) {
                    protectedMapBlocks.add(coord)
                }
            }
        }
        spawnPos = generated.spawnPos
        minYVoid = generated.minYVoid
        lootedChests.clear()

        val preservedRemotePlayers = state.entities.filter { it.kind == EntityKind.PLAYER }
        return state.copy(
            isWaitingPhase = false,
            isWaitingPaused = false,
            waitingCountdownSec = 0,
            matchResult = null,
            winnerName = null,
            camera = CameraState(
                x = spawnPos.first,
                y = spawnPos.second + 1.55f,
                z = spawnPos.third,
                yawDeg = 0f,
                pitchDeg = -8f
            ),
            velY = 0f,
            hp = 20,
            hunger = 20,
            beds = generated.beds,
            entities = generated.entities + preservedRemotePlayers,
            eventMessage = "Maç Başladı! Harita: ${state.mapName}",
            frameVersion = state.frameVersion + 1L
        )
    }

    fun dismissMatchResultToSpectate(state: WorldSessionState): WorldSessionState {
        val mr = state.matchResult ?: return state
        return state.copy(
            gameMode = GameMode.SPECTATOR,
            isFlying = true,
            matchResult = mr.copy(dismissedToSpectate = true),
            eventMessage = "İzleyici moduna geçildi.",
            frameVersion = state.frameVersion + 1L
        )
    }

    fun mergeRemotePlayers(state: WorldSessionState, remotePlayers: List<RoomPlayer>, localUid: String?): WorldSessionState {
        val nonPlayers = state.entities.filter { it.kind != EntityKind.PLAYER }
        val prevRemoteById = state.entities.filter { it.kind == EntityKind.PLAYER }.associateBy { it.id }
        val remoteEntities = remotePlayers
            .filter { it.userId != localUid && it.alive }
            .map { rp ->
                val palette = SKIN_PALETTES.getOrNull(rp.skinColor.toInt())?.second ?: Color(0xFF00BCD4)
                val entityId = "player_${rp.userId}"
                val prev = prevRemoteById[entityId]
                val incomingHp = rp.hp.toInt().coerceIn(0, 20)
                val hurtFlash = if (prev != null && incomingHp < prev.hp) 0.35f else (prev?.hurtFlashTimer ?: 0f)
                WorldEntity(
                    id = entityId,
                    name = rp.username,
                    kind = EntityKind.PLAYER,
                    x = rp.x.toFloat(),
                    y = rp.y.toFloat(),
                    z = rp.z.toFloat(),
                    yaw = rp.yaw.toFloat(),
                    hp = incomingHp,
                    maxHp = 20,
                    rank = Rank.fromCode(rp.rank),
                    level = rp.level.toInt().coerceIn(1, 40),
                    team = rp.team,
                    bodyColor = palette,
                    headColor = Color(0xFFFFCC80),
                    alive = rp.alive,
                    hurtFlashTimer = hurtFlash
                )
            }
        return state.copy(
            remotePlayers = remotePlayers,
            entities = nonPlayers + remoteEntities,
            frameVersion = state.frameVersion + 1L
        )
    }

    fun mergeRemoteBlocks(state: WorldSessionState, remoteBlocks: List<RoomBlock>): WorldSessionState {
        if (state.isWaitingPhase || remoteBlocks.isEmpty()) return state
        val hash = remoteBlocks.hashCode()
        if (hash == lastServerBlockHash) return state
        lastServerBlockHash = hash
        for (rb in remoteBlocks) {
            val coord = VoxelCoord(rb.x.toInt(), rb.y.toInt(), rb.z.toInt())
            val type = BlockType.fromCode(rb.blockType)
            if (type == BlockType.AIR) {
                blocks.remove(coord)
                playerPlacedBlocks.remove(coord)
            } else {
                blocks[coord] = type
                playerPlacedBlocks.add(coord)
            }
        }
        return state.copy(frameVersion = state.frameVersion + 1L)
    }

    fun stepFrame(
        current: WorldSessionState,
        dt: Float,
        moveForward: Float,
        moveStrafe: Float,
        flyUp: Boolean,
        flyDown: Boolean,
        savedPlotData: String,
        onMatchRewards: (coinsDelta: Int, xpDelta: Int) -> Unit,
        onMatchEnded: (won: Boolean) -> Unit = {},
        onAutoReturnToLobby: () -> Unit = {}
    ): WorldSessionState {
        if (!current.active) return current
        val clampedDt = dt.coerceIn(0.005f, 0.05f)
        var s = current
        var newlyEndedOutcome: MatchOutcome? = null
        var shouldReturnLobby = false

        // 1-second timer tick (waiting offline countdown, BuildBattle timer, Match Result return-to-lobby countdown)
        timerAccum += clampedDt
        if (timerAccum >= 1.0f) {
            timerAccum -= 1.0f
            val mr = s.matchResult
            if (mr != null && !mr.dismissedToSpectate) {
                val nextReturn = mr.returnCountdownSec - 1
                if (nextReturn <= 0) {
                    shouldReturnLobby = true
                    s = s.copy(matchResult = mr.copy(returnCountdownSec = 0))
                } else {
                    s = s.copy(matchResult = mr.copy(returnCountdownSec = nextReturn))
                }
            } else if (s.isWaitingPhase) {
                if (isOfflineSession && !s.isWaitingPaused) {
                    val nextWait = s.waitingCountdownSec - 1
                    if (nextWait <= 0) {
                        s = transitionToMatchArena(s, savedPlotData)
                    } else {
                        s = s.copy(waitingCountdownSec = nextWait)
                    }
                }
            } else if (s.serverType == ServerType.BUILD_BATTLE && !s.isVotingPhase && s.winnerName == null) {
                val nextMatch = s.matchTimerSec - 1
                if (nextMatch <= 0) {
                    s = s.copy(
                        matchTimerSec = 0,
                        isVotingPhase = true,
                        activeModal = WorldModalType.BUILD_BATTLE_VOTE,
                        eventMessage = "Süre Doldu! Yapıları Oyla"
                    )
                } else {
                    s = s.copy(matchTimerSec = nextMatch)
                }
            }
        }

        // Bedwars Generator ticks
        if (!s.isWaitingPhase && s.serverType == ServerType.BEDWARS) {
            genAccum += clampedDt
            if (genAccum >= 2.2f) {
                genAccum = 0f
                val playerToGenDist = hypot(s.camera.x - 1.5f, s.camera.z - (-1.5f))
                val bonusIron = if (playerToGenDist < 4.5f) 2 else 1
                val bonusGold = if (playerToGenDist < 4.5f) 1 else 0
                s = s.copy(
                    iron = s.iron + bonusIron,
                    gold = s.gold + bonusGold
                )
            }
        }

        // Player Movement & Physics
        val yawRad = Math.toRadians(s.camera.yawDeg.toDouble()).toFloat()
        val forwardX = sin(yawRad)
        val forwardZ = cos(yawRad)
        val rightX = cos(yawRad)
        val rightZ = -sin(yawRad)

        val speed = when {
            s.gameMode == GameMode.SPECTATOR -> 8.2f
            s.isFlying -> 7.6f
            s.gameMode == GameMode.CREATIVE -> 5.8f
            else -> 4.8f
        }
        val wishX = (forwardX * moveForward + rightX * moveStrafe) * speed * clampedDt
        val wishZ = (forwardZ * moveForward + rightZ * moveStrafe) * speed * clampedDt

        var nx = s.camera.x
        var feetY = s.camera.y - 1.55f
        var nz = s.camera.z
        var velY = s.velY
        var onGround = s.onGround
        val canFly = s.gameMode == GameMode.CREATIVE || s.gameMode == GameMode.SPECTATOR
        var isFlying = when {
            s.gameMode == GameMode.SPECTATOR -> true
            canFly -> s.isFlying
            else -> false
        }
        var hp = s.hp
        var hunger = if (s.gameMode == GameMode.CREATIVE || s.gameMode == GameMode.SPECTATOR) 20 else s.hunger
        var hurtFlash = (s.hurtFlashAlpha - clampedDt * 1.9f).coerceAtLeast(0f)
        var hurtShake = (s.hurtShakeTimer - clampedDt * 2.1f).coerceAtLeast(0f)
        var msg = s.eventMessage
        var updatedBeds = s.beds

        if (s.gameMode == GameMode.SPECTATOR) {
            nx += wishX
            nz += wishZ
            if (flyUp) feetY += 6.2f * clampedDt
            if (flyDown) feetY -= 6.2f * clampedDt
            onGround = false
            velY = 0f
            isFlying = true
        } else {
            if (!GamePhysics.isColliding(nx + wishX, feetY, nz, blocks)) {
                nx += wishX
            }
            if (!GamePhysics.isColliding(nx, feetY, nz + wishZ, blocks)) {
                nz += wishZ
            }

            // Soft entity-to-entity collision separation
            for (entity in s.entities) {
                if (!entity.alive) continue
                if (entity.kind != EntityKind.PLAYER && entity.kind != EntityKind.BOT && entity.kind != EntityKind.MOB) continue
                val dyEntity = abs(feetY - entity.y)
                if (dyEntity > 1.7f) continue
                val ex = nx - entity.x
                val ez = nz - entity.z
                val dist = hypot(ex, ez)
                if (dist in 0.001f..0.72f) {
                    val push = (0.72f - dist) * 0.45f
                    val px = (ex / dist) * push
                    val pz = (ez / dist) * push
                    if (!GamePhysics.isColliding(nx + px, feetY, nz, blocks)) nx += px
                    if (!GamePhysics.isColliding(nx, feetY, nz + pz, blocks)) nz += pz
                }
            }

            if (isFlying) {
                velY = 0f
                if (flyUp) {
                    val dy = 5.8f * clampedDt
                    if (!GamePhysics.hasCeilingAbove(nx, feetY + dy + 1.75f, nz, blocks)) {
                        feetY += dy
                    }
                    onGround = false
                } else if (flyDown) {
                    val dy = -5.8f * clampedDt
                    val candY = feetY + dy
                    if (GamePhysics.hasFloorUnder(nx, candY, nz, blocks)) {
                        feetY = floor(candY) + 1.0f
                        onGround = true
                        isFlying = false
                        msg = "Yere inildi (Uçuş kapandı)"
                    } else {
                        feetY = candY
                        onGround = false
                    }
                } else {
                    onGround = false
                }
            } else if (canFly && flyUp) {
                isFlying = true
                velY = 0f
                onGround = false
                val dy = 5.8f * clampedDt
                if (!GamePhysics.hasCeilingAbove(nx, feetY + dy + 1.75f, nz, blocks)) {
                    feetY += dy
                }
            } else {
                val prevVelY = velY
                velY = (velY - 22f * clampedDt).coerceAtLeast(-28f)
                val totalDy = velY * clampedDt
                val subSteps = if (abs(totalDy) > 0.35f) 2 else 1
                val stepDy = totalDy / subSteps
                var landed = false
                var bonked = false
                for (i in 0 until subSteps) {
                    val nextY = feetY + stepDy
                    if (velY < 0f && GamePhysics.hasFloorUnder(nx, nextY, nz, blocks)) {
                        feetY = floor(nextY) + 1.0f
                        landed = true
                        break
                    } else if (velY > 0f && GamePhysics.hasCeilingAbove(nx, nextY + 1.75f, nz, blocks)) {
                        bonked = true
                        break
                    } else {
                        feetY = nextY
                    }
                }
                if (landed) {
                    if (prevVelY < -11.5f && (s.gameMode == GameMode.SURVIVAL || s.gameMode == GameMode.ADVENTURE)) {
                        val fallDmg = ((-prevVelY - 10.5f) * 1.4f).toInt().coerceIn(1, 16)
                        hp = (hp - fallDmg).coerceAtLeast(0)
                        hurtFlash = 0.72f
                        hurtShake = 0.38f
                        msg = "Yüksekten düştün! -$fallDmg ♥"
                    }
                    velY = 0f
                    onGround = true
                } else if (bonked) {
                    velY = 0f
                    onGround = false
                } else {
                    onGround = false
                }
            }
        }

        // Survival / Adventure Hunger, Regeneration & Starvation
        if (s.gameMode == GameMode.SURVIVAL || s.gameMode == GameMode.ADVENTURE) {
            val moving = hypot(moveForward, moveStrafe) > 0.12f
            hungerAccum += clampedDt * if (moving) 0.45f else 0.06f
            if (hungerAccum >= 4.0f) {
                hungerAccum -= 4.0f
                hunger = (hunger - 1).coerceAtLeast(0)
            }
            if (hunger >= 18 && hp in 1 until s.maxHp) {
                regenAccum += clampedDt
                if (regenAccum >= 3.2f) {
                    regenAccum = 0f
                    hp = (hp + 1).coerceAtMost(s.maxHp)
                }
            } else {
                regenAccum = 0f
            }
            if (hunger == 0 && hp > 1) {
                starveAccum += clampedDt
                if (starveAccum >= 3.5f) {
                    starveAccum = 0f
                    hp = (hp - 1).coerceAtLeast(1)
                    hurtFlash = maxOf(hurtFlash, 0.50f)
                    hurtShake = maxOf(hurtShake, 0.25f)
                    msg = "Açlıktan canın azalıyor! Yemek ye."
                }
            } else {
                starveAccum = 0f
            }
        }

        // Void Check (Bedwars no-bed & Skywars elimination vs Lobby/Respawn)
        var currentMatchResult = s.matchResult
        if (feetY < minYVoid && s.gameMode != GameMode.SPECTATOR) {
            val blueBedIntact = updatedBeds.firstOrNull { it.team == "MAVI" }?.intact ?: true
            val isEliminated = !s.isWaitingPhase && (
                (s.serverType == ServerType.BEDWARS && !blueBedIntact) ||
                    s.serverType == ServerType.SKYWARS
                )
            if (isEliminated) {
                hp = 0
                isFlying = true
                msg = if (s.serverType == ServerType.SKYWARS) {
                    "Skywars boşluğuna düştün! Maçtan elendin."
                } else {
                    "Yatağın kırık olduğu için elendin!"
                }
                s = s.copy(gameMode = GameMode.SPECTATOR)
                if (currentMatchResult == null) {
                    currentMatchResult = MatchResultState(
                        outcome = MatchOutcome.LOST,
                        title = "ELENDİN!",
                        subtitle = msg ?: "Maçı kaybettin. Lobiye dönülüyor...",
                        returnCountdownSec = 8
                    )
                    newlyEndedOutcome = MatchOutcome.LOST
                }
            } else {
                nx = spawnPos.first
                feetY = spawnPos.second
                nz = spawnPos.third
                velY = 0f
                hp = 20
                hunger = 20
                hurtFlash = 0.65f
                hurtShake = 0.35f
                msg = "Boşluğa düştün! Başlangıç noktasına döndün."
            }
        }

        // --- AI ENTITIES UPDATE (Mobs & Bots with Movement, Jumping, Combat, Block & Entity Interaction) ---
        val entityHitDeltas = HashMap<String, Int>()
        val sourceEntities = s.entities
        val updatedEntities = sourceEntities.map { entity ->
            if (!entity.alive) return@map entity
            val nextCooldown = (entity.attackCooldown - clampedDt).coerceAtLeast(0f)
            val nextAiCooldown = (entity.aiActionCooldown - clampedDt).coerceAtLeast(0f)
            val nextHurtFlash = (entity.hurtFlashTimer - clampedDt * 2.2f).coerceAtLeast(0f)

            when (entity.kind) {
                EntityKind.MOB -> {
                    val hostile = entity.mobType?.hostile == true
                    val dx = nx - entity.x
                    val dz = nz - entity.z
                    val dist = hypot(dx, dz)
                    var ex = entity.x
                    var ey = entity.y
                    var ez = entity.z
                    var eVelY = entity.velY
                    var eOnGround = entity.onGround
                    var eYaw = entity.yaw
                    var cd = nextCooldown

                    if (hostile && !s.isWaitingPhase && s.gameMode == GameMode.SURVIVAL && dist in 0.9f..9.5f) {
                        val step = 1.7f * clampedDt
                        val candX = ex + (dx / dist) * step
                        val candZ = ez + (dz / dist) * step
                        eYaw = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
                        if (GamePhysics.isColliding(candX, ey, candZ, blocks) && eOnGround) {
                            eVelY = 7.8f
                            eOnGround = false
                        } else if (!GamePhysics.isColliding(candX, ey, candZ, blocks) && hasFloorUnder(candX, ey - 0.25f, candZ)) {
                            ex = candX
                            ez = candZ
                        }
                    }

                    // Mob gravity
                    eVelY = (eVelY - 22f * clampedDt).coerceAtLeast(-20f)
                    val nextMobY = ey + eVelY * clampedDt
                    if (eVelY < 0f && hasFloorUnder(ex, nextMobY, ez)) {
                        ey = floor(nextMobY) + 1.0f
                        eVelY = 0f
                        eOnGround = true
                    } else {
                        ey = nextMobY
                        eOnGround = false
                    }

                    if (hostile && !s.isWaitingPhase && s.gameMode == GameMode.SURVIVAL && dist < 1.55f && abs(feetY - ey) < 1.6f && cd <= 0f) {
                        hp = (hp - 2).coerceAtLeast(0)
                        hurtFlash = 0.72f
                        hurtShake = 0.38f
                        cd = 1.2f
                        msg = "${entity.name} sana saldırdı!"
                    }
                    entity.copy(
                        x = ex, y = ey, z = ez, yaw = eYaw,
                        velY = eVelY, onGround = eOnGround,
                        attackCooldown = cd, hurtFlashTimer = nextHurtFlash
                    )
                }

                EntityKind.BOT -> {
                    var ex = entity.x
                    var ey = entity.y
                    var ez = entity.z
                    var eVelY = entity.velY
                    var eOnGround = entity.onGround
                    var eYaw = entity.yaw
                    var cd = nextCooldown
                    var aiCd = nextAiCooldown
                    var wanderTimer = entity.aiWanderTimer - clampedDt
                    var strafeSign = entity.strafeSign

                    if (wanderTimer <= 0f) {
                        wanderTimer = 1.8f + (abs(entity.id.hashCode() % 10) * 0.15f)
                        strafeSign = -strafeSign
                    }

                    // 1. Gravity & Void Check for Bot
                    eVelY = (eVelY - 22f * clampedDt).coerceAtLeast(-24f)
                    val nextY = ey + eVelY * clampedDt
                    if (eVelY < 0f && hasFloorUnder(ex, nextY, ez)) {
                        ey = floor(nextY) + 1.0f
                        eVelY = 0f
                        eOnGround = true
                    } else {
                        ey = nextY
                        eOnGround = false
                    }

                    if (ey < minYVoid) {
                        val ownBed = updatedBeds.firstOrNull { it.team == entity.team }
                        if (!s.isWaitingPhase && s.serverType == ServerType.BEDWARS && ownBed != null && ownBed.intact) {
                            ex = ownBed.coord.x + 0.5f
                            ey = ownBed.coord.y + 0.6f
                            ez = ownBed.coord.z.toFloat()
                            eVelY = 0f
                        } else if (!s.isWaitingPhase) {
                            msg = "${entity.name} boşluğa düştü!"
                            return@map entity.copy(alive = false, hp = 0, hurtFlashTimer = nextHurtFlash)
                        } else {
                            ex = 0.5f; ey = 1.5f; ez = 0.5f; eVelY = 0f
                        }
                    }

                    // 2. Determine Bot AI Goal (Waiting Lobby Patrol vs Combat / Bed Hunting / Entity Interaction)
                    if (s.isWaitingPhase) {
                        // Patrol & playfully jump around waiting lobby
                        val angle = wanderTimer * 1.4f + (entity.id.hashCode() % 7)
                        val goalX = sin(angle) * 2.8f
                        val goalZ = cos(angle) * 2.8f
                        val dx = goalX - ex
                        val dz = goalZ - ez
                        val d = hypot(dx, dz)
                        if (d > 0.35f) {
                            val step = 1.6f * clampedDt
                            val cx = ex + (dx / d) * step
                            val cz = ez + (dz / d) * step
                            eYaw = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
                            if (GamePhysics.isColliding(cx, ey, cz, blocks) && eOnGround) {
                                eVelY = 7.6f
                                eOnGround = false
                            } else if (!GamePhysics.isColliding(cx, ey, cz, blocks) && hasFloorUnder(cx, ey - 0.3f, cz)) {
                                ex = cx
                                ez = cz
                            }
                        }
                        if (eOnGround && aiCd <= 0f) {
                            eVelY = 7.2f
                            eOnGround = false
                            aiCd = 3.2f
                        }
                        return@map entity.copy(
                            x = ex, y = ey, z = ez, yaw = eYaw,
                            velY = eVelY, onGround = eOnGround,
                            attackCooldown = cd, aiActionCooldown = aiCd,
                            aiWanderTimer = wanderTimer, strafeSign = strafeSign,
                            hurtFlashTimer = nextHurtFlash
                        )
                    }

                    // Match AI Target Selection
                    val playerEligible = s.gameMode == GameMode.SURVIVAL &&
                        (s.serverType == ServerType.BEDWARS || s.serverType == ServerType.SKYWARS)
                    val playerDist = if (playerEligible) hypot(nx - ex, nz - ez) else Float.MAX_VALUE

                    // Nearest other alive Mob or opposing Bot
                    val nearestOtherEntity = sourceEntities
                        .filter { other ->
                            other.alive && other.id != entity.id && (
                                (other.kind == EntityKind.MOB && (other.mobType?.hostile == true || s.serverType == ServerType.SURVIVAL)) ||
                                    (other.kind == EntityKind.BOT && (s.serverType == ServerType.SKYWARS || (s.serverType == ServerType.BEDWARS && other.team != entity.team)))
                                )
                        }
                        .minByOrNull { hypot(it.x - ex, it.z - ez) }
                    val otherDist = nearestOtherEntity?.let { hypot(it.x - ex, it.z - ez) } ?: Float.MAX_VALUE

                    // Nearest opposing Bed in Bedwars (prefer breaking bed if closer or player is far)
                    val targetBed = if (s.serverType == ServerType.BEDWARS) {
                        updatedBeds.filter { it.intact && it.team != entity.team }
                            .minByOrNull { hypot(it.coord.x + 0.5f - ex, it.coord.z + 0.5f - ez) }
                    } else null
                    val bedDist = targetBed?.let { hypot(it.coord.x + 0.5f - ex, it.coord.z + 0.5f - ez) } ?: Float.MAX_VALUE

                    var targetX = ex
                    var targetZ = ez
                    var hasTarget = false
                    var targetIsCombatEntity = false
                    var targetingPlayer = false

                    when {
                        playerEligible && playerDist <= minOf(otherDist, bedDist + 1.5f) && playerDist < 18f -> {
                            targetX = nx; targetZ = nz; hasTarget = true
                            targetIsCombatEntity = true; targetingPlayer = true
                        }
                        nearestOtherEntity != null && otherDist < 16f && (s.serverType == ServerType.SURVIVAL || otherDist <= bedDist) -> {
                            targetX = nearestOtherEntity.x; targetZ = nearestOtherEntity.z
                            hasTarget = true; targetIsCombatEntity = true
                        }
                        targetBed != null -> {
                            targetX = targetBed.coord.x + 0.5f; targetZ = targetBed.coord.z + 0.5f
                            hasTarget = true
                        }
                        else -> {
                            // Wander around plot/arena
                            val angle = wanderTimer + (entity.id.hashCode() % 11)
                            targetX = ex + sin(angle) * 2.5f
                            targetZ = ez + cos(angle) * 2.5f
                            hasTarget = true
                        }
                    }

                    if (hasTarget) {
                        val dx = targetX - ex
                        val dz = targetZ - ez
                        val dist = hypot(dx, dz)
                        if (dist > 0.25f) {
                            val dirX = dx / dist
                            val dirZ = dz / dist
                            eYaw = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
                            val strafeScale = if (targetIsCombatEntity && dist < 3.4f) 0.45f * strafeSign else 0f
                            val moveX = (dirX - dirZ * strafeScale) * 2.15f * clampedDt
                            val moveZ = (dirZ + dirX * strafeScale) * 2.15f * clampedDt
                            val candX = ex + moveX
                            val candZ = ez + moveZ

                            val blocked = GamePhysics.isColliding(candX, ey, candZ, blocks)
                            val floorAhead = hasFloorUnder(candX, ey - 0.35f, candZ)

                            if (blocked) {
                                val stepClear = !GamePhysics.isColliding(candX, ey + 1.05f, candZ, blocks)
                                if (stepClear && eOnGround) {
                                    // Bot Jump over 1-block obstacle
                                    eVelY = 8.2f
                                    eOnGround = false
                                } else if (aiCd <= 0f) {
                                    // Bot Block Interaction: break obstructing player-placed block
                                    val obsCoord = VoxelCoord(floor(candX).toInt(), floor(ey).toInt(), floor(candZ).toInt())
                                    if (obsCoord in playerPlacedBlocks) {
                                        blocks.remove(obsCoord)
                                        playerPlacedBlocks.remove(obsCoord)
                                        aiCd = 0.65f
                                    }
                                }
                            } else if (floorAhead || !eOnGround) {
                                ex = candX
                                ez = candZ
                            } else if ((s.serverType == ServerType.BEDWARS || s.serverType == ServerType.SKYWARS) && aiCd <= 0f) {
                                // Bot Block Interaction: bridge across void gap!
                                val bridgeCoord = VoxelCoord(floor(ex + dirX * 0.9f).toInt(), floor(ey).toInt() - 1, floor(ez + dirZ * 0.9f).toInt())
                                if (!blocks.containsKey(bridgeCoord)) {
                                    val bridgeMat = if (entity.team == "KIRMIZI") BlockType.WOOL_RED else BlockType.WOOD
                                    blocks[bridgeCoord] = bridgeMat
                                    playerPlacedBlocks.add(bridgeCoord)
                                    aiCd = 0.42f
                                }
                            }
                        }

                        // Combat Jump (bunny hop in PvP)
                        if (targetIsCombatEntity && dist in 1.6f..4.2f && eOnGround && aiCd <= 0f) {
                            eVelY = 7.4f
                            eOnGround = false
                            aiCd = 1.6f
                        }

                        // Combat Attack on Player
                        if (targetingPlayer && dist < 1.85f && abs(feetY - ey) < 1.7f && cd <= 0f) {
                            hp = (hp - 2).coerceAtLeast(0)
                            hurtFlash = 0.72f
                            hurtShake = 0.38f
                            cd = 1.05f
                            msg = "${entity.name} sana saldırdı!"
                        } else if (!targetingPlayer && nearestOtherEntity != null && otherDist < 1.95f && cd <= 0f) {
                            // Entity Interaction: Bot attacks Mob or Enemy Bot
                            entityHitDeltas[nearestOtherEntity.id] = (entityHitDeltas[nearestOtherEntity.id] ?: 0) + 3
                            cd = 1.1f
                            if (nearestOtherEntity.kind == EntityKind.MOB) {
                                msg = "${entity.name}, ${nearestOtherEntity.name} ile savaşıyor!"
                            }
                        }

                        // Block Interaction: Bot breaks opposing intact Bedwars bed!
                        if (targetBed != null && bedDist < 2.0f && aiCd <= 0f) {
                            blocks.remove(targetBed.coord)
                            updatedBeds = updatedBeds.map {
                                if (it.coord == targetBed.coord) it.copy(intact = false) else it
                            }
                            aiCd = 1.5f
                            hurtShake = maxOf(hurtShake, 0.25f)
                            msg = "${entity.name}, ${targetBed.label} Takımının Yatağını Kırdı!"
                        }
                    }

                    entity.copy(
                        x = ex, y = ey, z = ez, yaw = eYaw,
                        velY = eVelY, onGround = eOnGround,
                        attackCooldown = cd, aiActionCooldown = aiCd,
                        aiWanderTimer = wanderTimer, strafeSign = strafeSign,
                        hurtFlashTimer = nextHurtFlash
                    )
                }

                else -> entity.copy(hurtFlashTimer = nextHurtFlash)
            }
        }.map { e ->
            val dmgTaken = entityHitDeltas[e.id] ?: 0
            if (dmgTaken > 0 && e.alive) {
                val nh = (e.hp - dmgTaken).coerceAtLeast(0)
                e.copy(hp = nh, alive = nh > 0, hurtFlashTimer = 0.35f)
            } else {
                e
            }
        }.toMutableList()

        // BuildBattle Bot periodically places blocks on its showcase side
        if (!s.isWaitingPhase && s.serverType == ServerType.BUILD_BATTLE && !s.isVotingPhase) {
            botBuildAccum += clampedDt
            if (botBuildAccum >= 2.2f) {
                botBuildAccum = 0f
                val rx = Random.nextInt(2, 6)
                val ry = Random.nextInt(1, 5)
                val rz = Random.nextInt(2, 6)
                val c = VoxelCoord(rx, ry, rz)
                if (!blocks.containsKey(c)) {
                    blocks[c] = listOf(BlockType.GOLD, BlockType.DIAMOND, BlockType.BRICK, BlockType.WOOL_CYAN).random()
                    playerPlacedBlocks.add(c)
                }
            }
        }

        // Check player death -> respawn or eliminate (and trigger Return-to-Lobby result on elimination!)
        if (hp <= 0 && s.gameMode == GameMode.SURVIVAL) {
            val blueBed = updatedBeds.firstOrNull { it.team == "MAVI" }
            val eliminated = (s.serverType == ServerType.BEDWARS && blueBed != null && !blueBed.intact) ||
                s.serverType == ServerType.SKYWARS
            if (eliminated) {
                msg = if (s.serverType == ServerType.SKYWARS) "Elendin! İzleyici moduna geçtin." else "Yatağın kırık olduğu için elendin!"
                isFlying = true
                s = s.copy(gameMode = GameMode.SPECTATOR, hp = 0)
                if (currentMatchResult == null) {
                    currentMatchResult = MatchResultState(
                        outcome = MatchOutcome.LOST,
                        title = "ELENDİN!",
                        subtitle = msg ?: "Maçı kaybettin.",
                        returnCountdownSec = 8
                    )
                    newlyEndedOutcome = MatchOutcome.LOST
                }
            } else {
                nx = spawnPos.first
                feetY = spawnPos.second
                nz = spawnPos.third
                velY = 0f
                hp = 20
                hunger = 20
                hurtFlash = 0.75f
                hurtShake = 0.40f
                msg = "Yeniden doğdun!"
            }
        }

        // Check Victory in Bedwars / Skywars -> trigger MatchResult & Return-to-Lobby flow!
        var winner = s.winnerName
        if (!s.isWaitingPhase && winner == null && currentMatchResult == null &&
            (s.serverType == ServerType.BEDWARS || s.serverType == ServerType.SKYWARS)
        ) {
            val aliveOpponents = updatedEntities.count {
                (it.kind == EntityKind.BOT || it.kind == EntityKind.PLAYER) && it.alive
            }
            val anyOpponentsStarted = updatedEntities.any { it.kind == EntityKind.BOT } || s.remotePlayers.size >= 2
            if (anyOpponentsStarted && aliveOpponents == 0 && hp > 0 && s.gameMode != GameMode.SPECTATOR) {
                winner = "SEN KAZANDIN! (+150 Coin, +80 XP)"
                msg = winner
                onMatchRewards(150, 80)
                currentMatchResult = MatchResultState(
                    outcome = MatchOutcome.WON,
                    title = "ZAFER!",
                    subtitle = "Tüm rakipleri eledin! +150 Coin, +80 XP",
                    returnCountdownSec = 8
                )
                newlyEndedOutcome = MatchOutcome.WON
            }
        }

        if (newlyEndedOutcome != null) {
            onMatchEnded(newlyEndedOutcome == MatchOutcome.WON)
        }
        if (shouldReturnLobby) {
            onAutoReturnToLobby()
        }

        val cam = s.camera.copy(x = nx, y = feetY + 1.55f, z = nz)
        val targetBlock = GamePhysics.raycastBlock(cam, blocks)
        val targetEntity = GamePhysics.findTargetEntity(cam, updatedEntities)
        val nextSwing = if (s.swingProgress > 0f) (s.swingProgress - clampedDt * 4.2f).coerceAtLeast(0f) else 0f

        return s.copy(
            camera = cam,
            velY = velY,
            onGround = onGround,
            isFlying = isFlying,
            hp = hp,
            hunger = hunger,
            hurtFlashAlpha = hurtFlash,
            hurtShakeTimer = hurtShake,
            beds = updatedBeds,
            entities = updatedEntities,
            targetedCoord = targetBlock?.hitCoord,
            targetedEntity = targetEntity,
            swingProgress = nextSwing,
            winnerName = winner,
            matchResult = currentMatchResult,
            eventMessage = msg,
            frameVersion = s.frameVersion + 1L
        )
    }

    private fun hasFloorUnder(x: Float, feetY: Float, z: Float): Boolean =
        GamePhysics.hasFloorUnder(x, feetY, z, blocks)

    fun jump(state: WorldSessionState): WorldSessionState {
        if (!state.active) return state
        return if (state.onGround && !state.isFlying) {
            state.copy(velY = 8.4f, onGround = false)
        } else {
            state
        }
    }

    fun toggleFlying(state: WorldSessionState): WorldSessionState {
        if (!state.active) return state
        if (state.gameMode == GameMode.SPECTATOR) {
            return state.copy(isFlying = true)
        }
        if (state.gameMode != GameMode.CREATIVE) return state
        val nextFly = !state.isFlying
        return state.copy(
            isFlying = nextFly,
            velY = 0f,
            onGround = if (nextFly) false else state.onGround,
            eventMessage = if (nextFly) "Uçuş Açıldı (Yukarı/Aşağı ok ile yüksel/alçal)" else "Uçuş Kapatıldı",
            frameVersion = state.frameVersion + 1L
        )
    }

    fun applyPlayerDamage(
        state: WorldSessionState,
        damage: Int,
        sourceName: String = "",
        knockX: Float = 0f,
        knockZ: Float = 0f
    ): WorldSessionState {
        if (!state.active || state.gameMode == GameMode.CREATIVE || state.gameMode == GameMode.SPECTATOR) return state
        val dmg = damage.coerceAtLeast(1)
        val newHp = (state.hp - dmg).coerceAtLeast(0)
        val feetY = state.camera.y - 1.55f
        var nx = state.camera.x
        var nz = state.camera.z
        if (!GamePhysics.isColliding(nx + knockX, feetY, nz, blocks)) nx += knockX
        if (!GamePhysics.isColliding(nx, feetY, nz + knockZ, blocks)) nz += knockZ
        val msg = if (sourceName.isNotBlank()) "$sourceName sana vurdu! (-$dmg ♥)" else state.eventMessage
        return state.copy(
            camera = state.camera.copy(x = nx, z = nz),
            hp = newHp,
            velY = state.velY.coerceAtLeast(3.2f),
            hurtFlashAlpha = 0.75f,
            hurtShakeTimer = 0.40f,
            eventMessage = msg,
            frameVersion = state.frameVersion + 1L
        )
    }

    fun lookDelta(state: WorldSessionState, dx: Float, dy: Float): WorldSessionState {
        if (!state.active) return state
        val newYaw = (state.camera.yawDeg + dx * 0.28f) % 360f
        val newPitch = (state.camera.pitchDeg - dy * 0.24f).coerceIn(-75f, 75f)
        return state.copy(
            camera = state.camera.copy(yawDeg = newYaw, pitchDeg = newPitch),
            frameVersion = state.frameVersion + 1L
        )
    }

    fun canBreakBlockAt(
        state: WorldSessionState,
        coord: VoxelCoord,
        blockType: BlockType,
        canBreakProtectedBlocks: Boolean
    ): Pair<Boolean, String?> {
        if (state.gameMode == GameMode.ADVENTURE) {
            return false to "Macera modunda blok kırılamaz!"
        }
        if (state.isWaitingPhase || state.isVotingPhase) {
            return if (canBreakProtectedBlocks) true to null
            else false to "Yetkisiz hesaplar bekleme alanı bloklarını kıramaz!"
        }
        if (state.serverType == ServerType.LOBI && !canBreakProtectedBlocks) {
            return false to "Yetkin yok! Lobi bloklarını sadece yetkililer ve mimarlar kırabilir."
        }
        if (coord.y <= 0 && !isOfflineSession && state.serverType != ServerType.SKYBLOCK && !canBreakProtectedBlocks) {
            return false to "Taban bloğu kırılamaz."
        }
        if (coord in protectedMapBlocks && coord !in playerPlacedBlocks && blockType != BlockType.BED && !canBreakProtectedBlocks) {
            return false to "Yetkin yok! Bu harita bloğu korumalıdır."
        }
        return true to null
    }

    fun handlePrimaryAction(
        state: WorldSessionState,
        heldItem: GameItem?,
        canBreakProtectedBlocks: Boolean = false,
        onBlockBroken: (VoxelCoord, BlockType) -> Unit,
        onEntityReward: (coinsDelta: Int, xpDelta: Int) -> Unit,
        onPortalTrigger: (ServerType) -> Unit,
        onPlayerHit: (WorldEntity, Int) -> Unit = { _, _ -> }
    ): WorldSessionState {
        if (!state.active || state.gameMode == GameMode.SPECTATOR) return state
        val swung = state.copy(swingProgress = 1f)

        // 1. Check Entity Target first (Combat or NPC interaction)
        val targetEntity = swung.targetedEntity
        if (targetEntity != null) {
            when (targetEntity.kind) {
                EntityKind.NPC_PORTAL -> {
                    targetEntity.targetServer?.let(onPortalTrigger)
                    return swung
                }
                EntityKind.NPC_MARKET -> {
                    return swung.copy(activeModal = WorldModalType.MARKET_NPC)
                }
                EntityKind.NPC_BANK -> {
                    return swung.copy(activeModal = WorldModalType.BANK_NPC)
                }
                EntityKind.PLAYER -> {
                    val dmg = heldItem?.attackDamage ?: 2
                    val updated = swung.entities.map {
                        if (it.id == targetEntity.id) it.copy(hurtFlashTimer = 0.35f) else it
                    }
                    onPlayerHit(targetEntity, dmg)
                    return swung.copy(
                        entities = updated,
                        eventMessage = "${targetEntity.name} oyuncusuna vurdun! (-$dmg ♥)",
                        frameVersion = swung.frameVersion + 1L
                    )
                }
                EntityKind.BOT, EntityKind.MOB -> {
                    val dmg = heldItem?.attackDamage ?: 2
                    val newHp = (targetEntity.hp - dmg).coerceAtLeast(0)
                    val killed = newHp == 0
                    val updated = swung.entities.map {
                        if (it.id == targetEntity.id) {
                            it.copy(hp = newHp, alive = !killed, hurtFlashTimer = 0.35f)
                        } else it
                    }
                    if (killed) {
                        onEntityReward(40, 25)
                    }
                    return swung.copy(
                        entities = updated,
                        eventMessage = if (killed) "${targetEntity.name} yok edildi! (+40 Coin)" else "${targetEntity.name}: -$dmg HP",
                        frameVersion = swung.frameVersion + 1L
                    )
                }
                else -> {}
            }
        }

        // 2. Break Targeted Block (with Non-Staff Protection)
        val hit = GamePhysics.raycastBlock(swung.camera, blocks) ?: return swung
        val (allowed, denyReason) = canBreakBlockAt(swung, hit.hitCoord, hit.blockType, canBreakProtectedBlocks)
        if (!allowed) {
            return swung.copy(eventMessage = denyReason ?: "Bu blok kırılamaz.")
        }

        var updatedBeds = swung.beds
        var msg = "${hit.blockType.displayName} kırıldı"
        if (hit.blockType == BlockType.BED) {
            val brokenBed = swung.beds.firstOrNull { it.coord == hit.hitCoord }
            if (brokenBed != null) {
                if (brokenBed.team == "MAVI") {
                    return swung.copy(eventMessage = "Kendi takım yatağını kıramazsın!")
                }
                updatedBeds = swung.beds.map {
                    if (it.coord == hit.hitCoord) it.copy(intact = false) else it
                }
                msg = "${brokenBed.label} Takımın Yatağı Kırıldı! (+100 Coin)"
                onEntityReward(100, 50)
            }
        }

        blocks.remove(hit.hitCoord)
        playerPlacedBlocks.remove(hit.hitCoord)
        onBlockBroken(hit.hitCoord, hit.blockType)
        return swung.copy(
            beds = updatedBeds,
            eventMessage = msg,
            frameVersion = swung.frameVersion + 1L
        )
    }

    fun handleSecondaryAction(
        state: WorldSessionState,
        heldItem: GameItem?,
        canDesignBuild: Boolean,
        hasFullDesignTools: Boolean,
        onBlockPlaced: (VoxelCoord, BlockType) -> Unit,
        onConsumeInventoryItem: (String) -> Unit,
        onChestLooted: (items: List<InventorySlot>, coinsDelta: Int) -> Unit,
        onPortalTrigger: (ServerType) -> Unit
    ): WorldSessionState {
        if (!state.active || state.gameMode == GameMode.SPECTATOR) return state
        val swung = state.copy(swingProgress = 1f)

        // Interact with NPC or Player/Bot if looking at one
        val targetEntity = swung.targetedEntity
        if (targetEntity != null) {
            when (targetEntity.kind) {
                EntityKind.NPC_PORTAL -> {
                    targetEntity.targetServer?.let(onPortalTrigger)
                    return swung
                }
                EntityKind.NPC_MARKET -> return swung.copy(activeModal = WorldModalType.MARKET_NPC)
                EntityKind.NPC_BANK -> return swung.copy(activeModal = WorldModalType.BANK_NPC)
                EntityKind.PLAYER, EntityKind.BOT -> return swung.copy(
                    interactTargetEntity = targetEntity,
                    activeModal = WorldModalType.PLAYER_INTERACT
                )
                else -> {}
            }
        }

        // Eat Food item if held
        if (heldItem != null && heldItem.foodRestore > 0) {
            if (swung.hunger >= 20 && swung.hp >= swung.maxHp) {
                return swung.copy(eventMessage = "Karnın ve canın zaten dolu!")
            }
            val nextHunger = (swung.hunger + heldItem.foodRestore).coerceAtMost(20)
            val healBonus = (heldItem.foodRestore / 2).coerceAtLeast(1)
            val nextHp = (swung.hp + healBonus).coerceAtMost(swung.maxHp)
            if (swung.gameMode != GameMode.CREATIVE) {
                onConsumeInventoryItem(heldItem.id)
            }
            return swung.copy(
                hunger = nextHunger,
                hp = nextHp,
                eventMessage = "${heldItem.name} yendi! (+${heldItem.foodRestore} Açlık, +$healBonus ♥)",
                frameVersion = swung.frameVersion + 1L
            )
        }

        // Open Chest in Skywars / Skyblock
        val hit = GamePhysics.raycastBlock(swung.camera, blocks) ?: return swung
        if (hit.blockType == BlockType.CHEST) {
            if (lootedChests.add(hit.hitCoord)) {
                val loot = listOf(
                    InventorySlot("iron_sword", 1),
                    InventorySlot("stone", 16),
                    InventorySlot("bread", 4),
                    InventorySlot("tnt", 2)
                )
                onChestLooted(loot, 65)
                return swung.copy(eventMessage = "Sandık Yağmalandı! Demir Kılıç + 16 Taş + 4 Ekmek + 2 TNT")
            }
            return swung.copy(eventMessage = "Bu sandık zaten yağmalandı.")
        }

        // Block placement permission check
        if (swung.isWaitingPhase && !canDesignBuild) {
            return swung.copy(eventMessage = "Bekleme alanında sadece yetkililer blok koyabilir.")
        }
        val allowedInLobi = swung.serverType == ServerType.LOBI && (canDesignBuild || (isOfflineSession && swung.gameMode == GameMode.CREATIVE))
        if (swung.serverType == ServerType.LOBI && !allowedInLobi) {
            return swung.copy(eventMessage = "Lobi'de sadece Mimar/Yetkili blok koyabilir.")
        }
        if (swung.gameMode == GameMode.ADVENTURE) {
            return swung.copy(eventMessage = "Macera modunda blok konulamaz.")
        }

        val blockToPlace = heldItem?.blockType ?: return swung.copy(eventMessage = "Yerleştirmek için envanterden blok seç.")
        val targetPos = hit.placeCoord
        // Avoid placing block inside player's body
        val playerCellX = floor(swung.camera.x).toInt()
        val playerCellY = floor(swung.camera.y - 1.5f).toInt()
        val playerCellZ = floor(swung.camera.z).toInt()
        if (targetPos.x == playerCellX && targetPos.z == playerCellZ && (targetPos.y == playerCellY || targetPos.y == playerCellY + 1)) {
            return swung
        }

        blocks[targetPos] = blockToPlace
        playerPlacedBlocks.add(targetPos)
        onBlockPlaced(targetPos, blockToPlace)

        // Mimar has 3x1 wall brush in Creative/Design; Akademi has single block
        if (swung.gameMode == GameMode.CREATIVE && hasFullDesignTools && heldItem.id == "stone") {
            val extra = VoxelCoord(targetPos.x + 1, targetPos.y, targetPos.z)
            if (!blocks.containsKey(extra)) {
                blocks[extra] = blockToPlace
                playerPlacedBlocks.add(extra)
                onBlockPlaced(extra, blockToPlace)
            }
        }

        if (swung.gameMode != GameMode.CREATIVE) {
            onConsumeInventoryItem(heldItem.id)
        }

        return swung.copy(
            eventMessage = "${blockToPlace.displayName} yerleştirildi",
            frameVersion = swung.frameVersion + 1L
        )
    }

    fun summonMob(state: WorldSessionState, mobType: MobType): WorldSessionState {
        val yawRad = Math.toRadians(state.camera.yawDeg.toDouble()).toFloat()
        val sx = state.camera.x + sin(yawRad) * 2.8f
        val sz = state.camera.z + cos(yawRad) * 2.8f
        val sy = floor(state.camera.y - 1.55f)
        val mob = WorldEntity(
            id = "mob_${System.currentTimeMillis()}_${Random.nextInt(1000)}",
            name = mobType.displayName,
            kind = EntityKind.MOB,
            x = sx,
            y = sy,
            z = sz,
            hp = mobType.maxHp,
            maxHp = mobType.maxHp,
            mobType = mobType,
            bodyColor = mobType.bodyColor,
            headColor = mobType.headColor,
            alive = true
        )
        return state.copy(
            entities = state.entities + mob,
            eventMessage = "${mobType.displayName} çağrıldı (Summon)!",
            frameVersion = state.frameVersion + 1L
        )
    }

    fun killAllMobs(state: WorldSessionState, mobType: MobType?): WorldSessionState {
        val updated = state.entities.filterNot {
            it.kind == EntityKind.MOB && (mobType == null || it.mobType == mobType)
        }
        val label = mobType?.displayName ?: "Tüm yaratıklar"
        return state.copy(
            entities = updated,
            eventMessage = "$label temizlendi (Kill)!",
            frameVersion = state.frameVersion + 1L
        )
    }
}
