/** 音频：双 BGM 轨道 + 音效（InnerAudioContext） */
import type { SoundEvent, BgmTrack } from "./sound-types";

const SFX_FILES: Record<SoundEvent, string> = {
  CATCH: "assets/audio/sfx_catch.mp3", HIT: "assets/audio/sfx_hit.mp3",
  MOUNT: "assets/audio/sfx_mount.mp3", CRUSH: "assets/audio/sfx_crush.mp3",
  JUMP: "assets/audio/sfx_jump.mp3", CLICK: "assets/audio/sfx_click.mp3",
  GAMEOVER: "assets/audio/sfx_gameover.mp3",
};
const BGM_FILES: Record<"MENU" | "GAME", string> = {
  MENU: "assets/audio/bgm_menu.mp3", GAME: "assets/audio/bgm_loop.mp3",
};

let pool: (wx.InnerAudioContext | null)[] = [];
let bgmStream: wx.InnerAudioContext | null = null;
let bgmTrack: "NONE" | "MENU" | "GAME" = "NONE";

export function initAudio() {
  if (pool.length) return;
  (Object.keys(SFX_FILES) as SoundEvent[]).forEach(ev => {
    const a = wx.createInnerAudioContext();
    a.src = SFX_FILES[ev];
    a.volume = 0.9;
    pool.push(a);
    sfxPool[ev] = a;
  });
}
const sfxPool: Partial<Record<SoundEvent, wx.InnerAudioContext>> = {};

export function sfx(ev: SoundEvent) {
  const a = sfxPool[ev];
  if (a) { a.stop(); a.play(); }
}

export function startBgm(track: "MENU" | "GAME") {
  if (bgmTrack === track && bgmStream) return;
  if (bgmStream) { bgmStream.stop(); bgmStream = null; }
  const a = wx.createInnerAudioContext();
  a.src = BGM_FILES[track];
  a.loop = true;
  a.volume = 0.45;
  a.play();
  bgmStream = a;
  bgmTrack = track;
}
export function stopBgm() {
  if (bgmStream) { bgmStream.stop(); bgmStream = null; }
  bgmTrack = "NONE";
}
export function releaseAudio() { stopBgm(); }
