package com.example.data

import android.content.Context
import com.example.R
import com.example.engine.AmatatuEngine
import com.example.model.AntiCheatLog
import com.example.model.GameHistoryRecord
import com.example.model.GameTable
import com.example.model.InviteCode
import com.example.model.MoveAudit
import com.example.model.PlayerHand
import com.example.model.PlayingCard
import com.example.model.PublicProfile
import com.example.model.Suit
import com.example.model.UserAccount
import com.example.model.WalletTransaction
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AmatatuRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun newDocId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    private fun generateInviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString {
            append("AMT")
            repeat(4) { append(alphabet.random()) }
        }
    }

    private fun generateReference(prefix: String): String =
        "$prefix-${(100000..999999).random()}"

    private fun <T> Flow<T>.logFirestoreErrors(operation: OperationType, path: String, uid: String): Flow<T> =
        catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            val toLog = firestoreError ?: (error as? Exception)
            if (toLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(toLog, operation, path)
            }
            throw error
        }

    // --- Account & Profile ---

    suspend fun ensureUserAccountInitialized(
        displayName: String,
        photoUrl: String?
    ): Result<UserAccount> = runCatching {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        val snap = userRef.get().await()
        if (snap.exists()) {
            return@runCatching snap.toObject(
                UserAccount::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: UserAccount(id = uid, userId = uid, displayName = displayName)
        }

        val cleanName = displayName.trim().ifBlank { "Kampala Player" }.take(36)
        val welcomeTxId = newDocId("tx")
        val batch = db.batch()

        val userPayload = hashMapOf<String, Any>(
            "userId" to uid,
            "displayName" to cleanName,
            "phoneNumber" to "",
            "preferredProvider" to "MTN_MOMO",
            "avatarKey" to "crown_gold",
            "walletBalanceUgx" to 25000L,
            "escrowUgx" to 0L,
            "gamesPlayed" to 0L,
            "gamesWon" to 0L,
            "totalPointsWon" to 0L,
            "totalEarningsUgx" to 0L,
            "isBanned" to false,
            "soundEnabled" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!photoUrl.isNullOrBlank()) {
            userPayload["photoUrl"] = photoUrl.take(450)
        }

        val publicPayload = mapOf(
            "userId" to uid,
            "displayName" to cleanName,
            "avatarKey" to "crown_gold",
            "gamesPlayed" to 0L,
            "gamesWon" to 0L,
            "totalEarningsUgx" to 0L,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val bonusTxPayload = mapOf(
            "userId" to uid,
            "userName" to cleanName,
            "type" to "WELCOME_BONUS",
            "amountUgx" to 25000L,
            "feeUgx" to 0L,
            "netAmountUgx" to 25000L,
            "provider" to "SYSTEM",
            "phoneNumber" to "",
            "referenceCode" to generateReference("WLC"),
            "tableId" to "",
            "status" to "COMPLETED",
            "note" to "Starter UGX club chips",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        batch.set(userRef, userPayload)
        batch.set(db.collection("public_profiles").document(uid), publicPayload)
        batch.set(db.collection("transactions").document(welcomeTxId), bonusTxPayload)
        batch.commit().await()

        userRef.get().await().toObject(
            UserAccount::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ) ?: UserAccount(id = uid, userId = uid, displayName = cleanName, walletBalanceUgx = 25000L)
    }.onFailure { e ->
        handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}")
    }

    fun observeCurrentUser(uid: String): Flow<UserAccount?> = flow {
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid).snapshots()
                .map { snap ->
                    snap.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logFirestoreErrors(OperationType.GET, path, uid)
        )
    }

    suspend fun getCurrentUser(uid: String): Result<UserAccount?> = runCatching {
        db.collection("users").document(uid).get().await()
            .toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.onFailure { handleFirestoreError(it, OperationType.GET, "users/$uid") }

    suspend fun updateProfile(
        displayName: String,
        phoneNumber: String,
        preferredProvider: String,
        avatarKey: String,
        soundEnabled: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val cleanName = displayName.trim().take(36)
        require(cleanName.length >= 2) { "Display name must be at least 2 characters" }
        val cleanPhone = phoneNumber.trim().take(20)
        val batch = db.batch()
        batch.update(
            db.collection("users").document(uid),
            mapOf(
                "displayName" to cleanName,
                "phoneNumber" to cleanPhone,
                "preferredProvider" to preferredProvider,
                "avatarKey" to avatarKey,
                "soundEnabled" to soundEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("public_profiles").document(uid),
            mapOf(
                "displayName" to cleanName,
                "avatarKey" to avatarKey,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "users/${auth.currentUser?.uid}") }

    fun observeLeaderboard(uid: String): Flow<List<PublicProfile>> = flow {
        val path = "public_profiles"
        emitAll(
            db.collection(path)
                .whereGreaterThanOrEqualTo("gamesWon", 0L)
                .orderBy("gamesWon", Query.Direction.DESCENDING)
                .limit(25)
                .snapshots()
                .map { snap ->
                    snap.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun checkIsAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        if (user.email.equals("markniaras@gmail.com", ignoreCase = true) && user.isEmailVerified) {
            return true
        }
        return runCatching {
            db.collection("admins").document(user.uid).get().await().exists()
        }.getOrDefault(false)
    }

    // --- Wallet & Mobile Money Cashier ---

    fun observeUserTransactions(uid: String): Flow<List<WalletTransaction>> = flow {
        val path = "transactions"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(WalletTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun submitMobileMoneyDeposit(
        userName: String,
        amountUgx: Long,
        provider: String,
        phoneNumber: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(amountUgx in 1000L..5000000L) { "Deposit must be between UGX 1,000 and UGX 5,000,000" }
        val cleanPhone = phoneNumber.trim()
        require(cleanPhone.length >= 9) { "Enter a valid Ugandan Mobile Money number" }

        val txId = newDocId("dep")
        val refCode = generateReference(if (provider == "MTN_MOMO") "MTN" else "AIR")
        val payload = mapOf(
            "userId" to uid,
            "userName" to userName.take(40),
            "type" to "DEPOSIT",
            "amountUgx" to amountUgx,
            "feeUgx" to 0L,
            "netAmountUgx" to amountUgx,
            "provider" to provider,
            "phoneNumber" to cleanPhone.take(20),
            "referenceCode" to refCode,
            "tableId" to "",
            "status" to "PENDING",
            "note" to "Mobile Money deposit awaiting cashier verification",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("transactions").document(txId).set(payload).await()
        refCode
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, "transactions") }

    suspend fun submitMobileMoneyWithdrawal(
        userName: String,
        amountUgx: Long,
        provider: String,
        phoneNumber: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(amountUgx in 2000L..5000000L) { "Withdrawal must be between UGX 2,000 and UGX 5,000,000" }
        val cleanPhone = phoneNumber.trim()
        require(cleanPhone.length >= 9) { "Enter a valid Ugandan Mobile Money number" }

        val txId = newDocId("wdl")
        val refCode = generateReference(if (provider == "MTN_MOMO") "MTW" else "ATW")
        val userRef = db.collection("users").document(uid)
        val txRef = db.collection("transactions").document(txId)

        db.runTransaction { txn ->
            val snap = txn.get(userRef)
            val balance = snap.getLong("walletBalanceUgx") ?: 0L
            val isBanned = snap.getBoolean("isBanned") ?: false
            check(!isBanned) { "Account is suspended. Contact Admin." }
            check(balance >= amountUgx) { "Insufficient UGX balance" }

            txn.update(
                userRef,
                mapOf(
                    "walletBalanceUgx" to (balance - amountUgx),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            txn.set(
                txRef,
                mapOf(
                    "userId" to uid,
                    "userName" to userName.take(40),
                    "type" to "WITHDRAWAL",
                    "amountUgx" to amountUgx,
                    "feeUgx" to 0L,
                    "netAmountUgx" to amountUgx,
                    "provider" to provider,
                    "phoneNumber" to cleanPhone.take(20),
                    "referenceCode" to refCode,
                    "tableId" to "",
                    "status" to "PENDING",
                    "note" to "Mobile Money cashout queued for payout",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()
        refCode
    }.onFailure { handleFirestoreError(it, OperationType.WRITE, "transactions") }

    // --- Lobby & Matchmaking ---

    fun observePublicWaitingTables(uid: String): Flow<List<GameTable>> = flow {
        val path = "tables"
        emitAll(
            db.collection(path)
                .whereEqualTo("isPrivate", false)
                .whereEqualTo("status", "WAITING")
                .snapshots()
                .map { snap ->
                    snap.toObjects(GameTable::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeUserTables(uid: String): Flow<List<GameTable>> = flow {
        val path = "tables"
        emitAll(
            db.collection(path)
                .whereArrayContains("playerIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(GameTable::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeTable(tableId: String, uid: String): Flow<GameTable?> = flow {
        val path = "tables/$tableId"
        emitAll(
            db.collection("tables").document(tableId).snapshots()
                .map { snap -> snap.toObject(GameTable::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logFirestoreErrors(OperationType.GET, path, uid)
        )
    }

    fun observePlayerHand(tableId: String, playerUid: String, currentUserUid: String): Flow<PlayerHand?> = flow {
        val path = "tables/$tableId/hands/$playerUid"
        emitAll(
            db.collection("tables").document(tableId).collection("hands").document(playerUid)
                .snapshots()
                .map { snap -> snap.toObject(PlayerHand::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logFirestoreErrors(OperationType.GET, path, currentUserUid)
        )
    }

    fun observeTableMoves(tableId: String, uid: String): Flow<List<MoveAudit>> = flow {
        val path = "tables/$tableId/moves"
        emitAll(
            db.collection("tables").document(tableId).collection("moves")
                .whereArrayContains("playerIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(MoveAudit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.moveSequence }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun createTable(
        user: UserAccount,
        title: String,
        stakeUgx: Long,
        maxPlayers: Int,
        isPrivate: Boolean,
        isBotMatch: Boolean
    ): Result<String> = runCatching {
        val uid = requireUserId()
        check(!user.isBanned) { "Account is suspended" }
        require(stakeUgx in 0L..100000L) { "Invalid UGX stake" }
        val tableId = newDocId("tbl")
        val inviteCode = if (isPrivate) generateInviteCode() else ""
        val userRef = db.collection("users").document(uid)
        val tableRef = db.collection("tables").document(tableId)

        val deal = if (isBotMatch) {
            AmatatuEngine.dealMatch(tableId, listOf(uid, AmatatuEngine.BOT_UID))
        } else null

        val potIfBot = stakeUgx * 2L
        val payoutIfBot = if (stakeUgx == 0L) 0L else (potIfBot * 95L) / 100L

        db.runTransaction { txn ->
            val uSnap = txn.get(userRef)
            val balance = uSnap.getLong("walletBalanceUgx") ?: 0L
            val escrow = uSnap.getLong("escrowUgx") ?: 0L
            check(balance >= stakeUgx) { "Insufficient UGX balance for stake UGX $stakeUgx" }

            if (stakeUgx > 0L) {
                txn.update(
                    userRef,
                    mapOf(
                        "walletBalanceUgx" to (balance - stakeUgx),
                        "escrowUgx" to (escrow + stakeUgx),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val txRef = db.collection("transactions").document(newDocId("esc"))
                txn.set(
                    txRef,
                    mapOf(
                        "userId" to uid,
                        "userName" to user.displayName.take(40),
                        "type" to "STAKE_ESCROW",
                        "amountUgx" to stakeUgx,
                        "feeUgx" to 0L,
                        "netAmountUgx" to stakeUgx,
                        "provider" to "WALLET_ESCROW",
                        "phoneNumber" to "",
                        "referenceCode" to generateReference("ESC"),
                        "tableId" to tableId,
                        "status" to "COMPLETED",
                        "note" to "Table stake locked: $title",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            if (isBotMatch && deal != null) {
                val playerHand = deal.handsByPlayer[uid].orEmpty()
                val botHand = deal.handsByPlayer[AmatatuEngine.BOT_UID].orEmpty()
                val cleanTitle = title.trim().ifBlank { "Amatatu Table" }.let { if (it.length < 2) "$it Table" else it }.take(48)
                txn.set(
                    tableRef,
                    mapOf(
                        "title" to cleanTitle,
                        "hostId" to uid,
                        "stakeUgx" to stakeUgx,
                        "platformFeePct" to 5L,
                        "potUgx" to potIfBot,
                        "payoutUgx" to payoutIfBot,
                        "maxPlayers" to 2L,
                        "isPrivate" to true,
                        "inviteCode" to "",
                        "isBotMatch" to true,
                        "status" to "ACTIVE",
                        "playerIds" to listOf(uid, AmatatuEngine.BOT_UID),
                        "playerNames" to mapOf(uid to user.displayName.take(36), AmatatuEngine.BOT_UID to AmatatuEngine.BOT_NAME),
                        "playerAvatars" to mapOf(uid to user.avatarKey, AmatatuEngine.BOT_UID to AmatatuEngine.BOT_AVATAR),
                        "cardCounts" to mapOf(uid to playerHand.size.toLong(), AmatatuEngine.BOT_UID to botHand.size.toLong()),
                        "turnDirection" to 1L,
                        "currentTurnUid" to uid,
                        "cutSuit" to deal.cutSuit.name,
                        "cutCard" to deal.cutCard,
                        "topCard" to deal.topCard,
                        "demandedSuit" to "",
                        "pendingDrawPenalty" to 0L,
                        "drawPile" to deal.drawPile,
                        "discardPile" to deal.discardPile,
                        "moveSequence" to 0L,
                        "lastActionSummary" to "Match dealt! Cut Suit is ${deal.cutSuit.label}. Your turn.",
                        "deckCommitHash" to deal.deckCommitHash,
                        "cutCallerId" to "",
                        "winnerId" to "",
                        "winnerName" to "",
                        "winReason" to "",
                        "payoutSettled" to false,
                        "flaggedCheat" to false,
                        "cheatReason" to "",
                        "revealedHands" to emptyMap<String, List<String>>(),
                        "revealedPoints" to emptyMap<String, Long>(),
                        "settledUids" to emptyList<String>(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                txn.set(
                    tableRef.collection("hands").document(uid),
                    mapOf(
                        "playerId" to uid,
                        "tableId" to tableId,
                        "cards" to playerHand,
                        "handPoints" to AmatatuEngine.calculatePoints(playerHand).toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                txn.set(
                    tableRef.collection("hands").document(AmatatuEngine.BOT_UID),
                    mapOf(
                        "playerId" to AmatatuEngine.BOT_UID,
                        "tableId" to tableId,
                        "cards" to botHand,
                        "handPoints" to AmatatuEngine.calculatePoints(botHand).toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                val initPayout = if (stakeUgx == 0L) 0L else (stakeUgx * maxPlayers * 95L) / 100L
                val cleanTitle = title.trim().ifBlank { "Amatatu Table" }.let { if (it.length < 2) "$it Table" else it }.take(48)
                txn.set(
                    tableRef,
                    mapOf(
                        "title" to cleanTitle,
                        "hostId" to uid,
                        "stakeUgx" to stakeUgx,
                        "platformFeePct" to 5L,
                        "potUgx" to stakeUgx,
                        "payoutUgx" to initPayout,
                        "maxPlayers" to maxPlayers.toLong().coerceIn(2L, 4L),
                        "isPrivate" to isPrivate,
                        "inviteCode" to inviteCode,
                        "isBotMatch" to false,
                        "status" to "WAITING",
                        "playerIds" to listOf(uid),
                        "playerNames" to mapOf(uid to user.displayName.take(36)),
                        "playerAvatars" to mapOf(uid to user.avatarKey),
                        "cardCounts" to mapOf(uid to 0L),
                        "turnDirection" to 1L,
                        "currentTurnUid" to uid,
                        "cutSuit" to "",
                        "cutCard" to "",
                        "topCard" to "",
                        "demandedSuit" to "",
                        "pendingDrawPenalty" to 0L,
                        "drawPile" to emptyList<String>(),
                        "discardPile" to emptyList<String>(),
                        "moveSequence" to 0L,
                        "lastActionSummary" to "Waiting for players to take seats",
                        "deckCommitHash" to "",
                        "cutCallerId" to "",
                        "winnerId" to "",
                        "winnerName" to "",
                        "winReason" to "",
                        "payoutSettled" to false,
                        "flaggedCheat" to false,
                        "cheatReason" to "",
                        "revealedHands" to emptyMap<String, List<String>>(),
                        "revealedPoints" to emptyMap<String, Long>(),
                        "settledUids" to emptyList<String>(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (isPrivate && inviteCode.isNotEmpty()) {
                    txn.set(
                        db.collection("invite_codes").document(inviteCode),
                        mapOf(
                            "code" to inviteCode,
                            "tableId" to tableId,
                            "hostId" to uid,
                            "stakeUgx" to stakeUgx,
                            "maxPlayers" to maxPlayers.toLong().coerceIn(2L, 4L),
                            "status" to "WAITING",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
        }.await()

        tableId
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, "tables") }

    suspend fun quickMatchOnline(user: UserAccount, stakeUgx: Long): Result<String> = runCatching {
        val uid = requireUserId()
        val waitingSnap = db.collection("tables")
            .whereEqualTo("isPrivate", false)
            .whereEqualTo("status", "WAITING")
            .get()
            .await()

        val matchCandidate = waitingSnap.toObjects(
            GameTable::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ).firstOrNull { tbl ->
            tbl.stakeUgx == stakeUgx &&
                !tbl.playerIds.contains(uid) &&
                tbl.playerIds.size < tbl.maxPlayers.toInt()
        }

        if (matchCandidate != null) {
            joinTableById(user, matchCandidate.id).getOrThrow()
            matchCandidate.id
        } else {
            val label = if (stakeUgx == 0L) "Open Felt" else "UGX $stakeUgx Open Table"
            createTable(
                user = user,
                title = label,
                stakeUgx = stakeUgx,
                maxPlayers = 2,
                isPrivate = false,
                isBotMatch = false
            ).getOrThrow()
        }
    }.onFailure { handleFirestoreError(it, OperationType.WRITE, "tables") }

    suspend fun joinPrivateTableByCode(user: UserAccount, rawCode: String): Result<String> = runCatching {
        requireUserId()
        val cleanCode = rawCode.trim().uppercase()
        require(cleanCode.length in 4..12) { "Enter a valid invite code (e.g. AMT7K2P)" }
        val inviteSnap = db.collection("invite_codes").document(cleanCode).get().await()
        val invite = inviteSnap.toObject(InviteCode::class.java)
            ?: throw IllegalStateException("Room code $cleanCode not found")
        check(invite.status == "WAITING") { "Room $cleanCode is no longer open (${invite.status})" }
        joinTableById(user, invite.tableId).getOrThrow()
        invite.tableId
    }.onFailure { handleFirestoreError(it, OperationType.GET, "invite_codes/$rawCode") }

    suspend fun joinTableById(user: UserAccount, tableId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        check(!user.isBanned) { "Account is suspended" }
        val userRef = db.collection("users").document(uid)
        val tableRef = db.collection("tables").document(tableId)

        db.runTransaction { txn ->
            val tSnap = txn.get(tableRef)
            val table = tSnap.toObject(GameTable::class.java)
                ?: throw IllegalStateException("Table not found")
            if (table.playerIds.contains(uid)) return@runTransaction
            check(table.status == "WAITING") { "Match already started or closed" }
            check(table.playerIds.size < table.maxPlayers.toInt()) { "Table is full" }

            val uSnap = txn.get(userRef)
            val balance = uSnap.getLong("walletBalanceUgx") ?: 0L
            val escrow = uSnap.getLong("escrowUgx") ?: 0L
            check(balance >= table.stakeUgx) { "Insufficient UGX balance (need UGX ${table.stakeUgx})" }

            val newPlayers = table.playerIds + uid
            val newNames = table.playerNames + (uid to user.displayName.take(36))
            val newAvatars = table.playerAvatars + (uid to user.avatarKey)
            val newCounts = table.cardCounts + (uid to 0L)
            val newPot = table.stakeUgx * newPlayers.size
            val newPayout = if (table.stakeUgx == 0L) 0L else (newPot * (100L - table.platformFeePct)) / 100L

            if (table.stakeUgx > 0L) {
                txn.update(
                    userRef,
                    mapOf(
                        "walletBalanceUgx" to (balance - table.stakeUgx),
                        "escrowUgx" to (escrow + table.stakeUgx),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                txn.set(
                    db.collection("transactions").document(newDocId("esc")),
                    mapOf(
                        "userId" to uid,
                        "userName" to user.displayName.take(40),
                        "type" to "STAKE_ESCROW",
                        "amountUgx" to table.stakeUgx,
                        "feeUgx" to 0L,
                        "netAmountUgx" to table.stakeUgx,
                        "provider" to "WALLET_ESCROW",
                        "phoneNumber" to "",
                        "referenceCode" to generateReference("ESC"),
                        "tableId" to tableId,
                        "status" to "COMPLETED",
                        "note" to "Joined table ${table.title}",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            txn.update(
                tableRef,
                mapOf(
                    "playerIds" to newPlayers,
                    "playerNames" to newNames,
                    "playerAvatars" to newAvatars,
                    "cardCounts" to newCounts,
                    "potUgx" to newPot,
                    "payoutUgx" to newPayout,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "tables/$tableId") }

    suspend fun startWaitingTable(table: GameTable): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(table.status == "WAITING") { "Table is not waiting" }
        require(table.playerIds.contains(uid)) { "Only seated players can start" }
        require(table.playerIds.size >= 2) { "Need at least 2 players to start" }

        val deal = AmatatuEngine.dealMatch(table.id, table.playerIds)
        val counts = deal.handsByPlayer.mapValues { it.value.size.toLong() }
        val pot = table.stakeUgx * table.playerIds.size
        val payout = if (table.stakeUgx == 0L) 0L else (pot * (100L - table.platformFeePct)) / 100L

        val batch = db.batch()
        val tableRef = db.collection("tables").document(table.id)
        batch.update(
            tableRef,
            mapOf(
                "status" to "ACTIVE",
                "cardCounts" to counts,
                "currentTurnUid" to table.playerIds.first(),
                "cutSuit" to deal.cutSuit.name,
                "cutCard" to deal.cutCard,
                "topCard" to deal.topCard,
                "demandedSuit" to "",
                "pendingDrawPenalty" to 0L,
                "drawPile" to deal.drawPile,
                "discardPile" to deal.discardPile,
                "moveSequence" to 0L,
                "lastActionSummary" to "Cards dealt! Cut Suit: ${deal.cutSuit.label}",
                "deckCommitHash" to deal.deckCommitHash,
                "potUgx" to pot,
                "payoutUgx" to payout,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        for ((playerUid, cards) in deal.handsByPlayer) {
            batch.set(
                tableRef.collection("hands").document(playerUid),
                mapOf(
                    "playerId" to playerUid,
                    "tableId" to table.id,
                    "cards" to cards,
                    "handPoints" to AmatatuEngine.calculatePoints(cards).toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        if (table.inviteCode.isNotBlank()) {
            batch.update(
                db.collection("invite_codes").document(table.inviteCode),
                mapOf("status" to "ACTIVE", "updatedAt" to FieldValue.serverTimestamp())
            )
        }
        batch.commit().await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "tables/${table.id}") }

    // --- Gameplay Actions & Anti-Cheat ---

    suspend fun playCard(
        table: GameTable,
        hand: PlayerHand,
        card: PlayingCard,
        chosenSuitForAce: Suit?,
        triggerCut: Boolean,
        botHandForInstantCut: List<String>? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        if (table.currentTurnUid != uid) {
            reportAntiCheatAnomaly(
                tableId = table.id,
                reportedUid = uid,
                eventType = "OUT_OF_TURN_ATTEMPT",
                details = "Tried playing ${card.code} when turn belongs to ${table.currentTurnUid}",
                severity = "LOW"
            )
            throw IllegalStateException("Wait for your turn!")
        }

        val playerName = table.playerNames[uid] ?: "Player"
        val moveResult = AmatatuEngine.applyPlayCard(
            playerId = uid,
            playerName = playerName,
            cardCode = card.code,
            chosenSuitForAce = chosenSuitForAce,
            triggerCut = triggerCut,
            hand = hand.cards,
            playerIds = table.playerIds,
            topCardCode = table.topCard,
            demandedSuitName = table.demandedSuit,
            pendingDrawPenalty = table.pendingDrawPenalty,
            turnDirection = table.turnDirection,
            cutSuitName = table.cutSuit,
            drawPile = table.drawPile,
            discardPile = table.discardPile
        )

        if (moveResult is AmatatuEngine.MoveResult.InvalidMove) {
            throw IllegalStateException(moveResult.reason)
        }

        val valid = moveResult as AmatatuEngine.MoveResult.ValidPlay
        val nextSeq = table.moveSequence + 1L
        val tableRef = db.collection("tables").document(table.id)
        val handRef = tableRef.collection("hands").document(uid)
        val moveRef = tableRef.collection("moves").document("seq_$nextSeq")
        val updatedCounts = table.cardCounts.toMutableMap().apply {
            put(uid, valid.newHand.size.toLong())
        }

        val actionType = if (valid.triggersSevenCutShowdown) "CUT_SEVEN" else "PLAY"
        val digest = AmatatuEngine.moveDigest(
            tableId = table.id,
            moveSequence = nextSeq,
            playerId = uid,
            actionType = actionType,
            cardPlayed = card.code,
            topCard = valid.newTopCard
        )

        val batch = db.batch()
        batch.update(
            handRef,
            mapOf(
                "cards" to valid.newHand,
                "handPoints" to AmatatuEngine.calculatePoints(valid.newHand).toLong(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            moveRef,
            mapOf(
                "tableId" to table.id,
                "playerIds" to table.playerIds,
                "moveSequence" to nextSeq,
                "playerId" to uid,
                "playerName" to playerName.take(40),
                "actionType" to actionType,
                "cardPlayed" to card.code,
                "demandedSuit" to valid.newDemandedSuit,
                "drawCount" to 0L,
                "handSizeAfter" to valid.newHand.size.toLong(),
                "antiCheatDigest" to digest,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        when {
            valid.triggersWinEmptyHand -> {
                val revealed = mapOf(uid to valid.newHand)
                batch.update(
                    tableRef,
                    mapOf(
                        "status" to "FINISHED",
                        "cardCounts" to updatedCounts,
                        "turnDirection" to valid.newTurnDirection,
                        "currentTurnUid" to uid,
                        "topCard" to valid.newTopCard,
                        "demandedSuit" to "",
                        "pendingDrawPenalty" to 0L,
                        "drawPile" to valid.newDrawPile,
                        "discardPile" to valid.newDiscardPile,
                        "moveSequence" to nextSeq,
                        "lastActionSummary" to valid.summary,
                        "winnerId" to uid,
                        "winnerName" to playerName,
                        "winReason" to "EMPTY_HAND",
                        "payoutSettled" to false,
                        "revealedHands" to revealed,
                        "revealedPoints" to mapOf(uid to 0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            valid.triggersSevenCutShowdown -> {
                val myPts = AmatatuEngine.calculatePoints(valid.newHand).toLong()
                if (table.isBotMatch && botHandForInstantCut != null) {
                    val botPts = AmatatuEngine.calculatePoints(botHandForInstantCut).toLong()
                    val playerWon = myPts < botPts
                    val winUid = if (playerWon) uid else AmatatuEngine.BOT_UID
                    val winName = if (playerWon) playerName else AmatatuEngine.BOT_NAME
                    batch.update(
                        tableRef,
                        mapOf(
                            "status" to "FINISHED",
                            "cardCounts" to updatedCounts,
                            "turnDirection" to valid.newTurnDirection,
                            "currentTurnUid" to winUid,
                            "topCard" to valid.newTopCard,
                            "demandedSuit" to "",
                            "pendingDrawPenalty" to 0L,
                            "drawPile" to valid.newDrawPile,
                            "discardPile" to valid.newDiscardPile,
                            "moveSequence" to nextSeq,
                            "lastActionSummary" to "7-Cut Showdown! $playerName ($myPts pts) vs Bot ($botPts pts) → $winName wins!",
                            "winnerId" to winUid,
                            "winnerName" to winName,
                            "winReason" to "SEVEN_CUT",
                            "payoutSettled" to false,
                            "revealedHands" to mapOf(uid to valid.newHand, AmatatuEngine.BOT_UID to botHandForInstantCut),
                            "revealedPoints" to mapOf(uid to myPts, AmatatuEngine.BOT_UID to botPts),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.update(
                        tableRef,
                        mapOf(
                            "status" to "SHOWDOWN",
                            "cardCounts" to updatedCounts,
                            "topCard" to valid.newTopCard,
                            "demandedSuit" to "",
                            "pendingDrawPenalty" to 0L,
                            "drawPile" to valid.newDrawPile,
                            "discardPile" to valid.newDiscardPile,
                            "moveSequence" to nextSeq,
                            "lastActionSummary" to valid.summary,
                            "cutCallerId" to uid,
                            "revealedHands" to mapOf(uid to valid.newHand),
                            "revealedPoints" to mapOf(uid to myPts),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            else -> {
                batch.update(
                    tableRef,
                    mapOf(
                        "cardCounts" to updatedCounts,
                        "turnDirection" to valid.newTurnDirection,
                        "currentTurnUid" to valid.nextTurnUid,
                        "topCard" to valid.newTopCard,
                        "demandedSuit" to valid.newDemandedSuit,
                        "pendingDrawPenalty" to valid.newPendingPenalty,
                        "drawPile" to valid.newDrawPile,
                        "discardPile" to valid.newDiscardPile,
                        "moveSequence" to nextSeq,
                        "lastActionSummary" to valid.summary,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }

        batch.commit().await()
        valid.summary
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "tables/${table.id}") }

    suspend fun drawCard(
        table: GameTable,
        hand: PlayerHand
    ): Result<String> = runCatching {
        val uid = requireUserId()
        check(table.currentTurnUid == uid) { "Wait for your turn!" }
        val playerName = table.playerNames[uid] ?: "Player"
        val drawResult = AmatatuEngine.applyDraw(
            playerId = uid,
            playerName = playerName,
            hand = hand.cards,
            playerIds = table.playerIds,
            turnDirection = table.turnDirection,
            pendingDrawPenalty = table.pendingDrawPenalty,
            topCardCode = table.topCard,
            drawPile = table.drawPile,
            discardPile = table.discardPile
        )

        val nextSeq = table.moveSequence + 1L
        val tableRef = db.collection("tables").document(table.id)
        val handRef = tableRef.collection("hands").document(uid)
        val moveRef = tableRef.collection("moves").document("seq_$nextSeq")
        val updatedCounts = table.cardCounts.toMutableMap().apply {
            put(uid, drawResult.newHand.size.toLong())
        }
        val digest = AmatatuEngine.moveDigest(
            tableId = table.id,
            moveSequence = nextSeq,
            playerId = uid,
            actionType = "DRAW",
            cardPlayed = "",
            topCard = table.topCard
        )

        val batch = db.batch()
        batch.update(
            handRef,
            mapOf(
                "cards" to drawResult.newHand,
                "handPoints" to AmatatuEngine.calculatePoints(drawResult.newHand).toLong(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            moveRef,
            mapOf(
                "tableId" to table.id,
                "playerIds" to table.playerIds,
                "moveSequence" to nextSeq,
                "playerId" to uid,
                "playerName" to playerName.take(40),
                "actionType" to "DRAW",
                "cardPlayed" to "",
                "demandedSuit" to "",
                "drawCount" to drawResult.drawnCards.size.toLong(),
                "handSizeAfter" to drawResult.newHand.size.toLong(),
                "antiCheatDigest" to digest,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            tableRef,
            mapOf(
                "cardCounts" to updatedCounts,
                "currentTurnUid" to drawResult.nextTurnUid,
                "pendingDrawPenalty" to 0L,
                "drawPile" to drawResult.newDrawPile,
                "discardPile" to drawResult.newDiscardPile,
                "moveSequence" to nextSeq,
                "lastActionSummary" to drawResult.summary,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        drawResult.summary
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "tables/${table.id}") }

    suspend fun submitShowdownReveal(table: GameTable, hand: PlayerHand): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (table.status != "SHOWDOWN") return@runCatching
        if (table.revealedPoints.containsKey(uid)) return@runCatching

        val tableRef = db.collection("tables").document(table.id)
        db.runTransaction { txn ->
            val snap = txn.get(tableRef)
            val fresh = snap.toObject(GameTable::class.java) ?: return@runTransaction
            if (fresh.status != "SHOWDOWN" || fresh.revealedPoints.containsKey(uid)) return@runTransaction

            val myPts = AmatatuEngine.calculatePoints(hand.cards).toLong()
            val newRevealedHands = fresh.revealedHands + (uid to hand.cards)
            val newRevealedPts = fresh.revealedPoints + (uid to myPts)

            val allRevealed = fresh.playerIds.all { newRevealedPts.containsKey(it) }
            if (allRevealed) {
                val cutterId = fresh.cutCallerId
                val cutterPts = newRevealedPts[cutterId] ?: Long.MAX_VALUE
                val bestOther = newRevealedPts.filterKeys { it != cutterId }.minByOrNull { it.value }
                val winnerUid = if (bestOther != null && cutterPts >= bestOther.value) {
                    bestOther.key
                } else {
                    cutterId
                }
                val winnerName = fresh.playerNames[winnerUid] ?: "Player"
                txn.update(
                    tableRef,
                    mapOf(
                        "status" to "FINISHED",
                        "revealedHands" to newRevealedHands,
                        "revealedPoints" to newRevealedPts,
                        "winnerId" to winnerUid,
                        "winnerName" to winnerName,
                        "winReason" to "SEVEN_CUT",
                        "payoutSettled" to false,
                        "lastActionSummary" to "7-Cut verified! $winnerName wins with ${newRevealedPts[winnerUid]} pts.",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                txn.update(
                    tableRef,
                    mapOf(
                        "revealedHands" to newRevealedHands,
                        "revealedPoints" to newRevealedPts,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }.await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "tables/${table.id}") }

    suspend fun executeBotTurn(table: GameTable, botHand: PlayerHand): Result<String> = runCatching {
        val uid = requireUserId()
        if (!table.isBotMatch || table.status != "ACTIVE" || table.currentTurnUid != AmatatuEngine.BOT_UID) {
            return@runCatching ""
        }

        val (chosenCard, chosenSuit) = AmatatuEngine.chooseBotCard(
            botHand = botHand.cards,
            topCardCode = table.topCard,
            demandedSuitName = table.demandedSuit,
            pendingDrawPenalty = table.pendingDrawPenalty,
            cutSuitName = table.cutSuit
        )

        val tableRef = db.collection("tables").document(table.id)
        val botHandRef = tableRef.collection("hands").document(AmatatuEngine.BOT_UID)
        val nextSeq = table.moveSequence + 1L
        val moveRef = tableRef.collection("moves").document("seq_$nextSeq")
        val batch = db.batch()

        if (chosenCard != null) {
            val triggerCut = AmatatuEngine.canTriggerSevenCut(
                chosenCard, botHand.cards, table.cutSuit, table.pendingDrawPenalty
            ) && AmatatuEngine.calculatePoints(botHand.cards - chosenCard.code) <= 15

            val moveRes = AmatatuEngine.applyPlayCard(
                playerId = AmatatuEngine.BOT_UID,
                playerName = AmatatuEngine.BOT_NAME,
                cardCode = chosenCard.code,
                chosenSuitForAce = chosenSuit,
                triggerCut = triggerCut,
                hand = botHand.cards,
                playerIds = table.playerIds,
                topCardCode = table.topCard,
                demandedSuitName = table.demandedSuit,
                pendingDrawPenalty = table.pendingDrawPenalty,
                turnDirection = table.turnDirection,
                cutSuitName = table.cutSuit,
                drawPile = table.drawPile,
                discardPile = table.discardPile
            ) as AmatatuEngine.MoveResult.ValidPlay

            val counts = table.cardCounts.toMutableMap().apply {
                put(AmatatuEngine.BOT_UID, moveRes.newHand.size.toLong())
            }
            val actionType = if (moveRes.triggersSevenCutShowdown) "CUT_SEVEN" else "PLAY"
            val digest = AmatatuEngine.moveDigest(
                table.id, nextSeq, AmatatuEngine.BOT_UID, actionType, chosenCard.code, moveRes.newTopCard
            )
            batch.update(
                botHandRef,
                mapOf(
                    "cards" to moveRes.newHand,
                    "handPoints" to AmatatuEngine.calculatePoints(moveRes.newHand).toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                moveRef,
                mapOf(
                    "tableId" to table.id,
                    "playerIds" to table.playerIds,
                    "moveSequence" to nextSeq,
                    "playerId" to AmatatuEngine.BOT_UID,
                    "playerName" to AmatatuEngine.BOT_NAME,
                    "actionType" to actionType,
                    "cardPlayed" to chosenCard.code,
                    "demandedSuit" to moveRes.newDemandedSuit,
                    "drawCount" to 0L,
                    "handSizeAfter" to moveRes.newHand.size.toLong(),
                    "antiCheatDigest" to digest,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            if (moveRes.triggersWinEmptyHand) {
                batch.update(
                    tableRef,
                    mapOf(
                        "status" to "FINISHED",
                        "cardCounts" to counts,
                        "currentTurnUid" to AmatatuEngine.BOT_UID,
                        "topCard" to moveRes.newTopCard,
                        "demandedSuit" to "",
                        "pendingDrawPenalty" to 0L,
                        "drawPile" to moveRes.newDrawPile,
                        "discardPile" to moveRes.newDiscardPile,
                        "moveSequence" to nextSeq,
                        "lastActionSummary" to moveRes.summary,
                        "winnerId" to AmatatuEngine.BOT_UID,
                        "winnerName" to AmatatuEngine.BOT_NAME,
                        "winReason" to "EMPTY_HAND",
                        "payoutSettled" to true,
                        "revealedHands" to mapOf(AmatatuEngine.BOT_UID to moveRes.newHand),
                        "revealedPoints" to mapOf(AmatatuEngine.BOT_UID to 0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else if (moveRes.triggersSevenCutShowdown) {
                val playerHandSnap = tableRef.collection("hands").document(uid).get().await()
                val playerCards = playerHandSnap.toObject(PlayerHand::class.java)?.cards.orEmpty()
                val playerPts = AmatatuEngine.calculatePoints(playerCards).toLong()
                val botPts = AmatatuEngine.calculatePoints(moveRes.newHand).toLong()
                val botWins = botPts < playerPts
                val winUid = if (botWins) AmatatuEngine.BOT_UID else uid
                val winName = if (botWins) AmatatuEngine.BOT_NAME else (table.playerNames[uid] ?: "Player")
                batch.update(
                    tableRef,
                    mapOf(
                        "status" to "FINISHED",
                        "cardCounts" to counts,
                        "currentTurnUid" to winUid,
                        "topCard" to moveRes.newTopCard,
                        "demandedSuit" to "",
                        "pendingDrawPenalty" to 0L,
                        "drawPile" to moveRes.newDrawPile,
                        "discardPile" to moveRes.newDiscardPile,
                        "moveSequence" to nextSeq,
                        "lastActionSummary" to "Bot dropped Cut 7! Bot ($botPts pts) vs You ($playerPts pts)",
                        "winnerId" to winUid,
                        "winnerName" to winName,
                        "winReason" to "SEVEN_CUT",
                        "payoutSettled" to (winUid == AmatatuEngine.BOT_UID),
                        "revealedHands" to mapOf(AmatatuEngine.BOT_UID to moveRes.newHand, uid to playerCards),
                        "revealedPoints" to mapOf(AmatatuEngine.BOT_UID to botPts, uid to playerPts),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    tableRef,
                    mapOf(
                        "cardCounts" to counts,
                        "turnDirection" to moveRes.newTurnDirection,
                        "currentTurnUid" to moveRes.nextTurnUid,
                        "topCard" to moveRes.newTopCard,
                        "demandedSuit" to moveRes.newDemandedSuit,
                        "pendingDrawPenalty" to moveRes.newPendingPenalty,
                        "drawPile" to moveRes.newDrawPile,
                        "discardPile" to moveRes.newDiscardPile,
                        "moveSequence" to nextSeq,
                        "lastActionSummary" to moveRes.summary,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            moveRes.summary
        } else {
            val drawRes = AmatatuEngine.applyDraw(
                playerId = AmatatuEngine.BOT_UID,
                playerName = AmatatuEngine.BOT_NAME,
                hand = botHand.cards,
                playerIds = table.playerIds,
                turnDirection = table.turnDirection,
                pendingDrawPenalty = table.pendingDrawPenalty,
                topCardCode = table.topCard,
                drawPile = table.drawPile,
                discardPile = table.discardPile
            )
            val counts = table.cardCounts.toMutableMap().apply {
                put(AmatatuEngine.BOT_UID, drawRes.newHand.size.toLong())
            }
            val digest = AmatatuEngine.moveDigest(
                table.id, nextSeq, AmatatuEngine.BOT_UID, "DRAW", "", table.topCard
            )
            batch.update(
                botHandRef,
                mapOf(
                    "cards" to drawRes.newHand,
                    "handPoints" to AmatatuEngine.calculatePoints(drawRes.newHand).toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                moveRef,
                mapOf(
                    "tableId" to table.id,
                    "playerIds" to table.playerIds,
                    "moveSequence" to nextSeq,
                    "playerId" to AmatatuEngine.BOT_UID,
                    "playerName" to AmatatuEngine.BOT_NAME,
                    "actionType" to "DRAW",
                    "cardPlayed" to "",
                    "demandedSuit" to "",
                    "drawCount" to drawRes.drawnCards.size.toLong(),
                    "handSizeAfter" to drawRes.newHand.size.toLong(),
                    "antiCheatDigest" to digest,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                tableRef,
                mapOf(
                    "cardCounts" to counts,
                    "currentTurnUid" to drawRes.nextTurnUid,
                    "pendingDrawPenalty" to 0L,
                    "drawPile" to drawRes.newDrawPile,
                    "discardPile" to drawRes.newDiscardPile,
                    "moveSequence" to nextSeq,
                    "lastActionSummary" to drawRes.summary,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            drawRes.summary
        }
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "tables/${table.id}") }

    suspend fun cancelWaitingTable(table: GameTable): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(table.status == "WAITING" && table.hostId == uid && table.playerIds.size == 1) {
            "Only sole host in waiting lobby can cancel"
        }
        val userRef = db.collection("users").document(uid)
        val tableRef = db.collection("tables").document(table.id)
        db.runTransaction { txn ->
            val uSnap = txn.get(userRef)
            val balance = uSnap.getLong("walletBalanceUgx") ?: 0L
            val escrow = uSnap.getLong("escrowUgx") ?: 0L
            if (table.stakeUgx > 0L) {
                txn.update(
                    userRef,
                    mapOf(
                        "walletBalanceUgx" to (balance + table.stakeUgx),
                        "escrowUgx" to (escrow - table.stakeUgx).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                txn.set(
                    db.collection("transactions").document(newDocId("ref")),
                    mapOf(
                        "userId" to uid,
                        "userName" to (table.playerNames[uid] ?: "Player"),
                        "type" to "REFUND",
                        "amountUgx" to table.stakeUgx,
                        "feeUgx" to 0L,
                        "netAmountUgx" to table.stakeUgx,
                        "provider" to "WALLET_ESCROW",
                        "phoneNumber" to "",
                        "referenceCode" to generateReference("REF"),
                        "tableId" to table.id,
                        "status" to "COMPLETED",
                        "note" to "Table cancelled: stake refunded",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            txn.update(
                tableRef,
                mapOf(
                    "status" to "CANCELLED",
                    "lastActionSummary" to "Table closed by host",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (table.inviteCode.isNotBlank()) {
                txn.update(
                    db.collection("invite_codes").document(table.inviteCode),
                    mapOf("status" to "CANCELLED", "updatedAt" to FieldValue.serverTimestamp())
                )
            }
        }.await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "tables/${table.id}") }

    suspend fun forfeitMatch(table: GameTable): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(table.status == "ACTIVE" && table.playerIds.contains(uid)) { "Not in active match" }
        val otherUid = table.playerIds.firstOrNull { it != uid } ?: uid
        val otherName = table.playerNames[otherUid] ?: "Opponent"
        val myName = table.playerNames[uid] ?: "Player"

        db.collection("tables").document(table.id).update(
            mapOf(
                "status" to "FINISHED",
                "winnerId" to otherUid,
                "winnerName" to otherName,
                "winReason" to "OPPONENT_FORFEIT",
                "payoutSettled" to false,
                "lastActionSummary" to "$myName forfeited. $otherName wins the pot!",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "tables/${table.id}") }

    /**
     * Settles the user's own match completion once (winner claims pot & payout; loser clears escrow)
     * and writes game_history if not yet created.
     */
    suspend fun settleFinishedMatchForCurrentUser(table: GameTable): Result<Boolean> = runCatching {
        val uid = requireUserId()
        if (table.status != "FINISHED" || !table.playerIds.contains(uid)) return@runCatching false

        val historyId = "hist_${table.id}"
        val historyRef = db.collection("game_history").document(historyId)
        val tableRef = db.collection("tables").document(table.id)
        val userRef = db.collection("users").document(uid)
        val pubRef = db.collection("public_profiles").document(uid)
        val isWinner = table.winnerId == uid

        var didCredit = false
        db.runTransaction { txn ->
            val tSnap = txn.get(tableRef)
            val freshTable = tSnap.toObject(GameTable::class.java) ?: return@runTransaction
            val uSnap = txn.get(userRef)
            val hSnap = txn.get(historyRef)

            val balance = uSnap.getLong("walletBalanceUgx") ?: 0L
            val escrow = uSnap.getLong("escrowUgx") ?: 0L
            val played = uSnap.getLong("gamesPlayed") ?: 0L
            val won = uSnap.getLong("gamesWon") ?: 0L
            val earnings = uSnap.getLong("totalEarningsUgx") ?: 0L
            val ptsWon = uSnap.getLong("totalPointsWon") ?: 0L
            val name = uSnap.getString("displayName") ?: "Player"
            val avatar = uSnap.getString("avatarKey") ?: "crown_gold"

            val alreadySettledUser = freshTable.settledUids.contains(uid)
            val updatedSettled = if (alreadySettledUser) freshTable.settledUids else freshTable.settledUids + uid

            if (isWinner && !freshTable.payoutSettled && !alreadySettledUser) {
                didCredit = true
                val creditUgx = freshTable.payoutUgx
                val newEscrow = (escrow - freshTable.stakeUgx).coerceAtLeast(0L)
                txn.update(
                    tableRef,
                    mapOf(
                        "payoutSettled" to true,
                        "settledUids" to updatedSettled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                txn.update(
                    userRef,
                    mapOf(
                        "walletBalanceUgx" to (balance + creditUgx),
                        "escrowUgx" to newEscrow,
                        "gamesPlayed" to (played + 1L),
                        "gamesWon" to (won + 1L),
                        "totalPointsWon" to (ptsWon + 25L),
                        "totalEarningsUgx" to (earnings + creditUgx),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                txn.update(
                    pubRef,
                    mapOf(
                        "gamesPlayed" to (played + 1L),
                        "gamesWon" to (won + 1L),
                        "totalEarningsUgx" to (earnings + creditUgx),
                        "displayName" to name,
                        "avatarKey" to avatar,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (creditUgx > 0L) {
                    txn.set(
                        db.collection("transactions").document(newDocId("win")),
                        mapOf(
                            "userId" to uid,
                            "userName" to name.take(40),
                            "type" to "WIN_PAYOUT",
                            "amountUgx" to creditUgx,
                            "feeUgx" to (freshTable.potUgx - creditUgx).coerceAtLeast(0L),
                            "netAmountUgx" to creditUgx,
                            "provider" to "SYSTEM",
                            "phoneNumber" to "",
                            "referenceCode" to generateReference("WIN"),
                            "tableId" to freshTable.id,
                            "status" to "COMPLETED",
                            "note" to "Won pot on ${freshTable.title} (${freshTable.winReason})",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            } else if (!isWinner && !alreadySettledUser) {
                val newEscrow = (escrow - freshTable.stakeUgx).coerceAtLeast(0L)
                txn.update(
                    tableRef,
                    mapOf(
                        "settledUids" to updatedSettled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                txn.update(
                    userRef,
                    mapOf(
                        "escrowUgx" to newEscrow,
                        "gamesPlayed" to (played + 1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                txn.update(
                    pubRef,
                    mapOf(
                        "gamesPlayed" to (played + 1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            if (!hSnap.exists()) {
                val feeUgx = (freshTable.potUgx - freshTable.payoutUgx).coerceAtLeast(0L)
                txn.set(
                    historyRef,
                    mapOf(
                        "tableId" to freshTable.id,
                        "tableTitle" to freshTable.title,
                        "stakeUgx" to freshTable.stakeUgx,
                        "potUgx" to freshTable.potUgx,
                        "payoutUgx" to freshTable.payoutUgx,
                        "platformFeeUgx" to feeUgx,
                        "playerIds" to freshTable.playerIds,
                        "playerNames" to freshTable.playerNames,
                        "winnerId" to freshTable.winnerId,
                        "winnerName" to freshTable.winnerName,
                        "winReason" to freshTable.winReason.ifBlank { "EMPTY_HAND" },
                        "finalScores" to freshTable.revealedPoints,
                        "deckCommitHash" to freshTable.deckCommitHash,
                        "moveCount" to freshTable.moveSequence,
                        "antiCheatVerified" to !freshTable.flaggedCheat,
                        "flaggedReason" to freshTable.cheatReason,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }.await()
        didCredit
    }.onFailure { handleFirestoreError(it, OperationType.WRITE, "game_history/${table.id}") }

    // --- Game History & Anti-Cheat ---

    fun observeUserGameHistory(uid: String): Flow<List<GameHistoryRecord>> = flow {
        val path = "game_history"
        emitAll(
            db.collection(path)
                .whereArrayContains("playerIds", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(GameHistoryRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun reportAntiCheatAnomaly(
        tableId: String,
        reportedUid: String,
        eventType: String,
        details: String,
        severity: String = "MEDIUM"
    ): Result<Unit> = runCatching {
        val reporterUid = requireUserId()
        db.collection("anti_cheat_logs").document(newDocId("ac")).set(
            mapOf(
                "tableId" to tableId,
                "reportedUid" to reportedUid,
                "reporterUid" to reporterUid,
                "eventType" to eventType.take(40),
                "details" to details.take(240),
                "severity" to severity,
                "resolved" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.CREATE, "anti_cheat_logs") }

    // --- Admin Dashboard ---

    fun observeAllUsersForAdmin(uid: String): Flow<List<UserAccount>> = flow {
        val path = "users"
        emitAll(
            db.collection(path).snapshots()
                .map { snap ->
                    snap.toObjects(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.walletBalanceUgx }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAllTransactionsForAdmin(uid: String): Flow<List<WalletTransaction>> = flow {
        val path = "transactions"
        emitAll(
            db.collection(path).snapshots()
                .map { snap ->
                    snap.toObjects(WalletTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAllTablesForAdmin(uid: String): Flow<List<GameTable>> = flow {
        val path = "tables"
        emitAll(
            db.collection(path).snapshots()
                .map { snap ->
                    snap.toObjects(GameTable::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    fun observeAntiCheatLogsForAdmin(uid: String): Flow<List<AntiCheatLog>> = flow {
        val path = "anti_cheat_logs"
        emitAll(
            db.collection(path).snapshots()
                .map { snap ->
                    snap.toObjects(AntiCheatLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun adminApproveTransaction(tx: WalletTransaction): Result<Unit> = runCatching {
        requireUserId()
        require(tx.status == "PENDING") { "Transaction already processed" }
        val txRef = db.collection("transactions").document(tx.id)
        val userRef = db.collection("users").document(tx.userId)

        db.runTransaction { txn ->
            val tSnap = txn.get(txRef)
            val status = tSnap.getString("status") ?: ""
            check(status == "PENDING") { "Already processed" }

            if (tx.type == "DEPOSIT") {
                val uSnap = txn.get(userRef)
                val balance = uSnap.getLong("walletBalanceUgx") ?: 0L
                txn.update(
                    userRef,
                    mapOf(
                        "walletBalanceUgx" to (balance + tx.netAmountUgx),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            txn.update(
                txRef,
                mapOf(
                    "status" to "COMPLETED",
                    "note" to "Approved & settled by Admin",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "transactions/${tx.id}") }

    suspend fun adminRejectTransaction(tx: WalletTransaction, reason: String): Result<Unit> = runCatching {
        requireUserId()
        require(tx.status == "PENDING") { "Transaction already processed" }
        val txRef = db.collection("transactions").document(tx.id)
        val userRef = db.collection("users").document(tx.userId)

        db.runTransaction { txn ->
            val tSnap = txn.get(txRef)
            check(tSnap.getString("status") == "PENDING") { "Already processed" }

            if (tx.type == "WITHDRAWAL") {
                val uSnap = txn.get(userRef)
                val balance = uSnap.getLong("walletBalanceUgx") ?: 0L
                txn.update(
                    userRef,
                    mapOf(
                        "walletBalanceUgx" to (balance + tx.amountUgx),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            txn.update(
                txRef,
                mapOf(
                    "status" to "REJECTED",
                    "note" to reason.ifBlank { "Rejected by Admin" }.take(180),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "transactions/${tx.id}") }

    suspend fun adminTogglePlayerBan(targetUid: String, ban: Boolean): Result<Unit> = runCatching {
        requireUserId()
        db.collection("users").document(targetUid).update(
            mapOf(
                "isBanned" to ban,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "users/$targetUid") }

    suspend fun adminResolveCheatLog(logId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("anti_cheat_logs").document(logId).update(
            mapOf("resolved" to true)
        ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "anti_cheat_logs/$logId") }

    suspend fun adminCancelTable(tableId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("tables").document(tableId).update(
            mapOf(
                "status" to "CANCELLED",
                "flaggedCheat" to true,
                "cheatReason" to "Closed by Admin",
                "lastActionSummary" to "Table closed by Admin",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { handleFirestoreError(it, OperationType.UPDATE, "tables/$tableId") }
}
