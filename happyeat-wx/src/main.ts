/** HappyEat 微信小游戏入口：场景状态机 + 双区操控 + 绘制 + 存储/分享/开放数据域 */
import { C, BgMode, Candy } from "./config";
import { GameWorld } from "./world";
import { loadAll, images, drawBackground, groundYFor, drawSprite, roundRect, backgroundFileFor } from "./draw";
import { initAudio, startBgm, stopBgm, sfx } from "./audio";

declare const wx: any;
type PageName = "LOADING" | "MENU" | "GAME" | "PAUSE" | "SETTINGS" | "HELP" | "LEADERBOARD" | "GAME_OVER" | "TUTORIAL";

let canvas: any, ctx: any;
let W = 0, H = 0, dpr = 1;
let page: PageName = "LOADING";
let bgMode: BgMode | string = "auto";
let jumpOnLeft = false;
let nickname = "玩家";
let settingsChanged = false;

const world = new GameWorld();
const openData = wx.getOpenDataContext();

interface Rect { x: number; y: number; w: number; h: number }
const buttons: Record<string, Rect> = {};
function setBtn(k: string, r: Rect) { buttons[k] = r; }
function hit(r: Rect | undefined, x: number, y: number): boolean {
  return !!r && x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h;
}

/* ---------- 存储 ---------- */
function loadSettings() {
  bgMode = wx.getStorageSync("bg") || "auto";
  jumpOnLeft = !!wx.getStorageSync("jumpLeft");
  nickname = wx.getStorageSync("nickname") || "玩家";
}
function saveSettings() { /* 字段在各自操作里已即时保存 */ }

function bestScore(): number { return wx.getStorageSync("best") || 0; }
function saveBest(s: number) { wx.setStorageSync("best", s); }
function records(): { score: number; date: string }[] {
  try { return wx.getStorageSync("records") || []; } catch (e) { return []; }
}
function addRecord(score: number) {
  if (score <= 0) return;
  const list = records();
  const d = new Date();
  const date = `${d.getFullYear()}/${d.getMonth() + 1}/${d.getDate()}`;
  list.unshift({ score, date });
  wx.setStorageSync("records", list.slice(0, 50));
}

/* ---------- 音频状态机 ---------- */
function applyAudio() {
  const p = page;
  if (p === "MENU" || p === "SETTINGS" || p === "HELP" || p === "LEADERBOARD") startBgm("MENU");
  else if (p === "GAME" || p === "PAUSE" || p === "TUTORIAL") startBgm("GAME");
  else stopBgm();
}
function stopAllBgm() { stopBgm(); }

/* ---------- 触控 ---------- */
let moveTouchId: number | null = null;
let moveLastX = 0;
let jumpHoldUntil = 0;

