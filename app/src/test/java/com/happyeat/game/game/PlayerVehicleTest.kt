package com.happyeat.game.game

import com.happyeat.game.config.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 载具撞击次数：骑着的时候再捡只刷新时间；报废后再捡是一辆新车，撞击从 0 计。
 */
class PlayerVehicleTest {

    @Test
    fun scrapThenRemountStartsHitCountAtZero() {
        val player = Player()
        player.mount(0L, 1)
        repeat(GameConfig.VEHICLE_MAX_HITS) { player.registerCrush() }
        assertFalse(player.riding)
        assertEquals(GameConfig.VEHICLE_MAX_HITS, player.vehicleHits)

        player.mount(5_000L, 1)
        assertTrue(player.riding)
        assertEquals(0, player.vehicleHits)
        assertEquals(GameConfig.TANK_AMMO, player.ammoRemaining)

        // 新车要能再承受满额之前的撞击，不能第一下就报废
        player.registerCrush()
        assertTrue(player.riding)
        assertEquals(1, player.vehicleHits)
    }

    @Test
    fun remountWhileRidingKeepsHitsAndRefreshesDuration() {
        val player = Player()
        player.mount(0L, 1)
        player.registerCrush()
        player.registerCrush()

        player.mount(4_000L, 1)

        assertTrue(player.riding)
        assertEquals(2, player.vehicleHits)
        assertEquals(GameConfig.TANK_AMMO, player.ammoRemaining)
        assertEquals(4_000L + GameConfig.VEHICLE_DURATION_MS, player.vehicleExpiresAtMs)
    }

    @Test
    fun expiryThenRemountStartsHitCountAtZero() {
        val player = Player()
        player.mount(0L, 1)
        player.registerCrush()
        player.registerCrush()
        player.tickVehicle(GameConfig.VEHICLE_DURATION_MS)
        assertFalse(player.riding)

        player.mount(GameConfig.VEHICLE_DURATION_MS, 1)

        assertTrue(player.riding)
        assertEquals(0, player.vehicleHits)
        assertEquals(GameConfig.VEHICLE_DURATION_MS + GameConfig.VEHICLE_DURATION_MS, player.vehicleExpiresAtMs)
    }
}
