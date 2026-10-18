# HappyEat

Android 离线休闲接物游戏：接住食物、驾驶 NPC 送来的坦克，躲避危险物与从屏幕边缘出现的猛兽。使用 Kotlin、单 Activity 和 SurfaceView/Canvas，不依赖第三方游戏引擎。

## 操作与设置

- 在底部移动区左右滑动，角色按手指位移移动；按下独立跳跃键立即起跳，长按不连续跳跃。
- 支持双指同时移动和跳跃，空中可以调整方向；驾驶坦克时不能跳跃，原跳跃键变为射击键并显示弹数。游戏画面本身不接受移动、跳跃或射击手势。
- 默认左侧移动、右侧跳跃；在设置中可交换位置，重启后保留。
- 设置支持修改用户名、切换语言和选择自动/白天/黄昏/夜晚背景。改名不改写已有排行榜记录。
- 暂停菜单可以继续游戏或返回主界面；帮助页提供物品图例和操作说明。

## 分数阶段

| 分数 | 新解锁内容 |
| --- | --- |
| 0–50 | 只掉落加分食物，不出现载具、危险物或猛兽 |
| 51–100 | 开放危险物和 NPC 送坦克 |
| 101–200 | 每 12 秒尝试生成一只狮、虎或狼，猛兽只在地面移动 |
| 201–300 | 猛兽可以随机跳跃，出场频率仍保持低频 |
| 301 起 | 猛兽出场间隔由约 12 秒逐渐缩短，600 分及以上保持 6 秒 |

阈值采用“超过”：50、100、200、300 分仍属于前一阶段。开局食物按既有豆/肉/馅饼权重重新归一化；超过 50 分后恢复完整生成分布，原载具机会用于送坦克，不从空中掉落。猛兽在解锁前不积累生成计时，重新开始恢复初始阶段。掉落速度、掉落频率及背景切换仍使用原有时间规则。

## NPC 送坦克与射击

汽车已移除。51 分起，每次生成机会有 4% 概率由 NPC 从随机一侧开来坦克，停在距出场侧边缘屏幕宽度的 1/3 处。停稳后 NPC 下车跑回原侧，坦克等待 3 秒；主角在地面接触即可上车，未上车则消失。驶入中或从上方跳过不能上车；NPC 和无人坦克不参与伤害碰撞。

最多一辆坦克正在驶入或等候；此时再次抽到送车机会就改生成食物。已驾驶坦克仍可接收另一辆停稳的坦克：刷新时间、补满弹药，已承受的撞击次数保留。

- 骑乘持续 15 秒或承受 3 次撞击，先到即报废；可以左右移动，但不能跳跃。
- 初始 3 发子弹，按下动作键发射一发，长按不连射。弹尽禁用射击，仍可移动、撞击；报废后恢复跳跃键。
- 有猛兽时向它们的来向开火；没有猛兽时保留上次开火方向，首次默认由送车侧决定：左侧送来朝右，右侧送来朝左；移动不会改变开火方向。
- 子弹水平直飞，不追踪、不穿透，只击败命中的一只猛兽，不打空中危险物；炮击不消耗撞击耐久。
- 撞击或炮击击败狼得 5 分，狮子或老虎得 10 分。
- 场上还有猛兽时，后续猛兽只能从同一侧出现；全部离场或被击败后，下一批重新随机选择来向。同屏上限仍为 3 只。

完整方案与验收范围见 [坦克交付与射击方案](docs/tank-delivery-plan.md)。

## 当前界面与音乐

底部控制条与场景分离，移动区为薄荷色、跳跃键为金色，具有按压与禁用反馈。人物和掉落物已放大，所有掉落物取消类别外圈；碰撞框仍为显示尺寸的 75%。精灵缺失时保留实心占位，六张背景覆盖两组白天、黄昏和夜晚场景。

游戏 BGM 使用 ACE-Step 生成的器乐版本，处理后约 54.25 秒，以 Ogg Vorbis 随 APK 打包，运行游戏不需要连接音乐服务。BGM 使用 MediaPlayer 循环，音效使用 SoundPool。菜单曲和原有音效保持不变；生成与处理信息见 [音乐记录](docs/game-music.md)。

## 构建与测试

需要 JDK 17、Android SDK 36；本机 SDK 路径配置在不入库的 `local.properties`。Gradle、插件及依赖版本以构建文件为准，`gradle.properties` 中 Java 路径需要适配本机。

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lint
./gradlew assembleDebugAndroidTest
```

调试包：`app/build/outputs/apk/debug/app-debug.apk`。

原生回归使用自定义 instrumentation runner，建议在模拟器或专用测试设备运行：

```sh
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument -w -e group 操作区 com.happyeat.game.test/com.happyeat.game.MainActivityInstrumentation
adb -s DEVICE_SERIAL shell am instrument -w -e group 坦克 -e qa true com.happyeat.game.test/com.happyeat.game.MainActivityInstrumentation
adb -s DEVICE_SERIAL shell am instrument -w -e group 音频 com.happyeat.game.test/com.happyeat.game.MainActivityInstrumentation
```

省略 `-e group` 运行全部场景；`真实位图`、`优化` 为绘制及视觉场景分组，`-e qa true` 可采集相关截图。音频组完整播放并跨越两次循环，运行时间随资源长度变化。

本轮 51 项单元测试通过，覆盖分数阶段、NPC 送达、坦克补给、弹药、炮击与重开。正常屏及小屏（720×1280、字体 1.3 倍）坦克原生测试各 4 项通过，相关操控、语言重建、死亡、帮助及几何回归通过。详细记录见 [玩法验收](docs/gameplay-validation.md)。最终音乐实测时长 54,250 ms，播放进度达到 54,217 ms 并回绕两次；这些结果不代替真机手感或人工音乐试听。

## 工程入口

- `app/src/main/java/com/happyeat/game/game/`：世界、实体、绘制与声音。
- `app/src/main/java/com/happyeat/game/ui/`：页面、HUD 和独立控制条。
- `app/src/main/java/com/happyeat/game/data/`：本机排行榜和偏好。
- `app/src/main/java/com/happyeat/game/config/GameConfig.kt`：玩法、尺寸、样式和音量参数。
- [AGENTS.md](AGENTS.md)：项目开发约定与玩法规格。
