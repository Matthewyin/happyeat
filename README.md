# HappyEat

Android 离线休闲接物游戏：接住食物和载具，躲避危险物与从两侧出现的猛兽。使用 Kotlin、单 Activity 和 SurfaceView/Canvas，不依赖第三方游戏引擎。

## 操作与设置

- 在底部移动区左右滑动，角色按手指位移移动；按下独立跳跃键立即起跳，长按不连续跳跃。
- 支持双指同时移动和跳跃，空中可以调整方向；游戏画面本身不接受移动或跳跃手势。
- 默认左侧移动、右侧跳跃；在设置中可交换位置，重启后保留。
- 设置支持修改用户名、切换语言和选择自动/白天/黄昏/夜晚背景。改名不改写已有排行榜记录。
- 暂停菜单可以继续游戏或返回主界面；帮助页提供物品图例和操作说明。

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
adb -s DEVICE_SERIAL shell am instrument -w -e group 音频 com.happyeat.game.test/com.happyeat.game.MainActivityInstrumentation
```

省略 `-e group` 运行全部场景；`真实位图`、`优化` 为绘制及视觉场景分组，`-e qa true` 可采集相关截图。音频组完整播放并跨越两次循环，运行时间随资源长度变化。

本轮已有 18 项单元测试通过，正常屏及小屏操作区测试各 3 项通过，真实位图和视觉场景通过。最终音乐实测时长 54,250 ms，播放进度达到 54,238 ms 并回绕两次；这些结果不代替真机手感或人工音乐试听。

## 工程入口

- `app/src/main/java/com/happyeat/game/game/`：世界、实体、绘制与声音。
- `app/src/main/java/com/happyeat/game/ui/`：页面、HUD 和独立控制条。
- `app/src/main/java/com/happyeat/game/data/`：本机排行榜和偏好。
- `app/src/main/java/com/happyeat/game/config/GameConfig.kt`：玩法、尺寸、样式和音量参数。
- [AGENTS.md](AGENTS.md)：项目开发约定与玩法规格。