let audioArmed = false;
wx.onTouchStart(function (e) {
  if (!audioArmed) { audioArmed = true; applyAudio(); }
  const t = e.touches[0];
  if (!t) return;
  const x = toViewX(t.clientX), y = t.clientY;
  if (page === "GAME") {
    if (hit(buttons.jumpBtn, x, y)) {
      world.performPlayerAction();
      jumpHoldUntil = Date.now() + 300;
      return;
    }
    if (hit(buttons.moveZone, x, y) || (y > H - 300 && !hit(buttons.jumpBtn, x, y))) {
      moveTouchId = t.identifier; moveLastX = x;
    }
    return;
  }
  if (page === "TUTORIAL") {
    wx.setStorageSync("tutorialShown", true);
    startGame();
    return;
  }
  if (page === "MENU") {
    if (hit(buttons.start, x, y)) { sfx("CLICK"); startFromMenu(); return; }
    if (hit(buttons.leaderboard, x, y)) { sfx("CLICK"); showPage("LEADERBOARD"); return; }
    if (hit(buttons.settings, x, y)) { sfx("CLICK"); showPage("SETTINGS"); return; }
    if (hit(buttons.help, x, y)) { sfx("CLICK"); showPage("HELP"); return; }
    if (hit(buttons.exit, x, y)) { sfx("CLICK"); wx.exitMiniProgram(); return; }
  } else if (page === "PAUSE") {
    if (hit(buttons.resume, x, y)) { resumeGame(); return; }
    if (hit(buttons.settings2, x, y)) { showPage("SETTINGS"); return; }
    if (hit(buttons.help2, x, y)) { showPage("HELP"); return; }
  } else if (page === "GAME_OVER") {
    if (hit(buttons.leaderboard2, x, y)) { showPage("LEADERBOARD"); return; }
    if (hit(buttons.restart, x, y)) { restartGame(); return; }
    if (hit(buttons.mainmenu, x, y)) { showMenu(); return; }
  } else if (page === "SETTINGS") {
    if (hit(buttons.back, x, y)) { backFromPage(); return; }
    if (hit(buttons.rename, x, y)) { editNickname(); return; }
    for (let i = 0; i < 3; i++) if (hit((buttons as any)["lang" + i], x, y)) { setLanguage(i); return; }
    for (let i = 0; i < 4; i++) if (hit((buttons as any)["bg" + i], x, y)) { setBg(i); return; }
    if (hit(buttons.jumpSide, x, y)) {
      jumpOnLeft = !jumpOnLeft;
      wx.setStorageSync("jumpLeft", jumpOnLeft);
    }
  } else if (page === "HELP") {
    if (hit(buttons.back, x, y)) { backFromPage(); return; }
  } else if (page === "LEADERBOARD") {
    if (hit(buttons.back, x, y)) { backFromPage(); return; }
  }
});
wx.onTouchMove(function (e) {
  if (page !== "GAME" || moveTouchId === null) return;
  for (const t of e.changedTouches) {
    if (t.identifier === moveTouchId) {
      world.dragPlayerBy(t.clientX - moveLastX);
      moveLastX = toViewX(t.clientX);
    }
  }
});
wx.onTouchEnd(function (e) {
  for (const t of e.changedTouches) if (t.identifier === moveTouchId) moveTouchId = null;
});

function editNickname() {
  wx.showKeyboard({ defaultValue: nickname, maxLength: 20 });
  wx.onKeyboardInput(function handler(res: any) {
    nickname = (res.value || "").trim() || "玩家";
    wx.offKeyboardInput(handler);
  });
  wx.onKeyboardComplete(function handler() {
    wx.setStorageSync("nickname", nickname);
    wx.offKeyboardComplete(handler);
  });
}
function setLanguage(i: number) {
  // 微信小游戏语言跟随微信客户端；此处仅保存偏好供文案切换
  wx.setStorageSync("langPref", ["system", "en", "zh"][i]);
}
function setBg(i: number) {
  bgMode = ["auto", "day", "sunset", "night"][i];
  wx.setStorageSync("bg", bgMode);
}

/* ---------- 页面切换 ---------- */
function showMenu() { hideOverlays(); setPage("MENU"); }
function backFromPage() {
  if (pauseVisible) { showPage("PAUSE"); applyAudio(); }
  else showMenu();
}
function startFromMenu() {
  sfx("CLICK");
  if (!wx.getStorageSync("tutorialShown")) { showPage("TUTORIAL"); applyAudio(); }
  else startGame();
}
function startGame() {
  world.reset();
  showPage("GAME");
  applyAudio();
  world.refreshHud();
}
function restartGame() { gameOverHide(); startGame(); }
function resumeGame() { pauseVisible = false; page = "GAME"; applyAudio(); }
let pauseVisible = false, gameOverVisible = false;
function gameOverHide() { gameOverVisible = false; }
function showMenuSafe() { gameOverHide(); showMenu(); }
function hideOverlays() { pauseVisible = false; gameOverVisible = false; }

let lastScoreSubmitted = -1;
world.listener = {
  onHudChanged(score, lives, pts) { hud = { score, lives, pts }; },
  onVehicleChanged(riding, hits, seconds) { veh = { riding, hits, seconds }; },
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
        KVDataList: [{ key: "score", value: String(score) }],
      });
    } catch (e) { /* 忽略 */ }
  },
};
let hud = { score: 0, lives: C.START_LIVES, pts: C.POINTS_PER_EXTRA_LIFE };
let veh = { riding: false, hits: 0, seconds: 0 };

