package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.GameTable
import com.example.model.PublicProfile
import com.example.model.StakeTier
import com.example.model.StakeTiers
import com.example.model.UserAccount
import com.example.ui.UiState
import com.example.ui.components.AvatarCrest
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatUgx
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldAbyss
import com.example.ui.theme.EmeraldFelt
import com.example.ui.theme.EmeraldFeltLight
import com.example.ui.theme.JadeSuccess
import com.example.ui.theme.KampalaGold
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MutedSage
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.WarmOffWhite

@Composable
fun LobbyScreen(
    user: UserAccount,
    selectedStakeUgx: Long,
    waitingTablesState: UiState<List<GameTable>>,
    myTablesState: UiState<List<GameTable>>,
    leaderboardState: UiState<List<PublicProfile>>,
    isBusy: Boolean,
    isWideLayout: Boolean,
    onSelectStake: (Long) -> Unit,
    onQuickMatch: (Long) -> Unit,
    onPlayBot: (Long) -> Unit,
    onCreatePrivateRoom: (String, Long, Int) -> Unit,
    onJoinByCode: (String) -> Unit,
    onJoinPublicTable: (String) -> Unit,
    onResumeTable: (String) -> Unit
) {
    var showCreatePrivateDialog by remember { mutableStateOf(false) }
    var showJoinCodeDialog by remember { mutableStateOf(false) }

    val selectedTier = StakeTiers.ALL.firstOrNull { it.stakeUgx == selectedStakeUgx } ?: StakeTiers.ALL[2]

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Hero Action Banner
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, KampalaGold.copy(alpha = 0.35f))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_amatatu_hero),
                        contentDescription = "Amatatu parlour felt",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        EmeraldAbyss.copy(alpha = 0.94f),
                                        EmeraldAbyss.copy(alpha = 0.80f),
                                        EmeraldAbyss.copy(alpha = 0.65f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusPill("LIVE UGX TABLES", KampalaGold, KampalaGold)
                            StatusPill(selectedTier.tag, JadeSuccess, JadeSuccess)
                        }

                        Text(
                            text = "${selectedTier.label} • ${if (selectedTier.stakeUgx == 0L) "Free Practice" else formatUgx(selectedTier.stakeUgx)}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = WarmOffWhite
                        )
                        Text(
                            text = selectedTier.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSage
                        )

                        if (isWideLayout) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onQuickMatch(selectedStakeUgx) },
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.buttonColors(containerColor = KampalaGold, contentColor = EmeraldAbyss),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("quick_match_button")
                                ) {
                                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Quick Online Match")
                                }
                                FilledTonalButton(
                                    onClick = { onPlayBot(selectedStakeUgx) },
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = EmeraldFeltLight, contentColor = WarmOffWhite),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("play_bot_button")
                                ) {
                                    Icon(Icons.Filled.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Instant vs Bot")
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onQuickMatch(selectedStakeUgx) },
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.buttonColors(containerColor = KampalaGold, contentColor = EmeraldAbyss),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("quick_match_button")
                                ) {
                                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Quick Match Random Opponent", fontWeight = FontWeight.Bold)
                                }
                                FilledTonalButton(
                                    onClick = { onPlayBot(selectedStakeUgx) },
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = EmeraldFeltLight, contentColor = WarmOffWhite),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("play_bot_button")
                                ) {
                                    Icon(Icons.Filled.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Play Instant vs Kampala Bot")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showCreatePrivateDialog = true },
                                enabled = !isBusy,
                                border = BorderStroke(1.dp, KampalaGold.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("create_private_room_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = KampalaGold, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Private Room", color = KampalaGold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { showJoinCodeDialog = true },
                                enabled = !isBusy,
                                border = BorderStroke(1.dp, SubtleBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("join_invite_code_button")
                            ) {
                                Icon(Icons.Filled.VpnKey, contentDescription = null, tint = WarmOffWhite, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Enter Code", color = WarmOffWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // UGX Stake Selector
        item {
            SectionHeader(
                title = "Select UGX Stake Table",
                subtitle = "Choose your entry stake — 95% pot awarded automatically to winner"
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(StakeTiers.ALL, key = { it.id }) { tier ->
                    StakeTierCard(
                        tier = tier,
                        selected = tier.stakeUgx == selectedStakeUgx,
                        affordable = user.walletBalanceUgx >= tier.stakeUgx,
                        onClick = { onSelectStake(tier.stakeUgx) }
                    )
                }
            }
        }

        // My Active / Waiting Rooms
        val myActiveTables = (myTablesState as? UiState.Success)?.data
            ?.filter { it.status == "ACTIVE" || it.status == "WAITING" || it.status == "SHOWDOWN" }
            .orEmpty()
        if (myActiveTables.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Your Active Matches",
                    subtitle = "Tap to resume or share your room invitation code"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    myActiveTables.forEach { table ->
                        TableRowCard(
                            table = table,
                            isJoinedByMe = true,
                            onAction = { onResumeTable(table.id) }
                        )
                    }
                }
            }
        }

        // Public Open Multiplayer Tables
        item {
            SectionHeader(
                title = "Open Public UGX Tables",
                subtitle = "Live tables waiting for online challengers"
            )
            Spacer(modifier = Modifier.height(8.dp))
            when (waitingTablesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = KampalaGold)
                    }
                }
                is UiState.Error -> {
                    Text(
                        text = waitingTablesState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is UiState.Success -> {
                    val joinable = waitingTablesState.data.filter { !it.playerIds.contains(user.userId) }
                    if (joinable.isEmpty()) {
                        Surface(
                            color = SurfaceCard,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.Group, contentDescription = null, tint = MutedSage, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No open public seats right now", style = MaterialTheme.typography.titleMedium, color = WarmOffWhite)
                                Text(
                                    "Tap Quick Match Online to host a public table or start an instant Kampala Bot match.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSage
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            joinable.forEach { table ->
                                TableRowCard(
                                    table = table,
                                    isJoinedByMe = false,
                                    onAction = { onJoinPublicTable(table.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Kampala Leaderboard Preview
        item {
            SectionHeader(
                title = "Kampala Leaders",
                subtitle = "Top verified winners across UGX tables"
            )
            Spacer(modifier = Modifier.height(8.dp))
            when (leaderboardState) {
                is UiState.Success -> {
                    if (leaderboardState.data.isEmpty()) {
                        Text("Play your first match to claim #1!", color = MutedSage, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                            border = BorderStroke(1.dp, SubtleBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                leaderboardState.data.take(5).forEachIndexed { idx, profile ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(
                                                text = "#${idx + 1}",
                                                fontFamily = MonoFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = if (idx == 0) KampalaGold else MutedSage
                                            )
                                            AvatarCrest(profile.avatarKey, size = 34.dp, highlightGold = idx == 0)
                                            Column {
                                                Text(profile.displayName, style = MaterialTheme.typography.titleMedium, color = WarmOffWhite)
                                                Text("${profile.gamesWon} wins • ${profile.gamesPlayed} played", style = MaterialTheme.typography.bodyMedium, color = MutedSage)
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = KampalaGold, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = formatUgx(profile.totalEarningsUgx),
                                                fontFamily = MonoFontFamily,
                                                color = KampalaGold,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }

    if (showCreatePrivateDialog) {
        CreatePrivateRoomDialog(
            defaultStakeUgx = selectedStakeUgx,
            onDismiss = { showCreatePrivateDialog = false },
            onConfirm = { title, stake, maxPlayers ->
                showCreatePrivateDialog = false
                onCreatePrivateRoom(title, stake, maxPlayers)
            }
        )
    }

    if (showJoinCodeDialog) {
        JoinInviteCodeDialog(
            onDismiss = { showJoinCodeDialog = false },
            onConfirm = { code ->
                showJoinCodeDialog = false
                onJoinByCode(code)
            }
        )
    }
}

@Composable
private fun StakeTierCard(
    tier: StakeTier,
    selected: Boolean,
    affordable: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("stake_tier_${tier.id}"),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) SurfaceElevated else SurfaceCard,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) KampalaGold else SubtleBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tier.tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) KampalaGold else MutedSage
                )
                if (!affordable) {
                    Text("LOW BAL", style = MaterialTheme.typography.labelSmall, color = AmberWarning)
                }
            }
            Text(
                text = if (tier.stakeUgx == 0L) "FREE" else formatUgx(tier.stakeUgx),
                style = MaterialTheme.typography.titleLarge,
                color = WarmOffWhite,
                fontFamily = MonoFontFamily
            )
            Text(
                text = tier.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedSage,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TableRowCard(
    table: GameTable,
    isJoinedByMe: Boolean,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, if (isJoinedByMe) KampalaGold.copy(alpha = 0.5f) else SubtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (table.isPrivate) {
                        Icon(Icons.Filled.Lock, contentDescription = "Private room", tint = KampalaGold, modifier = Modifier.size(16.dp))
                    }
                    Text(table.title, style = MaterialTheme.typography.titleMedium, color = WarmOffWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(table.status, if (table.status == "ACTIVE") JadeSuccess else AmberWarning, WarmOffWhite)
                    Text(
                        text = "${table.playerIds.size}/${table.maxPlayers} seats • ${if (table.stakeUgx == 0L) "Free" else formatUgx(table.stakeUgx)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSage
                    )
                }
                if (table.inviteCode.isNotBlank()) {
                    Text(
                        text = "Invite Code: ${table.inviteCode}",
                        fontFamily = MonoFontFamily,
                        color = KampalaGold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isJoinedByMe) EmeraldFeltLight else KampalaGold,
                    contentColor = if (isJoinedByMe) KampalaGold else EmeraldAbyss
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (isJoinedByMe) "Open" else "Join")
            }
        }
    }
}

@Composable
private fun CreatePrivateRoomDialog(
    defaultStakeUgx: Long,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, Int) -> Unit
) {
    var title by remember { mutableStateOf("Friends Table") }
    var stakeUgx by remember { mutableStateOf(defaultStakeUgx) }
    var maxPlayers by remember { mutableIntStateOf(2) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = { Text("Create Private Room", color = WarmOffWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Room Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("private_room_title_input")
                )
                Text("Stake Amount", style = MaterialTheme.typography.labelLarge, color = MutedSage)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(StakeTiers.ALL) { tier ->
                        Surface(
                            color = if (stakeUgx == tier.stakeUgx) KampalaGold else EmeraldFelt,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { stakeUgx = tier.stakeUgx }
                        ) {
                            Text(
                                text = if (tier.stakeUgx == 0L) "Free" else "${tier.stakeUgx / 1000.0}k".replace(".0k", "k"),
                                color = if (stakeUgx == tier.stakeUgx) EmeraldAbyss else WarmOffWhite,
                                fontFamily = MonoFontFamily,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
                Text("Max Seats", style = MaterialTheme.typography.labelLarge, color = MutedSage)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4).forEach { seats ->
                        Surface(
                            color = if (maxPlayers == seats) KampalaGold else EmeraldFelt,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { maxPlayers = seats }
                        ) {
                            Text(
                                text = "$seats Players",
                                color = if (maxPlayers == seats) EmeraldAbyss else WarmOffWhite,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, stakeUgx, maxPlayers) },
                colors = ButtonDefaults.buttonColors(containerColor = KampalaGold, contentColor = EmeraldAbyss),
                modifier = Modifier.testTag("confirm_create_private_room")
            ) {
                Text("Create & Generate Code")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MutedSage) }
        }
    )
}

@Composable
private fun JoinInviteCodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = { Text("Join Private Room", color = WarmOffWhite) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the invitation code shared by the room host.", style = MaterialTheme.typography.bodyMedium, color = MutedSage)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(12) },
                    label = { Text("Invite Code (e.g. AMT7K2P)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("invite_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code) },
                enabled = code.trim().length >= 4,
                colors = ButtonDefaults.buttonColors(containerColor = KampalaGold, contentColor = EmeraldAbyss),
                modifier = Modifier.testTag("confirm_join_code")
            ) {
                Text("Join Room")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MutedSage) }
        }
    )
}
