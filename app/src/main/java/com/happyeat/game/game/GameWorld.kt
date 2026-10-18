package com.happyeat.game.game

import com.happyeat.game.config.GameConfig
import kotlin.random.Random

class GameWorld(var listener: Listener? = null, private val rand: Random = Random.Default) {

    interface Listener {
        fun onHudChanged(score: Int, lives: Int, pointsToNextLife: Int)
        fun onVehicleChanged(riding: Boolean, hitsUsed: Int, secondsLeft: Int, ammoRemaining: Int, supplyVersion: Int)
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
    var tankDelivery: TankDelivery? = null
    val returningNpcs = ArrayList<ReturningNpc>()
    val bullets = ArrayList<Bullet>()

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
    private var previousFrameScore = 0

    /**
     * 正式对局保持开启。
     * 单测关掉之后，这一帧只结算已经放进场的实体，随机生成不会把断言打乱。
     */
    internal var spawnEnabled = true

    /** 上一帧已经推给 HUD 的骑乘状态。秒数、撞击或是否在骑有变化才再推，时间到下车也靠这次比较。 */
    private var hudRiding = false
    private var hudSeconds = 0
    private var hudHits = 0
    private var hudAmmo = 0
    private var hudSupplyVersion = 0

    val difficulty: Float
        get() = 1f + (GameConfig.DIFFICULTY_MAX_MULTIPLIER - 1f) *
            (gameTimeMs.toFloat() / GameConfig.DIFFICULTY_RAMP_MS).coerceAtMost(1f)

    fun onSize(w: Float, h: Float) {
        viewW = w
        viewH = h
        groundY = h - GameConfig.BEAST_SIZE * w
        player.sizePx = GameConfig.PLAYER_SIZE * w
        if (player.x == 0f) player.x = w / 2f
        clampPlayer()
    }

    fun refreshHud() {
        listener?.onHudChanged(score, lives, if (gameOver) 0 else (nextExtraLifeAt - score).coerceAtLeast(0))
        publishVehicleHud(force = true)
    }

    fun reset() {
        items.clear()
        beasts.clear()
        tankDelivery = null
        returningNpcs.clear()
        bullets.clear()
        score = 0
        lives = GameConfig.START_LIVES
        gameOver = false
        gameTimeMs = 0L
        nextExtraLifeAt = GameConfig.POINTS_PER_EXTRA_LIFE
        fallTimerMs = 0L
        beastTimerMs = 0L
        previousFrameScore = 0
        player.riding = false
        player.vehicleHits = 0
        player.vehicleExpiresAtMs = 0L
        player.invincibleUntilMs = 0L
        player.resetState()
        if (viewW > 0f) player.x = viewW / 2f
        listener?.onHudChanged(score, lives, nextExtraLifeAt)
        publishVehicleHud(force = true)
    }

    fun dragPlayerBy(dx: Float) {
        if (viewW == 0f) return
        player.x += dx
        clampPlayer()
    }

    private fun clampPlayer() {
        val half = (if (player.riding) GameConfig.TANK_SIZE * viewW else player.sizePx) / 2f
        player.x = player.x.coerceIn(half, viewW - half)
    }

    fun performPlayerAction() {
        if (gameOver || viewW == 0f) return
        player.tickVehicle(gameTimeMs)
        if (player.riding) {
            if (player.ammoRemaining > 0) {
                val direction = beasts.firstOrNull()?.let { -it.direction } ?: checkNotNull(player.lastFireDirection)
                player.lastFireDirection = direction
                player.ammoRemaining--
                bullets.add(Bullet(player.x + direction * GameConfig.BULLET_MUZZLE * viewW,
                    groundY - GameConfig.BULLET_HEIGHT * viewW, direction,
                    GameConfig.BULLET_SIZE * viewW, GameConfig.BULLET_SPEED * viewW))
            }
        } else if (!player.isAirborne(gameTimeMs)) {
            player.jump(gameTimeMs)
            SoundManager.play(SoundEvent.JUMP)
        }
        publishVehicleHud()
    }

    private fun updateDelivery(dt: Float) {
        returningNpcs.forEach { it.x += it.direction * GameConfig.NPC_SPEED * viewW * dt }
        returningNpcs.removeAll { it.x < -it.sizePx || it.x > viewW + it.sizePx }
        val delivery = tankDelivery ?: return
        if (delivery.update(dt, gameTimeMs)) {
            returningNpcs.add(ReturningNpc(delivery.x, -delivery.direction, GameConfig.NPC_SIZE * viewW))
        }
        val stopped = delivery.stoppedAtMs ?: return
        if (gameTimeMs - stopped >= GameConfig.TANK_WAIT_MS) {
            tankDelivery = null
        } else if (!player.isAirborne(gameTimeMs) &&
            kotlin.math.abs(player.x - delivery.x) < ((if (player.riding) GameConfig.TANK_SIZE * viewW else player.displaySizePx) + delivery.sizePx) * GameConfig.HITBOX_SCALE / 2f) {
            player.mount(gameTimeMs, delivery.direction)
            clampPlayer()
            tankDelivery = null
            SoundManager.play(SoundEvent.MOUNT)
        }
    }

