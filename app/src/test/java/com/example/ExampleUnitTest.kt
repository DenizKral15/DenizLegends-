package com.example

import com.example.engine.CameraState
import com.example.engine.GamePhysics
import com.example.engine.GameSessionController
import com.example.engine.MatchOutcome
import com.example.engine.MatchResultState
import com.example.engine.VoxelMapGenerator
import com.example.model.*
import com.example.viewmodel.AppEntryMode
import com.example.viewmodel.MainUiState
import com.example.viewmodel.isBanActiveAt
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `rank rbac permissions match specification and Kurucu self protection`() {
        assertTrue(Rank.KURUCU.isProtectedFromPunishment)
        assertTrue(Rank.KURUCU.canAssignRank(Rank.KURUCU, Rank.OYUNCU))
        assertTrue(Rank.KURUCU.canAssignRank(Rank.YONETICI, Rank.OYUNCU))

        // Kurucu cannot demote or punish their own Kurucu account
        assertFalse(Rank.KURUCU.canAssignRank(Rank.OYUNCU, Rank.KURUCU, isSelf = true))
        assertFalse(Rank.KURUCU.canAssignRank(Rank.YONETICI, Rank.KURUCU, isSelf = true))
        assertTrue(Rank.KURUCU.canAssignRank(Rank.KURUCU, Rank.KURUCU, isSelf = true))
        assertFalse(Rank.KURUCU.canPunish(Rank.KURUCU, isSelf = true))
        assertTrue(Rank.KURUCU.canPunish(Rank.OYUNCU, isSelf = false))

        // Admin cannot assign or modify Kurucu or Admin
        assertTrue(Rank.YONETICI.canAccessManagementTab)
        assertFalse(Rank.YONETICI.canAssignRank(Rank.KURUCU, Rank.OYUNCU))
        assertFalse(Rank.YONETICI.canAssignRank(Rank.YONETICI, Rank.OYUNCU))
        assertFalse(Rank.YONETICI.canAssignRank(Rank.VIP, Rank.YONETICI))
        assertTrue(Rank.YONETICI.canAssignRank(Rank.MVIP_P, Rank.OYUNCU))

        // Moderator: support & moderation only, no rank assignment
        assertFalse(Rank.MODERATOR.canAccessManagementTab)
        assertTrue(Rank.MODERATOR.canHandleTickets)
        assertTrue(Rank.MODERATOR.canHandleReports)
        assertTrue(Rank.MODERATOR.canBan)
        assertTrue(Rank.MODERATOR.canSpectateMatch)
        assertFalse(Rank.MODERATOR.canAssignRank(Rank.VIP, Rank.OYUNCU))

        // Yardimci: mute & support only
        assertFalse(Rank.YARDIMCI.canAccessManagementTab)
        assertTrue(Rank.YARDIMCI.canHandleTickets)
        assertTrue(Rank.YARDIMCI.canMute)
        assertFalse(Rank.YARDIMCI.canBan)
        assertFalse(Rank.YARDIMCI.canWarn)
        assertFalse(Rank.YARDIMCI.canHandleReports)

        // Mimar & Akademi: no staff panel, Mimar > Akademi design tools
        assertFalse(Rank.MIMAR.canAccessStaffPanel)
        assertTrue(Rank.MIMAR.canBuildAndDesign)
        assertTrue(Rank.MIMAR.hasFullDesignTools)
        assertFalse(Rank.AKADEMI.canAccessStaffPanel)
        assertTrue(Rank.AKADEMI.canBuildAndDesign)
        assertFalse(Rank.AKADEMI.hasFullDesignTools)
    }

    @Test
    fun `level system covers Komur 1 to Pro 5 across 40 levels`() {
        assertEquals("Kömür 1", LevelSystem.getTierInfo(1).fullName)
        assertEquals("Kömür 5", LevelSystem.getTierInfo(5).fullName)
        assertEquals("Taş 1", LevelSystem.getTierInfo(6).fullName)
        assertEquals("Demir 1", LevelSystem.getTierInfo(11).fullName)
        assertEquals("Altın 1", LevelSystem.getTierInfo(16).fullName)
        assertEquals("Elmas 1", LevelSystem.getTierInfo(21).fullName)
        assertEquals("Netherite 1", LevelSystem.getTierInfo(26).fullName)
        assertEquals("Usta 1", LevelSystem.getTierInfo(31).fullName)
        assertEquals("Pro 5", LevelSystem.getTierInfo(40).fullName)
    }

    @Test
    fun `each server generates distinct maps and bedwars beds`() {
        ServerType.entries.forEach { srv ->
            val waiting = VoxelMapGenerator.generateWaitingLobby(srv, srv.waitingLobbyMap)
            assertTrue(waiting.blocks.isNotEmpty())
            val match = VoxelMapGenerator.generateMatchWorld(srv, srv.matchMaps.first())
            assertTrue(match.blocks.isNotEmpty())
            if (srv == ServerType.BEDWARS) {
                assertEquals(4, match.beds.size)
            }
        }
    }

    @Test
    fun `raycast hits target block in front of camera`() {
        val blocks = mapOf(VoxelCoord(0, 2, 3) to BlockType.STONE)
        val cam = CameraState(x = 0.5f, y = 2.5f, z = 0.5f, yawDeg = 0f, pitchDeg = 0f)
        val hit = GamePhysics.raycastBlock(cam, blocks)
        assertNotNull(hit)
        assertEquals(VoxelCoord(0, 2, 3), hit?.hitCoord)
        assertEquals(VoxelCoord(0, 2, 2), hit?.placeCoord)
    }

    @Test
    fun `aabb collision checks feet torso and head slices`() {
        val blocks = mapOf(VoxelCoord(2, 3, 2) to BlockType.STONE)
        // Player feet at y=2.0 means head slice (feetY + 1.72 = 3.72) hits block y=3
        assertTrue(GamePhysics.isColliding(2.5f, 2.0f, 2.5f, blocks))
        assertFalse(GamePhysics.isColliding(4.5f, 2.0f, 4.5f, blocks))
    }

    @Test
    fun `creative flight toggle and damage hurt flash work properly`() {
        val controller = GameSessionController()
        val creativeState = controller.startSession(
            roomId = "r1",
            networkId = "official",
            serverType = ServerType.CREATIVE,
            mapName = "Arsa",
            gameMode = GameMode.CREATIVE,
            savedPlotData = "",
            isOffline = true
        )
        assertFalse(creativeState.isFlying)
        val flyingState = controller.toggleFlying(creativeState)
        assertTrue(flyingState.isFlying)
        val landedState = controller.toggleFlying(flyingState)
        assertFalse(landedState.isFlying)

        val survivalState = controller.startSession(
            roomId = "r2",
            networkId = "official",
            serverType = ServerType.SURVIVAL,
            mapName = "Orman",
            gameMode = GameMode.SURVIVAL,
            savedPlotData = "",
            isOffline = true
        )
        val hurt = controller.applyPlayerDamage(survivalState, 4, "Zombi")
        assertEquals(16, hurt.hp)
        assertTrue(hurt.hurtFlashAlpha > 0.5f)
        assertTrue(hurt.hurtShakeTimer > 0.2f)
    }

    @Test
    fun `hotbar swap put and eating food restore hunger`() {
        val slots = listOf(InventorySlot("wood_sword", 1), InventorySlot("bread", 4))
        val swapped = swapInventoryIndices(slots, 0, 1)
        assertEquals("bread", swapped[0].itemId)
        assertEquals("wood_sword", swapped[1].itemId)

        val put = putItemAtHotbarSlot(swapped, 1, "diamond_block", 64)
        assertEquals("diamond_block", put[1].itemId)
        assertEquals(64, put[1].count)

        val controller = GameSessionController()
        val survival = controller.startSession(
            roomId = "r3",
            networkId = "official",
            serverType = ServerType.SURVIVAL,
            mapName = "Orman",
            gameMode = GameMode.SURVIVAL,
            savedPlotData = "",
            isOffline = true
        ).copy(hunger = 10, hp = 14)
        var consumed = ""
        val afterEat = controller.handleSecondaryAction(
            state = survival,
            heldItem = GameCatalog.findById("bread"),
            canDesignBuild = false,
            hasFullDesignTools = false,
            onBlockPlaced = { _, _ -> },
            onConsumeInventoryItem = { consumed = it },
            onChestLooted = { _, _ -> },
            onPortalTrigger = {}
        )
        assertEquals(16, afterEat.hunger)
        assertEquals(17, afterEat.hp)
        assertEquals("bread", consumed)
    }

    @Test
    fun `ban disconnection logic handles permanent timed expiry and offline isolation`() {
        val now = 1_700_000_000_000L
        val permanentBan = NetworkMember(userId = "u1", banned = true, banReason = "Hile", banUntilMillis = 0L)
        val activeTimedBan = NetworkMember(userId = "u2", banned = true, banReason = "Hakaret", banUntilMillis = now + 60_000L)
        val expiredBan = NetworkMember(userId = "u3", banned = true, banReason = "Eski", banUntilMillis = now - 1_000L)
        val cleanMember = NetworkMember(userId = "u4", banned = false, banUntilMillis = 0L)

        assertTrue(permanentBan.isBanActiveAt(now))
        assertTrue(activeTimedBan.isBanActiveAt(now))
        assertFalse(expiredBan.isBanActiveAt(now))
        assertFalse(cleanMember.isBanActiveAt(now))

        val onlineBannedState = MainUiState(
            entryMode = AppEntryMode.ONLINE,
            currentMember = permanentBan,
            banClockMillis = now
        )
        assertTrue(onlineBannedState.isMemberBannedActive)

        val offlineSwitchState = onlineBannedState.copy(entryMode = AppEntryMode.OFFLINE)
        assertFalse(offlineSwitchState.isMemberBannedActive)

        val futureTimedBan = NetworkMember(
            userId = "u5",
            banned = true,
            banReason = "Spam",
            banUntilMillis = System.currentTimeMillis() + 600_000L
        )
        assertTrue(MainUiState(entryMode = AppEntryMode.ONLINE, currentMember = futureTimedBan).isMemberBannedActive)
    }

    @Test
    fun `non staff players cannot break protected lobby waiting or map blocks`() {
        val controller = GameSessionController()
        val lobbyState = controller.startSession(
            roomId = "lobi1",
            networkId = "official",
            serverType = ServerType.LOBI,
            mapName = "Deniz Limanı",
            gameMode = GameMode.SURVIVAL,
            savedPlotData = "",
            isOffline = false
        )
        val someCoord = controller.blocks.keys.first()
        val someType = controller.blocks[someCoord]!!

        // Non-staff blocked in LOBI
        val (nonStaffAllowed, reason) = controller.canBreakBlockAt(lobbyState, someCoord, someType, canBreakProtectedBlocks = false)
        assertFalse(nonStaffAllowed)
        assertNotNull(reason)

        // Staff allowed in LOBI
        val (staffAllowed, _) = controller.canBreakBlockAt(lobbyState, someCoord, someType, canBreakProtectedBlocks = true)
        assertTrue(staffAllowed)

        // Bedwars match: non-staff cannot break protected island stone, CAN break enemy bed
        val waitingBw = controller.startSession(
            roomId = "bw1",
            networkId = "official",
            serverType = ServerType.BEDWARS,
            mapName = "Mercan Adaları",
            gameMode = GameMode.SURVIVAL,
            savedPlotData = "",
            isOffline = true
        )
        val waitingCoord = controller.blocks.keys.first()
        assertFalse(controller.canBreakBlockAt(waitingBw, waitingCoord, BlockType.WOOD, canBreakProtectedBlocks = false).first)

        val arenaBw = controller.transitionToMatchArena(waitingBw)
        val redBed = arenaBw.beds.first { it.team == "KIRMIZI" }
        assertTrue(controller.canBreakBlockAt(arenaBw, redBed.coord, BlockType.BED, canBreakProtectedBlocks = false).first)

        val protectedFloor = controller.blocks.entries.first { it.value == BlockType.STONE || it.value == BlockType.GRASS }.key
        assertFalse(controller.canBreakBlockAt(arenaBw, protectedFloor, BlockType.STONE, canBreakProtectedBlocks = false).first)
    }

    @Test
    fun `online matchmaking policy handles 1 player wait 30s countdown and 3s full room`() {
        val now = 1_700_000_000_000L

        // 1 player -> paused waiting
        val p1 = MatchmakingPolicy.evaluate(playerCount = 1, maxPlayers = 8, currentStartAtMillis = 0L, nowMillis = now)
        assertEquals(MatchmakingPhase.WAITING_FOR_PLAYERS, p1.phase)
        assertEquals(0L, p1.targetStartAtMillis)
        assertEquals(-1, p1.remainingSec)

        // 2 players -> 30s countdown
        val p2 = MatchmakingPolicy.evaluate(playerCount = 2, maxPlayers = 8, currentStartAtMillis = 0L, nowMillis = now)
        assertEquals(MatchmakingPhase.COUNTDOWN_30S, p2.phase)
        assertEquals(now + 30_000L, p2.targetStartAtMillis)
        assertEquals(30, p2.remainingSec)

        // Max players -> compresses countdown to 3s
        val pMax = MatchmakingPolicy.evaluate(playerCount = 8, maxPlayers = 8, currentStartAtMillis = now + 25_000L, nowMillis = now)
        assertEquals(MatchmakingPhase.FULL_ROOM_3S, pMax.phase)
        assertEquals(now + 3_000L, pMax.targetStartAtMillis)
        assertEquals(3, pMax.remainingSec)
    }

    @Test
    fun `maintenance mode blocks non staff and allows staff and delete requires kurucu`() {
        assertTrue(Rank.KURUCU.canManageMaintenance)
        assertTrue(Rank.YONETICI.canManageMaintenance)
        assertFalse(Rank.MODERATOR.canManageMaintenance)
        assertTrue(Rank.KURUCU.canDeleteNetwork)
        assertFalse(Rank.YONETICI.canDeleteNetwork)

        val maintNet = GameNetwork(id = "n1", name = "TestNet", maintenanceMode = true, official = false)
        val playerMember = NetworkMember(userId = "p1", rank = Rank.OYUNCU.code)
        val modMember = NetworkMember(userId = "m1", rank = Rank.MODERATOR.code)

        val playerState = MainUiState(
            entryMode = AppEntryMode.ONLINE,
            activeNetworkId = "n1",
            networks = listOf(maintNet),
            currentMember = playerMember
        )
        assertTrue(playerState.isNetworkMaintenanceBlocked)

        val modState = playerState.copy(currentMember = modMember)
        assertFalse(modState.isNetworkMaintenanceBlocked)

        val offlineMaintState = playerState.copy(entryMode = AppEntryMode.OFFLINE)
        assertFalse(offlineMaintState.isNetworkMaintenanceBlocked)
    }

    @Test
    fun `bot ai moves jumps and triggers victory return countdown on elimination`() {
        val controller = GameSessionController()
        val bwArena = controller.transitionToMatchArena(
            controller.startSession(
                roomId = "bw_ai",
                networkId = "official",
                serverType = ServerType.BEDWARS,
                mapName = "Mercan Adaları",
                gameMode = GameMode.SURVIVAL,
                savedPlotData = "",
                isOffline = true
            )
        )
        val initialBot = bwArena.entities.first { it.kind == EntityKind.BOT }
        var current = bwArena
        repeat(15) {
            current = controller.stepFrame(
                current = current,
                dt = 0.05f,
                moveForward = 0f,
                moveStrafe = 0f,
                flyUp = false,
                flyDown = false,
                savedPlotData = "",
                onMatchRewards = { _, _ -> }
            )
        }
        val movedBot = current.entities.first { it.id == initialBot.id }
        assertTrue(movedBot.x != initialBot.x || movedBot.z != initialBot.z)

        // Eliminate all bots -> verify victory MatchResultState & auto-return callback
        val botsEliminated = current.copy(
            entities = current.entities.map { if (it.kind == EntityKind.BOT) it.copy(alive = false, hp = 0) else it }
        )
        var endedWon: Boolean? = null
        val wonState = controller.stepFrame(
            current = botsEliminated,
            dt = 0.05f,
            moveForward = 0f,
            moveStrafe = 0f,
            flyUp = false,
            flyDown = false,
            savedPlotData = "",
            onMatchRewards = { _, _ -> },
            onMatchEnded = { won -> endedWon = won }
        )
        assertEquals(MatchOutcome.WON, wonState.matchResult?.outcome)
        assertEquals(true, endedWon)

        var returnedToLobby = false
        var tickState = wonState.copy(matchResult = wonState.matchResult?.copy(returnCountdownSec = 1))
        for (i in 0..25) {
            tickState = controller.stepFrame(
                current = tickState,
                dt = 0.05f,
                moveForward = 0f,
                moveStrafe = 0f,
                flyUp = false,
                flyDown = false,
                savedPlotData = "",
                onMatchRewards = { _, _ -> },
                onAutoReturnToLobby = { returnedToLobby = true }
            )
            if (returnedToLobby) break
        }
        assertTrue(returnedToLobby)
    }

    @Test
    fun `daily bonus policy enforces 24 hour cooldown across online and offline states`() {
        val now = System.currentTimeMillis()

        // First-time user (0L) can claim
        assertTrue(DailyBonusPolicy.canClaimAt(0L, now))
        assertEquals(0L, DailyBonusPolicy.remainingMillisAt(0L, now))
        assertEquals("BONUS", DailyBonusPolicy.formatCompactBadge(0L))

        // Immediate repeat or within 24h is blocked
        val claimedOneMinAgo = now - 60_000L
        assertFalse(DailyBonusPolicy.canClaimAt(claimedOneMinAgo, now))
        assertEquals(DailyBonusPolicy.COOLDOWN_MS - 60_000L, DailyBonusPolicy.remainingMillisAt(claimedOneMinAgo, now))

        val claimedJustUnder24h = now - (DailyBonusPolicy.COOLDOWN_MS - 1_000L)
        assertFalse(DailyBonusPolicy.canClaimAt(claimedJustUnder24h, now))
        assertEquals(1_000L, DailyBonusPolicy.remainingMillisAt(claimedJustUnder24h, now))

        // Exactly 24h or more unlocks claim again
        val claimed24hAgo = now - DailyBonusPolicy.COOLDOWN_MS
        assertTrue(DailyBonusPolicy.canClaimAt(claimed24hAgo, now))
        assertEquals(0L, DailyBonusPolicy.remainingMillisAt(claimed24hAgo, now))

        // Offline MainUiState reflects offlineProfile.lastDailyBonusClaimMillis
        val offlineReady = MainUiState(
            entryMode = AppEntryMode.OFFLINE,
            offlineProfile = OfflineProfileState(lastDailyBonusClaimMillis = 0L)
        )
        assertTrue(offlineReady.canClaimDailyBonusNow)

        val offlineCooldown = MainUiState(
            entryMode = AppEntryMode.OFFLINE,
            offlineProfile = OfflineProfileState(lastDailyBonusClaimMillis = now - 5_000L)
        )
        assertFalse(offlineCooldown.canClaimDailyBonusNow)
        assertTrue(offlineCooldown.dailyBonusRemainingMillisAt(now) > 0L)

        // Online MainUiState reflects NetworkMember.lastDailyBonusAt and busy/ban locks
        val onlineCooldown = MainUiState(
            entryMode = AppEntryMode.ONLINE,
            currentMember = NetworkMember(
                userId = "u1",
                lastDailyBonusAt = com.google.firebase.Timestamp(java.util.Date(now - 3_600_000L))
            )
        )
        assertFalse(onlineCooldown.canClaimDailyBonusNow)
        assertTrue(onlineCooldown.dailyBonusRemainingMillisAt(now) > 22L * 3_600_000L)

        val onlineBusy = MainUiState(
            entryMode = AppEntryMode.ONLINE,
            currentMember = NetworkMember(userId = "u1", lastDailyBonusAt = null),
            isDailyBonusClaimBusy = true
        )
        assertFalse(onlineBusy.canClaimDailyBonusNow)
    }
}

