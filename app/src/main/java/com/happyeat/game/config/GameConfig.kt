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

    const val RIDING_PLAYER_OFFSET = 0.2f
    const val RIDING_PLAYER_FALLBACK_SCALE = 0.8f

    const val PLAYER_SIZE = 0.13f
    const val ITEM_SIZE = 0.09f
    const val BEAST_SIZE = 0.12f

    const val HITBOX_SCALE = 0.75f

    const val JUMP_HEIGHT = 0.20f
    const val JUMP_AIRTIME_MS = 640L

    // 由最终六张素材的实际草地上沿核验，物理地面保持不变。
    val BG_GROUND_FRACTIONS: Map<String, Float> = mapOf(
        "background" to 0.833f,
        "background_sunset" to 0.805f,
        "background_night" to 0.805f,
        "background_orchard" to 0.73f,
        "background_orchard_sunset" to 0.72f,
        "background_orchard_night" to 0.72f,
    )
    const val BG_FADE_MS = 900L

    const val BG_PHASE_MS = 60_000L

    object Audio {
        const val BGM_VOLUME = 0.45f
        const val SFX_VOLUME = 0.9f
        const val MAX_SFX_STREAMS = 5
    }

    object Ui {
        const val CONTROL_INSET = 4f
        const val CONTROL_DEPTH = 5f
        const val CONTROL_HIGHLIGHT = 2f
        const val CONTROL_HIGHLIGHT_INSET = 12f
        const val CONTROL_PRESSED_MINT = 0xFF78CBA6.toInt()
        const val CONTROL_PRESSED_GOLD = 0xFFF3BC36.toInt()
        const val CONTROL_DISABLED = 0xFFDDE2DA.toInt()
        const val CONTROL_SHADOW = 0xFF72988E.toInt()
        const val CONTROL_GOLD_SHADOW = 0xFFBA883B.toInt()

        const val CONTROLS_HEIGHT = 112
        const val JUMP_AREA_WIDTH = 104
        const val INK = 0xFF062B45.toInt()
        const val CREAM_CARD = 0xFFFFF8E7.toInt()
        const val BLUE = 0xFF176DC5.toInt()
        const val BLUE_DARK = 0xFF0B477C.toInt()
        const val GOLD = 0xFFFFD34D.toInt()
        const val MINT = 0xFFA2E8C2.toInt()
        const val SKY = 0xFFBCEBFA.toInt()
        const val MUTED_DARK = 0xFF456476.toInt()
        const val TOMATO = 0xFFB53C3C.toInt()
        const val AMBER_DARK = BLUE_DARK
        const val SCRIM = 0x99062B45.toInt()
        const val RIPPLE = 0x33062B45
        const val RADIUS = 24f
        const val STROKE = 3
        const val GAP = 12
        const val PAD = 20
        const val SMALL = 8
        const val TOUCH = 52
        const val ICON = 28
        const val ART_ICON = 48
        const val TITLE = 26f
        const val BODY = 16f
        const val PRIMARY_TEXT = 20f
        const val TEXT_SHADOW = 2f
        const val SMALL_TEXT = 14f
        const val MENU_PRIMARY_TEXT = 22f
        const val MENU_PRIMARY_HEIGHT = 64
        const val MENU_GAP = 4
        const val HERO_WEIGHT = 1f
        const val HERO_MIN = 160
        const val BRAND_HEIGHT = 120
        const val ICON_LIMIT = 256
        const val DIALOG_WIDTH_MARGIN = 24
        const val HUD_SMALL = 12f
        const val HUD_SCORE = 18f
    }
}
