package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.AmatatuEngine
import com.example.model.GameTable
import com.example.model.MoveAudit
import com.example.model.PlayingCard
import com.example.model.Rank
import com.example.model.Suit
import com.example.model.UserAccount
import com.example.ui.ActiveTableState
import com.example.ui.components.AvatarCrest
import com.example.ui.components.PlayingCardBack
import com.example.ui.components.PlayingCardFace
import com.example.ui.components.StatusPill
import com.example.ui.components.SuitBadge
import com.example.ui.components.formatUgx
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardInkCrimson
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldAbyss
import com.example.ui.theme.EmeraldFelt
import com.example.ui.theme.EmeraldFeltLight
import com.example.ui.theme.GoldShimmer
import com.example.ui.theme.JadeSuccess
import com.example.ui.theme.KampalaGold
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MutedSage
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.WarmOffWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameTableScreen(
    tableState: ActiveTableState,
    user: UserAccount,
    isBusy: Boolean,
    onPlayCard: (PlayingCard, Suit?, Boolean) -> Unit,
    onDrawCard: () -> Unit,
    onStartTable: (GameTable) -> Unit,
    onCancelTable: (GameTable) -> Unit,
    onForfeitMatch: (GameTable) -> Unit,
    onReportAnomaly: (String, String, String) -> Unit,
    onToggleSound: () -> Unit,
    onBackToLobby: () -> Unit
) {
    val table = tableState.table
    val myHand = tableState.myHand
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var aceCardPendingSuit by remember { mutableStateOf<PlayingCard?>(null) }
    var cutCardPendingDecision by remember { mutableStateOf<PlayingCard?>(null) }
    var showAuditSheet by remember { mutableStateOf(false) }
    var showForfeitConfirm by remember { mutableStateOf(false) }
    var copiedCode by remember { mutableStateOf(false) }

    val isLiveGame = table?.status == "ACTIVE" || table?.status == "SHOWDOWN"

    BackHandler {
        if (isLiveGame) {
            showForfeitConfirm = true
        } else {
            onBackToLobby()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(EmeraldAbyss)) {
        Image(
            painter = painterResource(id = R.drawable.img_felt_pattern),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.32f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            EmeraldAbyss.copy(alpha = 0.86f),
                            EmeraldFelt.copy(alpha = 0.52f),
                            EmeraldAbyss.copy(alpha = 0.94f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Table Header Bar
            Surface(
                color = EmeraldAbyss.copy(alpha = 0.84f),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                if (isLiveGame) showForfeitConfirm = true else onBackToLobby()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("table_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to lobby",
                                tint = WarmOffWhite
                            )
                        }
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(
                                text = table?.title ?: "Loading Table...",
                                style = MaterialTheme.typography.titleMedium,
                                color = WarmOffWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (table != null) {
                                Text(
                                    text = if (table.stakeUgx == 0L) "Practice Felt" else "Pot ${formatUgx(table.potUgx)} • Payout ${formatUgx(table.payoutUgx)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = KampalaGold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleSound,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("table_sound_toggle")
                        ) {
                            Icon(
                                imageVector = if (user.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = if (user.soundEnabled) "Mute sound effects" else "Enable sound effects",
                                tint = if (user.soundEnabled) KampalaGold else MutedSage
                            )
                        }
                        IconButton(
                            onClick = { showAuditSheet = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("open_audit_sheet_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "Anti-cheat ledger and move audit",
                                tint = JadeSuccess
                            )
                        }
                    }
                }
            }

            if (table == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = KampalaGold)
                        Text("Syncing table state...", color = MutedSage, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                when (table.status) {
                    "WAITING" -> WaitingRoomContent(
                        table = table,
                        currentUserId = user.userId,
                        isBusy = isBusy,
                        copiedCode = copiedCode,
                        onCopyInviteCode = { code ->
                            clipboard.setText(AnnotatedString(code))
                            copiedCode = true
                        },
                        onShareInviteCode = { code ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Join my Amatatu table \"${table.title}\" (${if (table.stakeUgx == 0L) "Free" else formatUgx(table.stakeUgx)}) with Invite Code: $code"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Room Code"))
                        },
                        onStartTable = { onStartTable(table) },
                        onCancelTable = { onCancelTable(table) }
                    )

                    "FINISHED", "CANCELLED" -> FinishedMatchContent(
                        table = table,
                        currentUserId = user.userId,
                        onBackToLobby = onBackToLobby
                    )

                    else -> ActivePlayTableContent(
                        table = table,
                        myCards = myHand?.cards.orEmpty(),
                        currentUserId = user.userId,
                        isBusy = isBusy,
                        onCardTapped = { card ->
                            val isCutReady = AmatatuEngine.canTriggerSevenCut(
                                card = card,
                                hand = myHand?.cards.orEmpty(),
                                cutSuitName = table.cutSuit,
                                pendingDrawPenalty = table.pendingDrawPenalty
                            )
                            when {
                                isCutReady && (myHand?.cards?.size ?: 0) > 1 -> cutCardPendingDecision = card
                                card.rank == Rank.ACE -> aceCardPendingSuit = card
                                else -> onPlayCard(card, null, false)
                            }
                        },
                        onQuickSevenCut = { card ->
                            onPlayCard(card, null, true)
                        },
                        onDrawCard = onDrawCard,
                        onForfeitClick = { showForfeitConfirm = true }
                    )
                }
            }
        }
    }

    if (aceCardPendingSuit != null) {
        val aceCard = aceCardPendingSuit!!
        AlertDialog(
            onDismissRequest = { aceCardPendingSuit = null },
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = if ((table?.pendingDrawPenalty ?: 0L) > 0L) "Shield Active! Choose New Suit" else "Ace Wild — Demand a Suit",
                    color = WarmOffWhite
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Select the suit that opponents must match after ${aceCard.displayLabel}:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSage
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Suit.entries.forEach { suit ->
                            val suitColor = if (suit.isRed) Color(0xFFFF7A8A) else GoldShimmer
                            Surface(
                                color = EmeraldFeltLight,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, suitColor.copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        aceCardPendingSuit = null
                                        onPlayCard(aceCard, suit, false)
                                    }
                                    .testTag("ace_suit_${suit.code}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(suit.symbol, fontSize = 24.sp, color = suitColor)
                                    Text(suit.label, style = MaterialTheme.typography.labelSmall, color = WarmOffWhite, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { aceCardPendingSuit = null }) {
                    Text("Cancel", color = MutedSage)
                }
            }
        )
    }

    if (cutCardPendingDecision != null && table != null && myHand != null) {
        val cutCard = cutCardPendingDecision!!
        val remainingPts = AmatatuEngine.calculatePoints(myHand.cards - cutCard.code)
        AlertDialog(
            onDismissRequest = { cutCardPendingDecision = null },
            containerColor = SurfaceCard,
            title = { Text("Drop Cut ${cutCard.displayLabel}?", color = KampalaGold) },
            text = {
                Text(
                    "Your remaining hand is $remainingPts pts (under the 25 pt limit). Dropping the 7-Cut freezes the table for an immediate lowest-points showdown!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WarmOffWhite
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        cutCardPendingDecision = null
                        onPlayCard(cutCard, null, true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KampalaGold, contentColor = EmeraldAbyss),
                    modifier = Modifier.testTag("confirm_seven_cut_button")
                ) {
                    Text("Drop 7-Cut Showdown", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        cutCardPendingDecision = null
                        onPlayCard(cutCard, null, false)
                    }) {
                        Text("Play Regular 7", color = WarmOffWhite)
                    }
                    TextButton(onClick = { cutCardPendingDecision = null }) {
                        Text("Cancel", color = MutedSage)
                    }
                }
            }
        )
    }

    if (showForfeitConfirm && table != null) {
        AlertDialog(
            onDismissRequest = { showForfeitConfirm = false },
            containerColor = SurfaceCard,
            title = { Text("Leave Active Match?", color = WarmOffWhite) },
            text = {
                Text(
                    "Forfeiting awards the table pot (${if (table.stakeUgx == 0L) "Free" else formatUgx(table.payoutUgx)}) to your opponent. You can also just step back to the Lobby and resume later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSage
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showForfeitConfirm = false
                        onForfeitMatch(table)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert, contentColor = WarmOffWhite),
                    modifier = Modifier.testTag("confirm_forfeit_button")
                ) {
                    Text("Forfeit Match")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showForfeitConfirm = false
                        onBackToLobby()
                    }) {
                        Text("Stay Seated (Lobby)", color = KampalaGold)
                    }
                    TextButton(onClick = { showForfeitConfirm = false }) {
                        Text("Resume Play", color = MutedSage)
                    }
                }
            }
        )
    }

    if (showAuditSheet && table != null) {
        AntiCheatAuditSheet(
            table = table,
            moves = tableState.moves,
            currentUserId = user.userId,
            onReport = { reportedUid, details ->
                onReportAnomaly(table.id, reportedUid, details)
            },
            onDismiss = { showAuditSheet = false }
        )
    }
}

