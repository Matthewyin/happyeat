package com.happyeat.game.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.view.Gravity
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.happyeat.game.R
import com.happyeat.game.data.AppDatabase
import com.happyeat.game.data.Prefs
import com.happyeat.game.game.BgMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val CARD_COLOR = 0xF0101724.toInt()
private const val MUTED = 0xFF94A3B8.toInt()
private const val ACCENT = 0xFFFCD34D.toInt()

private fun dp(activity: AppCompatActivity, v: Int): Int =
    (v * activity.resources.displayMetrics.density).toInt()

private fun dpf(activity: AppCompatActivity, v: Float): Float =
    v * activity.resources.displayMetrics.density

/** 深色圆角卡片弹窗骨架，返回 dialog 和内容列。 */
private fun cardDialog(activity: AppCompatActivity, title: String): Pair<Dialog, LinearLayout> {
    val dialog = Dialog(activity)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

    val outer = FrameLayout(activity).apply {
        setPadding(dp(activity, 28), dp(activity, 16), dp(activity, 28), dp(activity, 16))
    }
    val card = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dpf(activity, 30f)
            setColor(CARD_COLOR)
        }
        setPadding(dp(activity, 30), dp(activity, 26), dp(activity, 30), dp(activity, 24))
    }
    outer.addView(card, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
    dialog.setContentView(outer)

    val titleView = TextView(activity).apply {
        text = title
        textSize = 19f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
    }
    card.addView(titleView, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(activity, 16) })

    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.window?.setLayout(
        activity.resources.displayMetrics.widthPixels - dp(activity, 8),
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )
    return dialog to card
}

private fun rowParams(activity: AppCompatActivity, h: Int): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(activity, h) }

private fun cardText(activity: AppCompatActivity, text: String, size: Float = 15f, color: Int = Color.WHITE): TextView =
    TextView(activity).apply {
        this.text = text
        textSize = size
        setTextColor(color)
    }

/** 菜单行：圆角底，可选中态。 */
private fun menuRow(
    activity: AppCompatActivity,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
): LinearLayout {
    val row = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dpf(activity, 18f)
            if (selected) {
                setColor(0x2EF59E0B)
                setStroke(dp(activity, 1), 0xFFF59E0B.toInt())
            } else {
                setColor(0x14FFFFFF)
            }
        }
        setPadding(dp(activity, 18), dp(activity, 13), dp(activity, 18), dp(activity, 13))
        addView(cardText(activity, label, 15f))
        val check = cardText(activity, if (selected) "✓" else "", 15f, 0xFFFCD34D.toInt())
        check.gravity = Gravity.END
        addView(check, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
        ).apply { gravity = Gravity.END })
        setOnClickListener { onClick() }
    }
    return row
}

fun showExitConfirmDialog(activity: AppCompatActivity) {
    val (dialog, card) = cardDialog(activity, activity.getString(R.string.exit_confirm))
    card.addView(cardText(activity, activity.getString(R.string.exit_message), 15f, MUTED))
    val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    fun pill(text: String, danger: Boolean, onClick: () -> Unit) {
        val btn = TextView(activity).apply {
            this.text = text
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dpf(activity, 22f)
                if (danger) {
                    setColor(0x40EF4444)
                    setStroke(dp(activity, 1), 0xFFEF4444.toInt())
                } else {
                    setColor(0x2EFFFFFF)
                }
            }
            setPadding(0, dp(activity, 12), 0, dp(activity, 12))
            setOnClickListener { onClick() }
        }
        row.addView(btn, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
        ).apply { marginEnd = dp(activity, if (danger) 12 else 0) })
    }
    pill(activity.getString(R.string.cancel), false) { dialog.dismiss() }
    pill(activity.getString(R.string.exit_game), true) {
        dialog.dismiss()
        activity.finishAffinity()
    }
    card.addView(row, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(activity, 20) })
    dialog.show()
}

fun showNameDialog(activity: AppCompatActivity, onSaved: () -> Unit) {
    val (dialog, card) = cardDialog(activity, activity.getString(R.string.welcome_title))
    card.addView(cardText(activity, activity.getString(R.string.welcome_prompt), 14f, MUTED))
    val input = EditText(activity).apply {
        hint = activity.getString(R.string.username)
        filters = arrayOf(InputFilter.LengthFilter(20))
        background = GradientDrawable().apply {
            cornerRadius = dpf(activity, 14f)
            setColor(0x14FFFFFF)
            setStroke(dp(activity, 1), 0x3FFFFFFF.toInt())
        }
        setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 12))
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF94A3B8.toInt())
    }
    card.addView(input, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(activity, 12); bottomMargin = dp(activity, 18) })
    val ok = TextView(activity).apply {
        text = activity.getString(R.string.ok)
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(ACCENT)
        gravity = Gravity.END
        setPadding(0, dp(activity, 4), dp(activity, 4), 0)
    }
    ok.setOnClickListener {
        val name = input.text.toString().trim()
        if (name.length in 2..20) {
            Prefs.setUserName(activity, name)
            dialog.dismiss()
            onSaved()
        } else {
            input.error = activity.getString(R.string.name_too_short)
        }
    }
    card.addView(ok)
    dialog.show()
}

fun showLanguageDialog(activity: AppCompatActivity) {
    val (dialog, card) = cardDialog(activity, activity.getString(R.string.language))
    val options = listOf(
        activity.getString(R.string.follow_system) to "",
        activity.getString(R.string.english) to "en",
        activity.getString(R.string.chinese) to "zh",
    )
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val checkedTag = when {
        current.isEmpty() -> ""
        current.startsWith("zh") -> "zh"
        else -> "en"
    }
    options.forEach { (label, tag) ->
        card.addView(menuRow(activity, label, tag == checkedTag) {
            val locales = when (tag) {
                "" -> LocaleListCompat.getEmptyLocaleList()
                "en" -> LocaleListCompat.forLanguageTags("en")
                else -> LocaleListCompat.forLanguageTags("zh")
            }
            dialog.dismiss()
            AppCompatDelegate.setApplicationLocales(locales)
        }, rowParams(activity, 10))
    }
    dialog.show()
}

