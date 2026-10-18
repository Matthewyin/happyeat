package com.happyeat.game.game

import com.happyeat.game.config.GameConfig
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class TankDeliveryTest {
    private fun world() = GameWorld().apply { onSize(1000f, 2000f); spawnEnabled = false; reset() }
    private fun stop(w: GameWorld, direction: Int = 1): TankDelivery {
        val d = TankDelivery(direction, w.viewW)
        w.tankDelivery = d
        w.player.x = if (direction > 0) w.viewW else 0f
        while (d.stoppedAtMs == null) w.update(10)
        return d
    }
    private fun board(w: GameWorld, direction: Int = 1) {
        val d = stop(w, direction)
        w.player.x = d.x
        w.update(0)
        assertTrue(w.player.riding)
    }
    private fun beast(w: GameWorld, x: Float, kind: BeastKind = BeastKind.WOLF, speed: Float = 0f, direction: Int = -1) =
        Beast(kind, GameConfig.BEAST_SIZE * w.viewW, speed, x, direction).also { w.beasts.add(it) }
    private fun shot(w: GameWorld, x: Float, direction: Int = 1, speed: Float = 1500f) =
        Bullet(x, w.groundY - GameConfig.BULLET_HEIGHT * w.viewW, direction,
            GameConfig.BULLET_SIZE * w.viewW, speed).also { w.bullets.add(it) }
    private fun score(w: GameWorld, points: Int) {
        repeat(points) {
            w.items.add(FallingItem(ItemKind.BEAN, 90f, 0f, w.player.x,
                w.player.displayCenterY(w.groundY, w.gameTimeMs, w.viewH)))
            w.update(0)
        }
    }

    @Test fun sameSizeRebuildPreservesIncomingWorldPosition() {
        val w = world()
        w.tankDelivery = TankDelivery(-1, w.viewW)
        w.dragPlayerBy(-w.viewW)
        w.update(600)
        val x = w.player.x
        val deliveryX = w.tankDelivery!!.x
        val time = w.gameTimeMs
        w.onSize(w.viewW, w.viewH)
        assertEquals(x, w.player.x, 0f)
        assertEquals(deliveryX, w.tankDelivery!!.x, 0f)
        assertEquals(time, w.gameTimeMs)
    }

    @Test fun deliveryStopsOnBothSidesAndReturnsIndependently() {
        for (direction in listOf(-1, 1)) {
            val w = world()
            val d = TankDelivery(direction, w.viewW)
            w.tankDelivery = d
            w.player.x = d.x
            w.update(0)
            assertFalse(w.player.riding)
            w.player.x = if (direction > 0) w.viewW else 0f
            while (d.stoppedAtMs == null) w.update(10)
            assertEquals(w.viewW * if (direction > 0) 1f / 3 else 2f / 3, d.x, 0.001f)
            assertEquals(1, w.returningNpcs.size)
            val npc = w.returningNpcs.single()
            assertEquals(-direction, npc.direction)
            w.player.x = d.x
            w.update(0)
            assertTrue(w.player.riding)
            assertNull(w.tankDelivery)
            assertEquals(1, w.returningNpcs.size)
            val x = npc.x
            w.update(100)
            assertTrue((npc.x - x) * -direction > 0)
            w.update(3000)
            assertTrue(w.returningNpcs.isEmpty())
        }
    }

    @Test fun airborneIncludingFirstInstantCannotBoardButLandingCan() {
        val w = world()
        val d = stop(w)
        w.player.x = d.x
        w.performPlayerAction()
        w.update(0)
        assertFalse(w.player.riding)
        w.update(GameConfig.JUMP_AIRTIME_MS / 2)
        assertFalse(w.player.riding)
        w.update(GameConfig.JUMP_AIRTIME_MS / 2)
        assertTrue(w.player.riding)
        assertFalse(w.player.isAirborne(w.gameTimeMs))
    }

    @Test fun waitStartsAtStopAndExpiresBeforeContactAtExactBoundary() {
        val w = world()
        val d = stop(w)
        assertEquals(w.gameTimeMs, d.stoppedAtMs)
        w.update(GameConfig.TANK_WAIT_MS - 1)
        assertSame(d, w.tankDelivery)
        w.player.x = d.x
        w.update(1)
        assertNull(w.tankDelivery)
        assertFalse(w.player.riding)
        // 延长返回路程，隔离等待超时后的独立生命周期。
        val other = world()
        val late = TankDelivery(1, other.viewW).apply { x = targetX }
        other.tankDelivery = late
        other.player.x = other.viewW
        other.update(0)
        val npc = other.returningNpcs.single()
        npc.x = other.viewW
        other.update(GameConfig.TANK_WAIT_MS)
        assertNull(other.tankDelivery)
        assertSame(npc, other.returningNpcs.single())
        other.update(1000)
        assertTrue(other.returningNpcs.isEmpty())
    }

    @Test fun resupplyKeepsHitsDirectionAndRefreshesAmmoTime() {
        val w = world(); board(w)
        w.player.registerCrush(); w.player.registerCrush()
        w.performPlayerAction()
        val oldExpiry = w.player.vehicleExpiresAtMs
        board(w, -1)
        assertEquals(2, w.player.vehicleHits)
        assertEquals(1, w.player.lastFireDirection)
        assertEquals(3, w.player.ammoRemaining)
        assertTrue(w.player.vehicleExpiresAtMs > oldExpiry)
        w.player.registerCrush()
        assertFalse(w.player.riding)
        board(w)
        assertEquals(0, w.player.vehicleHits)
    }

    @Test fun actionUsesDeliveryDefaultAcrossCenterThenKeepsActualFireDirection() {
        for (direction in listOf(-1, 1)) {
            val w = world(); board(w, direction)
            w.dragPlayerBy(direction * w.viewW)
            w.performPlayerAction()
            assertEquals(direction, w.bullets.last().direction)
            assertEquals(direction, w.player.lastFireDirection)
            beast(w, 0f, direction = direction)
            w.performPlayerAction()
            assertEquals(-direction, w.bullets.last().direction)
            w.beasts.clear()
            w.dragPlayerBy(-direction * w.viewW)
            w.performPlayerAction()
            assertEquals(-direction, w.bullets.last().direction)
            w.performPlayerAction()
            assertEquals(3, w.bullets.size)
            assertEquals(0, w.player.ammoRemaining)
            assertFalse(w.player.isAirborne(w.gameTimeMs))
            w.player.jump(w.gameTimeMs)
            assertFalse(w.player.isAirborne(w.gameTimeMs))
            w.reset()
            assertNull(w.player.lastFireDirection)
            assertTrue(w.bullets.isEmpty() && w.returningNpcs.isEmpty())
            assertNull(w.tankDelivery)
        }
    }

    @Test fun ridingEdgesContainTankAndWalkingEdgesStayUnchanged() {
        val w = world(); board(w)
        w.dragPlayerBy(-w.viewW)
        assertEquals(GameConfig.TANK_SIZE * w.viewW / 2, w.player.x, 0f)
        w.dragPlayerBy(w.viewW * 2)
        assertEquals(w.viewW - GameConfig.TANK_SIZE * w.viewW / 2, w.player.x, 0f)
        w.update(GameConfig.VEHICLE_DURATION_MS)
        w.dragPlayerBy(-w.viewW)
        assertEquals(w.player.sizePx / 2, w.player.x, 0f)
    }

    @Test fun sweepFindsClosestWithoutPiercingOrDoubleScore() {
        for (direction in listOf(-1, 1)) {
            val w = world()
            w.player.x = 0f
            val near = beast(w, if (direction > 0) 400f else 600f)
            val far = beast(w, if (direction > 0) 600f else 400f)
            shot(w, if (direction > 0) 0f else 1000f, direction, 100000f)
            w.update(50)
            assertFalse(near in w.beasts)
            assertTrue(far in w.beasts)
            assertEquals(5, w.score)
            assertTrue(w.bullets.isEmpty())
        }
        val w = world(); w.player.x = 0f
        beast(w, 400f); shot(w, 0f, speed = 100000f); shot(w, 0f, speed = 100000f)
        w.update(50)
        assertEquals(5, w.score)
        assertTrue(w.beasts.isEmpty())
    }

    @Test fun secondBulletContinuesWhenFirstCandidateAlreadyKilled() {
        val w = world(); w.player.x = 0f
        beast(w, 400f); beast(w, 600f, BeastKind.TIGER)
        shot(w, 0f, speed = 100000f); shot(w, 0f, speed = 100000f)
        w.update(50)
        assertEquals(15, w.score)
        assertTrue(w.beasts.isEmpty() && w.bullets.isEmpty())
    }

    @Test fun movingBeastCrossesBulletAndExitDoesNotDiscardHit() {
        val w = world(); w.player.x = 0f
        beast(w, 900f, speed = 100000f)
        shot(w, 100f, speed = 100000f)
        w.update(50)
        assertEquals(5, w.score)
        assertTrue(w.beasts.isEmpty() && w.bullets.isEmpty())
    }

    @Test fun everyBeastUsesSameRewardForCrushAndShellWithoutShellDurability() {
        for (kind in BeastKind.entries) {
            val expected = if (kind == BeastKind.WOLF) 5 else 10
            val w = world(); board(w)
            beast(w, w.player.x, kind)
            shot(w, w.player.x)
            w.update(0)
            assertEquals(expected, w.score)
            assertEquals(0, w.player.vehicleHits)
            assertTrue(w.beasts.isEmpty())
            beast(w, w.player.x, kind)
            w.update(0)
            assertEquals(expected * 2, w.score)
            assertEquals(1, w.player.vehicleHits)
        }
    }

    @Test fun bulletSurvivesVehicleExpiryAndDeathFreezesScore() {
        val w = world(); board(w)
        w.update(GameConfig.VEHICLE_DURATION_MS - 1)
        w.performPlayerAction()
        w.update(1)
        assertFalse(w.player.riding)
        assertEquals(1, w.bullets.size)
        beast(w, w.bullets.single().x + 100f)
        w.update(100)
        assertEquals(5, w.score)
        while (w.lives > 1) {
            w.items.add(FallingItem(ItemKind.ROCK, 90f, 0f, w.player.x,
                w.player.displayCenterY(w.groundY, w.gameTimeMs, w.viewH)))
            w.update(0); w.update(GameConfig.INVINCIBLE_AFTER_HIT_MS)
        }
        beast(w, 700f); shot(w, 700f)
        w.items.add(FallingItem(ItemKind.ROCK, 90f, 0f, w.player.x,
            w.player.displayCenterY(w.groundY, w.gameTimeMs, w.viewH)))
        w.update(0)
        assertTrue(w.gameOver)
        assertEquals(5, w.score)
        w.update(500)
        assertEquals(5, w.score)
    }

    @Test fun jumpingBeastAboveBulletIsNotHitAcrossApexButLandingIsHit() {
        val w = world(); score(w, 201); w.player.x = 0f
        val noJump = object : Random() { override fun nextBits(bitCount: Int) = 0; override fun nextFloat() = 1f }
        val b = Beast(BeastKind.WOLF, 120f, 0f, 500f, 1, noJump)
        b.jumping = true
        b.update(0.20f, true)
        w.beasts.add(b)
        shot(w, 0f, speed = 10000f)
        w.update(100)
        assertTrue(b in w.beasts)
        assertEquals(201, w.score)
        shot(w, 500f, speed = 0f)
        w.update(300)
        assertFalse(b in w.beasts)
        assertEquals(206, w.score)
    }

    @Test fun resupplyUsesTankBaseInsteadOfRiderWidthAndTangentDoesNotBoard() {
        val w = world(); board(w)
        w.player.registerCrush(); w.player.registerCrush(); w.performPlayerAction(); w.performPlayerAction()
        val d = stop(w)
        val priorVersion = w.player.supplyVersion
        w.player.x = d.x + d.sizePx * GameConfig.HITBOX_SCALE + 0.001f
        w.update(0)
        assertSame(d, w.tankDelivery)
        w.player.x = d.x + 120f
        w.update(0)
        assertNull(w.tankDelivery)
        assertEquals(3, w.player.ammoRemaining)
        assertEquals(2, w.player.vehicleHits)
        assertEquals(priorVersion + 1, w.player.supplyVersion)
        val full = stop(w, -1)
        w.player.x = full.x
        w.update(0)
        assertEquals(priorVersion + 2, w.player.supplyVersion)
    }

    @Test fun nonCombatDeliveryAndNpcDoNotShieldDamageOrStopShell() {
        val w = world()
        val d = TankDelivery(1, w.viewW)
        w.tankDelivery = d
        w.update(1000)
        w.player.x = d.x
        w.items.add(FallingItem(ItemKind.ROCK, 90f, 0f, w.player.x,
            w.player.displayCenterY(w.groundY, w.gameTimeMs, w.viewH)))
        w.update(0)
        assertEquals(GameConfig.START_LIVES - 1, w.lives)
        assertFalse(w.player.riding)
        assertEquals(0, w.score)
        w.returningNpcs.add(ReturningNpc(300f, -1, 65f))
        beast(w, 600f)
        shot(w, 0f, speed = 10000f)
        w.update(100)
        assertEquals(5, w.score)
        assertNotNull(w.tankDelivery)
        assertTrue(w.returningNpcs.isNotEmpty())
    }

    @Test fun fractionalJumpBoundariesKeepFiftyMsAndLongSweepConsistent() {
        val never = object : Random() { override fun nextBits(bitCount: Int) = 0; override fun nextFloat() = 1f }
        for (start in listOf(0.493f, 0.977f)) for (duration in listOf(50L, 550L)) {
            fun scenario(): GameWorld {
                val w = world(); score(w, 201); w.player.x = 0f
                val b = Beast(BeastKind.WOLF, 120f, 0f, 500f, 1, never)
                b.jumping = true; b.update(start * GameConfig.BEAST_JUMP_DURATION_MS / 1000f, true)
                w.beasts.add(b)
                shot(w, 500f, speed = 0f)
                return w
            }
            val whole = scenario(); val reference = scenario()
            whole.update(duration)
            repeat(duration.toInt()) { reference.update(1) }
            assertEquals(reference.score, whole.score)
            assertEquals(reference.beasts.size, whole.beasts.size)
            if (start < 0.5f && duration == 50L) assertEquals(201, whole.score)
            else assertEquals(206, whole.score)
        }
    }

    @Test fun hudPublishesAmmoImmediatelyAndExpiryIsFreshMount() {
        val w = world()
        val ammo = mutableListOf<Int>()
        w.listener = object : GameWorld.Listener {
            override fun onHudChanged(score: Int, lives: Int, pointsToNextLife: Int) {}
            override fun onGameOver(score: Int) {}
            override fun onVehicleChanged(riding: Boolean, hitsUsed: Int, secondsLeft: Int, ammoRemaining: Int, supplyVersion: Int) { ammo.add(ammoRemaining) }
        }
        board(w); w.performPlayerAction(); w.performPlayerAction(); w.performPlayerAction()
        assertEquals(listOf(3, 2, 1, 0), ammo.takeLast(4))
        w.player.registerCrush(); w.player.registerCrush()
        val d = stop(w, -1)
        w.player.vehicleExpiresAtMs = w.gameTimeMs
        w.player.x = d.x
        w.update(0)
        assertTrue(w.player.riding)
        assertEquals(0, w.player.vehicleHits)
        assertEquals(3, w.player.ammoRemaining)
    }
}
