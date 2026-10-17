package com.happyeat.game.game

import com.happyeat.game.config.GameConfig
import org.junit.Assert.*
import org.junit.Test

class PlayerGeometryTest {
    @Test fun geometryFollowsWalkingRidingAndJumping() {
        for (spriteSize in listOf(null, 109f)) {
            val p = Player().apply { x = 250f; sizePx = 110.5f; spriteSizePx = spriteSize }
            verify(p, 0L, 0f)
            for (kind in listOf(ItemKind.CAR, ItemKind.TANK)) {
                p.mount(kind, 0L)
                verify(p, 0L, 0f)
                p.jump(0L)
                verify(p, GameConfig.JUMP_AIRTIME_MS / 2, GameConfig.JUMP_HEIGHT * 2000f)
                p.tickVehicle(GameConfig.VEHICLE_DURATION_MS)
                verify(p, maxOf(GameConfig.VEHICLE_DURATION_MS, GameConfig.JUMP_AIRTIME_MS), 0f)
                p.resetState()
                p.mount(kind, 0L)
                repeat(GameConfig.VEHICLE_MAX_HITS) { p.registerCrush() }
                assertFalse(p.riding)
                verify(p, 0L, 0f)
            }
        }
    }

    private fun verify(p: Player, now: Long, jump: Float) {
        val cy = 1800f - p.sizePx / 2f - jump -
            if (p.riding) p.sizePx * GameConfig.RIDING_PLAYER_OFFSET else 0f
        val size = p.spriteSizePx ?: p.sizePx * if (p.riding) GameConfig.RIDING_PLAYER_FALLBACK_SCALE else 1f
        val box = p.hitbox(1800f, now, 2000f)
        assertEquals(cy, p.displayCenterY(1800f, now, 2000f), 0.001f)
        assertEquals(cy, (box.top + box.bottom) / 2f, 0.001f)
        assertEquals(size * GameConfig.HITBOX_SCALE, box.right - box.left, 0.001f)
        assertEquals(size * GameConfig.HITBOX_SCALE, box.bottom - box.top, 0.001f)
        assertFalse(box.intersects(Aabb(box.right, box.top, box.right + 1f, box.bottom)))
        assertTrue(box.intersects(Aabb(box.right - 1f, box.top, box.right + 1f, box.bottom)))
    }
}
