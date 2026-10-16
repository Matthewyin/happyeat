package com.happyeat.game.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.happyeat.game.R

enum class SoundEvent { CATCH, HIT, MOUNT, CRUSH, JUMP, CLICK, GAMEOVER }

/** 两轨 BGM：MENU（主菜单/设置/帮助/排行榜）与 GAME（对局中），互斥播放 */
enum class BgmTrack { NONE, MENU, GAME }

object SoundManager {
    private const val BGM_VOLUME = 0.45f
    private const val SFX_VOLUME = 0.9f

    private var pool: SoundPool? = null
    private val ids = HashMap<SoundEvent, Int>()
    private var bgmMenuId = -1
    private var bgmGameId = -1
    private var bgmStream = 0
    private var bgmTrack = BgmTrack.NONE

    fun init(context: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val newPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(attrs)
            .build()
        pool = newPool
        ids[SoundEvent.CATCH] = newPool.load(context, R.raw.sfx_catch, 1)
        ids[SoundEvent.HIT] = newPool.load(context, R.raw.sfx_hit, 1)
        ids[SoundEvent.MOUNT] = newPool.load(context, R.raw.sfx_mount, 1)
        ids[SoundEvent.CRUSH] = newPool.load(context, R.raw.sfx_crush, 1)
        ids[SoundEvent.JUMP] = newPool.load(context, R.raw.sfx_jump, 1)
        ids[SoundEvent.CLICK] = newPool.load(context, R.raw.sfx_click, 1)
        ids[SoundEvent.GAMEOVER] = newPool.load(context, R.raw.sfx_gameover, 1)
        bgmMenuId = newPool.load(context, R.raw.bgm_menu, 1)
        bgmGameId = newPool.load(context, R.raw.bgm_loop, 1)
    }

    fun play(event: SoundEvent) {
        val p = pool ?: return
        val id = ids[event] ?: return
        p.play(id, SFX_VOLUME, SFX_VOLUME, 1, 0, 1f)
    }

    /** 切换/启动 BGM 轨道；相同轨道重复调用是无操作 */
    fun startBgm(track: BgmTrack) {
        if (track == BgmTrack.NONE) {
            stopBgm()
            return
        }
        val p = pool ?: return
        if (track == bgmTrack && bgmStream != 0) return
        if (bgmStream != 0) {
            p.stop(bgmStream)
            bgmStream = 0
        }
        val id = when (track) {
            BgmTrack.MENU -> bgmMenuId
            BgmTrack.GAME -> bgmGameId
            BgmTrack.NONE -> return
        }
        if (id > 0) bgmStream = p.play(id, BGM_VOLUME, BGM_VOLUME, 0, -1, 1f)
        bgmTrack = track
    }

    fun stopBgm() {
        if (bgmStream != 0) {
            pool?.stop(bgmStream)
            bgmStream = 0
        }
        bgmTrack = BgmTrack.NONE
    }

    fun release() {
        stopBgm()
        pool?.release()
        pool = null
        ids.clear()
    }
}
