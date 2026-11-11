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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.WalletTransaction
import com.example.ui.AdminDashboardState
import com.example.ui.UiState
import com.example.ui.components.StatusPill
import com.example.ui.components.formatUgx
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldAbyss
import com.example.ui.theme.JadeSuccess
import com.example.ui.theme.KampalaGold
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MutedSage
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.WarmOffWhite

@Composable
fun AdminDashboardScreen(
    adminState: UiState<AdminDashboardState>,
    isBusy: Boolean,
    onApproveTx: (WalletTransaction) -> Unit,
    onRejectTx: (WalletTransaction, String) -> Unit,
    onToggleBan: (String, Boolean) -> Unit,
    onCancelTable: (String) -> Unit,
    onResolveCheatLog: (String) -> Unit
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    var txToReject by remember { mutableStateOf<WalletTransaction?>(null) }
    var rejectReason by remember { mutableStateOf("MoMo reference not confirmed") }

    when (adminState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KampalaGold)
            }
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(adminState.message, color = CrimsonAlert, style = MaterialTheme.typography.bodyLarge)
            }
        }
        is UiState.Success -> {
            val data = adminState.data
            val pendingTxs = data.transactions.filter { it.status == "PENDING" }
            val unresolvedCheats = data.antiCheatLogs.filter { !it.resolved }
            val activeTables = data.tables.filter { it.status in listOf("WAITING", "ACTIVE", "SHOWDOWN") }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdminKpiBox("Pending MoMo", "${pendingTxs.size}", AmberWarning, Modifier.weight(1f))
                        AdminKpiBox("Players", "${data.users.size}", KampalaGold, Modifier.weight(1f))
                        AdminKpiBox("Live Tables", "${activeTables.size}", JadeSuccess, Modifier.weight(1f))
                        AdminKpiBox("Alerts", "${unresolvedCheats.size}", CrimsonAlert, Modifier.weight(1f))
                    }
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(
                            listOf(
                                0 to "MoMo Queue (${pendingTxs.size})",
                                1 to "Players (${data.users.size})",
                                2 to "Tables (${data.tables.size})",
                                3 to "Anti-Cheat (${unresolvedCheats.size})"
                            )
                        ) { (idx, label) ->
                            val active = selectedSection == idx
                            Surface(
                                color = if (active) KampalaGold else SurfaceCard,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, if (active) KampalaGold else SubtleBorder),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(50))
                                    .clickable { selectedSection = idx }
                                    .testTag("admin_tab_$idx")
                            ) {
                                Text(
                                    text = label,
                                    color = if (active) EmeraldAbyss else WarmOffWhite,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }

                when (selectedSection) {
                    0 -> {
                        if (data.transactions.isEmpty()) {
                            item { EmptyAdminCard("No Mobile Money or wallet transactions found.") }
                        } else {
                            items(data.transactions, key = { it.id }) { tx ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                    border = BorderStroke(1.dp, if (tx.status == "PENDING") AmberWarning.copy(alpha = 0.6f) else SubtleBorder)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("${tx.userName} • ${tx.type}", style = MaterialTheme.typography.titleMedium, color = WarmOffWhite)
                                            StatusPill(
                                                tx.status,
                                                if (tx.status == "COMPLETED") JadeSuccess else if (tx.status == "PENDING") AmberWarning else CrimsonAlert,
                                                WarmOffWhite
                                            )
                                        }
                                        Text(
                                            text = "${formatUgx(tx.amountUgx)} via ${tx.provider} (${tx.phoneNumber.ifBlank { "Wallet" }}) • Ref ${tx.referenceCode}",
                                            fontFamily = MonoFontFamily,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = KampalaGold
                                        )
                                        if (tx.status == "PENDING") {
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                                Button(
                                                    onClick = { onApproveTx(tx) },
                                                    enabled = !isBusy,
                                                    colors = ButtonDefaults.buttonColors(containerColor = JadeSuccess, contentColor = EmeraldAbyss),
                                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("admin_approve_${tx.id}")
                                                ) {
                                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Approve")
                                                }
                                                OutlinedButton(
                                                    onClick = { txToReject = tx },
                                                    enabled = !isBusy,
                                                    border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.7f)),
                                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("admin_reject_${tx.id}")
                                                ) {
                                                    Icon(Icons.Filled.Close, contentDescription = null, tint = CrimsonAlert, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Reject", color = Color(0xFFFFA8A8))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        if (data.users.isEmpty()) {
                            item { EmptyAdminCard("No registered players yet.") }
                        } else {
                            items(data.users, key = { it.userId }) { player ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                    border = BorderStroke(1.dp, if (player.isBanned) CrimsonAlert.copy(alpha = 0.6f) else SubtleBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(player.displayName, style = MaterialTheme.typography.titleMedium, color = WarmOffWhite)
                                                if (player.isBanned) {
                                                    StatusPill("BANNED", CrimsonAlert, CrimsonAlert)
                                                }
                                            }
                                            Text(
                                                text = "Bal: ${formatUgx(player.walletBalanceUgx)} • Escrow: ${formatUgx(player.escrowUgx)}",
                                                fontFamily = MonoFontFamily,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = KampalaGold
                                            )
                                            Text(
                                                text = "${player.gamesWon}W / ${player.gamesPlayed}P • ${player.phoneNumber.ifBlank { "No MoMo set" }}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MutedSage
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { onToggleBan(player.userId, !player.isBanned) },
                                            enabled = !isBusy,
                                            border = BorderStroke(1.dp, if (player.isBanned) JadeSuccess else CrimsonAlert),
                                            modifier = Modifier.minimumInteractiveComponentSize().testTag("admin_ban_${player.userId}")
                                        ) {
                                            Icon(
                                                imageVector = if (player.isBanned) Icons.Filled.VerifiedUser else Icons.Filled.Block,
                                                contentDescription = null,
                                                tint = if (player.isBanned) JadeSuccess else CrimsonAlert,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (player.isBanned) "Unban" else "Ban", color = WarmOffWhite)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        if (data.tables.isEmpty()) {
                            item { EmptyAdminCard("No tables recorded yet.") }
                        } else {
                            items(data.tables, key = { it.id }) { tbl ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                    border = BorderStroke(1.dp, SubtleBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(tbl.title, style = MaterialTheme.typography.titleMedium, color = WarmOffWhite)
                                                StatusPill(tbl.status, AmberWarning, WarmOffWhite)
                                            }
                                            Text(
                                                text = "Stake ${formatUgx(tbl.stakeUgx)} • Pot ${formatUgx(tbl.potUgx)} • Moves #${tbl.moveSequence}",
                                                fontFamily = MonoFontFamily,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = KampalaGold
                                            )
                                        }
                                        if (tbl.status in listOf("WAITING", "ACTIVE", "SHOWDOWN")) {
                                            OutlinedButton(
                                                onClick = { onCancelTable(tbl.id) },
                                                enabled = !isBusy,
                                                border = BorderStroke(1.dp, CrimsonAlert),
                                                modifier = Modifier.minimumInteractiveComponentSize().testTag("admin_close_table_${tbl.id}")
                                            ) {
                                                Text("Close", color = Color(0xFFFFA8A8))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        if (data.antiCheatLogs.isEmpty()) {
                            item { EmptyAdminCard("Zero anti-cheat anomalies reported.") }
                        } else {
                            items(data.antiCheatLogs, key = { it.id }) { log ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                                    border = BorderStroke(1.dp, if (!log.resolved) CrimsonAlert.copy(alpha = 0.6f) else SubtleBorder)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(log.eventType, style = MaterialTheme.typography.titleMedium, color = AmberWarning)
                                            StatusPill(if (log.resolved) "RESOLVED" else log.severity, if (log.resolved) JadeSuccess else CrimsonAlert, WarmOffWhite)
                                        }
                                        Text(log.details, style = MaterialTheme.typography.bodyMedium, color = WarmOffWhite)
                                        Text("Table: ${log.tableId} • Reported UID: ${log.reportedUid}", fontFamily = MonoFontFamily, style = MaterialTheme.typography.labelSmall, color = MutedSage)
                                        if (!log.resolved) {
                                            Button(
                                                onClick = { onResolveCheatLog(log.id) },
                                                enabled = !isBusy,
                                                colors = ButtonDefaults.buttonColors(containerColor = KampalaGold, contentColor = EmeraldAbyss),
                                                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("admin_resolve_${log.id}")
                                            ) {
                                                Icon(Icons.Filled.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Mark Incident Resolved")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (txToReject != null) {
        val tx = txToReject!!
        AlertDialog(
            onDismissRequest = { txToReject = null },
            containerColor = SurfaceCard,
            title = { Text("Reject ${tx.referenceCode}?", color = WarmOffWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (tx.type == "WITHDRAWAL") "Rejecting returns ${formatUgx(tx.amountUgx)} to ${tx.userName}'s wallet." else "Deposit will be marked REJECTED without crediting funds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSage
                    )
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        label = { Text("Reason") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRejectTx(tx, rejectReason)
                        txToReject = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert, contentColor = WarmOffWhite)
                ) {
                    Text("Confirm Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = { txToReject = null }) { Text("Cancel", color = MutedSage) }
            }
        )
    }
}

@Composable
private fun AdminKpiBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = SurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MutedSage, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(value, fontFamily = MonoFontFamily, style = MaterialTheme.typography.titleLarge, color = color)
        }
    }
}

@Composable
private fun EmptyAdminCard(text: String) {
    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MutedSage, modifier = Modifier.padding(20.dp))
    }
}
