package com.example.engine

import com.example.model.PlayingCard
import com.example.model.Rank
import com.example.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmatatuEngineTest {

    @Test
    fun dealMatch_dealsSevenCardsPerPlayerAndCreatesCommitHash() {
        val deal = AmatatuEngine.dealMatch("tbl_1", listOf("p1", "p2"))
        assertEquals(7, deal.handsByPlayer["p1"]?.size)
        assertEquals(7, deal.handsByPlayer["p2"]?.size)
        assertEquals(64, deal.deckCommitHash.length)
        assertEquals(52 - 14 - 2, deal.drawPile.size)
    }

    @Test
    fun twoAttack_stacksPickTwoAndBlocksNonDefenseCards() {
        val sevenH = PlayingCard(Rank.SEVEN, Suit.HEARTS)
        val twoD = PlayingCard(Rank.TWO, Suit.DIAMONDS)
        val aceS = PlayingCard(Rank.ACE, Suit.SPADES)
        val hand = listOf("7H", "2D", "AS")

        assertFalse(AmatatuEngine.isCardPlayable(sevenH, "2H", "", 2L, "CLUBS", hand))
        assertTrue(AmatatuEngine.isCardPlayable(twoD, "2H", "", 2L, "CLUBS", hand))
        assertTrue(AmatatuEngine.isCardPlayable(aceS, "2H", "", 2L, "CLUBS", hand))
    }

    @Test
    fun sevenCut_requiresCutSuitAndRemainingHandAtOrBelow25Points() {
        val cutSeven = PlayingCard(Rank.SEVEN, Suit.HEARTS)
        val lowHand = listOf("7H", "3C", "4D", "5S") // remaining = 12 pts <= 25
        val highHand = listOf("7H", "KC", "QD", "AS") // remaining = 40 pts > 25

        assertTrue(AmatatuEngine.canTriggerSevenCut(cutSeven, lowHand, "HEARTS", 0L))
        assertFalse(AmatatuEngine.canTriggerSevenCut(cutSeven, highHand, "HEARTS", 0L))
        assertFalse(AmatatuEngine.canTriggerSevenCut(cutSeven, lowHand, "SPADES", 0L))
    }

    @Test
    fun jackJumpInTwoPlayer_keepsTurnOnSamePlayer() {
        val result = AmatatuEngine.applyPlayCard(
            playerId = "p1",
            playerName = "Alice",
            cardCode = "JH",
            chosenSuitForAce = null,
            triggerCut = false,
            hand = listOf("JH", "4C"),
            playerIds = listOf("p1", "p2"),
            topCardCode = "5H",
            demandedSuitName = "",
            pendingDrawPenalty = 0L,
            turnDirection = 1L,
            cutSuitName = "SPADES",
            drawPile = listOf("9D"),
            discardPile = listOf("5H")
        ) as AmatatuEngine.MoveResult.ValidPlay

        assertEquals("p1", result.nextTurnUid)
    }
}
