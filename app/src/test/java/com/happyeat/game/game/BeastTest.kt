package com.happyeat.game.game

import com.happyeat.game.config.GameConfig
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class BeastTest {
    @Test fun dynamicGatePreservesDirectionAndResetsJumpWithoutSampling() {
        for (kind in BeastKind.entries) for (direction in listOf(-1, 1)) {
            var calls = 0
            val random = object : Random() {
                override fun nextBits(bitCount: Int): Int = error("不应调用")
                override fun nextFloat(): Float { calls++; return 0f }
            }
            val beast = Beast(kind, 100f, 20f, 500f, direction, random)
            beast.update(1f, false)
            assertEquals(500f + 20f * direction, beast.x, 0f)
            assertEquals(0, calls)
            assertFalse(beast.jumping)
            beast.update(0.01f, true)
            assertTrue(beast.jumping)
            beast.update(GameConfig.BEAST_JUMP_DURATION_MS / 2000f, true)
            assertEquals(GameConfig.BEAST_JUMP_HEIGHT * 2000f, beast.heightOffset(2000f), 0.001f)
            beast.update(GameConfig.BEAST_JUMP_DURATION_MS / 2000f, true)
            assertFalse(beast.jumping)
            beast.update(0.01f, true)
            val before = calls
            beast.update(0.01f, false)
            assertFalse(beast.jumping)
            assertEquals(0f, beast.jumpT, 0f)
            assertEquals(before, calls)
            assertEquals(direction, beast.direction)
        }
    }
}
