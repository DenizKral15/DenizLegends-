package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "DenizOyuncu",
    val email: String = "",
    val skinColor: Long = 0L,
    val hatId: String = "builder_hat",
    val globalCoins: Long = 1000L,
    val globalGems: Long = 20L,
    val globalXp: Long = 0L,
    val globalLevel: Long = 1L,
    val preferredNetworkId: String = "official",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "username" to username,
        "email" to email,
        "skinColor" to skinColor,
        "hatId" to hatId,
        "globalCoins" to globalCoins,
        "globalGems" to globalGems,
        "globalXp" to globalXp,
        "globalLevel" to globalLevel,
        "preferredNetworkId" to preferredNetworkId,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class GameNetwork(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val policyText: String = "",
    val founderUid: String = "",
    val founderName: String = "",
    val official: Boolean = false,
    val active: Boolean = true,
    val maintenanceMode: Boolean = false,
    val playerCount: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "name" to name,
        "description" to description,
        "policyText" to policyText,
        "founderUid" to founderUid,
        "founderName" to founderName,
        "official" to official,
        "active" to active,
        "maintenanceMode" to maintenanceMode,
        "playerCount" to playerCount,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class NetworkMember(
    @DocumentId val id: String = "",
    val userId: String = "",
    val networkId: String = "official",
    val username: String = "Oyuncu",
    val rank: String = Rank.OYUNCU.code,
    val level: Long = 1L,
    val xp: Long = 0L,
    val coins: Long = 500L,
    val gems: Long = 15L,
    val gameMode: String = GameMode.SURVIVAL.code,
    val inventoryJson: String = "wood_sword:1,wool_cyan:32,wood:16,pickaxe:1,bread:4",
    val banned: Boolean = false,
    val banReason: String = "",
    val banUntilMillis: Long = 0L,
    val muted: Boolean = false,
    val muteReason: String = "",
    val muteUntilMillis: Long = 0L,
    val warningsCount: Long = 0L,
    val skinColor: Long = 0L,
    val lastDailyBonusAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val rankEnum: Rank get() = Rank.fromCode(rank)
    val gameModeEnum: GameMode get() = GameMode.fromCode(gameMode)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "networkId" to networkId,
        "username" to username,
        "rank" to rank,
        "level" to level,
        "xp" to xp,
        "coins" to coins,
        "gems" to gems,
        "gameMode" to gameMode,
        "inventoryJson" to inventoryJson,
        "banned" to banned,
        "banReason" to banReason,
        "banUntilMillis" to banUntilMillis,
        "muted" to muted,
        "muteReason" to muteReason,
        "muteUntilMillis" to muteUntilMillis,
        "warningsCount" to warningsCount,
        "skinColor" to skinColor,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class GameRoom(
    @DocumentId val id: String = "",
    val networkId: String = "official",
    val serverType: String = ServerType.LOBI.code,
    val mapName: String = "",
    val waitingMapName: String = "",
    val status: String = "WAITING",
    val hostUid: String = "",
    val maxPlayers: Long = 12L,
    val playerCount: Long = 1L,
    val theme: String = "",
    val winnerName: String = "",
    val matchStartAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val serverTypeEnum: ServerType get() = ServerType.fromCode(serverType)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "networkId" to networkId,
        "serverType" to serverType,
        "mapName" to mapName,
        "waitingMapName" to waitingMapName,
        "status" to status,
        "hostUid" to hostUid,
        "maxPlayers" to maxPlayers,
        "playerCount" to playerCount,
        "theme" to theme,
        "winnerName" to winnerName,
        "matchStartAtMillis" to matchStartAtMillis,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class RoomPlayer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val roomId: String = "",
    val networkId: String = "official",
    val username: String = "",
    val rank: String = Rank.OYUNCU.code,
    val level: Long = 1L,
    val team: String = "NONE",
    val x: Double = 0.0,
    val y: Double = 2.0,
    val z: Double = 0.0,
    val yaw: Double = 0.0,
    val hp: Long = 20L,
    val alive: Boolean = true,
    val skinColor: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "roomId" to roomId,
        "networkId" to networkId,
        "username" to username,
        "rank" to rank,
        "level" to level,
        "team" to team,
        "x" to x,
        "y" to y,
        "z" to z,
        "yaw" to yaw,
        "hp" to hp,
        "alive" to alive,
        "skinColor" to skinColor,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class RoomBlock(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val x: Long = 0L,
    val y: Long = 0L,
    val z: Long = 0L,
    val blockType: String = BlockType.STONE.code,
    val placedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> = mapOf(
        "roomId" to roomId,
        "x" to x,
        "y" to y,
        "z" to z,
        "blockType" to blockType,
        "placedBy" to placedBy,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class RoomChatMessage(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val networkId: String = "official",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRank: String = Rank.OYUNCU.code,
    val senderLevel: Long = 1L,
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "roomId" to roomId,
        "networkId" to networkId,
        "senderUid" to senderUid,
        "senderName" to senderName,
        "senderRank" to senderRank,
        "senderLevel" to senderLevel,
        "text" to text,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class RoomInteraction(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val networkId: String = "official",
    val fromUid: String = "",
    val fromName: String = "",
    val targetUid: String = "",
    val actionType: String = "WAVE",
    val amount: Long = 0L,
    val itemId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "roomId" to roomId,
        "networkId" to networkId,
        "fromUid" to fromUid,
        "fromName" to fromName,
        "targetUid" to targetUid,
        "actionType" to actionType,
        "amount" to amount,
        "itemId" to itemId,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val networkId: String = "official",
    val authorUid: String = "",
    val authorName: String = "",
    val subject: String = "",
    val message: String = "",
    val status: String = "OPEN",
    val staffReply: String = "",
    val repliedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "networkId" to networkId,
        "authorUid" to authorUid,
        "authorName" to authorName,
        "subject" to subject,
        "message" to message,
        "status" to status,
        "staffReply" to staffReply,
        "repliedBy" to repliedBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PlayerReport(
    @DocumentId val id: String = "",
    val networkId: String = "official",
    val reporterUid: String = "",
    val reporterName: String = "",
    val targetUid: String = "",
    val targetName: String = "",
    val reason: String = "",
    val status: String = "PENDING",
    val reviewedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "networkId" to networkId,
        "reporterUid" to reporterUid,
        "reporterName" to reporterName,
        "targetUid" to targetUid,
        "targetName" to targetName,
        "reason" to reason,
        "status" to status,
        "reviewedBy" to reviewedBy,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PlayerPlot(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val networkId: String = "official",
    val mode: String = "CREATIVE",
    val plotName: String = "Arsam",
    val blocksData: String = "",
    val islandLevel: Long = 1L,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> = mapOf(
        "ownerUid" to ownerUid,
        "networkId" to networkId,
        "mode" to mode,
        "plotName" to plotName,
        "blocksData" to blocksData,
        "islandLevel" to islandLevel,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
