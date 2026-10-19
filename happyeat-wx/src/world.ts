/** GameWorld v5 忠实移植（367 行 Kotlin → TS） */
import { C, ItemKind, BeastKind } from "./config";
import { Aabb, intersects, sweptHit, Player, Beast, FallingItem, Bullet, TankDelivery, ReturningNpc } from "./entities";

import { sfx } from "./audio";

export interface WorldListener {
  onHudChanged(score: number, lives: number, pointsToNextLife: number): void;
  onVehicleChanged(riding: boolean, hits: number, seconds: number, ammo: number, supplyVersion: number): void;
  onGameOver(score: number): void;
}

interface HitRec { time: number; bullet: Bullet; beast: Beast; }

export type BeastT = Beast & { __k: BeastKind };

export class GameWorld {
  listener: WorldListener | null = null;
  viewW = 0; viewH = 0; groundY = 0;
  player = new Player();
  items: FallingItem[] = [];
  beasts: Beast[] = [];
  tankDelivery: TankDelivery | null = null;
  returningNpcs: ReturningNpc[] = [];
  bullets: Bullet[] = [];
  score = 0;
  lives = C.START_LIVES;
  gameOver = false;
  gameTimeMs = 0;
  spawnEnabled = true;

  private nextExtraLifeAt = C.POINTS_PER_EXTRA_LIFE;
  private fallTimerMs = 0;
  private beastTimerMs = 0;
  private previousFrameScore = 0;

  private hudRiding = false; private hudSeconds = 0; private hudHits = 0; private hudAmmo = 0; private hudSupplyVersion = 0;

  get difficulty(): number {
    return 1 + (C.DIFFICULTY_MAX_MULTIPLIER - 1) *
      Math.min(1, this.gameTimeMs / C.DIFFICULTY_RAMP_MS);
  }

  groundFraction = 0.8;
  onSize(w: number, h: number) {
    this.viewW = w; this.viewH = h;
    this.groundY = h * this.groundFraction;
    this.player.sizePx = C.PLAYER_SIZE * w;
    this.clampPlayer();
  }
  bgIndex = 0;

  refreshHud() { this.publishVehicleHud(true); }

  reset() {
    this.items.length = 0; this.beasts.length = 0; this.bullets.length = 0;
    this.returningNpcs.length = 0; this.tankDelivery = null;
    this.score = 0; this.lives = C.START_LIVES; this.gameOver = false; this.gameTimeMs = 0;
    this.nextExtraLifeAt = C.POINTS_PER_EXTRA_LIFE; this.fallTimerMs = 0; this.beastTimerMs = 0;
    this.previousFrameScore = 0; this.spawnEnabled = true;
    this.player.resetState();
    if (this.viewW > 0) { this.player.x = this.viewW / 2; this.clampPlayer(); }
    this.publishVehicleHud(true);
    this.listener?.onHudChanged(this.score, this.lives, this.nextExtraLifeAt - this.score);
  }

  dragPlayerBy(dx: number) {
    if (this.viewW === 0) return;
    this.player.x += dx;
    this.clampPlayer();
  }
  private clampPlayer() {
    const half = (this.player.riding ? C.TANK_SIZE * this.viewW : this.player.sizePx) / 2;
    this.player.x = Math.min(Math.max(this.player.x, half), this.viewW - half);
  }

  performPlayerAction() {
    if (this.gameOver || this.viewW === 0) return;
    this.player.tickVehicle(this.gameTimeMs);
    if (this.player.riding) {
      if (this.player.ammoRemaining > 0) {
        const beast = this.beasts[0];
        const direction = beast ? -beast.direction : (this.player.lastFireDirection ?? 1);
        this.player.lastFireDirection = direction;
        this.player.ammoRemaining--;
        this.bullets.push(new Bullet(
          this.player.x + direction * C.BULLET_MUZZLE * this.viewW,
          this.groundY - C.BULLET_HEIGHT * this.viewW, direction,
          C.BULLET_SIZE * this.viewW, C.BULLET_SPEED * this.viewW));
      }
    } else if (!this.player.isAirborne(this.gameTimeMs)) {
      this.player.jump(this.gameTimeMs);
      sfx("JUMP");
    }
    this.publishVehicleHud();
  }

