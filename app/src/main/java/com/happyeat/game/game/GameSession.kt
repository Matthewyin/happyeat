package com.happyeat.game.game

import androidx.lifecycle.ViewModel
import com.happyeat.game.ui.OverlayRouter

internal class GameSession : ViewModel() {
    enum class Page { MENU, GAME, PAUSE, SETTINGS, HELP, LEADERBOARD, GAME_OVER, TUTORIAL }
    val world = GameWorld()
    val router = OverlayRouter()
    var page = Page.MENU
    var pausedByUi = false
    var helpFromPause = false
    var inMenu = true
    var settled = false
}
