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
    var vehicleKind: ItemKind? = null
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
        if (!isAirborne(nowMs)) jumpStartMs = nowMs
    }

    fun mount(kind: ItemKind, nowMs: Long) {
        // 还骑在上一辆上：只换车型并刷新 10 秒，撞击次数接着算，不叠满也不清零。
        // 已经下车（时间走完，或撞满 3 次）后再捡：这是一辆新车，撞击从 0 重新计。
        // 清零必须写在 riding = true 之前，否则分不清是续骑还是新车。
        if (!riding) vehicleHits = 0
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
