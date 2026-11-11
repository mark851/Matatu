package com.example.engine

import com.example.model.PlayingCard
import com.example.model.Rank
import com.example.model.Suit
import java.security.MessageDigest
import kotlin.random.Random

object AmatatuEngine {

    const val BOT_UID = "kampala_bot"
    const val BOT_NAME = "Kampala Master Bot"
    const val BOT_AVATAR = "spade_ace"
    const val CUT_THRESHOLD_POINTS = 25

    data class DealSetup(
        val handsByPlayer: Map<String, List<String>>,
        val topCard: String,
        val cutCard: String,
        val cutSuit: Suit,
        val drawPile: List<String>,
        val discardPile: List<String>,
        val deckCommitHash: String
    )

    sealed class MoveResult {
        data class ValidPlay(
            val newHand: List<String>,
            val newTopCard: String,
            val newDemandedSuit: String,
            val newPendingPenalty: Long,
            val newTurnDirection: Long,
            val nextTurnUid: String,
            val newDrawPile: List<String>,
            val newDiscardPile: List<String>,
            val triggersWinEmptyHand: Boolean,
            val triggersSevenCutShowdown: Boolean,
            val summary: String
        ) : MoveResult()

        data class InvalidMove(val reason: String) : MoveResult()
    }

    data class DrawResult(
        val drawnCards: List<String>,
        val newHand: List<String>,
        val newDrawPile: List<String>,
        val newDiscardPile: List<String>,
        val nextTurnUid: String,
        val exhaustedDeck: Boolean,
        val summary: String
    )

    fun fullDeck(): List<PlayingCard> =
        Suit.entries.flatMap { suit -> Rank.entries.map { rank -> PlayingCard(rank, suit) } }

    fun calculatePoints(cardCodes: List<String>): Int =
        cardCodes.mapNotNull { PlayingCard.parse(it) }.sumOf { it.points }

    fun dealMatch(tableId: String, playerIds: List<String>, random: Random = Random.Default): DealSetup {
        val shuffled = fullDeck().shuffled(random).toMutableList()
        val commitHash = sha256("AMATATU:$tableId:${shuffled.joinToString(",") { it.code }}")

        // Pick a fair non-special starter card for topCard (not 2, A, J, 8)
        val starterIndex = shuffled.indexOfFirst {
            it.rank != Rank.TWO && it.rank != Rank.ACE && it.rank != Rank.JACK && it.rank != Rank.EIGHT
        }.coerceAtLeast(0)
        val starterCard = shuffled.removeAt(starterIndex)

        // Pick bottom cut card establishing the Cut Suit
        val cutCard = shuffled.removeAt(shuffled.lastIndex)

        val hands = linkedMapOf<String, List<String>>()
        for (uid in playerIds) {
            val playerCards = mutableListOf<String>()
            repeat(7) {
                if (shuffled.isNotEmpty()) {
                    playerCards.add(shuffled.removeAt(0).code)
                }
            }
            hands[uid] = playerCards
        }

        return DealSetup(
            handsByPlayer = hands,
            topCard = starterCard.code,
            cutCard = cutCard.code,
            cutSuit = cutCard.suit,
            drawPile = shuffled.map { it.code },
            discardPile = listOf(starterCard.code),
            deckCommitHash = commitHash
        )
    }

    fun effectiveSuit(topCardCode: String, demandedSuitName: String): Suit {
        val demanded = Suit.fromNameOrNull(demandedSuitName)
        if (demanded != null) return demanded
        return PlayingCard.parse(topCardCode)?.suit ?: Suit.SPADES
    }

    fun isCutSevenCard(card: PlayingCard, cutSuitName: String): Boolean {
        val cutSuit = Suit.fromNameOrNull(cutSuitName) ?: return false
        return card.rank == Rank.SEVEN && card.suit == cutSuit
    }

    fun canTriggerSevenCut(
        card: PlayingCard,
        hand: List<String>,
        cutSuitName: String,
        pendingDrawPenalty: Long
    ): Boolean {
        if (pendingDrawPenalty > 0L) return false
        if (!isCutSevenCard(card, cutSuitName)) return false
        val remaining = hand.toMutableList()
        if (!remaining.remove(card.code)) return false
        return calculatePoints(remaining) <= CUT_THRESHOLD_POINTS
    }

    fun isCardPlayable(
        card: PlayingCard,
        topCardCode: String,
        demandedSuitName: String,
        pendingDrawPenalty: Long,
        cutSuitName: String,
        hand: List<String>
    ): Boolean {
        val topCard = PlayingCard.parse(topCardCode) ?: return false

        // Under Pick-2 attack: only another 2 or Ace of Spades (shield) can be played
        if (pendingDrawPenalty > 0L) {
            return card.rank == Rank.TWO || (card.rank == Rank.ACE && card.suit == Suit.SPADES)
        }

        // Valid 7-Cut can be played directly on your turn to end the round
        if (canTriggerSevenCut(card, hand, cutSuitName, pendingDrawPenalty)) {
            return true
        }

        // Ace is wild when not under a 2-attack
        if (card.rank == Rank.ACE) {
            return true
        }

        val activeSuit = effectiveSuit(topCardCode, demandedSuitName)
        return card.suit == activeSuit || card.rank == topCard.rank
    }

