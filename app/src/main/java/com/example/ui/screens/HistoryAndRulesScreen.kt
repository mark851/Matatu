package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.GameHistoryRecord
import com.example.model.PlayingCard
import com.example.model.Rank
import com.example.model.Suit
import com.example.model.UserAccount
import com.example.ui.UiState
import com.example.ui.components.PlayingCardFace
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatUgx
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldAbyss
import com.example.ui.theme.EmeraldFelt
import com.example.ui.theme.JadeSuccess
import com.example.ui.theme.KampalaGold
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MutedSage
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WarmOffWhite

@Composable
fun HistoryAndRulesScreen(
    user: UserAccount,
    historyState: UiState<List<GameHistoryRecord>>,
    initialTab: Int = 0
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(
                0 to ("Match History" to Icons.Filled.History),
                1 to ("Amatatu Rules" to Icons.AutoMirrored.Filled.MenuBook)
            ).forEach { (idx, meta) ->
                val active = selectedTab == idx
                Surface(
                    color = if (active) KampalaGold else SurfaceCard,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (active) KampalaGold else SubtleBorder),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { selectedTab = idx }
                        .testTag(if (idx == 0) "tab_game_history" else "tab_amatatu_rules")
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = meta.second,
                            contentDescription = null,
                            tint = if (active) EmeraldAbyss else WarmOffWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = meta.first,
                            color = if (active) EmeraldAbyss else WarmOffWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (selectedTab == 0) {
            MatchHistoryList(user = user, historyState = historyState)
        } else {
            AmatatuRulesGuide()
        }
    }
}

@Composable
private fun MatchHistoryList(
    user: UserAccount,
    historyState: UiState<List<GameHistoryRecord>>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatChipCard("Matches", "${user.gamesPlayed}", Modifier.weight(1f))
                StatChipCard("Wins", "${user.gamesWon}", Modifier.weight(1f))
                StatChipCard("Win Rate", "${user.winRatePercent}%", Modifier.weight(1f))
            }
        }

        when (historyState) {
            is UiState.Loading -> item {
                Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KampalaGold)
                }
            }
            is UiState.Error -> item {
                Text(historyState.message, color = CrimsonAlert, style = MaterialTheme.typography.bodyMedium)
            }
            is UiState.Success -> {
                if (historyState.data.isEmpty()) {
                    item {
                        Surface(
                            color = SurfaceCard,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.History, contentDescription = null, tint = MutedSage, modifier = Modifier.size(34.dp))
                                Text("No Completed Matches Yet", style = MaterialTheme.typography.titleMedium, color = WarmOffWhite)
                                Text(
                                    "Your match winners, 7-Cut showdowns, UGX payouts, and anti-cheat verification hashes appear here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSage
                                )
                            }
                        }
                    }
                } else {
                    items(historyState.data, key = { it.id }) { record ->
                        val iWon = record.winnerId == user.userId
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            border = BorderStroke(1.dp, if (iWon) KampalaGold.copy(alpha = 0.55f) else SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(record.tableTitle, style = MaterialTheme.typography.titleMedium, color = WarmOffWhite)
                                    StatusPill(
                                        text = if (iWon) "WON" else "LOST",
                                        containerColor = if (iWon) JadeSuccess else MutedSage,
                                        contentColor = if (iWon) JadeSuccess else MutedSage
                                    )
                                }
                                Text(
                                    text = "Winner: ${record.winnerName} • ${record.winReason.replace("_", " ")}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = KampalaGold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (record.stakeUgx == 0L) "Free Practice" else "Stake ${formatUgx(record.stakeUgx)} • Payout ${formatUgx(record.payoutUgx)}",
                                        fontFamily = MonoFontFamily,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = WarmOffWhite
                                    )
                                    Text(
                                        text = "${record.moveCount} moves",
                                        fontFamily = MonoFontFamily,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MutedSage
                                    )
                                }
                                if (record.finalScores.isNotEmpty()) {
                                    val scoreLine = record.finalScores.entries.joinToString(" • ") { (pid, pts) ->
                                        "${record.playerNames[pid] ?: "Player"}: ${pts}p"
                                    }
                                    Text("Scores: $scoreLine", style = MaterialTheme.typography.bodyMedium, color = MutedSage)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        Icons.Filled.VerifiedUser,
                                        contentDescription = null,
                                        tint = if (record.antiCheatVerified) JadeSuccess else AmberWarning,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (record.antiCheatVerified) "Deck Hash Verified (${record.deckCommitHash.take(10)}...)" else "Flagged: ${record.flaggedReason}",
                                        fontFamily = MonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (record.antiCheatVerified) JadeSuccess else AmberWarning
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChipCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = SurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MutedSage)
            Text(value, fontFamily = MonoFontFamily, style = MaterialTheme.typography.titleLarge, color = KampalaGold)
        }
    }
}

