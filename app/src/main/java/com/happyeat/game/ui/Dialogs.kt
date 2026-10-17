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

private const val MUTED = Candy.MUTED_DARK
private const val ACCENT = Candy.BLUE_DARK

private fun dp(activity: AppCompatActivity, v: Int): Int = uiDp(activity, v)
private fun dpf(activity: AppCompatActivity, v: Float): Float = v * activity.resources.displayMetrics.density

private fun cardDialog(activity: AppCompatActivity, title: String): Pair<Dialog, LinearLayout> {
    val dialog = Dialog(activity)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val scroll = android.widget.ScrollView(activity).apply {
        setPadding(dp(activity, Candy.GAP), dp(activity, Candy.GAP), dp(activity, Candy.GAP), dp(activity, Candy.GAP))
    }
    val card = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = uiShape(activity, Candy.CREAM_CARD)
        setPadding(dp(activity, Candy.PAD), dp(activity, Candy.PAD), dp(activity, Candy.PAD), dp(activity, Candy.PAD))
    }
    scroll.addView(card)
    dialog.setContentView(scroll)
    card.addView(cardText(activity, title, Candy.TITLE).apply { setTypeface(typeface, Typeface.BOLD) }, rowParams(activity, Candy.GAP))
    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
        android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    dialog.setOnShowListener {
        dialog.window?.setLayout(activity.resources.displayMetrics.widthPixels - dp(activity, Candy.DIALOG_WIDTH_MARGIN),
            LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    return dialog to card
}

private fun rowParams(activity: AppCompatActivity, h: Int) = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(activity, h) }

private fun cardText(activity: AppCompatActivity, text: String, size: Float = Candy.BODY, color: Int = Candy.INK) =
    TextView(activity).apply { this.text = text; textSize = size; setTextColor(color) }

private fun menuRow(activity: AppCompatActivity, label: String, selected: Boolean = false, onClick: () -> Unit): LinearLayout =
    LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = uiShape(activity, if (selected) Candy.MINT else Candy.CREAM_CARD)
        minimumHeight = dp(activity, Candy.TOUCH)
        setPadding(dp(activity, Candy.GAP), dp(activity, Candy.GAP), dp(activity, Candy.GAP), dp(activity, Candy.GAP))
        addView(cardText(activity, label), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (selected) addView(uiIcon(activity, "ui_check"), LinearLayout.LayoutParams(dp(activity, Candy.ICON), dp(activity, Candy.ICON)))
        setOnClickListener { onClick() }
    }

fun showExitConfirmDialog(activity: AppCompatActivity) {
    val (dialog, card) = cardDialog(activity, activity.getString(R.string.exit_confirm))
    card.addView(cardText(activity, activity.getString(R.string.exit_message)), rowParams(activity, Candy.PAD))
    card.addView(uiButton(activity, R.string.cancel, "ui_close") { dialog.dismiss() }, rowParams(activity, Candy.GAP))
    card.addView(uiButton(activity, R.string.exit_game, "ui_exit", primary = true) {
        dialog.dismiss(); activity.finishAffinity()
    }, rowParams(activity, Candy.GAP))
    dialog.show()
}

fun showNameDialog(activity: AppCompatActivity, editing: Boolean = false, onSaved: () -> Unit) {
    val (dialog, card) = cardDialog(activity, activity.getString(
        if (editing) R.string.edit_username else R.string.welcome_title))
    card.addView(cardText(activity, activity.getString(
        if (editing) R.string.edit_username_prompt else R.string.welcome_prompt)), rowParams(activity, Candy.GAP))
    val input = EditText(activity).apply {
        hint = activity.getString(R.string.username)
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        textSize = Candy.BODY
        filters = arrayOf(InputFilter.LengthFilter(20))
        background = uiShape(activity, Candy.CREAM_CARD)
        setPadding(dp(activity, Candy.GAP), dp(activity, Candy.GAP), dp(activity, Candy.GAP), dp(activity, Candy.GAP))
        setTextColor(Candy.INK); setHintTextColor(Candy.MUTED_DARK)
        setSingleLine(true)
        if (editing) {
            setText(Prefs.userName(activity).orEmpty())
            setSelection(text.length)
        }
    }
    card.addView(input, rowParams(activity, Candy.GAP))
    val errorText = cardText(activity, activity.getString(R.string.name_too_short), Candy.SMALL_TEXT, Candy.TOMATO).apply {
        visibility = android.view.View.GONE
        accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    card.addView(errorText, rowParams(activity, Candy.GAP))
    if (editing) card.addView(uiButton(activity, R.string.cancel, "ui_close") {
        dialog.dismiss()
    }, rowParams(activity, Candy.GAP))
    card.addView(uiButton(activity, if (editing) R.string.save else R.string.ok, "ui_check", primary = true) {
        val name = input.text.toString().trim()
        if (name.length in 2..20) {
            Prefs.setUserName(activity, name); dialog.dismiss(); onSaved()
        } else errorText.visibility = android.view.View.VISIBLE
    }, rowParams(activity, Candy.GAP))
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