    fun applyPlayCard(
        playerId: String,
        playerName: String,
        cardCode: String,
        chosenSuitForAce: Suit?,
        triggerCut: Boolean,
        hand: List<String>,
        playerIds: List<String>,
        topCardCode: String,
        demandedSuitName: String,
        pendingDrawPenalty: Long,
        turnDirection: Long,
        cutSuitName: String,
        drawPile: List<String>,
        discardPile: List<String>
    ): MoveResult {
        val card = PlayingCard.parse(cardCode) ?: return MoveResult.InvalidMove("Unknown card $cardCode")
        if (!hand.contains(cardCode)) {
            return MoveResult.InvalidMove("Card $cardCode is not in your hand")
        }

        if (!isCardPlayable(card, topCardCode, demandedSuitName, pendingDrawPenalty, cutSuitName, hand)) {
            return MoveResult.InvalidMove("${card.displayLabel} does not match suit or rank")
        }

        val newHand = hand.toMutableList().apply { remove(cardCode) }
        val newDiscard = discardPile + cardCode

        // Check 7-Cut Showdown
        val cutAllowed = canTriggerSevenCut(card, hand, cutSuitName, pendingDrawPenalty)
        if (triggerCut && !cutAllowed) {
            val remainingPts = calculatePoints(newHand)
            return MoveResult.InvalidMove("7-Cut requires remaining hand <= $CUT_THRESHOLD_POINTS pts (yours: $remainingPts pts)")
        }
        if (triggerCut && cutAllowed && newHand.isNotEmpty()) {
            return MoveResult.ValidPlay(
                newHand = newHand,
                newTopCard = cardCode,
                newDemandedSuit = "",
                newPendingPenalty = 0L,
                newTurnDirection = turnDirection,
                nextTurnUid = playerId,
                newDrawPile = drawPile,
                newDiscardPile = newDiscard,
                triggersWinEmptyHand = false,
                triggersSevenCutShowdown = true,
                summary = "$playerName dropped the Cut ${card.displayLabel}! Showdown!"
            )
        }

        // Empty hand wins immediately
        if (newHand.isEmpty()) {
            return MoveResult.ValidPlay(
                newHand = emptyList(),
                newTopCard = cardCode,
                newDemandedSuit = "",
                newPendingPenalty = 0L,
                newTurnDirection = turnDirection,
                nextTurnUid = playerId,
                newDrawPile = drawPile,
                newDiscardPile = newDiscard,
                triggersWinEmptyHand = true,
                triggersSevenCutShowdown = false,
                summary = "$playerName shed their final card (${card.displayLabel})!"
            )
        }

        var nextDir = turnDirection
        var nextPenalty = pendingDrawPenalty
        var nextDemandedSuit = ""
        var steps = 1
        var actionText = "$playerName played ${card.displayLabel}"

        when {
            pendingDrawPenalty > 0L && card.rank == Rank.ACE && card.suit == Suit.SPADES -> {
                nextPenalty = 0L
                val demanded = chosenSuitForAce ?: card.suit
                nextDemandedSuit = demanded.name
                actionText = "$playerName blocked Pick-$pendingDrawPenalty with A♠ → ${demanded.symbol}"
            }
            card.rank == Rank.TWO -> {
                nextPenalty = pendingDrawPenalty + 2L
                actionText = "$playerName stacked ${card.displayLabel} (Pick $nextPenalty!)"
            }
            card.rank == Rank.ACE -> {
                val demanded = chosenSuitForAce ?: card.suit
                nextDemandedSuit = demanded.name
                actionText = "$playerName played ${card.displayLabel} → demanded ${demanded.label}"
            }
            card.rank == Rank.JACK || card.rank == Rank.EIGHT -> {
                steps = 2
                actionText = if (playerIds.size == 2) {
                    "$playerName jumped with ${card.displayLabel}! Plays again"
                } else {
                    "$playerName skipped next player with ${card.displayLabel}!"
                }
            }
            card.rank == Rank.KING && playerIds.size >= 3 -> {
                nextDir = -turnDirection
                actionText = "$playerName reversed direction with ${card.displayLabel}!"
            }
        }

        val nextUid = advanceTurn(playerId, playerIds, nextDir, steps)

        return MoveResult.ValidPlay(
            newHand = newHand,
            newTopCard = cardCode,
            newDemandedSuit = nextDemandedSuit,
            newPendingPenalty = nextPenalty,
            newTurnDirection = nextDir,
            nextTurnUid = nextUid,
            newDrawPile = drawPile,
            newDiscardPile = newDiscard,
            triggersWinEmptyHand = false,
            triggersSevenCutShowdown = false,
            summary = actionText
        )
    }

