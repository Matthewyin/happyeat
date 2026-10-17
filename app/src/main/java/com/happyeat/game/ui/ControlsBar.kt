package com.happyeat.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.happyeat.game.R
import com.happyeat.game.config.GameConfig

class ControlsBar(context: Context, move: (Float) -> Unit, jump: () -> Unit) : LinearLayout(context) {
    private val moveArea = Area(R.string.controls_move, "ui_move", move, null)
    private val jumpArea = Area(R.string.controls_jump, "ui_tap", null, jump)

    init {
        orientation = HORIZONTAL
        isMotionEventSplittingEnabled = true
        setPadding(dp(Candy.SMALL), dp(Candy.SMALL), dp(Candy.SMALL), dp(Candy.SMALL))
        setBackgroundColor(Candy.CREAM_CARD)
        setJumpOnLeft(false)
    }

    fun setJumpOnLeft(left: Boolean) {
        clearInput()
        removeAllViews()
        val moveParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        val jumpParams = LayoutParams(dp(GameConfig.Ui.JUMP_AREA_WIDTH), LayoutParams.MATCH_PARENT)
        if (left) {
            addView(jumpArea, jumpParams)
            addView(moveArea, moveParams)
        } else {
            addView(moveArea, moveParams)
            addView(jumpArea, jumpParams)
        }
    }

    fun clearInput() {
        moveArea.clearInput()
        jumpArea.clearInput()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) clearInput()
        moveArea.isEnabled = enabled
        jumpArea.isEnabled = enabled
    }

    override fun onDetachedFromWindow() {
        clearInput()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private inner class Area(label: Int, icon: String, val move: ((Float) -> Unit)?, val jump: (() -> Unit)?) : LinearLayout(context) {
        private var pointerId = MotionEvent.INVALID_POINTER_ID
        private var lastX = 0f

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(label)
            setWillNotDraw(false)
            addView(uiIcon(context, icon), LayoutParams(dp(Candy.ICON), dp(Candy.ICON)))
            addView(TextView(context).apply {
                setText(label)
                setTextColor(Candy.INK)
                textSize = Candy.SMALL_TEXT
                gravity = Gravity.CENTER
            })
        }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val face = RectF()

        override fun drawableStateChanged() {
            super.drawableStateChanged()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val density = resources.displayMetrics.density
            val inset = GameConfig.Ui.CONTROL_INSET * density
            val depth = GameConfig.Ui.CONTROL_DEPTH * density
            val radius = GameConfig.Ui.RADIUS * density
            face.set(inset, inset + depth, width - inset, height - inset)
            paint.style = Paint.Style.FILL
            paint.color = if (jump != null) GameConfig.Ui.CONTROL_GOLD_SHADOW else GameConfig.Ui.CONTROL_SHADOW
            canvas.drawRoundRect(face, radius, radius, paint)
            face.top = inset + if (isPressed && isEnabled) depth else 0f
            face.bottom = height - inset - if (isPressed && isEnabled) 0f else depth
            paint.color = when {
                !isEnabled -> GameConfig.Ui.CONTROL_DISABLED
                jump != null -> if (isPressed) GameConfig.Ui.CONTROL_PRESSED_GOLD else Candy.GOLD
                else -> if (isPressed) GameConfig.Ui.CONTROL_PRESSED_MINT else Candy.MINT
            }
            canvas.drawRoundRect(face, radius, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = GameConfig.Ui.STROKE * density
            paint.color = Candy.INK
            canvas.drawRoundRect(face, radius, radius, paint)
            if (isEnabled && !isPressed) {
                val highlightInset = GameConfig.Ui.CONTROL_HIGHLIGHT_INSET * density
                paint.strokeWidth = GameConfig.Ui.CONTROL_HIGHLIGHT * density
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = Candy.CREAM_CARD
                canvas.drawLine(face.left + highlightInset, face.top + inset,
                    face.right - highlightInset, face.top + inset, paint)
            }
            super.onDraw(canvas)
        }

        fun clearInput() {
            pointerId = MotionEvent.INVALID_POINTER_ID
            lastX = 0f
            isPressed = false
            invalidate()
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled) return true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    if (pointerId != MotionEvent.INVALID_POINTER_ID) return true
                    clearInput()
                    pointerId = event.getPointerId(event.actionIndex)
                    lastX = event.getX(event.actionIndex)
                    isPressed = true
                    invalidate()
                    jump?.invoke()
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = event.findPointerIndex(pointerId)
                    if (index < 0) clearInput() else {
                        val x = event.getX(index)
                        move?.invoke(x - lastX)
                        lastX = x
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.getPointerId(event.actionIndex) == pointerId) clearInput()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> clearInput()
            }
            return true
        }

        // 触摸只在按下时跳跃，无障碍点击仍可执行同一个动作。
        override fun performClick(): Boolean {
            super.performClick()
            if (isEnabled) jump?.invoke()
            return true
        }
    }
}