/* ---------- 绘制 ---------- */
var viewW = 0, viewOffX = 0;
const INK = "#0B3D2E", CREAM = "#FFF6E3", AMBER = "#F59E0B", GOLD = "#FCD34D";

function draw() {
  ctx.save();
  ctx.beginPath(); ctx.rect(viewOffX, 0, W, H); ctx.clip();
  if (page === "LOADING") {
    ctx.fillStyle = "#7AB6F0"; ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = "#fff"; ctx.font = "bold 28px sans-serif"; ctx.textAlign = "center";
    ctx.fillText("加载中 " + Math.round(W) + "x" + Math.round(H), W / 2, H / 2);
    ctx.textAlign = "left";
    return;
  }
  const now = Date.now();
  const phase = world.gameTimeMs;
  const gY = groundYFor(W, H, bgMode, phase);
  world.groundY = gY;

  if (page === "MENU") { drawMenu(now); return; }
  drawBackground(ctx, W, H, bgMode, phase, now);

  // 实体
  for (const it of world.items) {
    const img = images[it.kind.toLowerCase()];
    ctx.strokeStyle = it.kind === "BEAN" || it.kind === "MEAT" || it.kind === "PIE" ? "#22C55E" : "#EF4444";
    ctx.lineWidth = it.sizePx * 0.09;
    ctx.beginPath(); ctx.arc(it.x, it.y, it.sizePx * 0.62, 0, 7); ctx.stroke();
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
    ctx.beginPath(); ctx.arc(b.x, b.y, b.sizePx, 0, 7); ctx.fill();
  }

  // 玩家（骑乘时画坦克）
  const p = world.player;
  if (!(!p.riding && p.isInvincible(world.gameTimeMs) && Math.floor(world.gameTimeMs / 120) % 2 === 0)) {
    if (p.riding) drawSprite(ctx, images.tank, p.x, gY - p.sizePx * 0.35, p.sizePx * 1.6);
    drawSprite(ctx, images.player, p.x, p.displayCenterY(gY, world.gameTimeMs, H), p.displaySizePx);
  }

  (canvas as any).__lastDrawTs = Date.now();
  if (page === "SETTINGS") { drawSettings(); return; }
  if (page === "HELP") { drawHelpPage(); return; }
  if (page === "LEADERBOARD") { drawLeaderboardPage(); return; }
  // 双区操控条（糖果风）
  var scale = W / 390;
  var bh = C.UI.CONTROLS_HEIGHT * 0.9 * scale;
  var jumpW = C.UI.JUMP_AREA_WIDTH * scale;
  var inset = C.UI.CONTROL_INSET * scale;
  var by = H - bh - inset - 6;
  var moveR: Rect, jumpR: Rect;
  if (jumpOnLeft) {
    jumpR = { x: inset, y: by, w: jumpW, h: bh };
    moveR = { x: inset + jumpW + inset, y: by, w: W - jumpW - inset * 3, h: bh };
  } else {
    moveR = { x: inset, y: by, w: W - jumpW - inset * 3, h: bh };
    jumpR = { x: W - jumpW - inset, y: by, w: jumpW, h: bh };
  }
  setBtn("moveZone", moveR); setBtn("jumpBtn", jumpR);
  var p2 = world.player;
  var jumpLabel = p2.riding ? ("射击 ×" + p2.ammoRemaining) : "跳跃";
  drawCandyIcon(moveR, images.ui_move, p2.riding ? "#78CBA6" : "#FFFDF4", "#0B3D2E", INK, "滑动移动");
  drawCandyIcon(jumpR, images.ui_tap, "#F3BC36", "#0B3D2E", INK, jumpLabel);
  if (p2.riding) {
    ctx.fillStyle = INK; ctx.font = "bold 16px sans-serif"; ctx.textAlign = "center";
    ctx.fillText("坦克待命", W / 2, by - 10); ctx.textAlign = "left";
  }

  drawHud();
  if (page === "PAUSE") drawPause();
  if (page === "GAME_OVER") drawGameOver();
  if (page === "TUTORIAL") drawTutorial();
}