    fun update(dtMs: Long) {
        if (gameOver || viewW == 0f) return
        gameTimeMs += dtMs
        val dt = dtMs / 1000f
        val diff = difficulty
        // 碰撞加分从下一帧生效，所有实体共用这一帧的阶段。
        val frameScore = score
        val restartBeastTimer =
            (previousFrameScore <= GameConfig.BEAST_SCORE_THRESHOLD && frameScore > GameConfig.BEAST_SCORE_THRESHOLD) ||
            (previousFrameScore <= GameConfig.BEAST_FREQUENCY_SCORE_THRESHOLD && frameScore > GameConfig.BEAST_FREQUENCY_SCORE_THRESHOLD)
        previousFrameScore = frameScore
        if (frameScore <= GameConfig.BEAST_SCORE_THRESHOLD || restartBeastTimer) beastTimerMs = 0L

        if (spawnEnabled) {
            fallTimerMs += dtMs
            val fallInterval = (GameConfig.FALL_SPAWN_INTERVAL_MS / diff).toLong().coerceAtLeast(50L)
            while (fallTimerMs >= fallInterval) {
                fallTimerMs -= fallInterval
                spawnItem(frameScore)
            }

            if (frameScore > GameConfig.BEAST_SCORE_THRESHOLD && !restartBeastTimer) {
                beastTimerMs += dtMs
                val progress = ((frameScore - GameConfig.BEAST_FREQUENCY_SCORE_THRESHOLD).toFloat() /
                    GameConfig.BEAST_FREQUENCY_RAMP_SCORE).coerceIn(0f, 1f)
                val beastInterval = (GameConfig.BEAST_SPAWN_INTERVAL_MS -
                    (GameConfig.BEAST_SPAWN_INTERVAL_MS - GameConfig.BEAST_MIN_SPAWN_INTERVAL_MS) * progress).toLong()
                if (beastTimerMs >= beastInterval) {
                    beastTimerMs = 0L
                    if (beasts.size < GameConfig.MAX_BEASTS_ON_SCREEN) spawnBeast()
                }
            }
        }

        player.tickVehicle(gameTimeMs)
        updateDelivery(dt)

        val fallSpeed = GameConfig.FALL_SPEED_BASE * viewH * diff
        val itemSize = GameConfig.ITEM_SIZE * viewW
        var i = items.size - 1
        while (i >= 0) {
            val item = items[i]
            item.y += fallSpeed * item.speedFactor * dt
            if (item.y - itemSize > viewH) {
                items.removeAt(i)
            } else if (item.hitbox().intersects(player.hitbox(groundY, gameTimeMs, viewH))) {
                handleItemContact(item)
                items.removeAt(i)
                // 这一下已经把本局结束了。同帧里还排在后面的食物不再结算，
                // 否则结束面板和数据库记下的分会比左上角少。
                if (gameOver) break
            }
            i--
        }

        // 人已经死了就不要再和猛兽结算，避免同一帧里死亡之后又改分数。
        if (gameOver) {
            publishVehicleHud()
            return
        }

        // 只细分运动与炮弹碰撞，生成计时仍按原帧执行一次。
        var remaining = dtMs / 1000f
        do {
            val seconds = beasts.fold(minOf(remaining, GameConfig.PHYSICS_STEP_MS / 1000f)) { limit, beast ->
                minOf(limit, beast.secondsToJumpBoundary)
            }
            val oldBeasts = beasts.associateWith { it.hitbox(groundY, viewH) }
            val oldBullets = bullets.associateWith { it.hitbox() }
            beasts.forEach { it.update(seconds, frameScore > GameConfig.BEAST_JUMP_SCORE_THRESHOLD) }
            bullets.forEach { it.x += it.direction * it.speed * seconds }
            data class Hit(val time: Float, val bullet: Bullet, val beast: Beast)
            val hits = ArrayList<Hit>()
            for (bullet in bullets) for (beast in beasts) {
                sweptHit(oldBullets.getValue(bullet), bullet.hitbox(), oldBeasts.getValue(beast),
                    beast.hitbox(groundY, viewH))?.let { hits.add(Hit(it, bullet, beast)) }
            }
            for (hit in hits.sortedBy { it.time }) {
                if (hit.bullet in bullets && hit.beast in beasts) {
                    bullets.remove(hit.bullet)
                    beasts.remove(hit.beast)
                    addScore(beastScore(hit.beast))
                    SoundManager.play(SoundEvent.CRUSH)
                }
            }
            bullets.removeAll { it.x < -it.sizePx || it.x > viewW + it.sizePx }
            remaining -= seconds
        } while (remaining > 0)
        var j = beasts.size - 1
        while (j >= 0 && !gameOver) {
            val beast = beasts[j]
            val offScreen = (beast.direction == 1 && beast.x - beast.sizePx > viewW) ||
                (beast.direction == -1 && beast.x + beast.sizePx < 0f)
            if (offScreen) {
                beasts.removeAt(j)
            } else if (beast.hitbox(groundY, viewH).intersects(player.hitbox(groundY, gameTimeMs, viewH))) {
                if (player.riding) {
                    addScore(beastScore(beast))
                    player.registerCrush()
                    SoundManager.play(SoundEvent.CRUSH)
                    beasts.removeAt(j)
                } else if (!player.isInvincible(gameTimeMs)) {
                    loseLife()
                    SoundManager.play(SoundEvent.HIT)
                }
            }
            j--
        }
        publishVehicleHud()
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
            else -> {
                if (player.riding) {
                    addScore(GameConfig.SCORE_CRUSH_HAZARD)
                    player.registerCrush()
                    SoundManager.play(SoundEvent.CRUSH)
                } else if (!player.isInvincible(gameTimeMs)) {
                    loseLife()
                    SoundManager.play(SoundEvent.HIT)
                }
            }
        }
    }

