package org.searchmob.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.searchmob.R
import org.searchmob.service.BatteryOptimization
import org.searchmob.service.OemGuidance
import org.searchmob.service.SearchMobServiceState
import org.searchmob.service.ServiceController
import org.searchmob.service.ServiceState

/**
 * Landing screen: the entry point to search and settings, plus the always-on service status,
 * battery-exemption, and OEM-guidance affordances carried over from the foreground-service phase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(str(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = str(R.string.settings_title))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = str(R.string.home_tagline), style = MaterialTheme.typography.bodyLarge)

            Button(onClick = onOpenSearch, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Search, contentDescription = null)
                Text(text = "  " + str(R.string.home_search_button))
            }

            ServiceCard()
            BatteryCard()
            OemGuidanceCard()
        }
    }
}

@Composable
private fun ServiceCard() {
    val context = LocalContext.current
    val state by SearchMobServiceState.state.collectAsStateWithLifecycle()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = str(R.string.service_status_label), style = MaterialTheme.typography.titleMedium)
            Text(
                text =
                    when (state) {
                        ServiceState.Stopped -> str(R.string.service_state_stopped)
                        ServiceState.Starting -> str(R.string.service_state_starting)
                        ServiceState.Running -> str(R.string.service_state_running)
                    },
            )
            if (state == ServiceState.Running) {
                Button(onClick = { ServiceController.stop(context) }) {
                    Text(str(R.string.service_stop))
                }
            } else {
                Button(onClick = { ServiceController.start(context) }) {
                    Text(str(R.string.service_start))
                }
            }
        }
    }
}

@Composable
private fun BatteryCard() {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(runCatching { BatteryOptimization.isExempt(context) }.getOrDefault(false)) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = str(R.string.battery_label), style = MaterialTheme.typography.titleMedium)
            Text(text = if (exempt) str(R.string.battery_exempt) else str(R.string.battery_not_exempt))
            if (!exempt) {
                Button(onClick = {
                    context.startActivity(BatteryOptimization.requestExemptionIntent(context))
                    exempt = runCatching { BatteryOptimization.isExempt(context) }.getOrDefault(false)
                }) {
                    Text(str(R.string.battery_allow))
                }
            }
        }
    }
}

@Composable
private fun OemGuidanceCard() {
    val context = LocalContext.current
    val guidance = remember { OemGuidance.forManufacturer(Build.MANUFACTURER) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = str(R.string.oem_guidance_warning), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guidance.url)))
            }) {
                Text(str(R.string.oem_guidance_button))
            }
        }
    }
}

@Composable
private fun str(id: Int): String = LocalContext.current.getString(id)
