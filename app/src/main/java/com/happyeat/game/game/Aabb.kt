package com.happyeat.game.game

/**
 * 轴对齐判定框。
 * 规则层不使用 Android 的 RectF，碰撞可以在 JVM 单测里直接跑。
 * 相接的边不算命中，和原来 RectF.intersects 的半开区间一致。
 */
class Aabb(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun intersects(other: Aabb): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
}
