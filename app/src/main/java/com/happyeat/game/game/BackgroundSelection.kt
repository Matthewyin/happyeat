package com.happyeat.game.game

import com.happyeat.game.config.GameConfig

enum class BgMode { AUTO, DAY, SUNSET, NIGHT }

object BackgroundSelection {
    val scenes = listOf(
        listOf("background", "background_sunset", "background_night"),
        listOf("background_orchard", "background_orchard_sunset", "background_orchard_night"),
    )

    fun resource(timeMs: Long, mode: BgMode): String {
        val phase = timeMs / GameConfig.BG_PHASE_MS
        val periods = scenes.first().size
        val scene = ((phase / periods) % scenes.size).toInt()
        val period = when (mode) {
            BgMode.AUTO -> (phase % periods).toInt()
            BgMode.DAY -> 0
            BgMode.SUNSET -> 1
            BgMode.NIGHT -> 2
        }
        return scenes[scene][period]
    }
}
