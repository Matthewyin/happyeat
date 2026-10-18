package com.happyeat.game.game

import com.happyeat.game.config.GameConfig
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class BeastKind { TIGER, LION, WOLF }

class Beast(
    val kind: BeastKind,
    val sizePx: Float,
    val speedPxPerSec: Float,
    var x: Float,
    val direction: Int,
    private val rand: Random = Random.Default,
) {
    var jumping = false
    var jumpT = 0f
        private set

    // 在顶点和落地处截断扫掠线段，避免一个线段横跨正弦曲线的转折。
    val secondsToJumpBoundary: Float
        get() = if (jumping) ((if (jumpT < 0.5f) 0.5f else 1f) - jumpT) *
            GameConfig.BEAST_JUMP_DURATION_MS / 1000f else Float.POSITIVE_INFINITY

    fun update(dt: Float, allowJump: Boolean) {
        x += speedPxPerSec * direction * dt
        if (!allowJump) {
            jumping = false
            jumpT = 0f
        } else if (jumping) {
            jumpT += dt * 1000f / GameConfig.BEAST_JUMP_DURATION_MS
            if (jumpT >= 1f) {
                jumping = false
                jumpT = 0f
            }
        } else if (rand.nextFloat() < GameConfig.BEAST_JUMP_CHANCE_PER_SEC * dt) {
            jumping = true
        }
    }

    fun heightOffset(viewH: Float): Float =
        if (jumping) (sin(jumpT * PI) * GameConfig.BEAST_JUMP_HEIGHT * viewH).toFloat() else 0f

    fun hitbox(groundY: Float, viewH: Float): Aabb {
        val half = sizePx * GameConfig.HITBOX_SCALE / 2f
        val cy = groundY - sizePx / 2f - heightOffset(viewH)
        return Aabb(x - half, cy - half, x + half, cy + half)
    }
}
