package com.example.data

import android.content.Context
import com.example.R
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DailyBonusCooldownException(val remainingMillis: Long) : Exception("Günlük bonus bekleme süresinde")

class DenizCraftRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun isOfficialAdminEmail(): Boolean {
        val user = auth.currentUser ?: return false
        return user.email == "hediyeergezer4@gmail.com" && user.isEmailVerified
    }

    private fun <T> Flow<T>.logAndRethrowFirestore(path: String, op: OperationType, uid: String): Flow<T> =
        catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val toLog = firestoreError ?: (error as? Exception)
            if (toLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(toLog, op, path)
            }
            throw error
        }

    // --- USER ACCOUNT ---
    suspend fun ensureUserAccount(): Result<UserAccount> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val docRef = db.collection("users").document(uid)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existing != null) return Result.success(existing)
            }
            val rawName = auth.currentUser?.displayName?.trim().orEmpty()
                .replace(Regex("[^a-zA-Z0-9_çğıöşüÇĞİÖŞÜ ]"), "")
                .take(18)
                .ifBlank { "Deniz_${uid.take(5)}" }
            val newAccount = UserAccount(
                userId = uid,
                username = rawName,
                email = auth.currentUser?.email.orEmpty().take(110),
                skinColor = 0L,
                hatId = "builder_hat",
                globalCoins = 1000L,
                globalGems = 20L,
                globalXp = 0L,
                globalLevel = 1L,
                preferredNetworkId = "official"
            )
            docRef.set(newAccount.toCreateMap()).await()
            Result.success(newAccount.copy(id = uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateUserAccountCosmetics(username: String, skinColor: Long, preferredNetworkId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val cleanName = username.trim().take(24)
        if (cleanName.length < 2) return Result.failure(IllegalArgumentException("Kullanıcı adı en az 2 karakter olmalı"))
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "username" to cleanName,
                    "skinColor" to skinColor.coerceIn(0L, 32L),
                    "preferredNetworkId" to preferredNetworkId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- NETWORKS (OFFICIAL + INDEPENDENT) ---
    suspend fun ensureOfficialNetworkIfAdmin() {
        if (!isOfficialAdminEmail()) return
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("networks").document("official")
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val net = GameNetwork(
                    name = "DenizCraft Resmi Network",
                    description = "DenizCraft resmi çok oyunculu mini oyun ağı",
                    policyText = "Hile, hakaret ve bug kullanımı yasaktır. Tüm yetkili kararları geçerlidir.",
                    founderUid = uid,
                    founderName = auth.currentUser?.displayName?.take(20) ?: "Kurucu",
                    official = true,
                    active = true,
                    playerCount = 1L
                )
                docRef.set(net.toCreateMap()).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }

    fun observeNetworks(): Flow<List<GameNetwork>> = flow {
        val uid = auth.currentUser?.uid ?: return@flow
        val path = "networks"
        emitAll(
            db.collection("networks")
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(GameNetwork::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logAndRethrowFirestore(path, OperationType.LIST, uid)
        )
    }

    suspend fun createIndependentNetwork(
        name: String,
        description: String,
        policyText: String,
        founderName: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val cleanName = name.trim().take(40)
        if (cleanName.length < 3) return Result.failure(IllegalArgumentException("Network adı en az 3 karakter olmalı"))
        val netId = "net_${uid.take(6)}_${System.currentTimeMillis() % 100000}"
        val docRef = db.collection("networks").document(netId)
        return try {
            val net = GameNetwork(
                name = cleanName,
                description = description.trim().take(200),
                policyText = policyText.trim().take(500).ifBlank { "Sunucu kurallarına uyunuz." },
                founderUid = uid,
                founderName = founderName.trim().take(24).ifBlank { "Kurucu" },
                official = false,
                active = true,
                playerCount = 1L
            )
            docRef.set(net.toCreateMap()).await()
            Result.success(netId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun setNetworkMaintenanceMode(networkId: String, enabled: Boolean): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val docRef = db.collection("networks").document(networkId)
        return try {
            docRef.update(
                mapOf(
                    "maintenanceMode" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteIndependentNetwork(networkId: String): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        if (networkId == "official") {
            return Result.failure(IllegalArgumentException("Resmi ağ silinemez."))
        }
        val docRef = db.collection("networks").document(networkId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- PER-NETWORK MEMBER PROFILE ---
    suspend fun ensureNetworkMember(
        networkId: String,
        username: String,
        skinColor: Long,
        isFounderOfNetwork: Boolean
    ): Result<NetworkMember> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val docRef = db.collection("networks").document(networkId).collection("members").document(uid)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(NetworkMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (existing != null) return Result.success(existing)
            }
            val initialRank = if (isFounderOfNetwork || (networkId == "official" && isOfficialAdminEmail())) {
                Rank.KURUCU.code
            } else {
                Rank.OYUNCU.code
            }
            val member = NetworkMember(
                userId = uid,
                networkId = networkId,
                username = username.trim().take(24).ifBlank { "Oyuncu_${uid.take(4)}" },
                rank = initialRank,
                level = 1L,
                xp = 0L,
                coins = 500L,
                gems = 15L,
                gameMode = GameMode.SURVIVAL.code,
                inventoryJson = "wood_sword:1,wool_cyan:32,wood:16,pickaxe:1,bread:4",
                skinColor = skinColor.coerceIn(0L, 32L)
            )
            docRef.set(member.toCreateMap()).await()
            Result.success(member.copy(id = uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeMember(networkId: String, userId: String): Flow<NetworkMember?> = flow {
        val uid = auth.currentUser?.uid ?: return@flow
        val path = "networks/$networkId/members/$userId"
        emitAll(
            db.collection("networks").document(networkId).collection("members").document(userId)
                .snapshots()
                .map { snap -> snap.toObject(NetworkMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logAndRethrowFirestore(path, OperationType.GET, uid)
        )
    }

    fun observeNetworkMembers(networkId: String): Flow<List<NetworkMember>> = flow {
        val uid = auth.currentUser?.uid ?: return@flow
        val path = "networks/$networkId/members"
        emitAll(
            db.collection("networks").document(networkId).collection("members")
                .whereEqualTo("networkId", networkId)
                .snapshots()
                .map { snap -> snap.toObjects(NetworkMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logAndRethrowFirestore(path, OperationType.LIST, uid)
        )
    }

    suspend fun updateSelfProgress(
        networkId: String,
        coins: Long,
        gems: Long,
        xp: Long,
        level: Long,
        inventoryJson: String,
        skinColor: Long
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val docRef = db.collection("networks").document(networkId).collection("members").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "coins" to coins.coerceAtLeast(0L),
                    "gems" to gems.coerceAtLeast(0L),
                    "xp" to xp.coerceAtLeast(0L),
                    "level" to level.coerceIn(1L, 40L),
                    "inventoryJson" to inventoryJson.take(7900),
                    "skinColor" to skinColor.coerceIn(0L, 32L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun claimDailyBonus(
        networkId: String,
        coinsDelta: Long,
        gemsDelta: Long,
        addedXp: Long
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val docRef = db.collection("networks").document(networkId).collection("members").document(uid)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val member = snap.toObject(NetworkMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Üye kaydı bulunamadı.")
                val lastClaimMs = member.lastDailyBonusAt?.toDate()?.time ?: 0L
                val remaining = DailyBonusPolicy.remainingMillisAt(lastClaimMs, System.currentTimeMillis())
                if (remaining > 0L) {
                    throw DailyBonusCooldownException(remaining)
                }
                val (nextLv, nextXp) = addXpForDailyBonus(member.level.toInt(), member.xp, addedXp)
                tx.update(
                    docRef,
                    mapOf(
                        "coins" to (member.coins + coinsDelta).coerceAtLeast(0L),
                        "gems" to (member.gems + gemsDelta).coerceAtLeast(0L),
                        "xp" to nextXp,
                        "level" to nextLv.toLong(),
                        "lastDailyBonusAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DailyBonusCooldownException) {
            Result.failure(e)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    private fun addXpForDailyBonus(currentLevel: Int, currentXp: Long, addedXp: Long): Pair<Int, Long> {
        var lv = currentLevel.coerceIn(1, 40)
        var xp = (currentXp + addedXp).coerceAtLeast(0L)
        while (lv < 40) {
            val needed = LevelSystem.xpForNextLevel(lv)
            if (xp >= needed) {
                xp -= needed
                lv++
            } else {
                break
            }
        }
        return lv to xp
    }

    // --- ROOMS & MULTIPLAYER SYNC ---
    fun observeRooms(networkId: String): Flow<List<GameRoom>> = flow {
        val uid = auth.currentUser?.uid ?: return@flow
        val path = "networks/$networkId/rooms"
        emitAll(
            db.collection("networks").document(networkId).collection("rooms")
                .whereEqualTo("networkId", networkId)
                .snapshots()
                .map { snap -> snap.toObjects(GameRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logAndRethrowFirestore(path, OperationType.LIST, uid)
        )
    }

    suspend fun createRoom(
        networkId: String,
        serverType: ServerType,
        mapName: String,
        theme: String = ""
    ): Result<GameRoom> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val roomsCol = db.collection("networks").document(networkId).collection("rooms")
        val docRef = roomsCol.document()
        return try {
            val room = GameRoom(
                id = docRef.id,
                networkId = networkId,
                serverType = serverType.code,
                mapName = mapName.take(60),
                waitingMapName = serverType.waitingLobbyMap.take(60),
                status = if (serverType == ServerType.LOBI || serverType == ServerType.CREATIVE || serverType == ServerType.SKYBLOCK) "PLAYING" else "WAITING",
                hostUid = uid,
                maxPlayers = serverType.defaultMaxPlayers.toLong(),
                playerCount = 1L,
                theme = theme.take(60),
                winnerName = ""
            )
            docRef.set(room.toCreateMap()).await()
            Result.success(room)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateRoomState(networkId: String, roomId: String, status: String, playerCount: Long, winnerName: String = ""): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Önce Google ile giriş yapılmalıdır."))
        val docRef = db.collection("networks").document(networkId).collection("rooms").document(roomId)
        return try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "playerCount" to playerCount.coerceIn(0L, 24L),
                    "winnerName" to winnerName.take(40),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateRoomMatchmaking(
        networkId: String,
        roomId: String,
        status: String,
        matchStartAtMillis: Long,
        winnerName: String = ""
    ): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val docRef = db.collection("networks").document(networkId).collection("rooms").document(roomId)
        return try {
            docRef.update(
                mapOf(
                    "status" to status,
                    "matchStartAtMillis" to matchStartAtMillis.coerceAtLeast(0L),
                    "winnerName" to winnerName.take(40),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncLocalPlayerInRoom(
        networkId: String,
        roomId: String,
        player: RoomPlayer
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val docRef = db.collection("networks").document(networkId)
            .collection("rooms").document(roomId)
            .collection("players").document(uid)
        return try {
            docRef.set(player.copy(userId = uid, roomId = roomId, networkId = networkId).toMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun findOrCreateRoom(
        networkId: String,
        serverType: ServerType,
        mapName: String
    ): Result<GameRoom> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        return try {
            val snap = db.collection("networks").document(networkId).collection("rooms")
                .whereEqualTo("networkId", networkId)
                .get()
                .await()
            val rooms = snap.toObjects(GameRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val requiresWaitingRoom = serverType == ServerType.BEDWARS ||
                serverType == ServerType.SKYWARS ||
                serverType == ServerType.BUILD_BATTLE
            val candidates = rooms.filter {
                it.serverType == serverType.code &&
                    it.status != "FINISHED" &&
                    (!requiresWaitingRoom || it.status == "WAITING") &&
                    it.playerCount < it.maxPlayers
            }
            val match = candidates.firstOrNull { it.mapName == mapName } ?: candidates.firstOrNull()
            if (match != null) {
                val nextCount = (match.playerCount + 1L).coerceAtMost(match.maxPlayers)
                updateRoomState(networkId, match.id, match.status, nextCount, match.winnerName)
                Result.success(match.copy(playerCount = nextCount))
            } else {
                createRoom(networkId, serverType, mapName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinExistingRoom(networkId: String, room: GameRoom): GameRoom {
        val nextCount = (room.playerCount + 1L).coerceAtMost(room.maxPlayers)
        updateRoomState(networkId, room.id, room.status, nextCount, room.winnerName)
        return room.copy(playerCount = nextCount)
    }

    suspend fun leaveRoom(networkId: String, roomId: String) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("networks").document(networkId)
            .collection("rooms").document(roomId)
            .collection("players").document(uid)
        try {
            docRef.delete().await()
            val roomRef = db.collection("networks").document(networkId).collection("rooms").document(roomId)
            try {
                val snap = roomRef.get().await()
                val room = snap.toObject(GameRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (room != null && room.status != "FINISHED" && room.playerCount > 0L) {
                    updateRoomState(networkId, roomId, room.status, (room.playerCount - 1L).coerceAtLeast(0L), room.winnerName)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Non-critical if banned or room already closed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
        }
    }

    suspend fun removePlayerFromAllNetworkRooms(
        networkId: String,
        targetUid: String,
        updateRoomCounts: Boolean = true
    ): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        return try {
            val roomsCol = db.collection("networks").document(networkId).collection("rooms")
            val roomsSnap = roomsCol.whereEqualTo("networkId", networkId).get().await()
            for (roomDoc in roomsSnap.documents) {
                val roomRef = roomDoc.reference
                val playerRef = roomRef.collection("players").document(targetUid)
                try {
                    if (updateRoomCounts) {
                        db.runTransaction { tx ->
                            val pSnap = tx.get(playerRef)
                            if (pSnap.exists()) {
                                val rSnap = tx.get(roomRef)
                                tx.delete(playerRef)
                                val status = rSnap.getString("status") ?: ""
                                val count = rSnap.getLong("playerCount") ?: 0L
                                if (rSnap.exists() && status != "FINISHED" && count > 0L) {
                                    tx.update(
                                        roomRef,
                                        mapOf(
                                            "playerCount" to (count - 1L).coerceAtLeast(0L),
                                            "updatedAt" to FieldValue.serverTimestamp()
                                        )
                                    )
                                }
                            }
                            null
                        }.await()
                    } else {
                        val pSnap = playerRef.get().await()
                        if (pSnap.exists()) {
                            playerRef.delete().await()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Fallback delete if transaction on room count failed
                    runCatching { playerRef.delete().await() }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeRoomPlayers(networkId: String, roomId: String): Flow<List<RoomPlayer>> = flow {
        val uid = auth.currentUser?.uid ?: return@flow
        val path = "networks/$networkId/rooms/$roomId/players"
        emitAll(
            db.collection("networks").document(networkId)
                .collection("rooms").document(roomId)
                .collection("players")
                .whereEqualTo("roomId", roomId)
                .snapshots()
                .map { snap -> snap.toObjects(RoomPlayer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logAndRethrowFirestore(path, OperationType.LIST, uid)
        )
    }

    fun observeRoomBlocks(networkId: String, roomId: String): Flow<List<RoomBlock>> = flow {
        val uid = auth.currentUser?.uid ?: return@flow
        val path = "networks/$networkId/rooms/$roomId/blocks"
        emitAll(
            db.collection("networks").document(networkId)
                .collection("rooms").document(roomId)
                .collection("blocks")
                .whereEqualTo("roomId", roomId)
                .snapshots()
                .map { snap -> snap.toObjects(RoomBlock::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logAndRethrowFirestore(path, OperationType.LIST, uid)
        )
    }

    suspend fun syncRoomBlock(
        networkId: String,
        roomId: String,
        coord: VoxelCoord,
        blockType: BlockType
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val key = coord.key()
        val docRef = db.collection("networks").document(networkId)
            .collection("rooms").document(roomId)
            .collection("blocks").document(key)
        return try {
            val block = RoomBlock(
                roomId = roomId,
                x = coord.x.toLong(),
                y = coord.y.toLong(),
                z = coord.z.toLong(),
                blockType = blockType.code,
                placedBy = uid
            )
            docRef.set(block.toMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeRoomChat(networkId: String, roomId: String): Flow<List<RoomChatMessage>> = flow {
        val uid = auth.currentUser?.uid ?: return@flow
        val path = "networks/$networkId/rooms/$roomId/chat"
        emitAll(
            db.collection("networks").document(networkId)
                .collection("rooms").document(roomId)
                .collection("chat")
                .whereEqualTo("roomId", roomId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(RoomChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt ?: com.google.firebase.Timestamp(0, 0) }
                        .takeLast(40)
                }
                .logAndRethrowFirestore(path, OperationType.LIST, uid)
        )
    }

    suspend fun sendRoomChatMessage(
        networkId: String,
        roomId: String,
        member: NetworkMember,
        text: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val clean = text.trim().take(200)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Mesaj boş olamaz"))
        val docRef = db.collection("networks").document(networkId)
            .collection("rooms").document(roomId)
            .collection("chat").document()
        return try {
            val msg = RoomChatMessage(
                roomId = roomId,
                networkId = networkId,
                senderUid = uid,
                senderName = member.username,
                senderRank = member.rank,
                senderLevel = member.level,
                text = clean
            )
            docRef.set(msg.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- ROOM PLAYER INTERACTIONS (PvP, Gifts, Waves) ---
    fun observeRoomInteractions(networkId: String, roomId: String): Flow<List<RoomInteraction>> = flow {
        val uid = auth.currentUser?.uid ?: return@flow
        val path = "networks/$networkId/rooms/$roomId/interactions"
        emitAll(
            db.collection("networks").document(networkId)
                .collection("rooms").document(roomId)
                .collection("interactions")
                .whereEqualTo("roomId", roomId)
                .snapshots()
                .map { snap -> snap.toObjects(RoomInteraction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logAndRethrowFirestore(path, OperationType.LIST, uid)
        )
    }

    suspend fun sendRoomInteraction(
        networkId: String,
        roomId: String,
        fromName: String,
        targetUid: String,
        actionType: String,
        amount: Long = 0L,
        itemId: String = ""
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val docRef = db.collection("networks").document(networkId)
            .collection("rooms").document(roomId)
            .collection("interactions").document()
        return try {
            val interaction = RoomInteraction(
                roomId = roomId,
                networkId = networkId,
                fromUid = uid,
                fromName = fromName.trim().take(24).ifBlank { "Oyuncu" },
                targetUid = targetUid,
                actionType = actionType,
                amount = amount.coerceIn(0L, 1000L),
                itemId = itemId.take(40)
            )
            docRef.set(interaction.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun consumeRoomInteraction(networkId: String, roomId: String, interactionId: String) {
        val docRef = db.collection("networks").document(networkId)
            .collection("rooms").document(roomId)
            .collection("interactions").document(interactionId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Non-critical cleanup if already deleted
        }
    }

    // --- PERSISTENT CREATIVE / SKYBLOCK PLOTS ---
    suspend fun savePlayerPlot(
        networkId: String,
        mode: String,
        plotName: String,
        blocksData: String,
        islandLevel: Long
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val plotId = "${uid}_${mode.lowercase()}"
        val docRef = db.collection("networks").document(networkId).collection("plots").document(plotId)
        return try {
            val plot = PlayerPlot(
                ownerUid = uid,
                networkId = networkId,
                mode = mode,
                plotName = plotName.trim().take(40).ifBlank { "Arsam" },
                blocksData = blocksData.take(19500),
                islandLevel = islandLevel.coerceIn(1L, 9999L)
            )
            docRef.set(plot.toMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun loadPlayerPlot(networkId: String, mode: String): PlayerPlot? {
        val uid = auth.currentUser?.uid ?: return null
        val plotId = "${uid}_${mode.lowercase()}"
        val docRef = db.collection("networks").document(networkId).collection("plots").document(plotId)
        return try {
            val snap = docRef.get().await()
            snap.toObject(PlayerPlot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            null
        }
    }
}
