package com.asura.finanzas.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.asura.finanzas.R
import com.asura.finanzas.ui.components.Lucide
import com.asura.finanzas.ui.theme.Broke

/**
 * The web keeps five items in the mobile bar and folds planning behind "More".
 * Same split here, so someone moving between the two apps finds things in the
 * place they already learned.
 */
enum class MoreDestination(val labelRes: Int, val icon: ImageVector) {
    Goals(R.string.nav_goals, Lucide.PiggyBank),
    Budgets(R.string.nav_budgets, Lucide.Target),
    Subscriptions(R.string.nav_subscriptions, Lucide.CreditCard),
    Categories(R.string.categories_title, Icons.Outlined.Category),
}

/**
 * The planning destinations, as the sheet the web slides up over whatever page
 * you were on. It used to be a tab of its own here, with a "Planning" page and
 * a list of cards — a screen the web does not have at all.
 */
@Composable
fun MoreSheet(onDismiss: () -> Unit, onOpen: (MoreDestination) -> Unit) {
    val colors = Broke.colors
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                // `bg-surface-raised` is translucent, and in the browser the
                // blur behind it is what makes it read as a solid sheet. With
                // no cheap backdrop blur here, the page is laid under it.
                .background(colors.surface)
                .background(colors.surfaceRaised)
                // Swallow taps so hitting the sheet does not close it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Text(
                // The web's `.eyebrow`: small, spaced and upper-case.
                stringResource(R.string.nav_planning).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.2.sp,
                    letterSpacing = 2.02.sp,
                ),
                color = colors.fgMuted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            )
            Column(Modifier.padding(horizontal = 8.dp)) {
                MoreDestination.entries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpen(entry) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            entry.icon,
                            contentDescription = null,
                            tint = colors.fg,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            stringResource(entry.labelRes),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = colors.fg,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Lucide.ChevronRight,
                            contentDescription = null,
                            tint = colors.fgSubtle,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
