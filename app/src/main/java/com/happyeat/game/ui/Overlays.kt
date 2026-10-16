package com.happyeat.game.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.happyeat.game.R
import com.happyeat.game.data.Prefs
import com.happyeat.game.data.ScoreRecord
import com.happyeat.game.game.BgMode
import java.text.DateFormat
import java.util.Date

/** 当前 per-app locale 标签（""/en/zh），供设置页回显选中态 */
object AppCompatLocaleReader {
    fun current(): String {
        val tags = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return when {
            tags.isEmpty() -> ""
            tags.startsWith("zh") -> "zh"
            else -> "en"
        }
    }
}

/** 糖果街机配色 */
object Candy {
    const val INK = 0xFF0B3D2E.toInt()
    const val CREAM_CARD = 0xFFFFFDF4.toInt()
    const val AMBER = 0xFFF59E0B.toInt()
    const val AMBER_DARK = 0xFFB45309.toInt()
    const val AMBER_GLOW = 0xFFFDE68A.toInt()
    const val GOLD = 0xFFFCD34D.toInt()
    const val MUTED_DARK = 0x660B3D2E.toInt()
    const val TOMATO = 0xFFEF4444.toInt()
    const val TOMATO_DEEP = 0xFFB91C1C.toInt()
}

enum class PillStyle { PRIMARY, GHOST, DANGER }

fun candyTypeface(context: Context): Typeface =
    ResourcesCompat.getFont(context, R.font.baloo2) ?: Typeface.DEFAULT_BOLD

/** 游戏内浮层（暂停/结束/教学）：奶油圆角卡片风格 */
abstract class OverlayPanel(context: Context) : FrameLayout(context) {

    init {
        setBackgroundColor(0x99000000.toInt())
        isClickable = true
        isFocusable = true
        visibility = GONE
    }

    protected fun column(): LinearLayout {
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        return col
    }

    protected fun cardColumn(): LinearLayout {
        val card = FrameLayout(context)
        card.background = GradientDrawable().apply {
            cornerRadius = dpf(32f)
            setColor(Candy.CREAM_CARD)
            setStroke(dp(3), Candy.INK)
        }
        val col = LinearLayout(context)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        card.addView(col)
        addView(card, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER,
        ).apply { setMargins(dp(28), 0, dp(28), 0) })
        col.setPadding(dp(32), dp(30), dp(32), dp(26))
        return col
    }

    protected fun styledLabel(sizeSp: Float, bold: Boolean = false, color: Int = Candy.INK): TextView =
        TextView(context).apply {
            setTextColor(color)
            textSize = sizeSp
            gravity = Gravity.CENTER
            typeface = candyTypeface(context)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    protected fun pillButton(textRes: Int, style: PillStyle = PillStyle.GHOST, onClick: () -> Unit): Button =
        Button(context).apply {
            setText(textRes)
            setSingleLine(true)
            textSize = 17f
            minWidth = 0
            minHeight = 0
            setPadding(0, dp(12), 0, dp(12))
            typeface = candyTypeface(context)
            setTypeface(typeface, Typeface.BOLD)
            val (fill, stroke, text) = when (style) {
                PillStyle.PRIMARY -> Triple(Candy.AMBER, Candy.AMBER_GLOW, Candy.INK)
                PillStyle.DANGER -> Triple(0x40EF4444, Candy.TOMATO, 0xFFFCA5A5.toInt())
                PillStyle.GHOST -> Triple(0x14101724.toInt(), 0x330B3D2E.toInt(), Candy.INK)
            }
            val shape = GradientDrawable().apply {
                cornerRadius = dpf(28f)
                setColor(fill)
                setStroke(dp(2), stroke)
            }
            background = RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF), shape, null,
            )
            setTextColor(text)
            setOnClickListener { onClick() }
        }

    protected fun styledButton(textRes: Int, onClick: () -> Unit): Button =
        pillButton(textRes, PillStyle.GHOST, onClick)

    protected fun colParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(12) }

    protected fun wideParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(14) }

    protected fun loadUiBitmap(name: String): Bitmap? = try {
        context.assets.open("sprites/$name").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    protected fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    protected fun dpf(v: Float): Float = v * context.resources.displayMetrics.density

    open fun isVisibleNow(): Boolean = visibility == VISIBLE

    fun show() {
        visibility = VISIBLE
    }

    fun hide() {
        visibility = GONE
    }
}

