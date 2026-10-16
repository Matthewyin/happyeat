package com.happyeat.game.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 叠层返回去向：对局里开设置要回到暂停，结束面板进排行榜要回到结束面板。
 */
class OverlayRouterTest {

    @Test
    fun gearDuringPlayComesBackToPause() {
        val router = OverlayRouter()
        router.settingsWhilePaused()
        assertEquals(OverlayRouter.Place.PAUSE, router.backFromSettings())
    }

    @Test
    fun settingsFromMenuComesBackToMenu() {
        val router = OverlayRouter()
        router.settingsFromMenu()
        assertEquals(OverlayRouter.Place.MENU, router.backFromSettings())
    }

    @Test
    fun leaderboardFromGameOverComesBackToGameOver() {
        val router = OverlayRouter()
        router.openLeaderboard(fromGameOver = true)
        assertEquals(OverlayRouter.Place.GAME_OVER, router.backFromLeaderboard())
    }

    @Test
    fun leaderboardFromMenuComesBackToMenu() {
        val router = OverlayRouter()
        router.openLeaderboard(fromGameOver = false)
        assertEquals(OverlayRouter.Place.MENU, router.backFromLeaderboard())
    }
}
