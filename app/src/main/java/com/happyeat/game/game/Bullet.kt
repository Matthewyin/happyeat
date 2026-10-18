package com.happyeat.game.game

import com.happyeat.game.config.GameConfig
import kotlin.math.max
import kotlin.math.min

class Bullet(var x: Float, val y: Float, val direction: Int, val sizePx: Float, val speed: Float) {
    fun hitbox(): Aabb {
        val half = sizePx * GameConfig.HITBOX_SCALE / 2f
        return Aabb(x - half, y - half, x + half, y + half)
    }
}

// 相对运动求最早进入时间；严格相切不算相交。
internal fun sweptHit(a: Aabb, endA: Aabb, b: Aabb, endB: Aabb): Float? {
    var enter = 0f
    var exit = 1f
    fun axis(lowA: Float, highA: Float, lowB: Float, highB: Float, velocity: Float): Boolean {
        if (velocity == 0f) return lowA < highB && highA > lowB
        val first = (lowB - highA) / velocity
        val last = (highB - lowA) / velocity
        enter = max(enter, min(first, last))
        exit = min(exit, max(first, last))
        return enter < exit
    }
    if (!axis(a.left, a.right, b.left, b.right, endA.left - a.left - (endB.left - b.left))) return null
    if (!axis(a.top, a.bottom, b.top, b.bottom, endA.top - a.top - (endB.top - b.top))) return null
    return enter.takeIf { it < exit }
}