    fun applyDraw(
        playerId: String,
        playerName: String,
        hand: List<String>,
        playerIds: List<String>,
        turnDirection: Long,
        pendingDrawPenalty: Long,
        topCardCode: String,
        drawPile: List<String>,
        discardPile: List<String>,
        random: Random = Random.Default
    ): DrawResult {
        val countToDraw = if (pendingDrawPenalty > 0L) pendingDrawPenalty.toInt() else 1
        val mutableDraw = drawPile.toMutableList()
        var mutableDiscard = discardPile.toMutableList()
        val drawn = mutableListOf<String>()

        repeat(countToDraw) {
            if (mutableDraw.isEmpty() && mutableDiscard.size > 1) {
                val keepTop = mutableDiscard.lastOrNull() ?: topCardCode
                val recyclable = mutableDiscard.dropLast(1).shuffled(random)
                mutableDraw.addAll(recyclable)
                mutableDiscard = mutableListOf(keepTop)
            }
            if (mutableDraw.isNotEmpty()) {
                drawn.add(mutableDraw.removeAt(0))
            }
        }

        val newHand = hand + drawn
        val nextUid = advanceTurn(playerId, playerIds, turnDirection, steps = 1)
        val exhausted = drawn.isEmpty() && mutableDraw.isEmpty() && mutableDiscard.size <= 1
        val summary = if (pendingDrawPenalty > 0L) {
            "$playerName drew ${drawn.size} penalty cards"
        } else {
            "$playerName drew ${drawn.size} card"
        }

        return DrawResult(
            drawnCards = drawn,
            newHand = newHand,
            newDrawPile = mutableDraw,
            newDiscardPile = mutableDiscard,
            nextTurnUid = nextUid,
            exhaustedDeck = exhausted,
            summary = summary
        )
    }

    fun advanceTurn(
        currentUid: String,
        playerIds: List<String>,
        direction: Long,
        steps: Int
    ): String {
        if (playerIds.isEmpty()) return currentUid
        val idx = playerIds.indexOf(currentUid).coerceAtLeast(0)
        val size = playerIds.size
        val stepDelta = if (direction >= 0) steps else -steps
        val nextIdx = ((idx + stepDelta) % size + size) % size
        return playerIds[nextIdx]
    }

    /**
     * Smart tactical bot choice:
     * 1. Shed final card immediately if possible.
     * 2. Trigger 7-Cut if strictly winning/low points (<= 15 remaining pts).
     * 3. Defend/counter 2-attacks with 2 or A♠.
     * 4. Play Jumps (J/8) to chain turns, or highest point non-Ace card to reduce hand risk.
     */
    fun chooseBotCard(
        botHand: List<String>,
        topCardCode: String,
        demandedSuitName: String,
        pendingDrawPenalty: Long,
        cutSuitName: String
    ): Pair<PlayingCard?, Suit?> {
        val parsed = botHand.mapNotNull { PlayingCard.parse(it) }
        val playable = parsed.filter {
            isCardPlayable(it, topCardCode, demandedSuitName, pendingDrawPenalty, cutSuitName, botHand)
        }
        if (playable.isEmpty()) return null to null

        // If 1 card left, play it immediately
        if (botHand.size == 1) return playable.first() to mostCommonSuit(parsed, playable.first())

        // If holding Cut 7 and remaining <= 15 pts, chop!
        val cut7 = playable.firstOrNull {
            canTriggerSevenCut(it, botHand, cutSuitName, pendingDrawPenalty) &&
                calculatePoints(botHand - it.code) <= 15
        }
        if (cut7 != null) return cut7 to null

        // Prefer Jump (J or 8) or Attack (2) or highest point non-Ace card
        val nonAce = playable.filter { it.rank != Rank.ACE }
        val chosen = nonAce.firstOrNull { it.rank == Rank.JACK || it.rank == Rank.EIGHT }
            ?: nonAce.firstOrNull { it.rank == Rank.TWO }
            ?: nonAce.maxByOrNull { it.points }
            ?: playable.first()

        val suitChoice = if (chosen.rank == Rank.ACE) mostCommonSuit(parsed, chosen) else null
        return chosen to suitChoice
    }

    private fun mostCommonSuit(hand: List<PlayingCard>, excluding: PlayingCard): Suit {
        val remaining = hand.filterNot { it.code == excluding.code }
        return remaining.groupingBy { it.suit }.eachCount().maxByOrNull { it.value }?.key ?: Suit.HEARTS
    }

    fun moveDigest(
        tableId: String,
        moveSequence: Long,
        playerId: String,
        actionType: String,
        cardPlayed: String,
        topCard: String
    ): String = sha256("$tableId:$moveSequence:$playerId:$actionType:$cardPlayed:$topCard").take(24)

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
