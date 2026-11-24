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

class StaffRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    // --- SUPPORT TICKETS ---
    fun observeTickets(networkId: String): Flow<List<SupportTicket>> = flow {
        val uid = auth.currentUser?.uid ?: return@flow
        val path = "networks/$networkId/tickets"
        emitAll(
            db.collection("networks").document(networkId).collection("tickets")
                .whereEqualTo("networkId", networkId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: com.google.firebase.Timestamp(0, 0) }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createSupportTicket(
        networkId: String,
        authorName: String,
        subject: String,
        message: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val cleanSubject = subject.trim().take(80)
        val cleanMessage = message.trim().take(400)
        if (cleanSubject.length < 3 || cleanMessage.length < 3) {
            return Result.failure(IllegalArgumentException("Konu ve mesaj en az 3 karakter olmalı"))
        }
        val docRef = db.collection("networks").document(networkId).collection("tickets").document()
        return try {
            val ticket = SupportTicket(
                networkId = networkId,
                authorUid = uid,
                authorName = authorName.trim().take(24).ifBlank { "Oyuncu" },
                subject = cleanSubject,
                message = cleanMessage,
                status = "OPEN"
            )
            docRef.set(ticket.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun replySupportTicket(
        networkId: String,
        ticketId: String,
        staffName: String,
        replyText: String,
        newStatus: String = "ANSWERED"
    ): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Önce Google ile giriş yapılmalıdır."))
        val docRef = db.collection("networks").document(networkId).collection("tickets").document(ticketId)
        return try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "staffReply" to replyText.trim().take(400),
                    "repliedBy" to staffName.trim().take(30),
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

    // --- PLAYER REPORTS ---
    fun observeReports(networkId: String): Flow<List<PlayerReport>> = flow {
        val uid = auth.currentUser?.uid ?: return@flow
        val path = "networks/$networkId/reports"
        emitAll(
            db.collection("networks").document(networkId).collection("reports")
                .whereEqualTo("networkId", networkId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PlayerReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: com.google.firebase.Timestamp(0, 0) }
                }
                .catch { error ->
                    val fe = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fe == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) handleFirestoreError(fe ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createReport(
        networkId: String,
        reporterName: String,
        targetUid: String,
        targetName: String,
        reason: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Oturum kapalı"))
        val cleanReason = reason.trim().take(200)
        if (cleanReason.length < 3) return Result.failure(IllegalArgumentException("İhlal sebebi en az 3 karakter olmalı"))
        val docRef = db.collection("networks").document(networkId).collection("reports").document()
        return try {
            val report = PlayerReport(
                networkId = networkId,
                reporterUid = uid,
                reporterName = reporterName.trim().take(24).ifBlank { "Oyuncu" },
                targetUid = targetUid.ifBlank { "unknown_target" },
                targetName = targetName.trim().take(24).ifBlank { "Şüpheli" },
                reason = cleanReason,
                status = "PENDING"
            )
            docRef.set(report.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun reviewReport(
        networkId: String,
        reportId: String,
        accepted: Boolean,
        staffName: String
    ): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Önce Google ile giriş yapılmalıdır."))
        val docRef = db.collection("networks").document(networkId).collection("reports").document(reportId)
        return try {
            docRef.update(
                mapOf(
                    "status" to if (accepted) "ACCEPTED" else "REJECTED",
                    "reviewedBy" to staffName.trim().take(30),
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

    // --- STAFF MODERATION & ADMIN MUTATIONS ---
    suspend fun banMember(
        networkId: String,
        targetUid: String,
        banned: Boolean,
        reason: String,
        durationMinutes: Long? // null = permanent
    ): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Önce Google ile giriş yapılmalıdır."))
        val until = if (!banned || durationMinutes == null) 0L else System.currentTimeMillis() + (durationMinutes * 60_000L)
        val docRef = db.collection("networks").document(networkId).collection("members").document(targetUid)
        return try {
            docRef.update(
                mapOf(
                    "banned" to banned,
                    "banReason" to if (banned) reason.trim().take(200) else "",
                    "banUntilMillis" to until,
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

    suspend fun muteMember(
        networkId: String,
        targetUid: String,
        muted: Boolean,
        reason: String,
        durationMinutes: Long?
    ): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Önce Google ile giriş yapılmalıdır."))
        val until = if (!muted || durationMinutes == null) 0L else System.currentTimeMillis() + (durationMinutes * 60_000L)
        val docRef = db.collection("networks").document(networkId).collection("members").document(targetUid)
        return try {
            docRef.update(
                mapOf(
                    "muted" to muted,
                    "muteReason" to if (muted) reason.trim().take(200) else "",
                    "muteUntilMillis" to until,
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

    suspend fun warnMember(networkId: String, targetUid: String, currentWarnings: Long): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Önce Google ile giriş yapılmalıdır."))
        val docRef = db.collection("networks").document(networkId).collection("members").document(targetUid)
        return try {
            docRef.update(
                mapOf(
                    "warningsCount" to (currentWarnings + 1L),
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

    suspend fun setMemberRank(networkId: String, targetUid: String, newRank: Rank): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Önce Google ile giriş yapılmalıdır."))
        val docRef = db.collection("networks").document(networkId).collection("members").document(targetUid)
        return try {
            docRef.update(
                mapOf(
                    "rank" to newRank.code,
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

    suspend fun setMemberGameMode(networkId: String, targetUid: String, mode: GameMode): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Önce Google ile giriş yapılmalıdır."))
        val docRef = db.collection("networks").document(networkId).collection("members").document(targetUid)
        return try {
            docRef.update(
                mapOf(
                    "gameMode" to mode.code,
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

    suspend fun updateMemberEconomyAndLevel(
        networkId: String,
        targetUid: String,
        coins: Long,
        gems: Long,
        level: Long,
        inventoryJson: String
    ): Result<Unit> {
        auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Önce Google ile giriş yapılmalıdır."))
        val docRef = db.collection("networks").document(networkId).collection("members").document(targetUid)
        return try {
            docRef.update(
                mapOf(
                    "coins" to coins.coerceAtLeast(0L),
                    "gems" to gems.coerceAtLeast(0L),
                    "level" to level.coerceIn(1L, 40L),
                    "inventoryJson" to inventoryJson.take(7900),
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
}