function drawHud() {
  ctx.fillStyle = "rgba(16,23,36,0.35)";
  roundRect(ctx, 12, 12, 250, 92, 18); ctx.fill();
  ctx.fillStyle = "#fff"; ctx.font = "bold 22px Baloo 2, sans-serif";
  ctx.fillText("分数 " + hud.score, 28, 44);
  var heart = images.ui_heart;
  if (heart) ctx.drawImage(heart, 28, 56, 18, 18);
  ctx.font = "16px Baloo 2, sans-serif";
  ctx.fillText(String(hud.lives), heart ? 52 : 28, 72);
  ctx.fillText("还差 " + hud.pts + " 分送命", 28, 96);
  if (veh.riding) {
    ctx.fillStyle = GOLD;
    ctx.fillText(`骑乘中 · 剩 ${veh.seconds} 秒 · 撞击 ${veh.hits}/3`, 28, 122);
  }
  // 右上圆形按钮
  circleIcon(W - 120, 46, images.ui_pause, function () { pauseGame(); });
  circleIcon(W - 52, 46, images.ui_settings, function () { openSettings(); });
}
let btnRects: Record<string, Rect> = {};
function circleIcon(x: number, y: number, img: any, onTap: () => void) {
  ctx.fillStyle = "rgba(16,23,36,0.35)";
  ctx.beginPath(); ctx.arc(x, y, 26, 0, 7); ctx.fill();
  if (img) ctx.drawImage(img, x - 14, y - 14, 28, 28);
  btnRects["ic" + x] = { x: x - 30, y: y - 30, w: 60, h: 60 };
  (buttons as any)["ic" + x] = { x: x - 30, y: y - 30, w: 60, h: 60 };
}

function drawMenu(_now: number) {
  const hero = images.menu_hero;
  if (hero) ctx.drawImage(hero, 0, 0, W, H * 0.62);
  ctx.fillStyle = "rgba(11,61,46,0.92)";
  ctx.fillRect(0, H * 0.58, W, H * 0.42);
  const emblem = images.title_emblem;
  ctx.fillStyle = "#fff";
  ctx.font = "bold 64px Baloo 2, sans-serif";
  ctx.textAlign = "center";
  ctx.fillText("HappyEat", W / 2, H * 0.64);
  ctx.font = "18px sans-serif"; ctx.fillStyle = GOLD;
  ctx.fillText("接住美味 · 躲开危险", W / 2, H * 0.64 + 34);
  ctx.textAlign = "left";

  const bx = W * 0.08, bw = W * 0.84;
  setBtn("start", { x: bx, y: H * 0.74, w: bw, h: 64 });
  drawCandy(buttons.start, AMBER, "#FDE68A", INK, "▶ 开始游戏");
  setBtn("leaderboard", { x: bx, y: H * 0.74 + 80, w: bw, h: 56 });
  drawCandy(buttons.leaderboard, CREAM, "#D9C9A3", INK, "🏆 排行榜");
  setBtn("settings", { x: bx, y: H * 0.74 + 148, w: bw, h: 56 });
  drawCandy(buttons.settings, CREAM, "#D9C9A3", INK, "⚙ 设置");
  setBtn("help", { x: bx, y: H * 0.74 + 216, w: bw, h: 56 });
  drawCandy(buttons.help, CREAM, "#D9C9A3", INK, "❓ 帮助");
  setBtn("exit", { x: bx, y: H * 0.74 + 284, w: bw, h: 56 });
  drawCandy(buttons.exit, 0x40EF4444, "#EF4444", "#FCA5A5", "↪ 退出游戏");
  ctx.textAlign = "left";
}

