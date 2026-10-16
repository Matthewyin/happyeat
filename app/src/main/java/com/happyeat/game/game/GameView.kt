package com.happyeat.game.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.happyeat.game.config.GameConfig

enum class BgMode { AUTO, DAY, SUNSET, NIGHT }

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    interface HudListener {
        fun onHudChanged(score: Int, lives: Int, pointsToNextLife: Int)
        fun onVehicleChanged(riding: Boolean, hitsUsed: Int, secondsLeft: Int)
        fun onGameOver(score: Int)
    }

    var hudListener: HudListener? = null

    @Volatile var bgMode = BgMode.AUTO
    @Volatile var inMenu = true

    private val mainHandler = Handler(Looper.getMainLooper())
    private val world = GameWorld(object : GameWorld.Listener {
        override fun onHudChanged(score: Int, lives: Int, pointsToNextLife: Int) {
            mainHandler.post { hudListener?.onHudChanged(score, lives, pointsToNextLife) }
        }

        override fun onVehicleChanged(riding: Boolean, hitsUsed: Int, secondsLeft: Int) {
            mainHandler.post { hudListener?.onVehicleChanged(riding, hitsUsed, secondsLeft) }
        }

        override fun onGameOver(score: Int) {
            mainHandler.post { hudListener?.onGameOver(score) }
        }
    })

    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var pausedByUi = false

    private var backgroundDay: Bitmap? = null
    private var backgroundSunset: Bitmap? = null
    private var backgroundNight: Bitmap? = null
    private var shownBg: Bitmap? = null
    private var prevBg: Bitmap? = null
    private var bgFadeStartMs = -1L
    private var playerBmp: Bitmap? = null
    private val itemBmps = HashMap<ItemKind, Bitmap?>()
    private val beastBmps = HashMap<BeastKind, Bitmap?>()

    private val bgPaint = Paint()
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dstRect = RectF()
    private var lastTouchX = 0f
    private var downX = 0f
    private var downY = 0f
    private var downAtMs = 0L

    init {
        holder.addCallback(this)
    }

    fun startGame() {
        synchronized(world) { world.reset() }
        inMenu = false
        resumeGame()
    }

    fun enterMenu() {
        inMenu = true
        synchronized(world) { world.reset() }
        pauseGame()
    }

    fun pauseGame() {
        pausedByUi = true
        SoundManager.stopBgm()
        stopLoop()
    }

    fun resumeGame() {
        pausedByUi = false
        if (holder.surface.isValid) {
            startLoop()
            if (!inMenu) SoundManager.startBgm(BgmTrack.GAME)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val frame = holder.surfaceFrame
        applySize(frame.width().toFloat(), frame.height().toFloat())
        if (!pausedByUi) startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        applySize(width.toFloat(), height.toFloat())
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopLoop()
    }

    override fun run() {
        val frameBudgetMs = 1000L / TARGET_FPS
        var lastNanos = System.nanoTime()
        while (running) {
            val canvas = try {
                holder.lockCanvas()
            } catch (e: Exception) {
                break
            } ?: continue
            val now = System.nanoTime()
            val dtMs = ((now - lastNanos) / 1_000_000L).coerceAtMost(50L)
            lastNanos = now
            synchronized(world) {
                if (!inMenu) world.update(dtMs)
                drawFrame(canvas)
            }
            try {
                holder.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                break
            }
            val spentMs = (System.nanoTime() - now) / 1_000_000L
            if (spentMs < frameBudgetMs) {
                try {
                    Thread.sleep(frameBudgetMs - spentMs)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                downX = event.x
                downY = event.y
                downAtMs = android.os.SystemClock.elapsedRealtime()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                lastTouchX = event.x
                synchronized(world) { world.dragPlayerBy(dx) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dt = android.os.SystemClock.elapsedRealtime() - downAtMs
                val slop = GameConfig.TAP_SLOP_DP * resources.displayMetrics.density
                val still = kotlin.math.abs(event.x - downX) < slop &&
                    kotlin.math.abs(event.y - downY) < slop
                if (dt < GameConfig.TAP_MAX_MS && still) {
                    synchronized(world) { world.jumpPlayer() }
                    SoundManager.play(SoundEvent.JUMP)
                }
            }
        }
        return true
    }

    private fun startLoop() {
        if (running) return
        running = true
        thread = Thread(this, "game-loop").also { it.start() }
    }

    private fun stopLoop() {
        running = false
        try {
            thread?.join(1000L)
        } catch (e: InterruptedException) {
        }
        thread = null
    }

    private fun applySize(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        synchronized(world) {
            world.onSize(w, h)
            prepareSprites(w.toInt(), h.toInt())
        }
    }

    private fun prepareSprites(w: Int, h: Int) {
        backgroundDay = loadAndScale(SPRITE_BACKGROUND, w, h)
        backgroundSunset = loadAndScale(SPRITE_BACKGROUND_SUNSET, w, h)
        backgroundNight = loadAndScale(SPRITE_BACKGROUND_NIGHT, w, h)
        shownBg = null
        prevBg = null
        bgFadeStartMs = -1L
        val itemSize = (GameConfig.ITEM_SIZE * w).toInt().coerceAtLeast(8)
        val playerSize = (GameConfig.PLAYER_SIZE * w).toInt().coerceAtLeast(8)
        val beastSize = (GameConfig.BEAST_SIZE * w).toInt().coerceAtLeast(8)
        playerBmp = loadAndScale(SPRITE_PLAYER, playerSize, playerSize)
        for (kind in ItemKind.entries) {
            itemBmps[kind] = loadAndScale(ITEM_SPRITES.getValue(kind), itemSize, itemSize)
        }
        for (kind in BeastKind.entries) {
            beastBmps[kind] = loadAndScale(BEAST_SPRITES.getValue(kind), beastSize, beastSize)
        }
    }

    private fun loadAndScale(name: String, w: Int, h: Int): Bitmap? = try {
        context.assets.open("$SPRITES_DIR/$name.png").use { input ->
            val raw = BitmapFactory.decodeStream(input) ?: return null
            if (raw.width == w && raw.height == h) raw
            else Bitmap.createScaledBitmap(raw, w, h, true)
        }
    } catch (e: Exception) {
        null
    }

    private fun currentBgBitmap(): Bitmap? = when (bgMode) {
        BgMode.DAY -> backgroundDay
        BgMode.SUNSET -> backgroundSunset
        BgMode.NIGHT -> backgroundNight
        BgMode.AUTO -> {
            val phase = (world.gameTimeMs / GameConfig.BG_PHASE_MS).toInt() % 3
            when (phase) {
                0 -> backgroundDay
                1 -> backgroundSunset
                else -> backgroundNight
            }
        }
    }

    private fun drawFrame(canvas: Canvas) {
        val w = world.viewW
        val h = world.viewH
        val bg = currentBgBitmap()
        if (bg != null) {
            if (bg !== shownBg) {
                prevBg = shownBg
                shownBg = bg
                bgFadeStartMs = if (prevBg != null) android.os.SystemClock.elapsedRealtime() else -1L
            }
            dstRect.set(0f, 0f, w, h)
            canvas.drawBitmap(bg, null, dstRect, bgPaint)
            val prev = prevBg
            if (prev != null && bgFadeStartMs > 0) {
                val fade = (android.os.SystemClock.elapsedRealtime() - bgFadeStartMs) / 900f
                if (fade >= 1f) {
                    prevBg = null
                    bgFadeStartMs = -1L
                } else {
                    bgPaint.alpha = ((1f - fade) * 255).toInt()
                    canvas.drawBitmap(prev, null, dstRect, bgPaint)
                    bgPaint.alpha = 255
                }
            }
        } else {
            canvas.drawColor(Color.rgb(122, 182, 240))
            fallbackPaint.color = Color.rgb(96, 158, 72)
            canvas.drawRect(0f, world.groundY, w, h, fallbackPaint)
        }
        for (item in world.items) {
            ringPaint.color = RING_COLORS.getValue(item.kind)
            ringPaint.strokeWidth = item.sizePx * 0.09f
            canvas.drawCircle(item.x, item.y, item.sizePx * 0.62f, ringPaint)
            drawSprite(canvas, itemBmps[item.kind], item.x, item.y, item.sizePx, FALLBACK_COLORS.getValue(item.kind))
        }
        for (beast in world.beasts) {
            drawBeast(canvas, beast)
        }
        drawPlayer(canvas)
    }

    private fun drawSprite(canvas: Canvas, bmp: Bitmap?, cx: Float, cy: Float, size: Float, fallbackColor: Int) {
        if (bmp != null) {
            canvas.drawBitmap(bmp, cx - bmp.width / 2f, cy - bmp.height / 2f, null)
        } else {
            fallbackPaint.color = fallbackColor
            canvas.drawCircle(cx, cy, size / 2f, fallbackPaint)
        }
    }

    private fun drawBeast(canvas: Canvas, beast: Beast) {
        val cy = world.groundY - beast.sizePx / 2f - beast.heightOffset(world.viewH)
        val bmp = beastBmps[beast.kind]
        if (bmp != null) {
            val left = beast.x - bmp.width / 2f
            val top = cy - bmp.height / 2f
            if (beast.direction < 0) {
                canvas.save()
                canvas.scale(-1f, 1f, beast.x, cy)
                canvas.drawBitmap(bmp, left, top, null)
                canvas.restore()
            } else {
                canvas.drawBitmap(bmp, left, top, null)
            }
        } else {
            fallbackPaint.color = Color.rgb(198, 124, 78)
            canvas.drawCircle(beast.x, cy, beast.sizePx / 2f, fallbackPaint)
        }
    }

    private fun drawPlayer(canvas: Canvas) {
        val p = world.player
        val flickerHidden = !p.riding && p.isInvincible(world.gameTimeMs) &&
            (world.gameTimeMs / FLICKER_PERIOD_MS) % 2L == 0L
        if (flickerHidden) return
        val cy = world.groundY - p.sizePx / 2f - p.heightOffset(world.gameTimeMs, world.viewH)
        if (p.riding) {
            val vehicleBmp = if (p.vehicleKind == ItemKind.TANK) itemBmps[ItemKind.TANK] else itemBmps[ItemKind.CAR]
            drawSprite(canvas, vehicleBmp, p.x, cy + p.sizePx * 0.15f, p.sizePx * 1.3f, Color.rgb(92, 98, 112))
            drawSprite(canvas, playerBmp, p.x, cy - p.sizePx * 0.2f, p.sizePx * 0.8f, Color.rgb(232, 181, 124))
        } else {
            drawSprite(canvas, playerBmp, p.x, cy, p.sizePx, Color.rgb(232, 181, 124))
        }
    }

    companion object {
        private const val TARGET_FPS = 60
        private const val FLICKER_PERIOD_MS = 120L
        private const val SPRITES_DIR = "sprites"
        private const val SPRITE_PLAYER = "player"
        private const val SPRITE_BACKGROUND = "background"
        private const val SPRITE_BACKGROUND_SUNSET = "background_sunset"
        private const val SPRITE_BACKGROUND_NIGHT = "background_night"
        private val ITEM_SPRITES = mapOf(
            ItemKind.BEAN to "bean",
            ItemKind.MEAT to "meat",
            ItemKind.PIE to "pie",
            ItemKind.ROCK to "rock",
            ItemKind.BOMB to "bomb",
            ItemKind.KNIFE to "knife",
            ItemKind.CAR to "car",
            ItemKind.TANK to "tank",
        )
        private val BEAST_SPRITES = mapOf(
            BeastKind.TIGER to "tiger",
            BeastKind.LION to "lion",
            BeastKind.WOLF to "wolf",
        )
        private val RING_COLORS = mapOf(
            ItemKind.BEAN to Color.rgb(76, 175, 80),
            ItemKind.MEAT to Color.rgb(76, 175, 80),
            ItemKind.PIE to Color.rgb(76, 175, 80),
            ItemKind.ROCK to Color.rgb(244, 67, 54),
            ItemKind.BOMB to Color.rgb(244, 67, 54),
            ItemKind.KNIFE to Color.rgb(244, 67, 54),
            ItemKind.CAR to Color.rgb(255, 193, 7),
            ItemKind.TANK to Color.rgb(255, 193, 7),
        )
        private val FALLBACK_COLORS = mapOf(
            ItemKind.BEAN to Color.rgb(141, 189, 93),
            ItemKind.MEAT to Color.rgb(196, 86, 62),
            ItemKind.PIE to Color.rgb(233, 196, 106),
            ItemKind.ROCK to Color.rgb(128, 128, 128),
            ItemKind.BOMB to Color.rgb(40, 40, 46),
            ItemKind.KNIFE to Color.rgb(192, 197, 206),
            ItemKind.CAR to Color.rgb(64, 123, 197),
            ItemKind.TANK to Color.rgb(85, 107, 47),
        )
    }
}
