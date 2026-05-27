package org.searchmob

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.searchmob.service.BatteryOptimization
import org.searchmob.service.OemGuidance
import org.searchmob.service.SearchMobServiceState
import org.searchmob.service.ServiceController
import org.searchmob.service.ServiceState
import org.searchmob.ui.theme.SearchMobTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the always-on service when the app is opened (idempotent if already running).
        ServiceController.start(this)

        setContent {
            // Ask for notification permission on Android 13+ so the service notification is visible.
            val notifPermission =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { /* result handled by the OS; the service runs regardless */ }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            SearchMobTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "SearchMob", style = MaterialTheme.typography.headlineMedium)
        Text(text = stringRes(R.string.home_tagline), style = MaterialTheme.typography.bodyLarge)

        ServiceCard()
        BatteryCard()
        OemGuidanceCard()
    }
}

@Composable
private fun ServiceCard() {
    val context = LocalContext.current
    val state by SearchMobServiceState.state.collectAsState()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = stringRes(R.string.service_status_label), style = MaterialTheme.typography.titleMedium)
            Text(
                text =
                    when (state) {
                        ServiceState.Stopped -> stringRes(R.string.service_state_stopped)
                        ServiceState.Starting -> stringRes(R.string.service_state_starting)
                        ServiceState.Running -> stringRes(R.string.service_state_running)
                    },
            )
            if (state == ServiceState.Running) {
                Button(onClick = { ServiceController.stop(context) }) {
                    Text(stringRes(R.string.service_stop))
                }
            } else {
                Button(onClick = { ServiceController.start(context) }) {
                    Text(stringRes(R.string.service_start))
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
            Text(text = stringRes(R.string.battery_label), style = MaterialTheme.typography.titleMedium)
            Text(text = if (exempt) stringRes(R.string.battery_exempt) else stringRes(R.string.battery_not_exempt))
            if (!exempt) {
                Button(onClick = {
                    // User-initiated only: fire the system exemption prompt, then re-check.
                    context.startActivity(BatteryOptimization.requestExemptionIntent(context))
                    exempt = runCatching { BatteryOptimization.isExempt(context) }.getOrDefault(false)
                }) {
                    Text(stringRes(R.string.battery_allow))
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
            Text(text = stringRes(R.string.oem_guidance_warning), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guidance.url)))
            }) {
                Text(stringRes(R.string.oem_guidance_button))
            }
        }
    }
}

/** Small helper so composables can read string resources via the local context. */
@Composable
private fun stringRes(id: Int): String = LocalContext.current.getString(id)

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SearchMobTheme {
        HomeScreen()
    }
}
