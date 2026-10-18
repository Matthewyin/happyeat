package com.happyeat.game.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import com.happyeat.game.R
import com.happyeat.game.data.Prefs
import com.happyeat.game.data.ScoreRecord
import com.happyeat.game.game.BgMode
import java.text.DateFormat
import java.util.Date

object AppCompatLocaleReader {
    fun current(): String = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags().let {
        when { it.isEmpty() -> ""; it.startsWith("zh") -> "zh"; else -> "en" }
    }
}

/** 方案二的共用颜色与尺寸，页面和活跃弹窗共用。 */
object Candy {
    const val INK = com.happyeat.game.config.GameConfig.Ui.INK
    const val CREAM_CARD = com.happyeat.game.config.GameConfig.Ui.CREAM_CARD
    const val BLUE = com.happyeat.game.config.GameConfig.Ui.BLUE
    const val BLUE_DARK = com.happyeat.game.config.GameConfig.Ui.BLUE_DARK
    const val GOLD = com.happyeat.game.config.GameConfig.Ui.GOLD
    const val MINT = com.happyeat.game.config.GameConfig.Ui.MINT
    const val SKY = com.happyeat.game.config.GameConfig.Ui.SKY
    const val MUTED_DARK = com.happyeat.game.config.GameConfig.Ui.MUTED_DARK
    const val TOMATO = com.happyeat.game.config.GameConfig.Ui.TOMATO
    const val AMBER_DARK = com.happyeat.game.config.GameConfig.Ui.AMBER_DARK
    const val SCRIM = com.happyeat.game.config.GameConfig.Ui.SCRIM
    const val RIPPLE = com.happyeat.game.config.GameConfig.Ui.RIPPLE
    const val RADIUS = com.happyeat.game.config.GameConfig.Ui.RADIUS
    const val STROKE = com.happyeat.game.config.GameConfig.Ui.STROKE
    const val GAP = com.happyeat.game.config.GameConfig.Ui.GAP
    const val PAD = com.happyeat.game.config.GameConfig.Ui.PAD
    const val SMALL = com.happyeat.game.config.GameConfig.Ui.SMALL
    const val TOUCH = com.happyeat.game.config.GameConfig.Ui.TOUCH
    const val ICON = com.happyeat.game.config.GameConfig.Ui.ICON
    const val ART_ICON = com.happyeat.game.config.GameConfig.Ui.ART_ICON
    const val TITLE = com.happyeat.game.config.GameConfig.Ui.TITLE
    const val BODY = com.happyeat.game.config.GameConfig.Ui.BODY
    const val SMALL_TEXT = com.happyeat.game.config.GameConfig.Ui.SMALL_TEXT
    const val HERO_WEIGHT = com.happyeat.game.config.GameConfig.Ui.HERO_WEIGHT
    const val HERO_MIN = com.happyeat.game.config.GameConfig.Ui.HERO_MIN
    const val BRAND_HEIGHT = com.happyeat.game.config.GameConfig.Ui.BRAND_HEIGHT
    const val ICON_LIMIT = com.happyeat.game.config.GameConfig.Ui.ICON_LIMIT
    const val DIALOG_WIDTH_MARGIN = com.happyeat.game.config.GameConfig.Ui.DIALOG_WIDTH_MARGIN
    const val HUD_SMALL = com.happyeat.game.config.GameConfig.Ui.HUD_SMALL
    const val HUD_SCORE = com.happyeat.game.config.GameConfig.Ui.HUD_SCORE
}

fun candyTypeface(context: Context): Typeface = ResourcesCompat.getFont(context, R.font.baloo2) ?: Typeface.DEFAULT_BOLD
fun uiDp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
fun uiShape(context: Context, color: Int, round: Boolean = false) = GradientDrawable().apply {
    if (round) shape = GradientDrawable.OVAL else cornerRadius = Candy.RADIUS * context.resources.displayMetrics.density
    setColor(color)
    setStroke(uiDp(context, Candy.STROKE), Candy.INK)
}

fun uiBitmap(context: Context, name: String, limit: Int = Candy.ICON_LIMIT): Bitmap? = try {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open("sprites/$name.png").use { BitmapFactory.decodeStream(it, null, options) }
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / (options.inSampleSize * 2) >= limit) options.inSampleSize *= 2
    options.inJustDecodeBounds = false
    context.assets.open("sprites/$name.png").use { BitmapFactory.decodeStream(it, null, options) }
} catch (_: java.io.IOException) { null }

