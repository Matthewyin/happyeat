package com.happyeat.game

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.happyeat.game.game.GameSession
import com.happyeat.game.game.GameSession.Page
import kotlinx.coroutines.cancel
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.happyeat.game.config.GameConfig
import com.happyeat.game.data.AppDatabase
import com.happyeat.game.data.Prefs
import com.happyeat.game.data.ScoreRecord
import com.happyeat.game.game.BgMode
import com.happyeat.game.game.BgmTrack
import com.happyeat.game.game.GameView
import com.happyeat.game.game.SoundEvent
import com.happyeat.game.game.SoundManager
import com.happyeat.game.ui.GameOverPanel
import com.happyeat.game.ui.OverlayRouter
import com.happyeat.game.ui.HelpPage
import com.happyeat.game.ui.LeaderboardPage
import com.happyeat.game.ui.MenuPage
import com.happyeat.game.ui.PausePanel
import com.happyeat.game.ui.SettingsPage
import com.happyeat.game.ui.TutorialPanel
import com.happyeat.game.ui.showExitConfirmDialog
import com.happyeat.game.ui.showNameDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.DateFormat
import kotlin.math.max

class MainActivity : AppCompatActivity(), GameView.HudListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dateFormat by lazy { DateFormat.getDateInstance(DateFormat.SHORT) }

    private lateinit var gameView: GameView
    private lateinit var scoreText: TextView
    private lateinit var livesText: TextView
    private lateinit var nextLifeText: TextView
    private lateinit var vehicleText: TextView
    private lateinit var pausePanel: PausePanel
    private lateinit var gameOverPanel: GameOverPanel
    private lateinit var tutorialPanel: TutorialPanel
    private lateinit var menuPage: MenuPage
    private lateinit var settingsPage: SettingsPage
    private lateinit var helpPage: HelpPage
    private lateinit var leaderboardPage: LeaderboardPage
    private lateinit var session: GameSession
    private var pausedByUi: Boolean
        get() = session.pausedByUi
        set(value) { session.pausedByUi = value }
    private var helpFromPause: Boolean
        get() = session.helpFromPause
        set(value) { session.helpFromPause = value }
    private val router get() = session.router

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundManager.init(this)
        session = ViewModelProvider(this)[GameSession::class.java]
        gameView = GameView(this, session)
        gameView.hudListener = this
        gameView.bgMode = parseBgMode(Prefs.backgroundMode(this))

        val root = FrameLayout(this)
        root.addView(gameView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(
            buildHud(),
            FrameLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP),
        )
        pausePanel = PausePanel(this)
        gameOverPanel = GameOverPanel(this)
        tutorialPanel = TutorialPanel(this)
        menuPage = MenuPage(this)
        settingsPage = SettingsPage(this)
        helpPage = HelpPage(this)
        leaderboardPage = LeaderboardPage(this)
        root.addView(pausePanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(gameOverPanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(settingsPage, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(helpPage, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(leaderboardPage, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(menuPage, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(tutorialPanel, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)

        pausePanel.onMenu = { if (!finishIfDead()) backToMenu() }
        pausePanel.onResume = { resumeGame() }
        pausePanel.onSettings = { openSettingsPage(fromPause = true) }
        pausePanel.onHelp = { openHelpPage(fromPause = true) }
        gameOverPanel.onRestart = { restartGame() }
        gameOverPanel.onLeaderboard = { openLeaderboardPage(fromGameOver = true) }
        gameOverPanel.onMenu = { backToMenu() }
        menuPage.onStart = { startFromMenu() }
        menuPage.onLeaderboard = { openLeaderboardPage(fromGameOver = false) }
        menuPage.onSettings = { openSettingsPage() }
        menuPage.onHelp = { openHelpPage() }
        menuPage.onExit = {
            SoundManager.play(SoundEvent.CLICK)
            showExitConfirmDialog(this)
        }
        settingsPage.onBack = back@{
            if (session.page != Page.SETTINGS || finishIfDead()) return@back
            settingsPage.hidePage()
            when (router.backFromSettings()) {
                OverlayRouter.Place.PAUSE -> {
                    session.page = Page.PAUSE
                    pausePanel.show()
                    applyAudioState()
                }
                else -> showMenuPage()
            }
        }
        settingsPage.onEditUserName = {
            showNameDialog(this, editing = true) { settingsPage.onShow() }
        }
        settingsPage.onLanguageSelected = { tag ->
            val locales = when (tag) {
                "en" -> LocaleListCompat.forLanguageTags("en")
                "zh" -> LocaleListCompat.forLanguageTags("zh")
                else -> LocaleListCompat.getEmptyLocaleList()
            }
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
        }
        settingsPage.onBackgroundSelected = { mode -> gameView.bgMode = mode }
        helpPage.onBack = back@{
            if (session.page != Page.HELP || finishIfDead()) return@back
            helpPage.hidePage()
            if (helpFromPause) {
                session.page = Page.PAUSE
                pausePanel.show()
                applyAudioState()
            } else {
                showMenuPage()
            }
        }
        leaderboardPage.onBack = back@{
            if (session.page != Page.LEADERBOARD) return@back
            leaderboardPage.hidePage()
            when (router.backFromLeaderboard()) {
                // 从结束面板进来的，返回时把那张面板再显示出来，分数文字还在。
                OverlayRouter.Place.GAME_OVER -> {
                    session.page = Page.GAME_OVER
                    gameOverPanel.show()
                    applyAudioState()
                }
                else -> showMenuPage()
            }
        }
        leaderboardPage.onShowLoad = { loadLeaderboard() }
        tutorialPanel.onDismiss = {
            Prefs.setTutorialShown(this)
            tutorialPanel.hide()
            restartGame()
        }

        restorePage()
        gameView.refreshHud()
    }

    override fun onStart() {
        super.onStart()
        if (Prefs.userName(this) == null) {
            showNameDialog(this) { /* 菜单已在背后可见 */ }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        val uiBlocked = pausedByUi || anyOverlayVisible() || anyPageVisible()
        if (!uiBlocked && Prefs.userName(this) != null) {
            gameView.resumeGame()
        }
        applyAudioState()
    }

    override fun onPause() {
        gameView.pauseGame()
        SoundManager.stopBgm()
        super.onPause()
    }

    override fun onDestroy() {
        gameView.release()
        scope.cancel()
        SoundManager.release()
        super.onDestroy()
    }

    override fun onHudChanged(score: Int, lives: Int, pointsToNextLife: Int) {
        scoreText.text = getString(R.string.score_fmt, score)
        livesText.text = getString(R.string.lives_fmt, lives)
        nextLifeText.text =
            if (lives >= GameConfig.MAX_LIVES) "" else getString(R.string.next_life_fmt, pointsToNextLife)
    }

    override fun onVehicleChanged(riding: Boolean, hitsUsed: Int, secondsLeft: Int) {
        vehicleText.text =
            if (riding) getString(R.string.vehicle_fmt, secondsLeft, hitsUsed, GameConfig.VEHICLE_MAX_HITS) else ""
    }

    override fun onGameOver(score: Int) {
        val dead = synchronized(session.world) { !session.inMenu && session.world.gameOver }
        if (dead) finishGame()
    }

    private fun finishIfDead(): Boolean {
        gameView.pauseGame()
        val dead = synchronized(session.world) { !session.inMenu && session.world.gameOver }
        if (dead) finishGame()
        return dead
    }

    private fun finishGame() {
        gameView.pauseGame()
        val score = synchronized(session.world) { session.world.score }
        if (session.settled && session.page == Page.LEADERBOARD) return
        hideAllUi()
        session.page = Page.GAME_OVER
        pausedByUi = true
        SoundManager.stopBgm()
        val best = max(Prefs.bestScore(this), score)
        gameOverPanel.show(score, best)
        if (session.settled) return
        session.settled = true
        Prefs.setBestScore(this, best)
        val record = ScoreRecord(userName = Prefs.userName(this).orEmpty(), score = score, createdAt = System.currentTimeMillis())
        val dao = AppDatabase.get(applicationContext).scoreDao()
        // 保存只捕获 DAO 和成绩，不随旧 Activity 销毁而取消。
        CoroutineScope(Dispatchers.IO).launch { dao.insert(record) }
    }

    private fun restorePage() {
        if (session.world.gameOver && !session.settled) {
            finishGame()
            return
        }
        hideAllUi()
        when (session.page) {
            Page.MENU -> menuPage.showPage()
            Page.PAUSE -> pausePanel.show()
            Page.SETTINGS -> settingsPage.showPage()
            Page.HELP -> helpPage.showPage()
            Page.LEADERBOARD -> {
                gameOverPanel.show(session.world.score, Prefs.bestScore(this))
                gameOverPanel.hide()
                leaderboardPage.showPage()
                loadLeaderboard()
            }
            Page.GAME_OVER -> gameOverPanel.show(session.world.score, Prefs.bestScore(this))
            Page.TUTORIAL -> tutorialPanel.show()
            Page.GAME -> Unit
        }
        applyAudioState()
    }

    private fun buildHud(): View {
        val bar = LinearLayout(this).apply {
            gravity = Gravity.TOP
            setPadding(dp(com.happyeat.game.ui.Candy.GAP), dp(com.happyeat.game.ui.Candy.GAP),
                dp(com.happyeat.game.ui.Candy.GAP), dp(com.happyeat.game.ui.Candy.SMALL))
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = com.happyeat.game.ui.uiShape(this@MainActivity, com.happyeat.game.ui.Candy.CREAM_CARD)
            setPadding(dp(com.happyeat.game.ui.Candy.GAP), dp(com.happyeat.game.ui.Candy.SMALL),
                dp(com.happyeat.game.ui.Candy.GAP), dp(com.happyeat.game.ui.Candy.SMALL))
        }
        fun label(size: Float) = TextView(this).apply {
            textSize = size
            setTextColor(com.happyeat.game.ui.Candy.INK)
        }
        scoreText = label(com.happyeat.game.ui.Candy.HUD_SCORE)
        livesText = label(com.happyeat.game.ui.Candy.SMALL_TEXT)
        nextLifeText = label(com.happyeat.game.ui.Candy.HUD_SMALL)
        vehicleText = label(com.happyeat.game.ui.Candy.HUD_SMALL)
        left.addView(scoreText)
        val life = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        life.addView(com.happyeat.game.ui.uiIcon(this, "ui_heart"), LinearLayout.LayoutParams(
            dp(com.happyeat.game.ui.Candy.ICON), dp(com.happyeat.game.ui.Candy.ICON)))
        life.addView(livesText)
        left.addView(life); left.addView(nextLifeText); left.addView(vehicleText)
        bar.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fun button(res: Int, icon: String, action: () -> Unit) {
            bar.addView(com.happyeat.game.ui.uiButton(this, res, icon, round = true, action = action),
                LinearLayout.LayoutParams(dp(com.happyeat.game.ui.Candy.TOUCH), dp(com.happyeat.game.ui.Candy.TOUCH)).apply {
                    marginStart = dp(com.happyeat.game.ui.Candy.SMALL)
                })
        }
        button(R.string.pause_action, "ui_pause") { pauseGame() }
        button(R.string.settings, "ui_settings") { openSettingsFromGear() }
        return bar
    }

    private fun anyOverlayVisible(): Boolean =
        pausePanel.isVisibleNow() || gameOverPanel.isVisibleNow() || tutorialPanel.isVisibleNow()

    private fun anyPageVisible(): Boolean =
        menuPage.isVisibleNow() || settingsPage.isVisibleNow() ||
            helpPage.isVisibleNow() || leaderboardPage.isVisibleNow()

    /** BGM 状态机：页面=菜单曲；对局（含暂停/教学浮层）=游戏曲；Game Over=无 */
    private fun applyAudioState() {
        val track = when {
            anyPageVisible() -> BgmTrack.MENU
            gameOverPanel.isVisibleNow() -> BgmTrack.NONE
            else -> BgmTrack.GAME
        }
        SoundManager.startBgm(track)
    }

    private fun hideAllPages() {
        menuPage.hidePage()
        settingsPage.hidePage()
        helpPage.hidePage()
        leaderboardPage.hidePage()
    }

    private fun hideAllUi() {
        hideAllPages()
        pausePanel.hide()
        gameOverPanel.hide()
        tutorialPanel.hide()
    }

    private fun showMenuPage() {
        hideAllUi()
        session.page = Page.MENU
        menuPage.showPage()
        pausedByUi = false
        // 菜单表示没有进行中的一局。循环若还在跑，这里停掉并清空，避免菜单盖住一局还在结算的游戏。
        if (!gameView.inMenu) gameView.enterMenu()
        applyAudioState()
    }

    /** 对局中点齿轮：先暂停循环，再盖设置。返回时回到暂停面板。 */
    private fun openSettingsFromGear() {
        if (gameOverPanel.isVisibleNow() || anyPageVisible()) return
        if (finishIfDead()) return
        pausedByUi = true
        router.settingsWhilePaused()
        presentSettings()
    }

    private fun openSettingsPage(fromPause: Boolean = false) {
        if (finishIfDead()) return
        if (fromPause) {
            router.settingsWhilePaused()
            pausedByUi = true
        } else {
            router.settingsFromMenu()
        }
        presentSettings()
    }

    private fun presentSettings() {
        session.page = Page.SETTINGS
        pausePanel.hide()
        hideAllPages()
        settingsPage.showPage()
        applyAudioState()
    }

    private fun openHelpPage(fromPause: Boolean = false) {
        if (finishIfDead()) return
        session.page = Page.HELP
        helpFromPause = fromPause
        if (fromPause) pausedByUi = true
        pausePanel.hide()
        hideAllPages()
        helpPage.showPage()
        applyAudioState()
    }

    private fun openLeaderboardPage(fromGameOver: Boolean) {
        session.page = Page.LEADERBOARD
        router.openLeaderboard(fromGameOver)
        // 结束面板全屏可点。不先藏掉的话，看完排行榜再开一局，旧面板还挡在新局上面。
        if (fromGameOver) gameOverPanel.hide()
        hideAllPages()
        leaderboardPage.showPage()
        applyAudioState()
        loadLeaderboard()
    }

    private fun loadLeaderboard() {
        scope.launch {
            val records = AppDatabase.get(applicationContext).scoreDao().topScores(50)
            if (session.page != Page.LEADERBOARD || isDestroyed) return@launch
            leaderboardPage.render(records, Prefs.userName(this@MainActivity), dateFormat)
        }
    }

    private fun startFromMenu() {
        SoundManager.play(SoundEvent.CLICK)
        gameView.pauseGame()
        hideAllUi()
        session.settled = false
        pausedByUi = false
        if (!Prefs.tutorialShown(this)) {
            session.page = Page.TUTORIAL
            tutorialPanel.show()
            applyAudioState()
        } else {
            restartGame()
        }
    }

    private fun pauseGame() {
        if (gameOverPanel.isVisibleNow() || anyPageVisible()) return
        if (finishIfDead()) return
        session.page = Page.PAUSE
        pausedByUi = true
        pausePanel.show()
        applyAudioState()
    }

    private fun resumeGame() {
        if (session.page != Page.PAUSE || finishIfDead()) return
        session.page = Page.GAME
        pausedByUi = false
        pausePanel.hide()
        gameView.resumeGame()
        applyAudioState()
    }

    private fun restartGame() {
        gameView.pauseGame()
        hideAllUi()
        session.page = Page.GAME
        session.settled = false
        pausedByUi = false
        helpFromPause = false
        router.settingsFromMenu()
        router.openLeaderboard(false)
        gameView.startGame()
        applyAudioState()
    }

    private fun backToMenu() {
        gameOverPanel.hide()
        gameView.enterMenu()
        showMenuPage()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun parseBgMode(raw: String): BgMode = when (raw) {
        "day" -> BgMode.DAY
        "sunset" -> BgMode.SUNSET
        "night" -> BgMode.NIGHT
        else -> BgMode.AUTO
    }

    private companion object {
        const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