/** ============ 全屏页面（独立界面，奶油糖果风） ============ */

abstract class Page(context: Context) : OverlayPanel(context) {

    var onShowLoad: (() -> Unit)? = null

    protected open fun onShow() {}

    init {
        visibility = GONE
        isClickable = true
        isFocusable = true
    }

    override fun isVisibleNow(): Boolean = visibility == VISIBLE

    fun showPage() {
        visibility = VISIBLE
        alpha = 0f
        translationX = dpf(70f)
        animate().alpha(1f).translationX(0f).setDuration(230).start()
        onShow()
        onShowLoad?.invoke()
    }

    fun hidePage() {
        visibility = GONE
    }

    /** 页面标题栏：返回按钮 + 大标题 */
    protected fun titleBar(title: String, onBack: () -> Unit): LinearLayout {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(10))
        }
        val back = TextView(context).apply {
            text = "◀"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = candyTypeface(context)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x330B3D2E.toInt())
            }
            setOnClickListener { onBack() }
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(52), dp(52)))
        bar.addView(TextView(context).apply {
            text = title
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = candyTypeface(context)
            setTypeface(typeface, Typeface.BOLD)
            setShadowLayer(dpf(4f), 0f, dpf(2f), 0x66000000.toInt())
        }, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
        ).apply { marginStart = dp(16) })
        return bar
    }

    /** 奶油圆角内容卡（带滚动） */
    protected fun contentCard(parent: ViewGroup): LinearLayout {
        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
        }
        scroll.addView(col)
        val card = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dpf(30f)
                setColor(Candy.CREAM_CARD)
                setStroke(dp(3), Candy.INK)
            }
        }
        card.addView(scroll)
        parent.addView(card, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        ).apply { setMargins(dp(20), dp(6), dp(20), dp(24)) })
        return col
    }

    /** 装饰圆斑（页面氛围） */
    protected fun addBlobs(container: FrameLayout, colors: List<Int>) {
        colors.forEachIndexed { i, c ->
            val blob = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                }
            }
            val size = dp(150 + i * 70)
            container.addView(blob, FrameLayout.LayoutParams(size, size).apply {
                gravity = when (i % 4) {
                    0 -> Gravity.TOP or Gravity.START
                    1 -> Gravity.BOTTOM or Gravity.END
                    2 -> Gravity.BOTTOM or Gravity.START
                    else -> Gravity.TOP or Gravity.END
                }
                marginStart = dp(-30 + i * 18)
                topMargin = dp(80 + i * 130)
            })
        }
    }

    /** 糖果双层按钮：底层深色 + 面层抬起，按压下沉 */
    protected fun candyButton(
        parent: ViewGroup,
        text: String,
        base: Int,
        shadow: Int,
        textColor: Int,
        onClick: () -> Unit,
    ): FrameLayout {
        val shadowView = View(context).apply {
            background = GradientDrawable().apply { cornerRadius = dpf(30f); setColor(shadow) }
        }
        val label = TextView(context).apply {
            this.text = text
            textSize = 18f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            typeface = candyTypeface(context)
            setTypeface(typeface, Typeface.BOLD)
            background = RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x40FFFFFF),
                GradientDrawable().apply { cornerRadius = dpf(30f); setColor(base) },
                null,
            )
        }
        val wrap = FrameLayout(context)
        wrap.addView(shadowView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        wrap.addView(label, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
        ).apply { setMargins(0, 0, 0, dp(6)) })
        wrap.setOnClickListener { onClick() }
        wrap.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().translationY(dpf(5f)).setDuration(50).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().translationY(0f).setDuration(90).start()
            }
            false
        }
        parent.addView(wrap, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(62),
        ).apply { bottomMargin = dp(16) })
        return wrap
    }

    protected fun sectionTitle(parent: ViewGroup, text: String) {
        parent.addView(TextView(context).apply {
            this.text = text
            textSize = 16f
            setTextColor(Candy.INK)
            typeface = candyTypeface(context)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(10); topMargin = dp(6) })
    }

    /** 设置行：标签 + 选中 ✓ */
    protected fun optionRow(parent: ViewGroup, label: String, selected: Boolean, onClick: () -> Unit) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpf(18f)
                if (selected) {
                    setColor(0x2EF59E0B.toInt())
                    setStroke(dp(2), Candy.AMBER)
                } else {
                    setColor(0x140B3D2E.toInt())
                    setStroke(dp(1), 0x330B3D2E.toInt())
                }
            }
            setPadding(dp(18), dp(12), dp(18), dp(12))
            addView(TextView(context).apply {
                this.text = label
                textSize = 15f
                setTextColor(Candy.INK)
                typeface = candyTypeface(context)
            })
            addView(TextView(context).apply {
                text = if (selected) "✓" else ""
                textSize = 16f
                setTextColor(Candy.AMBER_DARK)
                typeface = candyTypeface(context)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.END
            })
            setOnClickListener { onClick() }
        }
        parent.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(10) })
    }

}

