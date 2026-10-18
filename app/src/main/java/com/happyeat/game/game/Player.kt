package com.happyeat.game.game

import com.happyeat.game.config.GameConfig
import kotlin.math.PI
import kotlin.math.sin

class Player {
    var x = 0f
    var sizePx = 0f
    var spriteSizePx: Float? = null
    val displaySizePx: Float
        get() = spriteSizePx ?: (sizePx * if (riding) GameConfig.RIDING_PLAYER_FALLBACK_SCALE else 1f)

    fun displayCenterY(groundY: Float, nowMs: Long, viewH: Float): Float =
        groundY - sizePx / 2f - heightOffset(nowMs, viewH) -
            if (riding) sizePx * GameConfig.RIDING_PLAYER_OFFSET else 0f

    var riding = false
    var supplyVersion = 0
        private set
    var ammoRemaining = 0
    var lastFireDirection: Int? = null
    var vehicleHits = 0
    var vehicleExpiresAtMs = 0L
    var invincibleUntilMs = 0L

    private var jumpStartMs = Long.MIN_VALUE

    fun hitbox(groundY: Float, nowMs: Long, viewH: Float): Aabb {
        val half = displaySizePx * GameConfig.HITBOX_SCALE / 2f
        val cy = displayCenterY(groundY, nowMs, viewH)
        return Aabb(x - half, cy - half, x + half, cy + half)
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
        if (!riding && !isAirborne(nowMs)) jumpStartMs = nowMs
    }

    fun mount(nowMs: Long, defaultDirection: Int) {
        if (!riding) vehicleHits = 0
        riding = true
        supplyVersion++
        jumpStartMs = Long.MIN_VALUE
        if (lastFireDirection == null) lastFireDirection = defaultDirection
        ammoRemaining = GameConfig.TANK_AMMO
        vehicleExpiresAtMs = nowMs + GameConfig.VEHICLE_DURATION_MS
    }

    fun registerCrush() {
        vehicleHits++
        if (vehicleHits >= GameConfig.VEHICLE_MAX_HITS) { riding = false; ammoRemaining = 0 }
    }

    fun tickVehicle(nowMs: Long) {
        if (riding && nowMs >= vehicleExpiresAtMs) { riding = false; ammoRemaining = 0 }
    }

    fun resetState() {
        supplyVersion = 0
        ammoRemaining = 0
        lastFireDirection = null
        jumpStartMs = Long.MIN_VALUE
    }
}