fun uiIcon(context: Context, name: String): ImageView = ImageView(context).apply {
    setImageBitmap(uiBitmap(context, name))
    scaleType = ImageView.ScaleType.FIT_CENTER
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
}

fun uiButton(context: Context, textRes: Int, icon: String? = null, primary: Boolean = false,
    round: Boolean = false, action: () -> Unit): View {
    val bitmap = icon?.let { uiBitmap(context, it) }
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = uiDp(context, Candy.TOUCH)
        minimumWidth = uiDp(context, Candy.TOUCH)
        setPadding(uiDp(context, Candy.GAP), uiDp(context, Candy.SMALL), uiDp(context, Candy.GAP), uiDp(context, Candy.SMALL))
        contentDescription = context.getString(textRes)
        background = RippleDrawable(ColorStateList.valueOf(Candy.RIPPLE), uiShape(context,
            if (primary) Candy.BLUE else if (round && icon == "ui_trophy") Candy.GOLD
            else if (round && icon == "ui_settings") Candy.MINT else Candy.CREAM_CARD, round), null)
        isFocusable = true
        setOnClickListener { action() }
    }
    if (bitmap != null) row.addView(ImageView(context).apply {
        setImageBitmap(bitmap)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }, LinearLayout.LayoutParams(uiDp(context, Candy.ICON), uiDp(context, Candy.ICON)))
    if (!round || bitmap == null) row.addView(TextView(context).apply {
        setText(textRes)
        textSize = if (primary) com.happyeat.game.config.GameConfig.Ui.PRIMARY_TEXT else Candy.BODY
        setTextColor(if (primary) Candy.CREAM_CARD else Candy.INK)
        typeface = candyTypeface(context)
        if (primary) setTypeface(typeface, Typeface.BOLD)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        gravity = Gravity.CENTER
        if (bitmap != null) setPadding(uiDp(context, Candy.SMALL), 0, 0, 0)
    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    return row
}

enum class PillStyle { PRIMARY, GHOST, DANGER }

abstract class OverlayPanel(context: Context) : FrameLayout(context) {
    init { setBackgroundColor(Candy.SCRIM); isClickable = true; isFocusable = true; visibility = GONE }
    protected fun dp(v: Int) = uiDp(context, v)
    protected fun styledLabel(size: Float = Candy.BODY, bold: Boolean = false, color: Int = Candy.INK) = TextView(context).apply {
        textSize = size; setTextColor(color); gravity = Gravity.CENTER
        typeface = candyTypeface(context)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    protected fun cardColumn(): LinearLayout {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = uiShape(context, Candy.CREAM_CARD)
            setPadding(dp(Candy.PAD), dp(Candy.PAD), dp(Candy.PAD), dp(Candy.PAD))
        }
        val scroll = ScrollView(context).apply { isFillViewport = false; addView(col) }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            setMargins(dp(Candy.PAD), dp(Candy.PAD), dp(Candy.PAD), dp(Candy.PAD))
        })
        return col
    }
    protected fun wideParams() = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(Candy.GAP) }
    protected fun colParams() = wideParams()
    protected fun pillButton(res: Int, style: PillStyle = PillStyle.GHOST, action: () -> Unit): View =
        uiButton(context, res, buttonIcon(res), style == PillStyle.PRIMARY, action = action)
    protected fun buttonIcon(res: Int): String? = when (res) {
        R.string.resume, R.string.menu_start -> "ui_play"
        R.string.restart -> "ui_restart"
        R.string.settings -> "ui_settings"
        R.string.menu_help -> "ui_help"
        R.string.leaderboard -> "ui_trophy"
        R.string.back_to_menu -> "ui_back"
        else -> null
    }
    open fun isVisibleNow() = visibility == VISIBLE
    fun show() { visibility = VISIBLE }
    fun hide() { visibility = GONE }
}

