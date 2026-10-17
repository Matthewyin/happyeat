package com.happyeat.game.game

import com.happyeat.game.config.GameConfig
import org.junit.Assert.*
import org.junit.Test

class JumpHeightTest {
    @Test fun playerPeakExceedsEveryBeastAcrossPortraitSizes() {
        for ((width, height) in listOf(720f to 896f, 720f to 1280f, 1080f to 2400f, 1200f to 1920f)) {
            val world = GameWorld().apply { spawnEnabled = false; onSize(width, height); reset() }
            val player = world.player
            val standing = player.displayCenterY(world.groundY, world.gameTimeMs, height)
            world.jumpPlayer()
            world.update(GameConfig.JUMP_AIRTIME_MS / 2)
            val peak = player.displayCenterY(world.groundY, world.gameTimeMs, height)
            for (kind in BeastKind.entries) {
                val beast = Beast(kind, GameConfig.BEAST_SIZE * width, 0f, width / 2, 1)
                beast.jumping = true
                beast.update(GameConfig.BEAST_JUMP_DURATION_MS / 2000f)
                val beastCenter = world.groundY - beast.sizePx / 2 - beast.heightOffset(height)
                assertTrue("人物峰值须高于 $kind", standing - peak > beast.heightOffset(height))
                val playerBox = player.hitbox(world.groundY, world.gameTimeMs, height)
                val beastBox = beast.hitbox(world.groundY, height)
                assertTrue("${width}x${height} 的玩家底边须越过 $kind 顶边", playerBox.bottom < beastBox.top)
                assertFalse(playerBox.intersects(beastBox))
                assertTrue(peak < beastCenter)
                assertTrue(peak - player.displaySizePx / 2 < beastCenter - beast.sizePx / 2)
                assertTrue(peak + player.displaySizePx / 2 < beastCenter + beast.sizePx / 2)
                beast.update(GameConfig.BEAST_JUMP_DURATION_MS / 2000f)
                assertEquals(0f, beast.heightOffset(height), 0.001f)
            }
            world.update(GameConfig.JUMP_AIRTIME_MS - GameConfig.JUMP_AIRTIME_MS / 2)
            assertFalse(player.isAirborne(world.gameTimeMs))
            assertEquals(standing, player.displayCenterY(world.groundY, world.gameTimeMs, height), 0.001f)
        }
    }

    @Test fun configuredSizesKeepTouchingSeparateFromOverlap() {
        for ((width, height) in listOf(720f to 896f, 720f to 1280f, 1080f to 2400f, 1200f to 1920f)) {
            val world = GameWorld().apply { spawnEnabled = false; onSize(width, height); reset() }
            for (riding in listOf(false, true)) for (sprite in listOf(null, (width * GameConfig.PLAYER_SIZE).toInt().toFloat())) {
                world.player.riding = riding
                world.player.spriteSizePx = sprite
                val box = world.player.hitbox(world.groundY, world.gameTimeMs, height)
                for (kind in ItemKind.entries) {
                    val size = width * GameConfig.ITEM_SIZE
                    val item = FallingItem(kind, size, 0f,
                        box.right + size * GameConfig.HITBOX_SCALE / 2, (box.top + box.bottom) / 2)
                    assertEquals(size * GameConfig.HITBOX_SCALE, item.hitbox().right - item.hitbox().left, 0.001f)
                    // 浮点加减可能产生亚像素误差，向外留一个像素后再验证实际重叠。
                    item.x += 1f
                    assertFalse(box.intersects(item.hitbox()))
                    item.x -= 2f
                    assertTrue(box.intersects(item.hitbox()))
                }
                val beast = Beast(BeastKind.TIGER, width * GameConfig.BEAST_SIZE, 0f, world.player.x, 1)
                world.player.resetState()
                world.player.jump(world.gameTimeMs)
                val peakBox = world.player.hitbox(world.groundY, world.gameTimeMs + GameConfig.JUMP_AIRTIME_MS / 2, height)
                assertTrue(peakBox.bottom < beast.hitbox(world.groundY, height).top)
            }
        }
    }

    @Test fun airborneDragMovesBothWaysWithoutRestartingJumpAndClampsEdges() {
        val world = GameWorld().apply { spawnEnabled = false; onSize(1080f, 2400f); reset() }
        world.jumpPlayer()
        world.update(GameConfig.JUMP_AIRTIME_MS / 4)
        val x = world.player.x
        val height = world.player.heightOffset(world.gameTimeMs, world.viewH)
        world.dragPlayerBy(-world.viewW / 8)
        assertTrue(world.player.x < x)
        world.dragPlayerBy(world.viewW / 4)
        assertTrue(world.player.x > x)
        world.jumpPlayer()
        assertEquals(height, world.player.heightOffset(world.gameTimeMs, world.viewH), 0f)
        world.dragPlayerBy(-world.viewW * 2)
        assertEquals(world.player.sizePx / 2, world.player.x, 0f)
        world.dragPlayerBy(world.viewW * 2)
        assertEquals(world.viewW - world.player.sizePx / 2, world.player.x, 0f)
        world.update(GameConfig.JUMP_AIRTIME_MS - GameConfig.JUMP_AIRTIME_MS / 4)
        assertFalse(world.player.isAirborne(world.gameTimeMs))
        assertEquals(0f, world.player.heightOffset(world.gameTimeMs, world.viewH), 0f)
    }
}
