package com.happyeat.game.game

import com.happyeat.game.config.GameConfig
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class ScoreStagesTest {
    private class FixedRandom(var value: Float = 0.99f, val choice: Int = 0, val left: Boolean = true) : Random() {
        var floatCalls = 0
        var booleanCalls = 0
        override fun nextBits(bitCount: Int): Int = error("必须明确指定随机分支")
        override fun nextFloat(): Float { floatCalls++; return value }
        override fun nextInt(until: Int): Int = choice.also { require(it < until) }
        override fun nextBoolean(): Boolean { booleanCalls++; return left }
    }

    private fun world(random: Random = FixedRandom()) = GameWorld(rand = random).apply {
        onSize(1000f, 2000f)
        reset()
    }

    private fun contact(w: GameWorld, kind: ItemKind) {
        w.items.add(FallingItem(kind, GameConfig.ITEM_SIZE * w.viewW, 0f, w.player.x,
            w.player.displayCenterY(w.groundY, w.gameTimeMs, w.viewH)))
        w.update(0)
    }

    private fun score(w: GameWorld, target: Int) {
        val enabled = w.spawnEnabled
        w.spawnEnabled = false
        while (w.score + GameConfig.SCORE_PIE <= target) contact(w, ItemKind.PIE)
        while (w.score < target) contact(w, ItemKind.BEAN)
        w.spawnEnabled = enabled
        assertEquals(target, w.score)
    }

    // 小步推进并移开实体，隔离随机碰撞，不改变生成计时。
    private fun advance(w: GameWorld, ms: Long) {
        var remaining = ms
        while (remaining > 0) {
            w.items.clear()
            w.beasts.forEach { it.x = if (it.direction == 1) 0f else w.viewW }
            val step = minOf(remaining, 100L)
            w.update(step)
            remaining -= step
        }
    }

    private fun expectSpawnAfter(w: GameWorld, interval: Long) {
        val before = w.beasts.size
        advance(w, interval - 1)
        assertEquals(before, w.beasts.size)
        advance(w, 1)
        assertEquals(before + 1, w.beasts.size)
    }

    @Test fun actualItemGenerationUsesFoodWeightsAndRestoresEveryKindAt51() {
        val cases = listOf(
            Triple(0f, 0, ItemKind.BEAN),
            Triple(0.50f, 0, ItemKind.MEAT),
            Triple(0.61f, 0, ItemKind.PIE),
            Triple(0.69f, 0, null),
            Triple(0.72f, 0, ItemKind.ROCK),
            Triple(0.99f, 1, ItemKind.BOMB),
            Triple(0.99f, 2, ItemKind.KNIFE))
        for (points in listOf(0, 49, 50, 51, 99, 100)) {
            for ((roll, choice, expected) in cases) {
                val w = world(FixedRandom(roll, choice))
                score(w, points)
                advance(w, 900)
                if (points > 50 && expected == null) {
                    assertTrue(w.items.isEmpty())
                    assertEquals(1, w.tankDelivery?.direction)
                    continue
                }
                assertNull(w.tankDelivery)
                assertEquals(1, w.items.size)
                val foodRoll = roll * (GameConfig.P_BEAN + GameConfig.P_MEAT + GameConfig.P_PIE)
                val food = when {
                    foodRoll < GameConfig.P_BEAN -> ItemKind.BEAN
                    foodRoll < GameConfig.P_BEAN + GameConfig.P_MEAT -> ItemKind.MEAT
                    else -> ItemKind.PIE
                }
                assertEquals(if (points <= 50) food else expected, w.items.single().kind)
            }
        }
        for ((roll, expected) in listOf(0f to ItemKind.BEAN, 0.73f to ItemKind.BEAN,
            0.74f to ItemKind.MEAT, 0.88f to ItemKind.MEAT,
            0.89f to ItemKind.PIE, 0.99f to ItemKind.PIE)) {
            val w = world(FixedRandom(roll))
            advance(w, 900)
            assertEquals(expected, w.items.single().kind)
        }
        val tank = world(FixedRandom(0.69f, left = false))
        score(tank, 51)
        advance(tank, 900)
        assertTrue(tank.items.isEmpty())
        assertEquals(-1, tank.tankDelivery?.direction)
    }

    @Test fun deliveryIntervalBoundariesBusyFoodFallbackAndLongFrameStayExact() {
        val food = GameConfig.P_BEAN + GameConfig.P_MEAT + GameConfig.P_PIE
        for ((roll, delivery) in listOf(food - 0.0001f to false, food to true,
            food + GameConfig.P_VEHICLE - 0.0001f to true, food + GameConfig.P_VEHICLE to false)) {
            val w = world(FixedRandom(roll)); score(w, 51); w.update(900)
            assertEquals(delivery, w.tankDelivery != null)
            assertEquals(if (delivery) 0 else 1, w.items.size)
        }
        for ((fallback, expected) in listOf(0f to ItemKind.BEAN, 0.8f to ItemKind.MEAT, 0.99f to ItemKind.PIE)) {
            val random = object : Random() {
                var calls = 0
                override fun nextBits(bitCount: Int) = 0
                override fun nextFloat(): Float = if (calls++ == 0) 0.69f else fallback
            }
            val w = world(random); score(w, 51)
            val d = TankDelivery(-1, w.viewW); w.tankDelivery = d
            w.update(900)
            assertSame(d, w.tankDelivery)
            assertEquals(expected, w.items.single().kind)
        }
        val w = world(FixedRandom(0.69f)); score(w, 51)
        w.update(3600)
        assertNotNull(w.tankDelivery)
        assertEquals(3, w.items.size)
        assertTrue(w.items.all { it.kind == ItemKind.BEAN })
        assertEquals(1, w.returningNpcs.size)
        // 返回 NPC 不占用送车名额。
        val d = w.tankDelivery!!; w.player.x = d.x; w.update(0)
        assertNull(w.tankDelivery)
        w.update(900)
        assertNotNull(w.tankDelivery)
    }

    @Test fun nonemptyBatchKeepsSideWithoutDrawingAnotherDirection() {
        val random = FixedRandom(0f)
        val w = world(random); score(w, 101); w.update(0)
        expectSpawnAfter(w, 12000)
        val calls = random.booleanCalls
        val direction = w.beasts.single().direction
        expectSpawnAfter(w, 12000)
        assertEquals(calls, random.booleanCalls)
        assertTrue(w.beasts.all { it.direction == direction })
        w.beasts.clear()
        expectSpawnAfter(w, 12000)
        assertEquals(calls + 1, random.booleanCalls)
    }

    @Test fun foodCollisionCrosses50WithoutResettingFallTimer() {
        val w = world(FixedRandom(0.99f))
        score(w, 49)
        advance(w, 800)
        contact(w, ItemKind.PIE)
        assertEquals(59, w.score)
        advance(w, 100)
        assertEquals(ItemKind.ROCK, w.items.single().kind)
    }

    @Test fun longLowScoreNeverAccumulatesBeastsAndUnlockWaitsFullInterval() {
        val w = world()
        score(w, 100)
        advance(w, GameConfig.DIFFICULTY_RAMP_MS + 1000)
        assertTrue(w.beasts.isEmpty())
        contact(w, ItemKind.BEAN)
        w.update(1000)
        assertTrue(w.beasts.isEmpty())
        expectSpawnAfter(w, 12000)
        assertFalse(w.beasts.single().jumping)
    }

    @Test fun initialLongLowScoreStillProducesOnlyFood() {
        val w = world()
        repeat(3100) {
            advance(w, 100)
            assertTrue(w.items.all { it.kind in listOf(ItemKind.BEAN, ItemKind.MEAT, ItemKind.PIE) })
            assertTrue(w.beasts.isEmpty())
        }
        assertEquals(0, w.score)
    }

    @Test fun crossing100ByPieStartsFreshTimer() {
        val w = world()
        score(w, 99)
        advance(w, 11999)
        contact(w, ItemKind.PIE)
        assertEquals(109, w.score)
        w.update(0)
        expectSpawnAfter(w, 12000)
    }

    @Test fun intervalsFollowScoreNotElapsedTimeAndCapAt600() {
        for ((points, interval) in listOf(101 to 12000L, 199 to 12000L, 200 to 12000L,
            201 to 12000L, 299 to 12000L, 300 to 12000L, 301 to 11980L,
            450 to 9000L, 600 to 6000L, 601 to 6000L, 10000 to 6000L)) {
            val w = world()
            score(w, points)
            w.update(0)
            expectSpawnAfter(w, interval)
        }
    }

    @Test fun crossing300DiscardsRemainderButLaterScoresDoNotStarveSpawns() {
        val w = world()
        score(w, 299)
        w.update(0)
        advance(w, 11900)
        contact(w, ItemKind.PIE)
        assertEquals(309, w.score)
        w.update(0)
        expectSpawnAfter(w, 11820)
        w.beasts.clear()
        repeat(10) {
            advance(w, 1000)
            contact(w, ItemKind.BEAN)
        }
        assertEquals(319, w.score)
        expectSpawnAfter(w, 1620)
    }

    @Test fun crossing200EnablesExistingBeastsNextFrameWithoutResettingTimer() {
        val random = FixedRandom(0f)
        val w = world(random)
        score(w, 199)
        w.update(0)
        advance(w, 11000)
        val beast = Beast(BeastKind.WOLF, 100f, 0f, 0f, 1, random)
        w.beasts.add(beast)
        val before = random.floatCalls
        contact(w, ItemKind.PIE)
        assertEquals(209, w.score)
        assertFalse(beast.jumping)
        assertEquals(before, random.floatCalls)
        advance(w, 1)
        assertTrue(beast.jumping)
        expectSpawnAfter(w, 999)
        assertTrue(w.beasts.last().jumping)
    }

    @Test fun generatedBeastsUseInjectedRandomForKindDirectionAndJump() {
        for (kind in BeastKind.entries) for (left in listOf(false, true)) {
            val w = world(FixedRandom(0f, kind.ordinal, left))
            score(w, 201)
            w.update(0)
            expectSpawnAfter(w, 12000)
            val beast = w.beasts.single()
            assertEquals(kind, beast.kind)
            assertEquals(if (left) 1 else -1, beast.direction)
            assertTrue(beast.jumping)
        }
    }

    @Test fun exact200DisablesJumpAnd201EnablesAllExistingKindsEvenWithoutSpawning() {
        for (kind in BeastKind.entries) {
            val random = FixedRandom(0f)
            val w = world()
            score(w, 200)
            w.spawnEnabled = false
            val beast = Beast(kind, 100f, 10f, 0f, 1, random)
            w.beasts.add(beast)
            w.update(100)
            assertFalse(beast.jumping)
            assertEquals(0, random.floatCalls)
            contact(w, ItemKind.BEAN)
            assertFalse(beast.jumping)
            w.update(1)
            assertTrue(beast.jumping)
            assertEquals(1, random.floatCalls)
        }
    }

    @Test fun fullScreenDiscardsOpportunityAndLongFrameDoesNotBuildDebt() {
        val random = FixedRandom(0f)
        val w = world(random)
        score(w, 101)
        w.update(0)
        repeat(GameConfig.MAX_BEASTS_ON_SCREEN) {
            w.beasts.add(Beast(BeastKind.TIGER, 100f, 0f, 0f, 1))
        }
        advance(w, 15000)
        w.beasts.clear()
        expectSpawnAfter(w, 9000)
        w.beasts.clear()
        val before = random.booleanCalls
        w.update(36000)
        assertEquals(before + 1, random.booleanCalls)
        // 长帧可能让新兽直接离场，用后续完整等待验证没有欠账。
        w.beasts.clear()
        expectSpawnAfter(w, 12000)
    }

    @Test fun resetClearsHighScoreEntitiesTimeAndBothTimers() {
        val w = world()
        score(w, 600)
        w.update(0)
        expectSpawnAfter(w, 6000)
        advance(w, 5500)
        w.reset()
        assertEquals(0, w.score)
        assertEquals(0L, w.gameTimeMs)
        assertTrue(w.beasts.isEmpty())
        assertTrue(w.items.isEmpty())
        advance(w, 900)
        assertEquals(ItemKind.PIE, w.items.single().kind)
        score(w, 101)
        w.update(0)
        expectSpawnAfter(w, 12000)
    }
}
