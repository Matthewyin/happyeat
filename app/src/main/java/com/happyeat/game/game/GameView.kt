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

class GameView internal constructor(context: Context, private val session: GameSession) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    interface HudListener {
        fun onHudChanged(score: Int, lives: Int, pointsToNextLife: Int)
        fun onVehicleChanged(riding: Boolean, hitsUsed: Int, secondsLeft: Int)
        fun onGameOver(score: Int)
    }

    var hudListener: HudListener? = null

    @Volatile var bgMode = BgMode.AUTO
        set(value) {
            synchronized(world) {
                field = value
                prevBg = null
                bgFadeStartMs = -1L
            }
            prepareBackground(fade = false)
            drawPausedFrame()
        }
    var inMenu: Boolean
        get() = session.inMenu
        private set(value) { session.inMenu = value }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val world = session.world
    private var eventVersion = 0L
    @Volatile private var released = false

    private fun postHud(action: (HudListener) -> Unit) {
        val version = eventVersion
        mainHandler.post {
            val valid = synchronized(world) { !released && version == eventVersion && world.listener === worldListener }
            if (valid) hudListener?.let(action)
        }
    }

    private val worldListener: GameWorld.Listener = object : GameWorld.Listener {
        override fun onHudChanged(score: Int, lives: Int, pointsToNextLife: Int) {
            postHud { it.onHudChanged(score, lives, pointsToNextLife) }
        }

        override fun onVehicleChanged(riding: Boolean, hitsUsed: Int, secondsLeft: Int) {
            postHud { it.onVehicleChanged(riding, hitsUsed, secondsLeft) }
        }

        override fun onGameOver(score: Int) {
            postHud { it.onGameOver(score) }
        }
    }

    @Volatile private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var pausedByUi = true

    private data class BackgroundKey(val name: String, val width: Int, val height: Int)
    private var backgroundKey: BackgroundKey? = null
    private var backgroundGeneration = 0L
    private var prevBackgroundName: String? = null
    private var backgroundLoads = 0
    private var backgroundDecoder: (String, Int, Int) -> Bitmap? = ::decodeBackground
    private var spriteWidth = 0
    private var spriteHeight = 0
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
        synchronized(world) { world.listener = worldListener }
        holder.addCallback(this)
    }

    fun startGame() {
        pauseGame()
        synchronized(world) {
            if (released) return
            eventVersion++
            inMenu = false
            world.reset()
        }
        resumeGame()
    }

    fun enterMenu() {
        pauseGame()
        synchronized(world) {
            if (released) return
            eventVersion++
            inMenu = true
            world.reset()
        }
    }

    fun pauseGame() {
        synchronized(world) { pausedByUi = true }
        SoundManager.stopBgm()
        stopLoop()
    }

    fun resumeGame() {
        synchronized(world) {
            if (released || inMenu || world.gameOver) return
            pausedByUi = false
        }
        if (holder.surface.isValid) {
            startLoop()
            if (!inMenu) SoundManager.startBgm(BgmTrack.GAME)
        }
    }

    fun refreshHud() {
        synchronized(world) { world.refreshHud() }
    }

    fun release() {
        synchronized(world) {
            released = true
            pausedByUi = true
            eventVersion++
            shownBg = null
            prevBg = null
            if (world.listener === worldListener) world.listener = null
        }
        stopLoop()
        mainHandler.removeCallbacksAndMessages(null)
        hudListener = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        synchronized(world) {
            backgroundGeneration++
            if (shownBg == null) backgroundKey = null
        }
        val frame = holder.surfaceFrame
        applySize(frame.width().toFloat(), frame.height().toFloat())
        if (!pausedByUi) startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        applySize(width.toFloat(), height.toFloat())
        drawPausedFrame()
    }

    private fun drawPausedFrame() {
        if (pausedByUi && !released && holder.surface.isValid) {
            val canvas = holder.lockCanvas() ?: return
            try {
                synchronized(world) { if (!released) drawFrame(canvas) }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopLoop()
    }

    override fun run() {
        val frameBudgetMs = 1000L / TARGET_FPS
        var lastNanos = System.nanoTime()
        while (running && thread === Thread.currentThread()) {
            prepareBackground(fade = true)
            val canvas = try {
                holder.lockCanvas()
            } catch (e: Exception) {
                break
            } ?: continue
            val now = System.nanoTime()
            val dtMs = ((now - lastNanos) / 1_000_000L).coerceAtMost(50L)
            lastNanos = now
            synchronized(world) {
                if (!released) {
                    if (!pausedByUi && thread === Thread.currentThread() && !inMenu) world.update(dtMs)
                    // 暂停可能发生在取得 Canvas 后，仍需提交静态场景，不能提交未绘制的空帧。
                    drawFrame(canvas)
                }
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
        if (released || pausedByUi || inMenu) return true
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
                synchronized(world) { if (!released && !pausedByUi) world.dragPlayerBy(dx) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dt = android.os.SystemClock.elapsedRealtime() - downAtMs
                val slop = GameConfig.TAP_SLOP_DP * resources.displayMetrics.density
                val still = kotlin.math.abs(event.x - downX) < slop &&
                    kotlin.math.abs(event.y - downY) < slop
                if (dt < GameConfig.TAP_MAX_MS && still) {
                    synchronized(world) { if (!released && !pausedByUi) world.jumpPlayer() }
                    SoundManager.play(SoundEvent.JUMP)
                }
            }
        }
        return true
    }

    private fun startLoop() {
        if (running || released || pausedByUi) return
        running = true
        thread = Thread(this, "game-loop")
        thread?.start()
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
            if (released) return
            world.onSize(w, h)
            if (spriteWidth != w.toInt() || spriteHeight != h.toInt()) {
                prepareSprites(w.toInt(), h.toInt())
                spriteWidth = w.toInt()
                spriteHeight = h.toInt()
            }
        }
        prepareBackground(fade = false)
    }

    private fun prepareSprites(w: Int, h: Int) {
        val itemSize = (GameConfig.ITEM_SIZE * w).toInt().coerceAtLeast(8)
        val playerSize = (GameConfig.PLAYER_SIZE * w).toInt().coerceAtLeast(8)
        val beastSize = (GameConfig.BEAST_SIZE * w).toInt().coerceAtLeast(8)
        playerBmp = loadAndScale(SPRITE_PLAYER, playerSize, playerSize)
        world.player.spriteSizePx = playerBmp?.width?.toFloat()
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

    private fun desiredBackground(): BackgroundKey = BackgroundKey(
        BackgroundSelection.resource(world.gameTimeMs, bgMode), world.viewW.toInt(), world.viewH.toInt())

    private fun prepareBackground(fade: Boolean) {
        val (key, generation) = synchronized(world) {
            if (released || world.viewW <= 0 || world.viewH <= 0) return
            val desired = desiredBackground()
            if (desired == backgroundKey) return
            desired to backgroundGeneration
        }
        // 解码不持有 world 锁或 Surface Canvas；安装前再次核对局面和尺寸。
        val decoded = backgroundDecoder(key.name, key.width, key.height)
        synchronized(world) {
            backgroundLoads++
            if (released || backgroundGeneration != generation || desiredBackground() != key) return
            prevBackgroundName = backgroundKey?.name
            prevBg = if (fade && decoded != null) shownBg else null
            shownBg = decoded
            backgroundKey = key
            bgFadeStartMs = if (prevBg != null) android.os.SystemClock.elapsedRealtime() else -1L
        }
    }

    private fun decodeBackground(name: String, width: Int, height: Int): Bitmap? = try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open("$SPRITES_DIR/$name.png").use { BitmapFactory.decodeStream(it, null, options) }
            options.inSampleSize = 1
            while (options.outWidth / (options.inSampleSize * 2) >= width &&
                options.outHeight / (options.inSampleSize * 2) >= height) options.inSampleSize *= 2
            options.inJustDecodeBounds = false
            context.assets.open("$SPRITES_DIR/$name.png").use { BitmapFactory.decodeStream(it, null, options) }
        } catch (_: java.io.IOException) { null }

    private fun drawBackground(canvas: Canvas, bitmap: Bitmap, resource: String) {
        val split = (bitmap.height * GameConfig.BG_GROUND_FRACTIONS.getValue(resource)).toInt()
        val source = android.graphics.Rect(0, 0, bitmap.width, split)
        dstRect.set(0f, 0f, world.viewW, world.groundY)
        canvas.drawBitmap(bitmap, source, dstRect, bgPaint)
        source.set(0, split, bitmap.width, bitmap.height)
        dstRect.set(0f, world.groundY, world.viewW, world.viewH)
        canvas.drawBitmap(bitmap, source, dstRect, bgPaint)
    }

    private fun drawFrame(canvas: Canvas) {
        val w = world.viewW
        val h = world.viewH
        val bg = shownBg
        if (bg != null) {
            drawBackground(canvas, bg, checkNotNull(backgroundKey).name)
            val prev = prevBg
            if (prev != null && bgFadeStartMs > 0) {
                val fade = (android.os.SystemClock.elapsedRealtime() - bgFadeStartMs) / GameConfig.BG_FADE_MS.toFloat()
                if (fade >= 1f) {
                    prevBg = null
                    bgFadeStartMs = -1L
                } else {
                    bgPaint.alpha = ((1f - fade) * 255).toInt()
                    drawBackground(canvas, prev, checkNotNull(prevBackgroundName))
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
            drawSprite(canvas, playerBmp, p.x, p.displayCenterY(world.groundY, world.gameTimeMs, world.viewH), p.displaySizePx, Color.rgb(232, 181, 124))
        } else {
            drawSprite(canvas, playerBmp, p.x, p.displayCenterY(world.groundY, world.gameTimeMs, world.viewH), p.displaySizePx, Color.rgb(232, 181, 124))
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
