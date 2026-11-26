package com.nexuscharge.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

data class LiveStats(
    val level: Int?,
    val precisePercent: Float?,   // estimate; null until calibrated
    val isCharging: Boolean,
    val isFull: Boolean,
    val voltageV: Float?,
    val voltageChangeV: Float?,
    val percentPerMin: Float?,
    val minutesToFull: Int?,
)

/** Reads the battery every second and works out speed, decimals and time to full. */
class ChargeTracker {
    private var lastLevel = -1
    private var counterAtLevelChange = 0
    private var calibrated = false
    private var wasCharging: Boolean? = null
    private var startVoltage: Float? = null
    private val samples = ArrayDeque<Pair<Long, Int>>() // (time ms, charge counter µAh)

    fun update(context: Context): LiveStats {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val info = readBatteryInfo(context, sticky)
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 1..100 } ?: info.percent
        val counter = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it > 0 && it != Int.MIN_VALUE }
        val now = SystemClock.elapsedRealtime()

        // Start fresh when the charger is plugged in or removed.
        if (info.isCharging != wasCharging) {
            wasCharging = info.isCharging
            samples.clear()
            calibrated = false
            lastLevel = -1
            startVoltage = if (info.isCharging) info.voltageV else null
        }

        // Decimals only become reliable after we see the level change once.
        if (level != null && counter != null && level != lastLevel) {
            if (lastLevel != -1) calibrated = true
            lastLevel = level
            counterAtLevelChange = counter
        }
        val perPercent = if (level != null && level > 0 && counter != null) counter.toFloat() / level else null

        val precise = if (calibrated && level != null && counter != null && perPercent != null) {
            val moved = (counter - counterAtLevelChange) / perPercent
            if (info.isCharging) level + moved.coerceIn(0f, 0.99f)
            else level + 0.99f - (-moved).coerceIn(0f, 0.99f)
        } else null

        // Speed in % per minute, from the last 5 minutes of charge counter readings.
        if (counter != null) {
            samples.addLast(now to counter)
            while (samples.size > 1 && now - samples.first().first > 5 * 60_000) samples.removeFirst()
        }
        val first = samples.firstOrNull()
        val spanMin = if (first != null) (now - first.first) / 60_000f else 0f
        val rate = if (first != null && counter != null && perPercent != null && spanMin >= 0.5f) {
            ((counter - first.second) / perPercent) / spanMin
        } else null

        // Time to full: Android's own estimate first (Android 9+), else our speed.
        val systemMinutes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager?.computeChargeTimeRemaining()?.takeIf { it > 0 }?.let { (it / 60_000).toInt() }
        } else null
        val nowPercent = precise ?: level?.toFloat()
        val ourMinutes = if (rate != null && rate > 0.01f && nowPercent != null) {
            ((100f - nowPercent) / rate).toInt()
        } else null

        val sv = startVoltage
        val v = info.voltageV
        return LiveStats(
            level = level,
            precisePercent = precise,
            isCharging = info.isCharging,
            isFull = info.isFull,
            voltageV = v,
            voltageChangeV = if (sv != null && v != null) v - sv else null,
            percentPerMin = rate,
            minutesToFull = if (info.isCharging) systemMinutes ?: ourMinutes else null,
        )
    }
}

private fun formatMinutes(m: Int) = if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"

/** Card that refreshes every second while the app is on screen. */
@Composable
fun LiveCard() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tracker = remember { ChargeTracker() }
    var stats by remember { mutableStateOf<LiveStats?>(null) }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                stats = tracker.update(context)
                delay(1_000)
            }
        }
    }

    val s = stats ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Live charging",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            LiveRow(
                "Precise level",
                s.precisePercent?.let { "%.2f %%".format(it) }
                    ?: (s.level?.let { "$it% (calibrating…)" } ?: UNAVAILABLE),
            )
            LiveRow(
                "Speed",
                s.percentPerMin?.let { "%+.2f %% / min".format(it) } ?: "Measuring…",
            )
            LiveRow("Voltage now", s.voltageV?.let { "%.3f V".format(it) } ?: UNAVAILABLE)
            LiveRow(
                "Voltage rise",
                s.voltageChangeV?.let { "%+.3f V".format(it) } ?: "—",
            )
            LiveRow(
                "Time to full",
                when {
                    s.isFull -> "Full"
                    !s.isCharging -> "Not charging"
                    s.minutesToFull != null -> formatMinutes(s.minutesToFull)
                    else -> "Calculating…"
                },
            )
            Text(
                text = "Decimals and time are estimates. They get better after the level changes once.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun LiveRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
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
}