function drawCandyIcon(r: Rect, icon: any, base: string, stroke: string, color: string, label: string) {
  ctx.fillStyle = "#D9C9A3";
  roundRect(ctx, r.x, r.y + 5, r.w, r.h, r.h / 2); ctx.fill();
  ctx.fillStyle = base;
  roundRect(ctx, r.x, r.y, r.w, r.h - 5, (r.h - 5) / 2); ctx.fill();
  ctx.strokeStyle = stroke; ctx.lineWidth = 2;
  roundRect(ctx, r.x, r.y, r.w, r.h - 5, (r.h - 5) / 2); ctx.stroke();
  if (icon) ctx.drawImage(icon, r.x + 18, r.y + (r.h - 5) / 2 - 14, 28, 28);
  ctx.fillStyle = color;
  ctx.font = "bold 22px Baloo 2, sans-serif";
  ctx.textAlign = "center";
  ctx.fillText(label, r.x + r.w / 2 + 14, r.y + r.h / 2 - 5 + 8);
  ctx.textAlign = "left";
}

function drawCandy(r: Rect, base: string, stroke: string, color: string, label: string) {
  ctx.fillStyle = "#D9C9A3";
  roundRect(ctx, r.x, r.y + 5, r.w, r.h, r.h / 2); ctx.fill();
  ctx.fillStyle = base;
  roundRect(ctx, r.x, r.y, r.w, r.h - 5, (r.h - 5) / 2); ctx.fill();
  ctx.strokeStyle = stroke; ctx.lineWidth = 2;
  roundRect(ctx, r.x, r.y, r.w, r.h - 5, (r.h - 5) / 2); ctx.stroke();
  ctx.fillStyle = color;
  ctx.font = "bold 22px Baloo 2, sans-serif";
  ctx.textAlign = "center";
  ctx.fillText(label, r.x + r.w / 2, r.y + r.h / 2 - 5 + 8);
  ctx.textAlign = "left";
}

function drawPanelCard(title: string) {
  ctx.fillStyle = "rgba(11,61,46,0.92)";
  ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = CREAM;
  roundRect(ctx, W * 0.07, H * 0.16, W * 0.86, H * 0.68, 30); ctx.fill();
  ctx.strokeStyle = INK; ctx.lineWidth = 3;
  roundRect(ctx, W * 0.07, H * 0.16, W * 0.86, H * 0.68, 30); ctx.stroke();
  ctx.fillStyle = INK;
  ctx.font = "bold 30px Baloo 2, sans-serif";
  ctx.fillText(title, W * 0.12, H * 0.24);
}

function drawPause() {
  drawPanelCard("已暂停");
  const b1 = { x: W * 0.3, y: H * 0.4, w: W * 0.4, h: 60 };
  const b2 = { x: W * 0.3, y: H * 0.5, w: W * 0.4, h: 60 };
  const b3 = { x: W * 0.3, y: H * 0.6, w: W * 0.4, h: 60 };
  setBtn("resume", b1); setBtn("settings2", b2); setBtn("help2", b3);
  drawCandy(b1, AMBER, "#FDE68A", INK, "▶ 继续");
  drawCandy(b2, CREAM, "#D9C9A3", INK, "⚙ 设置");
  drawCandy(b3, CREAM, "#D9C9A3", INK, "❓ 帮助");
}

function drawGameOver() {
  ctx.fillStyle = "rgba(11,61,46,0.92)";
  ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = "#fff";
  ctx.font = "bold 44px Baloo 2, sans-serif"; ctx.textAlign = "center";
  ctx.fillText("游戏结束", W / 2, H * 0.3);
  ctx.font = "24px sans-serif";
  ctx.fillText("本局得分 " + world.score, W / 2, H * 0.4);
  ctx.fillText("最高分 " + bestScore(), W / 2, H * 0.47);
  ctx.textAlign = "left";
  const bw = W * 0.5, bx = W * 0.25;
  setBtn("leaderboard2", { x: bx, y: H * 0.55, w: bw, h: 60 });
  drawCandy(buttons.leaderboard2, CREAM, "#D9C9A3", INK, "🏆 排行榜");
  setBtn("restart", { x: bx, y: H * 0.65, w: bw, h: 60 });
  drawCandy(buttons.restart, AMBER, "#FDE68A", INK, "↻ 重新开始");
  setBtn("mainmenu", { x: bx, y: H * 0.75, w: bw, h: 60 });
  drawCandy(buttons.mainmenu, CREAM, "#D9C9A3", INK, "⌂ 主菜单");
  ctx.textAlign = "left";
}

