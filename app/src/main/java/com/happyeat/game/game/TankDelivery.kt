package com.happyeat.game.game

import com.happyeat.game.config.GameConfig
import kotlin.math.abs

class TankDelivery(val direction: Int, val worldWidth: Float) {
    val sizePx = GameConfig.TANK_SIZE * worldWidth
    var x = if (direction > 0) -sizePx else worldWidth + sizePx
    val targetX = worldWidth * if (direction > 0) GameConfig.TANK_STOP_FRACTION else 1f - GameConfig.TANK_STOP_FRACTION
    var stoppedAtMs: Long? = null
        private set

    fun update(dt: Float, nowMs: Long): Boolean {
        if (stoppedAtMs != null) return false
        val distance = GameConfig.TANK_SPEED * worldWidth * dt
        if (abs(targetX - x) <= distance) {
            x = targetX
            stoppedAtMs = nowMs
            return true
        }
        x += direction * distance
        return false
    }
}

class ReturningNpc(var x: Float, val direction: Int, val sizePx: Float)
