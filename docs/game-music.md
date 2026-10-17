# 游戏 BGM 生成与集成记录

2026-10-17 按用户要求使用 ACE-Step 生成纯器乐游戏 BGM。只替换游戏曲 `app/src/main/res/raw/bgm_loop.ogg`，菜单曲和七类音效未改；播放器使用现有 MediaPlayer 循环接口。

## 生成来源

- 模型：`acestep-v15-turbo`，后端 `mlx`，使用自托管服务。
- 调用：`POST /release_task` → `POST /query_result` → `GET /v1/audio`。
- 任务 ID：`0a6cbecf-7940-4c79-9fff-68dd9cd2ac98`。
- 参数：`task_type=text2music`、`audio_duration=64`、`bpm=120`、`key_scale=C major`、`time_signature=4`、`inference_steps=8`、`batch_size=1`、`audio_format=wav`、`seed=20261017`。
- `thinking`、`sample_mode`、`use_format`、`use_random_seed` 均为 false；`vocal_language=unknown`，`lyrics=[Instrumental]`。

实际提示词：

```text
Pure instrumental cheerful cartoon arcade game background music, playful marimba and pizzicato strings, warm bouncy bass, light crisp percussion, sparkling xylophone melody, sunny pastoral adventure, catchy simple repeating motifs, steady 120 BPM in C major 4/4, continuous even energy, clean spacious mix, loop-friendly, no dramatic intro or ending. Strictly instrumental: no singing, no vocals, no spoken words, no choir, no humming, no vocal samples.
```

原始输出为 64 秒、48 kHz、双声道 PCM16 WAV。原始 SHA-256：

```text
b6fe37d53ba2983b0305859da7d958abb284c7ce727f6135d4bd16ca5b28ae25
```

游戏运行只读取打包资源，无需访问生成服务。原始音频和生成日志留在本机，不随仓库提交。

## 循环处理

原始音频末尾约 5 秒低于 −50 dB。截取原曲 `[1.5, 56)` 秒，重采样到 44.1 kHz 双声道；首尾各 250 ms 以互补余弦权重交叠，将文件边界放在原本连续的采样位置。衰减约 2.007 dB，使编码前峰值为 −3 dBFS；没有压缩动态。

编码使用 SoundFile 0.14.0 / libsndfile 1.2.2，格式 Ogg Vorbis、`compression_level=0.5`，FFmpeg 8.0.1 独立解码核验。最终 54.25 秒、2,392,425 帧、1,094,217 bytes。删除同名旧 WAV，避免 Android raw 资源冲突。

最终 SHA-256：

```text
7bbfb1183e929a789e3897d76fd6f13a25473ea8588b48d973594440803ec193
```

处理依据为采样及能量分析，没有以生成提示中的 BPM 宣称精确节拍或整小节剪辑。

## 验证与限制

- 最终文件与 APK 内资源逐字节一致；菜单音乐和所有 SFX 保持原哈希。
- 解码峰值约 −2.97 dBFS、综合响度 −15.8 LUFS，削波采样数为 0，完整解码成功。
- 接缝采样跳变约 0.00211，曲内差分第 99 百分位约 0.04761；接缝处未检测到达到 −50 dB、持续 10 ms 的空隙。
- Android 原生音频组实测 `duration=54250 ms`、`positionMax=54238 ms`、`wraps=2`；同轨调用、切轨、停止/释放/初始化及直接调用生命周期回调的恢复检查通过；未完整实测真实结算、各页面导航和系统切后台时的新曲音频状态。
- 音源按纯器乐要求生成，但以上指标不能代替人工确认无人声残留、节拍衔接、风格和音效清晰度。人工听感待用户试听，不能描述为已通过无缝或无人声听感验收。