@Composable
private fun WaitingRoomContent(
    table: GameTable,
    currentUserId: String,
    isBusy: Boolean,
    copiedCode: Boolean,
    onCopyInviteCode: (String) -> Unit,
    onShareInviteCode: (String) -> Unit,
    onStartTable: () -> Unit,
    onCancelTable: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.94f)),
            border = BorderStroke(1.dp, KampalaGold.copy(alpha = 0.45f)),
            modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill("WAITING FOR PLAYERS", AmberWarning, AmberWarning)
                    Text(
                        text = "${table.playerIds.size}/${table.maxPlayers} Seated",
                        style = MaterialTheme.typography.labelLarge,
                        color = KampalaGold
                    )
                }

                Text(
                    text = table.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = WarmOffWhite
                )

                Text(
                    text = if (table.stakeUgx == 0L) "Free Practice Room" else "Entry Stake: ${formatUgx(table.stakeUgx)} per player • Winner Pot (95%): ${formatUgx(table.stakeUgx * table.maxPlayers * 95L / 100L)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSage
                )

                if (table.inviteCode.isNotBlank()) {
                    Surface(
                        color = EmeraldFelt,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, KampalaGold.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Private Room Invitation Code", style = MaterialTheme.typography.labelMedium, color = MutedSage)
                            Text(
                                text = table.inviteCode,
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                color = KampalaGold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { onCopyInviteCode(table.inviteCode) },
                                    border = BorderStroke(1.dp, KampalaGold.copy(alpha = 0.6f)),
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("copy_invite_code_button")
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = KampalaGold, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (copiedCode) "Copied!" else "Copy Code", color = KampalaGold)
                                }
                                FilledTonalButton(
                                    onClick = { onShareInviteCode(table.inviteCode) },
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = EmeraldFeltLight, contentColor = WarmOffWhite),
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("share_invite_code_button")
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Invite Friend")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = SubtleBorder)
                Text("Seated Challengers", style = MaterialTheme.typography.titleMedium, color = WarmOffWhite)

                for (seat in 0 until table.maxPlayers.toInt()) {
                    val uid = table.playerIds.getOrNull(seat)
                    if (uid != null) {
                        val name = table.playerNames[uid] ?: "Player"
                        val avatar = table.playerAvatars[uid] ?: "crown_gold"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(EmeraldFelt)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AvatarCrest(avatar, size = 38.dp, highlightGold = uid == table.hostId)
                                Column {
                                    Text(name, style = MaterialTheme.typography.titleMedium, color = WarmOffWhite)
                                    Text(
                                        text = if (uid == table.hostId) "Room Host" else "Ready to Deal",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MutedSage
                                    )
                                }
                            }
                            StatusPill("SEATED", JadeSuccess, JadeSuccess)
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(EmeraldAbyss.copy(alpha = 0.5f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Open Seat #${seat + 1} — waiting for challenger...", style = MaterialTheme.typography.bodyMedium, color = MutedSage)
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = KampalaGold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (table.playerIds.size >= 2) {
                    Button(
                        onClick = onStartTable,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = KampalaGold, contentColor = EmeraldAbyss),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("start_match_button")
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Deal Cards & Start Match", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "Need at least 2 seated players to deal the deck.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AmberWarning,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (table.hostId == currentUserId && table.playerIds.size == 1) {
                    OutlinedButton(
                        onClick = onCancelTable,
                        enabled = !isBusy,
                        border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.65f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("cancel_table_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = CrimsonAlert, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel Room & Refund Stake", color = Color(0xFFFFA8A8))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivePlayTableContent(
    table: GameTable,
    myCards: List<String>,
    currentUserId: String,
    isBusy: Boolean,
    onCardTapped: (PlayingCard) -> Unit,
    onQuickSevenCut: (PlayingCard) -> Unit,
    onDrawCard: () -> Unit,
    onForfeitClick: () -> Unit
) {
    val isMyTurn = table.status == "ACTIVE" && table.currentTurnUid == currentUserId
    val parsedMyCards = remember(myCards) { myCards.mapNotNull { PlayingCard.parse(it) } }
    val myPoints = remember(myCards) { AmatatuEngine.calculatePoints(myCards) }
    val topCard = PlayingCard.parse(table.topCard)
    val cutCard = PlayingCard.parse(table.cutCard)
    val demandedSuit = Suit.fromNameOrNull(table.demandedSuit)
    val cutSuit = Suit.fromNameOrNull(table.cutSuit)

    val playableCutSeven = remember(parsedMyCards, table.cutSuit, table.pendingDrawPenalty, isMyTurn) {
        if (!isMyTurn) null
        else parsedMyCards.firstOrNull {
            AmatatuEngine.canTriggerSevenCut(it, myCards, table.cutSuit, table.pendingDrawPenalty)
        }
    }

    val opponents = table.playerIds.filter { it != currentUserId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Opponent Seats Strip
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            items(opponents, key = { it }) { oppUid ->
                val isTurn = table.currentTurnUid == oppUid && table.status == "ACTIVE"
                val oppName = table.playerNames[oppUid] ?: "Challenger"
                val oppAvatar = table.playerAvatars[oppUid] ?: "spade_ace"
                val cardCount = table.cardCounts[oppUid] ?: 0L

                Surface(
                    color = if (isTurn) SurfaceElevated else SurfaceCard.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(if (isTurn) 2.dp else 1.dp, if (isTurn) KampalaGold else SubtleBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AvatarCrest(oppAvatar, size = 36.dp, highlightGold = isTurn)
                        Column {
                            Text(
                                text = oppName,
                                style = MaterialTheme.typography.titleMedium,
                                color = WarmOffWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isTurn) "THINKING..." else "$cardCount cards left",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isTurn) KampalaGold else MutedSage
                            )
                        }
                        PlayingCardBack(width = 34.dp, height = 48.dp, badgeText = "$cardCount")
                    }
                }
            }
        }

        // 2. Center Felt Arena (Cut Suit + Draw Stock + Discard Pile + Action Banner)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (table.status == "SHOWDOWN") {
                Surface(
                    color = KampalaGold.copy(alpha = 0.18f),
                    border = BorderStroke(1.5.dp, KampalaGold),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = KampalaGold, strokeWidth = 2.dp)
                        Text(
                            text = "7-CUT SHOWDOWN! Revealing all hands...",
                            style = MaterialTheme.typography.titleMedium,
                            color = KampalaGold
                        )
                    }
                }
            }

            if (table.pendingDrawPenalty > 0L) {
                Surface(
                    color = CrimsonAlert.copy(alpha = 0.22f),
                    border = BorderStroke(1.5.dp, CrimsonAlert),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "PICK-${table.pendingDrawPenalty} ATTACK ACTIVE — Play 2, A♠ Shield, or Draw ${table.pendingDrawPenalty}!",
                        color = Color(0xFFFFB4B4),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cut Card & Cut Suit Anchor
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CUT SUIT", style = MaterialTheme.typography.labelSmall, color = MutedSage)
                    if (cutCard != null) {
                        PlayingCardFace(
                            card = cutCard,
                            isCutSeven = false,
                            width = 58.dp,
                            height = 84.dp
                        )
                    }
                    if (cutSuit != null) {
                        SuitBadge(cutSuit)
                    }
                }

                // Draw Pile
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (table.pendingDrawPenalty > 0L) "PENALTY" else "STOCK",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (table.pendingDrawPenalty > 0L) Color(0xFFFFA8A8) else MutedSage
                    )
                    PlayingCardBack(
                        width = 78.dp,
                        height = 114.dp,
                        badgeText = if (table.pendingDrawPenalty > 0L) "+${table.pendingDrawPenalty}" else "${table.drawPile.size}",
                        onClick = if (isMyTurn && !isBusy) onDrawCard else null,
                        modifier = Modifier.testTag("draw_pile_button")
                    )
                    Text(
                        text = if (isMyTurn) "Tap to Draw" else "Deck (${table.drawPile.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMyTurn) KampalaGold else MutedSage
                    )
                }

                // Discard Top Card
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("DISCARD", style = MaterialTheme.typography.labelSmall, color = MutedSage)
                    if (topCard != null) {
                        PlayingCardFace(
                            card = topCard,
                            isPlayable = true,
                            width = 84.dp,
                            height = 122.dp
                        )
                    } else {
                        PlayingCardBack(width = 84.dp, height = 122.dp)
                    }
                    if (demandedSuit != null) {
                        SuitBadge(demandedSuit)
                    } else if (topCard != null) {
                        Text(
                            text = "Match ${topCard.suit.symbol} or ${topCard.rank.symbol}",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarmOffWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Turn Status & Last Action Summary Pill
            Surface(
                color = SurfaceCard.copy(alpha = 0.92f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (isMyTurn) KampalaGold else SubtleBorder),
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isMyTurn) "YOUR TURN — Select a highlighted card or Draw" else "${table.playerNames[table.currentTurnUid] ?: "Opponent"}'s Turn",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isMyTurn) KampalaGold else WarmOffWhite
                        )
                        if (table.lastActionSummary.isNotBlank()) {
                            Text(
                                text = table.lastActionSummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSage,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (table.playerIds.size >= 3) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = if (table.turnDirection >= 0) "Clockwise turn order" else "Reversed turn order",
                            tint = KampalaGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 3. Player Control Strip & Hand Cards
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedVisibility(visible = playableCutSeven != null) {
                playableCutSeven?.let { cutSeven ->
                    Button(
                        onClick = { onQuickSevenCut(cutSeven) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = KampalaGold, contentColor = EmeraldAbyss),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("seven_cut_quick_button")
                    ) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "DROP ${cutSeven.displayLabel} 7-CUT (${myPoints - cutSeven.points} pts left!)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        text = "Hand: $myPoints / 25 pts",
                        containerColor = if (myPoints <= 25) JadeSuccess else SurfaceElevated,
                        contentColor = if (myPoints <= 25) JadeSuccess else WarmOffWhite
                    )
                    Text(
                        text = "${parsedMyCards.size} cards",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedSage
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onDrawCard,
                        enabled = isMyTurn && !isBusy,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (table.pendingDrawPenalty > 0L) CrimsonAlert.copy(alpha = 0.25f) else EmeraldFeltLight,
                            contentColor = WarmOffWhite
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("draw_action_button")
                    ) {
                        Text(
                            text = if (table.pendingDrawPenalty > 0L) "Take +${table.pendingDrawPenalty}" else "Draw 1",
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = onForfeitClick,
                        border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.5f)),
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("forfeit_match_button")
                    ) {
                        Icon(Icons.Filled.Flag, contentDescription = "Forfeit match", tint = CrimsonAlert, modifier = Modifier.size(16.dp))
                    }
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                items(parsedMyCards.size, key = { index -> "${parsedMyCards[index].code}_$index" }) { index ->
                    val card = parsedMyCards[index]
                    val playable = isMyTurn && AmatatuEngine.isCardPlayable(
                        card = card,
                        topCardCode = table.topCard,
                        demandedSuitName = table.demandedSuit,
                        pendingDrawPenalty = table.pendingDrawPenalty,
                        cutSuitName = table.cutSuit,
                        hand = myCards
                    )
                    val isCutSeven = AmatatuEngine.canTriggerSevenCut(
                        card = card,
                        hand = myCards,
                        cutSuitName = table.cutSuit,
                        pendingDrawPenalty = table.pendingDrawPenalty
                    )
                    val liftOffset by animateDpAsState(
                        targetValue = if (playable) (-10).dp else 0.dp,
                        animationSpec = spring(),
                        label = "cardLift"
                    )

                    PlayingCardFace(
                        card = card,
                        isCutSeven = isCutSeven,
                        isPlayable = playable,
                        isDimmed = isMyTurn && !playable,
                        width = 76.dp,
                        height = 110.dp,
                        onClick = if (playable && !isBusy) ({ onCardTapped(card) }) else null,
                        modifier = Modifier
                            .offset(y = liftOffset)
                            .testTag("hand_card_${card.code}")
                    )
                }
            }
        }
    }
}