abstract class Page(context: Context) : OverlayPanel(context) {
    var onShowLoad: (() -> Unit)? = null
    init { setBackgroundColor(Candy.SKY) }
    protected open fun onShow() {}
    fun showPage() { show(); onShow(); onShowLoad?.invoke() }
    fun hidePage() = hide()
    protected fun pageColumn(title: Int, back: () -> Unit): LinearLayout {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val bar = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Candy.PAD), dp(Candy.GAP), dp(Candy.PAD), dp(Candy.GAP))
        }
        bar.addView(uiButton(context, R.string.back, "ui_back", round = true, action = back),
            LinearLayout.LayoutParams(dp(Candy.TOUCH), dp(Candy.TOUCH)))
        bar.addView(styledLabel(Candy.TITLE, true).apply { setText(title) }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bar)
        val scroll = ScrollView(context)
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = uiShape(context, Candy.CREAM_CARD)
            setPadding(dp(Candy.PAD), dp(Candy.PAD), dp(Candy.PAD), dp(Candy.PAD))
        }
        scroll.addView(col)
        root.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(dp(Candy.PAD), 0, dp(Candy.PAD), dp(Candy.PAD))
        })
        return col
    }
    protected fun section(col: LinearLayout, res: Int, icon: String) {
        col.addView(uiIcon(context, icon), LinearLayout.LayoutParams(dp(Candy.ART_ICON), dp(Candy.ART_ICON)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(styledLabel(Candy.BODY, true).apply { setText(res) }, wideParams())
    }
}

class MenuPage(context: Context) : Page(context) {
    var onStart: (() -> Unit)? = null
    var onLeaderboard: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    var onHelp: (() -> Unit)? = null
    var onExit: (() -> Unit)? = null
    init {
        fun menuParams() = wideParams().apply { bottomMargin = dp(com.happyeat.game.config.GameConfig.Ui.MENU_GAP) }
        val hero = uiBitmap(context, "menu_hero", context.resources.displayMetrics.heightPixels)
        addView(ImageView(context).apply {
            setImageBitmap(hero); scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val scroll = ScrollView(context).apply { isFillViewport = true }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(Candy.PAD), dp(Candy.GAP), dp(Candy.PAD), dp(com.happyeat.game.config.GameConfig.Ui.MENU_GAP))
        }
        scroll.addView(col)
        val brand = uiBitmap(context, "title_emblem", context.resources.displayMetrics.widthPixels)
        if (brand != null) col.addView(ImageView(context).apply {
            setImageBitmap(brand); scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = context.getString(R.string.app_name)
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(Candy.BRAND_HEIGHT)))
        else col.addView(styledLabel(Candy.TITLE, true).apply { setText(R.string.app_name) })
        col.addView(View(context), LinearLayout.LayoutParams(0, dp(Candy.HERO_MIN), Candy.HERO_WEIGHT))
        col.addView(uiButton(context, R.string.menu_start, "ui_play", primary = true) { onStart?.invoke() }.apply {
            minimumHeight = dp(com.happyeat.game.config.GameConfig.Ui.MENU_PRIMARY_HEIGHT)
            (this as LinearLayout).let { row ->
                (row.getChildAt(row.childCount - 1) as TextView).apply {
                    textSize = com.happyeat.game.config.GameConfig.Ui.MENU_PRIMARY_TEXT
                    setTypeface(typeface, Typeface.BOLD)
                }
            }
        }, menuParams())
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER }
        fun secondary(res: Int, icon: String, action: () -> Unit) {
            val group = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                contentDescription = context.getString(res); isFocusable = true
                setOnClickListener { action() }
            }
            group.addView(uiButton(context, res, icon, round = true, action = action).apply {
                isClickable = false; isFocusable = false
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }, LinearLayout.LayoutParams(dp(Candy.TOUCH), dp(Candy.TOUCH)))
            group.addView(styledLabel(Candy.SMALL_TEXT, true).apply {
                setText(res); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                setTextColor(Candy.CREAM_CARD)
                setShadowLayer(com.happyeat.game.config.GameConfig.Ui.TEXT_SHADOW, 0f, 0f, Candy.INK)
            }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            row.addView(group, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        secondary(R.string.leaderboard, "ui_trophy") { onLeaderboard?.invoke() }
        secondary(R.string.settings, "ui_settings") { onSettings?.invoke() }
        secondary(R.string.menu_help, "ui_help") { onHelp?.invoke() }
        col.addView(row, menuParams())
        col.addView(uiButton(context, R.string.exit_game, "ui_exit") { onExit?.invoke() }.apply {
            background = null
            (this as LinearLayout).let { row ->
                (row.getChildAt(row.childCount - 1) as TextView).apply {
                    setTextColor(Candy.CREAM_CARD)
                    setShadowLayer(com.happyeat.game.config.GameConfig.Ui.TEXT_SHADOW, 0f, 0f, Candy.INK)
                }
            }
        }, menuParams())
    }
}

class SettingsPage(context: Context) : Page(context) {
    var onBack: (() -> Unit)? = null
    var onEditUserName: () -> Unit = {}
    private val userNameText = styledLabel()
    var onLanguageSelected: (String) -> Unit = {}
    var onBackgroundSelected: (BgMode) -> Unit = {}
    var onJumpSideSelected: (Boolean) -> Unit = {}
    private val jumpRows = linkedMapOf<Boolean, LinearLayout>()
    private val languageRows = linkedMapOf<String, LinearLayout>()
    private val backgroundRows = linkedMapOf<String, LinearLayout>()
    private fun select(row: LinearLayout, selected: Boolean) {
        row.isSelected = selected
        row.background = uiShape(context, if (selected) Candy.MINT else Candy.CREAM_CARD)
        row.getChildAt(1).visibility = if (selected) VISIBLE else INVISIBLE
    }
    public override fun onShow() {
        userNameText.text = context.getString(R.string.current_username_fmt, Prefs.userName(context).orEmpty())
        jumpRows.forEach { (left, row) -> select(row, left == Prefs.jumpOnLeft(context)) }
        languageRows.forEach { (tag, row) -> select(row, tag == AppCompatLocaleReader.current()) }
        backgroundRows.forEach { (tag, row) -> select(row, tag == Prefs.backgroundMode(context)) }
    }
    private fun option(col: LinearLayout, label: Int, action: () -> Unit): LinearLayout {
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(Candy.TOUCH)
            setPadding(dp(Candy.GAP), dp(Candy.SMALL), dp(Candy.GAP), dp(Candy.SMALL))
            contentDescription = context.getString(label); isFocusable = true; setOnClickListener { action() }
        }
        row.addView(styledLabel().apply {
            setText(label); gravity = Gravity.START
            if (label == R.string.bg_night) uiBitmap(context, "ui_moon")?.let { bitmap ->
                val icon = android.graphics.drawable.BitmapDrawable(resources, bitmap).apply {
                    setBounds(0, 0, dp(Candy.ICON), dp(Candy.ICON))
                }
                setCompoundDrawables(icon, null, null, null)
                compoundDrawablePadding = dp(Candy.SMALL)
            }
        }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(uiIcon(context, "ui_check"), LinearLayout.LayoutParams(dp(Candy.ICON), dp(Candy.ICON)))
        col.addView(row, wideParams())
        return row
    }
    init {
        val col = pageColumn(R.string.settings) { onBack?.invoke() }
        col.addView(userNameText, wideParams())
        col.addView(uiButton(context, R.string.edit_username, "ui_settings") { onEditUserName() }, wideParams())
        section(col, R.string.entry_language, "ui_language")
        listOf(R.string.follow_system to "", R.string.english to "en", R.string.chinese to "zh").forEach { (label, tag) ->
            languageRows[tag] = option(col, label) { onLanguageSelected(tag); onShow() }
        }
        section(col, R.string.jump_position, "ui_tap")
        listOf(R.string.jump_right to false, R.string.jump_left to true).forEach { (label, left) ->
            jumpRows[left] = option(col, label) {
                Prefs.setJumpOnLeft(context, left)
                onJumpSideSelected(left)
                onShow()
            }
        }
        section(col, R.string.entry_background, "ui_sun")
        listOf(R.string.bg_auto to "auto", R.string.bg_day to "day", R.string.bg_sunset to "sunset", R.string.bg_night to "night").forEach { (label, tag) ->
            backgroundRows[tag] = option(col, label) {
                Prefs.setBackgroundMode(context, tag); onShow()
                onBackgroundSelected(when (tag) { "day" -> BgMode.DAY; "sunset" -> BgMode.SUNSET; "night" -> BgMode.NIGHT; else -> BgMode.AUTO })
            }
        }
        onShow()
    }
}

class HelpPage(context: Context) : Page(context) {
    var onBack: (() -> Unit)? = null
    init {
        val col = pageColumn(R.string.help_title) { onBack?.invoke() }
        listOf("bean" to R.string.help_bean, "meat" to R.string.help_meat, "pie" to R.string.help_pie,
            "tank" to R.string.help_vehicle, "bomb" to R.string.help_hazard, "tiger" to R.string.help_beast,
            "ui_tap" to R.string.help_controls, "ui_move" to R.string.help_air_move, "ui_help" to R.string.help_edges).forEach { (icon, text) ->
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(uiIcon(context, icon), LinearLayout.LayoutParams(dp(Candy.ART_ICON), dp(Candy.ART_ICON)))
            row.addView(styledLabel(Candy.BODY).apply { setText(text); gravity = Gravity.START; setPadding(dp(Candy.GAP), 0, 0, 0) },
                LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            col.addView(row, wideParams())
        }
    }
}

class LeaderboardPage(context: Context) : Page(context) {
    var onBack: (() -> Unit)? = null
    private val listCol = pageColumn(R.string.leaderboard) { onBack?.invoke() }
    fun render(records: List<ScoreRecord>, userName: String?, dateFormat: DateFormat) {
        listCol.removeAllViews()
        listCol.addView(uiIcon(context, "ui_trophy"), LinearLayout.LayoutParams(dp(Candy.ART_ICON), dp(Candy.ART_ICON)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        if (records.isEmpty()) listCol.addView(styledLabel().apply { setText(R.string.no_records) }, wideParams())
        val bestId = records.filter { it.userName == userName }.maxByOrNull { it.score }?.id
        records.forEachIndexed { index, record ->
            val mine = record.id == bestId
            if (mine) listCol.addView(uiIcon(context, "ui_check"), LinearLayout.LayoutParams(dp(Candy.ICON), dp(Candy.ICON)))
            listCol.addView(styledLabel().apply {
                text = context.getString(if (mine) R.string.best_marker_fmt else R.string.rank_entry_fmt,
                    index + 1, record.userName, record.score, dateFormat.format(Date(record.createdAt)))
                background = uiShape(context, if (mine) Candy.MINT else Candy.CREAM_CARD)
                setPadding(dp(Candy.GAP), dp(Candy.GAP), dp(Candy.GAP), dp(Candy.GAP))
            }, wideParams())
        }
    }
}

class GameOverPanel(context: Context) : OverlayPanel(context) {
    var onRestart: (() -> Unit)? = null
    var onLeaderboard: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null
    private val scoreText = styledLabel()
    private val bestText = styledLabel()
    init {
        val col = cardColumn()
        col.addView(styledLabel(Candy.TITLE, true).apply { setText(R.string.game_over) }, wideParams())
        col.addView(scoreText, wideParams()); col.addView(bestText, wideParams())
        col.addView(pillButton(R.string.restart, PillStyle.PRIMARY) { onRestart?.invoke() }, wideParams())
        col.addView(pillButton(R.string.leaderboard) { onLeaderboard?.invoke() }, wideParams())
        col.addView(pillButton(R.string.back_to_menu) { onMenu?.invoke() }, wideParams())
    }
    fun show(score: Int, best: Int) {
        scoreText.text = context.getString(R.string.your_score_fmt, score)
        bestText.text = context.getString(R.string.best_score_fmt, best)
        show()
    }
}

class PausePanel(context: Context) : OverlayPanel(context) {
    var onResume: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    var onHelp: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null
    init {
        val col = cardColumn()
        col.addView(styledLabel(Candy.TITLE, true).apply { setText(R.string.paused) }, wideParams())
        col.addView(pillButton(R.string.resume, PillStyle.PRIMARY) { onResume?.invoke() }, wideParams())
        col.addView(pillButton(R.string.settings) { onSettings?.invoke() }, wideParams())
        col.addView(pillButton(R.string.menu_help) { onHelp?.invoke() }, wideParams())
        col.addView(pillButton(R.string.back_to_menu) { onMenu?.invoke() }, wideParams())
    }
}

class TutorialPanel(context: Context) : OverlayPanel(context) {
    var onDismiss: (() -> Unit)? = null
    init {
        val col = cardColumn()
        for ((icon, label) in listOf("ui_move" to R.string.tutorial_drag, "ui_tap" to R.string.tutorial_jump)) {
            col.addView(uiIcon(context, icon), LinearLayout.LayoutParams(dp(Candy.ART_ICON), dp(Candy.ART_ICON)))
            col.addView(styledLabel().apply { setText(label) }, wideParams())
        }
        col.addView(styledLabel(Candy.SMALL_TEXT).apply { setText(R.string.tutorial_hint) }, wideParams())
        val dismiss = { onDismiss?.invoke(); hide() }
        setOnClickListener { dismiss() }; col.setOnClickListener { dismiss() }
    }
}
