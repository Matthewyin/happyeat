package com.happyeat.game.game

import com.happyeat.game.config.GameConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundSelectionTest {
    @Test fun automaticBoundariesAndWrap() {
        val expected = listOf("background", "background_sunset", "background_night", "background_orchard",
            "background_orchard_sunset", "background_orchard_night", "background")
        for (phase in expected.indices) {
            val time = phase * GameConfig.BG_PHASE_MS
            assertEquals(expected[phase], BackgroundSelection.resource(time, BgMode.AUTO))
            assertEquals(expected[phase], BackgroundSelection.resource(time + 1, BgMode.AUTO))
            if (phase > 0) assertEquals(expected[phase - 1], BackgroundSelection.resource(time - 1, BgMode.AUTO))
        }
    }
    @Test fun fixedModesKeepTimeOfDayAcrossScenes() {
        for ((mode, names) in listOf(
            BgMode.DAY to listOf("background", "background_orchard"),
            BgMode.SUNSET to listOf("background_sunset", "background_orchard_sunset"),
            BgMode.NIGHT to listOf("background_night", "background_orchard_night"))) {
            for (phase in 0..6) for (delta in listOf(0L, 1L, GameConfig.BG_PHASE_MS - 1)) {
                assertEquals(names[(phase / 3) % 2], BackgroundSelection.resource(phase * GameConfig.BG_PHASE_MS + delta, mode))
            }
        }
    }
    @Test fun longTimeDoesNotOverflowPhase() {
        val phase = Int.MAX_VALUE.toLong() * 6 + 4
        assertEquals("background_orchard_sunset", BackgroundSelection.resource(phase * GameConfig.BG_PHASE_MS, BgMode.AUTO))
        assertEquals("background_orchard_night", BackgroundSelection.resource(phase * GameConfig.BG_PHASE_MS, BgMode.NIGHT))
    }
}
