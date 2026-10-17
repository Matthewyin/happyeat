package com.happyeat.game.game

import com.happyeat.game.config.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对局结算：载具倒计时要跟着游戏时间走，死亡那一帧之后的食物不能再改分数。
 */
class GameWorldTest {

    @Test
    fun vehicleCountdownTicksDownAndClearsWhenTimeRunsOut() {
        val (world, probe) = newWorld()
        dropOnPlayer(world, ItemKind.CAR)
        world.update(16L)

        val mounted = probe.vehicle.last()
        assertTrue(mounted.riding)
        assertEquals(0, mounted.hits)
        val duration = GameConfig.VEHICLE_DURATION_MS
        fun seconds(ms: Long) = kotlin.math.ceil(ms / 1000.0).toInt()
        assertEquals(seconds(duration), mounted.seconds)
        val step = minOf(1_000L, duration - 1L).coerceAtLeast(0L)
        world.update(step)
        assertTrue(world.player.riding)
        assertEquals(seconds(duration - step), probe.vehicle.last().seconds)
        world.update(duration - step - 1L)
        assertTrue(world.player.riding)
        assertEquals(1, probe.vehicle.last().seconds)
        probe.vehicle.clear()
        world.update(1L)
        assertTrue(probe.vehicle.any { !it.riding && it.seconds == 0 })
        assertFalse(world.player.riding)
    }

    @Test
    fun freshVehicleAfterThreeCrushesSurvivesTheNextHit() {
        val (world, _) = newWorld()
        dropOnPlayer(world, ItemKind.CAR)
        world.update(16L)
        repeat(GameConfig.VEHICLE_MAX_HITS) {
            dropOnPlayer(world, ItemKind.ROCK)
            world.update(16L)
        }
        assertFalse(world.player.riding)

        dropOnPlayer(world, ItemKind.TANK)
        world.update(16L)
        assertTrue(world.player.riding)
        assertEquals(0, world.player.vehicleHits)

        dropOnPlayer(world, ItemKind.ROCK)
        world.update(16L)
        assertTrue(world.player.riding)
        assertEquals(1, world.player.vehicleHits)
    }

    @Test
    fun foodAfterTheKillingBlowDoesNotChangeTheRecordedScore() {
        val (world, probe) = newWorld()
        repeat(GameConfig.START_LIVES - 1) {
            dropOnPlayer(world, ItemKind.ROCK)
            world.update(16L)
            world.items.clear()
            // 等这一次受伤的无敌结束，下一次石头才能再扣命
            world.update(GameConfig.INVINCIBLE_AFTER_HIT_MS)
            world.items.clear()
        }
        assertEquals(1, probe.lives)
        val scoreBefore = world.score

        // 列表末尾先被处理。石头放在后面，先把最后一条命扣掉，豆子不该再加分
        dropOnPlayer(world, ItemKind.BEAN)
        dropOnPlayer(world, ItemKind.ROCK)
        world.update(16L)

        assertTrue(world.gameOver)
        assertEquals(scoreBefore, world.score)
        assertEquals(scoreBefore, probe.gameOverScore)
    }

    @Test
    fun foodTouchedBeforeTheKillingBlowInTheSameFrameStillScores() {
        val (world, probe) = newWorld()
        repeat(GameConfig.START_LIVES - 1) {
            dropOnPlayer(world, ItemKind.ROCK)
            world.update(16L)
            world.items.clear()
            world.update(GameConfig.INVINCIBLE_AFTER_HIT_MS)
            world.items.clear()
        }
        val scoreBefore = world.score

        // 豆子放在后面，先被接住，然后石头才送命。这一分要算进结算
        dropOnPlayer(world, ItemKind.ROCK)
        dropOnPlayer(world, ItemKind.BEAN)
        world.update(16L)

        assertTrue(world.gameOver)
        assertEquals(scoreBefore + GameConfig.SCORE_BEAN, world.score)
        assertEquals(scoreBefore + GameConfig.SCORE_BEAN, probe.gameOverScore)
    }

    private class Probe : GameWorld.Listener {
        var lives = GameConfig.START_LIVES
        var gameOverScore: Int? = null
        val vehicle = ArrayList<VehicleHud>()

        override fun onHudChanged(score: Int, lives: Int, pointsToNextLife: Int) {
            this.lives = lives
        }

        override fun onVehicleChanged(riding: Boolean, hitsUsed: Int, secondsLeft: Int) {
            vehicle.add(VehicleHud(riding, hitsUsed, secondsLeft))
        }

        override fun onGameOver(score: Int) {
            gameOverScore = score
        }
    }

    private data class VehicleHud(val riding: Boolean, val hits: Int, val seconds: Int)

    private fun newWorld(): Pair<GameWorld, Probe> {
        val probe = Probe()
        val world = GameWorld(probe)
        world.spawnEnabled = false
        world.onSize(1000f, 2000f)
        world.reset()
        probe.vehicle.clear()
        return world to probe
    }

    /** 把掉落物直接放到玩家身上，这一帧的位移仍会和判定框相交。 */
    private fun dropOnPlayer(world: GameWorld, kind: ItemKind) {
        val size = GameConfig.ITEM_SIZE * world.viewW
        val cy = world.player.displayCenterY(world.groundY, world.gameTimeMs, world.viewH)
        world.items.add(FallingItem(kind, size, 1f, world.player.x, cy))
    }
}
