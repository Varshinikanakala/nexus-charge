package com.nexuscharge.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.MediaStore
import android.text.format.DateFormat
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val Pink = Color(0xFFFF2D6F)
private val ButtonGrey = Color(0xFF1E1E1E)

/** Turns the camera flash on or off. Needs no permission. */
class Torch(context: Context) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val cameraId: String? = try {
        val m = manager
        if (m == null) null else m.cameraIdList.firstOrNull { id ->
            m.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: Exception) {
        null
    }

    /** Returns true if it worked. */
    fun set(on: Boolean): Boolean = try {
        val m = manager
        val id = cameraId
        if (m != null && id != null) {
            m.setTorchMode(id, on)
            true
        } else false
    } catch (e: Exception) {
        false
    }
}

/** Sends play/pause/next/previous to whatever music app is playing. */
private fun mediaKey(context: Context, keyCode: Int) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
}

private fun changeVolume(context: Context, direction: Int) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
}

private fun openCamera(context: Context) {
    try {
        context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
    }
}

private fun shortTime(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "$m min"

@Composable
fun ChargingScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tracker = remember { ChargeTracker() }
    val torch = remember { Torch(context) }
    var torchOn by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<LiveStats?>(null) }
    var info by remember { mutableStateOf<BatteryInfo?>(null) }

    BackHandler(onBack = onClose)

    // Full screen + keep the screen awake while this is shown.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            bars?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (torchOn) torch.set(false)
        }
    }

    // Refresh every second while visible.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                stats = tracker.update(context)
                info = readBatteryInfo(
                    context,
                    context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)),
                )
                delay(1_000)
            }
        }
    }

    val now = rememberNow()
    val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val s = stats
    val b = info
    val level = s?.precisePercent ?: s?.level?.toFloat()
    val percentText = s?.precisePercent?.let { "%.2f%%".format(it) }
        ?: s?.level?.let { "$it%" } ?: "—"
    val estimate = when {
        b?.isFull == true -> "Full"
        s?.isCharging != true -> "—"
        else -> s.minutesToFull?.let { shortTime(it) } ?: "…"
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = now.format(DateTimeFormatter.ofPattern(timePattern)),
                    color = Color.White,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 18.sp,
                )
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
                Gauge(fraction = (level ?: 0f) / 100f, color = Pink, modifier = Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(percentText, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = (b?.status ?: "").uppercase(),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        letterSpacing = 1.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatBox(b?.voltageV?.let { "%.3f V".format(it) } ?: "—", "Voltage", Modifier.weight(1f))
                StatBox(b?.temperatureC?.let { "%.1f°C".format(it) } ?: "—", "Temp", Modifier.weight(1f))
                StatBox(estimate, "Estimate", Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                RoundButton("⏮", "Previous track") { mediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
                RoundButton("⏯", "Play or pause") { mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
                RoundButton("⏭", "Next track") { mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                RoundButton("🔉", "Volume down") { changeVolume(context, AudioManager.ADJUST_LOWER) }
                RoundButton("🔊", "Volume up") { changeVolume(context, AudioManager.ADJUST_RAISE) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton("🔦", if (torchOn) "Turn torch off" else "Turn torch on", active = torchOn) {
                    if (torch.set(!torchOn)) torchOn = !torchOn
                    else Toast.makeText(context, "Torch not available", Toast.LENGTH_SHORT).show()
                }
                Text(
                    text = b?.powerW?.let { "%.2f W".format(it) } ?: "— W",
                    color = Color.White,
                    fontSize = 28.sp,
                )
                RoundButton("📷", "Open camera") { openCamera(context) }
            }
        }

        // Close button, top right.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(ButtonGrey)
                .clickable(onClickLabel = "Close", onClick = onClose)
                .semantics { contentDescription = "Close full screen" },
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = Color.White, fontSize = 20.sp)
        }
    }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .border(1.5.dp, Color(0xFF2A2A2A), RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp, horizontal = 6.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = Color.White, fontSize = 18.sp, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
    }
}

@Composable
private fun RoundButton(
    symbol: String,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(if (active) Color(0xFFFFD54F) else ButtonGrey)
            .clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 24.sp)
    }
}
