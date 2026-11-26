package com.nexuscharge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexusChargeTheme {
                var fullScreen by remember { mutableStateOf(false) }
                if (fullScreen) ChargingScreen(onClose = { fullScreen = false })
                else BatteryScreen(onStart = { fullScreen = true })
            }
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

/** Listens for battery broadcasts only while the screen is visible (no leaks). */
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

/** The current time, updated at the start of every minute. */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L * (60 - LocalDateTime.now().second))
            now = LocalDateTime.now()
        }
    }
    return now
}

@Composable
fun BatteryScreen(onStart: () -> Unit = {}) {
    val info = rememberBatteryInfo()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onStart) {
                Text("▶  Start", fontWeight = FontWeight.Bold)
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { innerPadding ->
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
                    HeroCard(info)
                    LiveCard()
                    TileGrid(buildTiles(info))
                }
                AboutCard()
                Spacer(Modifier.height(88.dp))
            }
        }
    }
}

@Composable
fun HeroCard(info: BatteryInfo) {
    val context = LocalContext.current
    val now = rememberNow()
    val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val percent = info.percent
    val gaugeColor = when {
        info.isCharging || info.isFull -> Color(0xFF4ADE80)
        percent != null && percent <= 20 -> Color(0xFFF43F5E)
        else -> Color(0xFF60A5FA)
    }
    val powerText = info.powerW?.let { "%.2f W".format(it) } ?: "Power: $NOT_SUPPORTED"
    val spoken = "Battery " + (percent?.let { "$it percent" } ?: "level unavailable") +
        ", ${info.status}, $powerText"

    Surface(
        color = Color(0xFF0B0B0F),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Column(
            modifier = Modifier.padding(vertical = 28.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern(timePattern)),
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Light,
            )
            Text(
                text = now.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(20.dp))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
                Gauge(fraction = (percent ?: 0) / 100f, color = gaugeColor, modifier = Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = percent?.let { "$it%" } ?: UNAVAILABLE,
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = info.status.uppercase(),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = powerText,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** A 270° ring that fills up with the battery level. */
@Composable
fun Gauge(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 14.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = Color.White.copy(alpha = 0.15f),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = 135f,
            sweepAngle = 270f * fraction.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** Each tile is (value, label). Missing readings never show fake numbers. */
fun buildTiles(info: BatteryInfo): List<Pair<String, String>> = listOf(
    (info.voltageV?.let { "%.2f V".format(it) } ?: UNAVAILABLE) to "Voltage",
    (info.temperatureC?.let { "%.1f °C".format(it) } ?: UNAVAILABLE) to "Temperature",
    (info.percent?.let { "$it%" } ?: UNAVAILABLE) to "Battery level",
    info.status to "Status",
    info.source to "Plugged",
    info.health to "Health",
    (info.currentMa?.let { "$it mA" } ?: NOT_SUPPORTED) to "Current",
    (info.chargeCounterMah?.let { "$it mAh" } ?: NOT_SUPPORTED) to "Charge counter",
    (info.designCapacityMah?.let { "$it mAh" } ?: UNAVAILABLE) to "Capacity",
    (info.technology ?: UNAVAILABLE) to "Technology",
    Build.MANUFACTURER.replaceFirstChar { it.uppercase() } to "Manufacturer",
    Build.MODEL to "Model",
)

@Composable
fun TileGrid(tiles: List<Pair<String, String>>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 340.dp) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { (value, label) -> Tile(value, label, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun Tile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .heightIn(min = 96.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier.heightIn(min = 96.dp).padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
                    "don't report current, charge counter or capacity, and units or signs can " +
                    "differ between manufacturers. Nexus Charge needs no permissions and does " +
                    "not collect, store, or send any data.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
