package com.happyeat.game

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.happyeat.game.config.GameConfig
import com.happyeat.game.config.PolishTestConfig
import com.happyeat.game.data.Prefs
import com.happyeat.game.data.AppDatabase
import com.happyeat.game.game.*
import com.happyeat.game.ui.*
import java.lang.reflect.Modifier

/** 在真实 Activity、主线程消息队列和 Canvas 上验证回归，不需要额外测试库。 */
class MainActivityInstrumentation : Instrumentation() {
    private lateinit var activity: MainActivity
    private val testUser = "__review_instrumentation__"
    private var passed = 0
    private var captureQa = false
    private var qaOnly = false
    private var qaMenuOnly = false
    private var groupFilter = ""

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        groupFilter = arguments?.getString("group") ?: ""
        captureQa = arguments?.getString("qa") == "true"
        qaMenuOnly = arguments?.getString("menuOnly") == "true"
        qaOnly = arguments?.getString("qaOnly") == "true"
        start()
    }

    override fun onStart() {
        val prefs = targetContext.getSharedPreferences("happyeat_prefs", 0)
        val oldPrefs = prefs.all.toMap()
        var oldLocale: String? = null
        var oldResourceLocales = ""
        var restoredLocale: String? = null
        var failure: Throwable? = null
        try {
            prefs.edit().putString("user_name", testUser).putBoolean("tutorial_shown", true).commit()
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            await { world().viewW > 0f }
            main {
                oldLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                oldResourceLocales = activity.resources.configuration.locales.toLanguageTags()
            }
            sendStatus(0, Bundle().apply {
                putString("stream", "BACKUP applicationLocales='$oldLocale' resourceLocales='$oldResourceLocales'\n")
            })
            if (!qaOnly || groupFilter == "坦克") {
            test("坦克送达与独立NPC") { tankDeliveryScenes() }
            test("坦克双指射击与空弹门控") { tankControlsScenes() }
            test("坦克炮击补给报废与清理") { tankCombatScenes() }
            test("坦克中英重建与换边布局") { tankLanguageScenes() }
            test("音频原生播放器切轨与完整循环") { nativeAudio() }
            test("优化视觉场景") { polishScenes() }
            test("操作区相对移动与多指") { controlsPointers() }
            test("操作区取消与生命周期") { controlsCancellation() }
            test("操作区布局与持久化") { controlsLayout() }
            test("语言重建保留完整世界及设置来源") { languageRetention() }
            test("菜单设置连续切换语言") { menuLanguages() }
            test("死亡排队与设置竞争及排行榜重开") { deathAndPages() }
            test("旧局死亡通知不得覆盖新局") { staleDeath() }
            test("死亡排队时重建只结算一次") { deathRecreate() }
            test("真实位图和缺图占位绘制范围") { drawingGeometry() }
            test("暂停放弃活局不结算且重开清理") { pauseToMenu() }
            test("背景选中回显及暂停 Surface 实际输出") { backgroundSelectionAndSurface() }
            test("中英帮助技巧可滚动阅读及返回来源") { helpTipsBothLanguages() }
            test("背景装载稳定键缺图尺寸及释放") { backgroundLoading() }
            test("正常入口教学快速暂停与结算背景") { normalEntryBackgroundFrames() }
            test("用户名保存校验取消重建历史记录及暂停入口") { usernameEditing() }
            }
            if (captureQa && groupFilter != "用户名" && groupFilter != "操作区" && groupFilter != "优化" && groupFilter != "音频" && groupFilter != "坦克") captureScreens()
        } catch (t: Throwable) {
            failure = t
        } finally {
            try {
                // 先等已启动的异步成绩保存完成，再仅删除本 runner 的成绩。
                waitForIdleSync()
                val db = AppDatabase.get(targetContext).openHelper.writableDatabase
                db.execSQL("DELETE FROM scores WHERE userName = ?", arrayOf(testUser))
                oldLocale?.let {
                    restoreLanguage(it, oldResourceLocales)
                    restoredLocale = it
                }
                main { if (::activity.isInitialized) activity.finish() }
                waitForIdleSync()
                val edit = prefs.edit().clear()
                oldPrefs.forEach { (key, value) ->
                    when (value) {
                        is String -> edit.putString(key, value)
                        is Int -> edit.putInt(key, value)
                        is Boolean -> edit.putBoolean(key, value)
                        is Long -> edit.putLong(key, value)
                        is Float -> edit.putFloat(key, value)
                    }
                }
                edit.commit()
            } catch (cleanup: Throwable) {
                if (failure == null) failure = cleanup else failure.addSuppressed(cleanup)
            }
        }
        finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", (if (qaOnly && groupFilter != "坦克") "仅截图采集，未运行测试\n" else if (groupFilter.isEmpty()) "通过 $passed 项\n" else "过滤组 '$groupFilter'：通过 $passed 项\n") +
                "BACKUP applicationLocales=${oldLocale?.let { "'$it'" } ?: "未获取"}\n" +
                "RESTORED applicationLocales=${restoredLocale?.let { "'$it'" } ?: "未确认"}\n" +
                (failure?.stackTraceToString() ?: "全部通过"))
            failure?.let { putString("shortMsg", it.toString()) }
        })
    }

    private fun restoreLanguage(tag: String, resourceLocales: String) {
        val monitor = addMonitor(MainActivity::class.java.name, null, false)
        try {
            main { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
            await {
                (monitor.lastActivity as? MainActivity)?.let { activity = it }
                !activity.isDestroyed && !activity.isFinishing &&
                    AppCompatDelegate.getApplicationLocales().toLanguageTags() == tag &&
                    activity.resources.configuration.locales.toLanguageTags() == resourceLocales
            }
            waitForIdleSync()
            main {
                check(AppCompatDelegate.getApplicationLocales().toLanguageTags() == tag)
                check(activity.resources.configuration.locales.toLanguageTags() == resourceLocales)
            }
            sendStatus(0, Bundle().apply { putString("stream", "RESTORED applicationLocales='$tag'\n") })
        } finally {
            removeMonitor(monitor)
        }
    }

    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync {
            try { action() } catch (t: Throwable) { failure = t }
        }
        failure?.let { throw it }
    }

    private fun test(name: String, action: () -> Unit) {
        if (groupFilter.isNotEmpty() && !name.contains(groupFilter)) return
        action()
        passed++
        sendStatus(0, Bundle().apply { putString("stream", "PASS $name\n") })
    }

    private fun view(): GameView = field(activity, "gameView")
    private fun world(): GameWorld = field(view(), "world")
    private inline fun <reified T> field(owner: Any, name: String): T =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner) as T
    private fun set(owner: Any, name: String, value: Any?) {
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)
    }
    private fun invoke(owner: Any, name: String) {
        owner.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(owner)
    }
    private fun panel(name: String): View = field(activity, name)
    private fun visible(name: String) = panel(name).visibility == View.VISIBLE
    private fun click(root: View, text: String) {
        val label = descendants(root).firstOrNull { it.isShown && it.isClickable && it.contentDescription?.toString() == text }
            ?: descendants(root).filterIsInstance<TextView>().firstOrNull { it.isShown && it.text.toString() == text }
            ?: error("没有找到按钮：$text")
        var target: View = label
        while (!target.isClickable) target = target.parent as? View ?: error("按钮不可点击：$text")
        check(target.performClick())
    }
    private fun click(root: String, textRes: Int) = click(panel(root), activity.getString(textRes))
    private fun descendants(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { descendants(root.getChildAt(it)) } else emptyList()
    private fun back(root: String) {
        click(panel(root), activity.getString(R.string.back))
    }
    private fun await(condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 10_000L
        while (SystemClock.uptimeMillis() < until) {
            var done = false
            main { done = condition() }
            if (done) return
            SystemClock.sleep(30L)
        }
        error("等待状态超时")
    }
    private fun deliverTank(w: GameWorld, direction: Int = 1) {
        val delivery = TankDelivery(direction, w.viewW)
        w.tankDelivery = delivery
        w.player.x = delivery.targetX
        while (w.tankDelivery != null) w.update(GameConfig.PHYSICS_STEP_MS)
        check(w.player.riding)
    }

    private fun newPausedGame() {
        main {
            invoke(activity, "restartGame")
            view().pauseGame()
            synchronized(world()) { world().spawnEnabled = false }
        }
    }

    private fun changeLanguage(tag: String) {
        val old = activity
        val monitor = addMonitor(MainActivity::class.java.name, null, false)
        main {
            click("settingsPage", if (tag == "zh") R.string.chinese else R.string.english)
        }
        val recreated = waitForMonitorWithTimeout(monitor, 10_000L)
        removeMonitor(monitor)
        check(recreated != null && recreated !== old) { "语言选择没有重建 Activity" }
        activity = recreated as MainActivity
        await { field<GameView>(activity, "gameView").width > 0 }
        waitForIdleSync()
        main {
            check(activity.resources.configuration.locales[0].language == tag)
            check(visible("settingsPage"))
            val oldView: GameView = field(old, "gameView")
            check(field<Boolean>(oldView, "released"))
            check(oldView.hudListener == null)
            check(world().listener === field<GameWorld.Listener>(view(), "worldListener"))
        }
    }

    private fun snapshot(w: GameWorld): List<Any?> {
        fun scalars(obj: Any): List<Any?> = obj.javaClass.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && (it.type.isPrimitive || it.type.isEnum) }
            .sortedBy { it.name }.map { it.isAccessible = true; it.get(obj) }
        return scalars(w) + scalars(w.player) + w.items.flatMap { listOf(it) + scalars(it) } +
            w.beasts.flatMap { listOf(it) + scalars(it) } +
            listOf(w.player.lastFireDirection, w.tankDelivery, w.tankDelivery?.stoppedAtMs) +
            (w.tankDelivery?.let { scalars(it) } ?: emptyList()) +
            w.returningNpcs.flatMap { listOf(it) + scalars(it) } + w.bullets.flatMap { listOf(it) + scalars(it) }
    }

    private fun languageRetention() {
        newPausedGame()
        lateinit var retained: GameWorld
        lateinit var before: List<Any?>
        main {
            retained = world()
            synchronized(retained) {
                set(retained, "score", 137)
                set(retained, "lives", 2)
                deliverTank(retained)
                set(retained, "gameTimeMs", 67_123L)
                retained.player.vehicleExpiresAtMs = retained.gameTimeMs + GameConfig.VEHICLE_DURATION_MS
                set(retained, "fallTimerMs", 177L)
                set(retained, "beastTimerMs", 244L)
                retained.player.x = retained.viewW * 0.4f
                retained.player.registerCrush()
                retained.player.jump(retained.gameTimeMs - 150L)
                retained.player.invincibleUntilMs = retained.gameTimeMs + 1900L
                retained.items.add(FallingItem(ItemKind.BEAN, 70f, 0.8f, 100f, 200f))
                retained.beasts.add(Beast(BeastKind.TIGER, 90f, 80f, 100f, 1).apply {
                    jumping = true
                    set(this, "jumpT", 0.25f)
                })
                retained.refreshHud()
                before = snapshot(retained)
            }
            click(activity.window.decorView, activity.getString(R.string.settings))
        }
        val tag = if (activity.resources.configuration.locales[0].language == "zh") "en" else "zh"
        changeLanguage(tag)
        main {
            check(world() === retained)
            check(snapshot(world()) == before) { "重建改变了世界内部状态" }
            check(!field<Boolean>(view(), "running"))
            check(field<TextView>(activity, "scoreText").text == activity.getString(R.string.score_fmt, 137))
            check(field<TextView>(activity, "livesText").text == activity.getString(R.string.lives_fmt, 2))
            val seconds = kotlin.math.ceil(GameConfig.VEHICLE_DURATION_MS / 1000.0).toInt()
            check(field<TextView>(activity, "vehicleText").text ==
                activity.getString(R.string.vehicle_fmt, seconds, 1, GameConfig.VEHICLE_MAX_HITS))
            back("settingsPage")
            check(visible("pausePanel") && !visible("menuPage"))
            click("pausePanel", R.string.settings)
        }
        changeLanguage(if (tag == "zh") "en" else "zh")
        main {
            check(world() === retained && snapshot(world()) == before)
            back("settingsPage")
            click("pausePanel", R.string.resume)
        }
        await { world().gameTimeMs > 67_123L }
        main { view().pauseGame(); check(world().gameTimeMs < 68_123L) }
    }

    private fun menuLanguages() {
        main { invoke(activity, "backToMenu"); click("menuPage", R.string.settings) }
        repeat(2) {
            val tag = if (activity.resources.configuration.locales[0].language == "zh") "en" else "zh"
            changeLanguage(tag)
            main {
                check(world().gameTimeMs == 0L && !field<Boolean>(view(), "running"))
                back("settingsPage")
                check(visible("menuPage") && !visible("pausePanel"))
                if (it == 0) click("menuPage", R.string.settings)
            }
        }
    }

    /** 主线程保持占用，后台走真实 update→Listener→Handler.post 后再操作页面。 */
    private fun queueDeath(score: Int) {
        val w = world()
        val worker = Thread {
            synchronized(w) {
                w.player.riding = false
                w.player.invincibleUntilMs = 0L
                w.player.resetState()
                set(w, "score", score)
                set(w, "lives", 1)
                w.items.clear()
                w.beasts.clear()
                w.items.add(FallingItem(ItemKind.ROCK, 50f, 0f, w.player.x,
                    w.player.displayCenterY(w.groundY, w.gameTimeMs, w.viewH)))
                w.update(1L)
            }
        }
        worker.start()
        worker.join()
        check(w.gameOver && w.score == score) { "死亡没有冻结预置成绩 $score" }
    }
    private fun savedScores(): List<Int> = AppDatabase.get(targetContext).openHelper.readableDatabase
        .query("SELECT score FROM scores WHERE userName = ? ORDER BY id", arrayOf(testUser)).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getInt(0)) }
        }
    private fun countScores(): Int = savedScores().size
    private fun awaitScores(expected: Int) {
        val until = SystemClock.uptimeMillis() + 10_000L
        while (countScores() < expected && SystemClock.uptimeMillis() < until) SystemClock.sleep(20L)
        check(countScores() == expected)
    }
    private fun onlyGameOver() {
        check(visible("gameOverPanel"))
        for (name in listOf("settingsPage", "pausePanel", "tutorialPanel", "menuPage", "leaderboardPage", "helpPage"))
            check(!visible(name)) { "残留浮层 $name" }
    }
    private fun assertFinalScore(score: Int, best: Int) {
        onlyGameOver()
        check(world().score == score && world().gameOver)
        check(field<TextView>(activity, "scoreText").text == activity.getString(R.string.score_fmt, score))
        check(field<TextView>(activity, "livesText").text == activity.getString(R.string.lives_fmt, 0))
        check(field<TextView>(panel("gameOverPanel"), "scoreText").text ==
            activity.getString(R.string.your_score_fmt, score))
        check(field<TextView>(panel("gameOverPanel"), "bestText").text ==
            activity.getString(R.string.best_score_fmt, best))
    }

    private fun assertRestarted() {
        for (name in listOf("gameOverPanel", "settingsPage", "pausePanel", "tutorialPanel",
            "menuPage", "leaderboardPage", "helpPage")) {
            check(!visible(name)) { "重开残留浮层 $name" }
        }
        check(!world().gameOver && world().score == 0 && world().lives == GameConfig.START_LIVES)
        check(field<TextView>(activity, "scoreText").text == activity.getString(R.string.score_fmt, 0))
        check(field<TextView>(activity, "livesText").text == activity.getString(R.string.lives_fmt, GameConfig.START_LIVES))
        val nextLife = if (GameConfig.START_LIVES >= GameConfig.MAX_LIVES) "" else
            activity.getString(R.string.next_life_fmt, GameConfig.POINTS_PER_EXTRA_LIFE)
        check(field<TextView>(activity, "nextLifeText").text == nextLife)
        check(field<TextView>(activity, "vehicleText").text.isEmpty())
    }

    private fun deathAndPages() {
        repeat(2) { order ->
            newPausedGame()
            val before = savedScores()
            val score = 137 + order * 114
            val best = maxOf(Prefs.bestScore(targetContext), score)
            main {
                if (order == 1) click(activity.window.decorView, activity.getString(R.string.settings))
                val oldBack: (() -> Unit)? = field(panel("settingsPage"), "onBack")
                queueDeath(score)
                if (order == 0) click(activity.window.decorView, activity.getString(R.string.settings))
                android.os.Handler(android.os.Looper.getMainLooper()).post { oldBack!!.invoke() }
            }
            waitForIdleSync()
            awaitScores(before.size + 1)
            check(savedScores() == before + score) { "死亡成绩记录的数量或分值不正确" }
            main {
                assertFinalScore(score, best)
                click("gameOverPanel", R.string.leaderboard)
                check(visible("leaderboardPage") && !visible("gameOverPanel"))
                back("leaderboardPage")
                assertFinalScore(score, best)
                click("gameOverPanel", R.string.restart)
                view().pauseGame()
            }
            // reset 的 HUD 经 Handler 异步投递，先排空再检查新局文案。
            waitForIdleSync()
            main { assertRestarted() }
            check(savedScores() == before + score)
        }
        main {
            view().resumeGame()
            activity.moveTaskToBack(true)
        }
        await { !field<Boolean>(view(), "running") }
        targetContext.startActivity(Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        await { field<Boolean>(view(), "running") }
        main { view().pauseGame() }
    }
    private fun staleDeath() {
        newPausedGame()
        val count = countScores()
        main {
            queueDeath(317)
            invoke(activity, "restartGame")
            view().pauseGame()
        }
        waitForIdleSync()
        main { assertRestarted() }
        check(countScores() == count)
    }
    private fun deathRecreate() {
        newPausedGame()
        val before = savedScores()
        val score = 463
        val best = maxOf(Prefs.bestScore(targetContext), score)
        val monitor = addMonitor(MainActivity::class.java.name, null, false)
        main { activity.recreate(); queueDeath(score) }
        activity = waitForMonitorWithTimeout(monitor, 10_000L) as MainActivity
        removeMonitor(monitor)
        waitForIdleSync()
        awaitScores(before.size + 1)
        main { assertFinalScore(score, best) }
        check(savedScores() == before + score) { "重建必须恰好保存一条非零成绩 $score" }
    }

    private fun pauseToMenu() {
        newPausedGame()
        val scores = savedScores()
        val best = Prefs.bestScore(targetContext)
        check(best < Int.MAX_VALUE) { "最高分已到整数上限，无法构造更高的放弃成绩" }
        val abandonedScore = best + 1
        main {
            val w = world()
            synchronized(w) {
                set(w, "score", abandonedScore)
                deliverTank(w)
                w.performPlayerAction()
                w.update(GameConfig.JUMP_AIRTIME_MS / 4)
                w.items.add(FallingItem(ItemKind.BEAN, 40f, 0f, 0f, 0f))
                w.beasts.add(Beast(BeastKind.WOLF, 40f, 0f, 0f, 1))
            }
            check(w.score > Prefs.bestScore(targetContext))
            click(activity.window.decorView, activity.getString(R.string.pause_action))
            check(visible("pausePanel"))
            // 后台排入旧局 HUD，保持主线程占用直到退出，验证旧通知失效。
            Thread { synchronized(w) { w.refreshHud() } }.apply { start(); join() }
            click("pausePanel", R.string.back_to_menu)
        }
        waitForIdleSync()
        main {
            check(visible("menuPage") && view().inMenu)
            check(!field<Boolean>(view(), "running"))
            for (name in listOf("pausePanel", "gameOverPanel", "settingsPage", "helpPage", "leaderboardPage", "tutorialPanel"))
                check(!visible(name))
            check(world().items.isEmpty() && world().beasts.isEmpty())
            check(!world().player.riding && !world().player.isAirborne(world().gameTimeMs))
            check(world().score == 0 && world().gameTimeMs == 0L)
        }
        check(savedScores() == scores) { "放弃活局不应写入排行榜" }
        check(Prefs.bestScore(targetContext) == best) { "放弃活局不应更新最高分" }
        main {
            click("menuPage", R.string.menu_start)
            view().pauseGame()
        }
        waitForIdleSync()
        main {
            assertRestarted()
            check(world().items.isEmpty() && world().beasts.isEmpty())
            check(!world().player.riding && !world().player.isAirborne(world().gameTimeMs))
        }
        check(savedScores() == scores && Prefs.bestScore(targetContext) == best)
    }

    private fun checkBackgroundSelection(tag: String) {
        val rows: Map<String, android.widget.LinearLayout> = field(panel("settingsPage"), "backgroundRows")
        check(rows.filterValues { it.isSelected }.keys == setOf(tag))
        rows.forEach { (key, row) ->
            check(row.getChildAt(1) is android.widget.ImageView)
            check(row.getChildAt(1).visibility == if (key == tag) View.VISIBLE else View.INVISIBLE)
            check(!row.contentDescription.isNullOrEmpty())
        }
        val languages: Map<String, android.widget.LinearLayout> = field(panel("settingsPage"), "languageRows")
        check(languages.filterValues { it.isSelected }.keys == setOf(AppCompatLocaleReader.current()))
        check(Prefs.backgroundMode(targetContext) == tag)
    }

    /** 读取 Surface 提交的缓冲区，不调用绘制方法，也不以模式字段代替画面证据。 */
    private fun assertSurfaceBackground(asset: String) {
        lateinit var v: GameView
        lateinit var expected: Bitmap
        main { v = view(); check(v.holder.surface.isValid) }
        expected = targetContext.assets.open("sprites/$asset.png").use { android.graphics.BitmapFactory.decodeStream(it) }
        checkNotNull(expected)
        val capture = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        // SurfaceView 默认 RGB565；先按目标尺寸绘制并量化，不能拿原图的 ARGB 色值直接比较。
        val reference = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.RGB_565)
        val split = (expected.height * checkNotNull(GameConfig.BG_GROUND_FRACTIONS[asset])).toInt()
        val ground = v.height - GameConfig.BEAST_SIZE * v.width
        val canvas = Canvas(reference)
        canvas.drawBitmap(expected, android.graphics.Rect(0, 0, expected.width, split),
            android.graphics.RectF(0f, 0f, v.width.toFloat(), ground), android.graphics.Paint())
        canvas.drawBitmap(expected, android.graphics.Rect(0, split, expected.width, expected.height),
            android.graphics.RectF(0f, ground, v.width.toFloat(), v.height.toFloat()), android.graphics.Paint())
        try {
            val done = java.util.concurrent.CountDownLatch(1)
            var result = -1
            main {
                android.view.PixelCopy.request(v, capture, { status -> result = status; done.countDown() },
                    android.os.Handler(android.os.Looper.getMainLooper()))
            }
            check(done.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "Surface 捕获超时" }
            check(result == android.view.PixelCopy.SUCCESS) { "Surface 捕获失败：$result" }
            for (fraction in listOf(0.125f, 0.25f, 0.375f, 0.85f, 0.98f)) for (xi in listOf(1, 2, 4, 5)) {
                val x = capture.width * xi / 6
                val y = (capture.height * fraction).toInt()
                val actual = capture.getPixel(x, y)
                val wanted = reference.getPixel(x, y)
                check(kotlin.math.abs(Color.red(actual) - Color.red(wanted)) <= 3 &&
                    kotlin.math.abs(Color.green(actual) - Color.green(wanted)) <= 3 &&
                    kotlin.math.abs(Color.blue(actual) - Color.blue(wanted)) <= 3) {
                    "Surface 未显示 ${asset}，采样点 ($x,$y)：$actual != $wanted"
                }
            }
        } finally { capture.recycle(); reference.recycle(); expected.recycle() }
    }

    private fun backgroundSelectionAndSurface() {
        newPausedGame()
        lateinit var before: List<Any?>
        main {
            synchronized(world()) { set(world(), "gameTimeMs", GameConfig.BG_PHASE_MS); before = snapshot(world()) }
            click(activity.window.decorView, activity.getString(R.string.settings))
        }
        val options = listOf(
            Triple(R.string.bg_day, "day", "background"),
            Triple(R.string.bg_sunset, "sunset", "background_sunset"),
            Triple(R.string.bg_night, "night", "background_night"),
            Triple(R.string.bg_auto, "auto", "background_sunset"),
        )
        for ((label, tag, bitmap) in options) {
            main {
                click("settingsPage", label)
                checkBackgroundSelection(tag)
                click("settingsPage", label)
                checkBackgroundSelection(tag)
                back("settingsPage")
                check(visible("pausePanel"))
                check(snapshot(world()) == before && !field<Boolean>(view(), "running"))
            }
            waitForIdleSync()
            assertSurfaceBackground(bitmap)
            main { click("pausePanel", R.string.settings); checkBackgroundSelection(tag) }
        }
        val automatic = listOf("background", "background_sunset", "background_night", "background_orchard",
            "background_orchard_sunset", "background_orchard_night", "background")
        for ((phase, bitmap) in automatic.withIndex()) {
            main {
                synchronized(world()) { set(world(), "gameTimeMs", phase * GameConfig.BG_PHASE_MS); before = snapshot(world()) }
                click("settingsPage", R.string.bg_auto)
            }
            assertSurfaceBackground(bitmap)
            main { check(snapshot(world()) == before && !field<Boolean>(view(), "running")) }
        }
        for ((label, names) in listOf(
            R.string.bg_day to listOf("background", "background_orchard"),
            R.string.bg_sunset to listOf("background_sunset", "background_orchard_sunset"),
            R.string.bg_night to listOf("background_night", "background_orchard_night"))) {
            for (scene in names.indices) {
                main {
                    synchronized(world()) { set(world(), "gameTimeMs", (scene * 3 + 1) * GameConfig.BG_PHASE_MS); before = snapshot(world()) }
                    click("settingsPage", label)
                }
                assertSurfaceBackground(names[scene])
                main { check(snapshot(world()) == before && !field<Boolean>(view(), "running")) }
            }
        }
        main {
            synchronized(world()) { set(world(), "gameTimeMs", GameConfig.BG_PHASE_MS); before = snapshot(world()) }
            click("settingsPage", R.string.bg_auto)
        }
        assertSurfaceBackground("background_sunset")
        main { click("settingsPage", R.string.bg_night) }
        changeLanguage(if (activity.resources.configuration.locales[0].language == "zh") "en" else "zh")
        await { view().holder.surface.isValid }
        main {
            checkBackgroundSelection("night")
            check(snapshot(world()) == before && !field<Boolean>(view(), "running"))
            back("settingsPage")
        }
        assertSurfaceBackground("background_night")
        // 显式销毁、重建 Surface 后仍须显示已选背景，同时核对参考图使用的像素格式。
        var surfaceFormat = android.graphics.PixelFormat.UNKNOWN
        val formatProbe = object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {}
            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {}
            override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                surfaceFormat = format
            }
        }
        main { view().holder.addCallback(formatProbe); view().visibility = View.GONE }
        await { !view().holder.surface.isValid }
        main { view().visibility = View.VISIBLE }
        await { view().holder.surface.isValid && surfaceFormat != android.graphics.PixelFormat.UNKNOWN }
        waitForIdleSync()
        main { view().holder.removeCallback(formatProbe) }
        check(surfaceFormat == android.graphics.PixelFormat.RGB_565) { "Surface 格式已变化，需同步参考图格式：$surfaceFormat" }
        sendStatus(0, Bundle().apply { putString("stream", "Surface format=$surfaceFormat (RGB_565)\n") })
        assertSurfaceBackground("background_night")
        main { check(snapshot(world()) == before && !field<Boolean>(view(), "running")) }
    }

    private fun backgroundLoading() {
        newPausedGame()
        main {
            val v = view()
            val w = world()
            val prepare = v.javaClass.getDeclaredMethod("prepareBackground", Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            val size = v.javaClass.getDeclaredMethod("applySize", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).apply { isAccessible = true }
            val decoder: (String, Int, Int) -> Bitmap? = field(v, "backgroundDecoder")
            val width = w.viewW; val height = w.viewH
            try {
                val loads = field<Int>(v, "backgroundLoads")
                repeat(20) { prepare.invoke(v, false) }
                check(field<Int>(v, "backgroundLoads") == loads)
                size.invoke(v, width, height)
                check(field<Int>(v, "backgroundLoads") == loads)
                var attempts = 0
                set(v, "backgroundDecoder", { _: String, _: Int, _: Int -> attempts++; null } as (String, Int, Int) -> Bitmap?)
                set(v, "backgroundKey", null)
                prepare.invoke(v, false)
                repeat(20) { prepare.invoke(v, false) }
                check(attempts == 1 && field<Bitmap?>(v, "shownBg") == null)
                size.invoke(v, width - 1, height)
                check(attempts == 2)
            } finally {
                set(v, "backgroundDecoder", decoder)
                set(v, "backgroundKey", null)
                size.invoke(v, width, height)
            }
            check(listOf(field<Bitmap?>(v, "shownBg"), field<Bitmap?>(v, "prevBg")).filterNotNull().size <= 2)
        }
        val recoveringView = view()
        lateinit var decoderBeforeRecovery: (String, Int, Int) -> Bitmap?
        var recoveryAttempts = 0
        var available = false
        main {
            decoderBeforeRecovery = field(recoveringView, "backgroundDecoder")
            set(recoveringView, "backgroundDecoder", { name: String, width: Int, height: Int ->
                recoveryAttempts++
                if (available) decoderBeforeRecovery(name, width, height) else null
            } as (String, Int, Int) -> Bitmap?)
            set(recoveringView, "backgroundKey", null)
            recoveringView.bgMode = BgMode.DAY
            repeat(5) { recoveringView.bgMode = BgMode.DAY }
            check(recoveryAttempts == 1 && field<Bitmap?>(recoveringView, "shownBg") == null)
        }
        try {
            main { available = true; recoveringView.visibility = View.GONE }
            await { !recoveringView.holder.surface.isValid }
            main { recoveringView.visibility = View.VISIBLE }
            await { recoveringView.holder.surface.isValid && field<Bitmap?>(recoveringView, "shownBg") != null }
            waitForIdleSync()
            main { check(recoveryAttempts == 2) }
            assertSurfaceBackground("background")
            main { recoveringView.visibility = View.GONE }
            await { !recoveringView.holder.surface.isValid }
            main { recoveringView.visibility = View.VISIBLE }
            await { recoveringView.holder.surface.isValid }
            waitForIdleSync()
            main { check(recoveryAttempts == 2) { "成功 key 在同尺寸重建时不应重复解码" } }
        } finally { main { set(recoveringView, "backgroundDecoder", decoderBeforeRecovery) } }
        lateinit var detached: GameView
        val entered = java.util.concurrent.CountDownLatch(1)
        val finishDecode = java.util.concurrent.CountDownLatch(1)
        main {
            val session = GameSession()
            session.world.onSize(100f, 200f)
            detached = GameView(activity, session)
            set(detached, "backgroundDecoder", { _: String, _: Int, _: Int ->
                entered.countDown()
                check(finishDecode.await(5, java.util.concurrent.TimeUnit.SECONDS))
                Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888)
            } as (String, Int, Int) -> Bitmap?)
        }
        var workerFailure: Throwable? = null
        val worker = Thread {
            try {
                detached.javaClass.getDeclaredMethod("prepareBackground", Boolean::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(detached, false)
            } catch (t: Throwable) { workerFailure = t }
        }
        worker.start()
        try {
            check(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            main { detached.release() }
        } finally { finishDecode.countDown(); worker.join(5000) }
        check(!worker.isAlive)
        workerFailure?.let { throw it }
        main { check(field<Bitmap?>(detached, "shownBg") == null && field<Bitmap?>(detached, "prevBg") == null) }
        var initialLoads = 0
        var maxResidentBytes = 0
        var maxDecodeMs = 0L
        lateinit var originalDecoder: (String, Int, Int) -> Bitmap?
        main {
            synchronized(world()) { set(world(), "gameTimeMs", GameConfig.BG_PHASE_MS - 1) }
            view().bgMode = BgMode.AUTO
            initialLoads = field(view(), "backgroundLoads")
            originalDecoder = field(view(), "backgroundDecoder")
            val decoder: (String, Int, Int) -> Bitmap? = { name, width, height ->
                val started = SystemClock.elapsedRealtime()
                val bitmap = originalDecoder(name, width, height)
                maxDecodeMs = maxOf(maxDecodeMs, SystemClock.elapsedRealtime() - started)
                val resident = listOf(field<Bitmap?>(view(), "shownBg"), field<Bitmap?>(view(), "prevBg"))
                    .filterNotNull().distinct().sumOf { it.allocationByteCount }
                maxResidentBytes = maxOf(maxResidentBytes, resident + (bitmap?.allocationByteCount ?: 0))
                bitmap
            }
            set(view(), "backgroundDecoder", decoder)
            view().resumeGame()
        }
        try {
            await { world().gameTimeMs > GameConfig.BG_PHASE_MS + GameConfig.BG_FADE_MS + 100 }
        } finally {
            main { view().pauseGame(); set(view(), "backgroundDecoder", originalDecoder) }
        }
        main {
            check(field<Int>(view(), "backgroundLoads") == initialLoads + 1) { "稳定帧重复解码或未装载阶段背景" }
            check(field<Bitmap?>(view(), "prevBg") == null) { "渐变结束未释放旧背景引用" }
        }
        assertSurfaceBackground("background_sunset")
        sendStatus(0, Bundle().apply { putString("stream", "背景实测：单次解码=${maxDecodeMs}ms，含临时解码位图峰值=${maxResidentBytes}bytes\n") })
    }

    private fun helpTipsBothLanguages() {
        for (tag in listOf("zh", "en")) {
            main { invoke(activity, "backToMenu"); click("menuPage", R.string.settings) }
            if (activity.resources.configuration.locales[0].language != tag) changeLanguage(tag)
            main { back("settingsPage"); click("menuPage", R.string.menu_help) }
            verifyReadableTips()
            main { back("helpPage"); check(visible("menuPage") && !visible("pausePanel")) }
            newPausedGame()
            main { click(activity.window.decorView, activity.getString(R.string.pause_action)); click("pausePanel", R.string.menu_help) }
            verifyReadableTips()
            main { back("helpPage"); check(visible("pausePanel") && !visible("menuPage")) }
        }
    }

    private fun verifyReadableTips() {
        waitForIdleSync()
        // 页面入场动画会临时移动内容，等动画结束再检查屏幕裁切。
        await { panel("helpPage").translationX == 0f }
        for (res in listOf(R.string.help_air_move, R.string.help_edges, R.string.help_bean,
            R.string.help_vehicle, R.string.help_hazard, R.string.help_beast)) {
            lateinit var label: TextView
            main {
                val page = panel("helpPage")
                label = descendants(page).filterIsInstance<TextView>().single { it.text == activity.getString(res) }
                check(descendants(page).any { it is android.widget.ScrollView })
                label.requestRectangleOnScreen(android.graphics.Rect(0, 0, label.width, label.height), true)
            }
            waitForIdleSync()
            main {
                val rect = android.graphics.Rect()
                check(label.getGlobalVisibleRect(rect) && rect.height() == label.height && rect.width() == label.width)
                val layout = checkNotNull(label.layout)
                check(layout.lineCount > 0)
                for (line in 0 until layout.lineCount) check(layout.getEllipsisCount(line) == 0)
                check(layout.getLineBottom(layout.lineCount - 1) <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
            }
        }
    }

    private fun normalEntryBackgroundFrames() {
        main {
            invoke(activity, "backToMenu")
            click("menuPage", R.string.settings)
            click("settingsPage", R.string.bg_day)
            back("settingsPage")
            targetContext.getSharedPreferences("happyeat_prefs", 0).edit().putBoolean("tutorial_shown", false).commit()
        }
        val monitor = addMonitor(MainActivity::class.java.name, null, false)
        main { activity.recreate() }
        activity = waitForMonitorWithTimeout(monitor, 10_000L) as MainActivity
        removeMonitor(monitor)
        await { view().holder.surface.isValid && view().width > 0 }
        waitForIdleSync()
        main { click("menuPage", R.string.menu_start); check(visible("tutorialPanel")) }
        assertSurfaceBackground("background")
        screenshot("normal-entry-tutorial")
        main {
            panel("tutorialPanel").performClick()
            click(activity.window.decorView, activity.getString(R.string.pause_action))
            check(visible("pausePanel"))
        }
        waitForIdleSync()
        screenshot("normal-entry-quick-pause")
        assertSurfaceBackground("background")
        repeat(8) {
            main {
                click("pausePanel", R.string.back_to_menu)
                click("menuPage", R.string.menu_start)
                click(activity.window.decorView, activity.getString(R.string.pause_action))
            }
            waitForIdleSync()
            assertSurfaceBackground("background")
        }
        val before = countScores()
        main { queueDeath(137) }
        waitForIdleSync(); awaitScores(before + 1)
        screenshot("normal-entry-game-over")
        assertSurfaceBackground("background")
    }

    private fun screenshot(name: String) {
        waitForIdleSync()
        SystemClock.sleep(200)
        val image = checkNotNull(uiAutomation.takeScreenshot())
        val folder = java.io.File(targetContext.getExternalFilesDir(null),
            if (groupFilter == "坦克") "qa-tank" else if (groupFilter == "优化") "qa-polish" else if (groupFilter == "操作区") "qa-controls" else if (groupFilter == "用户名") "qa-username" else "qa-b").apply { mkdirs() }
        java.io.File(folder, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }

    private fun captureScroll(page: String, prefix: String) {
        var index = 0
        var more = true
        while (more) {
            screenshot("$prefix-$index")
            main {
                val scroll = descendants(panel(page)).filterIsInstance<android.widget.ScrollView>().single()
                more = scroll.canScrollVertically(1)
                if (more) scroll.scrollBy(0, scroll.height / 2)
            }
            index++
        }
    }

    private fun nodes(node: android.view.accessibility.AccessibilityNodeInfo): List<android.view.accessibility.AccessibilityNodeInfo> =
        listOf(node) + (0 until node.childCount).flatMap { node.getChild(it)?.let { child -> nodes(child) } ?: emptyList() }

    private fun awaitNameNodes(
        description: String,
        ready: (List<android.view.accessibility.AccessibilityNodeInfo>) -> Boolean,
    ): List<android.view.accessibility.AccessibilityNodeInfo> {
        // 主线程空闲不代表可访问性窗口已切换；在测试线程等待目标窗口和节点。
        waitForIdleSync()
        uiAutomation.waitForIdle(100L, 5_000L)
        val until = SystemClock.uptimeMillis() + 10_000L
        while (SystemClock.uptimeMillis() < until) {
            val root = uiAutomation.rootInActiveWindow
            if (root != null && root.packageName?.toString() == targetContext.packageName) {
                val current = nodes(root)
                if (ready(current)) return current
            }
            SystemClock.sleep(30L)
        }
        error("等待改名可访问性窗口超时：$description")
    }

    private fun nameInput(expected: String? = null): android.view.accessibility.AccessibilityNodeInfo =
        awaitNameNodes("输入框${expected?.let { "文本=$it" }.orEmpty()}") { current ->
            current.any { it.className.toString() == "android.widget.EditText" &&
                (expected == null || it.text.toString() == expected) }
        }.single { it.className.toString() == "android.widget.EditText" }

    private fun enterName(value: String) {
        check(nameInput().performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
        waitForIdleSync()
    }

    private fun nameAction(res: Int) {
        val label = activity.getString(res)
        val button = awaitNameNodes("按钮=$label") { current ->
            current.any { it.contentDescription?.toString() == label && it.isClickable }
        }.first { it.contentDescription?.toString() == label && it.isClickable }
        check(button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        waitForIdleSync()
    }

    private fun checkCurrentName(expected: String) {
        val label = activity.getString(R.string.current_username_fmt, expected)
        awaitNameNodes("设置页显示=$expected，弹窗关闭") { current ->
            current.none { it.className.toString() == "android.widget.EditText" } &&
                current.any { it.text?.toString() == label }
        }
        main {
            check(Prefs.userName(activity) == expected)
            check(visible("settingsPage") && !field<Boolean>(view(), "running"))
            check(descendants(panel("settingsPage")).filterIsInstance<TextView>().any {
                it.text.toString() == activity.getString(R.string.current_username_fmt, expected)
            }) { "设置页没有立即显示当前用户名" }
        }
    }

    private fun openName(expected: String) {
        main { click("settingsPage", R.string.edit_username) }
        check(nameInput(expected).text.toString() == expected) { "改名弹窗预填不正确" }
    }

    private fun tapNameNode(node: android.view.accessibility.AccessibilityNodeInfo, visibleBounds: android.graphics.Rect? = null) {
        check(node.isVisibleToUser) { "目标节点不可见，不能模拟点按" }
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        if (visibleBounds != null) bounds.set(visibleBounds)
        check(!bounds.isEmpty)
        val now = SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(now, SystemClock.uptimeMillis(), action,
                bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
            sendPointerSync(event)
            event.recycle()
        }
    }

    private fun firstNameDefault(savedName: String) {
        var saved = 0
        main {
            invoke(activity, "backToMenu")
            targetContext.getSharedPreferences("happyeat_prefs", 0).edit().remove("user_name").commit()
            showNameDialog(activity) { saved++ }
        }
        val initialInput = nameInput()
        // 空输入的可访问性 text 可能是 hint；不能把提示文字判为预填值。
        check(initialInput.text.isNullOrEmpty() ||
            androidx.core.view.accessibility.AccessibilityNodeInfoCompat.wrap(initialInput).isShowingHintText) {
            "首次命名输入框存在实际预填内容：${initialInput.text}"
        }
        val ok = activity.getString(R.string.ok)
        val buttons = awaitNameNodes("首次命名默认按钮") { current ->
            current.any { it.contentDescription?.toString() == ok && it.isClickable }
        }
        for (res in listOf(R.string.cancel, R.string.save)) {
            val label = activity.getString(res)
            check(buttons.none { it.text?.toString() == label || it.contentDescription?.toString() == label }) {
                "首次命名不应出现 $label"
            }
        }
        if (captureQa) screenshot("username-first-default")
        enterName(" a ")
        nameAction(R.string.ok)
        awaitNameNodes("首次命名非法输入提示") { current -> current.any {
            it.text?.toString() == activity.getString(R.string.name_too_short) && it.isVisibleToUser
        } }
        main { check(Prefs.userName(activity) == null && saved == 0) }
        if (captureQa) screenshot("username-first-error")
        enterName(savedName)
        nameInput(savedName)
        if (captureQa) screenshot("username-first-save")
        nameAction(R.string.ok)
        awaitNameNodes("首次命名保存后返回菜单") { current ->
            current.none { it.className.toString() == "android.widget.EditText" } &&
                current.any { it.contentDescription?.toString() == activity.getString(R.string.menu_start) }
        }
        main { check(Prefs.userName(activity) == savedName && saved == 1 && visible("menuPage")) }
    }

    private fun nameWithKeyboard(tag: String, current: String, updated: String) {
        val originalFlags = uiAutomation.serviceInfo.flags
        uiAutomation.serviceInfo = uiAutomation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        fun keyboardBounds(): android.graphics.Rect? = uiAutomation.windows
            .filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .map { window -> android.graphics.Rect().also { window.getBoundsInScreen(it) } }
            .filterNot { it.isEmpty }.minByOrNull { it.top }
        fun aboveKeyboard(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
            val ime = keyboardBounds() ?: return false
            val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
            return node.isVisibleToUser && !bounds.isEmpty && bounds.top >= 0 &&
                bounds.bottom <= ime.top && !android.graphics.Rect.intersects(bounds, ime)
        }
        fun reachableArea(node: android.view.accessibility.AccessibilityNodeInfo): android.graphics.Rect? {
            val ime = keyboardBounds() ?: return null
            if (!node.isVisibleToUser) return null
            val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
            val area = android.graphics.Rect(bounds)
            val screen = activity.resources.displayMetrics
            if (!area.intersect(0, 0, screen.widthPixels, minOf(screen.heightPixels, ime.top))) return null
            if (!area.contains(bounds.centerX(), bounds.centerY())) return null
            return area
        }
        try {
            openName(current)
            tapNameNode(nameInput())
            val until = SystemClock.uptimeMillis() + 10_000L
            while (keyboardBounds() == null && SystemClock.uptimeMillis() < until) SystemClock.sleep(30L)
            check(keyboardBounds() != null) { "真实点按输入框后没有出现输入法窗口" }
            enterName(updated)
            nameInput(updated)
            if (captureQa) screenshot("username-$tag-keyboard")
            val label = activity.getString(R.string.save)
            var save: android.view.accessibility.AccessibilityNodeInfo? = null
            for (attempt in 0 until 8) {
                val currentNodes = awaitNameNodes("键盘打开时查找保存按钮") { currentNodes ->
                    currentNodes.any { it.contentDescription?.toString() == label }
                }
                save = currentNodes.firstOrNull {
                    it.contentDescription?.toString() == label && it.isClickable && aboveKeyboard(it)
                }
                if (save != null) break
                val candidate = currentNodes.first { it.contentDescription?.toString() == label && it.isClickable }
                val scroll = currentNodes.first { it.className.toString() == "android.widget.ScrollView" }
                val buttonBounds = android.graphics.Rect().also { candidate.getBoundsInScreen(it) }
                val scrollBounds = android.graphics.Rect().also { scroll.getBoundsInScreen(it) }
                val ime = keyboardBounds()
                val scrolled = scroll.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                sendStatus(0, Bundle().apply {
                    putString("stream", "用户名滚动 $tag attempt=$attempt save=$buttonBounds visible=${candidate.isVisibleToUser} " +
                        "IME=$ime scroll=$scrollBounds scrollable=${scroll.isScrollable} accepted=$scrolled\n")
                })
                // 动作返回值不代表动画及可访问性边界已更新；下一轮重新读取并执行同样的遮挡断言。
                waitForIdleSync()
                uiAutomation.waitForIdle(300L, 5_000L)
                if (!scrolled) break
            }
            // 优先滚动露出完整按钮；文案由截图审核，操作可达性由可见中心的实际点按验证。
            if (save == null) {
                save = awaitNameNodes("滚动后检查保存按钮实际可达区域") { currentNodes ->
                    currentNodes.any { it.contentDescription?.toString() == label && it.isClickable }
                }.firstOrNull {
                    it.contentDescription?.toString() == label && it.isClickable && reachableArea(it) != null
                }
            }
            if (save == null && captureQa) screenshot("username-$tag-keyboard-unreachable")
            val visibleSave = checkNotNull(save) { "键盘打开时滚动并等待后保存按钮中心仍不可达，参见滚动边界日志" }
            check(visibleSave.refresh() && reachableArea(visibleSave) != null) { "保存按钮中心被遮挡" }
            if (captureQa) screenshot("username-$tag-keyboard-save")
            // 截图后再次读取边界，防止布局移动导致坐标落入键盘。
            check(visibleSave.refresh())
            val area = checkNotNull(reachableArea(visibleSave)) { "点按前按钮与键盘边界发生变化" }
            val bounds = android.graphics.Rect().also { visibleSave.getBoundsInScreen(it) }
            val ime = checkNotNull(keyboardBounds())
            sendStatus(0, Bundle().apply {
                putString("stream", "用户名键盘 $tag：save=$bounds IME=$ime visible=$area tap=(${area.centerX()},${area.centerY()})\n")
            })
            // 使用可见区域中心真实点按，避免 ACTION_CLICK 绕过键盘遮挡。
            tapNameNode(visibleSave, area)
            checkCurrentName(updated)
        } finally {
            uiAutomation.serviceInfo = uiAutomation.serviceInfo.apply { flags = originalFlags }
        }
    }

    private fun usernameEditing() {
        // 测试成绩使用独立名称，清理时不触碰设备上的历史成绩。
        val suffix = java.lang.Long.toHexString(System.nanoTime())
        val oldName = "旧名$suffix"
        val newName = "新名$suffix"
        val db = AppDatabase.get(targetContext).openHelper.writableDatabase
        fun records(): List<List<String>> = db.query(
            "SELECT id, userName, score, createdAt FROM scores ORDER BY id"
        ).use { cursor -> buildList {
            while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) })
        } }
        fun waitRecords(name: String) {
            val until = SystemClock.uptimeMillis() + 10_000L
            while (records().none { it[1] == name } && SystemClock.uptimeMillis() < until) SystemClock.sleep(20L)
            check(records().count { it[1] == name } == 1)
        }
        try {
            firstNameDefault(oldName)
            for (tag in listOf("zh", "en")) {
                main {
                    Prefs.setUserName(activity, oldName)
                    invoke(activity, "backToMenu")
                    click("menuPage", R.string.settings)
                }
                if (activity.resources.configuration.locales[0].language != tag) changeLanguage(tag)
                checkCurrentName(oldName)
                if (captureQa) screenshot("username-$tag-settings")
                openName(oldName)
                if (captureQa) screenshot("username-$tag-prefill")
                for (invalid in listOf("", "   ", " a ")) {
                    enterName(invalid)
                    nameAction(R.string.save)
                    check(Prefs.userName(targetContext) == oldName)
                    awaitNameNodes("原校验错误提示") { current -> current.any {
                        it.text?.toString() == activity.getString(R.string.name_too_short) && it.isVisibleToUser
                    } }
                    nameInput()
                }
                if (captureQa) screenshot("username-$tag-error")
                enterName("取消修改")
                nameAction(R.string.cancel)
                checkCurrentName(oldName)
                openName(oldName)
                enterName("返回丢弃")
                sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                waitForIdleSync()
                // 部分输入法先消费返回键；第二次返回才关闭弹窗。
                if (awaitNameNodes("返回后的设置页或命名弹窗") { current ->
                    current.any { it.className.toString() == "android.widget.EditText" ||
                        it.text?.toString() == activity.getString(R.string.current_username_fmt, oldName) }
                }.any { it.className.toString() == "android.widget.EditText" }) {
                    sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    waitForIdleSync()
                }
                checkCurrentName(oldName)
                openName(oldName)
                enterName("外部丢弃")
                val now = SystemClock.uptimeMillis()
                for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                    val event = android.view.MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, 1f, 1f, 0)
                    sendPointerSync(event)
                    event.recycle()
                }
                waitForIdleSync()
                checkCurrentName(oldName)
                // 真实打开下一次弹窗，确保取消路径确实关闭旧窗口。
                var current = oldName
                for (value in listOf("  中文  ", "ab", "😀", "12345678901234567890", "123456789012345678901", "  $newName  ")) {
                    openName(current)
                    enterName(value)
                    val filtered = value.take(20)
                    check(nameInput(filtered).text.toString() == filtered)
                    nameAction(R.string.save)
                    current = filtered.trim()
                    checkCurrentName(current)
                }
                if (captureQa) screenshot("username-$tag-saved")
                val keyboardName = "键盘-$tag"
                nameWithKeyboard(tag, current, keyboardName)
                current = keyboardName
                val monitor = addMonitor(MainActivity::class.java.name, null, false)
                try {
                    main { activity.recreate() }
                    activity = checkNotNull(waitForMonitorWithTimeout(monitor, 10_000L)) as MainActivity
                } finally { removeMonitor(monitor) }
                await { view().width > 0 }
                waitForIdleSync()
                checkCurrentName(current)
                openName(current)
                nameAction(R.string.cancel)
                main { back("settingsPage"); check(visible("menuPage") && !visible("pausePanel")) }
            }
            main { Prefs.setUserName(activity, oldName) }
            newPausedGame()
            main { queueDeath(137) }
            waitForIdleSync()
            waitRecords(oldName)
            val history = records()
            val best = Prefs.bestScore(targetContext)
            newPausedGame()
            lateinit var before: List<Any?>
            lateinit var retained: GameWorld
            main {
                click(activity.window.decorView, activity.getString(R.string.pause_action))
                click("pausePanel", R.string.settings)
                retained = world()
                before = snapshot(retained)
            }
            openName(oldName)
            enterName("暂停取消")
            nameAction(R.string.cancel)
            checkCurrentName(oldName)
            main { check(world() === retained && snapshot(world()) == before) }
            openName(oldName)
            enterName(newName)
            nameAction(R.string.save)
            checkCurrentName(newName)
            main { check(world() === retained && snapshot(world()) == before) }
            check(records() == history && Prefs.bestScore(targetContext) == best)
            if (captureQa) screenshot("username-pause-saved")
            changeLanguage(if (activity.resources.configuration.locales[0].language == "zh") "en" else "zh")
            checkCurrentName(newName)
            main {
                check(world() === retained && snapshot(world()) == before)
                back("settingsPage")
                check(visible("pausePanel") && !visible("menuPage") && !field<Boolean>(view(), "running"))
                check(snapshot(world()) == before)
                click("pausePanel", R.string.resume)
            }
            await { field<Boolean>(view(), "running") }
            main { view().pauseGame(); queueDeath(138) }
            waitForIdleSync()
            waitRecords(newName)
            val after = records()
            check(after.filter { it[1] != newName } == history) { "改名改写了历史姓名、分数、日期或记录编号" }
            check(after.single { it[1] == newName }[2] == "138")
            check(Prefs.bestScore(targetContext) == maxOf(best, 138))
        } finally {
            db.execSQL("DELETE FROM scores WHERE userName IN (?, ?)", arrayOf(oldName, newName))
            main { Prefs.setUserName(activity, testUser) }
        }
    }

    private fun controls(): ControlsBar = field(activity, "controlsBar")

    private fun inputGame() {
        main { invoke(activity, "restartGame") }
        await { controls().isEnabled && view().width > 0 }
        main {
            // 只停止计时循环，保留正式动作门控；时间由测试在世界锁内推进。
            invoke(view(), "stopLoop")
            synchronized(world()) { world().spawnEnabled = false }
        }
    }

    private fun controlArea(jump: Boolean): View = field(controls(), if (jump) "jumpArea" else "moveArea")

    private data class Finger(val id: Int, val x: Float, val y: Float)

    private fun finger(id: Int, target: View): Finger {
        val at = IntArray(2).also { target.getLocationOnScreen(it) }
        val origin = IntArray(2).also { activity.window.decorView.getLocationOnScreen(it) }
        return Finger(id, at[0] - origin[0] + target.width / 2f, at[1] - origin[1] + target.height / 2f)
    }

    private var gestureStart = 0L
    private fun dispatch(action: Int, vararg fingers: Finger, index: Int = 0) {
        val now = SystemClock.uptimeMillis()
        if (action == android.view.MotionEvent.ACTION_DOWN) gestureStart = now
        val props = fingers.map { f -> android.view.MotionEvent.PointerProperties().apply {
            id = f.id; toolType = android.view.MotionEvent.TOOL_TYPE_FINGER
        } }.toTypedArray()
        val coords = fingers.map { f -> android.view.MotionEvent.PointerCoords().apply {
            x = f.x; y = f.y; pressure = 1f; size = 1f
        } }.toTypedArray()
        val event = android.view.MotionEvent.obtain(gestureStart, now,
            action or (index shl android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT), fingers.size,
            props, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { activity.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    private fun tickJump() = synchronized(world()) { world().update(GameConfig.JUMP_AIRTIME_MS + 1) }
    private fun airborne() = world().player.isAirborne(world().gameTimeMs)
    private fun near(actual: Float, expected: Float) { check(kotlin.math.abs(actual - expected) < 0.1f) { "$actual != $expected" } }

    private fun tankFrame(name: String) {
        main {
            val v = view()
            val canvas = checkNotNull(v.holder.lockCanvas())
            try {
                synchronized(world()) {
                    v.javaClass.getDeclaredMethod("drawFrame", Canvas::class.java).apply { isAccessible = true }
                        .invoke(v, canvas)
                }
            } finally { v.holder.unlockCanvasAndPost(canvas) }
        }
        if (captureQa) screenshot(name)
    }

    private fun tapTankAction() {
        main {
            val action = finger(19, controlArea(true))
            dispatch(android.view.MotionEvent.ACTION_DOWN, action)
            dispatch(android.view.MotionEvent.ACTION_UP, action)
        }
        waitForIdleSync()
    }

    private fun tankDeliveryScenes() {
        for (direction in listOf(-1, 1)) {
            inputGame()
            lateinit var delivery: TankDelivery
            main {
                synchronized(world()) {
                    val w = world()
                    delivery = TankDelivery(direction, w.viewW)
                    w.tankDelivery = delivery
                    w.update(600)
                    w.player.x = delivery.x
                    w.update(0)
                    check(!w.player.riding && delivery.stoppedAtMs == null)
                    w.player.x = if (direction > 0) w.viewW else 0f
                }
            }
            tankFrame("tank-incoming-$direction")
            main {
                synchronized(world()) {
                    while (delivery.stoppedAtMs == null) world().update(GameConfig.PHYSICS_STEP_MS)
                    near(delivery.x, world().viewW * if (direction > 0) GameConfig.TANK_STOP_FRACTION else 1f - GameConfig.TANK_STOP_FRACTION)
                    check(world().returningNpcs.size == 1)
                }
            }
            tankFrame("tank-stopped-npc-$direction")
            main { synchronized(world()) { world().player.x = delivery.x } }
            tapTankAction()
            main {
                synchronized(world()) {
                    world().update(0)
                    check(airborne() && !world().player.riding)
                    world().update(GameConfig.JUMP_AIRTIME_MS / 2)
                    check(!world().player.riding)
                }
            }
            tankFrame("tank-airborne-$direction")
            main {
                synchronized(world()) {
                    world().update(GameConfig.JUMP_AIRTIME_MS / 2)
                    check(world().player.riding && world().tankDelivery == null)
                    check(world().returningNpcs.isNotEmpty())
                    check(world().player.lastFireDirection == direction)
                }
            }
            waitForIdleSync()
            tankFrame("tank-boarded-npc-$direction")
            main {
                synchronized(world()) {
                    world().update(GameConfig.TANK_WAIT_MS)
                    check(world().returningNpcs.isEmpty())
                }
            }
            inputGame()
            main {
                synchronized(world()) {
                    val w = world()
                    delivery = TankDelivery(direction, w.viewW)
                    w.tankDelivery = delivery
                    w.player.x = if (direction > 0) w.viewW else 0f
                    while (delivery.stoppedAtMs == null) w.update(GameConfig.PHYSICS_STEP_MS)
                    w.update(GameConfig.TANK_WAIT_MS - 1)
                    check(w.tankDelivery === delivery)
                    w.player.x = delivery.x
                    w.update(1)
                    check(w.tankDelivery == null && !w.player.riding)
                }
            }
        }
    }

    private fun tankControlsScenes() {
        for (left in listOf(false, true)) {
            inputGame()
            main {
                synchronized(world()) { deliverTank(world(), if (left) -1 else 1) }
                controls().setJumpOnLeft(left)
            }
            waitForIdleSync()
            lateinit var move: Finger
            lateinit var action: Finger
            var start = 0f
            main {
                move = finger(7, controlArea(false)); action = finger(19, controlArea(true))
                start = world().player.x
                check(controlArea(true).contentDescription == activity.getString(R.string.controls_fire, 3))
                dispatch(android.view.MotionEvent.ACTION_DOWN, move)
                dispatch(android.view.MotionEvent.ACTION_POINTER_DOWN, move, action, index = 1)
                check(world().player.ammoRemaining == 2 && world().bullets.size == 1)
            }
            waitForIdleSync()
            main {
                dispatch(android.view.MotionEvent.ACTION_MOVE, move.copy(x = move.x + 20), action)
                near(world().player.x, start + 20)
                synchronized(world()) { world().update(1000) }
                dispatch(android.view.MotionEvent.ACTION_MOVE, move.copy(x = move.x + 40), action)
                near(world().player.x, start + 40)
                check(world().player.ammoRemaining == 2 && !airborne())
            }
            waitForIdleSync()
            main {
                dispatch(android.view.MotionEvent.ACTION_MOVE, move.copy(x = move.x + 50), action)
                near(world().player.x, start + 50)
                dispatch(android.view.MotionEvent.ACTION_POINTER_UP, move.copy(x = move.x + 50), action, index = 1)
                dispatch(android.view.MotionEvent.ACTION_POINTER_DOWN, move.copy(x = move.x + 50), action, index = 1)
                check(world().player.ammoRemaining == 1)
                dispatch(android.view.MotionEvent.ACTION_POINTER_UP, move.copy(x = move.x + 50), action, index = 1)
                dispatch(android.view.MotionEvent.ACTION_UP, move.copy(x = move.x + 50))
            }
            waitForIdleSync()
            tankFrame("tank-fire-$left")
            tapTankAction()
            main {
                check(world().player.ammoRemaining == 0)
                check(!controlArea(true).isEnabled && controlArea(false).isEnabled)
                check(controlArea(true).contentDescription == activity.getString(R.string.controls_fire, 0))
                activity.onWindowFocusChanged(false); activity.onWindowFocusChanged(true)
                check(!controlArea(true).isEnabled && controlArea(false).isEnabled)
                val x = world().player.x
                dispatch(android.view.MotionEvent.ACTION_DOWN, move)
                dispatch(android.view.MotionEvent.ACTION_MOVE, move.copy(x = move.x - 20))
                dispatch(android.view.MotionEvent.ACTION_UP, move.copy(x = move.x - 20))
                near(world().player.x, x - 20)
                controlArea(true).performClick()
                check(world().player.ammoRemaining == 0 && !airborne())
                invoke(view(), "stopLoop")
            }
            tankFrame("tank-empty-$left")
            main {
                synchronized(world()) {
                    world().dragPlayerBy(-world().viewW * 2)
                    near(world().player.x, GameConfig.TANK_SIZE * world().viewW / 2)
                }
            }
            tankFrame("tank-edge-left-$left")
            main {
                synchronized(world()) {
                    world().dragPlayerBy(world().viewW * 2)
                    near(world().player.x, world().viewW - GameConfig.TANK_SIZE * world().viewW / 2)
                }
            }
            tankFrame("tank-edge-right-$left")
        }
    }

    private fun tankCombatScenes() {
        inputGame()
        main { synchronized(world()) { deliverTank(world()) } }
        waitForIdleSync()
        for (kind in BeastKind.entries) {
            var score = 0
            main {
                synchronized(world()) {
                    val w = world(); score = w.score
                    w.beasts.add(Beast(kind, GameConfig.BEAST_SIZE * w.viewW, 0f,
                        w.player.x + w.viewW * 0.2f, -1))
                }
            }
            tapTankAction()
            main {
                synchronized(world()) {
                    world().update(20L)
                    check(world().bullets.size == 1)
                    check(world().score == score)
                }
            }
            tankFrame("tank-shell-$kind")
            main {
                synchronized(world()) {
                    world().update(130L)
                    check(world().beasts.isEmpty())
                    check(world().score == score + if (kind == BeastKind.WOLF) GameConfig.SCORE_WOLF else GameConfig.SCORE_CRUSH_BEAST)
                    check(world().player.vehicleHits == 0)
                }
            }
        }
        fun hit() = synchronized(world()) {
            val w = world()
            w.items.add(FallingItem(ItemKind.ROCK, GameConfig.ITEM_SIZE * w.viewW, 0f, w.player.x,
                w.player.displayCenterY(w.groundY, w.gameTimeMs, w.viewH)))
            w.update(0)
        }
        main {
            hit(); hit()
            synchronized(world()) {
                val direction = world().player.lastFireDirection
                deliverTank(world(), -1)
                check(world().player.vehicleHits == 2 && world().player.ammoRemaining == 3)
                check(world().player.lastFireDirection == direction)
            }
        }
        waitForIdleSync()
        main {
            check(controlArea(true).isEnabled)
            controlArea(true).performClick()
            check(world().player.ammoRemaining == 2)
            hit()
            check(!world().player.riding && world().bullets.isNotEmpty())
        }
        waitForIdleSync()
        main {
            check(controlArea(true).contentDescription == activity.getString(R.string.controls_jump))
            check(controlArea(true).isEnabled)
            synchronized(world()) {
                val w = world(); val shell = w.bullets.single(); val score = w.score
                w.beasts.add(Beast(BeastKind.WOLF, GameConfig.BEAST_SIZE * w.viewW, 0f,
                    shell.x + shell.direction * w.viewW * 0.15f, -shell.direction))
                w.update(100)
                check(w.score == score + GameConfig.SCORE_WOLF)
                deliverTank(w)
                w.update(GameConfig.VEHICLE_DURATION_MS)
                check(!w.player.riding)
            }
        }
        waitForIdleSync()
        tapTankAction()
        main {
            check(airborne())
            invoke(activity, "backToMenu")
            check(world().tankDelivery == null && world().returningNpcs.isEmpty() && world().bullets.isEmpty())
            check(world().player.lastFireDirection == null && world().player.ammoRemaining == 0)
        }
        inputGame()
        main { check(controlArea(true).contentDescription == activity.getString(R.string.controls_jump)) }
    }

    private fun tankLanguageScenes() {
        for (phase in listOf("incoming", "waiting", "returning", "shell")) {
            inputGame()
            lateinit var retained: GameWorld
            lateinit var before: List<Any?>
            main {
                synchronized(world()) {
                    val w = world(); retained = w
                    if (phase == "incoming" || phase == "waiting") {
                        val d = TankDelivery(-1, w.viewW); w.tankDelivery = d; w.dragPlayerBy(-w.viewW)
                        w.update(600)
                        if (phase == "waiting") while (d.stoppedAtMs == null) w.update(GameConfig.PHYSICS_STEP_MS)
                    } else deliverTank(w, -1)
                }
            }
            waitForIdleSync()
            if (phase == "shell") {
                tapTankAction()
                main { synchronized(world()) { world().player.registerCrush(); world().refreshHud() } }
                waitForIdleSync()
            }
            main {
                click(activity.window.decorView, activity.getString(R.string.settings))
                before = snapshot(world())
            }
            changeLanguage(if (activity.resources.configuration.locales[0].language == "zh") "en" else "zh")
            main {
                check(world() === retained) { "$phase 重建替换了 world" }
                val after = snapshot(world())
                check(after == before) {
                    "$phase 重建状态差异：" + before.indices.filter { before[it] != after.getOrNull(it) }
                        .joinToString { "[$it] ${before[it]} -> ${after.getOrNull(it)}" }
                }
                check(!field<Boolean>(view(), "running"))
                val expected = if (world().player.riding) activity.getString(R.string.controls_fire, world().player.ammoRemaining)
                    else activity.getString(R.string.controls_jump)
                check(controlArea(true).contentDescription == expected)
                click("settingsPage", if (Prefs.jumpOnLeft(activity)) R.string.jump_right else R.string.jump_left)
                check(snapshot(world()) == before)
            }
            if (captureQa) screenshot("tank-language-$phase-settings")
            main {
                back("settingsPage")
                check(snapshot(world()) == before)
                click("pausePanel", R.string.menu_help)
            }
            verifyReadableTips()
            if (captureQa) captureScroll("helpPage", "tank-language-$phase-help")
            main { back("helpPage"); click("pausePanel", R.string.resume); invoke(view(), "stopLoop") }
            waitForIdleSync()
            main {
                val label = descendants(controlArea(true)).filterIsInstance<TextView>().single()
                val layout = checkNotNull(label.layout)
                for (line in 0 until layout.lineCount) check(layout.getEllipsisCount(line) == 0)
                check(layout.getLineBottom(layout.lineCount - 1) <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
            }
            tankFrame("tank-language-$phase-restored")
        }
    }

    private fun controlsPointers() {
        inputGame()
        main {
            controls().setJumpOnLeft(false)
        }
        waitForIdleSync()
        main {
            val move = finger(7, controlArea(false))
            val jump = finger(19, controlArea(true))
            val start = world().player.x
            dispatch(android.view.MotionEvent.ACTION_DOWN, move)
            near(world().player.x, start)
            var moved = move.copy(x = move.x + 20)
            dispatch(android.view.MotionEvent.ACTION_MOVE, moved)
            near(world().player.x, start + 20)
            dispatch(android.view.MotionEvent.ACTION_POINTER_DOWN, moved, jump, index = 1)
            check(airborne())
            moved = moved.copy(x = moved.x + 20)
            dispatch(android.view.MotionEvent.ACTION_MOVE, moved, jump)
            near(world().player.x, start + 40)
            // 抬起 index 0 后，跳跃触点变为 index 0，保持按住不连跳。
            dispatch(android.view.MotionEvent.ACTION_POINTER_UP, moved, jump)
            tickJump()
            dispatch(android.view.MotionEvent.ACTION_MOVE, jump)
            check(!airborne())
            dispatch(android.view.MotionEvent.ACTION_UP, jump)
            val stopped = world().player.x
            tickJump()
            near(world().player.x, stopped)
            // 相反按下顺序，跳跃主指释放后移动仍跟踪其独立 ID。
            dispatch(android.view.MotionEvent.ACTION_DOWN, jump)
            dispatch(android.view.MotionEvent.ACTION_POINTER_DOWN, jump, move, index = 1)
            dispatch(android.view.MotionEvent.ACTION_POINTER_UP, jump, move)
            dispatch(android.view.MotionEvent.ACTION_MOVE, move.copy(x = move.x - 20))
            near(world().player.x, stopped - 20)
            dispatch(android.view.MotionEvent.ACTION_UP, move.copy(x = move.x - 20))
            tickJump()
            // 两个区域均覆盖 A/B/C：B 不抢占，A 抬起后 B 不接管，新按下 C 可接管。
            for (isJump in listOf(false, true)) {
                val a = finger(3, controlArea(isJump))
                val b = a.copy(id = 11, x = a.x + 2)
                val c = a.copy(id = 25, x = a.x - 2)
                dispatch(android.view.MotionEvent.ACTION_DOWN, a)
                val before = world().player.x
                dispatch(android.view.MotionEvent.ACTION_POINTER_DOWN, a, b, index = 1)
                tickJump()
                dispatch(android.view.MotionEvent.ACTION_MOVE, a, b.copy(x = b.x + 30))
                near(world().player.x, before)
                check(!airborne())
                dispatch(android.view.MotionEvent.ACTION_POINTER_UP, a, b)
                dispatch(android.view.MotionEvent.ACTION_MOVE, b.copy(x = b.x + 40))
                near(world().player.x, before)
                check(!airborne())
                dispatch(android.view.MotionEvent.ACTION_POINTER_DOWN, b, c, index = 1)
                if (isJump) check(airborne())
                dispatch(android.view.MotionEvent.ACTION_POINTER_UP, b, c)
                dispatch(android.view.MotionEvent.ACTION_MOVE, c.copy(x = c.x + 15))
                near(world().player.x, before + if (isJump) 0 else 15)
                dispatch(android.view.MotionEvent.ACTION_UP, c.copy(x = c.x + 15))
                tickJump()
            }
            // 跳跃指跨入移动区后继续滑动，仍只属于跳跃区。
            val beforeCross = world().player.x
            dispatch(android.view.MotionEvent.ACTION_DOWN, jump)
            check(airborne())
            val crossed = jump.copy(x = move.x, y = move.y)
            dispatch(android.view.MotionEvent.ACTION_MOVE, crossed)
            dispatch(android.view.MotionEvent.ACTION_MOVE, crossed.copy(x = crossed.x - 20))
            near(world().player.x, beforeCross)
            dispatch(android.view.MotionEvent.ACTION_UP, crossed)
            tickJump()
            // 跨区拖动只移动；顶到边界后反向立即生效。
            dispatch(android.view.MotionEvent.ACTION_DOWN, move)
            val far = move.copy(x = view().width * 3f)
            dispatch(android.view.MotionEvent.ACTION_MOVE, far)
            check(!airborne())
            val edge = world().player.x
            dispatch(android.view.MotionEvent.ACTION_MOVE, far.copy(x = far.x - 10))
            near(world().player.x, edge - 10)
            dispatch(android.view.MotionEvent.ACTION_UP, far)
            val screen = finger(6, view())
            val x = world().player.x
            dispatch(android.view.MotionEvent.ACTION_DOWN, screen)
            dispatch(android.view.MotionEvent.ACTION_MOVE, screen.copy(x = screen.x - 80))
            dispatch(android.view.MotionEvent.ACTION_UP, screen)
            near(world().player.x, x)
            check(!airborne())
        }
    }

    private fun controlsCancellation() {
        inputGame()
        main {
            val move = finger(7, controlArea(false))
            val jump = finger(19, controlArea(true))
            val beforeCancel = world().player.x
            dispatch(android.view.MotionEvent.ACTION_DOWN, move)
            dispatch(android.view.MotionEvent.ACTION_CANCEL, move)
            near(world().player.x, beforeCancel)
            dispatch(android.view.MotionEvent.ACTION_MOVE, move.copy(x = move.x + 20))
            near(world().player.x, beforeCancel)
            check(!controlArea(false).isPressed && !airborne())
            dispatch(android.view.MotionEvent.ACTION_DOWN, jump)
            check(airborne() && controlArea(true).isPressed)
            tickJump()
            check(!airborne())
            dispatch(android.view.MotionEvent.ACTION_CANCEL, jump)
            check(!airborne() && !controlArea(true).isPressed)
            for (exit in listOf("pause", "focus", "background", "restart", "swap")) {
                dispatch(android.view.MotionEvent.ACTION_DOWN, move)
                dispatch(android.view.MotionEvent.ACTION_POINTER_DOWN, move, jump, index = 1)
                when (exit) {
                    "pause" -> { invoke(activity, "pauseGame"); invoke(activity, "resumeGame") }
                    "focus" -> { activity.onWindowFocusChanged(false); activity.onWindowFocusChanged(true) }
                    "background" -> { callActivityOnPause(activity); callActivityOnResume(activity) }
                    "restart" -> invoke(activity, "restartGame")
                    "swap" -> controls().setJumpOnLeft(!Prefs.jumpOnLeft(activity))
                }
                invoke(view(), "stopLoop")
                tickJump()
                val x = world().player.x
                dispatch(android.view.MotionEvent.ACTION_MOVE, move.copy(x = move.x + 40), jump)
                near(world().player.x, x)
                check(!airborne() && !controlArea(false).isPressed && !controlArea(true).isPressed)
                dispatch(android.view.MotionEvent.ACTION_CANCEL, move, jump)
            }
            // 教学的关闭手势归覆盖层，不传给背后的跳跃区。
            invoke(activity, "backToMenu")
            activity.getSharedPreferences("happyeat_prefs", 0).edit().putBoolean("tutorial_shown", false).commit()
            invoke(activity, "startFromMenu")
            check(visible("tutorialPanel"))
        }
        // GONE 首次显示需要布局；UP 的点击回调可能排入主线程队列。
        await {
            val tutorial = panel("tutorialPanel")
            tutorial.isShown && tutorial.isLaidOut && !tutorial.isLayoutRequested &&
                tutorial.width > 0 && tutorial.height > 0
        }
        waitForIdleSync()
        main {
            val tap = finger(4, controlArea(true))
            dispatch(android.view.MotionEvent.ACTION_DOWN, tap)
            dispatch(android.view.MotionEvent.ACTION_UP, tap)
        }
        waitForIdleSync()
        main {
            invoke(view(), "stopLoop")
            check(!visible("tutorialPanel")) { "教学关闭回调尚未生效" }
            check(!airborne()) { "教学关闭手势触发了跳跃" }
            // 死亡后、结束面板尚未收到通知时，世界门控已拒绝输入。
            synchronized(world()) { set(world(), "gameOver", true) }
            val x = world().player.x
            val freshMove = finger(5, controlArea(false))
            val freshJump = finger(22, controlArea(true))
            dispatch(android.view.MotionEvent.ACTION_DOWN, freshMove)
            dispatch(android.view.MotionEvent.ACTION_POINTER_DOWN, freshMove, freshJump, index = 1)
            dispatch(android.view.MotionEvent.ACTION_MOVE, freshMove.copy(x = freshMove.x - 40), freshJump)
            near(world().player.x, x)
            check(!airborne())
            dispatch(android.view.MotionEvent.ACTION_CANCEL, freshMove, freshJump)
            synchronized(world()) { set(world(), "gameOver", false) }
        }
    }

    private fun controlsLayout() {
        inputGame()
        lateinit var retained: GameWorld
        lateinit var state: List<Any?>
        var size = 0 to 0
        var surfaceSize = 0 to 0
        await { view().holder.surface.isValid && view().holder.surfaceFrame.width() > 0 }
        main {
            retained = world()
            state = snapshot(retained)
            size = view().width to view().height
            surfaceSize = view().holder.surfaceFrame.let { it.width() to it.height() }
            check(surfaceSize == size)
            val gameAt = IntArray(2).also { view().getLocationOnScreen(it) }
            val barAt = IntArray(2).also { controls().getLocationOnScreen(it) }
            check(gameAt[1] + view().height <= barAt[1])
            check(world().groundY <= world().viewH)
            check(world().player.displayCenterY(world().groundY, world().gameTimeMs, world().viewH) < world().viewH)
            invoke(activity, "pauseGame")
        }
        fun unchanged() = main {
            check(world() === retained && snapshot(world()) == state)
            check((view().width to view().height) == size)
            check(view().holder.surface.isValid)
            val actualSurface = view().holder.surfaceFrame.let { it.width() to it.height() }
            check(actualSurface == surfaceSize) { "Surface 尺寸改变：$surfaceSize -> $actualSurface" }
        }
        waitForIdleSync()
        unchanged()
        val bars = androidx.core.view.WindowInsetsCompat.Type.systemBars()
        lateinit var controller: androidx.core.view.WindowInsetsControllerCompat
        var originalBehavior = 0
        main {
            controller = androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            originalBehavior = controller.systemBarsBehavior
            // 测试期间允许显式 show，避免沉浸策略自动隐藏干扰可见性等待。
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
        fun awaitBars(visible: Boolean) {
            await {
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
                insets != null &&
                    insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars()) == visible &&
                    insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.navigationBars()) == visible &&
                    !activity.window.decorView.isLayoutRequested
            }
            waitForIdleSync()
            unchanged()
            sendStatus(0, Bundle().apply {
                putString("stream", "操作区系统栏 visible=$visible，View=$size，Surface=$surfaceSize，世界快照不变\n")
            })
        }
        try {
            main { controller.hide(bars) }
            awaitBars(false)
            main { controller.show(bars) }
            awaitBars(true)
            main { controller.hide(bars) }
            awaitBars(false)
        } finally {
            main {
                controller.systemBarsBehavior = originalBehavior
                controller.hide(bars)
            }
        }
        for (tag in listOf("zh", "en")) {
            main { click("pausePanel", R.string.settings) }
            if (activity.resources.configuration.locales[0].language != tag) changeLanguage(tag)
            waitForIdleSync()
            unchanged()
            if (captureQa) screenshot("$tag-settings-top")
            for (left in listOf(true, false)) {
                main { click("settingsPage", if (left) R.string.jump_left else R.string.jump_right) }
                waitForIdleSync()
                main {
                    check(Prefs.jumpOnLeft(activity) == left)
                    check((controlArea(true).left < controlArea(false).left) == left)
                    val rows = descendants(panel("settingsPage"))
                    check(rows.first { it.contentDescription?.toString() == activity.getString(if (left) R.string.jump_left else R.string.jump_right) }.isSelected)
                }
                unchanged()
            }
            main { click("settingsPage", R.string.jump_left) }
            val monitor = addMonitor(MainActivity::class.java.name, null, false)
            try {
                main { activity.recreate() }
                activity = checkNotNull(waitForMonitorWithTimeout(monitor, 10_000L)) as MainActivity
            } finally { removeMonitor(monitor) }
            await { view().width > 0 && controls().width > 0 }
            waitForIdleSync()
            unchanged()
            main { check(Prefs.jumpOnLeft(activity) && controlArea(true).left < controlArea(false).left) }
            if (captureQa) captureScroll("settingsPage", "$tag-settings")
            main { back("settingsPage"); click("pausePanel", R.string.menu_help) }
            waitForIdleSync()
            unchanged()
            if (captureQa) captureScroll("helpPage", "$tag-help")
            main { back("helpPage") }
            unchanged()
            if (captureQa) screenshot("$tag-pause")
        }
        main { invoke(activity, "resumeGame"); invoke(view(), "stopLoop") }
        if (captureQa) screenshot("game-jump-left")
        main { controls().setJumpOnLeft(false) }
        waitForIdleSync()
        if (captureQa) screenshot("game-jump-right")
    }

    private fun captureScreens() {
        for (tag in listOf("zh", "en")) {
            main { invoke(activity, "backToMenu"); click("menuPage", R.string.settings) }
            if (activity.resources.configuration.locales[0].language != tag) changeLanguage(tag)
            screenshot("$tag-settings-top")
            main { descendants(panel("settingsPage")).filterIsInstance<android.widget.ScrollView>().single().fullScroll(View.FOCUS_DOWN) }
            screenshot("$tag-settings-bottom")
            main { back("settingsPage") }
            SystemClock.sleep(3000)
            screenshot("$tag-menu")
            if (qaMenuOnly) continue
            main { click("menuPage", R.string.menu_help) }
            screenshot("$tag-help-top")
            captureScroll("helpPage", "$tag-help-pages")
            main { descendants(panel("helpPage")).filterIsInstance<android.widget.ScrollView>().single().fullScroll(View.FOCUS_DOWN) }
            screenshot("$tag-help-bottom")
            main { back("helpPage"); click("menuPage", R.string.leaderboard) }
            waitForIdleSync()
            screenshot("$tag-leaderboard")
            main {
                (panel("leaderboardPage") as LeaderboardPage).render(emptyList(), testUser, java.text.DateFormat.getDateInstance())
            }
            screenshot("$tag-leaderboard-empty")
            main { back("leaderboardPage"); click("menuPage", R.string.exit_game) }
            screenshot("$tag-exit")
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            main { showNameDialog(activity) {} }
            screenshot("$tag-name")
            val root = uiAutomation.rootInActiveWindow
            val ok = nodes(root).first { it.contentDescription?.toString() == activity.getString(R.string.ok) }
            ok.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            screenshot("$tag-name-error")
            val edit = nodes(uiAutomation.rootInActiveWindow).first { it.className.toString() == "android.widget.EditText" }
            edit.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
            edit.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            val bounds = android.graphics.Rect()
            edit.getBoundsInScreen(bounds)
            val now = SystemClock.uptimeMillis()
            for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                val event = android.view.MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
                sendPointerSync(event)
                event.recycle()
            }
            SystemClock.sleep(500)
            screenshot("$tag-name-keyboard")
            repeat(4) {
                nodes(uiAutomation.rootInActiveWindow).firstOrNull { it.className.toString() == "android.widget.ScrollView" }
                    ?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                waitForIdleSync()
            }
            screenshot("$tag-name-keyboard-bottom")
            val visibleOk = nodes(uiAutomation.rootInActiveWindow).first {
                it.contentDescription?.toString() == activity.getString(R.string.ok) && it.isVisibleToUser
            }
            val currentInput = nodes(uiAutomation.rootInActiveWindow).first { it.className.toString() == "android.widget.EditText" }
            currentInput.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "HappyEat QA")
            })
            visibleOk.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            waitForIdleSync()
            main { check(Prefs.userName(activity) == "HappyEat QA"); Prefs.setUserName(activity, testUser) }
            newPausedGame()
            main { (panel("tutorialPanel") as TutorialPanel).show() }
            screenshot("$tag-tutorial")
            main { (panel("tutorialPanel") as TutorialPanel).hide() }
            screenshot("$tag-hud")
            main { click(activity.window.decorView, activity.getString(R.string.pause_action)) }
            screenshot("$tag-pause")
            val before = countScores()
            main { queueDeath(Prefs.bestScore(targetContext) + 1) }
            waitForIdleSync(); awaitScores(before + 1)
            screenshot("$tag-game-over")
            main { click("gameOverPanel", R.string.leaderboard) }
            waitForIdleSync()
            screenshot("$tag-leaderboard-best")
            newPausedGame()
            for (phase in 0..5) {
                main {
                    synchronized(world()) { set(world(), "gameTimeMs", phase * GameConfig.BG_PHASE_MS) }
                    view().bgMode = BgMode.AUTO
                }
                screenshot("$tag-scene-$phase")
            }
            main {
                val w = world()
                synchronized(w) {
                    set(w, "gameTimeMs", 0L)
                    w.items.clear(); w.beasts.clear()
                    w.player.x = w.viewW * 0.9f
                    ItemKind.entries.forEachIndexed { index, kind ->
                        w.items.add(FallingItem(kind, GameConfig.ITEM_SIZE * w.viewW, 0f,
                            w.viewW * (index % 3 + 1) / 4, w.viewH * (index / 3 + 1) / 5))
                    }
                    BeastKind.entries.flatMap { listOf(it to -1, it to 1) }.forEachIndexed { index, (kind, direction) ->
                        w.beasts.add(Beast(kind, GameConfig.BEAST_SIZE * w.viewW, 0f, w.viewW * (index + 1) / 8, direction))
                    }
                }
                view().bgMode = BgMode.DAY
            }
            screenshot("$tag-sprites-walking")
            main { view().bgMode = BgMode.NIGHT }
            screenshot("$tag-sprites-night")
            main { synchronized(world()) { deliverTank(world()) }; view().bgMode = BgMode.DAY }
            screenshot("$tag-sprites-riding")
            main {
                synchronized(world()) {
                    world().player.riding = false
                    world().performPlayerAction()
                    set(world(), "gameTimeMs", GameConfig.JUMP_AIRTIME_MS / 2)
                    world().beasts.forEach { it.jumping = true; it.update(GameConfig.BEAST_JUMP_DURATION_MS / 2000f, allowJump = true) }
                }
                view().bgMode = BgMode.DAY
            }
            screenshot("$tag-sprites-jumping")
        }
    }

    private fun nativeAudio() {
        fun player(): android.media.MediaPlayer? = field(SoundManager, "bgmPlayer")
        fun silent() { check(player() == null); check(field<BgmTrack>(SoundManager, "bgmTrack") == BgmTrack.NONE) }
        main { invoke(activity, "backToMenu"); SoundManager.startBgm(BgmTrack.MENU) }
        var first: android.media.MediaPlayer? = null
        main { first = checkNotNull(player()); check(first!!.isPlaying) }
        await { checkNotNull(player()).currentPosition > PolishTestConfig.AUDIO_PROGRESS_MS }
        main {
            val position = first!!.currentPosition
            SoundManager.startBgm(BgmTrack.MENU)
            check(player() === first)
            check(first!!.currentPosition >= position)
            SoundManager.startBgm(BgmTrack.GAME)
            check(player() !== first && checkNotNull(player()).isPlaying)
            for (track in listOf(BgmTrack.MENU, BgmTrack.GAME, BgmTrack.MENU, BgmTrack.GAME)) {
                SoundManager.startBgm(track)
                check(field<BgmTrack>(SoundManager, "bgmTrack") == track)
            }
            SoundManager.play(SoundEvent.CATCH)
        }
        var duration = 0
        main { duration = checkNotNull(player()).duration }
        val deadline = SystemClock.uptimeMillis() + duration * PolishTestConfig.LOOP_TIMEOUT_FACTOR
        var previous = 0
        var wraps = 0
        var maximum = 0
        while (SystemClock.uptimeMillis() < deadline && wraps < PolishTestConfig.LOOP_COUNT) {
            main {
                val current = checkNotNull(player())
                check(current.isPlaying)
                val position = current.currentPosition
                if (position + PolishTestConfig.AUDIO_PROGRESS_MS < previous) wraps++
                maximum = maxOf(maximum, position)
                previous = position
            }
            SystemClock.sleep(PolishTestConfig.AUDIO_POLL_MS)
        }
        check(wraps >= PolishTestConfig.LOOP_COUNT)
        check(maximum > duration - PolishTestConfig.END_TOLERANCE_MS)
        main {
            SoundManager.startBgm(BgmTrack.NONE); silent()
            SoundManager.startBgm(BgmTrack.GAME)
            SoundManager.stopBgm(); silent()
            SoundManager.release(); SoundManager.release(); silent()
            SoundManager.init(activity)
            SoundManager.startBgm(BgmTrack.MENU)
            check(checkNotNull(player()).isPlaying)
            callActivityOnPause(activity)
            callActivityOnStop(activity)
            silent()
            callActivityOnRestart(activity)
            callActivityOnStart(activity)
            callActivityOnResume(activity)
            check(checkNotNull(player()).isPlaying)
        }
        sendStatus(0, Bundle().apply { putString("stream", "GAME duration=$duration positionMax=$maximum wraps=$wraps；SFX 已发起，听感待人工验收\n") })
    }

    private fun polishScenes() {
        inputGame()
        main {
            view().pauseGame()
            val w = world()
            synchronized(w) {
                w.items.clear(); w.beasts.clear()
                w.player.x = w.viewW * PolishTestConfig.PLAYER_X
                ItemKind.entries.forEachIndexed { index, kind ->
                    w.items.add(FallingItem(kind, GameConfig.ITEM_SIZE * w.viewW, 0f,
                        w.viewW * (index % PolishTestConfig.COLUMNS + 1) / (PolishTestConfig.COLUMNS + 1),
                        w.viewH * (index / PolishTestConfig.COLUMNS + 1) / PolishTestConfig.ROWS_DIVISOR))
                }
                BeastKind.entries.forEachIndexed { index, kind ->
                    w.beasts.add(Beast(kind, GameConfig.BEAST_SIZE * w.viewW, 0f,
                        w.viewW * (index + 1) / PolishTestConfig.BEAST_DIVISOR, 1))
                }
            }
        }
        for (phase in BackgroundSelection.scenes.indices.flatMap { scene ->
            BackgroundSelection.scenes[scene].indices.map { scene * BackgroundSelection.scenes[scene].size + it }
        }) {
            main {
                synchronized(world()) { set(world(), "gameTimeMs", phase * GameConfig.BG_PHASE_MS) }
                view().bgMode = BgMode.AUTO
            }
            if (captureQa) screenshot("polish-background-$phase")
        }
        for (direction in listOf(-1, 1)) {
            main {
                synchronized(world()) { deliverTank(world(), direction) }
                view().bgMode = BgMode.DAY
            }
            if (captureQa) screenshot("polish-riding-$direction")
        }
        for (left in listOf(false, true)) {
            main { controls().setJumpOnLeft(left); controls().isEnabled = true }
            if (captureQa) screenshot("polish-controls-$left-normal")
            main { controlArea(true).isPressed = true; controlArea(false).isPressed = true }
            if (captureQa) screenshot("polish-controls-$left-pressed")
            main { controls().isEnabled = false; check(!controlArea(true).isPressed && !controlArea(false).isPressed) }
            if (captureQa) screenshot("polish-controls-$left-disabled")
        }
        main { controls().isEnabled = true; controls().setJumpOnLeft(false) }
    }

    private fun drawingGeometry() {
        newPausedGame()
        main {
            val v = view()
            val w = world()
            val original: Bitmap? = field(v, "playerBmp")
            val originalSize = w.player.spriteSizePx
            val bitmap = Bitmap.createBitmap(107, 107, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
            try {
                synchronized(w) {
                    check(original != null)
                    w.player.invincibleUntilMs = 0
                    for (image in listOf(original, bitmap, null)) {
                        set(v, "playerBmp", image)
                        w.player.spriteSizePx = image?.width?.toFloat()
                        for (riding in listOf(false, true)) {
                            w.player.riding = riding
                            if (image === original) {
                                check(field<Bitmap>(v, "playerBmp").width == original.width)
                                check(w.player.displaySizePx == original.width.toFloat())
                                val frame = Bitmap.createBitmap(w.viewW.toInt(), w.viewH.toInt(), Bitmap.Config.ARGB_8888)
                                try {
                                    v.javaClass.getDeclaredMethod("drawPlayer", Canvas::class.java).apply { isAccessible = true }
                                        .invoke(v, Canvas(frame))
                                    val pixels = IntArray(frame.width * frame.height)
                                    frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
                                    check(pixels.any { Color.alpha(it) > 0 })
                                } finally { frame.recycle() }
                                continue
                            }
                            val canvasBitmap = Bitmap.createBitmap(w.viewW.toInt(), w.viewH.toInt(), Bitmap.Config.ARGB_8888)
                            try {
                                v.javaClass.getDeclaredMethod("drawPlayer", Canvas::class.java).apply { isAccessible = true }
                                    .invoke(v, Canvas(canvasBitmap))
                                val color = if (image != null) Color.MAGENTA else Color.rgb(232, 181, 124)
                                var left = canvasBitmap.width; var right = -1
                                var top = canvasBitmap.height; var bottom = -1
                                for (y in 0 until canvasBitmap.height) for (x in 0 until canvasBitmap.width) {
                                    if (canvasBitmap.getPixel(x, y) == color) {
                                        left = minOf(left, x); right = maxOf(right, x)
                                        top = minOf(top, y); bottom = maxOf(bottom, y)
                                    }
                                }
                                check(right >= left)
                                val box = w.player.hitbox(w.groundY, w.gameTimeMs, w.viewH)
                                check(kotlin.math.abs((left + right + 1) / 2f - (box.left + box.right) / 2f) <= 2f)
                                check(kotlin.math.abs((top + bottom + 1) / 2f - (box.top + box.bottom) / 2f) <= 2f)
                                check(kotlin.math.abs(right - left + 1 - w.player.displaySizePx) <= 2f)
                                check(kotlin.math.abs(bottom - top + 1 - w.player.displaySizePx) <= 2f)
                                check(kotlin.math.abs(box.right - box.left - w.player.displaySizePx * GameConfig.HITBOX_SCALE) < 0.01f)
                            } finally { canvasBitmap.recycle() }
                        }
                    }
                }
            } finally {
                set(v, "playerBmp", original)
                w.player.spriteSizePx = originalSize
                bitmap.recycle()
            }
        }
    }
}
