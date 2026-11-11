package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlayingCard
import com.example.model.Suit
import com.example.ui.theme.CardInkBlack
import com.example.ui.theme.CardInkCrimson
import com.example.ui.theme.EmeraldAbyss
import com.example.ui.theme.EmeraldFelt
import com.example.ui.theme.EmeraldFeltLight
import com.example.ui.theme.GoldShimmer
import com.example.ui.theme.IvoryCard
import com.example.ui.theme.IvoryCardBorder
import com.example.ui.theme.KampalaGold
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.SurfaceCard
import java.text.NumberFormat
import java.util.Locale

fun formatUgx(amount: Long): String {
    val nf = NumberFormat.getNumberInstance(Locale.US)
    return "UGX ${nf.format(amount)}"
}

fun avatarIconFor(key: String): ImageVector = when (key) {
    "crown_gold" -> Icons.Filled.EmojiEvents
    "spade_ace" -> Icons.Filled.Casino
    "seven_chop" -> Icons.Filled.Star
    "lion_crest" -> Icons.Filled.AutoAwesome
    "diamond_vip" -> Icons.Filled.AccountBalanceWallet
    "emerald_shield" -> Icons.Filled.Shield
    else -> Icons.Filled.Security
}

@Composable
fun AvatarCrest(
    avatarKey: String,
    size: Dp = 44.dp,
    highlightGold: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    if (highlightGold) listOf(KampalaGold, Color(0xFFB58011))
                    else listOf(EmeraldFeltLight, EmeraldFelt)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = avatarIconFor(avatarKey),
            contentDescription = "Avatar crest",
            tint = if (highlightGold) EmeraldAbyss else KampalaGold,
            modifier = Modifier.size(size * 0.56f)
        )
    }
}

@Composable
fun PlayingCardFace(
    card: PlayingCard,
    isCutSeven: Boolean = false,
    isPlayable: Boolean = false,
    isDimmed: Boolean = false,
    width: Dp = 74.dp,
    height: Dp = 108.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ink = if (card.suit.isRed) CardInkCrimson else CardInkBlack
    val borderColor = when {
        isCutSeven -> KampalaGold
        isPlayable -> KampalaGold.copy(alpha = 0.9f)
        else -> IvoryCardBorder
    }
    val borderWidth = if (isCutSeven || isPlayable) 2.5.dp else 1.dp
    val faceBg = if (isDimmed) IvoryCard.copy(alpha = 0.58f) else IvoryCard

    Surface(
        modifier = modifier
            .size(width = width, height = height)
            .shadow(elevation = if (isPlayable) 10.dp else 4.dp, shape = RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        color = faceBg,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            Column(
                modifier = Modifier.align(Alignment.TopStart),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = card.rank.symbol,
                    color = ink,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (width >= 70.dp) 16.sp else 13.sp,
                    lineHeight = 16.sp
                )
                Text(
                    text = card.suit.symbol,
                    color = ink,
                    fontSize = if (width >= 70.dp) 14.sp else 11.sp,
                    lineHeight = 14.sp
                )
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = card.suit.symbol,
                    color = ink,
                    fontSize = if (width >= 70.dp) 30.sp else 22.sp
                )
                if (isCutSeven) {
                    Surface(
                        color = KampalaGold,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "CUT",
                            color = EmeraldAbyss,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Text(
                text = "${card.points}p",
                color = ink.copy(alpha = 0.55f),
                fontFamily = MonoFontFamily,
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
fun PlayingCardBack(
    width: Dp = 56.dp,
    height: Dp = 80.dp,
    badgeText: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(width = width, height = height)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(10.dp),
        color = EmeraldFelt,
        border = BorderStroke(1.5.dp, KampalaGold.copy(alpha = 0.8f)),
        shadowElevation = 5.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    Brush.radialGradient(
                        listOf(EmeraldFeltLight, EmeraldAbyss)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✦",
                color = KampalaGold,
                fontSize = 20.sp
            )
            if (badgeText != null) {
                Surface(
                    color = KampalaGold,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = EmeraldAbyss,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SuitBadge(suit: Suit, modifier: Modifier = Modifier) {
    val bg = if (suit.isRed) CardInkCrimson.copy(alpha = 0.2f) else SurfaceCard
    val fg = if (suit.isRed) Color(0xFFFF7A8A) else GoldShimmer
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = suit.symbol, color = fg, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = suit.label,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = containerColor.copy(alpha = 0.18f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, containerColor.copy(alpha = 0.55f))
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}