  private updateDelivery(dt: number) {
    for (const npc of this.returningNpcs) npc.x += npc.direction * C.NPC_SPEED * this.viewW * dt;
    this.returningNpcs = this.returningNpcs.filter(n => n.x > -n.sizePx && n.x < this.viewW + n.sizePx);
    const delivery = this.tankDelivery;
    if (!delivery) return;
    if (delivery.update(dt, this.gameTimeMs)) {
      this.returningNpcs.push(new ReturningNpc(delivery.x, -delivery.direction, C.NPC_SIZE * this.viewW));
    }
    const stopped = delivery.stoppedAtMs;
    if (stopped === null) return;
    if (this.gameTimeMs - stopped >= C.TANK_WAIT_MS) this.tankDelivery = null;
    else if (!this.player.isAirborne(this.gameTimeMs) &&
      Math.abs(this.player.x - delivery.x) <
        ((this.player.riding ? C.TANK_SIZE * this.viewW : this.player.displaySizePx) + delivery.sizePx) * C.HITBOX_SCALE / 2) {
      this.player.mount(this.gameTimeMs, delivery.direction);
      this.clampPlayer();
      this.tankDelivery = null;
      sfx("MOUNT");
    }
  }

  update(dtMs: number) {
    if (this.gameOver || this.viewW === 0) return;
    this.gameTimeMs += dtMs;
    const dt = dtMs / 1000;
    const diff = this.difficulty;
    const frameScore = this.score;
    const restartBeastTimer =
      (this.previousFrameScore <= C.BEAST_SCORE_THRESHOLD && frameScore > C.BEAST_SCORE_THRESHOLD) ||
      (this.previousFrameScore <= C.BEAST_FREQUENCY_SCORE_THRESHOLD && frameScore > C.BEAST_FREQUENCY_SCORE_THRESHOLD);
    this.previousFrameScore = frameScore;
    if (frameScore <= C.BEAST_SCORE_THRESHOLD || restartBeastTimer) this.beastTimerMs = 0;

    if (this.spawnEnabled) {
      this.fallTimerMs += dtMs;
      const fallInterval = Math.max(50, C.FALL_SPAWN_INTERVAL_MS / diff);
      while (this.fallTimerMs >= fallInterval) { this.fallTimerMs -= fallInterval; this.spawnItem(frameScore); }

      if (frameScore > C.BEAST_SCORE_THRESHOLD && !restartBeastTimer) {
        this.beastTimerMs += dtMs;
        const progress = Math.min(1, (frameScore - C.BEAST_FREQUENCY_SCORE_THRESHOLD) / C.BEAST_FREQUENCY_RAMP_SCORE);
        const beastInterval = C.BEAST_SPAWN_INTERVAL_MS -
          (C.BEAST_SPAWN_INTERVAL_MS - C.BEAST_MIN_SPAWN_INTERVAL_MS) * progress;
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
      if (item.y - itemSize > this.viewH) { this.items.splice(i, 1); continue; }
      if (intersects(item.hitbox(), this.player.hitbox(this.groundY, this.gameTimeMs, this.viewH))) {
        this.handleItemContact(item);
        this.items.splice(i, 1);
        if (this.gameOver) break;
      }
    }

    if (this.gameOver) { this.publishVehicleHud(); return; }

    var remaining = dtMs / 1000;
    do {
      let seconds = Math.min(remaining, C.PHYSICS_STEP_MS / 1000);
      for (const b of this.beasts) seconds = Math.min(seconds, b.secondsToJumpBoundary);
      const oldBeasts = new Map(this.beasts.map(b => [b, b.hitbox(this.groundY, this.viewH)]));
      const oldBullets = new Map(this.bullets.map(b => [b, b.hitbox()]));
      this.beasts.forEach(b => b.update(seconds, frameScore > C.BEAST_JUMP_SCORE_THRESHOLD));
      this.bullets.forEach(b => { b.x += b.direction * b.speed * seconds; });
      const hits: HitRec[] = [];
      for (const bullet of this.bullets) for (const beast of this.beasts) {
        const t = sweptHit(oldBullets.get(bullet)!, bullet.hitbox(), oldBeasts.get(beast)!, beast.hitbox(this.groundY, this.viewH));
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
      this.bullets = this.bullets.filter(b => b.x > -b.sizePx && b.x < this.viewW + b.sizePx);
      remaining -= seconds;
    } while (remaining > 0);

    for (let j = this.beasts.length - 1; j >= 0 && !this.gameOver; j--) {
      const beast = this.beasts[j];
      const offScreen = (beast.direction === 1 && beast.x - beast.sizePx > this.viewW) ||
        (beast.direction === -1 && beast.x + beast.sizePx < 0);
      if (offScreen) { this.beasts.splice(j, 1); continue; }
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

  private handleItemContact(item: FallingT) {
    if (item.kind === "BEAN") { this.addScore(C.SCORE_BEAN); sfx("CATCH"); }
    else if (item.kind === "MEAT") { this.addScore(C.SCORE_MEAT); sfx("CATCH"); }
    else if (item.kind === "PIE") { this.addScore(C.SCORE_PIE); sfx("CATCH"); }
    else {
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

  private beastScore(beast: Beast): number {
    return beast.kind === ("WOLF" as BeastKind) ? C.SCORE_WOLF : C.SCORE_CRUSH_BEAST;
  }

  private addScore(points: number) {
    if (this.gameOver) return;
    this.score += points;
    while (this.score >= this.nextExtraLifeAt && this.lives < C.MAX_LIVES) {
      this.lives++;
      this.nextExtraLifeAt += C.POINTS_PER_EXTRA_LIFE;
    }
    this.listener?.onHudChanged(this.score, this.lives, Math.max(0, this.nextExtraLifeAt - this.score));
  }

  private loseLife() {
    if (this.gameOver) return;
    this.lives--;
    if (this.lives <= 0) {
      this.lives = 0; this.gameOver = true;
      this.listener?.onHudChanged(this.score, this.lives, 0);
      sfx("GAMEOVER");
      this.listener?.onGameOver(this.score);
    } else {
      this.player.invincibleUntilMs = this.gameTimeMs + C.INVINCIBLE_AFTER_HIT_MS;
      this.listener?.onHudChanged(this.score, this.lives, Math.max(0, this.nextExtraLifeAt - this.score));
    }
  }

  private publishVehicleHud(force = false) {
    const p = this.player;
    const riding = p.riding;
    const seconds = riding ? this.secondsLeftOfVehicle() : 0;
    const hits = riding ? p.vehicleHits : 0;
    if (!force && riding === this.hudRiding && seconds === this.hudSeconds &&
      hits === this.hudHits && p.ammoRemaining === this.hudAmmo && p.supplyVersion === this.hudSupplyVersion) return;
    this.hudRiding = riding; this.hudSeconds = seconds; this.hudHits = hits; this.hudAmmo = p.ammoRemaining;
    this.hudSupplyVersion = p.supplyVersion;
    this.listener?.onVehicleChanged(riding, hits, seconds, p.ammoRemaining, p.supplyVersion);
  }

  private secondsLeftOfVehicle(): number {
    return Math.max(0, Math.floor((this.player.vehicleExpiresAtMs - this.gameTimeMs + 999) / 1000));
  }

  private spawnItem(frameScore: number) {
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
    let kind: ItemKind;
    if (roll < C.P_BEAN) kind = "BEAN";
    else if (roll < C.P_BEAN + C.P_MEAT) kind = "MEAT";
    else if (roll < C.P_BEAN + C.P_MEAT + C.P_PIE) kind = "PIE";
    else kind = (["ROCK", "BOMB", "KNIFE"] as ItemKind[])[Math.floor(Math.random() * 3)];
    const speedFactor = 1 + (Math.random() * 2 - 1) * C.FALL_SPEED_VARIANCE;
    const x = size + Math.random() * (this.viewW - 2 * size);
    this.items.push(new FallingItem(kind, size, speedFactor, x, -size));
  }

  private spawnBeast() {
    const size = C.BEAST_SIZE * this.viewW;
    const kinds: BeastKind[] = ["TIGER", "LION", "WOLF"];
    const kind = kinds[Math.floor(Math.random() * 3)];
    const fromLeft = this.beasts[0] ? this.beasts[0].direction > 0 : Math.random() < 0.5;
    const speed = C.BEAST_SPEED_BASE * this.viewW * this.difficulty * (0.9 + Math.random() * 0.2);
    const x = fromLeft ? -size : this.viewW + size;
    this.beasts.push(new Beast(kind, size, speed, x, fromLeft ? 1 : -1));
  }
}

