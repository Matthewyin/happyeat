package com.happyeat.game.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.media.MediaPlayer
import com.happyeat.game.config.GameConfig
import com.happyeat.game.R

enum class SoundEvent { CATCH, HIT, MOUNT, CRUSH, JUMP, CLICK, GAMEOVER }

/** 两轨 BGM：MENU（主菜单/设置/帮助/排行榜）与 GAME（对局中），互斥播放 */
enum class BgmTrack { NONE, MENU, GAME }

object SoundManager {
    private var appContext: Context? = null
    private var bgmPlayer: MediaPlayer? = null

    private var pool: SoundPool? = null
    private val ids = HashMap<SoundEvent, Int>()
    private var bgmTrack = BgmTrack.NONE

    fun init(context: Context) {
        if (pool != null) return
        appContext = context.applicationContext
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val newPool = SoundPool.Builder()
            .setMaxStreams(GameConfig.Audio.MAX_SFX_STREAMS)
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
    }

    fun play(event: SoundEvent) {
        val p = pool ?: return
        val id = ids[event] ?: return
        p.play(id, GameConfig.Audio.SFX_VOLUME, GameConfig.Audio.SFX_VOLUME, 1, 0, 1f)
    }

    /** 切换/启动 BGM 轨道；相同轨道重复调用是无操作 */
    fun startBgm(track: BgmTrack) {
        if (track == BgmTrack.NONE) {
            stopBgm()
            return
        }
        val context = appContext ?: return
        if (track == bgmTrack && bgmPlayer != null) return
        stopBgm()
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            val resource = if (track == BgmTrack.MENU) R.raw.bgm_menu else R.raw.bgm_loop
            context.resources.openRawResourceFd(resource).use {
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            player.prepare()
            player.isLooping = true
            player.setVolume(GameConfig.Audio.BGM_VOLUME, GameConfig.Audio.BGM_VOLUME)
            player.setOnErrorListener { failed, _, _ ->
                if (bgmPlayer === failed) stopBgm()
                true
            }
            bgmPlayer = player
            bgmTrack = track
            player.start()
        } catch (_: java.io.IOException) {
            player.release()
            bgmPlayer = null
            bgmTrack = BgmTrack.NONE
        } catch (_: RuntimeException) {
            player.release()
            bgmPlayer = null
            bgmTrack = BgmTrack.NONE
        }
    }

    fun stopBgm() {
        val player = bgmPlayer
        bgmPlayer = null
        bgmTrack = BgmTrack.NONE
        player?.release()
    }

    fun release() {
        stopBgm()
        pool?.release()
        pool = null
        ids.clear()
        appContext = null
    }
}
