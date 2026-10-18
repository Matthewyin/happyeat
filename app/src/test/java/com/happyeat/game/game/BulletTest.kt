package com.happyeat.game.game

import org.junit.Assert.*
import org.junit.Test

class BulletTest {
    private fun box(x: Float, y: Float = 0f) = Aabb(x, y, x + 2, y + 2)
    @Test fun relativeSweepCrossingOverlapZeroVelocityAndTangency() {
        assertEquals(0.4f, sweptHit(box(0f), box(20f), box(10f), box(10f))!!, 0.0001f)
        assertEquals(0.2f, sweptHit(box(0f), box(20f), box(10f), box(-10f))!!, 0.0001f)
        assertEquals(0f, sweptHit(box(0f), box(0f), box(1f), box(1f))!!, 0f)
        assertNull(sweptHit(box(0f), box(20f), box(10f, 2f), box(10f, 2f)))
        assertNull(sweptHit(box(0f), box(1f), box(10f), box(11f)))
        assertNotNull(sweptHit(box(20f), box(0f), box(10f), box(10f)))
        assertNull(sweptHit(box(0f), box(0f), box(2f), box(3f)))
        assertNull(sweptHit(box(0f), box(8f), box(10f), box(10f)))
        assertNotNull(sweptHit(box(0f), box(20f), box(10f, -10f), box(10f, 10f)))
    }
}
