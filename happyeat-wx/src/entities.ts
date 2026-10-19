/** GameWorld v5 忠实移植：实体 + 世界模拟（不含渲染） */
import { C, ItemKind, BeastKind } from "./config";

export type SoundEvent = "CATCH" | "HIT" | "MOUNT" | "CRUSH" | "JUMP" | "CLICK" | "GAMEOVER";
export interface WorldListener {
  onHudChanged(score: number, lives: number, pointsToNextLife: number): void;
  onVehicleChanged(riding: boolean, hits: number, seconds: number, ammo: number, supplyVersion: number): void;
  onGameOver(score: number): void;
  sfx(ev: SoundEvent): void;
}

export interface Aabb { l: number; t: number; r: number; b: number }
const hit = (cx: number, cy: number, half: number): Aabb => ({ l: cx - half, t: cy - half, r: cx + half, b: cy + half });
export function intersects(a: Aabb, b: Aabb): boolean { return a.l < b.r && a.r > b.l && a.t < b.b && a.b > b.t; }

/** 相对运动求最早进入时间；严格相切不算相交 */
export function sweptHit(a: Aabb, endA: Aabb, b: Aabb, endB: Aabb): number | null {
  let enter = 0, exit = 1;
  const axis = (la: number, ha: number, lb: number, hb: number, v: number): boolean => {
    if (v === 0) return la < hb && ha > lb;
    enter = Math.max(enter, (lb - ha) / v);
    exit = Math.min(exit, (hb - la) / v);
    return enter <= exit;
  };
  if (!axis(a.l, a.r, b.l, b.r, endA.l - a.l)) return null;
  if (!axis(a.t, a.b, b.t, b.b, endA.t - a.t)) return null;
  return enter <= exit ? enter : null;
}

export class Player {
  x = 0; sizePx = 0;
  riding = false;
  supplyVersion = 0;
  ammoRemaining = 0;
  lastFireDirection: number | null = null;
  vehicleHits = 0;
  vehicleExpiresAtMs = 0;
  invincibleUntilMs = 0;
  private jumpStartMs = Number.MIN_SAFE_INTEGER;

  get displaySizePx(): number {
    return this.sizePx * (this.riding ? C.RIDING_PLAYER_FALLBACK_SCALE : 1);
  }
  displayCenterY(groundY: number, nowMs: number, viewH: number): number {
    return groundY - this.sizePx / 2 - this.heightOffset(nowMs, viewH) -
      (this.riding ? this.sizePx * C.RIDING_PLAYER_OFFSET : 0);
  }
  hitbox(groundY: number, nowMs: number, viewH: number): Aabb {
    const half = this.displaySizePx * C.HITBOX_SCALE / 2;
    return hit(this.x, this.displayCenterY(groundY, nowMs, viewH), half);
  }
  isInvincible(nowMs: number) { return nowMs < this.invincibleUntilMs; }
  isAirborne(nowMs: number) { return this.jumpStartMs !== Number.MIN_SAFE_INTEGER && nowMs - this.jumpStartMs < C.JUMP_AIRTIME_MS; }
  heightOffset(nowMs: number, viewH: number): number {
    if (!this.isAirborne(nowMs)) return 0;
    const t = (nowMs - this.jumpStartMs) / C.JUMP_AIRTIME_MS;
    return Math.sin(t * Math.PI) * C.JUMP_HEIGHT * viewH;
  }
  jump(nowMs: number) { if (!this.riding && !this.isAirborne(nowMs)) this.jumpStartMs = nowMs; }
  mount(nowMs: number, defaultDirection: number) {
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
    if (this.vehicleHits >= C.VEHICLE_MAX_HITS) { this.riding = false; this.ammoRemaining = 0; }
  }
  tickVehicle(nowMs: number) { if (this.riding && nowMs >= this.vehicleExpiresAtMs) { this.riding = false; this.ammoRemaining = 0; } }
  resetState() {
    this.supplyVersion = 0; this.ammoRemaining = 0; this.lastFireDirection = null;
    this.jumpStartMs = Number.MIN_SAFE_INTEGER;
  }
}

export class Beast {
  jumping = false;
  jumpT = 0;
  constructor(
    public kind: BeastKind, public sizePx: number, public speedPxPerSec: number,
    public x: number, public direction: number,
    private rand: () => number = Math.random,
  ) {}
  get secondsToJumpBoundary(): number {
    if (!this.jumping) return Infinity;
    return ((this.jumpT < 0.5 ? 0.5 : 1) - this.jumpT) * C.BEAST_JUMP_DURATION_MS / 1000;
  }
  update(dt: number, allowJump: boolean) {
    this.x += this.speedPxPerSec * this.direction * dt;
    if (!allowJump) { this.jumping = false; this.jumpT = 0; }
    else if (this.jumping) {
      this.jumpT += dt * 1000 / C.BEAST_JUMP_DURATION_MS;
      if (this.jumpT >= 1) { this.jumping = false; this.jumpT = 0; }
    } else if (this.rand() < C.BEAST_JUMP_CHANCE_PER_SEC * dt) this.jumping = true;
  }
  heightOffset(viewH: number): number {
    return this.jumping ? Math.sin(this.jumpT * Math.PI) * C.BEAST_JUMP_HEIGHT * viewH : 0;
  }
  hitbox(groundY: number, viewH: number): Aabb {
    const half = this.sizePx * C.HITBOX_SCALE / 2;
    return hit(this.x, groundY - this.sizePx / 2 - this.heightOffset(viewH), half);
  }
}

export class FallingItem {
  constructor(
    public kind: ItemKind, public sizePx: number, public speedFactor: number,
    public x: number, public y: number,
  ) {}
  hitbox(): Aabb {
    const half = this.sizePx * C.HITBOX_SCALE / 2;
    return hit(this.x, this.y, half);
  }
}

export class Bullet {
  constructor(public x: number, public y: number, public direction: number, public sizePx: number, public speed: number) {}
  hitbox(): Aabb { const h = this.sizePx / 2; return hit(this.x, this.y, h); }
}

export class TankDelivery {
  sizePx: number;
  x: number;
  targetX: number;
  stoppedAtMs: number | null = null;
  constructor(public direction: number, public worldWidth: number) {
    this.sizePx = C.TANK_SIZE * worldWidth;
    this.x = direction > 0 ? -this.sizePx : worldWidth + this.sizePx;
    this.targetX = worldWidth * (direction > 0 ? C.TANK_STOP_FRACTION : 1 - C.TANK_STOP_FRACTION);
  }
  /** 返回 true = 已停靠 */
  update(dt: number, nowMs: number): boolean {
    if (this.stoppedAtMs !== null) return false;
    const dist = C.TANK_SPEED * this.worldWidth * dt;
    if (Math.abs(this.targetX - this.x) <= dist) { this.x = this.targetX; this.stoppedAtMs = nowMs; return true; }
    this.x += this.direction * dist;
    return false;
  }
}

export class ReturningNpc {
  constructor(public x: number, public direction: number, public sizePx: number) {}
}
