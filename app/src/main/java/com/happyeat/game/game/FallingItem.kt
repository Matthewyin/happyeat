package com.happyeat.game.game

import android.graphics.RectF
import com.happyeat.game.config.GameConfig

enum class ItemKind { BEAN, MEAT, PIE, ROCK, BOMB, KNIFE, CAR, TANK }

class FallingItem(
    val kind: ItemKind,
    val sizePx: Float,
    val speedFactor: Float,
    var x: Float,
    var y: Float,
) {
    fun hitbox(): RectF {
        val half = sizePx * GameConfig.HITBOX_SCALE / 2f
        return RectF(x - half, y - half, x + half, y + half)
    }
}
