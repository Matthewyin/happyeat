package com.happyeat.game.game

import android.graphics.RectF
import com.happyeat.game.config.GameConfig
import kotlin.random.Random

class GameWorld(private val listener: Listener) {

    interface Listener {
        fun onHudChanged(score: Int, lives: Int, pointsToNextLife: Int)
        fun onVehicleChanged(riding: Boolean, hitsUsed: Int, secondsLeft: Int)
        fun onGameOver(score: Int)
    }

    var viewW = 0f
        private set
    var viewH = 0f
        private set
    var groundY = 0f
        private set

    val player = Player()
    val items = ArrayList<FallingItem>()
    val beasts = ArrayList<Beast>()

    var score = 0
        private set
    var lives = GameConfig.START_LIVES
        private set
    var gameOver = false
        private set
    var gameTimeMs = 0L
        private set

    private var nextExtraLifeAt = GameConfig.POINTS_PER_EXTRA_LIFE
    private var fallTimerMs = 0L
    private var beastTimerMs = 0L
    private val rand = Random.Default

    val difficulty: Float
        get() = 1f + (GameConfig.DIFFICULTY_MAX_MULTIPLIER - 1f) *
            (gameTimeMs.toFloat() / GameConfig.DIFFICULTY_RAMP_MS).coerceAtMost(1f)

    fun onSize(w: Float, h: Float) {
        viewW = w
        viewH = h
        groundY = h - GameConfig.BEAST_SIZE * w
        player.sizePx = GameConfig.PLAYER_SIZE * w
        if (player.x == 0f) player.x = w / 2f
        player.x = player.x.coerceIn(player.sizePx / 2f, w - player.sizePx / 2f)
    }

    fun reset() {
        items.clear()
        beasts.clear()
        score = 0
        lives = GameConfig.START_LIVES
        gameOver = false
        gameTimeMs = 0L
        nextExtraLifeAt = GameConfig.POINTS_PER_EXTRA_LIFE
        fallTimerMs = 0L
        beastTimerMs = 0L
        player.riding = false
        player.vehicleKind = null
        player.vehicleHits = 0
        player.vehicleExpiresAtMs = 0L
        player.invincibleUntilMs = 0L
        player.resetState()
        if (viewW > 0f) player.x = viewW / 2f
        listener.onHudChanged(score, lives, nextExtraLifeAt)
        listener.onVehicleChanged(false, 0, 0)
    }

    fun dragPlayerBy(dx: Float) {
        if (viewW == 0f) return
        player.x = (player.x + dx).coerceIn(player.sizePx / 2f, viewW - player.sizePx / 2f)
    }

    fun jumpPlayer() {
        if (gameOver || viewW == 0f) return
        player.jump(gameTimeMs)
    }

    fun update(dtMs: Long) {
        if (gameOver || viewW == 0f) return
        gameTimeMs += dtMs
        val dt = dtMs / 1000f
        val diff = difficulty

        fallTimerMs += dtMs
        val fallInterval = (GameConfig.FALL_SPAWN_INTERVAL_MS / diff).toLong().coerceAtLeast(50L)
        while (fallTimerMs >= fallInterval) {
            fallTimerMs -= fallInterval
            spawnItem()
        }

        beastTimerMs += dtMs
        val beastInterval = (GameConfig.BEAST_SPAWN_INTERVAL_MS / diff).toLong()
        if (beastTimerMs >= beastInterval && beasts.size < GameConfig.MAX_BEASTS_ON_SCREEN) {
            beastTimerMs = 0L
            spawnBeast()
        }

        player.tickVehicle(gameTimeMs)

        val fallSpeed = GameConfig.FALL_SPEED_BASE * viewH * diff
        val itemSize = GameConfig.ITEM_SIZE * viewW
        var i = items.size - 1
        while (i >= 0) {
            val item = items[i]
            item.y += fallSpeed * item.speedFactor * dt
            if (item.y - itemSize > viewH) {
                items.removeAt(i)
            } else if (RectF.intersects(item.hitbox(), player.hitbox(groundY, gameTimeMs, viewH))) {
                handleItemContact(item)
                items.removeAt(i)
            }
            i--
        }

        var j = beasts.size - 1
        while (j >= 0) {
            val beast = beasts[j]
            beast.update(dt)
            val offScreen = (beast.direction == 1 && beast.x - beast.sizePx > viewW) ||
                (beast.direction == -1 && beast.x + beast.sizePx < 0f)
            if (offScreen) {
                beasts.removeAt(j)
            } else if (RectF.intersects(beast.hitbox(groundY, viewH), player.hitbox(groundY, gameTimeMs, viewH))) {
                if (player.riding) {
                    addScore(GameConfig.SCORE_CRUSH_BEAST)
                    player.registerCrush()
                    notifyVehicle()
                    SoundManager.play(SoundEvent.CRUSH)
                    beasts.removeAt(j)
                } else if (!player.isInvincible(gameTimeMs)) {
                    loseLife()
                    SoundManager.play(SoundEvent.HIT)
                }
            }
            j--
        }
    }

