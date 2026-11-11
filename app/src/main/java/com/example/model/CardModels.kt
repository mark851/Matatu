package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class Suit(val symbol: String, val label: String, val code: String, val isRed: Boolean) {
    HEARTS("♥", "Hearts", "H", true),
    DIAMONDS("♦", "Diamonds", "D", true),
    CLUBS("♣", "Clubs", "C", false),
    SPADES("♠", "Spades", "S", false);

    companion object {
        fun fromCode(code: String): Suit = entries.firstOrNull { it.code == code } ?: SPADES
        fun fromNameOrNull(name: String): Suit? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

enum class Rank(val symbol: String, val points: Int, val code: String) {
    ACE("A", 15, "A"),
    TWO("2", 20, "2"),
    THREE("3", 3, "3"),
    FOUR("4", 4, "4"),
    FIVE("5", 5, "5"),
    SIX("6", 6, "6"),
    SEVEN("7", 7, "7"),
    EIGHT("8", 8, "8"),
    NINE("9", 9, "9"),
    TEN("10", 10, "10"),
    JACK("J", 11, "J"),
    QUEEN("Q", 12, "Q"),
    KING("K", 13, "K");

    companion object {
        fun fromCode(code: String): Rank = entries.firstOrNull { it.code == code } ?: TWO
    }
}

data class PlayingCard(
    val rank: Rank,
    val suit: Suit
) {
    val code: String get() = "${rank.code}${suit.code}"
    val points: Int get() = rank.points
    val displayLabel: String get() = "${rank.symbol}${suit.symbol}"

    companion object {
        fun parse(code: String): PlayingCard? {
            if (code.length < 2) return null
            val suitCode = code.takeLast(1)
            val rankCode = code.dropLast(1)
            val suit = Suit.entries.firstOrNull { it.code == suitCode } ?: return null
            val rank = Rank.entries.firstOrNull { it.code == rankCode } ?: return null
            return PlayingCard(rank, suit)
        }
    }
}

data class StakeTier(
    val id: String,
    val label: String,
    val stakeUgx: Long,
    val tag: String,
    val description: String
)

object StakeTiers {
    val ALL = listOf(
        StakeTier("practice", "Practice Felt", 0L, "FREE", "Warm up vs Kampala Bot"),
        StakeTier("boda", "Boda Table", 1000L, "CASUAL", "Entry UGX 1,000 • Winner takes 1,900"),
        StakeTier("nakasero", "Nakasero Lounge", 2500L, "POPULAR", "Entry UGX 2,500 • Winner takes 4,750"),
        StakeTier("kololo", "Kololo Club", 5000L, "COMPETITIVE", "Entry UGX 5,000 • Winner takes 9,500"),
        StakeTier("serena", "Serena Suite", 10000L, "HIGH ROLLER", "Entry UGX 10,000 • Winner takes 19,000"),
        StakeTier("vip", "Kampala Crown VIP", 25000L, "VIP", "Entry UGX 25,000 • Winner takes 47,500"),
        StakeTier("royal", "Royal Pot", 50000L, "ELITE", "Entry UGX 50,000 • Winner takes 95,000")
    )
}

object AvatarPresets {
    val OPTIONS = listOf(
        "crown_gold" to "Royal Crown",
        "spade_ace" to "Ace of Spades",
        "seven_chop" to "Lucky Seven",
        "lion_crest" to "Kampala Crest",
        "diamond_vip" to "Diamond VIP",
        "emerald_shield" to "Emerald Shield"
    )
}

@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val preferredProvider: String = "MTN_MOMO",
    val avatarKey: String = "crown_gold",
    val photoUrl: String? = null,
    val walletBalanceUgx: Long = 25000L,
    val escrowUgx: Long = 0L,
    val gamesPlayed: Long = 0L,
    val gamesWon: Long = 0L,
    val totalPointsWon: Long = 0L,
    val totalEarningsUgx: Long = 0L,
    val isBanned: Boolean = false,
    val soundEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val winRatePercent: Int
        get() = if (gamesPlayed > 0) ((gamesWon * 100) / gamesPlayed).toInt() else 0
}

@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val avatarKey: String = "crown_gold",
    val gamesPlayed: Long = 0L,
    val gamesWon: Long = 0L,
    val totalEarningsUgx: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class InviteCode(
    @DocumentId val id: String = "",
    val code: String = "",
    val tableId: String = "",
    val hostId: String = "",
    val stakeUgx: Long = 0L,
    val maxPlayers: Long = 2L,
    val status: String = "WAITING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GameTable(
    @DocumentId val id: String = "",
    val title: String = "",
    val hostId: String = "",
    val stakeUgx: Long = 0L,
    val platformFeePct: Long = 5L,
    val potUgx: Long = 0L,
    val payoutUgx: Long = 0L,
    val maxPlayers: Long = 2L,
    val isPrivate: Boolean = false,
    val inviteCode: String = "",
    val isBotMatch: Boolean = false,
    val status: String = "WAITING",
    val playerIds: List<String> = emptyList(),
    val playerNames: Map<String, String> = emptyMap(),
    val playerAvatars: Map<String, String> = emptyMap(),
    val cardCounts: Map<String, Long> = emptyMap(),
    val turnDirection: Long = 1L,
    val currentTurnUid: String = "",
    val cutSuit: String = "",
    val cutCard: String = "",
    val topCard: String = "",
    val demandedSuit: String = "",
    val pendingDrawPenalty: Long = 0L,
    val drawPile: List<String> = emptyList(),
    val discardPile: List<String> = emptyList(),
    val moveSequence: Long = 0L,
    val lastActionSummary: String = "",
    val deckCommitHash: String = "",
    val cutCallerId: String = "",
    val winnerId: String = "",
    val winnerName: String = "",
    val winReason: String = "",
    val payoutSettled: Boolean = false,
    val flaggedCheat: Boolean = false,
    val cheatReason: String = "",
    val revealedHands: Map<String, List<String>> = emptyMap(),
    val revealedPoints: Map<String, Long> = emptyMap(),
    val settledUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PlayerHand(
    @DocumentId val id: String = "",
    val playerId: String = "",
    val tableId: String = "",
    val cards: List<String> = emptyList(),
    val handPoints: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MoveAudit(
    @DocumentId val id: String = "",
    val tableId: String = "",
    val playerIds: List<String> = emptyList(),
    val moveSequence: Long = 1L,
    val playerId: String = "",
    val playerName: String = "",
    val actionType: String = "PLAY",
    val cardPlayed: String = "",
    val demandedSuit: String = "",
    val drawCount: Long = 0L,
    val handSizeAfter: Long = 0L,
    val antiCheatDigest: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WalletTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val type: String = "DEPOSIT",
    val amountUgx: Long = 0L,
    val feeUgx: Long = 0L,
    val netAmountUgx: Long = 0L,
    val provider: String = "MTN_MOMO",
    val phoneNumber: String = "",
    val referenceCode: String = "",
    val tableId: String = "",
    val status: String = "PENDING",
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GameHistoryRecord(
    @DocumentId val id: String = "",
    val tableId: String = "",
    val tableTitle: String = "",
    val stakeUgx: Long = 0L,
    val potUgx: Long = 0L,
    val payoutUgx: Long = 0L,
    val platformFeeUgx: Long = 0L,
    val playerIds: List<String> = emptyList(),
    val playerNames: Map<String, String> = emptyMap(),
    val winnerId: String = "",
    val winnerName: String = "",
    val winReason: String = "",
    val finalScores: Map<String, Long> = emptyMap(),
    val deckCommitHash: String = "",
    val moveCount: Long = 0L,
    val antiCheatVerified: Boolean = true,
    val flaggedReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AntiCheatLog(
    @DocumentId val id: String = "",
    val tableId: String = "",
    val reportedUid: String = "",
    val reporterUid: String = "",
    val eventType: String = "",
    val details: String = "",
    val severity: String = "MEDIUM",
    val resolved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)