function drawTutorial() {
  ctx.fillStyle = "rgba(11,61,46,0.9)"; ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = "#fff"; ctx.font = "bold 26px sans-serif"; ctx.textAlign = "center";
  ctx.fillText("⬅ 按住拖动移动 ➡", W / 2, H * 0.38);
  ctx.fillText("⬆ 快速点按跳跃", W / 2, H * 0.48);
  ctx.font = "18px sans-serif"; ctx.fillStyle = GOLD;
  ctx.fillText("轻触任意处开始", W / 2, H * 0.58);
  ctx.textAlign = "left";
}

/* ---------- 设置/帮助/排行榜页 ---------- */
function drawLeaderboardPage() {
  ctx.fillStyle = "#FFF6E3"; ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = INK; ctx.font = "bold 34px Baloo 2, sans-serif";
  ctx.fillText("排行榜", 40, 80);
  circleBack(60, 60);
  renderFriendRank();
}
function drawHelpPage() {
  ctx.fillStyle = "#FFF6E3"; ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = INK; ctx.font = "bold 34px Baloo 2, sans-serif";
  ctx.fillText("帮助", 40, 80);
  circleBack(60, 60);
  var rows: Array<[string, string]> = [
    ["bean.png", "豆子：+1 分（绿圈=能吃）"],
    ["meat.png", "肉：+5 分"],
    ["pie.png", "馅饼：+10 分"],
    ["car.png", "车/坦克：骑乘挡伤害（黄圈）"],
    ["bomb.png", "石头/炸弹/刀：碰到掉命，快躲！（红圈）"],
    ["tiger.png", "猛兽贴地冲来——快速点按跳跃躲过"],
  ];
  var y = 130;
  rows.forEach(function (rw) {
    var img = images[rw[0].replace(".png", "")];
    if (img) ctx.drawImage(img, 40, y - 8, 44, 44);
    ctx.fillStyle = INK; ctx.font = "16px sans-serif";
    ctx.fillText(rw[1], 100, y + 18);
    y += 58;
  });
  ctx.fillStyle = Candy.AMBER_DARK; ctx.font = "bold 17px sans-serif";
  ctx.fillText("滑动移动 · 快速点按跳跃", 40, y + 24);
}

/* ---------- 设置/帮助/排行榜页 ---------- */
function drawSettings() {
  ctx.fillStyle = "#FFF6E3"; ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = INK; ctx.font = "bold 34px Baloo 2, sans-serif";
  ctx.fillText("设置", 40, 80);
  circleBack(60, 60);
  let y = 140;
  ctx.font = "bold 22px sans-serif"; ctx.fillText("当前用户名: " + nickname, 40, y); y += 50;
  const rn = { x: 40, y: y, w: W - 80, h: 56 };
  setBtn("rename", rn);
  drawCandy(rn, CREAM, "#D9C9A3", INK, "⚙ 修改用户名");
  y += 90;
  ctx.font = "bold 22px sans-serif"; ctx.fillText("语言 / Language", 40, y); y += 40;
  const langs = ["跟随系统", "English", "中文"];
  langs.forEach(function (l, i) {
    const r = { x: 40, y: y, w: W - 80, h: 52 };
    setBtn("lang" + i, r);
    drawOptionRow(r, l, i === langSel);
    y += 64;
  });
  y += 20;
  ctx.font = "bold 22px sans-serif"; ctx.fillText("背景", 40, y); y += 40;
  const bgs = ["自动（随时间变化）", "白天", "黄昏", "夜晚"];
  const bgVals = ["auto", "day", "sunset", "night"];
  bgs.forEach(function (l, i) {
    const r = { x: 40, y: y, w: W - 80, h: 52 };
    setBtn("bg" + i, r);
    drawOptionRow(r, l, bgMode === bgVals[i]);
    y += 64;
  });
}
let langSel = 0;
function drawOptionRow(r: Rect, label: string, selected: boolean) {
  ctx.fillStyle = selected ? "rgba(245,158,11,0.18)" : "rgba(11,61,46,0.08)";
  roundRect(ctx, r.x, r.y, r.w, r.h, 18); ctx.fill();
  ctx.strokeStyle = selected ? AMBER : "rgba(11,61,46,0.2)"; ctx.lineWidth = 2;
  roundRect(ctx, r.x, r.y, r.w, r.h, 18); ctx.stroke();
  ctx.fillStyle = INK; ctx.font = "18px sans-serif";
  ctx.fillText(label, r.x + 18, r.y + 34);
  if (selected) { ctx.fillStyle = AMBER; ctx.fillText("✓", r.x + r.w - 40, r.y + 34); }
}
function circleBack(x: number, y: number) {
  ctx.fillStyle = "rgba(11,61,46,0.15)";
  ctx.beginPath(); ctx.arc(x, y, 28, 0, 7); ctx.fill();
  ctx.fillStyle = INK; ctx.font = "bold 20px sans-serif"; ctx.textAlign = "center";
  ctx.fillText("◀", x, y + 8);
  ctx.textAlign = "left";
  setBtn("back", { x: x - 32, y: y - 32, w: 64, h: 64 });
}