    private fun handleItemContact(item: FallingItem) {
        when (item.kind) {
            ItemKind.BEAN -> {
                addScore(GameConfig.SCORE_BEAN)
                SoundManager.play(SoundEvent.CATCH)
            }
            ItemKind.MEAT -> {
                addScore(GameConfig.SCORE_MEAT)
                SoundManager.play(SoundEvent.CATCH)
            }
            ItemKind.PIE -> {
                addScore(GameConfig.SCORE_PIE)
                SoundManager.play(SoundEvent.CATCH)
            }
            ItemKind.CAR, ItemKind.TANK -> {
                player.mount(item.kind, gameTimeMs)
                notifyVehicle()
                SoundManager.play(SoundEvent.MOUNT)
            }
            else -> {
                if (player.riding) {
                    addScore(GameConfig.SCORE_CRUSH_HAZARD)
                    player.registerCrush()
                    notifyVehicle()
                    SoundManager.play(SoundEvent.CRUSH)
                } else if (!player.isInvincible(gameTimeMs)) {
                    loseLife()
                    SoundManager.play(SoundEvent.HIT)
                }
            }
        }
    }

    private fun addScore(points: Int) {
        score += points
        while (score >= nextExtraLifeAt && lives < GameConfig.MAX_LIVES) {
            lives++
            nextExtraLifeAt += GameConfig.POINTS_PER_EXTRA_LIFE
        }
        listener.onHudChanged(score, lives, (nextExtraLifeAt - score).coerceAtLeast(0))
    }

    private fun loseLife() {
        lives--
        if (lives <= 0) {
            lives = 0
            gameOver = true
            listener.onHudChanged(score, lives, 0)
            SoundManager.play(SoundEvent.GAMEOVER)
            listener.onGameOver(score)
        } else {
            player.invincibleUntilMs = gameTimeMs + GameConfig.INVINCIBLE_AFTER_HIT_MS
            listener.onHudChanged(score, lives, (nextExtraLifeAt - score).coerceAtLeast(0))
        }
    }

    private fun notifyVehicle() {
        listener.onVehicleChanged(
            player.riding,
            player.vehicleHits,
            if (player.riding) secondsLeftOfVehicle() else 0,
        )
    }

    private fun secondsLeftOfVehicle(): Int =
        ((player.vehicleExpiresAtMs - gameTimeMs + 999L) / 1000L).toInt().coerceAtLeast(0)

    private fun spawnItem() {
        val size = GameConfig.ITEM_SIZE * viewW
        val roll = rand.nextFloat()
        val kind = when {
            roll < GameConfig.P_BEAN -> ItemKind.BEAN
            roll < GameConfig.P_BEAN + GameConfig.P_MEAT -> ItemKind.MEAT
            roll < GameConfig.P_BEAN + GameConfig.P_MEAT + GameConfig.P_PIE -> ItemKind.PIE
            roll < GameConfig.P_BEAN + GameConfig.P_MEAT + GameConfig.P_PIE + GameConfig.P_VEHICLE ->
                if (rand.nextBoolean()) ItemKind.CAR else ItemKind.TANK
            else -> when (rand.nextInt(3)) {
                0 -> ItemKind.ROCK
                1 -> ItemKind.BOMB
                else -> ItemKind.KNIFE
            }
        }
        val speedFactor = 1f + (rand.nextFloat() * 2f - 1f) * GameConfig.FALL_SPEED_VARIANCE
        val x = size + rand.nextFloat() * (viewW - 2f * size)
        items.add(FallingItem(kind, size, speedFactor, x, -size))
    }

    private fun spawnBeast() {
        val size = GameConfig.BEAST_SIZE * viewW
        val kind = BeastKind.entries[rand.nextInt(BeastKind.entries.size)]
        val fromLeft = rand.nextBoolean()
        val speed = GameConfig.BEAST_SPEED_BASE * viewW * difficulty * (0.9f + rand.nextFloat() * 0.2f)
        val x = if (fromLeft) -size else viewW + size
        beasts.add(Beast(kind, size, speed, x, if (fromLeft) 1 else -1))
    }
}
