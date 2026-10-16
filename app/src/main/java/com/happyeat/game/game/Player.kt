package com.happyeat.game.game

import android.graphics.RectF
import com.happyeat.game.config.GameConfig
import kotlin.math.PI
import kotlin.math.sin

class Player {
    var x = 0f
    var sizePx = 0f
    var riding = false
    var vehicleKind: ItemKind? = null
    var vehicleHits = 0
    var vehicleExpiresAtMs = 0L
    var invincibleUntilMs = 0L

    private var jumpStartMs = Long.MIN_VALUE

    fun hitbox(groundY: Float, nowMs: Long, viewH: Float): RectF {
        val half = sizePx * GameConfig.HITBOX_SCALE / 2f
        val cy = groundY - sizePx / 2f - heightOffset(nowMs, viewH)
        return RectF(x - half, cy - half, x + half, cy + half)
    }

    fun isInvincible(nowMs: Long): Boolean = nowMs < invincibleUntilMs

    fun isAirborne(nowMs: Long): Boolean =
        jumpStartMs != Long.MIN_VALUE && nowMs - jumpStartMs < GameConfig.JUMP_AIRTIME_MS

    fun heightOffset(nowMs: Long, viewH: Float): Float {
        if (!isAirborne(nowMs)) return 0f
        val t = (nowMs - jumpStartMs).toFloat() / GameConfig.JUMP_AIRTIME_MS
        return (sin(t * PI) * GameConfig.JUMP_HEIGHT * viewH).toFloat()
    }

    fun jump(nowMs: Long) {
        if (!isAirborne(nowMs)) jumpStartMs = nowMs
    }

    fun mount(kind: ItemKind, nowMs: Long) {
        riding = true
        vehicleKind = kind
        vehicleExpiresAtMs = nowMs + GameConfig.VEHICLE_DURATION_MS
    }

    fun registerCrush() {
        vehicleHits++
        if (vehicleHits >= GameConfig.VEHICLE_MAX_HITS) riding = false
    }

    fun tickVehicle(nowMs: Long) {
        if (riding && nowMs >= vehicleExpiresAtMs) riding = false
    }

    fun resetState() {
        jumpStartMs = Long.MIN_VALUE
    }
}