@Composable
private fun FinishedMatchContent(
    table: GameTable,
    currentUserId: String,
    onBackToLobby: () -> Unit
) {
    val iWon = table.winnerId == currentUserId
    val isCancelled = table.status == "CANCELLED"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.96f)),
            border = BorderStroke(2.dp, if (iWon) KampalaGold else SubtleBorder),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(if (iWon) KampalaGold.copy(alpha = 0.2f) else EmeraldFeltLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (iWon) Icons.Filled.EmojiEvents else Icons.Filled.Casino,
                        contentDescription = null,
                        tint = if (iWon) KampalaGold else MutedSage,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Text(
                    text = when {
                        isCancelled -> "TABLE CANCELLED"
                        iWon -> "VICTORY! POT WON"
                        else -> "MATCH COMPLETE"
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (iWon) KampalaGold else WarmOffWhite,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isCancelled) table.lastActionSummary else "${table.winnerName} won (${table.winReason.replace("_", " ")})",
                    style = MaterialTheme.typography.titleMedium,
                    color = WarmOffWhite,
                    textAlign = TextAlign.Center
                )

                if (!isCancelled && table.stakeUgx > 0L) {
                    Surface(
                        color = if (iWon) JadeSuccess.copy(alpha = 0.16f) else EmeraldFelt,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (iWon) JadeSuccess else SubtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (iWon) "+${formatUgx(table.payoutUgx)} Credited" else "Pot Paid: ${formatUgx(table.payoutUgx)}",
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (iWon) JadeSuccess else MutedSage,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Total Pot ${formatUgx(table.potUgx)} (5% parlour fee)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSage
                            )
                        }
                    }
                }

                if (table.revealedPoints.isNotEmpty()) {
                    HorizontalDivider(color = SubtleBorder)
                    Text("Showdown Hand Totals", style = MaterialTheme.typography.titleMedium, color = WarmOffWhite)
                    table.playerIds.forEach { pid ->
                        val pName = table.playerNames[pid] ?: "Player"
                        val pts = table.revealedPoints[pid]
                        val cards = table.revealedHands[pid].orEmpty()
                        Surface(
                            color = EmeraldFelt,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (pid == currentUserId) "$pName (You)" else pName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (pid == table.winnerId) KampalaGold else WarmOffWhite
                                    )
                                    Text(
                                        text = if (pts != null) "$pts pts" else "${table.cardCounts[pid] ?: 0} cards",
                                        fontFamily = MonoFontFamily,
                                        color = if (pid == table.winnerId) JadeSuccess else MutedSage
                                    )
                                }
                                if (cards.isNotEmpty()) {
                                    Text(
                                        text = cards.mapNotNull { PlayingCard.parse(it)?.displayLabel }.joinToString("  "),
                                        fontFamily = MonoFontFamily,
                                        color = WarmOffWhite
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onBackToLobby,
                    colors = ButtonDefaults.buttonColors(containerColor = KampalaGold, contentColor = EmeraldAbyss),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("finished_back_lobby_button")
                ) {
                    Text("Return to Game Lobby", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AntiCheatAuditSheet(
    table: GameTable,
    moves: List<MoveAudit>,
    currentUserId: String,
    onReport: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reportNote by remember { mutableStateOf("") }
    val opponentUid = table.playerIds.firstOrNull { it != currentUserId } ?: table.hostId

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = JadeSuccess)
                Text("Anti-Cheat & Move Integrity", style = MaterialTheme.typography.headlineMedium, color = WarmOffWhite)
            }

            Surface(
                color = EmeraldFelt,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SubtleBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Deck Commit SHA-256", style = MaterialTheme.typography.labelSmall, color = MutedSage)
                    Text(
                        text = table.deckCommitHash.ifBlank { "Pending deal" },
                        fontFamily = MonoFontFamily,
                        color = KampalaGold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Private hands isolated per UID • Sequence lock #${table.moveSequence}",
                        style = MaterialTheme.typography.labelSmall,
                        color = JadeSuccess
                    )
                }
            }

            Text("Recent Verified Moves (${moves.size})", style = MaterialTheme.typography.titleMedium, color = WarmOffWhite)

            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(210.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (moves.isEmpty()) {
                    item {
                        Text("No moves recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MutedSage)
                    }
                } else {
                    items(moves, key = { it.id }) { m ->
                        Surface(color = EmeraldAbyss.copy(alpha = 0.65f), shape = RoundedCornerShape(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "#${m.moveSequence} ${m.playerName} • ${m.actionType}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = WarmOffWhite
                                    )
                                    Text(
                                        text = "Digest ${m.antiCheatDigest.take(12)}... • Hand ${m.handSizeAfter}",
                                        fontFamily = MonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedSage
                                    )
                                }
                                if (m.cardPlayed.isNotBlank()) {
                                    Text(
                                        text = PlayingCard.parse(m.cardPlayed)?.displayLabel ?: m.cardPlayed,
                                        fontFamily = MonoFontFamily,
                                        color = KampalaGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = SubtleBorder)
            Text("Flag Suspicious Activity", style = MaterialTheme.typography.titleMedium, color = WarmOffWhite)
            OutlinedTextField(
                value = reportNote,
                onValueChange = { reportNote = it },
                label = { Text("Describe anomaly (sent to Admin)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("anti_cheat_report_input")
            )
            OutlinedButton(
                onClick = {
                    onReport(opponentUid, reportNote)
                    reportNote = ""
                },
                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("submit_anti_cheat_report_button")
            ) {
                Text("Submit Anti-Cheat Log", color = AmberWarning)
            }
        }
    }
}
