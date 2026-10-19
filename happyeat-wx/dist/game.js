(() => {
  // src/config.ts
  var C = {
    START_LIVES: 3,
    MAX_LIVES: 30,
    POINTS_PER_EXTRA_LIFE: 200,
    INVINCIBLE_AFTER_HIT_MS: 2e3,
    FALL_SPAWN_INTERVAL_MS: 900,
    HAZARD_SCORE_THRESHOLD: 50,
    BEAST_SCORE_THRESHOLD: 100,
    BEAST_JUMP_SCORE_THRESHOLD: 200,
    BEAST_FREQUENCY_SCORE_THRESHOLD: 300,
    BEAST_FREQUENCY_RAMP_SCORE: 300,
    BEAST_SPAWN_INTERVAL_MS: 12e3,
    BEAST_MIN_SPAWN_INTERVAL_MS: 6e3,
    MAX_BEASTS_ON_SCREEN: 3,
    DIFFICULTY_RAMP_MS: 3e5,
    DIFFICULTY_MAX_MULTIPLIER: 2.5,
    P_BEAN: 0.5,
    P_MEAT: 0.1,
    P_PIE: 0.08,
    P_VEHICLE: 0.04,
    SCORE_BEAN: 1,
    SCORE_MEAT: 5,
    SCORE_PIE: 10,
    SCORE_CRUSH_HAZARD: 3,
    SCORE_CRUSH_BEAST: 10,
    SCORE_WOLF: 5,
    VEHICLE_DURATION_MS: 15e3,
    VEHICLE_MAX_HITS: 3,
    TANK_AMMO: 3,
    TANK_WAIT_MS: 3e3,
    TANK_STOP_FRACTION: 1 / 3,
    TANK_SIZE: 0.169,
    TANK_SPEED: 0.25,
    NPC_SIZE: 0.065,
    NPC_SPEED: 0.3,
    NPC_HAT_HEIGHT: 0.2,
    BULLET_SIZE: 0.015,
    BULLET_SPEED: 1.5,
    BULLET_HEIGHT: 0.169 * 0.56,
    BULLET_MUZZLE: 0.169 * 0.43,
    PHYSICS_STEP_MS: 10,
    FALL_SPEED_BASE: 0.28,
    FALL_SPEED_VARIANCE: 0.15,
    BEAST_SPEED_BASE: 0.35,
    BEAST_JUMP_CHANCE_PER_SEC: 0.8,
    BEAST_JUMP_HEIGHT: 0.12,
    BEAST_JUMP_DURATION_MS: 500,
    RIDING_PLAYER_OFFSET: 0.2,
    RIDING_PLAYER_FALLBACK_SCALE: 0.8,
    PLAYER_SIZE: 0.13,
    ITEM_SIZE: 0.09,
    BEAST_SIZE: 0.12,
    HITBOX_SCALE: 0.75,
    JUMP_HEIGHT: 0.2,
    JUMP_AIRTIME_MS: 640,
    BG_GROUND_FRACTIONS: {
      background: 0.833,
      background_sunset: 0.805,
      background_night: 0.805,
      background_orchard: 0.73,
      background_orchard_sunset: 0.72,
      background_orchard_night: 0.72
    },
    BG_FADE_MS: 900,
    /** UI（逻辑像素） */
    UI: {
      CONTROLS_HEIGHT: 112,
      JUMP_AREA_WIDTH: 104,
      CONTROL_INSET: 4,
      CONTROL_DEPTH: 5,
      CONTROL_HIGHLIGHT: 2,
      CONTROL_HIGHLIGHT_INSET: 12,
      PRESSED_MINT: "#78CBA6",
      PRESSED_GOLD: "#F3BC36",
      DISABLED: "#DDE2DA",
      SHADOW: "#72988E",
      GOLD_SHADOW: "#BA883B"
    }
  };
  var Candy = {
    INK: "#0B3D2E",
    CREAM: "#FFF6E3",
    AMBER: "#F59E0B",
    AMBER_DARK: "#B45309",
    GOLD: "#FCD34D",
    MUTED: "#94A3B8"
  };

  // src/entities.ts
  var hit = (cx, cy, half) => ({ l: cx - half, t: cy - half, r: cx + half, b: cy + half });
  function intersects(a, b) {
    return a.l < b.r && a.r > b.l && a.t < b.b && a.b > b.t;
  }
  function sweptHit(a, endA, b, endB) {
    let enter = 0, exit = 1;
    const axis = (la, ha, lb, hb, v) => {
      if (v === 0) return la < hb && ha > lb;
      enter = Math.max(enter, (lb - ha) / v);
      exit = Math.min(exit, (hb - la) / v);
      return enter <= exit;
    };
    if (!axis(a.l, a.r, b.l, b.r, endA.l - a.l)) return null;
    if (!axis(a.t, a.b, b.t, b.b, endA.t - a.t)) return null;
    return enter <= exit ? enter : null;
  }
  var Player = class {
    constructor() {
      this.x = 0;
      this.sizePx = 0;
      this.riding = false;
      this.supplyVersion = 0;
      this.ammoRemaining = 0;
      this.lastFireDirection = null;
      this.vehicleHits = 0;
      this.vehicleExpiresAtMs = 0;
      this.invincibleUntilMs = 0;
      this.jumpStartMs = Number.MIN_SAFE_INTEGER;
    }
    get displaySizePx() {
      return this.sizePx * (this.riding ? C.RIDING_PLAYER_FALLBACK_SCALE : 1);
    }
    displayCenterY(groundY, nowMs, viewH) {
      return groundY - this.sizePx / 2 - this.heightOffset(nowMs, viewH) - (this.riding ? this.sizePx * C.RIDING_PLAYER_OFFSET : 0);
    }
    hitbox(groundY, nowMs, viewH) {
      const half = this.displaySizePx * C.HITBOX_SCALE / 2;
      return hit(this.x, this.displayCenterY(groundY, nowMs, viewH), half);
    }
    isInvincible(nowMs) {
      return nowMs < this.invincibleUntilMs;
    }
    isAirborne(nowMs) {
      return this.jumpStartMs !== Number.MIN_SAFE_INTEGER && nowMs - this.jumpStartMs < C.JUMP_AIRTIME_MS;
    }
    heightOffset(nowMs, viewH) {
      if (!this.isAirborne(nowMs)) return 0;
      const t = (nowMs - this.jumpStartMs) / C.JUMP_AIRTIME_MS;
      return Math.sin(t * Math.PI) * C.JUMP_HEIGHT * viewH;
    }
    jump(nowMs) {
      if (!this.riding && !this.isAirborne(nowMs)) this.jumpStartMs = nowMs;
    }
    mount(nowMs, defaultDirection) {
      if (!this.riding) this.vehicleHits = 0;
      this.riding = true;
      this.supplyVersion++;
      this.jumpStartMs = Number.MIN_SAFE_INTEGER;
      if (this.lastFireDirection === null) this.lastFireDirection = defaultDirection;
      this.ammoRemaining = C.TANK_AMMO;
      this.vehicleExpiresAtMs = nowMs + C.VEHICLE_DURATION_MS;
    }
    registerCrush() {
      this.vehicleHits++;
      if (this.vehicleHits >= C.VEHICLE_MAX_HITS) {
        this.riding = false;
        this.ammoRemaining = 0;
      }
    }
    tickVehicle(nowMs) {
      if (this.riding && nowMs >= this.vehicleExpiresAtMs) {
        this.riding = false;
        this.ammoRemaining = 0;
      }
    }
    resetState() {
      this.supplyVersion = 0;
      this.ammoRemaining = 0;
      this.lastFireDirection = null;
      this.jumpStartMs = Number.MIN_SAFE_INTEGER;
    }
  };
  var Beast = class {
    constructor(kind, sizePx, speedPxPerSec, x, direction, rand = Math.random) {
      this.kind = kind;
      this.sizePx = sizePx;
      this.speedPxPerSec = speedPxPerSec;
      this.x = x;
      this.direction = direction;
      this.rand = rand;
      this.jumping = false;
      this.jumpT = 0;
    }
    get secondsToJumpBoundary() {
      if (!this.jumping) return Infinity;
      return ((this.jumpT < 0.5 ? 0.5 : 1) - this.jumpT) * C.BEAST_JUMP_DURATION_MS / 1e3;
    }
    update(dt, allowJump) {
      this.x += this.speedPxPerSec * this.direction * dt;
      if (!allowJump) {
        this.jumping = false;
        this.jumpT = 0;
      } else if (this.jumping) {
        this.jumpT += dt * 1e3 / C.BEAST_JUMP_DURATION_MS;
        if (this.jumpT >= 1) {
          this.jumping = false;
          this.jumpT = 0;
        }
      } else if (this.rand() < C.BEAST_JUMP_CHANCE_PER_SEC * dt) this.jumping = true;
    }
    heightOffset(viewH) {
      return this.jumping ? Math.sin(this.jumpT * Math.PI) * C.BEAST_JUMP_HEIGHT * viewH : 0;
    }
    hitbox(groundY, viewH) {
      const half = this.sizePx * C.HITBOX_SCALE / 2;
      return hit(this.x, groundY - this.sizePx / 2 - this.heightOffset(viewH), half);
    }
  };
  var FallingItem = class {
    constructor(kind, sizePx, speedFactor, x, y) {
      this.kind = kind;
      this.sizePx = sizePx;
      this.speedFactor = speedFactor;
      this.x = x;
      this.y = y;
    }
    hitbox() {
      const half = this.sizePx * C.HITBOX_SCALE / 2;
      return hit(this.x, this.y, half);
    }
  };
  var Bullet = class {
    constructor(x, y, direction, sizePx, speed) {
      this.x = x;
      this.y = y;
      this.direction = direction;
      this.sizePx = sizePx;
      this.speed = speed;
    }
    hitbox() {
      const h = this.sizePx / 2;
      return hit(this.x, this.y, h);
    }
  };
  var TankDelivery = class {
    constructor(direction, worldWidth) {
      this.direction = direction;
      this.worldWidth = worldWidth;
      this.stoppedAtMs = null;
      this.sizePx = C.TANK_SIZE * worldWidth;
      this.x = direction > 0 ? -this.sizePx : worldWidth + this.sizePx;
      this.targetX = worldWidth * (direction > 0 ? C.TANK_STOP_FRACTION : 1 - C.TANK_STOP_FRACTION);
    }
    /** 返回 true = 已停靠 */
    update(dt, nowMs) {
      if (this.stoppedAtMs !== null) return false;
      const dist = C.TANK_SPEED * this.worldWidth * dt;
      if (Math.abs(this.targetX - this.x) <= dist) {
        this.x = this.targetX;
        this.stoppedAtMs = nowMs;
        return true;
      }
      this.x += this.direction * dist;
      return false;
    }
  };
  var ReturningNpc = class {
    constructor(x, direction, sizePx) {
      this.x = x;
      this.direction = direction;
      this.sizePx = sizePx;
    }
  };

  // src/audio.ts
  var BGM_FILES = {
    MENU: "assets/audio/bgm_menu.mp3",
    GAME: "assets/audio/bgm_loop.mp3"
  };
  var bgmStream = null;
  var bgmTrack = "NONE";
  var sfxPool = {};
  function sfx(ev) {
    const a = sfxPool[ev];
    if (a) {
      a.stop();
      a.play();
    }
  }
  function startBgm(track) {
    if (bgmTrack === track && bgmStream) return;
    if (bgmStream) {
      bgmStream.stop();
      bgmStream = null;
    }
    const a = wx.createInnerAudioContext();
    a.src = BGM_FILES[track];
    a.loop = true;
    a.volume = 0.45;
    a.play();
    bgmStream = a;
    bgmTrack = track;
  }
  function stopBgm() {
    if (bgmStream) {
      bgmStream.stop();
      bgmStream = null;
    }
    bgmTrack = "NONE";
  }

  // src/world.ts
  var GameWorld = class {
    constructor() {
      this.listener = null;
      this.viewW = 0;
      this.viewH = 0;
      this.groundY = 0;
      this.player = new Player();
      this.items = [];
      this.beasts = [];
      this.tankDelivery = null;
      this.returningNpcs = [];
      this.bullets = [];
      this.score = 0;
      this.lives = C.START_LIVES;
      this.gameOver = false;
      this.gameTimeMs = 0;
      this.spawnEnabled = true;
      this.nextExtraLifeAt = C.POINTS_PER_EXTRA_LIFE;
      this.fallTimerMs = 0;
      this.beastTimerMs = 0;
      this.previousFrameScore = 0;
      this.hudRiding = false;
      this.hudSeconds = 0;
      this.hudHits = 0;
      this.hudAmmo = 0;
      this.hudSupplyVersion = 0;
      this.groundFraction = 0.8;
      this.bgIndex = 0;
    }
    get difficulty() {
      return 1 + (C.DIFFICULTY_MAX_MULTIPLIER - 1) * Math.min(1, this.gameTimeMs / C.DIFFICULTY_RAMP_MS);
    }
    onSize(w, h) {
      this.viewW = w;
      this.viewH = h;
      this.groundY = h * this.groundFraction;
      this.player.sizePx = C.PLAYER_SIZE * w;
      this.clampPlayer();
    }
    refreshHud() {
      this.publishVehicleHud(true);
    }
    reset() {
      var _a;
      this.items.length = 0;
      this.beasts.length = 0;
      this.bullets.length = 0;
      this.returningNpcs.length = 0;
      this.tankDelivery = null;
      this.score = 0;
      this.lives = C.START_LIVES;
      this.gameOver = false;
      this.gameTimeMs = 0;
      this.nextExtraLifeAt = C.POINTS_PER_EXTRA_LIFE;
      this.fallTimerMs = 0;
      this.beastTimerMs = 0;
      this.previousFrameScore = 0;
      this.spawnEnabled = true;
      this.player.resetState();
      if (this.viewW > 0) {
        this.player.x = this.viewW / 2;
        this.clampPlayer();
      }
      this.publishVehicleHud(true);
      (_a = this.listener) == null ? void 0 : _a.onHudChanged(this.score, this.lives, this.nextExtraLifeAt - this.score);
    }
    dragPlayerBy(dx) {
      if (this.viewW === 0) return;
      this.player.x += dx;
      this.clampPlayer();
    }
    clampPlayer() {
      const half = (this.player.riding ? C.TANK_SIZE * this.viewW : this.player.sizePx) / 2;
      this.player.x = Math.min(Math.max(this.player.x, half), this.viewW - half);
    }
    performPlayerAction() {
      var _a;
      if (this.gameOver || this.viewW === 0) return;
      this.player.tickVehicle(this.gameTimeMs);
      if (this.player.riding) {
        if (this.player.ammoRemaining > 0) {
          const beast = this.beasts[0];
          const direction = beast ? -beast.direction : (_a = this.player.lastFireDirection) != null ? _a : 1;
          this.player.lastFireDirection = direction;
          this.player.ammoRemaining--;
          this.bullets.push(new Bullet(
            this.player.x + direction * C.BULLET_MUZZLE * this.viewW,
            this.groundY - C.BULLET_HEIGHT * this.viewW,
            direction,
            C.BULLET_SIZE * this.viewW,
            C.BULLET_SPEED * this.viewW
          ));
        }
      } else if (!this.player.isAirborne(this.gameTimeMs)) {
        this.player.jump(this.gameTimeMs);
        sfx("JUMP");
      }
      this.publishVehicleHud();
    }
    updateDelivery(dt) {
      for (const npc of this.returningNpcs) npc.x += npc.direction * C.NPC_SPEED * this.viewW * dt;
      this.returningNpcs = this.returningNpcs.filter((n) => n.x > -n.sizePx && n.x < this.viewW + n.sizePx);
      const delivery = this.tankDelivery;
      if (!delivery) return;
      if (delivery.update(dt, this.gameTimeMs)) {
        this.returningNpcs.push(new ReturningNpc(delivery.x, -delivery.direction, C.NPC_SIZE * this.viewW));
      }
      const stopped = delivery.stoppedAtMs;
      if (stopped === null) return;
      if (this.gameTimeMs - stopped >= C.TANK_WAIT_MS) this.tankDelivery = null;
      else if (!this.player.isAirborne(this.gameTimeMs) && Math.abs(this.player.x - delivery.x) < ((this.player.riding ? C.TANK_SIZE * this.viewW : this.player.displaySizePx) + delivery.sizePx) * C.HITBOX_SCALE / 2) {
        this.player.mount(this.gameTimeMs, delivery.direction);
        this.clampPlayer();
        this.tankDelivery = null;
        sfx("MOUNT");
      }
    }
    update(dtMs) {
      if (this.gameOver || this.viewW === 0) return;
      this.gameTimeMs += dtMs;
      const dt = dtMs / 1e3;
      const diff = this.difficulty;
      const frameScore = this.score;
      const restartBeastTimer = this.previousFrameScore <= C.BEAST_SCORE_THRESHOLD && frameScore > C.BEAST_SCORE_THRESHOLD || this.previousFrameScore <= C.BEAST_FREQUENCY_SCORE_THRESHOLD && frameScore > C.BEAST_FREQUENCY_SCORE_THRESHOLD;
      this.previousFrameScore = frameScore;
      if (frameScore <= C.BEAST_SCORE_THRESHOLD || restartBeastTimer) this.beastTimerMs = 0;
      if (this.spawnEnabled) {
        this.fallTimerMs += dtMs;
        const fallInterval = Math.max(50, C.FALL_SPAWN_INTERVAL_MS / diff);
        while (this.fallTimerMs >= fallInterval) {
          this.fallTimerMs -= fallInterval;
          this.spawnItem(frameScore);
        }
        if (frameScore > C.BEAST_SCORE_THRESHOLD && !restartBeastTimer) {
          this.beastTimerMs += dtMs;
          const progress = Math.min(1, (frameScore - C.BEAST_FREQUENCY_SCORE_THRESHOLD) / C.BEAST_FREQUENCY_RAMP_SCORE);
          const beastInterval = C.BEAST_SPAWN_INTERVAL_MS - (C.BEAST_SPAWN_INTERVAL_MS - C.BEAST_MIN_SPAWN_INTERVAL_MS) * progress;
          if (this.beastTimerMs >= beastInterval) {
            this.beastTimerMs = 0;
            if (this.beasts.length < C.MAX_BEASTS_ON_SCREEN) this.spawnBeast();
          }
        }
      }
      this.player.tickVehicle(this.gameTimeMs);
      this.updateDelivery(dt);
      const fallSpeed = C.FALL_SPEED_BASE * this.viewH * diff;
      const itemSize = C.ITEM_SIZE * this.viewW;
      for (let i = this.items.length - 1; i >= 0; i--) {
        const item = this.items[i];
        item.y += fallSpeed * item.speedFactor * dt;
        if (item.y - itemSize > this.viewH) {
          this.items.splice(i, 1);
          continue;
        }
        if (intersects(item.hitbox(), this.player.hitbox(this.groundY, this.gameTimeMs, this.viewH))) {
          this.handleItemContact(item);
          this.items.splice(i, 1);
          if (this.gameOver) break;
        }
      }
      if (this.gameOver) {
        this.publishVehicleHud();
        return;
      }
      var remaining = dtMs / 1e3;
      do {
        let seconds = Math.min(remaining, C.PHYSICS_STEP_MS / 1e3);
        for (const b of this.beasts) seconds = Math.min(seconds, b.secondsToJumpBoundary);
        const oldBeasts = new Map(this.beasts.map((b) => [b, b.hitbox(this.groundY, this.viewH)]));
        const oldBullets = new Map(this.bullets.map((b) => [b, b.hitbox()]));
        this.beasts.forEach((b) => b.update(seconds, frameScore > C.BEAST_JUMP_SCORE_THRESHOLD));
        this.bullets.forEach((b) => {
          b.x += b.direction * b.speed * seconds;
        });
        const hits = [];
        for (const bullet of this.bullets) for (const beast of this.beasts) {
          const t = sweptHit(oldBullets.get(bullet), bullet.hitbox(), oldBeasts.get(beast), beast.hitbox(this.groundY, this.viewH));
          if (t !== null) hits.push({ time: t, bullet, beast });
        }
        hits.sort((a, b) => a.time - b.time);
        for (const h of hits) {
          if (this.bullets.includes(h.bullet) && this.beasts.includes(h.beast)) {
            this.bullets.splice(this.bullets.indexOf(h.bullet), 1);
            this.beasts.splice(this.beasts.indexOf(h.beast), 1);
            this.addScore(beastScore(h.beast));
            sfx("CRUSH");
          }
        }
        this.bullets = this.bullets.filter((b) => b.x > -b.sizePx && b.x < this.viewW + b.sizePx);
        remaining -= seconds;
      } while (remaining > 0);
      for (let j = this.beasts.length - 1; j >= 0 && !this.gameOver; j--) {
        const beast = this.beasts[j];
        const offScreen = beast.direction === 1 && beast.x - beast.sizePx > this.viewW || beast.direction === -1 && beast.x + beast.sizePx < 0;
        if (offScreen) {
          this.beasts.splice(j, 1);
          continue;
        }
        if (intersects(beast.hitbox(this.groundY, this.viewH), this.player.hitbox(this.groundY, this.gameTimeMs, this.viewH))) {
          if (this.player.riding) {
            this.addScore(beastScore(beast));
            this.player.registerCrush();
            sfx("CRUSH");
            this.beasts.splice(j, 1);
          } else if (!this.player.isInvincible(this.gameTimeMs)) {
            this.loseLife();
            sfx("HIT");
          }
        }
      }
      this.publishVehicleHud();
    }
    handleItemContact(item) {
      if (item.kind === "BEAN") {
        this.addScore(C.SCORE_BEAN);
        sfx("CATCH");
      } else if (item.kind === "MEAT") {
        this.addScore(C.SCORE_MEAT);
        sfx("CATCH");
      } else if (item.kind === "PIE") {
        this.addScore(C.SCORE_PIE);
        sfx("CATCH");
      } else {
        if (this.player.riding) {
          this.addScore(C.SCORE_CRUSH_HAZARD);
          this.player.registerCrush();
          sfx("CRUSH");
        } else if (!this.player.isInvincible(this.gameTimeMs)) {
          this.loseLife();
          sfx("HIT");
        }
      }
    }
    beastScore(beast) {
      return beast.kind === "WOLF" ? C.SCORE_WOLF : C.SCORE_CRUSH_BEAST;
    }
    addScore(points) {
      var _a;
      if (this.gameOver) return;
      this.score += points;
      while (this.score >= this.nextExtraLifeAt && this.lives < C.MAX_LIVES) {
        this.lives++;
        this.nextExtraLifeAt += C.POINTS_PER_EXTRA_LIFE;
      }
      (_a = this.listener) == null ? void 0 : _a.onHudChanged(this.score, this.lives, Math.max(0, this.nextExtraLifeAt - this.score));
    }
    loseLife() {
      var _a, _b, _c;
      if (this.gameOver) return;
      this.lives--;
      if (this.lives <= 0) {
        this.lives = 0;
        this.gameOver = true;
        (_a = this.listener) == null ? void 0 : _a.onHudChanged(this.score, this.lives, 0);
        sfx("GAMEOVER");
        (_b = this.listener) == null ? void 0 : _b.onGameOver(this.score);
      } else {
        this.player.invincibleUntilMs = this.gameTimeMs + C.INVINCIBLE_AFTER_HIT_MS;
        (_c = this.listener) == null ? void 0 : _c.onHudChanged(this.score, this.lives, Math.max(0, this.nextExtraLifeAt - this.score));
      }
    }
    publishVehicleHud(force = false) {
      var _a;
      const p = this.player;
      const riding = p.riding;
      const seconds = riding ? this.secondsLeftOfVehicle() : 0;
      const hits = riding ? p.vehicleHits : 0;
      if (!force && riding === this.hudRiding && seconds === this.hudSeconds && hits === this.hudHits && p.ammoRemaining === this.hudAmmo && p.supplyVersion === this.hudSupplyVersion) return;
      this.hudRiding = riding;
      this.hudSeconds = seconds;
      this.hudHits = hits;
      this.hudAmmo = p.ammoRemaining;
      this.hudSupplyVersion = p.supplyVersion;
      (_a = this.listener) == null ? void 0 : _a.onVehicleChanged(riding, hits, seconds, p.ammoRemaining, p.supplyVersion);
    }
    secondsLeftOfVehicle() {
      return Math.max(0, Math.floor((this.player.vehicleExpiresAtMs - this.gameTimeMs + 999) / 1e3));
    }
    spawnItem(frameScore) {
      const size = C.ITEM_SIZE * this.viewW;
      const foodWeight = C.P_BEAN + C.P_MEAT + C.P_PIE;
      var roll = Math.random() * (frameScore <= C.HAZARD_SCORE_THRESHOLD ? foodWeight : 1);
      if (roll >= foodWeight && roll < foodWeight + C.P_VEHICLE) {
        if (this.tankDelivery === null) {
          this.tankDelivery = new TankDelivery(Math.random() < 0.5 ? 1 : -1, this.viewW);
          return;
        }
        roll = Math.random() * foodWeight;
      }
      let kind;
      if (roll < C.P_BEAN) kind = "BEAN";
      else if (roll < C.P_BEAN + C.P_MEAT) kind = "MEAT";
      else if (roll < C.P_BEAN + C.P_MEAT + C.P_PIE) kind = "PIE";
      else kind = ["ROCK", "BOMB", "KNIFE"][Math.floor(Math.random() * 3)];
      const speedFactor = 1 + (Math.random() * 2 - 1) * C.FALL_SPEED_VARIANCE;
      const x = size + Math.random() * (this.viewW - 2 * size);
      this.items.push(new FallingItem(kind, size, speedFactor, x, -size));
    }
    spawnBeast() {
      const size = C.BEAST_SIZE * this.viewW;
      const kinds = ["TIGER", "LION", "WOLF"];
      const kind = kinds[Math.floor(Math.random() * 3)];
      const fromLeft = this.beasts[0] ? this.beasts[0].direction > 0 : Math.random() < 0.5;
      const speed = C.BEAST_SPEED_BASE * this.viewW * this.difficulty * (0.9 + Math.random() * 0.2);
      const x = fromLeft ? -size : this.viewW + size;
      this.beasts.push(new Beast(kind, size, speed, x, fromLeft ? 1 : -1));
    }
  };

  // src/draw.ts
  var SPRITE_FILES = [
    "bean",
    "bomb",
    "knife",
    "lion",
    "meat",
    "menu_hero",
    "pie",
    "player",
    "rock",
    "tank",
    "tiger",
    "title_emblem",
    "ui_back",
    "ui_check",
    "ui_close",
    "ui_exit",
    "ui_heart",
    "ui_help",
    "ui_language",
    "ui_moon",
    "ui_move",
    "ui_pause",
    "ui_play",
    "ui_restart",
    "ui_settings",
    "ui_sun",
    "ui_tap",
    "ui_trophy",
    "wolf"
  ];
  var BG_FILES = [
    "background",
    "background_sunset",
    "background_night",
    "background_orchard",
    "background_orchard_sunset",
    "background_orchard_night"
  ];
  var images = {};
  function loadAll(onProgress, onDone) {
    const files = [
      ...BG_FILES.map((f) => "assets/sprites/".concat(f, ".jpg")),
      ...SPRITE_FILES.map((f) => "assets/sprites/".concat(f, ".png"))
    ];
    let done = 0;
    for (const f of files) {
      const img = wx.createImage();
      img.onload = function() {
        images[f.replace("assets/sprites/", "").replace(/\.(png|jpg)$/, "")] = img;
        done++;
        onProgress(done, files.length);
        if (done === files.length) onDone();
      };
      img.onerror = function() {
        done++;
        if (done === files.length) onDone();
      };
      img.src = f;
    }
  }
  function backgroundFileFor(phaseMs, bgMode2) {
    return bgFileFor(bgMode2, phaseMs).replace("assets/sprites/", "").replace(".jpg", "");
  }
  function bgFileFor(bgMode2, phaseMs) {
    if (bgMode2 === "day") return "background";
    if (bgMode2 === "sunset") return "background_sunset";
    if (bgMode2 === "night") return "background_night";
    const phase = Math.floor(phaseMs / 6e4) % 6;
    return BG_FILES[phase];
  }
  var prevBg = null;
  var shownBg = null;
  var fadeStart = -1;
  function drawBackground(ctx2, vw, vh, bgMode2, phaseMs, now) {
    const file = bgFileFor(bgMode2, phaseMs);
    const img = images[file];
    if (!img) {
      ctx2.fillStyle = "#7ab6f0";
      ctx2.fillRect(0, 0, vw, vh);
      return;
    }
    if (img !== shownBg) {
      prevBg = shownBg;
      shownBg = img;
      fadeStart = prevBg ? now : -1;
    }
    ctx2.drawImage(img, 0, 0, vw, vh);
    const prev = prevBg;
    if (prev && fadeStart > 0) {
      const fade = (now - fadeStart) / C.BG_FADE_MS;
      if (fade >= 1) {
        prevBg = null;
        fadeStart = -1;
      } else {
        ctx2.globalAlpha = 1 - fade;
        ctx2.drawImage(prev, 0, 0, vw, vh);
        ctx2.globalAlpha = 1;
      }
    }
  }
  function groundYFor(vw, vh, bgMode2, phaseMs) {
    var _a;
    const file = bgFileFor(bgMode2, phaseMs).replace("assets/sprites/", "").replace(".jpg", "");
    const frac = (_a = C.BG_GROUND_FRACTIONS[file]) != null ? _a : 0.8;
    return vh * frac;
  }
  function drawSprite(ctx2, img, cx, cy, size) {
    if (!img) {
      ctx2.fillStyle = "#ccc";
      ctx2.beginPath();
      ctx2.arc(cx, cy, size / 2, 0, 7);
      ctx2.fill();
      return;
    }
    ctx2.drawImage(img, cx - size / 2, cy - size / 2, size, size);
  }
  function roundRect(ctx2, x, y, w, h, r) {
    ctx2.beginPath();
    ctx2.moveTo(x + r, y);
    ctx2.arcTo(x + w, y, x + w, y + h, r);
    ctx2.arcTo(x + w, y + h, x, y + h, r);
    ctx2.arcTo(x, y + h, x, y, r);
    ctx2.arcTo(x, y, x + w, y, r);
    ctx2.closePath();
  }

  // src/main.ts
  var canvas;
  var ctx;
  var W = 0;
  var H = 0;
  var dpr = 1;
  var page = "LOADING";
  var bgMode = "auto";
  var jumpOnLeft = false;
  var nickname = "\u73A9\u5BB6";
  var world = new GameWorld();
  var openData = wx.getOpenDataContext();
  var buttons = {};
  function setBtn(k, r) {
    buttons[k] = r;
  }
  function hit2(r, x, y) {
    return !!r && x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h;
  }
  function loadSettings() {
    bgMode = wx.getStorageSync("bg") || "auto";
    jumpOnLeft = !!wx.getStorageSync("jumpLeft");
    nickname = wx.getStorageSync("nickname") || "\u73A9\u5BB6";
  }
  function bestScore() {
    return wx.getStorageSync("best") || 0;
  }
  function saveBest(s) {
    wx.setStorageSync("best", s);
  }
  function records() {
    try {
      return wx.getStorageSync("records") || [];
    } catch (e) {
      return [];
    }
  }
  function addRecord(score) {
    if (score <= 0) return;
    const list = records();
    const d = /* @__PURE__ */ new Date();
    const date = "".concat(d.getFullYear(), "/").concat(d.getMonth() + 1, "/").concat(d.getDate());
    list.unshift({ score, date });
    wx.setStorageSync("records", list.slice(0, 50));
  }
  function applyAudio() {
    const p = page;
    if (p === "MENU" || p === "SETTINGS" || p === "HELP" || p === "LEADERBOARD") startBgm("MENU");
    else if (p === "GAME" || p === "PAUSE" || p === "TUTORIAL") startBgm("GAME");
    else stopBgm();
  }
  function stopAllBgm() {
    stopBgm();
  }
  var moveTouchId = null;
  var moveLastX = 0;
  var jumpHoldUntil = 0;
  var audioArmed = false;
  wx.onTouchStart(function(e) {
    if (!audioArmed) {
      audioArmed = true;
      applyAudio();
    }
    const t = e.touches[0];
    if (!t) return;
    const x = t.clientX, y = t.clientY;
    if (page === "GAME") {
      if (hit2(buttons.jumpBtn, x, y)) {
        world.performPlayerAction();
        jumpHoldUntil = Date.now() + 300;
        return;
      }
      if (hit2(buttons.moveZone, x, y) || y > H - 300 && !hit2(buttons.jumpBtn, x, y)) {
        moveTouchId = t.identifier;
        moveLastX = x;
      }
      return;
    }
    if (page === "TUTORIAL") {
      wx.setStorageSync("tutorialShown", true);
      startGame();
      return;
    }
    if (page === "MENU") {
      if (hit2(buttons.start, x, y)) {
        sfx("CLICK");
        startFromMenu();
        return;
      }
      if (hit2(buttons.leaderboard, x, y)) {
        sfx("CLICK");
        showPage("LEADERBOARD");
        return;
      }
      if (hit2(buttons.settings, x, y)) {
        sfx("CLICK");
        showPage("SETTINGS");
        return;
      }
      if (hit2(buttons.help, x, y)) {
        sfx("CLICK");
        showPage("HELP");
        return;
      }
      if (hit2(buttons.exit, x, y)) {
        sfx("CLICK");
        wx.exitMiniProgram();
        return;
      }
    } else if (page === "PAUSE") {
      if (hit2(buttons.resume, x, y)) {
        resumeGame();
        return;
      }
      if (hit2(buttons.settings2, x, y)) {
        showPage("SETTINGS");
        return;
      }
      if (hit2(buttons.help2, x, y)) {
        showPage("HELP");
        return;
      }
    } else if (page === "GAME_OVER") {
      if (hit2(buttons.leaderboard2, x, y)) {
        showPage("LEADERBOARD");
        return;
      }
      if (hit2(buttons.restart, x, y)) {
        restartGame();
        return;
      }
      if (hit2(buttons.mainmenu, x, y)) {
        showMenu();
        return;
      }
    } else if (page === "SETTINGS") {
      if (hit2(buttons.back, x, y)) {
        backFromPage();
        return;
      }
      if (hit2(buttons.rename, x, y)) {
        editNickname();
        return;
      }
      for (let i = 0; i < 3; i++) if (hit2(buttons["lang" + i], x, y)) {
        setLanguage(i);
        return;
      }
      for (let i = 0; i < 4; i++) if (hit2(buttons["bg" + i], x, y)) {
        setBg(i);
        return;
      }
      if (hit2(buttons.jumpSide, x, y)) {
        jumpOnLeft = !jumpOnLeft;
        wx.setStorageSync("jumpLeft", jumpOnLeft);
      }
    } else if (page === "HELP") {
      if (hit2(buttons.back, x, y)) {
        backFromPage();
        return;
      }
    } else if (page === "LEADERBOARD") {
      if (hit2(buttons.back, x, y)) {
        backFromPage();
        return;
      }
    }
  });
  wx.onTouchMove(function(e) {
    if (page !== "GAME" || moveTouchId === null) return;
    for (const t of e.changedTouches) {
      if (t.identifier === moveTouchId) {
        world.dragPlayerBy(t.clientX - moveLastX);
        moveLastX = t.clientX;
      }
    }
  });
  wx.onTouchEnd(function(e) {
    for (const t of e.changedTouches) if (t.identifier === moveTouchId) moveTouchId = null;
  });
  function editNickname() {
    wx.showKeyboard({ defaultValue: nickname, maxLength: 20 });
    wx.onKeyboardInput(function handler(res) {
      nickname = (res.value || "").trim() || "\u73A9\u5BB6";
      wx.offKeyboardInput(handler);
    });
    wx.onKeyboardComplete(function handler() {
      wx.setStorageSync("nickname", nickname);
      wx.offKeyboardComplete(handler);
    });
  }
  function setLanguage(i) {
    wx.setStorageSync("langPref", ["system", "en", "zh"][i]);
  }
  function setBg(i) {
    bgMode = ["auto", "day", "sunset", "night"][i];
    wx.setStorageSync("bg", bgMode);
  }
  function showMenu() {
    hideOverlays();
    setPage("MENU");
  }
  function backFromPage() {
    if (pauseVisible) {
      showPage("PAUSE");
      applyAudio();
    } else showMenu();
  }
  function startFromMenu() {
    sfx("CLICK");
    if (!wx.getStorageSync("tutorialShown")) {
      showPage("TUTORIAL");
      applyAudio();
    } else startGame();
  }
  function startGame() {
    world.reset();
    showPage("GAME");
    applyAudio();
    world.refreshHud();
  }
  function restartGame() {
    gameOverHide();
    startGame();
  }
  function resumeGame() {
    pauseVisible = false;
    page = "GAME";
    applyAudio();
  }
  var pauseVisible = false;
  var gameOverVisible = false;
  function gameOverHide() {
    gameOverVisible = false;
  }
  function hideOverlays() {
    pauseVisible = false;
    gameOverVisible = false;
  }
  var lastScoreSubmitted = -1;
  world.listener = {
    onHudChanged(score, lives, pts) {
      hud = { score, lives, pts };
    },
    onVehicleChanged(riding, hits, seconds) {
      veh = { riding, hits, seconds };
    },
    onGameOver(score) {
      stopAllBgm();
      gameOverVisible = true;
      showPage("GAME_OVER");
      const best = Math.max(bestScore(), score);
      saveBest(best);
      addRecord(score);
      lastScoreSubmitted = score;
      try {
        wx.setUserCloudStorage({
          KVDataList: [{ key: "score", value: String(score) }]
        });
      } catch (e) {
      }
    }
  };
  var hud = { score: 0, lives: C.START_LIVES, pts: C.POINTS_PER_EXTRA_LIFE };
  var veh = { riding: false, hits: 0, seconds: 0 };
  var INK = "#0B3D2E";
  var CREAM = "#FFF6E3";
  var AMBER = "#F59E0B";
  var GOLD = "#FCD34D";
  function draw() {
    if (page === "LOADING") {
      ctx.fillStyle = "#7AB6F0";
      ctx.fillRect(0, 0, W, H);
      ctx.fillStyle = "#fff";
      ctx.font = "bold 28px sans-serif";
      ctx.textAlign = "center";
      ctx.fillText("\u52A0\u8F7D\u4E2D " + Math.round(W) + "x" + Math.round(H), W / 2, H / 2);
      ctx.textAlign = "left";
      return;
    }
    const now = Date.now();
    const phase = world.gameTimeMs;
    const gY = groundYFor(W, H, bgMode, phase);
    world.groundY = gY;
    if (page === "MENU") {
      drawMenu(now);
      return;
    }
    drawBackground(ctx, W, H, bgMode, phase, now);
    for (const it of world.items) {
      const img = images[it.kind.toLowerCase()];
      ctx.strokeStyle = it.kind === "BEAN" || it.kind === "MEAT" || it.kind === "PIE" ? "#22C55E" : "#EF4444";
      ctx.lineWidth = it.sizePx * 0.09;
      ctx.beginPath();
      ctx.arc(it.x, it.y, it.sizePx * 0.62, 0, 7);
      ctx.stroke();
      drawSprite(ctx, img, it.x, it.y, it.sizePx);
    }
    for (const b of world.beasts) {
      const img = images[b.kind.toLowerCase()];
      drawSprite(ctx, img, b.x, gY - b.sizePx / 2 - b.heightOffset(H), b.sizePx);
    }
    for (const d of world.returningNpcs) {
      ctx.fillStyle = "#FFD34D";
      ctx.fillRect(d.x - d.sizePx / 2, gY - d.sizePx * 2, d.sizePx, d.sizePx * 2);
    }
    if (world.tankDelivery) {
      const d = world.tankDelivery;
      drawSprite(ctx, images.tank, d.x, gY - d.sizePx / 2, d.sizePx);
    }
    for (const b of world.bullets) {
      ctx.fillStyle = "#0B3D2E";
      ctx.beginPath();
      ctx.arc(b.x, b.y, b.sizePx, 0, 7);
      ctx.fill();
    }
    const p = world.player;
    if (!(!p.riding && p.isInvincible(world.gameTimeMs) && Math.floor(world.gameTimeMs / 120) % 2 === 0)) {
      if (p.riding) drawSprite(ctx, images.tank, p.x, gY - p.sizePx * 0.35, p.sizePx * 1.6);
      drawSprite(ctx, images.player, p.x, p.displayCenterY(gY, world.gameTimeMs, H), p.displaySizePx);
    }
    canvas.__lastDrawTs = Date.now();
    if (page === "SETTINGS") {
      drawSettings();
      return;
    }
    if (page === "HELP") {
      drawHelpPage();
      return;
    }
    if (page === "LEADERBOARD") {
      drawLeaderboardPage();
      return;
    }
    var scale = W / 390;
    var bh = C.UI.CONTROLS_HEIGHT * 0.9 * scale;
    var jumpW = C.UI.JUMP_AREA_WIDTH * scale;
    var inset = C.UI.CONTROL_INSET * scale;
    var by = H - bh - inset - 6;
    var moveR, jumpR;
    if (jumpOnLeft) {
      jumpR = { x: inset, y: by, w: jumpW, h: bh };
      moveR = { x: inset + jumpW + inset, y: by, w: W - jumpW - inset * 3, h: bh };
    } else {
      moveR = { x: inset, y: by, w: W - jumpW - inset * 3, h: bh };
      jumpR = { x: W - jumpW - inset, y: by, w: jumpW, h: bh };
    }
    setBtn("moveZone", moveR);
    setBtn("jumpBtn", jumpR);
    var p2 = world.player;
    var jumpLabel = p2.riding ? "\u5C04\u51FB \xD7" + p2.ammoRemaining : "\u8DF3\u8DC3";
    drawCandyIcon(moveR, images.ui_move, p2.riding ? "#78CBA6" : "#FFFDF4", "#0B3D2E", INK, "\u6ED1\u52A8\u79FB\u52A8");
    drawCandyIcon(jumpR, images.ui_tap, "#F3BC36", "#0B3D2E", INK, jumpLabel);
    if (p2.riding) {
      ctx.fillStyle = INK;
      ctx.font = "bold 16px sans-serif";
      ctx.textAlign = "center";
      ctx.fillText("\u5766\u514B\u5F85\u547D", W / 2, by - 10);
      ctx.textAlign = "left";
    }
    drawHud();
    if (page === "PAUSE") drawPause();
    if (page === "GAME_OVER") drawGameOver();
    if (page === "TUTORIAL") drawTutorial();
  }
  function drawHud() {
    ctx.fillStyle = "rgba(16,23,36,0.35)";
    roundRect(ctx, 12, 12, 250, 92, 18);
    ctx.fill();
    ctx.fillStyle = "#fff";
    ctx.font = "bold 22px Baloo 2, sans-serif";
    ctx.fillText("\u5206\u6570 " + hud.score, 28, 44);
    var heart = images.ui_heart;
    if (heart) ctx.drawImage(heart, 28, 56, 18, 18);
    ctx.font = "16px Baloo 2, sans-serif";
    ctx.fillText(String(hud.lives), heart ? 52 : 28, 72);
    ctx.fillText("\u8FD8\u5DEE " + hud.pts + " \u5206\u9001\u547D", 28, 96);
    if (veh.riding) {
      ctx.fillStyle = GOLD;
      ctx.fillText("\u9A91\u4E58\u4E2D \xB7 \u5269 ".concat(veh.seconds, " \u79D2 \xB7 \u649E\u51FB ").concat(veh.hits, "/3"), 28, 122);
    }
    circleIcon(W - 120, 46, images.ui_pause, function() {
      pauseGame();
    });
    circleIcon(W - 52, 46, images.ui_settings, function() {
      openSettings();
    });
  }
  var btnRects = {};
  function circleIcon(x, y, img, onTap) {
    ctx.fillStyle = "rgba(16,23,36,0.35)";
    ctx.beginPath();
    ctx.arc(x, y, 26, 0, 7);
    ctx.fill();
    if (img) ctx.drawImage(img, x - 14, y - 14, 28, 28);
    btnRects["ic" + x] = { x: x - 30, y: y - 30, w: 60, h: 60 };
    buttons["ic" + x] = { x: x - 30, y: y - 30, w: 60, h: 60 };
  }
  function drawMenu(_now) {
    const hero = images.menu_hero;
    if (hero) ctx.drawImage(hero, 0, 0, W, H * 0.62);
    ctx.fillStyle = "rgba(11,61,46,0.92)";
    ctx.fillRect(0, H * 0.58, W, H * 0.42);
    const emblem = images.title_emblem;
    ctx.fillStyle = "#fff";
    ctx.font = "bold 64px Baloo 2, sans-serif";
    ctx.textAlign = "center";
    ctx.fillText("HappyEat", W / 2, H * 0.64);
    ctx.font = "18px sans-serif";
    ctx.fillStyle = GOLD;
    ctx.fillText("\u63A5\u4F4F\u7F8E\u5473 \xB7 \u8EB2\u5F00\u5371\u9669", W / 2, H * 0.64 + 34);
    ctx.textAlign = "left";
    const bx = W * 0.08, bw = W * 0.84;
    setBtn("start", { x: bx, y: H * 0.74, w: bw, h: 64 });
    drawCandy(buttons.start, AMBER, "#FDE68A", INK, "\u25B6 \u5F00\u59CB\u6E38\u620F");
    setBtn("leaderboard", { x: bx, y: H * 0.74 + 80, w: bw, h: 56 });
    drawCandy(buttons.leaderboard, CREAM, "#D9C9A3", INK, "\u{1F3C6} \u6392\u884C\u699C");
    setBtn("settings", { x: bx, y: H * 0.74 + 148, w: bw, h: 56 });
    drawCandy(buttons.settings, CREAM, "#D9C9A3", INK, "\u2699 \u8BBE\u7F6E");
    setBtn("help", { x: bx, y: H * 0.74 + 216, w: bw, h: 56 });
    drawCandy(buttons.help, CREAM, "#D9C9A3", INK, "\u2753 \u5E2E\u52A9");
    setBtn("exit", { x: bx, y: H * 0.74 + 284, w: bw, h: 56 });
    drawCandy(buttons.exit, 1089422404, "#EF4444", "#FCA5A5", "\u21AA \u9000\u51FA\u6E38\u620F");
    ctx.textAlign = "left";
  }
  function drawCandyIcon(r, icon, base, stroke, color, label) {
    ctx.fillStyle = "#D9C9A3";
    roundRect(ctx, r.x, r.y + 5, r.w, r.h, r.h / 2);
    ctx.fill();
    ctx.fillStyle = base;
    roundRect(ctx, r.x, r.y, r.w, r.h - 5, (r.h - 5) / 2);
    ctx.fill();
    ctx.strokeStyle = stroke;
    ctx.lineWidth = 2;
    roundRect(ctx, r.x, r.y, r.w, r.h - 5, (r.h - 5) / 2);
    ctx.stroke();
    if (icon) ctx.drawImage(icon, r.x + 18, r.y + (r.h - 5) / 2 - 14, 28, 28);
    ctx.fillStyle = color;
    ctx.font = "bold 22px Baloo 2, sans-serif";
    ctx.textAlign = "center";
    ctx.fillText(label, r.x + r.w / 2 + 14, r.y + r.h / 2 - 5 + 8);
    ctx.textAlign = "left";
  }
  function drawCandy(r, base, stroke, color, label) {
    ctx.fillStyle = "#D9C9A3";
    roundRect(ctx, r.x, r.y + 5, r.w, r.h, r.h / 2);
    ctx.fill();
    ctx.fillStyle = base;
    roundRect(ctx, r.x, r.y, r.w, r.h - 5, (r.h - 5) / 2);
    ctx.fill();
    ctx.strokeStyle = stroke;
    ctx.lineWidth = 2;
    roundRect(ctx, r.x, r.y, r.w, r.h - 5, (r.h - 5) / 2);
    ctx.stroke();
    ctx.fillStyle = color;
    ctx.font = "bold 22px Baloo 2, sans-serif";
    ctx.textAlign = "center";
    ctx.fillText(label, r.x + r.w / 2, r.y + r.h / 2 - 5 + 8);
    ctx.textAlign = "left";
  }
  function drawPanelCard(title) {
    ctx.fillStyle = "rgba(11,61,46,0.92)";
    ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = CREAM;
    roundRect(ctx, W * 0.07, H * 0.16, W * 0.86, H * 0.68, 30);
    ctx.fill();
    ctx.strokeStyle = INK;
    ctx.lineWidth = 3;
    roundRect(ctx, W * 0.07, H * 0.16, W * 0.86, H * 0.68, 30);
    ctx.stroke();
    ctx.fillStyle = INK;
    ctx.font = "bold 30px Baloo 2, sans-serif";
    ctx.fillText(title, W * 0.12, H * 0.24);
  }
  function drawPause() {
    drawPanelCard("\u5DF2\u6682\u505C");
    const b1 = { x: W * 0.3, y: H * 0.4, w: W * 0.4, h: 60 };
    const b2 = { x: W * 0.3, y: H * 0.5, w: W * 0.4, h: 60 };
    const b3 = { x: W * 0.3, y: H * 0.6, w: W * 0.4, h: 60 };
    setBtn("resume", b1);
    setBtn("settings2", b2);
    setBtn("help2", b3);
    drawCandy(b1, AMBER, "#FDE68A", INK, "\u25B6 \u7EE7\u7EED");
    drawCandy(b2, CREAM, "#D9C9A3", INK, "\u2699 \u8BBE\u7F6E");
    drawCandy(b3, CREAM, "#D9C9A3", INK, "\u2753 \u5E2E\u52A9");
  }
  function drawGameOver() {
    ctx.fillStyle = "rgba(11,61,46,0.92)";
    ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = "#fff";
    ctx.font = "bold 44px Baloo 2, sans-serif";
    ctx.textAlign = "center";
    ctx.fillText("\u6E38\u620F\u7ED3\u675F", W / 2, H * 0.3);
    ctx.font = "24px sans-serif";
    ctx.fillText("\u672C\u5C40\u5F97\u5206 " + world.score, W / 2, H * 0.4);
    ctx.fillText("\u6700\u9AD8\u5206 " + bestScore(), W / 2, H * 0.47);
    ctx.textAlign = "left";
    const bw = W * 0.5, bx = W * 0.25;
    setBtn("leaderboard2", { x: bx, y: H * 0.55, w: bw, h: 60 });
    drawCandy(buttons.leaderboard2, CREAM, "#D9C9A3", INK, "\u{1F3C6} \u6392\u884C\u699C");
    setBtn("restart", { x: bx, y: H * 0.65, w: bw, h: 60 });
    drawCandy(buttons.restart, AMBER, "#FDE68A", INK, "\u21BB \u91CD\u65B0\u5F00\u59CB");
    setBtn("mainmenu", { x: bx, y: H * 0.75, w: bw, h: 60 });
    drawCandy(buttons.mainmenu, CREAM, "#D9C9A3", INK, "\u2302 \u4E3B\u83DC\u5355");
    ctx.textAlign = "left";
  }
  function drawTutorial() {
    ctx.fillStyle = "rgba(11,61,46,0.9)";
    ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = "#fff";
    ctx.font = "bold 26px sans-serif";
    ctx.textAlign = "center";
    ctx.fillText("\u2B05 \u6309\u4F4F\u62D6\u52A8\u79FB\u52A8 \u27A1", W / 2, H * 0.38);
    ctx.fillText("\u2B06 \u5FEB\u901F\u70B9\u6309\u8DF3\u8DC3", W / 2, H * 0.48);
    ctx.font = "18px sans-serif";
    ctx.fillStyle = GOLD;
    ctx.fillText("\u8F7B\u89E6\u4EFB\u610F\u5904\u5F00\u59CB", W / 2, H * 0.58);
    ctx.textAlign = "left";
  }
  function drawLeaderboardPage() {
    ctx.fillStyle = "#FFF6E3";
    ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = INK;
    ctx.font = "bold 34px Baloo 2, sans-serif";
    ctx.fillText("\u6392\u884C\u699C", 40, 80);
    circleBack(60, 60);
    renderFriendRank();
  }
  function drawHelpPage() {
    ctx.fillStyle = "#FFF6E3";
    ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = INK;
    ctx.font = "bold 34px Baloo 2, sans-serif";
    ctx.fillText("\u5E2E\u52A9", 40, 80);
    circleBack(60, 60);
    var rows = [
      ["bean.png", "\u8C46\u5B50\uFF1A+1 \u5206\uFF08\u7EFF\u5708=\u80FD\u5403\uFF09"],
      ["meat.png", "\u8089\uFF1A+5 \u5206"],
      ["pie.png", "\u9985\u997C\uFF1A+10 \u5206"],
      ["car.png", "\u8F66/\u5766\u514B\uFF1A\u9A91\u4E58\u6321\u4F24\u5BB3\uFF08\u9EC4\u5708\uFF09"],
      ["bomb.png", "\u77F3\u5934/\u70B8\u5F39/\u5200\uFF1A\u78B0\u5230\u6389\u547D\uFF0C\u5FEB\u8EB2\uFF01\uFF08\u7EA2\u5708\uFF09"],
      ["tiger.png", "\u731B\u517D\u8D34\u5730\u51B2\u6765\u2014\u2014\u5FEB\u901F\u70B9\u6309\u8DF3\u8DC3\u8EB2\u8FC7"]
    ];
    var y = 130;
    rows.forEach(function(rw) {
      var img = images[rw[0].replace(".png", "")];
      if (img) ctx.drawImage(img, 40, y - 8, 44, 44);
      ctx.fillStyle = INK;
      ctx.font = "16px sans-serif";
      ctx.fillText(rw[1], 100, y + 18);
      y += 58;
    });
    ctx.fillStyle = Candy.AMBER_DARK;
    ctx.font = "bold 17px sans-serif";
    ctx.fillText("\u6ED1\u52A8\u79FB\u52A8 \xB7 \u5FEB\u901F\u70B9\u6309\u8DF3\u8DC3", 40, y + 24);
  }
  function drawSettings() {
    ctx.fillStyle = "#FFF6E3";
    ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = INK;
    ctx.font = "bold 34px Baloo 2, sans-serif";
    ctx.fillText("\u8BBE\u7F6E", 40, 80);
    circleBack(60, 60);
    let y = 140;
    ctx.font = "bold 22px sans-serif";
    ctx.fillText("\u5F53\u524D\u7528\u6237\u540D: " + nickname, 40, y);
    y += 50;
    const rn = { x: 40, y, w: W - 80, h: 56 };
    setBtn("rename", rn);
    drawCandy(rn, CREAM, "#D9C9A3", INK, "\u2699 \u4FEE\u6539\u7528\u6237\u540D");
    y += 90;
    ctx.font = "bold 22px sans-serif";
    ctx.fillText("\u8BED\u8A00 / Language", 40, y);
    y += 40;
    const langs = ["\u8DDF\u968F\u7CFB\u7EDF", "English", "\u4E2D\u6587"];
    langs.forEach(function(l, i) {
      const r = { x: 40, y, w: W - 80, h: 52 };
      setBtn("lang" + i, r);
      drawOptionRow(r, l, i === langSel);
      y += 64;
    });
    y += 20;
    ctx.font = "bold 22px sans-serif";
    ctx.fillText("\u80CC\u666F", 40, y);
    y += 40;
    const bgs = ["\u81EA\u52A8\uFF08\u968F\u65F6\u95F4\u53D8\u5316\uFF09", "\u767D\u5929", "\u9EC4\u660F", "\u591C\u665A"];
    const bgVals = ["auto", "day", "sunset", "night"];
    bgs.forEach(function(l, i) {
      const r = { x: 40, y, w: W - 80, h: 52 };
      setBtn("bg" + i, r);
      drawOptionRow(r, l, bgMode === bgVals[i]);
      y += 64;
    });
  }
  var langSel = 0;
  function drawOptionRow(r, label, selected) {
    ctx.fillStyle = selected ? "rgba(245,158,11,0.18)" : "rgba(11,61,46,0.08)";
    roundRect(ctx, r.x, r.y, r.w, r.h, 18);
    ctx.fill();
    ctx.strokeStyle = selected ? AMBER : "rgba(11,61,46,0.2)";
    ctx.lineWidth = 2;
    roundRect(ctx, r.x, r.y, r.w, r.h, 18);
    ctx.stroke();
    ctx.fillStyle = INK;
    ctx.font = "18px sans-serif";
    ctx.fillText(label, r.x + 18, r.y + 34);
    if (selected) {
      ctx.fillStyle = AMBER;
      ctx.fillText("\u2713", r.x + r.w - 40, r.y + 34);
    }
  }
  function circleBack(x, y) {
    ctx.fillStyle = "rgba(11,61,46,0.15)";
    ctx.beginPath();
    ctx.arc(x, y, 28, 0, 7);
    ctx.fill();
    ctx.fillStyle = INK;
    ctx.font = "bold 20px sans-serif";
    ctx.textAlign = "center";
    ctx.fillText("\u25C0", x, y + 8);
    ctx.textAlign = "left";
    setBtn("back", { x: x - 32, y: y - 32, w: 64, h: 64 });
  }
  var lastTs = 0;
  var drawErrShown = false;
  var loopAnnounced = false;
  function loop(ts) {
    var _a;
    if (!loopAnnounced) {
      loopAnnounced = true;
      console.error("[HappyEat] loop frame 1 running, page=" + page);
    }
    const dt = lastTs ? Math.min(50, ts - lastTs) : 16;
    lastTs = ts;
    if (page === "GAME" && !gameOverFrozen()) {
      world.update(dt);
      world.groundFraction = (_a = C.BG_GROUND_FRACTIONS[backgroundFileFor(world.gameTimeMs, bgMode)]) != null ? _a : 0.8;
      world.groundY = H * world.groundFraction;
    }
    try {
      draw();
    } catch (e) {
      if (!drawErrShown) {
        drawErrShown = true;
        console.error("[HappyEat] draw error:", e.stack || e);
      }
    }
    requestAnimationFrame(loop);
  }
  function gameOverFrozen() {
    return world.gameOver;
  }
  function pauseGame() {
    if (page !== "GAME") return;
    showPage("PAUSE");
    pauseVisible = true;
  }
  function openSettings() {
    if (page === "GAME") {
      pauseVisible = true;
      gameViewBehind = true;
    }
    showPage("SETTINGS");
  }
  var gameViewBehind = false;
  function setPage(p) {
    page = p;
  }
  function showPage(p) {
    setPage(p);
    applyAudio();
  }
  function startGameBoot() {
    var _a;
    canvas = wx.createCanvas();
    const info = wx.getSystemInfoSync();
    dpr = Math.min(info.pixelRatio || 2, 2);
    W = info.screenWidth;
    H = info.screenHeight;
    canvas.width = W * dpr;
    canvas.height = H * dpr;
    ctx = canvas.getContext("2d");
    ctx.scale(dpr, dpr);
    loadSettings();
    world.bgIndex = 0;
    world.groundFraction = (_a = C.BG_GROUND_FRACTIONS[backgroundFileFor(0, bgMode)]) != null ? _a : 0.8;
    world.onSize(W, H);
    loadAll(function(d, t) {
    }, function() {
      showPage("MENU");
      applyAudio();
    });
    console.error("[HappyEat] boot OK: canvas", W, "x", H, "dpr", dpr);
    requestAnimationFrame(loop);
    initShare();
    showPage("LOADING");
  }
  function initShare() {
    wx.showShareMenu({ withShareTicket: false });
    wx.onShareAppMessage(function() {
      return {
        title: "HappyEat \u2014 \u63A5\u4F4F\u7F8E\u5473\uFF0C\u8EB2\u5F00\u5371\u9669\uFF01\u6211\u7684\u6700\u9AD8\u5206 " + bestScore() + " \u5206",
        imageUrl: "assets/sprites/title_emblem.png"
      };
    });
  }
  function renderFriendRank() {
    openData.postMessage({ type: "render", width: Math.round(W * 0.86), height: Math.round(H * 0.5), selfName: nickname });
    const c = openData.canvas;
    ctx.drawImage(c, W * 0.07, H * 0.2, W * 0.86, H * 0.5);
  }
  startGameBoot();
})();
