package com.nexuscharge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexusChargeTheme { BatteryScreen() }
        }
    }
}

@Composable
fun NexusChargeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * Listens for battery broadcasts while the screen is visible.
 * The receiver is registered in onStart and unregistered in onStop, so nothing leaks.
 */
@Composable
fun rememberBatteryInfo(): BatteryInfo? {
    val context = LocalContext.current
    var info by remember { mutableStateOf<BatteryInfo?>(null) }

    LifecycleStartEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                info = readBatteryInfo(c, intent)
            }
        }
        // Returns the last ("sticky") battery intent, so the screen fills in immediately.
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        info = readBatteryInfo(context, sticky)

        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    return info
}

@Composable
fun BatteryScreen() {
    val info = rememberBatteryInfo()

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Nexus Charge",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                if (info == null) {
                    Text("Reading battery…")
                } else {
                    LevelCard(info)
                    DetailsCard(info)
                }
                AboutCard()
            }
        }
    }
}

@Composable
fun LevelCard(info: BatteryInfo) {
    val percentText = info.percent?.let { "$it%" } ?: UNAVAILABLE
    val chargeText = if (info.isCharging) "Charging" else "Not charging"
    val spoken = "Battery level " +
        (info.percent?.let { "$it percent" } ?: "unavailable") + ", $chargeText"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                CircularProgressIndicator(
                    progress = { (info.percent ?: 0) / 100f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 12.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(percentText, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                color = if (info.isCharging) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = if (info.isCharging) "⚡ Charging" else "🔋 Not charging",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
fun DetailsCard(info: BatteryInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            InfoRow("Status", info.status)
            InfoRow("Charging source", info.source)
            InfoRow("Fully charged", if (info.isFull) "Yes" else "No")
            InfoRow("Temperature", info.temperatureC?.let { "%.1f °C".format(it) } ?: UNAVAILABLE)
            InfoRow("Voltage", info.voltageV?.let { "%.2f V".format(it) } ?: UNAVAILABLE)
            InfoRow("Health", info.health)
            InfoRow("Technology", info.technology ?: UNAVAILABLE)
            InfoRow("Current", info.currentMa?.let { "$it mA" } ?: NOT_SUPPORTED)
            InfoRow("Charge counter", info.chargeCounterMah?.let { "$it mAh" } ?: NOT_SUPPORTED)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
    HorizontalDivider()
}

@Composable
fun AboutCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Readings come straight from Android and vary by device. Some phones " +
                    "don't report current or charge counter, and units or signs can differ " +
                    "between manufacturers. Nexus Charge needs no permissions and does not " +
                    "collect, store, or send any data.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