class MenuPage(context: Context) : Page(context) {

    var onStart: (() -> Unit)? = null
    var onLeaderboard: (() -> Unit)? = null
    var onSettings: (() -> Unit)? = null
    var onHelp: (() -> Unit)? = null
    var onExit: (() -> Unit)? = null

    init {
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF0B3D2E.toInt(), 0xFF14532D.toInt()),
        )
        addBlobs(this, listOf(0x1AFFFFFF, 0x14FCD34D, 0x1AFFFFFF, 0x14FCD34D))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(col, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val emblem = loadUiBitmap("title_emblem.png")
        if (emblem != null) {
            val iv = ImageView(context)
            iv.setImageBitmap(emblem)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            col.addView(iv, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        } else {
            col.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.app_name)
            textSize = 46f
            setTextColor(Color.WHITE)
            typeface = candyTypeface(context)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setShadowLayer(dpf(6f), 0f, dpf(3f), 0x88000000.toInt())
        }
        col.addView(title, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(-40) })

        val tagline = TextView(context).apply {
            text = context.getString(R.string.tagline)
            textSize = 14f
            setTextColor(Candy.GOLD)
            typeface = candyTypeface(context)
            gravity = Gravity.CENTER
        }
        col.addView(tagline, LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(20); topMargin = dp(2) })

        val btnBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(42), 0, dp(42), dp(20))
        }
        col.addView(btnBox)
        candyButton(btnBox, context.getString(R.string.menu_start), Candy.AMBER, Candy.AMBER_DARK, Candy.INK) { onStart?.invoke() }
        candyButton(btnBox, context.getString(R.string.leaderboard), Candy.CREAM_CARD, 0xFFD9C9A3.toInt(), Candy.INK) { onLeaderboard?.invoke() }
        candyButton(btnBox, context.getString(R.string.settings), Candy.CREAM_CARD, 0xFFD9C9A3.toInt(), Candy.INK) { onSettings?.invoke() }
        candyButton(btnBox, context.getString(R.string.menu_help), Candy.CREAM_CARD, 0xFFD9C9A3.toInt(), Candy.INK) { onHelp?.invoke() }
        candyButton(btnBox, context.getString(R.string.exit_game), 0x40EF4444.toInt(), Candy.TOMATO, 0xFFFCA5A5.toInt()) { onExit?.invoke() }
    }
}

class SettingsPage(context: Context) : Page(context) {