@Composable
private fun AmatatuRulesGuide() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Official Ugandan Amatatu Rules",
                subtitle = "Master Kampala's favorite card game — 7 cards, 7-Cut Showdown & Pick-Two stacks"
            )
        }

        item {
            RuleCard(
                title = "1. The Deal & Cut Suit",
                body = "Played with a standard 52-card deck for 2 to 4 players. Every player receives 7 cards. One card is placed face-up as the Discard starter, and one card establishes the table's Cut Suit (Chopper Suit)."
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, KampalaGold.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("2. Special Power Cards", style = MaterialTheme.typography.titleLarge, color = KampalaGold)

                    SpecialCardRuleRow(
                        card = PlayingCard(Rank.TWO, Suit.HEARTS),
                        title = "2 — Pick Two (Stackable Attack)",
                        desc = "Next player must draw 2 cards — or defend by stacking another 2 (+4, +6, +8...) or blocking with Ace of Spades!"
                    )
                    SpecialCardRuleRow(
                        card = PlayingCard(Rank.ACE, Suit.SPADES),
                        title = "A♠ — Wild Suit & Attack Shield",
                        desc = "Any Ace can be played anytime to demand a new suit. Specifically A♠ also neutralizes a Pick-Two stack!"
                    )
                    SpecialCardRuleRow(
                        card = PlayingCard(Rank.JACK, Suit.CLUBS),
                        title = "J or 8 — Jump / Skip Turn",
                        desc = "Skips the next challenger. In 2-player tables, jumping gives you an immediate extra turn!"
                    )
                    SpecialCardRuleRow(
                        card = PlayingCard(Rank.KING, Suit.DIAMONDS),
                        title = "K — Reverse Turn Direction",
                        desc = "Reverses clockwise / counter-clockwise play order on 3–4 player tables."
                    )
                    SpecialCardRuleRow(
                        card = PlayingCard(Rank.SEVEN, Suit.HEARTS),
                        isCut = true,
                        title = "7 of Cut Suit — The Chopper (7-Cut)",
                        desc = "If the sum of your REMAINING cards is 25 points or less, dropping the 7 of the table's Cut Suit freezes the round! Every player reveals their hand — lowest total points wins the pot!"
                    )
                }
            }
        }

        item {
            RuleCard(
                title = "3. Card Point Values (for 7-Cut Showdown)",
                body = "• 2 = 20 pts\n• Ace (A) = 15 pts\n• King (K) = 13 pts\n• Queen (Q) = 12 pts\n• Jack (J) = 11 pts\n• 3 through 10 = Face value (3 to 10 pts)"
            )
        }

        item {
            RuleCard(
                title = "4. UGX Pots & Fair Play Anti-Cheat",
                body = "Entry stakes lock safely into Escrow when joining a table. Upon winning, 95% of the total pot is credited automatically to the winner's UGX wallet (5% parlour fee). Hands are isolated per player UID with a SHA-256 deck commit hash."
            )
        }
    }
}

@Composable
private fun RuleCard(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = KampalaGold)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = WarmOffWhite)
        }
    }
}

@Composable
private fun SpecialCardRuleRow(
    card: PlayingCard,
    isCut: Boolean = false,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayingCardFace(card = card, isCutSeven = isCut, width = 56.dp, height = 80.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = WarmOffWhite)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MutedSage)
        }
    }
}
