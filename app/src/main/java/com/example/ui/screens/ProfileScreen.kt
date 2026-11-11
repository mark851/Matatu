package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AvatarPresets
import com.example.model.UserAccount
import com.example.ui.components.AvatarCrest
import com.example.ui.components.StatusPill
import com.example.ui.components.formatUgx
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldAbyss
import com.example.ui.theme.EmeraldFelt
import com.example.ui.theme.JadeSuccess
import com.example.ui.theme.KampalaGold
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.MutedSage
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.WarmOffWhite

@Composable
fun ProfileScreen(
    user: UserAccount,
    isAdmin: Boolean,
    isBusy: Boolean,
    onSaveProfile: (String, String, String, String, Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    var displayName by remember(user.displayName) { mutableStateOf(user.displayName) }
    var phone by remember(user.phoneNumber) { mutableStateOf(user.phoneNumber) }
    var provider by remember(user.preferredProvider) { mutableStateOf(user.preferredProvider) }
    var avatarKey by remember(user.avatarKey) { mutableStateOf(user.avatarKey) }
    var soundEnabled by remember(user.soundEnabled) { mutableStateOf(user.soundEnabled) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (user.isBanned) {
            item {
                Surface(
                    color = CrimsonAlert.copy(alpha = 0.18f),
                    border = BorderStroke(1.5.dp, CrimsonAlert),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = CrimsonAlert)
                        Text(
                            text = "Your account is currently suspended from joining UGX tables or withdrawing funds. Contact Admin support.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFFB4B4)
                        )
                    }
                }
            }
        }

        // Player Identity Header
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, KampalaGold.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AvatarCrest(avatarKey, size = 64.dp, highlightGold = true)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(user.displayName, style = MaterialTheme.typography.headlineMedium, color = WarmOffWhite)
                        Text("UID: ${user.userId.take(10)}...", fontFamily = MonoFontFamily, style = MaterialTheme.typography.labelSmall, color = MutedSage)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(formatUgx(user.walletBalanceUgx), JadeSuccess, JadeSuccess)
                            if (isAdmin) {
                                StatusPill("ADMIN", KampalaGold, KampalaGold)
                            }
                        }
                    }
                }
            }
        }

        // Statistics Summary
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileStatBox("Played", "${user.gamesPlayed}", Modifier.weight(1f))
                ProfileStatBox("Won", "${user.gamesWon}", Modifier.weight(1f))
                ProfileStatBox("Win Rate", "${user.winRatePercent}%", Modifier.weight(1f))
                ProfileStatBox("Total Pts", "${user.totalPointsWon}", Modifier.weight(1f))
            }
        }

        // Edit Profile Form
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Player Profile & Preferences", style = MaterialTheme.typography.titleLarge, color = WarmOffWhite)

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it.take(36) },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.take(20) },
                        label = { Text("Mobile Money Number (+256...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("profile_phone_input")
                    )

                    Text("Default Mobile Money Network", style = MaterialTheme.typography.labelLarge, color = MutedSage)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("MTN_MOMO" to "MTN MoMo", "AIRTEL_MONEY" to "Airtel Money").forEach { (key, label) ->
                            val selected = provider == key
                            Surface(
                                color = if (selected) KampalaGold else EmeraldFelt,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { provider = key }
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) EmeraldAbyss else WarmOffWhite,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp)
                                )
                            }
                        }
                    }

                    Text("Select Table Crest", style = MaterialTheme.typography.labelLarge, color = MutedSage)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(AvatarPresets.OPTIONS, key = { it.first }) { (key, title) ->
                            val selected = avatarKey == key
                            Surface(
                                color = if (selected) SurfaceElevated else EmeraldFelt,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, if (selected) KampalaGold else SubtleBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { avatarKey = key }
                                    .testTag("avatar_option_$key")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AvatarCrest(key, size = 40.dp, highlightGold = selected)
                                    Text(title, style = MaterialTheme.typography.labelSmall, color = WarmOffWhite)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Table Sound Effects", style = MaterialTheme.typography.titleMedium, color = WarmOffWhite)
                            Text("Card taps, 7-Cut fanfare & coin chimes", style = MaterialTheme.typography.bodyMedium, color = MutedSage)
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { soundEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldAbyss, checkedTrackColor = KampalaGold),
                            modifier = Modifier.testTag("profile_sound_switch")
                        )
                    }

                    Button(
                        onClick = { onSaveProfile(displayName, phone, provider, avatarKey, soundEnabled) },
                        enabled = !isBusy && displayName.trim().length >= 2,
                        colors = ButtonDefaults.buttonColors(containerColor = KampalaGold, contentColor = EmeraldAbyss),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_profile_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Profile Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (isAdmin) {
            item {
                Surface(
                    color = KampalaGold.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, KampalaGold.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = KampalaGold)
                        Text(
                            text = "Administrator privileges active. Access the Admin tab to approve MoMo transactions and monitor tables.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WarmOffWhite
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onSignOut,
                border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.65f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = CrimsonAlert, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign Out", color = Color(0xFFFFB4B4), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProfileStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = SurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MutedSage)
            Text(value, fontFamily = MonoFontFamily, style = MaterialTheme.typography.titleMedium, color = KampalaGold)
        }
    }
}
