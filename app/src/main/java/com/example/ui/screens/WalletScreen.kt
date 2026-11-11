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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.UserAccount
import com.example.model.WalletTransaction
import com.example.ui.UiState
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.formatUgx
import com.example.ui.theme.AirtelRed
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldAbyss
import com.example.ui.theme.EmeraldFelt
import com.example.ui.theme.EmeraldFeltLight
import com.example.ui.theme.JadeSuccess
import com.example.ui.theme.KampalaGold
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MtnInk
import com.example.ui.theme.MtnYellow
import com.example.ui.theme.MutedSage
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.WarmOffWhite

@Composable
fun WalletScreen(
    user: UserAccount,
    transactionsState: UiState<List<WalletTransaction>>,
    isBusy: Boolean,
    onDeposit: (Long, String, String) -> Unit,
    onWithdraw: (Long, String, String) -> Unit
) {
    var mode by remember { mutableStateOf("DEPOSIT") }
    var provider by remember(user.preferredProvider) { mutableStateOf(user.preferredProvider) }
    var phoneInput by remember(user.phoneNumber) { mutableStateOf(user.phoneNumber.ifBlank { "+25677" }) }
    var amountInput by remember { mutableStateOf("10000") }
    var ledgerFilter by remember { mutableStateOf("ALL") }

    val parsedAmount = amountInput.filter { it.isDigit() }.toLongOrNull() ?: 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // UGX Balance Hero Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, KampalaGold.copy(alpha = 0.4f))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_momo_cashier),
                        contentDescription = "UGX Mobile Money Cashier",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        EmeraldAbyss.copy(alpha = 0.96f),
                                        EmeraldAbyss.copy(alpha = 0.88f),
                                        EmeraldAbyss.copy(alpha = 0.72f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = KampalaGold)
                            Text("AVAILABLE UGX WALLET", style = MaterialTheme.typography.labelLarge, color = KampalaGold)
                        }
                        Text(
                            text = formatUgx(user.walletBalanceUgx),
                            style = MaterialTheme.typography.displayLarge,
                            fontFamily = MonoFontFamily,
                            color = WarmOffWhite
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = EmeraldFelt.copy(alpha = 0.88f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SubtleBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("In Active Escrow", style = MaterialTheme.typography.labelSmall, color = MutedSage)
                                    Text(
                                        text = formatUgx(user.escrowUgx),
                                        fontFamily = MonoFontFamily,
                                        color = AmberWarning,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                            Surface(
                                color = EmeraldFelt.copy(alpha = 0.88f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SubtleBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Lifetime Won", style = MaterialTheme.typography.labelSmall, color = MutedSage)
                                    Text(
                                        text = formatUgx(user.totalEarningsUgx),
                                        fontFamily = MonoFontFamily,
                                        color = JadeSuccess,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Mobile Money Deposit / Withdraw Cashier Form
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isDep = mode == "DEPOSIT"
                        Surface(
                            color = if (isDep) KampalaGold else EmeraldFelt,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { mode = "DEPOSIT" }
                                .testTag("wallet_mode_deposit")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = if (isDep) EmeraldAbyss else WarmOffWhite, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Deposit UGX", color = if (isDep) EmeraldAbyss else WarmOffWhite, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            color = if (!isDep) KampalaGold else EmeraldFelt,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { mode = "WITHDRAWAL" }
                                .testTag("wallet_mode_withdraw")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = if (!isDep) EmeraldAbyss else WarmOffWhite, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Withdraw UGX", color = if (!isDep) EmeraldAbyss else WarmOffWhite, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Network Provider Selector
                    Text("Mobile Money Network", style = MaterialTheme.typography.labelLarge, color = MutedSage)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val isMtn = provider == "MTN_MOMO"
                        Surface(
                            color = if (isMtn) MtnYellow else EmeraldFelt,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, if (isMtn) MtnYellow else SubtleBorder),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { provider = "MTN_MOMO" }
                                .testTag("provider_mtn")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("MTN MoMo", fontWeight = FontWeight.Bold, color = if (isMtn) MtnInk else WarmOffWhite)
                                Text("077 / 078 / 076", style = MaterialTheme.typography.labelSmall, color = if (isMtn) MtnInk else MutedSage)
                            }
                        }

                        val isAirtel = provider == "AIRTEL_MONEY"
                        Surface(
                            color = if (isAirtel) AirtelRed else EmeraldFelt,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, if (isAirtel) AirtelRed else SubtleBorder),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { provider = "AIRTEL_MONEY" }
                                .testTag("provider_airtel")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Airtel Money", fontWeight = FontWeight.Bold, color = WarmOffWhite)
                                Text("070 / 075 / 074", style = MaterialTheme.typography.labelSmall, color = WarmOffWhite.copy(alpha = 0.85f))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it.take(20) },
                        label = { Text("Ugandan Mobile Number (e.g. +256772123456)") },
                        leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = KampalaGold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("wallet_phone_input")
                    )

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it.filter { ch -> ch.isDigit() }.take(8) },
                        label = { Text(if (mode == "DEPOSIT") "Deposit Amount (UGX 1,000 - 5,000,000)" else "Cashout Amount (UGX 2,000 - Balance)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("wallet_amount_input")
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(5000L, 10000L, 25000L, 50000L, 100000L)) { preset ->
                            Surface(
                                color = if (parsedAmount == preset) SurfaceElevated else EmeraldFelt,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (parsedAmount == preset) KampalaGold else SubtleBorder),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { amountInput = preset.toString() }
                            ) {
                                Text(
                                    text = formatUgx(preset),
                                    fontFamily = MonoFontFamily,
                                    color = if (parsedAmount == preset) KampalaGold else WarmOffWhite,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        color = EmeraldFelt,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SubtleBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                            Text(
                                text = if (mode == "DEPOSIT") {
                                    "Mobile Money Cashier Queue (sandbox ledger): requests are logged as PENDING and credited once verified by an Admin."
                                } else {
                                    "Withdrawals lock the requested UGX immediately and queue a PENDING cashout to your Mobile Money line for Admin release."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSage
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (mode == "DEPOSIT") onDeposit(parsedAmount, provider, phoneInput)
                            else onWithdraw(parsedAmount, provider, phoneInput)
                        },
                        enabled = !isBusy && parsedAmount > 0L && phoneInput.trim().length >= 9,
                        colors = ButtonDefaults.buttonColors(containerColor = KampalaGold, contentColor = EmeraldAbyss),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("wallet_submit_button")
                    ) {
                        Text(
                            text = if (mode == "DEPOSIT") "Request MoMo Deposit" else "Submit Cashout Request",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Ledger Header & Filters
        item {
            SectionHeader(
                title = "Transaction Ledger",
                subtitle = "Immutable UGX Mobile Money, escrow & payout history"
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("ALL" to "All", "PENDING" to "Pending", "COMPLETED" to "Completed", "DEPOSIT" to "Deposits", "WITHDRAWAL" to "Withdrawals")) { (key, label) ->
                    val active = ledgerFilter == key
                    Surface(
                        color = if (active) KampalaGold else SurfaceCard,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, if (active) KampalaGold else SubtleBorder),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(50))
                            .clickable { ledgerFilter = key }
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

        when (transactionsState) {
            is UiState.Loading -> item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KampalaGold)
                }
            }
            is UiState.Error -> item {
                Text(transactionsState.message, color = CrimsonAlert, style = MaterialTheme.typography.bodyMedium)
            }
            is UiState.Success -> {
                val filtered = transactionsState.data.filter { tx ->
                    when (ledgerFilter) {
                        "PENDING" -> tx.status == "PENDING"
                        "COMPLETED" -> tx.status == "COMPLETED"
                        "DEPOSIT" -> tx.type == "DEPOSIT"
                        "WITHDRAWAL" -> tx.type == "WITHDRAWAL"
                        else -> true
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Surface(
                            color = SurfaceCard,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No transactions matching this filter yet.",
                                color = MutedSage,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { tx ->
                        TransactionCard(tx)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionCard(tx: WalletTransaction) {
    val isCredit = tx.type in listOf("DEPOSIT", "WIN_PAYOUT", "REFUND", "WELCOME_BONUS")
    val statusColor = when (tx.status) {
        "COMPLETED" -> JadeSuccess
        "PENDING" -> AmberWarning
        else -> CrimsonAlert
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(tx.status, statusColor, statusColor)
                    Text(
                        text = tx.type.replace("_", " "),
                        style = MaterialTheme.typography.titleMedium,
                        color = WarmOffWhite
                    )
                }
                Text(
                    text = "${tx.referenceCode} • ${tx.provider.replace("_", " ")}${if (tx.phoneNumber.isNotBlank()) " (${tx.phoneNumber})" else ""}",
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedSage
                )
                if (tx.note.isNotBlank()) {
                    Text(tx.note, style = MaterialTheme.typography.bodyMedium, color = MutedSage)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${if (isCredit) "+" else "-"}${formatUgx(tx.netAmountUgx)}",
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = if (isCredit) JadeSuccess else WarmOffWhite
            )
        }
    }
}
