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
    private var pausedByUi = false
    private var helpFromPause = false
    private val router = OverlayRouter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SoundManager.init(this)
        gameView = GameView(this)
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
        settingsPage.onBack = {
            settingsPage.hidePage()
            when (router.backFromSettings()) {
                OverlayRouter.Place.PAUSE -> {
                    pausePanel.show()
                    applyAudioState()
                }
                else -> showMenuPage()
            }
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
        helpPage.onBack = {
            helpPage.hidePage()
            if (helpFromPause) {
                pausePanel.show()
                applyAudioState()
            } else {
                showMenuPage()
            }
        }
        leaderboardPage.onBack = {
            leaderboardPage.hidePage()
            when (router.backFromLeaderboard()) {
                // 从结束面板进来的，返回时把那张面板再显示出来，分数文字还在。
                OverlayRouter.Place.GAME_OVER -> {
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
            gameView.startGame()
        }

        onHudChanged(0, GameConfig.START_LIVES, GameConfig.POINTS_PER_EXTRA_LIFE)
        onVehicleChanged(false, 0, 0)
        showMenuPage()
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
        SoundManager.stopBgm()
        val best = max(Prefs.bestScore(this), score)
        Prefs.setBestScore(this, best)
        gameOverPanel.show(score, best)
        val user = Prefs.userName(this).orEmpty()
        scope.launch {
            AppDatabase.get(applicationContext).scoreDao().insert(
                ScoreRecord(userName = user, score = score, createdAt = System.currentTimeMillis()),
            )
        }
    }

    private fun buildHud(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(6))
        }

        fun infoLabel(sizeSp: Float, color: Int = Color.WHITE): TextView = TextView(this).apply {
            setTextColor(color)
            textSize = sizeSp
        }

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16 * resources.displayMetrics.density
                setColor(0x59101724.toInt())
            }
            setPadding(dp(14), dp(8), dp(16), dp(8))
        }
        scoreText = infoLabel(18f)
        livesText = infoLabel(14f)
        nextLifeText = infoLabel(12f)
        vehicleText = infoLabel(12f, Color.rgb(255, 213, 79))
        left.addView(scoreText)
        left.addView(livesText)
        left.addView(nextLifeText)
        left.addView(vehicleText)
        bar.addView(
            left,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        bar.addView(View(this), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        val circle = LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginStart = dp(10) }
        bar.addView(hudButton("⏸") { pauseGame() }, LinearLayout.LayoutParams(circle).apply { marginEnd = dp(10) })
        bar.addView(hudButton("⚙") { openSettingsFromGear() }, circle)
        return bar
    }

    private fun hudButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 17f
        minWidth = 0
        minHeight = 0
        setPadding(0, 0, 0, 0)
        val shape = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0x59101724.toInt())
            setStroke(dp(1), 0xFFE2E8F0.toInt())
        }
        background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33FFFFFF),
            shape,
            null,
        )
        setTextColor(Color.WHITE)
        setOnClickListener { onClick() }
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

    private fun showMenuPage() {
        // 结束面板如果还留着，下一次开始会被它整屏挡住。
        gameOverPanel.hide()
        hideAllPages()
        menuPage.showPage()
        pausedByUi = false
        // 菜单表示没有进行中的一局。循环若还在跑，这里停掉并清空，避免菜单盖住一局还在结算的游戏。
        if (!gameView.inMenu) gameView.enterMenu()
        applyAudioState()
    }

    /** 对局中点齿轮：先暂停循环，再盖设置。返回时回到暂停面板。 */
    private fun openSettingsFromGear() {
        if (gameOverPanel.isVisibleNow() || anyPageVisible()) return
        router.settingsWhilePaused()
        pauseGame()
        presentSettings()
    }

    private fun openSettingsPage(fromPause: Boolean = false) {
        if (fromPause) {
            router.settingsWhilePaused()
            pausedByUi = true
        } else {
            router.settingsFromMenu()
        }
        presentSettings()
    }

    private fun presentSettings() {
        pausePanel.hide()
        hideAllPages()
        settingsPage.showPage()
        applyAudioState()
    }

    private fun openHelpPage(fromPause: Boolean = false) {
        helpFromPause = fromPause
        if (fromPause) pausedByUi = true
        pausePanel.hide()
        hideAllPages()
        helpPage.showPage()
        applyAudioState()
    }

    private fun openLeaderboardPage(fromGameOver: Boolean) {
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
            leaderboardPage.render(records, Prefs.userName(this@MainActivity), dateFormat)
        }
    }

    private fun startFromMenu() {
        SoundManager.play(SoundEvent.CLICK)
        gameOverPanel.hide()
        menuPage.hidePage()
        if (!Prefs.tutorialShown(this)) {
            tutorialPanel.show()
            applyAudioState()
        } else {
            gameView.startGame()
            applyAudioState()
        }
    }

    private fun pauseGame() {
        if (gameOverPanel.isVisibleNow() || anyPageVisible()) return
        pausedByUi = true
        gameView.pauseGame()
        pausePanel.show()
        applyAudioState()
    }

    private fun resumeGame() {
        pausedByUi = false
        pausePanel.hide()
        gameView.resumeGame()
        applyAudioState()
    }

    private fun restartGame() {
        gameOverPanel.hide()
        hideAllPages()
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
