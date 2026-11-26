package com.nexuscharge.app

import android.content.Context
import android.content.Intent
import android.os.BatteryManager

const val UNAVAILABLE = "Unavailable"
const val NOT_SUPPORTED = "Not supported"

/** One snapshot of the battery. A null value means the device did not report it. */
data class BatteryInfo(
    val percent: Int?,
    val isCharging: Boolean,
    val isFull: Boolean,
    val status: String,
    val source: String,
    val health: String,
    val temperatureC: Float?,
    val voltageV: Float?,
    val technology: String?,
    val currentMa: Int?,
    val chargeCounterMah: Int?,
)

/** Turns the ACTION_BATTERY_CHANGED intent (may be null) into safe, readable values. */
fun readBatteryInfo(context: Context, intent: Intent?): BatteryInfo {
    fun extra(key: String, missing: Int) = intent?.getIntExtra(key, missing) ?: missing

    val level = extra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = extra(BatteryManager.EXTRA_SCALE, -1)
    val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null

    val statusCode = extra(BatteryManager.EXTRA_STATUS, -1)
    val plugged = extra(BatteryManager.EXTRA_PLUGGED, -1)

    // Temperature is sent in tenths of a degree Celsius.
    val tempTenths = extra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
    val temperatureC = if (tempTenths in -400..1000) tempTenths / 10f else null

    // Voltage is normally millivolts; a few devices send volts instead.
    val rawVoltage = extra(BatteryManager.EXTRA_VOLTAGE, -1)
    val voltageV = when {
        rawVoltage <= 0 -> null
        rawVoltage < 100 -> rawVoltage.toFloat()
        else -> rawVoltage / 1000f
    }

    val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
        ?.trim()?.takeIf { it.isNotEmpty() }

    // Optional readings. Unsupported devices return Int.MIN_VALUE (or 0).
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    fun property(id: Int): Int? =
        manager?.getIntProperty(id)?.takeIf { it != Int.MIN_VALUE && it != 0 }

    val currentMa = property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.let { it / 1000 }
    val chargeCounterMah = property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        ?.takeIf { it > 0 }?.let { it / 1000 }

    return BatteryInfo(
        percent = percent,
        isCharging = statusCode == BatteryManager.BATTERY_STATUS_CHARGING,
        isFull = statusCode == BatteryManager.BATTERY_STATUS_FULL,
        status = statusText(statusCode),
        source = sourceText(plugged),
        health = healthText(extra(BatteryManager.EXTRA_HEALTH, -1)),
        temperatureC = temperatureC,
        voltageV = voltageV,
        technology = technology,
        currentMa = currentMa,
        chargeCounterMah = chargeCounterMah,
    )
}

private fun statusText(code: Int) = when (code) {
    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Plugged in, not charging"
    BatteryManager.BATTERY_STATUS_FULL -> "Full"
    else -> "Unknown"
}

private fun sourceText(plugged: Int) = when (plugged) {
    0 -> "Not plugged in"
    BatteryManager.BATTERY_PLUGGED_AC -> "AC charger"
    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
    else -> "Unknown"
}

private fun healthText(code: Int) = when (code) {
    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
    BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
    else -> UNAVAILABLE
}
