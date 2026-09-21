package com.yj.magiccircle

import kotlin.math.roundToInt

data class ChargeSnapshot(
    val percent: Int?,
    val temperatureC: Float?,
    val health: Int?,
    val status: Int?,
    val plugged: Int?,
) {
    companion object {
        fun fromRaw(
            level: Int?,
            scale: Int?,
            temperatureTenths: Int?,
            health: Int?,
            status: Int?,
            plugged: Int?,
        ): ChargeSnapshot {
            val percent = if (level != null && scale != null && scale > 0 && level in 0..scale) {
                (level.toFloat() / scale * 100).roundToInt()
            } else null
            return ChargeSnapshot(percent, temperatureTenths?.div(10f), health, status, plugged)
        }
    }
}