    var onBack: (() -> Unit)? = null
    var onLanguageSelected: (String) -> Unit = {}
    var onBackgroundSelected: (BgMode) -> Unit = {}

    init {
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFFFF6E3.toInt(), 0xFFFDE9C8.toInt()),
        )
        addBlobs(this, listOf(0x2EF59E0B.toInt(), 0x1A0B3D2E.toInt(), 0x2EFCD34D.toInt(), 0x14101724))
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        root.addView(titleBar(context.getString(R.string.settings)) { onBack?.invoke() })
        val col = contentCard(root)

        sectionTitle(col, context.getString(R.string.entry_language))
        val current = AppCompatLocaleReader.current()
        listOf(
            context.getString(R.string.follow_system) to "",
            context.getString(R.string.english) to "en",
            context.getString(R.string.chinese) to "zh",
        ).forEach { (label, tag) ->
            optionRow(col, label, tag == current) { onLanguageSelected(tag) }
        }

        sectionTitle(col, context.getString(R.string.entry_background))
        val currentBg = Prefs.backgroundMode(context)
        listOf(
            context.getString(R.string.bg_auto) to "auto",
            context.getString(R.string.bg_day) to "day",
            context.getString(R.string.bg_sunset) to "sunset",
            context.getString(R.string.bg_night) to "night",
        ).forEach { (label, tag) ->
            optionRow(col, label, tag == currentBg) {
                Prefs.setBackgroundMode(context, tag)
                onBackgroundSelected(when (tag) {
                    "day" -> BgMode.DAY
                    "sunset" -> BgMode.SUNSET
                    "night" -> BgMode.NIGHT
                    else -> BgMode.AUTO
                })
            }
        }
    }
}

class HelpPage(context: Context) : Page(context) {

    var onBack: (() -> Unit)? = null

    init {
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFFFF6E3.toInt(), 0xFFFDE9C8.toInt()),
        )
        addBlobs(this, listOf(0x2EF59E0B.toInt(), 0x1A0B3D2E.toInt(), 0x2EFCD34D.toInt(), 0x14101724))
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        root.addView(titleBar(context.getString(R.string.help_title)) { onBack?.invoke() })
        val col = contentCard(root)

        fun row(sprite: String, text: String) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dpf(18f)
                    setColor(0x140B3D2E.toInt())
                }
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val bmp = loadUiBitmap(sprite)
            if (bmp != null) {
                val iv = ImageView(context)
                iv.setImageBitmap(bmp)
                iv.setPadding(0, 0, dp(14), 0)
                rowLayout.addView(iv, LinearLayout.LayoutParams(dp(40), dp(40)))
            }
            rowLayout.addView(TextView(context).apply {
                this.text = text
                textSize = 14f
                setTextColor(Candy.INK)
                typeface = candyTypeface(context)
            })
            col.addView(rowLayout, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(10) })
        }

        row("bean.png", context.getString(R.string.help_bean))
        row("meat.png", context.getString(R.string.help_meat))
        row("pie.png", context.getString(R.string.help_pie))
        row("car.png", context.getString(R.string.help_vehicle))
        row("bomb.png", context.getString(R.string.help_hazard))
        row("tiger.png", context.getString(R.string.help_beast))

        col.addView(TextView(context).apply {
            text = context.getString(R.string.help_controls)
            textSize = 15f
            setTextColor(Candy.AMBER_DARK)
            typeface = candyTypeface(context)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), dp(14), 0, 0)
        })
    }
}

class LeaderboardPage(context: Context) : Page(context) {

    var onBack: (() -> Unit)? = null

    private val listCol: LinearLayout
    private val emptyText: TextView

