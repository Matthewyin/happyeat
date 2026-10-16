package com.happyeat.game.ui

/**
 * 全屏页面的返回去向。
 * MainActivity 负责把对应面板显示出来、并把游戏循环暂停或恢复。
 * 去向集中在这里，避免齿轮和排行榜各写一套布尔值后对不上。
 */
internal class OverlayRouter {

    enum class Place {
        MENU,
        PAUSE,
        GAME_OVER,
    }

    private var settingsFromPause = false
    private var leaderboardFromGameOver = false

    /**
     * 这一次设置是在对局暂停之后打开的（右上角齿轮，或暂停面板里的设置）。
     * 关掉设置要回到暂停，不能掉进主菜单，也不能让循环在设置页后面继续跑。
     */
    fun settingsWhilePaused() {
        settingsFromPause = true
    }

    /** 主菜单里进设置：返回主菜单。 */
    fun settingsFromMenu() {
        settingsFromPause = false
    }

    fun backFromSettings(): Place = if (settingsFromPause) Place.PAUSE else Place.MENU

    /** 记住排行榜是从结束面板打开的，还是从主菜单打开的。 */
    fun openLeaderboard(fromGameOver: Boolean) {
        leaderboardFromGameOver = fromGameOver
    }

    fun backFromLeaderboard(): Place {
        val place = if (leaderboardFromGameOver) Place.GAME_OVER else Place.MENU
        leaderboardFromGameOver = false
        return place
    }
}
