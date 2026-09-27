package com.asura.finanzas.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asura.finanzas.R

/**
 * The web's `useOnline()` — `navigator.onLine`, live: true while the phone
 * has any network that claims internet. Like the browser's flag it says
 * nothing about whether the server answers; a request that fails still shows
 * its own error.
 */
@Composable
fun rememberOnline(): State<Boolean> {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    val online = remember {
        mutableStateOf(
            manager.getNetworkCapabilities(manager.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
        )
    }
    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online.value = true }
            override fun onLost(network: Network) {
                online.value = manager.getNetworkCapabilities(manager.activeNetwork)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        onDispose { manager.unregisterNetworkCallback(callback) }
    }
    return online
}

/**
 * The strip the web pins above every page while offline: `bg-amber-500/15
 * px-4 py-1.5 text-xs text-amber-300`, a 14 px `CloudOff` and the banner
 * text, centred. Amber-300 in both themes, as on the web.
 */
@Composable
fun OfflineStrip(modifier: Modifier = Modifier) {
    val amber = Color(0xFFFCD34D)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.CloudOff, contentDescription = null, tint = amber, modifier = Modifier.size(14.dp))
        Text(
            stringResource(R.string.offline_banner),
            style = MaterialTheme.typography.bodySmall,
            color = amber,
        )
    }
}