fun showBackgroundDialog(activity: AppCompatActivity, onSelected: (BgMode) -> Unit) {
    val (dialog, card) = cardDialog(activity, activity.getString(R.string.entry_background))
    val current = Prefs.backgroundMode(activity)
    listOf(
        activity.getString(R.string.bg_auto) to "auto",
        activity.getString(R.string.bg_day) to "day",
        activity.getString(R.string.bg_sunset) to "sunset",
        activity.getString(R.string.bg_night) to "night",
    ).forEach { (label, tag) ->
        card.addView(menuRow(activity, label, tag == current) {
            Prefs.setBackgroundMode(activity, tag)
            onSelected(when (tag) {
                "day" -> BgMode.DAY
                "sunset" -> BgMode.SUNSET
                "night" -> BgMode.NIGHT
                else -> BgMode.AUTO
            })
            dialog.dismiss()
        }, rowParams(activity, 10))
    }
    dialog.show()
}

fun showSettingsDialog(activity: AppCompatActivity, onBackgroundChanged: (BgMode) -> Unit) {
    val (dialog, card) = cardDialog(activity, activity.getString(R.string.settings))
    card.addView(menuRow(activity, activity.getString(R.string.entry_language)) {
        dialog.dismiss()
        showLanguageDialog(activity)
    }, rowParams(activity, 10))
    card.addView(menuRow(activity, activity.getString(R.string.entry_background)) {
        dialog.dismiss()
        showBackgroundDialog(activity, onBackgroundChanged)
    }, rowParams(activity, 10))
    dialog.show()
}

fun showHelpDialog(activity: AppCompatActivity) {
    val (dialog, card) = cardDialog(activity, activity.getString(R.string.help_title))
    val scroll = android.widget.ScrollView(activity).apply { isVerticalScrollBarEnabled = false }
    val col = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
    }

    fun row(sprite: String, text: String) {
        val rowLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dpf(activity, 16f)
                setColor(0x14FFFFFF)
            }
            setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10))
        }
        val bmp = try {
            activity.assets.open("sprites/$sprite").use { android.graphics.BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
        if (bmp != null) {
            val iv = android.widget.ImageView(activity)
            iv.setImageBitmap(bmp)
            iv.setPadding(0, 0, dp(activity, 14), 0)
            rowLayout.addView(iv, LinearLayout.LayoutParams(dp(activity, 40), dp(activity, 40)))
        }
        rowLayout.addView(cardText(activity, text, 14f))
        col.addView(rowLayout, rowParams(activity, 8))
    }

    row("bean.png", activity.getString(R.string.help_bean))
    row("meat.png", activity.getString(R.string.help_meat))
    row("pie.png", activity.getString(R.string.help_pie))
    row("car.png", activity.getString(R.string.help_vehicle))
    row("bomb.png", activity.getString(R.string.help_hazard))
    row("tiger.png", activity.getString(R.string.help_beast))

    val controls = cardText(activity, activity.getString(R.string.help_controls), 14f, 0xFFFCD34D.toInt())
    controls.setPadding(dp(activity, 4), dp(activity, 14), 0, 0)
    col.addView(controls)
    scroll.addView(col)
    card.addView(scroll, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { bottomMargin = dp(activity, 18) })

    val ok = TextView(activity).apply {
        text = activity.getString(R.string.ok)
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFFFCD34D.toInt())
        gravity = Gravity.END
    }
    ok.setOnClickListener { dialog.dismiss() }
    card.addView(ok)
    dialog.show()
}

fun showLeaderboardDialog(activity: AppCompatActivity, scope: CoroutineScope) {
    scope.launch {
        val records = AppDatabase.get(activity).scoreDao().topScores(LEADERBOARD_SIZE)
        val userName = Prefs.userName(activity)
        val bestId = records.filter { it.userName == userName }.maxByOrNull { it.score }?.id
        val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT)

        val (dialog, card) = cardDialog(activity, activity.getString(R.string.leaderboard))
        if (records.isEmpty()) {
            card.addView(cardText(activity, activity.getString(R.string.no_records), 14f, MUTED))
        } else {
            val scroll = android.widget.ScrollView(activity).apply { isVerticalScrollBarEnabled = false }
            val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            records.forEachIndexed { index, record ->
                val date = dateFormat.format(Date(record.createdAt))
                val mine = record.id == bestId
                val rowText = if (mine) {
                    activity.getString(R.string.best_marker_fmt, index + 1, record.userName, record.score, date)
                } else {
                    activity.getString(R.string.rank_entry_fmt, index + 1, record.userName, record.score, date)
                }
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    background = GradientDrawable().apply {
                        cornerRadius = dpf(activity, 14f)
                        setColor(if (mine) 0x2EF59E0B else 0x14FFFFFF)
                    }
                    setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10))
                    addView(cardText(activity, rowText, 14f, if (mine) ACCENT else Color.WHITE))
                }
                list.addView(row, rowParams(activity, 8))
            }
            scroll.addView(list)
            card.addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(activity, 18) })
        }
        val close = TextView(activity).apply {
            text = activity.getString(R.string.ok)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFFCD34D.toInt())
            gravity = Gravity.END
        }
        close.setOnClickListener { dialog.dismiss() }
        card.addView(close)
        dialog.show()
    }
}

private const val LEADERBOARD_SIZE = 50
