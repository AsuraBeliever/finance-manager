package com.asura.finanzas.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.BrokeApp
import com.asura.finanzas.R
import com.asura.finanzas.ui.LocalAppSettings
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

/**
 * The eye that masks every figure on screen, sitting in the page header exactly
 * where the web puts it. The setting itself already existed — what was missing
 * was the one-tap way to reach it without going into Ajustes.
 */
@Composable
fun PrivacyToggle(modifier: Modifier = Modifier) {
    val hidden = LocalAppSettings.current.hideBalances
    val scope = rememberCoroutineScope()
    val preferences = (LocalContext.current.applicationContext as BrokeApp).preferences

    Icon(
        imageVector = if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
        contentDescription = stringResource(
            if (hidden) R.string.dashboard_show_balance else R.string.dashboard_hide_balance,
        ),
        tint = Broke.colors.fgSubtle,
        // 15 px glyph in a `p-1` box, as on the web. Small for a touch
        // target, but a taller button pushes down everything under it in the
        // card, and the web's row height is the one being matched.
        modifier = modifier
            .size(23.dp)
            .clickable { scope.launch { preferences.setHideBalances(!hidden) } }
            .padding(4.dp),
    )
}
