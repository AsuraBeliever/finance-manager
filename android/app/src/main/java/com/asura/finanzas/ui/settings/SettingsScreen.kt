package com.asura.finanzas.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asura.finanzas.BuildConfig
import com.asura.finanzas.data.BrokeRepository
import com.asura.finanzas.ui.components.GlassCard
import com.asura.finanzas.ui.components.SectionTitle
import com.asura.finanzas.ui.theme.Broke
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repository: BrokeRepository,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Broke.colors
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(20.dp)),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("Ajustes")

        GlassCard(Modifier.fillMaxWidth()) {
            Text("Versión", style = MaterialTheme.typography.labelSmall, color = colors.fgMuted)
            Spacer(Modifier.height(4.dp))
            Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyLarge, color = colors.fg)
            Spacer(Modifier.height(12.dp))
            Text("Servidor", style = MaterialTheme.typography.labelSmall, color = colors.fgMuted)
            Spacer(Modifier.height(4.dp))
            Text(BuildConfig.API_BASE, style = MaterialTheme.typography.bodyMedium, color = colors.fgMuted)
        }

        OutlinedButton(
            onClick = { scope.launch { repository.logout(); onSignedOut() } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cerrar sesión", color = colors.danger)
        }
    }
}
