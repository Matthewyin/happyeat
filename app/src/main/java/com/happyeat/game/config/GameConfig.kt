package com.happyeat.game.config

object GameConfig {
    const val START_LIVES = 3
    const val MAX_LIVES = 30
    const val POINTS_PER_EXTRA_LIFE = 200
    const val INVINCIBLE_AFTER_HIT_MS = 2_000L

    const val FALL_SPAWN_INTERVAL_MS = 900L
    const val BEAST_SPAWN_INTERVAL_MS = 9_000L
    const val MAX_BEASTS_ON_SCREEN = 3

    const val DIFFICULTY_RAMP_MS = 300_000L
    const val DIFFICULTY_MAX_MULTIPLIER = 2.5f

    const val P_BEAN = 0.50f
    const val P_MEAT = 0.10f
    const val P_PIE = 0.08f
    const val P_VEHICLE = 0.04f

    const val SCORE_BEAN = 1
    const val SCORE_MEAT = 5
    const val SCORE_PIE = 10
    const val SCORE_CRUSH_HAZARD = 3
    const val SCORE_CRUSH_BEAST = 10

    const val VEHICLE_DURATION_MS = 10_000L
    const val VEHICLE_MAX_HITS = 3

    const val FALL_SPEED_BASE = 0.28f
    const val FALL_SPEED_VARIANCE = 0.15f
    const val BEAST_SPEED_BASE = 0.35f
    const val BEAST_JUMP_CHANCE_PER_SEC = 0.8f
    const val BEAST_JUMP_HEIGHT = 0.12f
    const val BEAST_JUMP_DURATION_MS = 500L

    const val PLAYER_SIZE = 0.11f
    const val ITEM_SIZE = 0.075f
    const val BEAST_SIZE = 0.12f

    const val HITBOX_SCALE = 0.75f

    const val JUMP_HEIGHT = 0.20f
    const val JUMP_AIRTIME_MS = 640L
    const val TAP_MAX_MS = 220L
    const val TAP_SLOP_DP = 12f

    const val BG_PHASE_MS = 60_000L
}
