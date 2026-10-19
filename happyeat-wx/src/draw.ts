/** 素材加载 + 绘制助手 + 场景背景（六场景 900ms 交叉淡入） */
import { C, BgMode } from "./config";

export declare const wx: any;

export const SPRITE_FILES = [
  "bean", "bomb", "knife", "lion", "meat", "menu_hero", "pie", "player",
  "rock", "tank", "tiger", "title_emblem", "ui_back", "ui_check", "ui_close",
  "ui_exit", "ui_heart", "ui_help", "ui_language", "ui_moon", "ui_move",
  "ui_pause", "ui_play", "ui_restart", "ui_settings", "ui_sun", "ui_tap",
  "ui_trophy", "wolf",
] as const;
export const BG_FILES = [
  "background", "background_sunset", "background_night",
  "background_orchard", "background_orchard_sunset", "background_orchard_night",
] as const;

export const images: Record<string, any> = {};

export function loadAll(onProgress: (done: number, total: number) => void, onDone: () => void) {
  const files: string[] = [
    ...BG_FILES.map(f => `assets/sprites/${f}.jpg`),
    ...SPRITE_FILES.map(f => `assets/sprites/${f}.png`),
  ];
  let done = 0;
  for (const f of files) {
    const img = wx.createImage();
    img.onload = function () {
      images[f.replace("assets/sprites/", "").replace(/\.(png|jpg)$/, "")] = img;
      done++;
      onProgress(done, files.length);
      if (done === files.length) onDone();
    };
    img.onerror = function () { done++; if (done === files.length) onDone(); };
    img.src = f;
  }
}

/** 场景选择：AUTO 按对局时间 60 秒轮换六景；手动固定 */
export function backgroundFileFor(phaseMs: number, bgMode: string): string {
  return bgFileFor(bgMode, phaseMs).replace("assets/sprites/", "").replace(".jpg", "");
}

export function bgFileFor(bgMode: string, phaseMs: number): string {
  if (bgMode === "day") return "background";
  if (bgMode === "sunset") return "background_sunset";
  if (bgMode === "night") return "background_night";
  const phase = Math.floor(phaseMs / 60000) % 6;
  return BG_FILES[phase];
}

let prevBg: HTMLImageLike | null = null;
let shownBg: HTMLImageLike | null = null;
let fadeStart = -1;
interface HTMLImageLike { width: number; height: number }

export function drawBackground(ctx: any, vw: number, vh: number, bgMode: string, phaseMs: number, now: number) {
  const file = bgFileFor(bgMode, phaseMs);
  const img = images[file];
  if (!img) { ctx.fillStyle = "#7ab6f0"; ctx.fillRect(0, 0, vw, vh); return; }
  if (img !== shownBg) {
    prevBg = shownBg; shownBg = img;
    fadeStart = prevBg ? now : -1;
  }
  ctx.drawImage(img, 0, 0, vw, vh);
  const prev = prevBg;
  if (prev && fadeStart > 0) {
    const fade = (now - fadeStart) / C.BG_FADE_MS;
    if (fade >= 1) { prevBg = null; fadeStart = -1; }
    else { ctx.globalAlpha = 1 - fade; ctx.drawImage(prev, 0, 0, vw, vh); ctx.globalAlpha = 1; }
  }
}

/** 场景草地上沿（物理地面按素材实测系数对齐） */
export function groundYFor(vw: number, vh: number, bgMode: string, phaseMs: number): number {
  const file = bgFileFor(bgMode, phaseMs).replace("assets/sprites/", "").replace(".jpg", "");
  const frac = (C.BG_GROUND_FRACTIONS as Record<string, number>)[file] ?? 0.8;
  return vh * frac;
}

export function drawSprite(ctx: any, img: any, cx: number, cy: number, size: number) {
  if (!img) { ctx.fillStyle = "#ccc"; ctx.beginPath(); ctx.arc(cx, cy, size / 2, 0, 7); ctx.fill(); return; }
  ctx.drawImage(img, cx - size / 2, cy - size / 2, size, size);
}

export function roundRect(ctx: any, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}