    init {
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFFFF6E3.toInt(), 0xFFFDE9C8.toInt()),
        )
        addBlobs(this, listOf(0x2EF59E0B.toInt(), 0x1A0B3D2E.toInt(), 0x2EFCD34D.toInt(), 0x14101724))
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        root.addView(titleBar(context.getString(R.string.leaderboard)) { onBack?.invoke() })
        listCol = contentCard(root)
        emptyText = TextView(context).apply {
            text = context.getString(R.string.no_records)
            textSize = 14f
            setTextColor(Candy.MUTED_DARK)
            typeface = candyTypeface(context)
            gravity = Gravity.CENTER
            setPadding(0, dp(30), 0, dp(30))
        }
        listCol.addView(emptyText)
    }

    fun render(records: List<ScoreRecord>, userName: String?, dateFormat: DateFormat) {
        listCol.removeAllViews()
        if (records.isEmpty()) {
            listCol.addView(emptyText)
            return
        }
        val bestId = records.filter { it.userName == userName }.maxByOrNull { it.score }?.id
        records.forEachIndexed { index, record ->
            val date = dateFormat.format(Date(record.createdAt))
            val mine = record.id == bestId
            val rowText = if (mine) {
                context.getString(R.string.best_marker_fmt, index + 1, record.userName, record.score, date)
            } else {
                context.getString(R.string.rank_entry_fmt, index + 1, record.userName, record.score, date)
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                background = GradientDrawable().apply {
                    cornerRadius = dpf(16f)
                    setColor(if (mine) 0x2EF59E0B.toInt() else 0x140B3D2E.toInt())
                    setStroke(if (mine) dp(2) else dp(1), if (mine) Candy.AMBER else 0x330B3D2E.toInt())
                }
                setPadding(dp(14), dp(11), dp(14), dp(11))
                addView(TextView(context).apply {
                    this.text = rowText
                    textSize = 14f
                    setTextColor(if (mine) Candy.AMBER_DARK else Candy.INK)
                    typeface = candyTypeface(context)
                })
            }
            listCol.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) })
        }
    }
}

/** ============ 游戏内浮层 ============ */

class GameOverPanel(context: Context) : OverlayPanel(context) {

    var onRestart: (() -> Unit)? = null
    var onLeaderboard: (() -> Unit)? = null
    var onMenu: (() -> Unit)? = null

    private val scoreText = styledLabel(20f)
    private val bestText = styledLabel(18f)

    init {
        val col = cardColumn()
        val title = styledLabel(30f, bold = true)
        title.setText(R.string.game_over)
        col.addView(title, colParams())
        col.addView(scoreText, colParams())
        col.addView(bestText, colParams())
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(pillButton(R.string.leaderboard) { onLeaderboard?.invoke() })
        row.addView(pillButton(R.string.restart, PillStyle.PRIMARY) { onRestart?.invoke() })
        col.addView(row, colParams())
        col.addView(styledButton(R.string.back_to_menu) { onMenu?.invoke() }, colParams())
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

    init {
        val col = cardColumn()
        val title = styledLabel(28f, bold = true)
        title.setText(R.string.paused)
        col.addView(title, colParams().apply { bottomMargin = dp(20) })
        col.addView(pillButton(R.string.resume, PillStyle.PRIMARY) { onResume?.invoke() }, wideParams())
        col.addView(pillButton(R.string.settings) { onSettings?.invoke() }, wideParams())
        col.addView(pillButton(R.string.menu_help) { onHelp?.invoke() })
    }
}

class TutorialPanel(context: Context) : OverlayPanel(context) {

    var onDismiss: (() -> Unit)? = null

    init {
        val col = cardColumn()
        val drag = styledLabel(24f, bold = true)
        drag.text = context.getString(R.string.tutorial_drag)
        col.addView(drag, colParams().apply { bottomMargin = dp(24) })
        val jump = styledLabel(24f, bold = true)
        jump.text = context.getString(R.string.tutorial_jump)
        col.addView(jump, colParams().apply { bottomMargin = dp(40) })
        val hint = styledLabel(15f, color = Candy.AMBER_DARK)
        hint.text = context.getString(R.string.tutorial_hint)
        col.addView(hint, colParams())
        setOnClickListener {
            onDismiss?.invoke()
            hide()
        }
    }
}