/* ---------- 主循环 ---------- */
let lastTs = 0;
let drawErrShown = false;
let loopAnnounced = false;
var viewOffY = 0;
function applyViewTransform() {
  var s = dpr;
  ctx.setTransform(s, 0, 0, s, viewOffX * s, 0);
}
function toViewX(clientX: number) { return clientX - viewOffX; }
function loop(ts: number) {
  if (!loopAnnounced) { loopAnnounced = true; console.error("[HappyEat] loop frame 1 running, page=" + page); }
  const dt = lastTs ? Math.min(50, ts - lastTs) : 16;
  lastTs = ts;
  if (page === "GAME" && !gameOverFrozen()) {
    world.update(dt);
    world.groundFraction = (C.BG_GROUND_FRACTIONS as Record<string, number>)[backgroundFileFor(world.gameTimeMs, bgMode)] ?? 0.8;
    world.groundY = H * world.groundFraction;
  }
  try { draw(); } catch (e) {
    if (!drawErrShown) { drawErrShown = true; console.error("[HappyEat] draw error:", (e as Error).stack || e); }
  }
  requestAnimationFrame(loop);
}
function gameOverFrozen() { return world.gameOver; }

function pauseGame() {
  if (page !== "GAME") return;
  showPage("PAUSE");
  pauseVisible = true;
}
function openSettings() {
  if (page === "GAME") { pauseVisible = true; gameViewBehind = true; }
  showPage("SETTINGS");
}
let gameViewBehind = false;
function openHelp() { if (page === "GAME") { gameViewBehind = true; pauseVisible = true; } showPage("HELP"); }

function setPage(p: PageName) { page = p; }
function showPage(p: PageName) { setPage(p); applyAudio(); }

/* ---------- 启动 ---------- */
function startGameBoot() {
  canvas = wx.createCanvas();
  const info = wx.getSystemInfoSync();
  dpr = Math.min(info.pixelRatio || 2, 2);
  H = info.windowHeight;
  W = Math.round(H / 2);
  viewOffX = Math.round((info.windowWidth - W) / 2);
  canvas.width = info.windowWidth * dpr; canvas.height = H * dpr;
  ctx = canvas.getContext("2d");
  applyViewTransform();
  loadSettings();
  world.bgIndex = 0;
  world.groundFraction = (C.BG_GROUND_FRACTIONS as Record<string, number>)[backgroundFileFor(0, bgMode)] ?? 0.8;
  world.onSize(W, H);
  loadAll(function (d, t) { /* 加载进度可绘制 */ }, function () {
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
  wx.onShareAppMessage(function () {
    return {
      title: "HappyEat — 接住美味，躲开危险！我的最高分 " + bestScore() + " 分",
      imageUrl: "assets/sprites/title_emblem.png",
    };
  });
}

/* 页面可见时开放数据域渲染好友排行 */
function renderFriendRank() {
  openData.postMessage({ type: "render", width: Math.round(W * 0.86), height: Math.round(H * 0.5), selfName: nickname });
  const c = openData.canvas;
  ctx.drawImage(c, W * 0.07, H * 0.2, W * 0.86, H * 0.5);
}



startGameBoot();
export {};
