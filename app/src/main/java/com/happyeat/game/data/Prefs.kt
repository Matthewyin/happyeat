package com.happyeat.game.data

import android.content.Context

object Prefs {
    private const val FILE = "happyeat_prefs"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_BEST_SCORE = "best_score"
    private const val KEY_BACKGROUND = "background_mode"
    private const val KEY_TUTORIAL_SHOWN = "tutorial_shown"

    fun userName(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_USER_NAME, null)

    fun setUserName(context: Context, name: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_USER_NAME, name).apply()
    }

    fun bestScore(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_BEST_SCORE, 0)

    fun setBestScore(context: Context, score: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putInt(KEY_BEST_SCORE, score).apply()
    }

    fun backgroundMode(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_BACKGROUND, "auto") ?: "auto"

    fun setBackgroundMode(context: Context, mode: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_BACKGROUND, mode).apply()
    }

    fun jumpOnLeft(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("jump_on_left", false)

    fun setJumpOnLeft(context: Context, left: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("jump_on_left", left).apply()
    }

    fun tutorialShown(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_TUTORIAL_SHOWN, false)

    fun setTutorialShown(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_TUTORIAL_SHOWN, true).apply()
    }
}