    private fun beastScore(beast: Beast) = if (beast.kind == BeastKind.WOLF) GameConfig.SCORE_WOLF else GameConfig.SCORE_CRUSH_BEAST

    private fun addScore(points: Int) {
        // 结束之后分数冻结，和已经发给面板、数据库的那一帧保持一致。
        if (gameOver) return
        score += points
        while (score >= nextExtraLifeAt && lives < GameConfig.MAX_LIVES) {
            lives++
            nextExtraLifeAt += GameConfig.POINTS_PER_EXTRA_LIFE
        }
        listener?.onHudChanged(score, lives, (nextExtraLifeAt - score).coerceAtLeast(0))
    }

    private fun loseLife() {
        if (gameOver) return
        lives--
        if (lives <= 0) {
            lives = 0
            gameOver = true
            listener?.onHudChanged(score, lives, 0)
            SoundManager.play(SoundEvent.GAMEOVER)
            listener?.onGameOver(score)
        } else {
            player.invincibleUntilMs = gameTimeMs + GameConfig.INVINCIBLE_AFTER_HIT_MS
            listener?.onHudChanged(score, lives, (nextExtraLifeAt - score).coerceAtLeast(0))
        }
    }

    /**
     * 骑乘行只在剩余秒数、撞击次数或上下车发生变化时刷新。
     * 每帧都推的话主线程会被刷屏；完全不推的话，倒计时会停在刚骑上的倒计时，
     * 时间走完后人已经下车，左上角那一行还在。
     */
    private fun publishVehicleHud(force: Boolean = false) {
        val riding = player.riding
        val seconds = if (riding) secondsLeftOfVehicle() else 0
        val hits = if (riding) player.vehicleHits else 0
        if (!force && riding == hudRiding && seconds == hudSeconds && hits == hudHits && player.ammoRemaining == hudAmmo && player.supplyVersion == hudSupplyVersion) return
        hudRiding = riding
        hudSeconds = seconds
        hudHits = hits
        hudAmmo = player.ammoRemaining
        hudSupplyVersion = player.supplyVersion
        listener?.onVehicleChanged(riding, hits, seconds, hudAmmo, hudSupplyVersion)
    }

    private fun secondsLeftOfVehicle(): Int =
        ((player.vehicleExpiresAtMs - gameTimeMs + 999L) / 1000L).toInt().coerceAtLeast(0)

    private fun spawnItem(frameScore: Int) {
        val size = GameConfig.ITEM_SIZE * viewW
        val foodWeight = GameConfig.P_BEAN + GameConfig.P_MEAT + GameConfig.P_PIE
        var roll = rand.nextFloat() * if (frameScore <= GameConfig.HAZARD_SCORE_THRESHOLD) foodWeight else 1f
        if (roll >= foodWeight && roll < foodWeight + GameConfig.P_VEHICLE) {
            if (tankDelivery == null) {
                tankDelivery = TankDelivery(if (rand.nextBoolean()) 1 else -1, viewW)
                return
            }
            roll = rand.nextFloat() * foodWeight
        }
        val kind = when {
            roll < GameConfig.P_BEAN -> ItemKind.BEAN
            roll < GameConfig.P_BEAN + GameConfig.P_MEAT -> ItemKind.MEAT
            roll < GameConfig.P_BEAN + GameConfig.P_MEAT + GameConfig.P_PIE -> ItemKind.PIE
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
        val fromLeft = beasts.firstOrNull()?.let { it.direction > 0 } ?: rand.nextBoolean()
        val speed = GameConfig.BEAST_SPEED_BASE * viewW * difficulty * (0.9f + rand.nextFloat() * 0.2f)
        val x = if (fromLeft) -size else viewW + size
        beasts.add(Beast(kind, size, speed, x, if (fromLeft) 1 else -1, rand))
    }
}
