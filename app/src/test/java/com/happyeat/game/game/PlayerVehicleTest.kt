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
        player.mount(ItemKind.CAR, 0L)
        repeat(GameConfig.VEHICLE_MAX_HITS) { player.registerCrush() }
        assertFalse(player.riding)
        assertEquals(GameConfig.VEHICLE_MAX_HITS, player.vehicleHits)

        player.mount(ItemKind.TANK, 5_000L)
        assertTrue(player.riding)
        assertEquals(0, player.vehicleHits)
        assertEquals(ItemKind.TANK, player.vehicleKind)

        // 新车要能再承受满额之前的撞击，不能第一下就报废
        player.registerCrush()
        assertTrue(player.riding)
        assertEquals(1, player.vehicleHits)
    }

    @Test
    fun remountWhileRidingKeepsHitsAndRefreshesDuration() {
        val player = Player()
        player.mount(ItemKind.CAR, 0L)
        player.registerCrush()
        player.registerCrush()

        player.mount(ItemKind.TANK, 4_000L)

        assertTrue(player.riding)
        assertEquals(2, player.vehicleHits)
        assertEquals(ItemKind.TANK, player.vehicleKind)
        assertEquals(4_000L + GameConfig.VEHICLE_DURATION_MS, player.vehicleExpiresAtMs)
    }

    @Test
    fun expiryThenRemountStartsHitCountAtZero() {
        val player = Player()
        player.mount(ItemKind.CAR, 0L)
        player.registerCrush()
        player.registerCrush()
        player.tickVehicle(GameConfig.VEHICLE_DURATION_MS)
        assertFalse(player.riding)

        player.mount(ItemKind.CAR, GameConfig.VEHICLE_DURATION_MS)

        assertTrue(player.riding)
        assertEquals(0, player.vehicleHits)
        assertEquals(GameConfig.VEHICLE_DURATION_MS + GameConfig.VEHICLE_DURATION_MS, player.vehicleExpiresAtMs)
    }
}
